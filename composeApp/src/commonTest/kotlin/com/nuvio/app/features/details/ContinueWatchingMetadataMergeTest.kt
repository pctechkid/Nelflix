package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals

class ContinueWatchingMetadataMergeTest {
    @Test
    fun `rich episode metadata keeps AIO release timing`() {
        val rich = MetaDetails(
            id = "tt123",
            type = "series",
            name = "The World's Strongest Rearguard",
            videos = listOf(
                MetaVideo(
                    id = "rich-episode-5",
                    title = "The Black Treasure Chest and the Young Wife's Secret",
                    released = "2026-07-05",
                    thumbnail = "rich-thumb",
                    season = 1,
                    episode = 5,
                    overview = "After receiving some rare treasures...",
                    runtime = 24,
                ),
            ),
        )
        val aioRelease = MetaDetails(
            id = "tt123",
            type = "series",
            name = "The World's Strongest Rearguard",
            videos = listOf(
                MetaVideo(
                    id = "aio-episode-5",
                    title = "Episode 5",
                    released = "2026-07-05T13:00Z",
                    thumbnail = null,
                    season = 1,
                    episode = 5,
                    overview = null,
                ),
            ),
        )

        val merged = rich.withContinueWatchingReleaseTimingFallback(aioRelease)
        val episode = merged.videos.single()

        assertEquals("The Black Treasure Chest and the Young Wife's Secret", episode.title)
        assertEquals("rich-thumb", episode.thumbnail)
        assertEquals("After receiving some rare treasures...", episode.overview)
        assertEquals(24, episode.runtime)
        assertEquals("2026-07-05T13:00Z", episode.released)
    }
}
