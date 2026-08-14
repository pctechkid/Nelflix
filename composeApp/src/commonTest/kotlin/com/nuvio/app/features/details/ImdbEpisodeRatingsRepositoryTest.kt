package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImdbEpisodeRatingsRepositoryTest {
    @Test
    fun `extracts imdb ids from addon ids and urls`() {
        assertEquals("tt15398776", extractImdbTitleId("series:tt15398776:1:2"))
        assertEquals("tt0944947", extractImdbTitleId("https://www.imdb.com/title/TT0944947/"))
        assertNull(extractImdbTitleId("tmdb:1399"))
    }

    @Test
    fun `extracts tmdb ids with optional media type`() {
        assertEquals(1399, extractTmdbTitleId("tmdb:1399"))
        assertEquals(1399, extractTmdbTitleId("series/tmdb:tv:1399"))
        assertNull(extractTmdbTitleId("tt0944947"))
    }

    @Test
    fun `maps numbered episodes and specials while dropping invalid ratings`() {
        val ratings = seriesGraphEpisodeRatings(
            listOf(
                SeriesGraphSeasonRatingsDto(
                    episodes = listOf(
                        SeriesGraphEpisodeRatingDto(seasonNumber = 0, episodeNumber = 1, voteAverage = 7.3),
                        SeriesGraphEpisodeRatingDto(seasonNumber = 1, episodeNumber = 2, voteAverage = 8.6),
                        SeriesGraphEpisodeRatingDto(seasonNumber = 1, episodeNumber = 0, voteAverage = 9.9),
                        SeriesGraphEpisodeRatingDto(seasonNumber = 1, episodeNumber = 3, voteAverage = null),
                        SeriesGraphEpisodeRatingDto(seasonNumber = 1, episodeNumber = 4, voteAverage = 0.0),
                        SeriesGraphEpisodeRatingDto(seasonNumber = 1, episodeNumber = 5, voteAverage = 10.1),
                        SeriesGraphEpisodeRatingDto(seasonNumber = 1, episodeNumber = 6, voteAverage = Double.NaN),
                    ),
                ),
            ),
        )

        assertEquals(
            mapOf(
                (0 to 1) to 7.3,
                (1 to 2) to 8.6,
            ),
            ratings,
        )
    }

    @Test
    fun `validated metadata ratings survive provider outage and override older provider values`() {
        val metadata = episodeMetadataImdbRatings(
            listOf(
                MetaVideo(id = "episode-1", title = "One", season = 1, episode = 1, imdbRating = "IMDb 8.7"),
                MetaVideo(id = "episode-2", title = "Two", season = 1, episode = 2, imdbRating = "0.0"),
                MetaVideo(id = "episode-3", title = "Three", season = 1, episode = 3, imdbRating = "11.2"),
            ),
        )

        assertEquals(mapOf((1 to 1) to 8.7), mergeEpisodeImdbRatings(emptyMap(), metadata))
        assertEquals(
            mapOf((1 to 1) to 8.7, (1 to 2) to 7.4),
            mergeEpisodeImdbRatings(
                providerRatings = mapOf((1 to 1) to 8.1, (1 to 2) to 7.4, (1 to 3) to Double.NaN),
                metadataRatings = metadata,
            ),
        )
    }

    @Test
    fun `negative provider results expire sooner than successful ratings`() {
        val emptyTtl = ImdbEpisodeRatingsRepository.cacheTtlMsForTests(emptyMap())
        val successTtl = ImdbEpisodeRatingsRepository.cacheTtlMsForTests(mapOf((1 to 1) to 8.0))

        assertTrue(emptyTtl > 0L)
        assertTrue(successTtl > emptyTtl)
    }
}
