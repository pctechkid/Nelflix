package com.nuvio.app.features.trakt

import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class TraktProgressRefreshTest {
    @Test
    fun `stale Trakt refresh cannot replace a newer optimistic completion`() {
        val remote = entry(updatedAt = 100L, completed = false)
        val local = entry(updatedAt = 200L, completed = true)

        val merged = mergeTraktRefreshWithRecentLocal(
            remoteEntries = listOf(remote),
            localEntries = listOf(local),
            refreshStartedAtEpochMs = 250L,
        )

        assertEquals(listOf(local), merged)
    }

    @Test
    fun `newer Trakt server state replaces an older local projection`() {
        val remote = entry(updatedAt = 300L, completed = true)
        val local = entry(updatedAt = 200L, completed = false)

        val merged = mergeTraktRefreshWithRecentLocal(
            remoteEntries = listOf(remote),
            localEntries = listOf(local),
            refreshStartedAtEpochMs = 250L,
        )

        assertEquals(listOf(remote), merged)
    }

    private fun entry(updatedAt: Long, completed: Boolean): WatchProgressEntry =
        WatchProgressEntry(
            contentType = "series",
            parentMetaId = "tt-show",
            parentMetaType = "series",
            videoId = "tt-show:1:5",
            title = "Show",
            seasonNumber = 1,
            episodeNumber = 5,
            lastPositionMs = if (completed) 1_000L else 500L,
            durationMs = 1_000L,
            lastUpdatedEpochMs = updatedAt,
            isCompleted = completed,
        )
}
