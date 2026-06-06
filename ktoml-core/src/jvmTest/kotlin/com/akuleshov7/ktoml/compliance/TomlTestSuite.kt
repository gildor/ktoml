package com.akuleshov7.ktoml.compliance

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.parsers.TomlParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.fail

/**
 * Runs the [toml-lang/toml-test](https://github.com/toml-lang/toml-test) compliance suite
 * against ktoml's parser (TOML 1.1 file list — matching the project goal).
 *
 * - **Valid tests:** parse TOML → convert AST to tagged JSON via [TomlTestConverter] → compare
 *   with expected JSON from the test suite.
 * - **Invalid tests:** verify that parsing throws an exception.
 *
 * Known failures are declared in [TomlTestBaseline.kt]. Every test is still executed:
 * - If a known failure **still fails** → test is skipped (expected behavior).
 * - If a known failure **now passes** → test **FAILS** with an XPASS message prompting
 *   removal from the baseline. This ensures bug fixes are detected automatically.
 *
 * ## Running
 *
 * ```
 * ./gradlew :ktoml-core:jvmTest --tests "com.akuleshov7.ktoml.compliance.TomlTestSuite"
 * ```
 *
 * Requires the `toml-test` git submodule: `git submodule update --init`
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TomlTestSuite {
    private val testDir = File("../toml-test/tests")

    private fun loadFileList(version: String = "1.1.0"): List<String> {
        val listFile = testDir.resolve("files-toml-$version")
        require(listFile.exists()) {
            "toml-test file list not found at ${listFile.absolutePath}. " +
                "Did you initialize the git submodule? Run: git submodule update --init"
        }
        return listFile.readLines().filter { it.isNotBlank() }
    }

    fun validTestCases(): Stream<Arguments> =
        loadFileList()
            .filter { it.startsWith("valid/") && it.endsWith(".toml") }
            .map { Arguments.of(it) }
            .stream()

    fun invalidTestCases(): Stream<Arguments> =
        loadFileList()
            .filter { it.startsWith("invalid/") && it.endsWith(".toml") }
            .map { Arguments.of(it) }
            .stream()

    @ParameterizedTest(name = "valid: {0}")
    @MethodSource("validTestCases")
    fun `valid TOML parses correctly`(testPath: String) {
        val tomlFile = testDir.resolve(testPath)
        val jsonFile = testDir.resolve(testPath.replace(".toml", ".json"))
        assumeTrue(tomlFile.exists(), "TOML file missing: $testPath")
        assumeTrue(jsonFile.exists(), "Expected JSON missing for: $testPath")

        val issueUrl = knownFailuresMap[testPath]

        val result = runCatching {
            val tomlInput = tomlFile.readText()
            val expectedJson = Json.parseToJsonElement(jsonFile.readText())
            val tree = TomlParser(TomlInputConfig.compliant()).parseString(tomlInput)
            val actualJson = TomlTestConverter.toJson(tree)
            assertJsonEqualsWithFloatTolerance(expectedJson, actualJson, testPath)
        }

        when {
            result.isSuccess && issueUrl != null ->
                fail("XPASS: '$testPath' now passes! Remove it from knownFailures. (Was: $issueUrl)")
            result.isFailure && issueUrl != null ->
                assumeTrue(false, "Known failure: $testPath — $issueUrl")
            result.isFailure ->
                result.getOrThrow()
        }
    }

    @ParameterizedTest(name = "invalid: {0}")
    @MethodSource("invalidTestCases")
    fun `invalid TOML is rejected`(testPath: String) {
        val tomlFile = testDir.resolve(testPath)
        assumeTrue(tomlFile.exists(), "TOML file missing: $testPath")

        val issueUrl = knownFailuresMap[testPath]

        val result = runCatching {
            val tomlInput = tomlFile.readText()
            assertFails("Expected parse failure for $testPath") {
                TomlParser(TomlInputConfig.compliant()).parseString(tomlInput)
            }
        }

        when {
            result.isSuccess && issueUrl != null ->
                fail("XPASS: '$testPath' now passes! Remove it from knownFailures. (Was: $issueUrl)")
            result.isFailure && issueUrl != null ->
                assumeTrue(false, "Known failure: $testPath — $issueUrl")
            result.isFailure ->
                result.getOrThrow()
        }
    }

    private fun assertJsonEqualsWithFloatTolerance(
        expected: JsonElement,
        actual: JsonElement,
        testPath: String,
        jsonPath: String = "$",
    ) {
        when {
            expected is JsonObject && actual is JsonObject -> {
                if (expected.isTaggedFloat() && actual.isTaggedFloat()) {
                    assertTaggedFloatEquals(expected, actual, testPath, jsonPath)
                    return
                }

                assertEquals(expected.keys, actual.keys, "Mismatch for $testPath at $jsonPath")
                expected.keys.forEach { key ->
                    assertJsonEqualsWithFloatTolerance(
                        expected.getValue(key),
                        actual.getValue(key),
                        testPath,
                        "$jsonPath.$key",
                    )
                }
            }

            expected is JsonArray && actual is JsonArray -> {
                assertEquals(expected.size, actual.size, "Mismatch for $testPath at $jsonPath")
                expected.indices.forEach { index ->
                    assertJsonEqualsWithFloatTolerance(
                        expected[index],
                        actual[index],
                        testPath,
                        "$jsonPath[$index]",
                    )
                }
            }

            else -> assertEquals(expected, actual, "Mismatch for $testPath at $jsonPath")
        }
    }

    private fun assertTaggedFloatEquals(
        expected: JsonObject,
        actual: JsonObject,
        testPath: String,
        jsonPath: String,
    ) {
        assertEquals(expected.keys, actual.keys, "Mismatch for $testPath at $jsonPath")
        assertEquals(expected["type"], actual["type"], "Mismatch for $testPath at $jsonPath.type")

        val expectedValue = expected["value"]?.jsonPrimitive?.content
            ?: fail("Missing float value for $testPath at $jsonPath")
        val actualValue = actual["value"]?.jsonPrimitive?.content
            ?: fail("Missing float value for $testPath at $jsonPath")

        if (!floatValuesMatch(expectedValue, actualValue)) {
            fail(
                "Float mismatch for $testPath at $jsonPath.value: " +
                    "expected <$expectedValue> but was <$actualValue>",
            )
        }
    }

    private fun JsonObject.isTaggedFloat(): Boolean =
        (this["type"] as? JsonPrimitive)?.content == "float" && containsKey("value")

    private fun floatValuesMatch(expected: String, actual: String): Boolean {
        if (expected.isTomlTestNaN() && actual.isTomlTestNaN()) {
            return true
        }

        val expectedFloat = parseTomlTestFloat(expected) ?: return false
        val actualFloat = parseTomlTestFloat(actual) ?: return false
        return expectedFloat == actualFloat
    }

    private fun String.isTomlTestNaN(): Boolean =
        lowercase().removePrefix("+").removePrefix("-") == "nan"

    private fun parseTomlTestFloat(value: String): Double? = when (value.lowercase()) {
        "nan", "+nan", "-nan" -> Double.NaN
        "inf", "+inf" -> Double.POSITIVE_INFINITY
        "-inf" -> Double.NEGATIVE_INFINITY
        else -> value.toDoubleOrNull()
    }
}

