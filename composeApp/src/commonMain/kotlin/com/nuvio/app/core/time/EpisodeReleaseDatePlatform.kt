package com.nuvio.app.core.time

internal expect object EpisodeReleaseDatePlatform {
    fun localDateTimeToEpochMs(
        normalizedIsoDateTime: String,
        timezoneId: String?,
    ): Long?
}
