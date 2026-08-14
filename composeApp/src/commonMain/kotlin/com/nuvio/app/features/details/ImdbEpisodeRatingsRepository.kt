package com.nuvio.app.features.details

import co.touchlab.kermit.Logger
import com.nuvio.app.core.cache.withBoundedEntry
import com.nuvio.app.features.library.LibraryClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

object ImdbEpisodeRatingsRepository {
    private data class CacheEntry(
        val ratings: Map<Pair<Int, Int>, Double>,
        val expiresAtMs: Long,
    )

    private data class ProviderHealth(
        val consecutiveFailures: Int,
        val retryAfterMs: Long,
    )

    private class RatingsProvider(
        val key: String,
        val request: suspend () -> List<SeriesGraphSeasonRatingsDto>,
    )

    private val log = Logger.withTag("ImdbEpisodeRatingsRepo")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    @Volatile
    private var cache: Map<String, CacheEntry> = emptyMap()
    @Volatile
    private var providerHealth: Map<String, ProviderHealth> = emptyMap()
    private val inFlight = mutableMapOf<String, Deferred<Map<Pair<Int, Int>, Double>>>()

    suspend fun getEpisodeRatings(
        imdbId: String?,
        tmdbId: Int?,
    ): Map<Pair<Int, Int>, Double> {
        val normalizedImdbId = normalizeImdbId(imdbId)
        val normalizedTmdbId = tmdbId?.takeIf { it > 0 }
        if (normalizedImdbId == null && normalizedTmdbId == null) return emptyMap()

        val cacheKey = "imdb:${normalizedImdbId.orEmpty()}|tmdb:${normalizedTmdbId ?: ""}"
        val now = currentTimeMs()
        val staleEntry = mutex.withLock {
            cache[cacheKey]?.also { cached ->
                if (cached.expiresAtMs > now) return cached.ratings
            }
        }

        val deferred = mutex.withLock {
            inFlight[cacheKey] ?: scope.async {
                try {
                    val fetchedRatings = fetchEpisodeRatings(
                        imdbId = normalizedImdbId,
                        tmdbId = normalizedTmdbId,
                    )
                    val ratings = fetchedRatings.ifEmpty { staleEntry?.ratings.orEmpty() }
                    mutex.withLock {
                        val writtenAtMs = currentTimeMs()
                        val freshEntries = cache.filterValues { it.expiresAtMs > writtenAtMs }
                        cache = freshEntries.withBoundedEntry(
                            key = cacheKey,
                            value = CacheEntry(
                                ratings = ratings,
                                expiresAtMs = writtenAtMs + episodeRatingsCacheTtlMs(fetchedRatings),
                            ),
                            maxEntries = MAX_CACHE_ENTRIES,
                        )
                    }
                    ratings
                } finally {
                    mutex.withLock {
                        inFlight.remove(cacheKey)
                    }
                }
            }.also { created ->
                inFlight[cacheKey] = created
            }
        }

        return deferred.await()
    }

    fun clearCache() {
        cache = emptyMap()
        providerHealth = emptyMap()
        inFlight.clear()
    }

    private suspend fun fetchEpisodeRatings(
        imdbId: String?,
        tmdbId: Int?,
    ): Map<Pair<Int, Int>, Double> {
        val providers = buildList {
            if (!imdbId.isNullOrBlank()) {
                add(
                    RatingsProvider(key = "tapframe") {
                        ImdbTapframeApi.getSeasonRatings(imdbId)
                    },
                )
            }
            if (tmdbId != null) {
                add(
                    RatingsProvider(key = "configured") {
                        SeriesGraphApi.getSeasonRatings(tmdbId)
                    },
                )
            }
        }

        providers.forEach { provider ->
            val ratings = fetchFromProvider(provider)
            if (ratings.isNotEmpty()) return ratings
        }

        return emptyMap()
    }

    private suspend fun fetchFromProvider(
        provider: RatingsProvider,
    ): Map<Pair<Int, Int>, Double> {
        val now = currentTimeMs()
        val canRequest = mutex.withLock {
            providerHealth[provider.key]?.retryAfterMs?.let { it <= now } ?: true
        }
        if (!canRequest) return emptyMap()

        val ratings = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
            seriesGraphEpisodeRatings(provider.request())
        }.orEmpty()

        mutex.withLock {
            if (ratings.isNotEmpty()) {
                providerHealth = providerHealth - provider.key
            } else {
                val failures = (providerHealth[provider.key]?.consecutiveFailures ?: 0) + 1
                val cooldownMultiplier = 1L shl (failures - 1).coerceIn(0, 4)
                providerHealth = providerHealth + (
                    provider.key to ProviderHealth(
                        consecutiveFailures = failures,
                        retryAfterMs = now + (PROVIDER_INITIAL_COOLDOWN_MS * cooldownMultiplier)
                            .coerceAtMost(PROVIDER_MAX_COOLDOWN_MS),
                    )
                )
                log.w { "Episode ratings provider ${provider.key} returned no usable ratings" }
            }
        }
        return ratings
    }

    private fun normalizeImdbId(value: String?): String? =
        extractImdbTitleId(value)

    private fun currentTimeMs(): Long = LibraryClock.nowEpochMs()

    private const val SUCCESS_CACHE_TTL_MS = 6L * 60L * 60L * 1000L
    private const val EMPTY_CACHE_TTL_MS = 2L * 60L * 1000L
    private const val PROVIDER_TIMEOUT_MS = 8_000L
    private const val PROVIDER_INITIAL_COOLDOWN_MS = 30_000L
    private const val PROVIDER_MAX_COOLDOWN_MS = 5L * 60L * 1000L
    private const val MAX_CACHE_ENTRIES = 24

    internal fun cacheTtlMsForTests(ratings: Map<Pair<Int, Int>, Double>): Long =
        episodeRatingsCacheTtlMs(ratings)

    private fun episodeRatingsCacheTtlMs(ratings: Map<Pair<Int, Int>, Double>): Long =
        if (ratings.isEmpty()) EMPTY_CACHE_TTL_MS else SUCCESS_CACHE_TTL_MS
}

internal fun seriesGraphEpisodeRatings(
    payload: List<SeriesGraphSeasonRatingsDto>,
): Map<Pair<Int, Int>, Double> = buildMap {
    payload.forEach { season ->
        season.episodes.orEmpty().forEach { episode ->
            val seasonNumber = episode.seasonNumber?.takeIf { it >= 0 } ?: return@forEach
            val episodeNumber = episode.episodeNumber?.takeIf { it > 0 } ?: return@forEach
            val voteAverage = validImdbEpisodeRating(episode.voteAverage) ?: return@forEach
            put(seasonNumber to episodeNumber, voteAverage)
        }
    }
}

internal fun extractImdbTitleId(value: String?): String? =
    value
        ?.let { Regex("tt[0-9]+", RegexOption.IGNORE_CASE).find(it)?.value }
        ?.lowercase()

internal fun extractTmdbTitleId(value: String?): Int? {
    val parts = value
        ?.trim()
        ?.split(':', '/', '?', '&', '=')
        ?.map(String::trim)
        .orEmpty()
    val tmdbIndex = parts.indexOfFirst { it.equals("tmdb", ignoreCase = true) }
    if (tmdbIndex < 0) return null
    return parts.drop(tmdbIndex + 1).firstNotNullOfOrNull(String::toIntOrNull)
}

internal fun parseValidImdbEpisodeRating(value: String?): Double? = value
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?.let { Regex("[0-9]+(?:\\.[0-9]+)?").find(it)?.value }
    ?.toDoubleOrNull()
    ?.let(::validImdbEpisodeRating)

internal fun validImdbEpisodeRating(value: Double?): Double? = value
    ?.takeIf { it.isFinite() && it > 0.0 && it <= 10.0 }

internal fun episodeMetadataImdbRatings(
    videos: List<MetaVideo>,
): Map<Pair<Int, Int>, Double> = buildMap {
    videos.forEach { episode ->
        val season = episode.season?.takeIf { it >= 0 } ?: return@forEach
        val number = episode.episode?.takeIf { it > 0 } ?: return@forEach
        val rating = parseValidImdbEpisodeRating(episode.imdbRating) ?: return@forEach
        put(season to number, rating)
    }
}

internal fun mergeEpisodeImdbRatings(
    providerRatings: Map<Pair<Int, Int>, Double>,
    metadataRatings: Map<Pair<Int, Int>, Double>,
): Map<Pair<Int, Int>, Double> = buildMap {
    providerRatings.forEach { (key, value) ->
        validImdbEpisodeRating(value)?.let { put(key, it) }
    }
    metadataRatings.forEach { (key, value) ->
        validImdbEpisodeRating(value)?.let { put(key, it) }
    }
}
