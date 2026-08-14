package com.nuvio.app.core.time

import java.time.LocalDateTime
import java.time.ZoneId

internal actual object EpisodeReleaseDatePlatform {
    actual fun localDateTimeToEpochMs(
        normalizedIsoDateTime: String,
        timezoneId: String?,
    ): Long? = runCatching {
        val zoneId = timezoneId
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let(ZoneId::of)
            ?: ZoneId.systemDefault()
        LocalDateTime.parse(normalizedIsoDateTime)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}
