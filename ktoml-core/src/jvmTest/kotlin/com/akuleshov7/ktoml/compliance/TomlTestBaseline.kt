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
 * | Parser rejects valid TOML | [#380](https://github.com/orchestr7/ktoml/issues/380) |
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

/** ktoml rejects valid TOML with ParseException */
data object ValidTomlRejected : KnownFailure {
    override val issue = 380
    override val tests = listOf(
        "valid/spec-1.0.0/array-0.toml",
    )
}

/** Missing validation — control characters not rejected */
data object MissingValidationControlChars : KnownFailure {
    override val issue = 383
    override val tests = listOf(
        "invalid/control/bare-cr.toml",
        "invalid/control/comment-cr.toml",
        "invalid/control/comment-del.toml",
        "invalid/control/comment-ff.toml",
        "invalid/control/comment-lf.toml",
        "invalid/control/comment-null.toml",
        "invalid/control/comment-us.toml",
        "invalid/control/multi-del.toml",
        "invalid/control/multi-lf.toml",
        "invalid/control/multi-null.toml",
        "invalid/control/multi-us.toml",
        "invalid/control/only-ff.toml",
        "invalid/control/only-vt.toml",
        "invalid/control/rawmulti-del.toml",
        "invalid/control/rawmulti-lf.toml",
        "invalid/control/rawmulti-null.toml",
        "invalid/control/rawmulti-us.toml",
        "invalid/control/rawstring-cr.toml",
        "invalid/control/rawstring-del.toml",
        "invalid/control/rawstring-lf.toml",
        "invalid/control/rawstring-null.toml",
        "invalid/control/rawstring-us.toml",
        "invalid/control/string-bs.toml",
        "invalid/control/string-cr.toml",
        "invalid/control/string-del.toml",
        "invalid/control/string-lf.toml",
        "invalid/control/string-null.toml",
        "invalid/control/string-us.toml",
    )
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
        "invalid/encoding/ideographic-space.toml",
    )
}

/**
 * Missing validation — table redefinition and duplicate keys not rejected.
 *
 * The redefinition / duplicate-key / overwrite / append-with-dotted-keys family is now rejected.
 * What remains here is purely invalid *table-name syntax* (empty key segments and multiline-string
 * keys in headers), which belongs to key-name parsing validation rather than table redefinition.
 */
data object MissingValidationTableRedefinition : KnownFailure {
    override val issue = 383
    override val tests = listOf(
        "invalid/table/append-with-dotted-keys-07.toml",
        "invalid/table/dot.toml",
        "invalid/table/dotdot.toml",
        "invalid/table/empty-implicit-table.toml",
        "invalid/table/multiline-key-01.toml",
        "invalid/table/multiline-key-02.toml",
        "invalid/table/trailing-dot.toml",
    )
}

/** Missing validation — invalid keys not rejected */
data object MissingValidationKeys : KnownFailure {
    override val issue = 383
    override val tests = listOf(
        "invalid/key/dot.toml",
        "invalid/key/dotdot.toml",
        "invalid/key/duplicate-keys-01.toml",
        "invalid/key/duplicate-keys-02.toml",
        "invalid/key/duplicate-keys-03.toml",
        "invalid/key/duplicate-keys-04.toml",
        "invalid/key/duplicate-keys-05.toml",
        "invalid/key/duplicate-keys-06.toml",
        "invalid/key/duplicate-keys-07.toml",
        "invalid/key/duplicate-keys-08.toml",
        "invalid/key/duplicate-keys-09.toml",
        "invalid/key/multiline-key-01.toml",
        "invalid/key/multiline-key-02.toml",
        "invalid/key/multiline-key-03.toml",
        "invalid/key/multiline-key-04.toml",
        "invalid/key/partial-quoted.toml",
        "invalid/key/start-dot.toml",
    )
}

/** Missing validation — invalid inline tables not rejected */
data object MissingValidationInlineTable : KnownFailure {
    override val issue = 383
    override val tests = listOf(
        "invalid/inline-table/duplicate-key-01.toml",
        "invalid/inline-table/duplicate-key-02.toml",
        "invalid/inline-table/duplicate-key-03.toml",
        "invalid/inline-table/overwrite-06.toml",
        "invalid/inline-table/overwrite-08.toml",
        "invalid/inline-table/overwrite-10.toml",
        "invalid/spec-1.0.0/inline-table-2-0.toml",
        "invalid/spec-1.0.0/inline-table-3-0.toml",
        "invalid/spec-1.0.0/table-9-0.toml",
        "invalid/spec-1.0.0/table-9-1.toml",
    )
}

/** Missing validation — invalid string escapes not rejected */
data object MissingValidationStringEscape : KnownFailure {
    override val issue = 383
    override val tests = listOf(
        "invalid/string/bad-escape-03.toml",
        "invalid/string/bad-uni-esc-06.toml",
        "invalid/string/bad-uni-esc-ml-06.toml",
        "invalid/string/multiline-bad-escape-04.toml",
    )
}

/** Missing validation — invalid datetimes not rejected */
data object MissingValidationDatetime : KnownFailure {
    override val issue = 383
    override val tests = listOf(
        "invalid/datetime/offset-minus-no-minute.toml",
        "invalid/datetime/offset-plus-no-minute.toml",
        "invalid/datetime/second-trailing-dot.toml",
        "invalid/local-time/no-secs.toml",
        "invalid/local-time/trailing-dot.toml",
        "invalid/local-datetime/no-secs.toml",
    )
}

/** Missing validation — invalid arrays not rejected */
data object MissingValidationArrays : KnownFailure {
    override val issue = 383
    override val tests = listOf(
        "invalid/array/only-comma-01.toml",
    )
}

/**
 * All known failure groups. Used by [TomlTestSuite] to build the lookup map.
 */
val allKnownFailures: List<KnownFailure> = listOf(
    ValidTomlRejected,
    MissingValidationControlChars,
    MissingValidationEncoding,
    MissingValidationTableRedefinition,
    MissingValidationKeys,
    MissingValidationInlineTable,
    MissingValidationStringEscape,
    MissingValidationDatetime,
    MissingValidationArrays,
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
