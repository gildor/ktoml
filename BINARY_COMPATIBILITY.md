# Binary compatibility validation

## Two separate baselines

`./gradlew apiCheck` guards the fork's recorded JVM and KLib API against future changes. The
initial dumps were generated from `toml-1.1` at `042878bc` (after the experimental tree API and
Okio/kotlinx-io adapters), with PR #52's API categorization. They are a current-source baseline,
not dumps of an upstream release or a promise that every historical fork release is compatible.
PR #54 adds tests without changing these dumps.

Compatibility with published upstream `com.akuleshov7:ktoml-core-jvm:0.7.1` is a separate audit.
A green `apiCheck` does **not** establish that an application compiled against upstream can run
with this fork. The optional published-jar audit below exposes differences independently.
There is no all-upstream-compatible assertion or allowlist that hides known breaks.

## Fork validation and coverage

[Kotlin binary compatibility validator](https://github.com/Kotlin/binary-compatibility-validator)
0.18.1 is used with this build's Kotlin 2.2.0. It is an established plugin in maintenance mode;
critical fixes and Kotlin support continue. KLib validation and its dump format are experimental.
The plugin attaches `apiCheck` to each library's `check`, so `build` also checks the baseline.
CI explicitly runs it on pull requests and pushes to `toml-1.1`/`main`, and the release workflow
runs it before both tagged releases and manual snapshots. CI never runs `apiDump`.

| Module | JVM dump | KLib targets |
| --- | --- | --- |
| `ktoml-core` | `api/ktoml-core.api` | JS, Wasm JS/WASI, and the Native targets below |
| `ktoml-source`, `ktoml-file`, `ktoml-okio`, `ktoml-kotlinx-io` | each module's `api/<module>.api` | Native targets below |

Native targets: iOS arm64/x64/simulator arm64, macOS arm64/x64, Linux arm64/x64, and MinGW x64.
`ktoml-datetime-tests` is a test consumer, not a published library, and is excluded.
There are no package, class, internal-marker, or experimental-marker exclusions. In particular,
`@InternalKtomlApi` and `@ExperimentalKtomlApi` declarations remain in the dumps when visible
to the tool. Internal APIs carry no stability promise; experimental APIs are not stabilized.
Keeping them in the dump makes changes explicit for review, including intentionally permitted
changes, without making them supported contracts. Deprecated public adapters are also retained.

Ordinary local validation covers the targets the host compiler supports; unsupported targets
are reported and skipped. Run the same strict validation as CI to reject skipped targets:

```sh
./gradlew apiCheck -PapiStrictKlibValidation=true -Pkotlin.native.enableKlibsCrossCompilation=true
```

Kotlin 2.2.0 requires that cross-compilation property to generate KLibs for other hosts. This
checks actual compiled library signatures for all configured targets, not inferred dumps.
It does not execute Native/JS/Wasm consumers, prove runtime behavior, validate source compatibility,
or compare dependencies' ABIs. Compiler/SDK availability can still prevent a strict build.
The `.api` and `.klib.api` files are checked by equality: additions also require review.

For an intentional API change, run `./gradlew apiDump` on a host supporting the required targets
(or enable KLib cross-compilation), review the dump diff, and commit it alongside the source.
A non-Apple host's default dump may infer unsupported targets from existing dumps; do not treat
inference as validation. Removing/changing supported signatures needs an explicit compatibility
and migration decision; regenerating dumps alone does not make a break safe.

## Published artifact audits

The audits resolve a JVM jar or a JS KLib non-transitively from Maven Central using isolated
configurations; they add no upstream dependencies to the library or its consumers. They use the
same validator and visibility filters as the fork dumps and write only under `ktoml-core/build/`.
The JS KLib audit is experimental, like the validator's other KLib checks.

```sh
# Defaults to upstream com.akuleshov7:ktoml-core-jvm:0.7.1.
./gradlew :ktoml-core:referenceJvmApiDiff

# Audit a released fork artifact independently of the current-source baseline.
./gradlew :ktoml-core:referenceJvmApiDiff -PreferenceApiCoordinate=app.gildor:ktoml-core-jvm:0.9.0

# Compare the published upstream core JS KLib against the current compiled JS KLib.
./gradlew :ktoml-core:referenceJsApiDiff

# Audit the fork's released JS KLib (the @klib suffix selects the artifact explicitly).
./gradlew :ktoml-core:referenceJsApiDiff -PreferenceJsApiCoordinate=app.gildor:ktoml-core-js:0.9.0@klib

# Dump only; useful for javap/manual review or keeping the raw audit output.
./gradlew :ktoml-core:referenceJvmApiDump
```

`referenceJvmApiDiff` and `referenceJsApiDiff` print a unified diff and exit nonzero if **any** API
differs. These are opt-in diagnostics, independent of `check` and publishing. Equality includes
additions and therefore is not a classifier of binary incompatibilities. It deliberately does
not suppress known differences. The plugin appends its generic `apiDump` suggestion on failure;
that suggestion is for fork baseline maintenance and does not resolve a published-artifact audit.
The generated reference dumps are `ktoml-core/build/api/reference/ktoml-core.api` and
`ktoml-core/build/api/reference/ktoml-core-js.klib.api`. Dump-only tasks are `referenceJvmApiDump`
and `referenceJsApiDump`.

The upstream 0.7.1 audits have known differences: `TomlEmitter`'s `kotlinx.datetime` overloads were
changed, and public AST signatures changed. The six-Boolean `TomlInputConfig` constructor,
`copy`, and default-argument entry points also differ on the PR #52 base. The separate
[PR #56](https://github.com/gildor/ktoml/pull/56) restores those config entry points with hidden
binary bridges and adds precompiled Java/Kotlin consumers; it does not restore full upstream ABI.
Keep those consumer tests as complementary coverage when that PR is merged: hidden/synthetic
binary bridges can be omitted by API dumps, so even a config dump diff is not a runtime verdict.
Do not recompile those fixture binaries against the fork during ordinary tests.

The fork 0.9.0 JVM audit also exits nonzero: it shows the newer experimental `TomlElement`/`TomlDecoder`
API and related decoder-interface changes. That expected difference is not evidence of a blanket
break or a compatibility guarantee for the released fork. The published artifacts checked were:

| Artifact | SHA-256 |
| --- | --- |
| `com.akuleshov7:ktoml-core-jvm:0.7.1` | `c22cb661eefc75e30007a1b08d37c4e93a157f7f3a950a478251632c0af6050d` |
| `app.gildor:ktoml-core-jvm:0.9.0` | `38aa6c24b0c37f023fd7979a8891460640cf1e6620e54979f2538394aef09957` |
| `com.akuleshov7:ktoml-core-js:0.7.1@klib` | `3eb73010327e7217930d15b772c95333c71f70981cad9d6abf50419e4d94a01b` |
| `app.gildor:ktoml-core-js:0.9.0@klib` | `1576717ffdf71e913c52b7b50ba4c13dcd5422b290b68065062702ae455bfce4` |

The published JS KLib manifests use Kotlin compiler/ABI version 2.2.0. Their library identities
differ: upstream uses `com.akuleshov7:ktoml-core`, while fork 0.9.0 uses `app.gildor:ktoml-core`.
The checked-in source baseline uses the build's default `com.akuleshov7` group; release publishing
overrides the group to `app.gildor`. The raw JS audit retains this identity difference, alongside
signature differences; the fork source baseline is not a check of the published manifest identity.

The artifact audits cover core JVM and one representative published core JS KLib. They do not
claim compatibility for adapters, published Native/Wasm KLibs, JavaScript exports or runtime
linking. Fork all-target KLib validation above is separate from published-artifact audits.

## Regression verification

On an otherwise clean checkout, temporarily change `public val ignoreUnknownNames` to
`internal val ignoreUnknownNames` in `TomlInputConfig` in
`ktoml-core/src/commonMain/kotlin/com/akuleshov7/ktoml/TomlConfig.kt`. This removes the original
public JVM getter while the library itself still compiles. Then run:

```sh
./gradlew :ktoml-core:jvmApiCheck :ktoml-core:klibApiCheck --continue
```

Both checks must fail with signature diffs, rather than compiler errors. Restore the source
without regenerating the baseline and rerun `apiCheck`; it must pass and the committed dumps
must remain unchanged. This checks a real signature regression rather than corrupting a golden
file. This probe was run during integration: both checks failed on the removed getter/property
(and KLib `component1`), then strict all-target `apiCheck` passed after restoration with no dump
changes. Run `./gradlew build -x detekt`, `./gradlew detektAll`, and `./gradlew diktatCheck` separately
for the usual tests and lint; upstream precompiled-consumer tests, once merged, run in core `jvmTest`.
