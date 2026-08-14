package com.nuvio.app.features.watchprogress

import com.nuvio.app.core.time.resolveDeviceLocalScheduledEpisodeReleaseEpochMs
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchProgressModelsTest {
    @Test
    fun `continue watching episode code uses colon notation`() {
        assertEquals("S1:E13", continueWatchingEpisodeCode(seasonNumber = 1, episodeNumber = 13))
        assertEquals("E4", continueWatchingEpisodeCode(seasonNumber = null, episodeNumber = 4))
        assertNull(continueWatchingEpisodeCode(seasonNumber = 1, episodeNumber = null))
    }

    @Test
    fun `continue watching subtitle uses normalized episode code`() {
        assertEquals(
            "S2:E5 • The Next Chapter",
            buildContinueWatchingEpisodeSubtitle(
                seasonNumber = 2,
                episodeNumber = 5,
                episodeTitle = "The Next Chapter",
            ),
        )
    }

    @Test
    fun `release alert changes exactly at the scheduled instant`() {
        val before = calculateReleaseAlertState(
            seedLastUpdatedEpochMs = 1_000L,
            seedSeasonNumber = 1,
            nextSeasonNumber = 1,
            releaseEpochMs = 10_000L,
            nowEpochMs = 9_999L,
        )
        val atBoundary = calculateReleaseAlertState(
            seedLastUpdatedEpochMs = 1_000L,
            seedSeasonNumber = 1,
            nextSeasonNumber = 1,
            releaseEpochMs = 10_000L,
            nowEpochMs = 10_000L,
        )

        assertFalse(before.isReleaseAlert)
        assertTrue(atBoundary.isReleaseAlert)
        assertFalse(atBoundary.isNewSeasonRelease)
    }

    @Test
    fun `released next episode does not revert to up next over time`() {
        val state = calculateReleaseAlertState(
            seedLastUpdatedEpochMs = 1_000L,
            seedSeasonNumber = 1,
            nextSeasonNumber = 1,
            releaseEpochMs = 10_000L,
            nowEpochMs = 365L * 24L * 60L * 60L * 1_000L,
        )

        assertTrue(state.isReleaseAlert)
    }

    @Test
    fun `new season alert uses the same release boundary`() {
        val state = calculateReleaseAlertState(
            seedLastUpdatedEpochMs = 1_000L,
            seedSeasonNumber = 1,
            nextSeasonNumber = 2,
            releaseEpochMs = 10_000L,
            nowEpochMs = 10_000L,
        )

        assertTrue(state.isReleaseAlert)
        assertTrue(state.isNewSeasonRelease)
    }

    @Test
    fun `missing release instant does not invent a release alert`() {
        val state = calculateReleaseAlertState(
            seedLastUpdatedEpochMs = 1_000L,
            seedSeasonNumber = 1,
            nextSeasonNumber = 2,
            releaseEpochMs = null,
            nowEpochMs = 10_000L,
        )

        assertFalse(state.isReleaseAlert)
        assertFalse(state.isNewSeasonRelease)
    }

    @Test
    fun `notification instant and chip transition share the adjusted boundary`() {
        val adjustedReleaseEpochMs = resolveDeviceLocalScheduledEpisodeReleaseEpochMs(
            raw = "2026-07-27T14:30Z",
            dateOnlyHour = 9,
            dateOnlyMinute = 0,
            localDateTimeToEpochMs = { _, _ -> 1_785_162_600_000L },
        )!!
        val item = WatchProgressEntry(
            contentType = "series",
            parentMetaId = "tt123",
            parentMetaType = "series",
            videoId = "tt123:1:1",
            title = "Show",
            seasonNumber = 1,
            episodeNumber = 1,
            lastPositionMs = 1_000L,
            durationMs = 1_000L,
            lastUpdatedEpochMs = adjustedReleaseEpochMs - 100_000L,
            isCompleted = true,
        ).toUpNextContinueWatchingItem(
            nextEpisode = MetaVideo(
                id = "tt123:1:2",
                title = "Episode 2",
                released = "2026-07-27T14:30Z",
                season = 1,
                episode = 2,
            ),
            releaseEpochMs = adjustedReleaseEpochMs,
        )

        assertEquals(adjustedReleaseEpochMs, item.releaseEpochMs)
        assertFalse(item.withReleaseAlertState(adjustedReleaseEpochMs - 60_000L).isReleaseAlert)
        assertFalse(item.withReleaseAlertState(adjustedReleaseEpochMs - 1L).isReleaseAlert)
        assertTrue(item.withReleaseAlertState(adjustedReleaseEpochMs).isReleaseAlert)
        assertTrue(item.withReleaseAlertState(adjustedReleaseEpochMs + 1L).isReleaseAlert)
    }

    @Test
    fun `fresh episode metadata replaces placeholder fields without changing progress`() {
        val entry = progressEntry(
            episodeTitle = "Episode 4",
            episodeThumbnail = null,
            pauseDescription = null,
        )
        val refreshed = entry.withRefreshedContinueWatchingMetadata(
            meta = MetaDetails(
                id = "tt123",
                type = "series",
                name = "Series",
                poster = "new-poster",
                background = "new-background",
                videos = listOf(
                    MetaVideo(
                        id = "tt123:1:4",
                        title = "The Real Episode Title",
                        thumbnail = "new-thumbnail",
                        season = 1,
                        episode = 4,
                        overview = "New authoritative summary",
                    ),
                ),
            ),
            checkedAtEpochMs = 20_000L,
        )

        assertEquals("The Real Episode Title", refreshed.episodeTitle)
        assertEquals("new-thumbnail", refreshed.episodeThumbnail)
        assertEquals("New authoritative summary", refreshed.pauseDescription)
        assertEquals(entry.lastPositionMs, refreshed.lastPositionMs)
        assertEquals(entry.durationMs, refreshed.durationMs)
        assertEquals(20_000L, refreshed.metadataCheckedAtEpochMs)
    }

    @Test
    fun `transient empty metadata cannot erase known episode fields`() {
        val entry = progressEntry(
            episodeTitle = "A Good Title",
            episodeThumbnail = "good-thumbnail",
            pauseDescription = "Good summary",
        )
        val refreshed = entry.withRefreshedContinueWatchingMetadata(
            meta = MetaDetails(
                id = "tt123",
                type = "series",
                name = "",
                poster = "",
                background = "",
                videos = listOf(
                    MetaVideo(
                        id = "tt123:1:4",
                        title = "",
                        thumbnail = "",
                        season = 1,
                        episode = 4,
                        overview = "",
                    ),
                ),
            ),
            checkedAtEpochMs = 20_000L,
        )

        assertEquals("A Good Title", refreshed.episodeTitle)
        assertEquals("good-thumbnail", refreshed.episodeThumbnail)
        assertEquals("Good summary", refreshed.pauseDescription)
        assertEquals("poster", refreshed.poster)
        assertEquals("background", refreshed.background)
    }

    @Test
    fun `generic refresh cannot replace a known episode title`() {
        val refreshed = progressEntry(
            episodeTitle = "A Good Title",
            episodeThumbnail = "thumbnail",
            pauseDescription = "summary",
        ).withRefreshedContinueWatchingMetadata(
            meta = MetaDetails(
                id = "tt123",
                type = "series",
                name = "Series",
                videos = listOf(
                    MetaVideo(
                        id = "tt123:1:4",
                        title = "Episode 4",
                        season = 1,
                        episode = 4,
                    ),
                ),
            ),
            checkedAtEpochMs = 20_000L,
        )

        assertEquals("A Good Title", refreshed.episodeTitle)
    }

    @Test
    fun `progress session cannot overwrite newer checked episode metadata`() {
        val previous = progressEntry(
            episodeTitle = "Fresh title",
            episodeThumbnail = "fresh-thumbnail",
            pauseDescription = "Fresh summary",
        ).copy(metadataCheckedAtEpochMs = 20_000L)
        val staleSession = WatchProgressPlaybackSession(
            contentType = "series",
            parentMetaId = "tt123",
            parentMetaType = "series",
            videoId = "tt123:1:4",
            title = "Series",
            poster = "old-poster",
            background = "old-background",
            seasonNumber = 1,
            episodeNumber = 4,
            episodeTitle = "Old title",
            episodeThumbnail = "old-thumbnail",
            pauseDescription = "Old summary",
        )

        val merged = staleSession.withPreservedCheckedMetadata(previous)

        assertEquals("Fresh title", merged.episodeTitle)
        assertEquals("fresh-thumbnail", merged.episodeThumbnail)
        assertEquals("Fresh summary", merged.pauseDescription)
        assertEquals("poster", merged.poster)
        assertEquals("background", merged.background)
    }

    @Test
    fun `richer session title can improve checked generic episode title`() {
        val previous = progressEntry(
            episodeTitle = "Episode 4",
            episodeThumbnail = null,
            pauseDescription = null,
        ).copy(metadataCheckedAtEpochMs = 20_000L)
        val session = WatchProgressPlaybackSession(
            contentType = "series",
            parentMetaId = "tt123",
            parentMetaType = "series",
            videoId = "tt123:1:4",
            title = "Series",
            seasonNumber = 1,
            episodeNumber = 4,
            episodeTitle = "The Real Episode Title",
            episodeThumbnail = "session-thumbnail",
            pauseDescription = "Session summary",
        )

        val merged = session.withPreservedCheckedMetadata(previous)

        assertEquals("The Real Episode Title", merged.episodeTitle)
        assertEquals("session-thumbnail", merged.episodeThumbnail)
        assertEquals("Session summary", merged.pauseDescription)
    }

    private fun progressEntry(
        episodeTitle: String?,
        episodeThumbnail: String?,
        pauseDescription: String?,
    ): WatchProgressEntry =
        WatchProgressEntry(
            contentType = "series",
            parentMetaId = "tt123",
            parentMetaType = "series",
            videoId = "tt123:1:4",
            title = "Series",
            poster = "poster",
            background = "background",
            seasonNumber = 1,
            episodeNumber = 4,
            episodeTitle = episodeTitle,
            episodeThumbnail = episodeThumbnail,
            pauseDescription = pauseDescription,
            lastPositionMs = 10_000L,
            durationMs = 100_000L,
            lastUpdatedEpochMs = 5_000L,
        )
}
