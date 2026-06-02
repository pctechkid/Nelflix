package com.nuvio.app.features.details

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal object SeriesGraphApi {
    suspend fun getSeasonRatings(tmdbId: Int): List<SeriesGraphSeasonRatingsDto> =
        requestSeasonRatings(
            baseUrl = ImdbEpisodeRatingsConfig.IMDB_RATINGS_API_BASE_URL,
            showId = tmdbId.toString(),
        )
}

internal object ImdbTapframeApi {
    suspend fun getSeasonRatings(imdbId: String): List<SeriesGraphSeasonRatingsDto> =
        requestSeasonRatings(
            baseUrl = ImdbEpisodeRatingsConfig.IMDB_TAPFRAME_API_BASE_URL,
            showId = imdbId,
        )
}

internal object ImdbApiDevEpisodeRatingsApi {
    suspend fun getEpisodeRatings(imdbId: String): Map<Pair<Int, Int>, Double> =
        requestImdbApiDevEpisodeRatings(imdbId)
}

@Serializable
internal data class SeriesGraphEpisodeRatingDto(
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val name: String? = null,
    val tconst: String? = null,
)

@Serializable
internal data class SeriesGraphSeasonRatingsDto(
    val episodes: List<SeriesGraphEpisodeRatingDto>? = null,
)

@Serializable
private data class ImdbApiDevEpisodesResponseDto(
    val episodes: List<ImdbApiDevEpisodeDto>? = null,
)

@Serializable
private data class ImdbApiDevEpisodeDto(
    val season: String? = null,
    val episodeNumber: Int? = null,
    val rating: ImdbApiDevRatingDto? = null,
)

@Serializable
private data class ImdbApiDevRatingDto(
    val aggregateRating: Double? = null,
)

private val seriesGraphLog = Logger.withTag("SeriesGraphApi")
private val seriesGraphJson = Json { ignoreUnknownKeys = true }

private suspend fun requestSeasonRatings(
    baseUrl: String,
    showId: String,
): List<SeriesGraphSeasonRatingsDto> {
    val resolvedBaseUrl = baseUrl.trim().trimEnd('/')
    if (resolvedBaseUrl.isBlank()) return emptyList()

    return runCatching {
        val response = httpRequestRaw(
            method = "GET",
            url = "$resolvedBaseUrl/api/shows/$showId/season-ratings",
            headers = mapOf("Accept" to "application/json"),
            body = "",
        )
        if (response.status !in 200..299 || response.body.isBlank()) {
            seriesGraphLog.w { "Season ratings request failed for $showId (${response.status})" }
            return emptyList()
        }
        seriesGraphJson.decodeFromString<List<SeriesGraphSeasonRatingsDto>>(response.body)
    }.onFailure { error ->
        seriesGraphLog.w(error) { "Season ratings request failed for $showId" }
    }.getOrDefault(emptyList())
}

private suspend fun requestImdbApiDevEpisodeRatings(imdbId: String): Map<Pair<Int, Int>, Double> =
    runCatching {
        val response = httpRequestRaw(
            method = "GET",
            url = "https://api.imdbapi.dev/titles/${imdbId.trim()}/episodes",
            headers = mapOf("Accept" to "application/json"),
            body = "",
        )
        if (response.status !in 200..299 || response.body.isBlank()) {
            seriesGraphLog.w { "IMDb API episode ratings request failed for $imdbId (${response.status})" }
            return emptyMap()
        }
        val body = seriesGraphJson.decodeFromString<ImdbApiDevEpisodesResponseDto>(response.body)
        buildMap {
            body.episodes.orEmpty().forEach { episode ->
                val seasonNumber = episode.season?.toIntOrNull() ?: return@forEach
                val episodeNumber = episode.episodeNumber ?: return@forEach
                val rating = episode.rating?.aggregateRating?.takeIf { it > 0.0 } ?: return@forEach
                put(seasonNumber to episodeNumber, rating)
            }
        }
    }.onFailure { error ->
        seriesGraphLog.w(error) { "IMDb API episode ratings request failed for $imdbId" }
    }.getOrDefault(emptyMap())
