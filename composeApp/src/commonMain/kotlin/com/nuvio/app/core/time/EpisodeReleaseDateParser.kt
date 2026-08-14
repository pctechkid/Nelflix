package com.nuvio.app.core.time

private val IsoDateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val EmbeddedIsoDateRegex = Regex("""(?<!\d)\d{4}-\d{2}-\d{2}(?!\d)""")
private val ZonedIsoDateTimeRegex = Regex(
    """^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?(Z|[+-]\d{2}:?\d{2})$""",
)
private val LocalIsoDateTimeRegex = Regex(
    """^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?$""",
)

/**
 * Resolves the instant used by release notifications and Continue Watching badges.
 * Explicit offsets are authoritative. Zone-less values use the configured notification zone.
 * The product's nine-hour release delay is then added to the parsed instant exactly once.
 */
internal fun resolveScheduledEpisodeReleaseEpochMs(
    raw: String?,
    timezoneId: String?,
    dateOnlyHour: Int,
    dateOnlyMinute: Int,
    localDateTimeToEpochMs: (String, String?) -> Long? =
        EpisodeReleaseDatePlatform::localDateTimeToEpochMs,
): Long? = resolveEpisodeReleaseTiming(
    raw = raw,
    timezoneId = timezoneId,
    dateOnlyHour = dateOnlyHour,
    dateOnlyMinute = dateOnlyMinute,
    localDateTimeToEpochMs = localDateTimeToEpochMs,
)?.adjustedReleaseEpochMs

/**
 * Resolves the product's release clock in the device timezone.
 *
 * AIO Metadata marks its release clock with `Z`, while Nelflix presents that clock as local
 * wall time. Normalizing the clock before adding the business delay keeps the displayed time,
 * Continue Watching boundary, and OS notification trigger on the same instant.
 */
internal fun resolveDeviceLocalScheduledEpisodeReleaseEpochMs(
    raw: String?,
    dateOnlyHour: Int,
    dateOnlyMinute: Int,
    localDateTimeToEpochMs: (String, String?) -> Long? =
        EpisodeReleaseDatePlatform::localDateTimeToEpochMs,
): Long? = resolveDeviceLocalEpisodeReleaseTiming(
    raw = raw,
    dateOnlyHour = dateOnlyHour,
    dateOnlyMinute = dateOnlyMinute,
    localDateTimeToEpochMs = localDateTimeToEpochMs,
)?.adjustedReleaseEpochMs

internal data class EpisodeReleaseTiming(
    val rawReleaseValue: String,
    val parsedRawReleaseEpochMs: Long,
    val adjustedReleaseEpochMs: Long,
)

internal fun resolveEpisodeReleaseTiming(
    raw: String?,
    timezoneId: String?,
    dateOnlyHour: Int,
    dateOnlyMinute: Int,
    localDateTimeToEpochMs: (String, String?) -> Long? =
        EpisodeReleaseDatePlatform::localDateTimeToEpochMs,
): EpisodeReleaseTiming? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val parsedRawReleaseEpochMs = parseZonedIsoDateTimeToEpochMs(value)
        ?: parseLocalIsoDateTime(value)?.let { normalized ->
            localDateTimeToEpochMs(normalized, timezoneId)
        }
        ?: run {
            val date = parseIsoCalendarDate(value)
                ?: EmbeddedIsoDateRegex.find(value)?.value?.let(::parseIsoCalendarDate)
                ?: return null
            val normalized = buildString {
                append(date)
                append('T')
                append(dateOnlyHour.coerceIn(0, 23).toString().padStart(2, '0'))
                append(':')
                append(dateOnlyMinute.coerceIn(0, 59).toString().padStart(2, '0'))
                append(":00.000")
            }
            localDateTimeToEpochMs(normalized, timezoneId)
        }
        ?: return null
    if (parsedRawReleaseEpochMs > Long.MAX_VALUE - EpisodeReleaseBusinessDelayMs) {
        return null
    }
    return EpisodeReleaseTiming(
        rawReleaseValue = value,
        parsedRawReleaseEpochMs = parsedRawReleaseEpochMs,
        adjustedReleaseEpochMs = parsedRawReleaseEpochMs + EpisodeReleaseBusinessDelayMs,
    )
}

internal fun resolveDeviceLocalEpisodeReleaseTiming(
    raw: String?,
    dateOnlyHour: Int,
    dateOnlyMinute: Int,
    localDateTimeToEpochMs: (String, String?) -> Long? =
        EpisodeReleaseDatePlatform::localDateTimeToEpochMs,
): EpisodeReleaseTiming? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val normalizedLocalDateTime = parseReleaseWallClockDateTime(value)
        ?: run {
            val date = parseIsoCalendarDate(value)
                ?: EmbeddedIsoDateRegex.find(value)?.value?.let(::parseIsoCalendarDate)
                ?: return null
            buildString {
                append(date)
                append('T')
                append(dateOnlyHour.coerceIn(0, 23).toString().padStart(2, '0'))
                append(':')
                append(dateOnlyMinute.coerceIn(0, 59).toString().padStart(2, '0'))
                append(":00.000")
            }
        }
    val parsedRawReleaseEpochMs = localDateTimeToEpochMs(
        normalizedLocalDateTime,
        null,
    ) ?: return null
    if (parsedRawReleaseEpochMs > Long.MAX_VALUE - EpisodeReleaseBusinessDelayMs) {
        return null
    }
    return EpisodeReleaseTiming(
        rawReleaseValue = value,
        parsedRawReleaseEpochMs = parsedRawReleaseEpochMs,
        adjustedReleaseEpochMs = parsedRawReleaseEpochMs + EpisodeReleaseBusinessDelayMs,
    )
}

internal fun parseEpisodeReleaseEpochMs(raw: String?): Long? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return parseZonedIsoDateTimeToEpochMs(value)
        ?: parseIsoCalendarDate(value)?.let(::isoDateAtUtcMidnightEpochMs)
        ?: EmbeddedIsoDateRegex.find(value)?.value
            ?.let(::parseIsoCalendarDate)
            ?.let(::isoDateAtUtcMidnightEpochMs)
}

private fun parseLocalIsoDateTime(value: String): String? {
    val match = LocalIsoDateTimeRegex.matchEntire(value.replace(' ', 'T')) ?: return null
    return match.dateTimeComponentsOrNull()?.normalizedLocalDateTime()
}

private fun parseReleaseWallClockDateTime(value: String): String? {
    val normalized = value.replace(' ', 'T')
    val match = ZonedIsoDateTimeRegex.matchEntire(normalized)
        ?: LocalIsoDateTimeRegex.matchEntire(normalized)
        ?: return null
    return match.dateTimeComponentsOrNull()?.normalizedLocalDateTime()
}

private fun parseZonedIsoDateTimeToEpochMs(value: String): Long? {
    val match = ZonedIsoDateTimeRegex.matchEntire(value.replace(' ', 'T')) ?: return null
    val components = match.dateTimeComponentsOrNull() ?: return null
    val offsetMs = parseOffsetMs(match.groupValues[8]) ?: return null
    return components.toUtcEpochMs() - offsetMs
}

private fun parseIsoCalendarDate(value: String?): String? {
    val date = value?.trim()?.takeIf(IsoDateRegex::matches) ?: return null
    val year = date.substring(0, 4).toIntOrNull() ?: return null
    val month = date.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val day = date.substring(8, 10).toIntOrNull() ?: return null
    if (day !in 1..daysInMonth(year, month)) return null
    return date
}

private data class DateTimeComponents(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val millisecond: Int,
) {
    fun toUtcEpochMs(): Long =
        isoEpochDay(year, month, day) * MillisPerDay +
            hour * MillisPerHour +
            minute * MillisPerMinute +
            second * MillisPerSecond +
            millisecond

    fun normalizedLocalDateTime(): String =
        year.toString().padStart(4, '0') + "-" +
            month.toString().padStart(2, '0') + "-" +
            day.toString().padStart(2, '0') + "T" +
            hour.toString().padStart(2, '0') + ":" +
            minute.toString().padStart(2, '0') + ":" +
            second.toString().padStart(2, '0') + "." +
            millisecond.toString().padStart(3, '0')
}

private fun MatchResult.dateTimeComponentsOrNull(): DateTimeComponents? {
    val year = groupValues[1].toIntOrNull() ?: return null
    val month = groupValues[2].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val day = groupValues[3].toIntOrNull() ?: return null
    val hour = groupValues[4].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val minute = groupValues[5].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    val second = groupValues[6]
        .takeIf(String::isNotEmpty)
        ?.toIntOrNull()
        ?.takeIf { it in 0..59 }
        ?: 0
    if (day !in 1..daysInMonth(year, month)) return null
    val millisecond = groupValues[7]
        .takeIf(String::isNotEmpty)
        ?.padEnd(3, '0')
        ?.take(3)
        ?.toIntOrNull()
        ?: 0
    return DateTimeComponents(year, month, day, hour, minute, second, millisecond)
}

private fun parseOffsetMs(value: String): Long? {
    if (value == "Z") return 0L
    val sign = when (value.firstOrNull()) {
        '+' -> 1L
        '-' -> -1L
        else -> return null
    }
    val digits = value.drop(1).replace(":", "")
    if (digits.length != 4) return null
    val hours = digits.take(2).toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val minutes = digits.drop(2).toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return sign * (hours * MillisPerHour + minutes * MillisPerMinute)
}

private fun isoDateAtUtcMidnightEpochMs(date: String): Long =
    isoEpochDay(
        year = date.substring(0, 4).toInt(),
        month = date.substring(5, 7).toInt(),
        day = date.substring(8, 10).toInt(),
    ) * MillisPerDay

private fun isoEpochDay(year: Int, month: Int, day: Int): Long {
    val adjustedYear = year.toLong() - if (month <= 2) 1L else 0L
    val era = if (adjustedYear >= 0L) adjustedYear / 400L else (adjustedYear - 399L) / 400L
    val yearOfEra = adjustedYear - era * 400L
    val adjustedMonth = month.toLong() + if (month > 2) -3L else 9L
    val dayOfYear = (153L * adjustedMonth + 2L) / 5L + day - 1L
    val dayOfEra = yearOfEra * 365L + yearOfEra / 4L - yearOfEra / 100L + dayOfYear
    return era * 146_097L + dayOfEra - 719_468L
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

private const val MillisPerSecond = 1_000L
private const val MillisPerMinute = 60L * MillisPerSecond
private const val MillisPerHour = 60L * MillisPerMinute
private const val MillisPerDay = 24L * MillisPerHour
internal const val EpisodeReleaseBusinessDelayHours = 9L
internal const val EpisodeReleaseBusinessDelayMs = EpisodeReleaseBusinessDelayHours * MillisPerHour
