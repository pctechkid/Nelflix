package com.nuvio.app.features.watchprogress

import com.nuvio.app.features.trakt.WatchProgressSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContinueWatchingEnrichmentCacheTest {

    @Test
    fun `next up cache snapshot keeps newest unique items within cap`() {
        val olderDuplicate = nextUpItem(contentId = "show-1", videoId = "provider-a", timestamp = 10L)
        val newerDuplicate = nextUpItem(contentId = "show-1", videoId = "provider-b", timestamp = 2_000L)
        val longTail = (2..75).map { index ->
            nextUpItem(contentId = "show-$index", videoId = "show-$index:1:1", timestamp = index.toLong())
        }

        val result = normalizeNextUpSnapshotForCache(listOf(olderDuplicate) + longTail + newerDuplicate)

        assertEquals(MaxCachedNextUpItems, result.size)
        assertEquals("provider-b", result.first().videoId)
        assertFalse(result.any { item -> item.videoId == "provider-a" })
        assertTrue(result.zipWithNext().all { (left, right) ->
            maxOf(left.sortTimestamp, left.lastWatched) >= maxOf(right.sortTimestamp, right.lastWatched)
        })
    }

    @Test
    fun `next up cache keeps the newest metadata snapshot over a newer sort timestamp`() {
        val freshMetadata = nextUpItem(
            contentId = "show-1",
            videoId = "show-1:1:5",
            timestamp = 100L,
        ).copy(
            episodeTitle = "Fresh title",
            episodeThumbnail = "https://image/fresh.jpg",
            metadataCheckedAtEpochMs = 20_000L,
        )
        val staleMetadata = freshMetadata.copy(
            episodeTitle = "Old title",
            episodeThumbnail = "https://image/old.jpg",
            sortTimestamp = 1_000L,
            lastWatched = 1_000L,
            metadataCheckedAtEpochMs = 10_000L,
        )

        val result = normalizeNextUpSnapshotForCache(listOf(freshMetadata, staleMetadata))

        assertEquals(1, result.size)
        assertEquals("Fresh title", result.single().episodeTitle)
        assertEquals("https://image/fresh.jpg", result.single().episodeThumbnail)
    }

    @Test
    fun `in progress cache snapshot keeps newest unique videos within cap`() {
        val olderDuplicate = inProgressItem(videoId = "movie-1", timestamp = 5L)
        val newerDuplicate = inProgressItem(videoId = "movie-1", timestamp = 2_000L)
        val longTail = (2..30).map { index ->
            inProgressItem(videoId = "movie-$index", timestamp = index.toLong())
        }

        val result = normalizeInProgressSnapshotForCache(listOf(olderDuplicate) + longTail + newerDuplicate)

        assertEquals(MaxCachedInProgressItems, result.size)
        assertEquals("movie-1", result.first().videoId)
        assertFalse(result.drop(1).any { item -> item.videoId == "movie-1" })
        assertTrue(result.zipWithNext().all { (left, right) -> left.lastWatched >= right.lastWatched })
    }

    @Test
    fun `next up cache is invalid when the completed episode advances`() {
        assertTrue(
            matchesNextUpCompletionSeed(
                seedSeason = 1,
                seedEpisode = 3,
                seedTimestamp = 1_000L,
                completedSeason = 1,
                completedEpisode = 3,
                completedTimestamp = 1_000L,
            ),
        )
        assertFalse(
            matchesNextUpCompletionSeed(
                seedSeason = 1,
                seedEpisode = 3,
                seedTimestamp = 1_000L,
                completedSeason = 1,
                completedEpisode = 4,
                completedTimestamp = 2_000L,
            ),
        )
    }

    @Test
    fun `incomplete cached metadata uses a bounded refresh age`() {
        val cached = nextUpItem(
            contentId = "show-1",
            videoId = "show-1:1:4",
            timestamp = 1L,
        ).copy(
            episode = 4,
            episodeTitle = "Episode 4",
            episodeThumbnail = "thumbnail",
            pauseDescription = "Summary",
            metadataCheckedAtEpochMs = 10_000L,
        )

        assertFalse(
            cached.needsContinueWatchingMetadataRefresh(
                nowEpochMs = 10_000L + IncompleteContinueWatchingMetadataTtlMs - 1L,
            ),
        )
        assertTrue(
            cached.needsContinueWatchingMetadataRefresh(
                nowEpochMs = 10_000L + IncompleteContinueWatchingMetadataTtlMs,
            ),
        )
    }

    @Test
    fun `invalidated generation cannot restore a stale cache snapshot`() {
        val profileId = 91_337
        ContinueWatchingEnrichmentCache.loadProfile(profileId, WatchProgressSource.NUVIO_SYNC)
        val staleGeneration = ContinueWatchingEnrichmentCache.generation.value

        ContinueWatchingEnrichmentCache.invalidate(profileId, WatchProgressSource.NUVIO_SYNC)

        assertFalse(
            ContinueWatchingEnrichmentCache.saveSnapshots(
                profileId = profileId,
                source = WatchProgressSource.NUVIO_SYNC,
                generation = staleGeneration,
                nextUp = listOf(nextUpItem("show", "stale", 1L)),
                inProgress = emptyList(),
                savedAtEpochMs = 1L,
            ),
        )
        assertTrue(ContinueWatchingEnrichmentCache.snapshots.value.nextUp.isEmpty())
    }

    @Test
    fun `content invalidation keeps unrelated enrichment and rejects stale writers`() {
        val profileId = 91_338
        val source = WatchProgressSource.NUVIO_SYNC
        ContinueWatchingEnrichmentCache.loadProfile(profileId, source)
        val initialGeneration = ContinueWatchingEnrichmentCache.generation.value
        assertTrue(
            ContinueWatchingEnrichmentCache.saveSnapshots(
                profileId = profileId,
                source = source,
                generation = initialGeneration,
                nextUp = listOf(
                    nextUpItem("naruto", "naruto:1:2", 2L),
                    nextUpItem("other-show", "other-show:1:2", 1L),
                ),
                inProgress = listOf(
                    inProgressItem("naruto", 2L),
                    inProgressItem("other-movie", 1L),
                ),
                savedAtEpochMs = 2L,
            ),
        )

        ContinueWatchingEnrichmentCache.invalidateContent(
            profileId = profileId,
            source = source,
            contentIds = listOf("naruto"),
        )

        val snapshot = ContinueWatchingEnrichmentCache.snapshots.value
        assertEquals(listOf("other-show"), snapshot.nextUp.map(CachedNextUpItem::contentId))
        assertEquals(listOf("other-movie"), snapshot.inProgress.map(CachedInProgressItem::contentId))
        assertFalse(
            ContinueWatchingEnrichmentCache.saveSnapshots(
                profileId = profileId,
                source = source,
                generation = initialGeneration,
                nextUp = listOf(nextUpItem("naruto", "stale", 3L)),
                inProgress = emptyList(),
                savedAtEpochMs = 3L,
            ),
        )
    }

    @Test
    fun `next up upsert replaces one series immediately and preserves unrelated enrichment`() {
        val profileId = 91_339
        val source = WatchProgressSource.NUVIO_SYNC
        ContinueWatchingEnrichmentCache.loadProfile(profileId, source)
        val initialGeneration = ContinueWatchingEnrichmentCache.generation.value
        assertTrue(
            ContinueWatchingEnrichmentCache.saveSnapshots(
                profileId = profileId,
                source = source,
                generation = initialGeneration,
                nextUp = listOf(
                    nextUpItem("naruto", "naruto:22:12", 12L),
                    nextUpItem("other-show", "other-show:1:2", 10L),
                ),
                inProgress = listOf(
                    inProgressItem("naruto", 12L),
                    inProgressItem("other-movie", 10L),
                ),
                savedAtEpochMs = 12L,
            ),
        )

        assertTrue(
            ContinueWatchingEnrichmentCache.upsertNextUp(
                profileId = profileId,
                source = source,
                item = nextUpItem("naruto", "naruto:22:13", 13L),
                savedAtEpochMs = 13L,
            ),
        )

        val snapshot = ContinueWatchingEnrichmentCache.snapshots.value
        assertEquals(
            listOf("naruto:22:13", "other-show:1:2"),
            snapshot.nextUp.map(CachedNextUpItem::videoId),
        )
        assertEquals(listOf("other-movie"), snapshot.inProgress.map(CachedInProgressItem::contentId))
        assertFalse(
            ContinueWatchingEnrichmentCache.saveNextUpSnapshot(
                profileId = profileId,
                source = source,
                generation = initialGeneration,
                nextUp = listOf(nextUpItem("naruto", "naruto:stale", 14L)),
                savedAtEpochMs = 14L,
            ),
        )
    }

    private fun nextUpItem(
        contentId: String,
        videoId: String,
        timestamp: Long,
    ): CachedNextUpItem =
        CachedNextUpItem(
            contentId = contentId,
            contentType = "series",
            name = "Show",
            videoId = videoId,
            season = 1,
            episode = 1,
            lastWatched = timestamp,
            sortTimestamp = timestamp,
        )

    private fun inProgressItem(
        videoId: String,
        timestamp: Long,
    ): CachedInProgressItem =
        CachedInProgressItem(
            contentId = videoId,
            contentType = "movie",
            name = "Movie",
            videoId = videoId,
            position = 120_000L,
            duration = 1_000_000L,
            lastWatched = timestamp,
        )
}
