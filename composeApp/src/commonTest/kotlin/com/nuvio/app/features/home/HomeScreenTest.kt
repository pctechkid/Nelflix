package com.nuvio.app.features.home

import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.withReleaseAlertState
import com.nuvio.app.features.watchprogress.CurrentEpisodeReleaseTimingRuleVersion
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.trakt.TRAKT_CONTINUE_WATCHING_DAYS_CAP_ALL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeScreenTest {

    @Test
    fun `build home continue watching items removes duplicate video ids`() {
        val inProgress = progressEntry(
            videoId = "tt0944947:1:4",
            title = "Game of Thrones",
            episodeTitle = "Cripples, Bastards, and Broken Things",
            lastUpdatedEpochMs = 250L,
        )
        val nextUp = continueWatchingItem(
            videoId = "tt0944947:1:4",
            subtitle = "Up Next • S1E4 • Cripples, Bastards, and Broken Things",
        )
        val movie = progressEntry(
            videoId = "movie-1",
            title = "Movie",
            lastUpdatedEpochMs = 100L,
            seasonNumber = null,
            episodeNumber = null,
            episodeTitle = null,
        )

        val result = buildHomeContinueWatchingItems(
            visibleEntries = listOf(inProgress, movie),
            nextUpItemsBySeries = mapOf("tt0944947" to (200L to nextUp)),
        )

        assertEquals(listOf("tt0944947:1:4", "movie-1"), result.map(ContinueWatchingItem::videoId))
        assertEquals("S1:E4 • Cripples, Bastards, and Broken Things", result.first().subtitle)
    }

    @Test
    fun `build home continue watching items prefers progress entry on timestamp tie`() {
        val inProgress = progressEntry(
            videoId = "show:1:5",
            title = "Show",
            episodeNumber = 5,
            episodeTitle = "The Wolf and the Lion",
            lastUpdatedEpochMs = 500L,
        )
        val nextUp = continueWatchingItem(
            videoId = "show:1:5",
            subtitle = "Up Next • S1E5 • The Wolf and the Lion",
        )

        val result = buildHomeContinueWatchingItems(
            visibleEntries = listOf(inProgress),
            nextUpItemsBySeries = mapOf("show" to (500L to nextUp)),
        )

        assertEquals(1, result.size)
        assertEquals("S1:E5 • The Wolf and the Lion", result.single().subtitle)
    }

    @Test
    fun `build home continue watching items suppresses next up when series has in progress resume`() {
        val inProgress = progressEntry(
            videoId = "show:1:4",
            title = "Show",
            episodeNumber = 4,
            episodeTitle = "Current",
            lastUpdatedEpochMs = 200L,
        )
        val nextUp = continueWatchingItem(
            videoId = "show:1:5",
            subtitle = "Up Next • S1E5 • Next",
        )

        val result = buildHomeContinueWatchingItems(
            visibleEntries = listOf(inProgress),
            nextUpItemsBySeries = mapOf("show" to (500L to nextUp)),
        )

        assertEquals(listOf("show:1:4"), result.map(ContinueWatchingItem::videoId))
        assertEquals("S1:E4 • Current", result.single().subtitle)
    }

    @Test
    fun `continue watching release day follows notification adjusted metadata day`() {
        assertEquals(
            "2026-07-26",
            continueWatchingNotificationReleaseDayIso("2026-07-25T17:30:00.000Z"),
        )
        assertEquals(
            "2026-07-25",
            continueWatchingNotificationReleaseDayIso("2026-07-25"),
        )
    }

    @Test
    fun `same canonical episode with provider specific video ids produces one tile`() {
        val sparse = progressEntry(
            videoId = "provider-a:episode-5",
            title = "Show",
            episodeNumber = 5,
            episodeTitle = "Episode 5",
            lastUpdatedEpochMs = 600L,
        ).copy(parentMetaId = "imdb:tt1234567")
        val complete = progressEntry(
            videoId = "provider-b:episode-5",
            title = "Show",
            episodeNumber = 5,
            episodeTitle = "A Real Episode Title",
            lastUpdatedEpochMs = 500L,
        ).copy(
            parentMetaId = "tt1234567",
            episodeThumbnail = "https://image/episode-5.jpg",
            pauseDescription = "Episode summary",
        )

        val result = buildHomeContinueWatchingItems(
            visibleEntries = listOf(sparse, complete),
            nextUpItemsBySeries = emptyMap(),
        )

        assertEquals(1, result.size)
        assertEquals("provider-b:episode-5", result.single().videoId)
    }

    @Test
    fun `different episodes from one series retain distinct canonical tiles`() {
        val episodeFive = progressEntry(
            videoId = "tt1234567:1:5",
            title = "Show",
            episodeNumber = 5,
            episodeTitle = "Five",
            lastUpdatedEpochMs = 500L,
        ).copy(parentMetaId = "tt1234567")
        val episodeSix = progressEntry(
            videoId = "tt1234567:1:6",
            title = "Show",
            episodeNumber = 6,
            episodeTitle = "Six",
            lastUpdatedEpochMs = 600L,
        ).copy(parentMetaId = "tt1234567")

        val result = buildHomeContinueWatchingItems(
            visibleEntries = listOf(episodeFive, episodeSix),
            nextUpItemsBySeries = emptyMap(),
        )

        assertEquals(listOf(6, 5), result.map(ContinueWatchingItem::episodeNumber))
    }

    @Test
    fun `real cached episode metadata replaces placeholders atomically`() {
        val live = continueWatchingItem(
            videoId = "tt1234567:1:5",
            subtitle = "S1:E5",
        ).copy(
            episodeNumber = 5,
            episodeTitle = "Episode 5",
            episodeThumbnail = null,
            pauseDescription = null,
            imageUrl = "https://image/series.jpg",
        )
        val cached = live.copy(
            episodeTitle = "The Real Title",
            episodeThumbnail = "https://image/episode.jpg",
            pauseDescription = "The real summary",
            subtitle = "stale derived text",
            imageUrl = "https://image/episode.jpg",
        )

        val merged = live.withFallbackMetadata(cached)

        assertEquals("The Real Title", merged.episodeTitle)
        assertEquals("S1:E5 • The Real Title", merged.subtitle)
        assertEquals("https://image/episode.jpg", merged.imageUrl)
        assertEquals("The real summary", merged.pauseDescription)
    }

    @Test
    fun `empty fallback cannot erase complete live episode metadata`() {
        val live = continueWatchingItem(
            videoId = "tt1234567:1:5",
            subtitle = "S1:E5",
        ).copy(
            episodeNumber = 5,
            episodeTitle = "The Real Title",
            episodeThumbnail = "https://image/episode.jpg",
            pauseDescription = "The real summary",
        )

        val merged = live.withFallbackMetadata(
            live.copy(
                episodeTitle = null,
                episodeThumbnail = null,
                pauseDescription = null,
                imageUrl = null,
            ),
        )

        assertEquals("The Real Title", merged.episodeTitle)
        assertEquals("https://image/episode.jpg", merged.imageUrl)
        assertEquals("The real summary", merged.pauseDescription)
    }

    @Test
    fun `older rich metadata cannot replace a newer rich snapshot`() {
        val older = continueWatchingItem(
            videoId = "tt1234567:1:5",
            subtitle = "S1:E5",
        ).copy(
            episodeNumber = 5,
            episodeTitle = "Old title",
            episodeThumbnail = "https://image/old.jpg",
            pauseDescription = "Old summary",
            metadataCheckedAtEpochMs = 10_000L,
        )
        val newer = older.copy(
            episodeTitle = "Fresh title",
            episodeThumbnail = "https://image/fresh.jpg",
            pauseDescription = "Fresh summary",
            metadataCheckedAtEpochMs = 20_000L,
        )

        val merged = older.withFallbackMetadata(newer)

        assertEquals("Fresh title", merged.episodeTitle)
        assertEquals("https://image/fresh.jpg", merged.imageUrl)
        assertEquals("Fresh summary", merged.pauseDescription)
        assertEquals(20_000L, merged.metadataCheckedAtEpochMs)
    }

    @Test
    fun `release boundary timer waits until the exact instant`() {
        assertEquals(1L, nextReleaseBoundaryDelayMs(nowEpochMs = 9_999L, releaseEpochMs = 10_000L))
        assertEquals(0L, nextReleaseBoundaryDelayMs(nowEpochMs = 10_000L, releaseEpochMs = 10_000L))
        assertEquals(0L, nextReleaseBoundaryDelayMs(nowEpochMs = 10_001L, releaseEpochMs = 10_000L))
    }

    @Test
    fun `cached release epoch is refreshed from raw metadata without stacking adjustments`() {
        var rawResolverCalled = false

        val restored = resolveCachedAdjustedReleaseEpochMs(
            cachedReleaseEpochMs = 10_000L,
            cachedTimingRuleVersion = CurrentEpisodeReleaseTimingRuleVersion,
        ) {
            rawResolverCalled = true
            20_000L
        }

        assertEquals(20_000L, restored)
        assertTrue(rawResolverCalled)
        assertEquals(
            20_000L,
            resolveCachedAdjustedReleaseEpochMs(
                cachedReleaseEpochMs = null,
                cachedTimingRuleVersion = 0,
            ) { 20_000L },
        )
        assertEquals(
            30_000L,
            resolveCachedAdjustedReleaseEpochMs(
                cachedReleaseEpochMs = 10_000L,
                cachedTimingRuleVersion = 0,
            ) { 30_000L },
        )
        assertEquals(
            10_000L,
            resolveCachedAdjustedReleaseEpochMs(
                cachedReleaseEpochMs = 10_000L,
                cachedTimingRuleVersion = CurrentEpisodeReleaseTimingRuleVersion,
            ) { null },
        )
    }

    @Test
    fun `date-only metadata cannot promote an episode before its exact release time`() {
        var resolverCalled = false

        val releaseEpochMs = resolvePreciseContinueWatchingReleaseEpochMs("2026-07-27") {
            resolverCalled = true
            10_000L
        }

        assertNull(releaseEpochMs)
        assertFalse(resolverCalled)
        assertFalse(
            scheduledNextUpItem(releaseEpochMs = releaseEpochMs)
                .withReleaseAlertState(nowEpochMs = 20_000L)
                .isReleaseAlert,
        )
    }

    @Test
    fun `exact notification timestamp is the only continue watching release boundary`() {
        var capturedRawValue: String? = null

        val releaseEpochMs = resolvePreciseContinueWatchingReleaseEpochMs(
            "2026-07-27T14:00:00.000Z",
        ) { rawValue ->
            capturedRawValue = rawValue
            11_000L
        }

        assertEquals("2026-07-27T14:00:00.000Z", capturedRawValue)
        assertFalse(
            scheduledNextUpItem(releaseEpochMs = releaseEpochMs)
                .withReleaseAlertState(nowEpochMs = 10_999L)
                .isReleaseAlert,
        )
        assertTrue(
            scheduledNextUpItem(releaseEpochMs = releaseEpochMs)
                .withReleaseAlertState(nowEpochMs = 11_000L)
                .isReleaseAlert,
        )
    }

    @Test
    fun `open app state changes at the scheduled boundary`() {
        val item = scheduledNextUpItem(releaseEpochMs = 10_000L)

        val before = buildHomeContinueWatchingItems(
            visibleEntries = emptyList(),
            nextUpItemsBySeries = mapOf("show" to (1_000L to item)),
            nowEpochMs = 9_999L,
        ).single()
        val atBoundary = buildHomeContinueWatchingItems(
            visibleEntries = emptyList(),
            nextUpItemsBySeries = mapOf("show" to (1_000L to item)),
            nowEpochMs = 10_000L,
        ).single()

        assertFalse(before.isReleaseAlert)
        assertTrue(atBoundary.isReleaseAlert)
    }

    @Test
    fun `resumed app recalculates a crossed release boundary`() {
        val item = scheduledNextUpItem(releaseEpochMs = 10_000L)

        val resumed = buildHomeContinueWatchingItems(
            visibleEntries = emptyList(),
            nextUpItemsBySeries = mapOf("show" to (1_000L to item)),
            nowEpochMs = 10_001L,
        ).single()

        assertTrue(resumed.isReleaseAlert)
    }

    @Test
    fun `Trakt continue watching window filters old progress only when Trakt source is active`() {
        val oldEntry = progressEntry(
            videoId = "old",
            title = "Old",
            lastUpdatedEpochMs = 1_000L,
            seasonNumber = null,
            episodeNumber = null,
        )
        val recentEntry = progressEntry(
            videoId = "recent",
            title = "Recent",
            lastUpdatedEpochMs = 30L * MILLIS_PER_DAY,
            seasonNumber = null,
            episodeNumber = null,
        )
        val entries = listOf(oldEntry, recentEntry)

        val filtered = filterEntriesForTraktContinueWatchingWindow(
            entries = entries,
            isTraktProgressActive = true,
            daysCap = 60,
            nowEpochMs = 90L * MILLIS_PER_DAY,
        )
        val nuvioSource = filterEntriesForTraktContinueWatchingWindow(
            entries = entries,
            isTraktProgressActive = false,
            daysCap = 60,
            nowEpochMs = 90L * MILLIS_PER_DAY,
        )

        assertEquals(listOf("recent"), filtered.map(WatchProgressEntry::videoId))
        assertEquals(listOf("old", "recent"), nuvioSource.map(WatchProgressEntry::videoId))
    }

    @Test
    fun `Trakt all history window keeps old progress`() {
        val oldEntry = progressEntry(
            videoId = "old",
            title = "Old",
            lastUpdatedEpochMs = 1_000L,
            seasonNumber = null,
            episodeNumber = null,
        )
        val recentEntry = progressEntry(
            videoId = "recent",
            title = "Recent",
            lastUpdatedEpochMs = 30L * MILLIS_PER_DAY,
            seasonNumber = null,
            episodeNumber = null,
        )

        val result = filterEntriesForTraktContinueWatchingWindow(
            entries = listOf(oldEntry, recentEntry),
            isTraktProgressActive = true,
            daysCap = TRAKT_CONTINUE_WATCHING_DAYS_CAP_ALL,
            nowEpochMs = 90L * MILLIS_PER_DAY,
        )

        assertEquals(listOf("old", "recent"), result.map(WatchProgressEntry::videoId))
    }

    private fun progressEntry(
        videoId: String,
        title: String,
        lastUpdatedEpochMs: Long,
        seasonNumber: Int? = 1,
        episodeNumber: Int? = 4,
        episodeTitle: String? = "Episode",
    ): WatchProgressEntry =
        WatchProgressEntry(
            contentType = if (seasonNumber != null && episodeNumber != null) "series" else "movie",
            parentMetaId = videoId.substringBefore(':'),
            parentMetaType = if (seasonNumber != null && episodeNumber != null) "series" else "movie",
            videoId = videoId,
            title = title,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            lastPositionMs = if (seasonNumber != null && episodeNumber != null) 120_000L else 60_000L,
            durationMs = 1_000_000L,
            lastUpdatedEpochMs = lastUpdatedEpochMs,
        )

    private fun continueWatchingItem(
        videoId: String,
        subtitle: String,
    ): ContinueWatchingItem =
        ContinueWatchingItem(
            parentMetaId = videoId.substringBefore(':'),
            parentMetaType = "series",
            videoId = videoId,
            title = "Show",
            subtitle = subtitle,
            imageUrl = null,
            seasonNumber = 1,
            episodeNumber = 4,
            episodeTitle = subtitle.substringAfterLast(" • ", "Episode"),
            resumePositionMs = 0L,
            durationMs = 0L,
            progressFraction = 0f,
        )

    private fun scheduledNextUpItem(releaseEpochMs: Long?): ContinueWatchingItem =
        ContinueWatchingItem(
            parentMetaId = "show",
            parentMetaType = "series",
            videoId = "show:1:2",
            title = "Show",
            subtitle = "S1:E2",
            imageUrl = null,
            seasonNumber = 1,
            episodeNumber = 2,
            releaseEpochMs = releaseEpochMs,
            isNextUp = true,
            nextUpSeedSeasonNumber = 1,
            nextUpSeedEpisodeNumber = 1,
            nextUpSeedLastUpdatedEpochMs = 1_000L,
            resumePositionMs = 0L,
            durationMs = 0L,
            progressFraction = 0f,
        )

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
