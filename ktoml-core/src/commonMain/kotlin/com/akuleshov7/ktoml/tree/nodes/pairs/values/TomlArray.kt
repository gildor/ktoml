package com.akuleshov7.ktoml.tree.nodes.pairs.values

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.exceptions.ParseException
import com.akuleshov7.ktoml.parsers.removeTrailingComma
import com.akuleshov7.ktoml.parsers.trimBrackets
import com.akuleshov7.ktoml.tree.nodes.TomlInlineTable
import com.akuleshov7.ktoml.tree.nodes.parseValue
import com.akuleshov7.ktoml.writers.TomlEmitter

/**
 * Toml AST Node for a representation of arrays: key = [value1, value2, value3]
 * @property content
 * @property multiline
 */
public class TomlArray internal constructor(
    override var content: Any,
    public var multiline: Boolean = false
) : TomlValue() {
    public constructor(
        rawContent: String,
        lineNo: Int,
        config: TomlInputConfig
    ) : this(rawContent.parse(lineNo, config))

    @Suppress("UNCHECKED_CAST")
    public fun parse(config: TomlInputConfig = TomlInputConfig()): List<Any> = content as List<Any>

    @Suppress("UNCHECKED_CAST")
    public override fun write(
        emitter: TomlEmitter,
        config: TomlOutputConfig
    ) {
        emitter.startArray()

        // an element is one of: a nested array, an inline table (array element), or a plain value
        val content = (content as List<Any>).map {
            if (it is TomlArray) {
                TomlArray(it.content, multiline)
            } else {
                it
            }
        }

        val last = content.lastIndex

        if (multiline) {
            emitter.indent()

            content.forEachIndexed { i, value ->
                emitter.emitNewLine()
                    .emitIndent()

                writeElement(value, emitter, config)

                if (i < last) {
                    emitter.emitElementDelimiter()
                }
            }

            emitter.dedent()
            emitter.emitNewLine()
                .emitIndent()
        } else {
            content.forEachIndexed { i, value ->
                emitter.emitWhitespace()

                writeElement(value, emitter, config)

                if (i < last) {
                    emitter.emitElementDelimiter()
                }
            }

            emitter.emitWhitespace()
        }

        emitter.endArray()
    }

    private fun writeElement(
        element: Any,
        emitter: TomlEmitter,
        config: TomlOutputConfig
    ): Unit = when (element) {
        is TomlValue -> element.write(emitter, config)
        is TomlInlineTable -> element.write(emitter, config)
        else -> throw ParseException("Unsupported array element type: ${element::class.simpleName}", 0)
    }

    public companion object {
        /**
         * recursively parse TOML array from the string: [ParsingArray -> Trimming values -> Parsing Nested Arrays]
         */
        private fun String.parse(lineNo: Int, config: TomlInputConfig = TomlInputConfig()): List<Any> =
            this.parseArray(lineNo)
                .map { it.trim() }
                .map {
                    when {
                        it.startsWith("[") -> TomlArray(it, lineNo, config)
                        // inline table as an array element: [ { a = 1 }, "b" ]
                        it.startsWith("{") -> TomlInlineTable.parseArrayElement(it, lineNo, config)
                        else -> it.parseValue(lineNo, config)
                    }
                }

        /**
         * method for splitting the string to the array: "[[a, b], [c], [d]]" to -> [a,b] [c] [d]
         */
        @Suppress("NESTED_BLOCK", "TOO_LONG_FUNCTION")
        private fun String.parseArray(lineNo: Int): MutableList<String> {
            val arrayContent = trim().trimBrackets().trim()
            // covering cases when the array is intentionally blank: myArray = []. It should be empty and not contain null
            if (arrayContent.isBlank()) {
                return mutableListOf()
            }

            val trimmed = arrayContent.removeTrailingComma().trim()
            if (trimmed.isBlank()) {
                throw ParseException("Array cannot contain only a comma", lineNo)
            }

            var nbBrackets = 0
            var nbBraces = 0
            var isInBasicString = false
            var isInLiteralString = false
            var bufferBetweenCommas = StringBuilder()
            val result: MutableList<String> = mutableListOf()

            for (i in trimmed.indices) {
                when (val current = trimmed[i]) {
                    '[' -> {
                        if (!isInBasicString && !isInLiteralString) {
                            nbBrackets++
                        }
                        bufferBetweenCommas.append(current)
                    }
                    ']' -> {
                        if (!isInBasicString && !isInLiteralString) {
                            nbBrackets--
                        }
                        bufferBetweenCommas.append(current)
                    }
                    '{' -> {
                        if (!isInBasicString && !isInLiteralString) {
                            nbBraces++
                        }
                        bufferBetweenCommas.append(current)
                    }
                    '}' -> {
                        if (!isInBasicString && !isInLiteralString) {
                            nbBraces--
                        }
                        bufferBetweenCommas.append(current)
                    }
                    '\'' -> {
                        if (!isInBasicString) {
                            isInLiteralString = !isInLiteralString
                        }
                        bufferBetweenCommas.append(current)
                    }
                    '"' -> {
                        if (!isInLiteralString) {
                            if (!isInBasicString) {
                                isInBasicString = true
                            } else if (trimmed[i - 1] != '\\') {
                                isInBasicString = false
                            }
                        }
                        bufferBetweenCommas.append(current)
                    }
                    // split only if we are on the highest level of brackets/braces (all are closed)
                    // and if we're not in a string
                    ',' -> if (isInBasicString || isInLiteralString || nbBrackets != 0 || nbBraces != 0) {
                        bufferBetweenCommas.append(current)
                    } else {
                        result.add(bufferBetweenCommas.toString())
                        bufferBetweenCommas = StringBuilder()
                    }
                    else -> bufferBetweenCommas.append(current)
                }
            }
            if (isInBasicString || isInLiteralString) {
                throw ParseException(
                    "Not able to parse the array: [$this] as it does not have closing quote",
                    lineNo,
                )
            }
            result.add(bufferBetweenCommas.toString())
            return result
        }
    }
}
