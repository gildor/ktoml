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
    public constructor(content: String, lineNo: Int) : this(content.parse())

    public constructor(content: Double, lineNo: Int) : this(content)

    override fun write(
        emitter: TomlEmitter,
        config: TomlOutputConfig
    ) {
        emitter.emitValue(content as Double)
    }

    private companion object {
        /**
         * Parses a TOML float literal into a [Double], supporting `_` digit separators in the
         * integer, fractional and exponent parts (e.g. `224_617.445_991_228`, `3e1_4`).
         *
         * Underscores are only allowed between two digits. A leading, trailing or doubled `_`,
         * or one adjacent to `.`/`e`/sign, is rejected with a [NumberFormatException] so that
         * invalid literals keep being treated as non-floats by the caller.
         */
        private fun String.parse(): Double {
            if (any { it == '_' } && !isValidUnderscorePlacement()) {
                throw NumberFormatException("Invalid underscore placement in float <$this>")
            }
            return replace("_", "").toDouble()
        }

        private fun String.isValidUnderscorePlacement(): Boolean =
            indices.none { index ->
                this[index] == '_' &&
                        !(index > 0 && this[index - 1].isDigit() &&
                                index < lastIndex && this[index + 1].isDigit())
            }
    }
}
