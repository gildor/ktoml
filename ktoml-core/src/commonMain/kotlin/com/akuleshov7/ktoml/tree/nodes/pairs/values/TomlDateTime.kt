/**
 * This file contains datetime-related AST node types for TOML parsing.
 */

package com.akuleshov7.ktoml.tree.nodes.pairs.values

import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.exceptions.TomlWritingException
import com.akuleshov7.ktoml.writers.TomlEmitter
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

private const val FRACTIONAL_SECOND_PRECISION = 3

/**
 * Preserves the original textual representation of an offset date-time while still exposing the parsed [instant].
 *
 * @property raw original TOML date-time text
 * @property instant parsed instant value
 */
@OptIn(ExperimentalTime::class)
public data class TomlOffsetDateTime(
    public val raw: String,
    public val instant: Instant,
) {
    override fun toString(): String = raw

    internal fun toRfc3339String(): String {
        val normalized = raw
            .replaceFirst(' ', 'T')
            .replaceFirst('t', 'T')
            .replaceFirst('z', 'Z')
        val timeStart = normalized.indexOf('T')
        if (timeStart == -1) {
            return normalized
        }

        val offsetStart = normalized.indexOfAny(charArrayOf('Z', '+', '-'), startIndex = timeStart + 1)
        if (offsetStart == -1) {
            return normalized
        }

        var timePart = normalized.substring(timeStart + 1, offsetStart)

        // Pad seconds if omitted (TOML 1.1 allows HH:MM without seconds)
        if (timePart.count { it == ':' } == 1) {
            timePart += ":00"
        }

        val dotIndex = timePart.indexOf('.')
        if (dotIndex != -1) {
            val fraction = timePart.substring(dotIndex + 1)
            if (fraction.length < FRACTIONAL_SECOND_PRECISION) {
                timePart = timePart.substring(0, dotIndex + 1) + fraction.padEnd(FRACTIONAL_SECOND_PRECISION, '0')
            }
        }

        return buildString {
            append(normalized.substring(0, timeStart + 1))
            append(timePart)
            append(normalized.substring(offsetStart))
        }
    }
}

/**
 * Toml AST Node for a representation of date-time types (offset date-time, local date-time, local date, local time)
 * @property content
 */
public class TomlDateTime
internal constructor(
    override var content: Any
) : TomlValue() {
    public constructor(content: String, lineNo: Int) : this(content.trim().parseToDateTime())

    override fun write(
        emitter: TomlEmitter,
        config: TomlOutputConfig
    ) {
        @OptIn(ExperimentalTime::class)
        when (val content = content) {
            is Instant -> emitter.emitValue(content)
            is TomlOffsetDateTime -> emitter.emitValue(content)
            is LocalDateTime -> emitter.emitValue(content)
            is LocalDate -> emitter.emitValue(content)
            is LocalTime -> emitter.emitValue(content)
            else ->
                throw TomlWritingException(
                    "Unknown date type ${content::class.simpleName} of <$content>"
                )
        }
    }

    public companion object {
        @OptIn(ExperimentalTime::class)
        private fun String.parseToDateTime(): Any {
            // TOML spec allows a space instead of the T, and case-insensitive t/z.
            // TOML 1.1 makes the seconds component optional, so pad an omitted `:SS` with `:00`.
            val normalized = this
                .replaceFirst(' ', 'T')
                .replaceFirst('t', 'T')
                .replaceFirst('z', 'Z')
                .padOffsetSeconds()
            return try {
                // Offset date-time
                val instant = Instant.parse(normalized)
                if (normalized.hasExplicitOffset()) {
                    TomlOffsetDateTime(this, instant)
                } else {
                    instant
                }
            } catch (e: IllegalArgumentException) {
                try {
                    // Local date-time
                    LocalDateTime.parse(normalized)
                } catch (e: IllegalArgumentException) {
                    try {
                        // Local date
                        LocalDate.parse(normalized)
                    } catch (e: IllegalArgumentException) {
                        // Local time
                        LocalTime.parse(normalized)
                    }
                }
            }
        }

        private fun String.hasExplicitOffset(): Boolean {
            val timeStart = indexOf('T')
            if (timeStart == -1) {
                return false
            }
            return indexOfAny(charArrayOf('+', '-', 'Z'), startIndex = timeStart + 1) != -1
        }

        /**
         * Inserts the seconds component (`:00`) into the time part of an offset date-time when it
         * is omitted (TOML 1.1 allows `HH:MM`). Strings without a `T` time component, or whose time
         * already carries seconds, are returned unchanged.
         */
        private fun String.padOffsetSeconds(): String {
            val timeStart = indexOf('T')
            if (timeStart == -1) {
                return this
            }
            val offsetStart = indexOfAny(charArrayOf('Z', '+', '-'), startIndex = timeStart + 1)
            val timeEnd = if (offsetStart == -1) length else offsetStart
            val timePart = substring(timeStart + 1, timeEnd)
            // `HH:MM` has a single colon; `HH:MM:SS` (optionally with a fraction) has two.
            if (timePart.count { it == ':' } != 1) {
                return this
            }
            return substring(0, timeEnd) + ":00" + substring(timeEnd)
        }
    }
}
