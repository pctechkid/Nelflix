package com.nuvio.app.features.watchprogress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContinueWatchingEnrichmentCacheTest {

    @Test
    fun `next up cache snapshot keeps newest unique items within cap`() {
        val olderDuplicate = nextUpItem(contentId = "show-1", videoId = "show-1:1:2", timestamp = 10L)
        val newerDuplicate = nextUpItem(contentId = "show-1", videoId = "show-1:1:3", timestamp = 2_000L)
        val longTail = (2..75).map { index ->
            nextUpItem(contentId = "show-$index", videoId = "show-$index:1:1", timestamp = index.toLong())
        }

        val result = normalizeNextUpSnapshotForCache(listOf(olderDuplicate) + longTail + newerDuplicate)

        assertEquals(MaxCachedNextUpItems, result.size)
        assertEquals("show-1:1:3", result.first().videoId)
        assertFalse(result.any { item -> item.videoId == "show-1:1:2" })
        assertTrue(result.zipWithNext().all { (left, right) ->
            maxOf(left.sortTimestamp, left.lastWatched) >= maxOf(right.sortTimestamp, right.lastWatched)
        })
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
