package com.akuleshov7.ktoml.compliance

/**
 * Known failures for the [toml-lang/toml-test](https://github.com/toml-lang/toml-test) compliance suite.
 *
 * Each [KnownFailure] object declares an issue ID and a list of test paths that fail due to that issue.
 * When a bug is fixed, the test will start passing and [TomlTestSuite] will fail with an
 * XPASS message — remove the test path from the corresponding object.
 *
 * ## Failure categories
 *
 * | Category | Issue |
 * |----------|-------|
 * | Dotted key expansion incorrect | [#377](https://github.com/orchestr7/ktoml/issues/377) |
 * | Multiline inline table crash | [#374](https://github.com/orchestr7/ktoml/issues/374) |
 * | Missing validation (accepts invalid) | [#383](https://github.com/orchestr7/ktoml/issues/383) |
 * | TOML 1.1 valid features unsupported | [#373](https://github.com/orchestr7/ktoml/issues/373) |
 *
 * The suite runs against the **TOML 1.1** file list (`files-toml-1.1.0`), matching the project goal.
 *
 * Related: [#32](https://github.com/orchestr7/ktoml/issues/32) (toml-test integration),
 * [#373](https://github.com/orchestr7/ktoml/issues/373) (TOML 1.1 umbrella)
 */
sealed interface KnownFailure {
    val issue: Int
    val tests: List<String>

    val issueUrl: String get() = "https://github.com/orchestr7/ktoml/issues/$issue"
}

/** Missing validation — bad UTF-8 encoding not rejected */
data object MissingValidationEncoding : KnownFailure {
    override val issue = 383
    override val tests = listOf(
        "invalid/encoding/bad-codepoint.toml",
        "invalid/encoding/bad-utf8-in-comment.toml",
        "invalid/encoding/bad-utf8-in-multiline.toml",
        "invalid/encoding/bad-utf8-in-multiline-literal.toml",
        "invalid/encoding/bad-utf8-in-string.toml",
        "invalid/encoding/bad-utf8-in-string-literal.toml",
    )
}

/** Missing validation — invalid datetimes not rejected */
data object MissingValidationDatetime : KnownFailure {
    override val issue = 383
    override val tests = listOf(
        "invalid/local-time/no-secs.toml",
        "invalid/local-datetime/no-secs.toml",
    )
}

/**
 * All known failure groups. Used by [TomlTestSuite] to build the lookup map.
 */
val allKnownFailures: List<KnownFailure> = listOf(
    MissingValidationEncoding,
    MissingValidationDatetime,
)

/**
 * Combined map of all known failures: test path → issue URL.
 * Used by [TomlTestSuite] to skip expected failures and detect fixes.
 */
val knownFailuresMap: Map<String, String> by lazy {
    allKnownFailures.flatMap { failure ->
        failure.tests.map { it to failure.issueUrl }
    }.toMap()
}
