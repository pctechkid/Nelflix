package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MetaDetailsParserTest {

    @Test
    fun `continue watching metadata merge upgrades placeholders and preserves complete fields`() {
        val fallback = MetaDetails(
            id = "tt1234567",
            type = "series",
            name = "Series",
            background = "https://image/background.jpg",
            videos = listOf(
                MetaVideo(
                    id = "provider-a:1:5",
                    title = "The Real Episode",
                    season = 1,
                    episode = 5,
                    thumbnail = "https://image/episode.jpg",
                    overview = "The real summary",
                ),
            ),
        )
        val sparseFresh = fallback.copy(
            background = null,
            videos = listOf(
                MetaVideo(
                    id = "provider-b:1:5",
                    title = "Episode 5",
                    season = 1,
                    episode = 5,
                    thumbnail = null,
                    overview = "",
                ),
            ),
        )

        val merged = sparseFresh.withContinueWatchingMetadataFallback(fallback)
        val episode = merged.videos.single()

        assertEquals("https://image/background.jpg", merged.background)
        assertEquals("The Real Episode", episode.title)
        assertEquals("https://image/episode.jpg", episode.thumbnail)
        assertEquals("The real summary", episode.overview)
        assertEquals("provider-b:1:5", episode.id)
    }

    @Test
    fun `parse rejects null meta object without json object cast crash`() {
        assertFailsWith<IllegalStateException> {
            MetaDetailsParser.parse("""{"meta":null}""")
        }
    }

    @Test
    fun `parse accepts bare meta object response`() {
        val result = MetaDetailsParser.parse(
            """
            {
              "id": "mal:62516",
              "type": "series",
              "name": "The Fragrant Flower Blooms with Dignity"
            }
            """.trimIndent(),
        )

        assertEquals("mal:62516", result.id)
        assertEquals("series", result.type)
        assertEquals("The Fragrant Flower Blooms with Dignity", result.name)
    }

    @Test
    fun `parse accepts anime style genre and certification aliases`() {
        val result = MetaDetailsParser.parse(
            """
            {
              "id": "mal:62516",
              "type": "series",
              "name": "Anime",
              "genre": "Animation, Drama",
              "content_rating": "TV-14",
              "videos": [{
                "id": "mal:62516:1",
                "title": "Episode 1",
                "season": 1,
                "episode": 1,
                "genres": "Fantasy, Adventure",
                "certification": "TV-PG"
              }]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("Animation", "Drama"), result.genres)
        assertEquals("TV-14", result.ageRating)
        assertEquals(listOf("Fantasy", "Adventure"), result.videos.single().genres)
        assertEquals("TV-PG", result.videos.single().ageRating)
    }

    @Test
    fun `parse treats blank metadata aliases as missing`() {
        val result = MetaDetailsParser.parse(
            """
            {
              "id": "tt1",
              "type": "series",
              "name": "Series",
              "genres": [" ", ""],
              "ageRating": " "
            }
            """.trimIndent(),
        )

        assertEquals(emptyList(), result.genres)
        assertEquals(null, result.ageRating)
    }

    @Test
    fun `episode IMDb fallback accepts only explicitly labelled IMDb fields`() {
        val result = MetaDetailsParser.parse(
            """
            {
              "id": "tt1",
              "type": "series",
              "name": "Series",
              "videos": [
                { "id": "one", "title": "One", "season": 1, "episode": 1, "imdb_rating": "8.6" },
                { "id": "two", "title": "Two", "season": 1, "episode": 2, "rating": "9.1" }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("8.6", result.videos[0].imdbRating)
        assertEquals(null, result.videos[1].imdbRating)
    }
}
