package com.akuleshov7.ktoml.tree.nodes

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.exceptions.ParseException
import com.akuleshov7.ktoml.parsers.*
import com.akuleshov7.ktoml.tree.nodes.pairs.keys.TomlKey
import com.akuleshov7.ktoml.tree.nodes.tables.InlineTableType
import com.akuleshov7.ktoml.writers.TomlEmitter

/**
 * Class for parsing and representing of inline tables: inline = { a = 5, b = 6 , c = 7 }
 *
 * @param lineNo
 * @param comments
 * @param inlineComment
 * @param inlineTableType type of inline table (primitive or array)
 * @property tomlKeyValues The key-value pairs in the inline table
 * @property multiline whether the inline table should be written in multiple lines
 * @property key null when this inline table is part of array of tables
 */
public class TomlInlineTable internal constructor(
    public val key: TomlKey?,
    internal val tomlKeyValues: List<TomlNode>,
    private val inlineTableType: InlineTableType,
    public val multiline: Boolean = false,
    lineNo: Int,
    comments: List<String> = emptyList(),
    inlineComment: String = ""
) : TomlNode(
    lineNo,
    comments,
    inlineComment
) {
    @Suppress("CUSTOM_GETTERS_SETTERS")
    override val name: String get() = key.toString()

    public constructor(
        keyValuePair: Pair<String, String>,
        lineNo: Int,
        comments: List<String> = emptyList(),
        inlineComment: String = "",
        config: TomlInputConfig = TomlInputConfig(),
        inlineTableType: InlineTableType = InlineTableType.PRIMITIVE,
        multiline: Boolean = false,
    ) : this(
        TomlKey(keyValuePair.first, lineNo),
        keyValuePair.second.parseInlineTableValue(keyValuePair, lineNo, config),
        inlineTableType,
        multiline,
        lineNo,
        comments,
        inlineComment,
    )

    public fun returnTable(tomlFileHead: TomlFile, currentParentalNode: TomlNode): TomlTable {
        val tomlTable = createTableRoot(currentParentalNode)

        // FixMe: this code duplication can be unified with the logic in TomlParser
        tomlKeyValues.forEach { keyValue ->
            when {
                keyValue is TomlKeyValue && keyValue.key.isDotted -> {
                    // in case parser has faced dot-separated complex key (a.b.c) it should create proper table [a.b],
                    // because table is the same as dotted key
                    val newTableSection = keyValue.createTomlTableFromDottedKey(tomlTable)

                    tomlFileHead
                        .insertTableToTree(newTableSection)
                        .appendChild(keyValue)
                }

                keyValue is TomlInlineTable -> tomlFileHead.insertTableToTree(
                    keyValue.returnTable(tomlFileHead, tomlTable)
                )

                keyValue is TomlArrayOfTablesElement -> tomlTable.appendChild(keyValue)

                // otherwise, it should simply append the keyValue to the parent
                else -> tomlTable.appendChild(keyValue)
            }
        }
        return tomlTable
    }

    public override fun write(
        emitter: TomlEmitter,
        config: TomlOutputConfig
    ) {
        key?.let {
            it.write(emitter)
            emitter.emitPairDelimiter()
        }

        when (inlineTableType) {
            InlineTableType.PRIMITIVE -> emitter.startInlineTable()
            InlineTableType.ARRAY -> emitter.startArray()
        }

        val isMultiline = multiline && inlineTableType == InlineTableType.ARRAY
        if (isMultiline) {
            emitter.indent()
        }
        tomlKeyValues.forEachIndexed { i, pair ->
            if (i > 0) {
                emitter.emitElementDelimiter()
            }
            if (isMultiline) {
                emitter.emitNewLine()
            } else {
                emitter.emitWhitespace()
            }

            pair.write(emitter, config)
        }
        if (isMultiline) {
            emitter.dedent()
        }

        if (!isMultiline) {
            emitter.emitWhitespace()
        }

        writeEnding(emitter, isMultiline)
    }

    private fun writeEnding(emitter: TomlEmitter, isMultiline: Boolean) {
        when (inlineTableType) {
            InlineTableType.PRIMITIVE -> emitter.endInlineTable()
            InlineTableType.ARRAY -> {
                if (isMultiline) {
                    emitter.emitNewLine()
                }
                emitter.emitIndent()
                    .endArray()
            }
        }
    }

    private fun createTableRoot(currentParentalNode: TomlNode): TomlTable = TomlTable(
        TomlKey(
            when (currentParentalNode) {
                is TomlTable -> currentParentalNode.fullTableKey.keyParts + key!!.keyParts
                is TomlArrayOfTablesElement -> (currentParentalNode.parent as TomlTable)
                    .fullTableKey.keyParts + key!!.keyParts
                // use the split key parts (a.b.c -> [a, b, c]) so that dotted inline-table keys
                // expand into nested tables; `name` would be the joined "a.b.c" single token.
                else -> key?.keyParts ?: listOf(name)
            },
        ),
        lineNo,
        type = if (this.isInlineArrayOfTables()) {
            TableType.ARRAY
        } else {
            TableType.PRIMITIVE
        },
        comments,
        inlineComment
    )

    private fun isInlineArrayOfTables(): Boolean = tomlKeyValues.any { it is TomlArrayOfTablesElement }

    public companion object {
        /**
         * Builds an inline table that is an element of an array of values (e.g. `[ {a = 1}, "b" ]`).
         * Such an element has no key of its own; its dotted keys are still expanded into nested
         * tables when the AST is converted/decoded.
         *
         * @param rawInlineTable the raw `{ ... }` string of the element
         * @param lineNo
         * @param config
         * @return a keyless [TomlInlineTable] for use inside a [com.akuleshov7.ktoml.tree.nodes.pairs.values.TomlArray]
         */
        internal fun parseArrayElement(
            rawInlineTable: String,
            lineNo: Int,
            config: TomlInputConfig
        ): TomlInlineTable = TomlInlineTable(
            key = null,
            tomlKeyValues = rawInlineTable.parseInlineTableValue("" to rawInlineTable, lineNo, config),
            inlineTableType = InlineTableType.PRIMITIVE,
            lineNo = lineNo,
        )

        private fun String.parseInlineTableValue(
            keyValuePair: Pair<String, String>,
            lineNo: Int,
            config: TomlInputConfig
        ): List<TomlNode> {
            if (this.startsWithIgnoreAllWhitespaces("[{")) {
                return parseInlineArrayOfTables(keyValuePair, lineNo, config)
            }

            val inlineTableContent = this.trimCurlyBraces().trim()
            // An empty inline table `{}` is valid, but a lone comma `{,}` is not — so only treat a
            // truly empty body as the empty table, before stripping the (TOML 1.1) trailing comma.
            // A stray comma elsewhere (e.g. `x=3,,y=4`) still leaves an empty element that fails.
            if (inlineTableContent.isEmpty()) {
                return listOf(TomlStubEmptyNode(lineNo))
            }
            val parsedList = inlineTableContent
                .removeTrailingComma()
                .trim()
                .splitInlineTableToKeyValue(config.allowEscapedQuotesInLiteralStrings, lineNo)
                .map {
                    it.parseTomlKeyValue(lineNo, comments = emptyList(), inlineComment = "", config)
                }

            return parsedList
        }

        /**
         * Returns true when this `[ ... ]` array is a pure array of inline tables (`[{..}, {..}]`),
         * as opposed to a mixed array that merely starts with an inline table (`[{..}, "b", 1]`).
         * Only the former is modelled as an array-of-tables; the latter is a normal array whose
         * elements happen to include inline tables.
         *
         * @return true if every top-level element of this array is an inline table
         */
        internal fun String.isArrayOfInlineTables(): Boolean {
            val elements = this.trim().splitInlineArrayOfTables()
            return elements.isNotEmpty() && elements.all { it.startsWithIgnoreAllWhitespaces("{") }
        }

        private fun String.parseInlineArrayOfTables(
            keyValuePair: Pair<String, String>,
            lineNo: Int,
            config: TomlInputConfig
        ): List<TomlNode> {
            val inlineTableValues = this.splitInlineArrayOfTables()

            return inlineTableValues.map { tableValue ->
                val inlineTableValues = tableValue.parseInlineTableValue(
                    keyValuePair.first to tableValue.trim(),
                    lineNo,
                    config,
                )

                TomlArrayOfTablesElement(lineNo, emptyList(), "").also { arrayOfTableElement ->
                    inlineTableValues.forEach { value ->
                        arrayOfTableElement.appendChild(value)
                    }
                }
            }
        }

        /**
         * Splits a `[ {..}, {..} ]` array of inline tables into its top-level `{..}` elements.
         *
         * A comma only separates elements when it sits at nesting depth zero (outside every `{}`,
         * `[]` and string). This is depth-aware, so an element may itself contain nested arrays or
         * inline tables (e.g. `{a.b = [{c.d = 1}]}`) without the inner `}`/`]` being mistaken for
         * an element boundary.
         */
        private fun String.splitInlineArrayOfTables(): List<String> {
            val clearedString = this
                .removePrefix("[")
                .removeSuffix("]")
            val result: MutableList<String> = mutableListOf()
            val current = StringBuilder()
            var openQuoteChar: Char? = null
            var depth = 0

            clearedString.forEach { currentChar ->
                when {
                    openQuoteChar != null -> if (currentChar == openQuoteChar) {
                        openQuoteChar = null
                    }
                    currentChar == '\"' || currentChar == '\'' -> openQuoteChar = currentChar
                    currentChar == '{' || currentChar == '[' -> depth++
                    currentChar == '}' || currentChar == ']' -> depth--
                    currentChar == ',' && depth == 0 -> {
                        // comma between top-level inline tables: flush the current element
                        result.add(current.toString().trim())
                        current.clear()
                        return@forEach
                    }
                    else -> {}
                }
                current.append(currentChar)
            }

            // 'current' is blank when array has a trailing comma
            if (current.isNotBlank()) {
                result.add(current.toString().trim())
            }
            return result
        }

        /**
         * That's basically split(",") function, but we ignore all commas inside arrays [ ],
         * nested tables { } and quotes " "/' '
         */
        @Suppress("TOO_LONG_FUNCTION")
        private fun String.splitInlineTableToKeyValue(
            allowEscapedQuotesInLiteralStrings: Boolean,
            lineNo: Int,
        ): List<String> {
            val clearedString = this.replaceEscaped(allowEscapedQuotesInLiteralStrings)
            val keyValueList: MutableList<String> = mutableListOf()
            var isLastAdded = false
            var currentQuoteChar: Char? = null
            var prevIdx = 0
            var curIdx = 0

            while (curIdx < clearedString.length) {
                val ch = clearedString[curIdx]
                if (ch == ',' && currentQuoteChar == null) {
                    keyValueList.add(this.substring(prevIdx, curIdx).trim())
                    prevIdx = curIdx + 1
                } else if (currentQuoteChar == null && (ch == '[' || ch == '{')) {
                    val closeBracketIdx = this.indexOfNextOutsideQuotes(
                        allowEscapedQuotesInLiteralStrings = allowEscapedQuotesInLiteralStrings,
                        searchChar = getCloseBracket(ch, lineNo),
                        startIndex = curIdx,
                    )
                    keyValueList.add(this.substring(prevIdx, closeBracketIdx + 1).trim())
                    val nextCommaIdx = this.indexOfNextOutsideQuotes(
                        allowEscapedQuotesInLiteralStrings = allowEscapedQuotesInLiteralStrings,
                        searchChar = ',',
                        startIndex = closeBracketIdx,
                    )
                    if (nextCommaIdx == -1) {
                        isLastAdded = true
                        break
                    }
                    prevIdx = nextCommaIdx + 1
                    curIdx = nextCommaIdx + 1
                } else if (ch == '\'' || ch == '\"') {
                    if (currentQuoteChar == null) {
                        currentQuoteChar = ch
                    } else if (currentQuoteChar == ch) {
                        currentQuoteChar = null
                    }
                }
                curIdx++
            }
            if (!isLastAdded) {
                keyValueList.add(this.substring(prevIdx, this.length).trim())
            }

            return keyValueList
        }

        private fun getCloseBracket(openBracket: Char, lineNo: Int): Char = if (openBracket == '[') {
            ']'
        } else if (openBracket == '{') {
            '}'
        } else {
            throw ParseException("Invalid open bracket: $openBracket, should never happen", lineNo)
        }
    }
}
