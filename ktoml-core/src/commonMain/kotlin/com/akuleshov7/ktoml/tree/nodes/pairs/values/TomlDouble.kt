package com.akuleshov7.ktoml.tree.nodes.pairs.values

import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.writers.TomlEmitter

/**
 * Toml AST Node for a representation of float types: key = 1.01.
 * Toml specification requires floating point numbers to be IEEE 754 binary64 values,
 * so it should be Kotlin Double (64 bits)
 * @property content
 */
public class TomlDouble
internal constructor(
    override var content: Any
) : TomlValue() {
    public constructor(content: String, lineNo: Int) : this(content.validateUnderscores().toDouble())

    public constructor(content: Double, lineNo: Int) : this(content)

    override fun write(
        emitter: TomlEmitter,
        config: TomlOutputConfig
    ) {
        emitter.emitValue(content as Double)
    }

    private companion object {
        /**
         * Validates TOML underscore rules and strips underscores.
         * Underscores must be surrounded by digits on both sides.
         */
        private fun String.validateUnderscores(): String {
            if ('_' !in this) return this
            val len = length
            for (i in indices) {
                if (this[i] == '_') {
                    if (i == 0 || i == len - 1) {
                        throw NumberFormatException("Invalid underscore in float: $this")
                    }
                    val prev = this[i - 1]
                    val next = this[i + 1]
                    if (!prev.isDigit() || !next.isDigit()) {
                        throw NumberFormatException("Invalid underscore in float: $this")
                    }
                }
            }
            return replace("_", "")
        }
    }
}
