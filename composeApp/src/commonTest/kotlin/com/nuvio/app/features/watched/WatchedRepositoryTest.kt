package com.nuvio.app.features.watched

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchedRepositoryTest {
    @Test
    fun watchedItemKey_isTypeAware() {
        assertEquals("movie:tt1:-1:-1", watchedItemKey(type = "movie", id = "tt1"))
    }

    @Test
    fun watchedItemKey_trimsValues() {
        assertEquals("series:abc:-1:-1", watchedItemKey(type = " series ", id = " abc "))
    }

    @Test
    fun watchedItemKey_includes_episode_coordinates() {
        assertEquals(
            "series:show:2:5",
            watchedItemKey(type = "series", id = "show", season = 2, episode = 5),
        )
    }

    @Test
    fun fullyWatchedSeries_ignores_specials() {
        val meta = MetaDetails(
            id = "show",
            type = "series",
            name = "Show",
            videos = listOf(
                MetaVideo(id = "special", title = "Special", season = 0, episode = 1, released = "2026-03-01"),
                MetaVideo(id = "ep1", title = "Episode 1", season = 1, episode = 1, released = "2026-03-08"),
                MetaVideo(id = "ep2", title = "Episode 2", season = 1, episode = 2, released = "2026-03-15"),
            ),
        )

        val result = meta.hasWatchedAllMainSeasonEpisodes(todayIsoDate = "2026-03-30") { episode ->
            episode.season == 1
        }

        assertTrue(result)
    }

    @Test
    fun pullMerge_pendingSeasonDeletesOverrideStaleServerRows() {
        val serverItems = (1..3).map { episode ->
            watchedEpisode(episode = episode, markedAtEpochMs = 1_000L)
        }
        val pendingDeletes = serverItems.map { item ->
            PendingWatchedMutation(item = item, isWatched = false)
        }

        val merged = mergeWatchedPull(
            serverItems = serverItems,
            pendingMutations = pendingDeletes,
        )

        assertTrue(merged.items.isEmpty())
    }

    @Test
    fun pullMerge_pendingMarkOverridesOlderServerRow() {
        val serverItem = watchedEpisode(episode = 2, markedAtEpochMs = 1_000L)
        val localItem = serverItem.copy(markedAtEpochMs = 2_000L)

        val merged = mergeWatchedPull(
            serverItems = listOf(serverItem),
            pendingMutations = listOf(PendingWatchedMutation(localItem, isWatched = true)),
        )

        assertEquals(localItem, merged.items.getValue(watchedItemKey("series", "show", 1, 2)))
    }

    @Test
    fun pullMerge_doesNotResurrectStaleLocalWatchedRowDeletedByAnotherDevice() {
        val staleLocalItem = watchedEpisode(episode = 3, markedAtEpochMs = 1_000L)

        val merged = mergeWatchedPull(
            serverItems = emptyList(),
            pendingMutations = emptyList(),
        )

        assertFalse(staleLocalItem.keyForTest() in merged.items)
    }

    @Test
    fun mutationAcknowledgement_preservesNewerOppositeMutation() {
        val item = watchedEpisode(episode = 4, markedAtEpochMs = 1_000L)
        val sentDelete = PendingWatchedMutation(item = item, isWatched = false)
        val newerMark = PendingWatchedMutation(item = item.copy(markedAtEpochMs = 2_000L), isWatched = true)
        val key = watchedItemKey("series", "show", 1, 4)

        val remaining = acknowledgePendingWatchedMutations(
            current = mapOf(key to newerMark),
            sent = mapOf(key to sentDelete),
        )

        assertEquals(newerMark, remaining[key])
        assertFalse(remaining.isEmpty())
    }

    private fun watchedEpisode(
        episode: Int,
        markedAtEpochMs: Long,
    ): WatchedItem = WatchedItem(
        id = "show",
        type = "series",
        name = "Episode $episode",
        season = 1,
        episode = episode,
        markedAtEpochMs = markedAtEpochMs,
    )

    private fun WatchedItem.keyForTest(): String = watchedItemKey(type, id, season, episode)
}

