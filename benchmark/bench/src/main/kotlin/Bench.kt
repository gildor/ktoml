import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.parsers.TomlParser
import java.io.File

/**
 * Micro-benchmark for the ktoml parser hot path: TomlParser.parseString -> TomlFile AST.
 *
 * This exercises exactly the functions changed on toml-1.1 (splitKeyToTokens, validateQuotes,
 * validateSymbols, TomlDouble underscore check, TomlDateTime.parseToDateTime/padOffsetSeconds),
 * without any deserialization on top.
 *
 * Run once per ktoml-core jar (separate JVMs), with identical JVM flags. Emits one JSON object
 * per case to stdout (prefixed RESULT:) for external aggregation across forks/versions.
 */

private val config = TomlInputConfig()

private fun parseOnce(parser: TomlParser, content: String): Int {
    val tree = parser.parseString(content)
    // Touch the AST so the parse can't be eliminated as dead code.
    return tree.children.size
}

private data class Stats(
    val n: Int,
    val min: Double,
    val p50: Double,
    val mean: Double,
    val p90: Double,
    val p99: Double,
    val max: Double,
    val stddevPct: Double,
)

private fun stats(timesNs: LongArray): Stats {
    val sorted = timesNs.sortedArray()
    val n = sorted.size
    fun pct(p: Double): Double = sorted[minOf(n - 1, (p * n).toInt())].toDouble()
    val mean = sorted.average()
    val variance = sorted.sumOf { val d = it - mean; d * d } / n
    val stddev = kotlin.math.sqrt(variance)
    // convert ns -> microseconds for readability
    fun us(x: Double) = x / 1000.0
    return Stats(
        n = n,
        min = us(sorted.first().toDouble()),
        p50 = us(pct(0.50)),
        mean = us(mean),
        p90 = us(pct(0.90)),
        p99 = us(pct(0.99)),
        max = us(sorted.last().toDouble()),
        stddevPct = if (mean > 0) stddev / mean * 100.0 else 0.0,
    )
}

private fun jsonEscape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

fun main(args: Array<String>) {
    val corpusDir = File(args.getOrElse(0) { "corpus" })
    val warmup = (System.getenv("BENCH_WARMUP") ?: "300").toInt()
    val measure = (System.getenv("BENCH_MEASURE") ?: "1000").toInt()
    val label = System.getenv("BENCH_LABEL") ?: "unknown"
    val fork = System.getenv("BENCH_FORK") ?: "0"

    val complexFiles = corpusDir.resolve("complex")
        .listFiles { f -> f.extension == "toml" }!!.sortedBy { it.name }
    val smallFiles = corpusDir.resolve("small")
        .listFiles { f -> f.extension == "toml" }!!.sortedBy { it.name }

    // Reuse one parser instance across iterations, matching real usage (Toml.Default reuses one).
    val parser = TomlParser(config)
    var sink = 0L

    // A "case" = a named list of TOML documents parsed once per iteration.
    fun benchCase(name: String, docs: List<String>, bytes: Int): Stats {
        repeat(warmup) { for (d in docs) sink += parseOnce(parser, d) }
        val times = LongArray(measure)
        for (i in 0 until measure) {
            val t0 = System.nanoTime()
            for (d in docs) sink += parseOnce(parser, d)
            times[i] = System.nanoTime() - t0
        }
        val st = stats(times)
        println(
            "RESULT:{" +
                "\"label\":\"${jsonEscape(label)}\"," +
                "\"fork\":\"${jsonEscape(fork)}\"," +
                "\"case\":\"${jsonEscape(name)}\"," +
                "\"bytes\":$bytes," +
                "\"n\":${st.n}," +
                "\"min_us\":${"%.3f".format(st.min)}," +
                "\"p50_us\":${"%.3f".format(st.p50)}," +
                "\"mean_us\":${"%.3f".format(st.mean)}," +
                "\"p90_us\":${"%.3f".format(st.p90)}," +
                "\"p99_us\":${"%.3f".format(st.p99)}," +
                "\"max_us\":${"%.3f".format(st.max)}," +
                "\"stddev_pct\":${"%.1f".format(st.stddevPct)}" +
                "}"
        )
        return st
    }

    // 1) Each complex file is its own case (one parse per iteration).
    for (f in complexFiles) {
        val text = f.readText()
        benchCase("complex/${f.name}", listOf(text), text.toByteArray().size)
    }

    // 2) Each small realistic file individually.
    for (f in smallFiles) {
        val text = f.readText()
        benchCase("small/${f.name}", listOf(text), text.toByteArray().size)
    }

    // 3) "many small realistic ones" aggregate: parse the whole small set once per iteration.
    val smallDocs = smallFiles.map { it.readText() }
    val smallBytes = smallDocs.sumOf { it.toByteArray().size }
    benchCase("small-suite(${smallFiles.size}files)", smallDocs, smallBytes)

    System.err.println("[$label fork=$fork] done; sink=$sink (warmup=$warmup measure=$measure)")
}
