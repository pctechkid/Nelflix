package com.nuvio.app.features.watchprogress

private const val MillisPerDay = 24L * 60L * 60L * 1000L
private const val ReleaseAlertWindowMs = 60L * MillisPerDay

class ReleaseAlertState(
    val isReleaseAlert: Boolean,
    val isNewSeasonRelease: Boolean,
)

fun calculateReleaseAlertState(
    seedLastUpdatedEpochMs: Long,
    seedSeasonNumber: Int?,
    nextSeasonNumber: Int?,
    releasedIso: String?,
): ReleaseAlertState {
    val releaseEpoch = parseReleaseDateToEpochMs(releasedIso)
        ?: return ReleaseAlertState(isReleaseAlert = false, isNewSeasonRelease = false)
    val nowMs = WatchProgressClock.nowEpochMs()
    val hasAired = nowMs >= releaseEpoch
    val isReleaseAlert = hasAired &&
        releaseEpoch > seedLastUpdatedEpochMs &&
        nowMs - releaseEpoch < ReleaseAlertWindowMs
    val isNewSeasonRelease = isReleaseAlert &&
        seedSeasonNumber != null &&
        nextSeasonNumber != null &&
        nextSeasonNumber != seedSeasonNumber

    return ReleaseAlertState(
        isReleaseAlert = isReleaseAlert,
        isNewSeasonRelease = isNewSeasonRelease,
    )
}

fun parseReleaseDateToEpochMs(raw: String?): Long? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val date = trimmed.substringBefore('T').substringBefore(' ')
    if (date.length < 10) return null
    if (date.getOrNull(4) != '-' || date.getOrNull(7) != '-') return null
    val year = date.substring(0, 4).toIntOrNull()?.takeIf { it in 1000..9999 } ?: return null
    val month = date.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val day = date.substring(8, 10).toIntOrNull()?.takeIf { it in 1..daysInMonth(year, month) } ?: return null
    return epochDay(year = year, month = month, day = day) * MillisPerDay
}

private fun epochDay(year: Int, month: Int, day: Int): Long {
    var adjustedYear = year
    adjustedYear -= if (month <= 2) 1 else 0
    val era = adjustedYear / 400
    val yearOfEra = adjustedYear - era * 400
    val monthForYear = month + if (month > 2) -3 else 9
    val dayOfYear = (153 * monthForYear + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146097L + dayOfEra - 719468L
}

private fun daysInMonth(year: Int, month: Int): Int =
    when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

private fun isLeapYear(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
