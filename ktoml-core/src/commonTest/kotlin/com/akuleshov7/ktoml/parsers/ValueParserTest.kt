package com.akuleshov7.ktoml.parsers

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.exceptions.ParseException
import com.akuleshov7.ktoml.tree.nodes.TomlKeyValuePrimitive
import com.akuleshov7.ktoml.tree.nodes.pairs.values.*
import com.akuleshov7.ktoml.tree.nodes.splitKeyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValueParserTest {
    @Test
    fun parsingTest() {
        testTomlValue(Pair("a", "\"gfhgfhfg\""), NodeType.STRING)
        testTomlValue(Pair("a", "123456"), NodeType.INT)
        testTomlValue(Pair("a", "12.2345"), NodeType.FLOAT)
        testTomlValue(Pair("a", "true"), NodeType.BOOLEAN)
        testTomlValue(Pair("a", "false"), NodeType.BOOLEAN)
        testTomlValue(Pair("a", "\'false\'"), NodeType.LITERAL_STRING)
    }

    @Test
    fun malformedIntegerLiteralsAreRejected() {
        listOf(
            "1__23",
            "0x-1",
            "_123",
            "_0b1",
            "_0x1",
            "_0o1",
            "01",
            "00",
            "0_0",
            "-01",
            "+01",
            "+0_1",
            "123_",
            "0b1_",
            "0x1_",
            "0o1_",
            "0b_1",
            "0x_1",
            "0o_1",
        ).forEach { literal ->
            assertFailsWith<ParseException> {
                TomlKeyValuePrimitive("a" to literal, 1)
            }
        }
    }

    @Test
    fun malformedFloatLiteralsAreRejected() {
        listOf(
            "1.e2",
            "3.e+20",
            ".12345",
            "-.12345",
            "+.12345",
            "03.14",
            "-03.14",
            "+03.14",
            "NaN",
            "1.",
            "-1.",
            "+1.",
        ).forEach { literal ->
            assertFailsWith<ParseException> {
                TomlKeyValuePrimitive("a" to literal, 1)
            }
        }
    }

    @Test
    fun validNumericLiteralsKeepParsing() {
        listOf("1_000", "0xdead_beef", "0o7_6_5", "0b1_0_1", "0", "+99", "-0").forEach { literal ->
            testTomlValue("a" to literal, NodeType.INT)
        }

        listOf("0.0", "3e2", "3E+2", "3e1_4", "+0e0", "-0.0", "nan", "+nan", "-nan").forEach { literal ->
            testTomlValue("a" to literal, NodeType.FLOAT)
        }
    }

    @Test
    fun dateTimeParsingTest() {
        testTomlValue("a" to "1979-05-27T07:32:00Z", NodeType.DATE_TIME)
        testTomlValue("a" to "1979-05-27T00:32:00-07:00", NodeType.DATE_TIME)
        testTomlValue("a" to "1979-05-27T00:32:00.999999-07:00", NodeType.DATE_TIME)
        testTomlValue("a" to "1979-05-27 07:32:00Z", NodeType.DATE_TIME)
        testTomlValue("a" to "1979-05-27T07:32:00", NodeType.DATE_TIME)
        testTomlValue("a" to "1979-05-27T00:32:00.999999", NodeType.DATE_TIME)
        testTomlValue("a" to "1979-05-27", NodeType.DATE_TIME)
    }

    @Test
    fun nullParsingTest() {
        testTomlValue("a" to "null", NodeType.NULL)
        assertFailsWith<ParseException> {
            testTomlValue("a" to "null", NodeType.NULL, TomlInputConfig(allowNullValues = false))
        }
    }

    @Test
    fun quotesParsingTest() {
        assertFailsWith<ParseException> {
            TomlKeyValuePrimitive(Pair("\"a", "123"), 0)
        }

        assertFailsWith<ParseException> {
            TomlKeyValuePrimitive(Pair("a", "hello world"), 0)
        }
        assertFailsWith<ParseException> {
            TomlKeyValuePrimitive(Pair("a", "\"before \" string\""), 0)
        }
    }

    @Test
    fun specialSymbolsParsing() {
        assertFailsWith<ParseException> { TomlKeyValuePrimitive(Pair("a", "\"hello\\world\""), 0) }

        var test = TomlKeyValuePrimitive(Pair("a", "\"hello\\tworld\""), 0)
        assertEquals("hello\tworld", test.value.content)

        test = TomlKeyValuePrimitive(Pair("a", "\"helloworld\\n\""), 0)
        assertEquals("helloworld\n", test.value.content)

        assertFailsWith<ParseException> {
            TomlKeyValuePrimitive(Pair("a", "\"helloworld\\\""), 0)
        }

        test = TomlKeyValuePrimitive(Pair("a", "\"hello\\nworld\""), 0)
        assertEquals("hello\nworld", test.value.content)

        test = TomlKeyValuePrimitive(Pair("a", "\"hello\\bworld\""), 0)
        assertEquals("hello\bworld", test.value.content)

        test = TomlKeyValuePrimitive(Pair("a", "\"hello\\rworld\""), 0)
        assertEquals("hello\rworld", test.value.content)

        test = TomlKeyValuePrimitive(Pair("a", "\"hello\\\\tworld\""), 0)
        assertEquals("hello\\tworld", test.value.content)

        test = TomlKeyValuePrimitive(Pair("a", "\"hello\\\\world\""), 0)
        assertEquals("hello\\world", test.value.content)

        test = TomlKeyValuePrimitive(Pair("a", "\"hello tworld\""), 0)
        assertEquals("hello tworld", test.value.content)

        test = TomlKeyValuePrimitive(Pair("a", "\"hello\t\\\\\\\\world\""), 0)
        assertEquals("hello\t\\\\world", test.value.content)

        test = TomlKeyValuePrimitive("a" to "\"Ɣ is greek\"", 0)
        assertEquals("Ɣ is greek", test.value.content)

        test = TomlKeyValuePrimitive("a" to "\"\\u0194 is greek\"", 0)
        assertEquals("Ɣ is greek", test.value.content)

        test = TomlKeyValuePrimitive("a" to "\"\\U0001F615 is emoji\"", 0)
        assertEquals("\uD83D\uDE15 is emoji", test.value.content)

        test = TomlKeyValuePrimitive("a" to "\"\uD83D\uDE15 is emoji\"", 0)
        assertEquals("\uD83D\uDE15 is emoji", test.value.content)

        test = TomlKeyValuePrimitive("a" to "\"I'm a string. \\\"You can quote me\\\". Name\\tJos\\u00E9\\nLocation\\tSF.\"", 0)
        assertEquals("I'm a string. \"You can quote me\". Name\tJosé\nLocation\tSF.", test.value.content)

        // regression test related to comments with an equals symbol after it
        var pairTest =
            "lineCaptureGroup = 1  # index `warningTextHasLine = false`\n".splitKeyValue(0)
        assertEquals(1L, TomlKeyValuePrimitive(pairTest, 0).value.content)

        pairTest =
            "lineCaptureGroup = \"1 = 2\"  # index = `warningTextHasLine = false`\n".splitKeyValue(0)
        assertEquals("1 = 2", TomlKeyValuePrimitive(pairTest, 0).value.content)
    }

    @Test
    fun multilineStringEscapesParsing() {
        var test = TomlKeyValuePrimitive(
            "a" to ("\"\"\"a \\\\" + "\n" + "b\"\"\""),
            0
        )
        assertEquals("a \\\nb", test.value.content)

        test = TomlKeyValuePrimitive(
            "a" to "\"\"\"\\\n   fox jumps over \\\t  \n   the lazy dog.\"\"\"",
            0
        )
        assertEquals("fox jumps over the lazy dog.", test.value.content)

        test = TomlKeyValuePrimitive("a" to "'''There is no escape\\'''", 0)
        assertEquals("There is no escape\\", test.value.content)

        assertFailsWith<ParseException> {
            TomlKeyValuePrimitive("a" to "\"\"\"t\\ \"\"\"", 0)
        }
    }

    @Test
    fun symbolsAfterComment() {
        val keyValue = "test_key = \"test_value\"  # \" some comment".splitKeyValue(0)
        assertEquals("test_value", TomlKeyValuePrimitive(keyValue, 0).value.content)
    }

    @Test
    fun parsingIssueValue() {
        assertFailsWith<ParseException> { " = false".splitKeyValue(0) }
        assertFailsWith<ParseException> { " just false".splitKeyValue(0) }
        assertFailsWith<ParseException> {
            TomlKeyValuePrimitive(
                Pair("a", "\"\\hello tworld\""),
                0
            )
        }
        assertFailsWith<ParseException> {
            TomlKeyValuePrimitive("a" to "val\\ue", 0)
        }
        assertFailsWith<ParseException> {
            TomlKeyValuePrimitive("a" to "\\x33", 0)
        }
        assertFailsWith<ParseException> {
            TomlKeyValuePrimitive("a" to "\\UFFFFFFFF", 0)
        }
        assertFailsWith<ParseException> {
            TomlKeyValuePrimitive("a" to "\\U00D80000", 0)
        }
    }
}

enum class NodeType {
    STRING, NULL, INT, FLOAT, BOOLEAN, INCORRECT, LITERAL_STRING, DATE_TIME
}

fun getNodeType(v: TomlValue): NodeType = when (v) {
    is TomlBasicString -> NodeType.STRING
    is TomlNull -> NodeType.NULL
    is TomlLong -> NodeType.INT
    is TomlDouble -> NodeType.FLOAT
    is TomlBoolean -> NodeType.BOOLEAN
    is TomlLiteralString -> NodeType.LITERAL_STRING
    is TomlDateTime -> NodeType.DATE_TIME
    else -> NodeType.INCORRECT
}


fun testTomlValue(
    keyValuePair: Pair<String, String>,
    expectedType: NodeType,
    config: TomlInputConfig = TomlInputConfig()
) {
    assertEquals(expectedType, getNodeType(TomlKeyValuePrimitive(keyValuePair, 0, config = config).value), keyValuePair.second)
}
