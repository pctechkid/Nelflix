package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetaDetailsMaturityTest {
    @Test
    fun `partial parent maturity metadata remains refreshable`() {
        assertFalse(meta(ageRating = "TV-14", genres = emptyList()).hasCompleteMaturityMetadata())
        assertFalse(meta(ageRating = null, genres = listOf("Animation")).hasCompleteMaturityMetadata())
        assertTrue(meta(ageRating = "TV-14", genres = listOf("Animation")).hasCompleteMaturityMetadata())
    }

    @Test
    fun `maturity fallback fills missing fields without replacing preferred metadata`() {
        val preferred = meta(
            ageRating = null,
            genres = listOf("Animation"),
            name = "Preferred title",
            videos = listOf(
                MetaVideo(
                    id = "preferred-episode-id",
                    title = "Preferred episode title",
                    season = 1,
                    episode = 2,
                    genres = listOf("Drama"),
                ),
            ),
        )
        val fallback = meta(
            ageRating = "TV-14",
            genres = listOf("Fallback genre"),
            name = "Fallback title",
            videos = listOf(
                MetaVideo(
                    id = "different-id",
                    title = "Fallback episode title",
                    season = 1,
                    episode = 2,
                    ageRating = "TV-PG",
                    genres = listOf("Fallback episode genre"),
                ),
            ),
        )

        val result = preferred.withMaturityFallback(fallback)

        assertEquals("Preferred title", result.name)
        assertEquals("TV-14", result.ageRating)
        assertEquals(listOf("Animation"), result.genres)
        assertEquals("Preferred episode title", result.videos.single().title)
        assertEquals("TV-PG", result.videos.single().ageRating)
        assertEquals(listOf("Drama"), result.videos.single().genres)
    }

    private fun meta(
        ageRating: String?,
        genres: List<String>,
        name: String = "Title",
        videos: List<MetaVideo> = emptyList(),
    ): MetaDetails = MetaDetails(
        id = "tt1234567",
        type = "series",
        name = name,
        ageRating = ageRating,
        genres = genres,
        videos = videos,
    )
}
