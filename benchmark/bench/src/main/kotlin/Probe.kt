import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.parsers.TomlParser

/** Probe which single-feature TOML snippets the linked ktoml version accepts. */
fun main() {
    val parser = TomlParser(TomlInputConfig())
    val cases = listOf(
        "int" to "x = 42",
        "int_neg" to "x = -17",
        "int_underscore" to "x = 1_000_000",
        "hex" to "x = 0xDEADBEEF",
        "hex_underscore" to "x = 0xDEAD_BEEF",
        "oct" to "x = 0o755",
        "bin" to "x = 0b1010_1101",
        "float" to "x = 3.14159",
        "float_exp" to "x = 6.626e-34",
        "float_exp_plus" to "x = 2.5e+10",
        "float_underscore_int" to "x = 9_224_617.0",
        "float_underscore_frac" to "x = 1.445_991",
        "float_underscore_exp" to "x = 6.626_070e-34",
        "float_neg" to "x = -0.5",
        "bool" to "x = true",
        "basic_string" to "x = \"hello world\"",
        "string_escapes" to "x = \"tab\\tnl\\nquote\\\"end\"",
        "string_unicode" to "x = \"caf\\u00e9 \\u2603\"",
        "literal_string" to "x = 'C:\\Users\\node'",
        "bare_key" to "my_key = 1",
        "quoted_key" to "\"my key\" = 1",
        "literal_key" to "'my.key' = 1",
        "dotted_key" to "a.b.c = 1",
        "offset_dt" to "x = 1979-05-27T07:32:00-08:00",
        "offset_dt_utc" to "x = 1979-05-27T07:32:00Z",
        "offset_dt_frac" to "x = 1979-05-27T07:32:00.999-08:00",
        "local_dt" to "x = 1979-05-27T07:32:00",
        "local_dt_frac" to "x = 1979-05-27T07:32:00.5",
        "local_date" to "x = 1979-05-27",
        "local_time" to "x = 07:32:00",
        "local_time_frac" to "x = 07:32:00.5",
        "array" to "x = [1, 2, 3]",
        "array_nested" to "x = [[1, 2], [3, 4]]",
        "array_string" to "x = [\"a\", \"b\"]",
        "inline_table" to "x = { a = 1, b = 2 }",
        "inline_nested" to "x = { a = { b = 1 } }",
        "table" to "[t]\nx = 1",
        "subtable" to "[a.b]\nx = 1",
        "array_of_tables" to "[[p]]\nx = 1\n[[p]]\nx = 2",
    )
    for ((name, snippet) in cases) {
        val ok = try {
            parser.parseString(snippet); "OK  "
        } catch (e: Throwable) {
            "FAIL ${e::class.simpleName}: ${e.message?.take(70)}"
        }
        println("$ok  $name")
    }
}
