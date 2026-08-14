package com.nuvio.app.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeReleaseDateParserTest {
    @Test
    fun `AIO release clock is resolved in the device timezone before adding nine hours`() {
        var capturedDateTime: String? = null
        var capturedTimezone: String? = "not-called"

        val timing = resolveDeviceLocalEpisodeReleaseTiming(
            raw = "2026-07-27T13:00:00.000Z",
            dateOnlyHour = 9,
            dateOnlyMinute = 0,
            localDateTimeToEpochMs = { dateTime, timezone ->
                capturedDateTime = dateTime
                capturedTimezone = timezone
                1_000L
            },
        )

        assertEquals("2026-07-27T13:00:00.000", capturedDateTime)
        assertNull(capturedTimezone)
        assertEquals(1_000L, timing?.parsedRawReleaseEpochMs)
        assertEquals(1_000L + EpisodeReleaseBusinessDelayMs, timing?.adjustedReleaseEpochMs)
    }

    @Test
    fun `device-local notification and chip boundary use the same adjusted epoch`() {
        val adjustedEpochMs = resolveDeviceLocalScheduledEpisodeReleaseEpochMs(
            raw = "2026-07-27T13:00:00.000Z",
            dateOnlyHour = 9,
            dateOnlyMinute = 0,
            localDateTimeToEpochMs = { _, _ -> 10_000L },
        )

        assertEquals(10_000L + EpisodeReleaseBusinessDelayMs, adjustedEpochMs)
    }

    @Test
    fun `minute precision AIO release clock does not fall back to date only six pm`() {
        var capturedDateTime: String? = null

        val adjustedEpochMs = resolveDeviceLocalScheduledEpisodeReleaseEpochMs(
            raw = "2026-07-27T14:30Z",
            dateOnlyHour = 9,
            dateOnlyMinute = 0,
            localDateTimeToEpochMs = { dateTime, _ ->
                capturedDateTime = dateTime
                20_000L
            },
        )

        assertEquals("2026-07-27T14:30:00.000", capturedDateTime)
        assertEquals(20_000L + EpisodeReleaseBusinessDelayMs, adjustedEpochMs)
    }

    @Test
    fun `raw noon receives exactly nine elapsed hours`() {
        val timing = resolveEpisodeReleaseTiming(
            raw = "2026-07-27T12:00:00Z",
            timezoneId = "Asia/Manila",
            dateOnlyHour = 9,
            dateOnlyMinute = 0,
        )

        assertEquals(1_785_153_600_000L, timing?.parsedRawReleaseEpochMs)
        assertEquals(1_785_186_000_000L, timing?.adjustedReleaseEpochMs)
        assertEquals(
            EpisodeReleaseBusinessDelayMs,
            timing!!.adjustedReleaseEpochMs - timing.parsedRawReleaseEpochMs,
        )
    }

    @Test
    fun `raw one pm becomes ten pm`() {
        assertEquals(
            1_785_189_600_000L,
            resolveScheduledEpisodeReleaseEpochMs(
                raw = "2026-07-27T13:00:00Z",
                timezoneId = "UTC",
                dateOnlyHour = 9,
                dateOnlyMinute = 0,
            ),
        )
    }

    @Test
    fun `raw eight pm rolls into five am the next day`() {
        assertEquals(
            1_785_214_800_000L,
            resolveScheduledEpisodeReleaseEpochMs(
                raw = "2026-07-27T20:00:00Z",
                timezoneId = "UTC",
                dateOnlyHour = 9,
                dateOnlyMinute = 0,
            ),
        )
    }

    @Test
    fun `nine-hour adjustment handles month and year rollover`() {
        assertEquals(
            1_785_560_400_000L,
            resolveScheduledEpisodeReleaseEpochMs(
                raw = "2026-07-31T20:00:00Z",
                timezoneId = "UTC",
                dateOnlyHour = 9,
                dateOnlyMinute = 0,
            ),
        )
        assertEquals(
            1_798_779_600_000L,
            resolveScheduledEpisodeReleaseEpochMs(
                raw = "2026-12-31T20:00:00Z",
                timezoneId = "UTC",
                dateOnlyHour = 9,
                dateOnlyMinute = 0,
            ),
        )
    }

    @Test
    fun `explicit source offset is authoritative regardless of device zone`() {
        val manilaResult = resolveScheduledEpisodeReleaseEpochMs(
            raw = "2026-07-27T12:00:00+02:00",
            timezoneId = "Asia/Manila",
            dateOnlyHour = 9,
            dateOnlyMinute = 0,
        )
        val losAngelesResult = resolveScheduledEpisodeReleaseEpochMs(
            raw = "2026-07-27T12:00:00+02:00",
            timezoneId = "America/Los_Angeles",
            dateOnlyHour = 9,
            dateOnlyMinute = 0,
        )

        assertEquals(manilaResult, losAngelesResult)
        assertEquals(1_785_178_800_000L, manilaResult)
    }

    @Test
    fun `local timestamps use source zone before adding elapsed delay`() {
        var capturedDateTime: String? = null
        var capturedTimezone: String? = null

        val timing = resolveEpisodeReleaseTiming(
            raw = "2026-11-01 01:30:00",
            timezoneId = "America/New_York",
            dateOnlyHour = 9,
            dateOnlyMinute = 0,
            localDateTimeToEpochMs = { dateTime, timezone ->
                capturedDateTime = dateTime
                capturedTimezone = timezone
                1_793_511_000_000L
            },
        )

        assertEquals("2026-11-01T01:30:00.000", capturedDateTime)
        assertEquals("America/New_York", capturedTimezone)
        assertEquals(1_793_511_000_000L, timing?.parsedRawReleaseEpochMs)
        assertEquals(1_793_543_400_000L, timing?.adjustedReleaseEpochMs)
    }

    @Test
    fun `date-only releases adjust the configured notification base time`() {
        var capturedDateTime: String? = null
        var capturedTimezone: String? = null

        val result = resolveScheduledEpisodeReleaseEpochMs(
            raw = "2026-07-26",
            timezoneId = "Asia/Manila",
            dateOnlyHour = 9,
            dateOnlyMinute = 30,
            localDateTimeToEpochMs = { dateTime, timezone ->
                capturedDateTime = dateTime
                capturedTimezone = timezone
                1_000L
            },
        )

        assertEquals(1_000L + EpisodeReleaseBusinessDelayMs, result)
        assertEquals("2026-07-26T09:30:00.000", capturedDateTime)
        assertEquals("Asia/Manila", capturedTimezone)
    }

    @Test
    fun `blank and invalid releases have no scheduled instant`() {
        assertNull(
            resolveScheduledEpisodeReleaseEpochMs(
                raw = " ",
                timezoneId = "UTC",
                dateOnlyHour = 9,
                dateOnlyMinute = 0,
            ),
        )
        assertNull(
            resolveScheduledEpisodeReleaseEpochMs(
                raw = "not-a-date",
                timezoneId = "UTC",
                dateOnlyHour = 9,
                dateOnlyMinute = 0,
            ),
        )
    }
}
