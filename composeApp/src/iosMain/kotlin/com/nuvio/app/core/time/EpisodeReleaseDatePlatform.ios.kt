package com.nuvio.app.core.time

import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.timeIntervalSince1970

internal actual object EpisodeReleaseDatePlatform {
    actual fun localDateTimeToEpochMs(
        normalizedIsoDateTime: String,
        timezoneId: String?,
    ): Long? {
        val formatter = NSDateFormatter().apply {
            dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS"
            timezoneId
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(NSTimeZone::timeZoneWithName)
                ?.let { timeZone = it }
        }
        return formatter.dateFromString(normalizedIsoDateTime)
            ?.timeIntervalSince1970
            ?.times(1_000.0)
            ?.toLong()
    }
}
