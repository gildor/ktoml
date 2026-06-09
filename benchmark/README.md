# ktoml `toml-1.1` parser performance — benchmark report

**Issue:** gildor/ktoml#22 · **Date:** 2026-06-09 · Run independently.

## TL;DR

Measured the parse-only hot path (`TomlParser.parseString` → AST) of **upstream `main`
(`ec84e4b`, = ktoml 0.7.1)** vs **our `toml-1.1` (`a0ca8f4`)** on identical input.

- **Realistic small config files (the common real-world case): `toml-1.1` is slightly *faster*** —
  mean **−2.6%**, and **−2.8%** for the 11-file suite parsed back-to-back. No regression.
- **Large broad-feature document (1440 key/value pairs): +3.8%** — within the issue's ~5% budget.
- **One real regression: datetime-dense documents → +20%.** Attributable to the rewritten
  `TomlDateTime.parseToDateTime` (regex pre-validation + `padOffsetSeconds` + extra string
  allocations + `TomlOffsetDateTime` wrapper, per datetime value). This exceeds the 5% threshold.
- **Deeply-nested / array-of-tables document: −36% (much faster).**

**Verdict against #22 acceptance ("≤ ~5% regression"):** met for every workload **except
datetime-heavy input**, which regresses ~20%. The hot function is identified below; it is
optimizable without losing correctness.

A separate, pre-existing parser **crash bug** surfaced while building the corpus — filed as
**gildor/ktoml#29** (affects upstream too; not introduced by our changes).

---

## Results

8 forks (separate JVMs) per version, 500 warmup + 1500 measured parses per case, identical JVM
flags. Statistic = per-fork median (p50); table shows median-of-forks and the contention-robust
min-of-forks. Δ = `toml-1.1 / upstream − 1` (positive = slower on `toml-1.1`).

```
case                              bytes |  --- median-of-forks p50 (µs) ---  |  --- min-of-forks p50 (µs) ---
                                        |   upstream   toml-1.1        Δ |   upstream  toml-1.1        Δ
--------------------------------------------------------------------------------------------------------------------
complex/datetime-heavy.toml       29395 |    2227.71    2676.38   +20.1% |    2207.33   2653.42   +20.2%
complex/large-mixed.toml          45081 |    3270.83    3399.17    +3.9% |    3253.46   3376.00    +3.8%
complex/nested-structures.toml     8954 |     954.54     621.85   -34.9% |     951.00    612.08   -35.6%
small/cargo.toml                    739 |      38.04      37.08    -2.5% |      37.38     36.33    -2.8%
small/ci-config.toml                638 |      25.06      25.52    +1.8% |      24.75     25.17    +1.7%
small/feature-flags.toml            615 |      40.96      39.65    -3.2% |      40.46     38.75    -4.2%
small/foundry.toml                  483 |      28.90      27.67    -4.3% |      28.42     26.96    -5.1%
small/hugo-config.toml              547 |      31.50      30.35    -3.6% |      31.33     29.88    -4.7%
small/libs.versions.toml           1035 |     109.42      99.67    -8.9% |     108.62     98.62    -9.2%
small/netlify.toml                  592 |      31.35      30.10    -4.0% |      31.04     30.00    -3.4%
small/pyproject.toml                804 |      44.40      44.21    -0.4% |      43.54     43.62    +0.2%
small/rustfmt.toml                  366 |      19.38      18.25    -5.8% |      18.67     18.04    -3.3%
small/server-config.toml            641 |      29.79      30.65    +2.9% |      29.33     30.42    +3.7%
small/wrangler.toml                 509 |      22.44      22.19    -1.1% |      22.12     21.83    -1.3%
small-suite(11files)               6969 |     434.38     422.75    -2.7% |     430.54    418.54    -2.8%
```

The per-fork numbers are tight (e.g. datetime-heavy upstream 2207–2306µs vs toml-1.1 2653–2698µs
across all 8 forks), so these deltas are signal, not noise.

---

## Analysis

### Regression: datetime-heavy +20%

The issue's hypothesis is confirmed. Upstream's `parseToDateTime` was essentially one call:

```kotlin
Instant.parse(replaceFirst(' ', 'T'))   // + fallbacks for local types
```

`toml-1.1` runs, **per datetime value**, before parsing:

1. `isDateTimeLike()` — a regex match, plus up to 3 more regex matches in `hasValidDateTimeSyntax()`
   to reject malformed literals early;
2. three chained `replaceFirst(...)` calls (`' '→'T'`, `'t'→'T'`, `'z'→'Z'`), each allocating a new
   `String`;
3. `padOffsetSeconds()` — `indexOf` + `indexOfAny` + three `substring`s + a `count {}` + concat;
4. `hasExplicitOffset()` and, for offset datetimes, allocation of a `TomlOffsetDateTime` wrapper.

`datetime-heavy.toml` is ~420 datetimes (7 per table × 70 tables), so this per-value overhead
dominates. This is **correctness work** (it's what fixes #375/#383 offset & validation cases), but
it is optimizable. Suggested levers if a fix is wanted:

- Skip the regex pre-validation on the happy path — let `Instant.parse`/`LocalX.parse` throw and
  validate only on failure (the common case is well-formed input).
- Collapse the three `replaceFirst` passes into a single scan.
- Gate `padOffsetSeconds` on a cheap check (it already early-returns when there's no `'T'`/offset,
  but it still allocates substrings on the common offset path).
- Only allocate `TomlOffsetDateTime` when `raw != normalized` or when the offset is actually needed
  downstream.

### Speedup: nested-structures −36%

Array-of-tables / nested-array parsing got materially faster on `toml-1.1` (the array and
array-of-tables paths were reworked for #378/#381). This is a real win for that shape of document.

### Small realistic files: ~−2.6%

Cargo/pyproject/wrangler/foundry/etc. (no or few datetimes) are all within noise of break-even and
on average slightly faster. The added per-key/per-string validation (`validateQuotes`,
`validateSymbols`, escape-aware `splitKeyToTokens`, `TomlDouble` underscore check) is **not**
measurably costly on realistic configs — for typical usage, `toml-1.1` does not regress.

---

## Methodology

- **Fair-comparison gate.** The comparison only uses TOML both parsers accept. A feature probe
  showed the *only* divergence is **underscores in float literals** (e.g. `1.445_991`): upstream
  rejects them, `toml-1.1` accepts them (fix #18). The corpus therefore uses underscore-free floats
  but still exercises every changed hot path (the new per-value validation runs on *every* float,
  string, key, and datetime regardless of syntax). Both versions parse all 15 cases and produce
  **identical** top-level AST child counts (`sink=258`).
- **Same toolchain.** Both `ktoml-core` jvm jars were built locally from source with the same Gradle/
  Kotlin (2.2.0), JVM target 1.8 — so only library code differs, not the compiler.
- **Isolation.** Two versions share the package `com.akuleshov7.ktoml.*` and **cannot** coexist on
  one classpath, so each runs in its own JVM with its own jar. The harness is compiled **separately
  against each jar** — necessary because `TomlInputConfig`'s constructor signature differs between
  versions (verified: a harness compiled against one throws `NoSuchMethodError` on the other).
- **Measurement.** `System.nanoTime` around `parser.parseString(...)`; the AST is touched to defeat
  dead-code elimination; one reused parser (matches real usage). 8 forks, interleaved upstream/toml-1.1
  per fork; `-Xms2g -Xmx2g -XX:+UseParallelGC -XX:+AlwaysPreTouch`; machine kept idle during the run.
- **Corpus** (`bench/corpus/`):
  - `complex/large-mixed.toml` — 1440 kv: quoted/literal/dotted keys, int/hex/oct/bin, floats,
    offset/local datetimes, arrays, inline tables, escaped strings.
  - `complex/datetime-heavy.toml` — ~980 kv, ~420 datetimes + many floats.
  - `complex/nested-structures.toml` — arrays of tables, deep arrays, nested inline tables.
  - `small/` — 11 realistic configs: Cargo, pyproject, Gradle `libs.versions`, Hugo, Netlify,
    rustfmt, foundry, wrangler, server/CI/feature-flag configs.

**Environment:** macOS (darwin 25.4.0), OpenJDK 25.0.3 LTS, Kotlin 2.2.0.

---

## Bug found & filed

**gildor/ktoml#29** — `TomlParser.parseString` crashes with
`IllegalArgumentException: Requested character count -1 is less than zero` on **single-line inline
tables nested ≥ 3 deep** (`x = { a = { a = { a = 1 } } }`). Depth 1–2 parse fine. Root cause:
`indexOfNextOutsideQuotes` does `this.drop(startIndex)` with no `startIndex >= 0` guard, and
`splitInlineTableToKeyValue` feeds it a negative index for deep nesting. **Affects upstream
`ec84e4b` identically** → pre-existing, not caused by `toml-1.1`, and does not affect the
comparison above. Related to upstream #360 (same area, decode-level) and closed #107.

---

## What's in this branch

This is the `benchmark/issue-22` branch — the full benchmark, not for merge into `toml-1.1`.

```
benchmark/
  README.md              # this file — methodology, results, analysis
  bench/                 # the harness Gradle project
    build.gradle.kts     # compiles against a ktoml-core jar passed via -PktomlJar
    settings.gradle.kts
    src/main/kotlin/
      Bench.kt           # the parse-only timing harness (emits RESULT: JSON)
      Probe.kt           # feature-acceptance + crash-depth probes
    corpus/              # the exact TOML inputs (3 complex + 11 small)
  scripts/               # build-jars.sh, gen-corpus.py, build-bench.sh, run-bench.sh, aggregate.py
  results/
    results.jsonl        # raw per-fork measurements (the data behind the table)
    summary.txt          # aggregate.py output
```

## Reproduce

The two `ktoml-core` jars are built from the `upstream/main` and `toml-1.1` git refs (which both live
in this repo), so the comparison works from any checkout.

```bash
scripts/build-jars.sh    # build ktoml-core jvm jars for both refs -> bench/libs/
python3 scripts/gen-corpus.py   # (re)generate the complex corpus
scripts/build-bench.sh   # compile the harness against each jar + resolve runtime deps
scripts/run-bench.sh     # 8 forks x 2 versions -> bench/results.jsonl
python3 scripts/aggregate.py    # print the comparison table
```

> Note: the scripts contain absolute paths from the environment they were run in
> (`/Users/gildor/hobby/ktoml-agent`). Adjust the path variables at the top of each script to
> reproduce elsewhere. Build artifacts (`bench/libs/*.jar`, `bench/build/`) are gitignored —
> they are regenerated by the scripts. The harness must be compiled **separately against each jar**
> (`TomlInputConfig`'s constructor signature differs between versions).

Harness: `bench/src/main/kotlin/Bench.kt`. Feature/crash probes: `bench/src/main/kotlin/Probe.kt`.
