/**
 * This file contains datetime-related AST node types for TOML parsing.
 *
 * ktoml-core does NOT depend on a date library. Date-time literals are validated by ktoml itself and
 * kept as raw text in the AST; converting them into concrete types (`kotlin.time.Instant`,
 * `kotlinx.datetime.LocalDate`/`LocalDateTime`/`LocalTime`) happens later, at the serialization layer,
 * via the serializers those libraries already provide. See [com.akuleshov7.ktoml.decoders.TomlAbstractDecoder]
 * and [com.akuleshov7.ktoml.encoders.TomlAbstractEncoder].
 */

package com.akuleshov7.ktoml.tree.nodes.pairs.values

import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.exceptions.ParseException
import com.akuleshov7.ktoml.exceptions.TomlWritingException
import com.akuleshov7.ktoml.writers.TomlEmitter

private const val DAYS_IN_LONG_MONTH = 31
private const val DAYS_IN_SHORT_MONTH = 30
private const val DAYS_IN_FEBRUARY = 28
private const val DAYS_IN_FEBRUARY_LEAP = 29
private const val MAX_MONTH = 12
private const val MAX_HOUR = 23
private const val MAX_MINUTE = 59

// TOML allows a leap second, so seconds run 00-60 (not 00-59).
private const val MAX_SECOND = 60

// Months with 30 days, plus February, used by the day-of-month check.
private const val FEBRUARY = 2
private const val APRIL = 4
private const val JUNE = 6
private const val SEPTEMBER = 9
private const val NOVEMBER = 11

// Gregorian leap-year rule divisors.
private const val LEAP_YEAR_DIVISOR = 4
private const val CENTURY_DIVISOR = 100
private const val LEAP_CENTURY_DIVISOR = 400

/**
 * An offset date-time literal, preserved as its original (normalized) text. Converted to
 * `kotlin.time.Instant` at the serialization layer.
 *
 * @property raw the normalized TOML offset date-time text (e.g. `1979-05-27T07:32:00Z`)
 */
public data class TomlOffsetDateTime(public val raw: String) {
    override fun toString(): String = raw
}

/**
 * A local date-time literal (no offset), preserved as its original (normalized) text. Converted to
 * `kotlinx.datetime.LocalDateTime` at the serialization layer.
 *
 * @property raw the normalized TOML local date-time text (e.g. `1979-05-27T07:32:00`)
 */
public data class TomlLocalDateTime(public val raw: String) {
    override fun toString(): String = raw
}

/**
 * A local date literal, preserved as its original text. Converted to `kotlinx.datetime.LocalDate` at
 * the serialization layer.
 *
 * @property raw the TOML local date text (e.g. `1979-05-27`)
 */
public data class TomlLocalDate(public val raw: String) {
    override fun toString(): String = raw
}

/**
 * A local time literal, preserved as its normalized text. Converted to `kotlinx.datetime.LocalTime` at
 * the serialization layer.
 *
 * @property raw the normalized TOML local time text (e.g. `07:32:00`)
 */
public data class TomlLocalTime(public val raw: String) {
    override fun toString(): String = raw
}

/**
 * Toml AST Node for a representation of date-time types (offset date-time, local date-time, local date, local time).
 * Its [content] holds one of [TomlOffsetDateTime], [TomlLocalDateTime], [TomlLocalDate] or [TomlLocalTime].
 * @property content
 */
public class TomlDateTime
internal constructor(
    override var content: Any
) : TomlValue() {
    public constructor(content: String, lineNo: Int) : this(content.trim().parseToDateTime(lineNo))

    override fun write(
        emitter: TomlEmitter,
        config: TomlOutputConfig
    ) {
        when (val content = content) {
            is TomlOffsetDateTime -> emitter.emitValue(content)
            is TomlLocalDateTime -> emitter.emitValue(content)
            is TomlLocalDate -> emitter.emitValue(content)
            is TomlLocalTime -> emitter.emitValue(content)
            else ->
                throw TomlWritingException(
                    "Unknown date type ${content::class.simpleName} of <$content>"
                )
        }
    }

    public companion object {
        /** Serial names of the date-time serializers ktoml bridges to (see decoder/encoder). */
        public const val INSTANT_SERIAL_NAME: String = "kotlin.time.Instant"
        public const val LOCAL_DATE_SERIAL_NAME: String = "kotlinx.datetime.LocalDate"
        public const val LOCAL_DATE_TIME_SERIAL_NAME: String = "kotlinx.datetime.LocalDateTime"
        public const val LOCAL_TIME_SERIAL_NAME: String = "kotlinx.datetime.LocalTime"

        // Regexes that classify a date-time literal by shape (syntax only — ranges are checked below).
        private val dateTimeLikeRegex = Regex("""^(?:\d{4}-\d{2}-\d{2}.*|\d{2}:\d{2}.*)$""")
        private val localDateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")
        private val localTimeRegex = Regex("""^\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$""")
        private val localDateTimeRegex = Regex(
            """^\d{4}-\d{2}-\d{2}[Tt ]\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$"""
        )
        private val offsetDateTimeRegex = Regex(
            """^\d{4}-\d{2}-\d{2}[Tt ]\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?(?:[Zz]|[+-]\d{2}:\d{2})$"""
        )

        /**
         * Classifies [this] date-time literal, validates it semantically (calendar/time/offset ranges)
         * using only ktoml's own code, and returns the matching raw holder. A syntactically date-time-like
         * but invalid literal throws a [ParseException]; anything that is not a date-time throws
         * [IllegalArgumentException] so the caller falls back to a string value.
         */
        private fun String.parseToDateTime(lineNo: Int): Any = when {
            offsetDateTimeRegex.matches(this) -> {
                val normalized = normalizeDateTime()
                validateOffsetDateTime(normalized, lineNo)
                TomlOffsetDateTime(normalized)
            }
            localDateTimeRegex.matches(this) -> {
                val normalized = normalizeDateTime()
                validateLocalDateTime(normalized, lineNo)
                TomlLocalDateTime(normalized)
            }
            localDateRegex.matches(this) -> {
                validateDate(this, this, lineNo)
                TomlLocalDate(this)
            }
            localTimeRegex.matches(this) -> {
                val normalized = padLocalTimeSeconds()
                validateTime(normalized, normalized, lineNo)
                TomlLocalTime(normalized)
            }
            // Looks like a date-time (per the spec shape) but matched no valid form -> reject.
            dateTimeLikeRegex.matches(this) ->
                throw ParseException("Invalid TOML date-time literal: <$this>", lineNo)
            // Not a date-time at all -> let the caller fall back to a string value.
            else -> throw IllegalArgumentException("Not a date-time literal: <$this>")
        }

        /**
         * Normalizes an offset/local date-time to canonical form: the date-time separator becomes `T`,
         * a lowercase `z` offset becomes `Z`, and an omitted seconds component is padded with `:00`
         * (TOML 1.1 allows `HH:MM`). Matches what the previous `LocalX.parse().toString()` produced.
         */
        private fun String.normalizeDateTime(): String =
            replaceFirst(' ', 'T')
                .replaceFirst('t', 'T')
                .replaceFirst('z', 'Z')
                .padDateTimeSeconds()

        /**
         * Inserts the seconds component (`:00`) into the time part of a date-time when it is omitted.
         * Strings whose time already carries seconds are returned unchanged.
         */
        private fun String.padDateTimeSeconds(): String {
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

        /** Pads an omitted seconds component (`HH:MM` -> `HH:MM:00`) in a bare local time. */
        private fun String.padLocalTimeSeconds(): String =
            if (count { it == ':' } == 1) "$this:00" else this

        private fun validateOffsetDateTime(normalized: String, lineNo: Int) {
            val timeAndOffset = normalized.substringAfter('T')
            val time = timeAndOffset.takeWhile { it != 'Z' && it != '+' && it != '-' }
            validateDate(normalized.substringBefore('T'), normalized, lineNo)
            validateTime(time, normalized, lineNo)
            validateOffset(timeAndOffset.substring(time.length), normalized, lineNo)
        }

        private fun validateLocalDateTime(normalized: String, lineNo: Int) {
            validateDate(normalized.substringBefore('T'), normalized, lineNo)
            validateTime(normalized.substringAfter('T'), normalized, lineNo)
        }

        /** Validates a `YYYY-MM-DD` [date]: month `01..12` and day within the month. */
        private fun validateDate(
            date: String,
            full: String,
            lineNo: Int
        ) {
            val parts = date.split('-')
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val day = parts[2].toInt()
            if (month !in 1..MAX_MONTH) {
                throw ParseException("Invalid month <$month> in date-time literal: <$full>", lineNo)
            }
            if (day !in 1..daysInMonth(year, month)) {
                throw ParseException("Invalid day <$day> in date-time literal: <$full>", lineNo)
            }
        }

        /**
         * Validates a (seconds-padded) `HH:MM:SS(.fff)?` [time]: hour `00..23`, minute `00..59`,
         * second `00..60` (a leap second is allowed). [full] is the original literal, for diagnostics.
         */
        private fun validateTime(
            time: String,
            full: String,
            lineNo: Int
        ) {
            val segments = time.split(':')
            val hour = segments[0].toInt()
            val minute = segments[1].toInt()
            val second = segments[2].substringBefore('.').toInt()
            if (hour > MAX_HOUR) {
                throw ParseException("Invalid hour <$hour> in date-time literal: <$full>", lineNo)
            }
            if (minute > MAX_MINUTE) {
                throw ParseException("Invalid minute <$minute> in date-time literal: <$full>", lineNo)
            }
            if (second > MAX_SECOND) {
                throw ParseException("Invalid second <$second> in date-time literal: <$full>", lineNo)
            }
        }

        /** Validates an offset suffix: `Z`, or `±HH:MM` with hour `00..23` and minute `00..59`. */
        private fun validateOffset(
            offset: String,
            full: String,
            lineNo: Int
        ) {
            if (offset == "Z") {
                return
            }
            // Drop the leading sign, then split into hour and minute.
            val parts = offset.drop(1).split(':')
            val offsetHour = parts[0].toInt()
            val offsetMinute = parts[1].toInt()
            if (offsetHour > MAX_HOUR) {
                throw ParseException("Invalid offset hour <$offsetHour> in date-time literal: <$full>", lineNo)
            }
            if (offsetMinute > MAX_MINUTE) {
                throw ParseException("Invalid offset minute <$offsetMinute> in date-time literal: <$full>", lineNo)
            }
        }

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            FEBRUARY -> if (isLeapYear(year)) DAYS_IN_FEBRUARY_LEAP else DAYS_IN_FEBRUARY
            APRIL, JUNE, SEPTEMBER, NOVEMBER -> DAYS_IN_SHORT_MONTH
            else -> DAYS_IN_LONG_MONTH
        }

        private fun isLeapYear(year: Int): Boolean =
            (year % LEAP_YEAR_DIVISOR == 0 && year % CENTURY_DIVISOR != 0) ||
                    year % LEAP_CENTURY_DIVISOR == 0
    }
}
