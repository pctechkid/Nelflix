package com.nuvio.app.features.player

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerMaturityMetadataTest {
    @Test
    fun `episode metadata takes precedence over parent metadata`() {
        val result = resolvePlayerMaturityMetadata(
            episode = MetaVideo(
                id = "episode",
                title = "Episode",
                ageRating = " TV-PG ",
                genres = listOf("Fantasy", "Adventure"),
            ),
            parent = parent(ageRating = "TV-14", genres = listOf("Animation")),
        )

        assertEquals("TV-PG", result.ratingCode)
        assertEquals("Fantasy, Adventure", result.genresLine)
    }

    @Test
    fun `parent fields independently fill missing episode fields`() {
        val result = resolvePlayerMaturityMetadata(
            episode = MetaVideo(
                id = "episode",
                title = "Episode",
                genres = listOf("Drama"),
            ),
            parent = parent(ageRating = "TV-14", genres = listOf("Animation")),
        )

        assertEquals("TV-14", result.ratingCode)
        assertEquals("Drama", result.genresLine)
    }

    @Test
    fun `imdb genres fill missing provider genres without inventing a rating`() {
        val result = resolvePlayerMaturityMetadata(
            episode = null,
            parent = parent(ageRating = " ", genres = listOf("", " ")),
            imdbGenres = listOf("Animation", "Drama"),
        )

        assertNull(result.ratingCode)
        assertEquals("Animation, Drama", result.genresLine)
    }

    @Test
    fun `all blank metadata hides the whole section`() {
        val result = resolvePlayerMaturityMetadata(
            episode = MetaVideo(id = "episode", title = "Episode", ageRating = " "),
            parent = parent(ageRating = "", genres = listOf(" ")),
            imdbGenres = listOf(""),
        )

        assertNull(result.ratingCode)
        assertNull(result.genresLine)
    }

    private fun parent(
        ageRating: String?,
        genres: List<String>,
    ): MetaDetails = MetaDetails(
        id = "series",
        type = "series",
        name = "Series",
        ageRating = ageRating,
        genres = genres,
    )
}
