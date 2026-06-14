package com.nuvio.app.features.player.skip

import com.nuvio.app.features.player.PlayerSettingsRepository

object SkipIntroRepository {

    private val cache = HashMap<String, List<SkipInterval>>()
    private val animeSkipShowIdCache = HashMap<String, String>()
    private const val NO_ID = "__none__"

    private val introDbConfigured: Boolean
        get() = IntroDbConfig.URL.isNotBlank()

    suspend fun getSkipIntervals(
        imdbId: String?,
        tmdbId: Int?,
        malId: String?,
        anilistId: String?,
        season: Int,
        episode: Int,
        durationMs: Long,
    ): List<SkipInterval> {
        val settings = PlayerSettingsRepository.uiState.value
        if (!settings.skipIntroEnabled) return emptyList()

        val cacheKey = listOf(
            "all",
            imdbId.orEmpty(),
            tmdbId?.toString().orEmpty(),
            malId.orEmpty(),
            anilistId.orEmpty(),
            season.toString(),
            episode.toString(),
            durationMs.takeIf { it > 0L }?.toString().orEmpty(),
        ).joinToString(":")
        cache[cacheKey]?.let { return it }

        val theIntroDbResult = fetchFromTheIntroDb(
            tmdbId = tmdbId,
            imdbId = imdbId,
            season = season,
            episode = episode,
            durationMs = durationMs,
        )
        if (theIntroDbResult.isNotEmpty()) return theIntroDbResult.also { cache[cacheKey] = it }

        if (introDbConfigured) {
            val result = imdbId?.let { fetchFromIntroDb(it, season, episode) }.orEmpty()
            if (result.isNotEmpty()) return result.also { cache[cacheKey] = it }
        }

        val aniSkipResult = malId?.let { fetchFromAniSkip(it, episode) }.orEmpty()
        if (aniSkipResult.isNotEmpty()) return aniSkipResult.also { cache[cacheKey] = it }

        val animeSkipResult = anilistId?.let {
            fetchFromAnimeSkip(it, episode, season = season)
        }.orEmpty()
        if (animeSkipResult.isNotEmpty()) return animeSkipResult.also { cache[cacheKey] = it }

        return emptyList<SkipInterval>().also { cache[cacheKey] = it }
    }

    suspend fun getSkipIntervals(imdbId: String?, season: Int, episode: Int): List<SkipInterval> {
        return getSkipIntervals(
            imdbId = imdbId,
            tmdbId = null,
            malId = null,
            anilistId = null,
            season = season,
            episode = episode,
            durationMs = 0L,
        )
    }

    private suspend fun fetchFromTheIntroDb(
        tmdbId: Int?,
        imdbId: String?,
        season: Int,
        episode: Int,
        durationMs: Long,
    ): List<SkipInterval> {
        return try {
            val data = SkipIntroApi.getTheIntroDbMedia(
                tmdbId = tmdbId,
                imdbId = imdbId,
                season = season,
                episode = episode,
                durationMs = durationMs,
            ) ?: return emptyList()
            buildList {
                addAll(data.intro.mapNotNull { it.toSkipIntervalOrNull("intro", durationMs) })
                addAll(data.recap.mapNotNull { it.toSkipIntervalOrNull("recap", durationMs) })
                addAll(data.credits.mapNotNull { it.toSkipIntervalOrNull("outro", durationMs) })
                addAll(data.preview.mapNotNull { it.toSkipIntervalOrNull("preview", durationMs) })
            }.sortedBy { it.startTime }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getSkipIntervalsForMal(malId: String, episode: Int): List<SkipInterval> {
        val settings = PlayerSettingsRepository.uiState.value
        if (!settings.skipIntroEnabled) return emptyList()

        val cacheKey = "mal:$malId:$episode"
        cache[cacheKey]?.let { return it }

        val aniSkipResult = fetchFromAniSkip(malId, episode)
        if (aniSkipResult.isNotEmpty()) return aniSkipResult.also { cache[cacheKey] = it }

        return emptyList<SkipInterval>().also { cache[cacheKey] = it }
    }

    suspend fun getSkipIntervalsForAnilist(anilistId: String, episode: Int, season: Int?): List<SkipInterval> {
        val settings = PlayerSettingsRepository.uiState.value
        if (!settings.skipIntroEnabled) return emptyList()

        val cacheKey = "anilist:$anilistId:$season:$episode"
        cache[cacheKey]?.let { return it }

        val result = fetchFromAnimeSkip(anilistId, episode, season = season)
        if (result.isNotEmpty()) return result.also { cache[cacheKey] = it }

        return emptyList<SkipInterval>().also { cache[cacheKey] = it }
    }

    private suspend fun fetchFromIntroDb(imdbId: String, season: Int, episode: Int): List<SkipInterval> {
        return try {
            val data = SkipIntroApi.getIntroDbSegments(imdbId, season, episode)
            if (data == null) return emptyList()
            listOfNotNull(
                data.intro.toSkipIntervalOrNull("intro"),
                data.recap.toSkipIntervalOrNull("recap"),
                data.outro.toSkipIntervalOrNull("outro"),
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun IntroDbSegment?.toSkipIntervalOrNull(type: String): SkipInterval? {
        if (this == null) return null
        val start = startSec ?: startMs?.let { it / 1000.0 }
        val end = endSec ?: endMs?.let { it / 1000.0 }
        if (start == null || end == null || end <= start) return null
        return SkipInterval(startTime = start, endTime = end, type = type, provider = "introdb")
    }

    private fun TheIntroDbSegment.toSkipIntervalOrNull(type: String, durationMs: Long): SkipInterval? {
        val start = startSec ?: startMs?.let { it / 1000.0 } ?: 0.0
        val end = endSec
            ?: endMs?.let { it / 1000.0 }
            ?: durationMs.takeIf { it > 0L }?.let { it / 1000.0 }
            ?: return null
        if (end <= start) return null
        return SkipInterval(startTime = start, endTime = end, type = type, provider = "theintrodb")
    }

    private suspend fun fetchFromAniSkip(malId: String, episode: Int): List<SkipInterval> {
        return try {
            val response = SkipIntroApi.getAniSkipTimes(malId, episode)
            if (response == null) return emptyList()
            if (!response.found) return emptyList()
            response.results?.map { result ->
                SkipInterval(
                    startTime = result.interval.startTime,
                    endTime = result.interval.endTime,
                    type = result.skipType,
                    provider = "aniskip",
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchFromAnimeSkip(anilistId: String, episode: Int, season: Int?): List<SkipInterval> {
        val settings = PlayerSettingsRepository.uiState.value
        val clientId = settings.animeSkipClientId.trim()
        if (clientId.isBlank()) return emptyList()
        if (!settings.animeSkipEnabled) return emptyList()

        return try {
            val showIds = resolveAnimeSkipShowIds(anilistId, clientId)
            if (showIds.isEmpty()) return emptyList()

            for (showId in showIds) {
                val query = "{ findEpisodesByShowId(showId: \"$showId\") { season number timestamps { at type { name } } } }"
                val response = SkipIntroApi.queryAnimeSkip(clientId, query) ?: continue
                val episodes = response.data?.findEpisodesByShowId ?: continue

                val targetEpisode = episodes.firstOrNull { ep ->
                    ep.number?.toIntOrNull() == episode &&
                        (season == null || ep.season?.toIntOrNull() == season)
                } ?: continue

                val sorted = (targetEpisode.timestamps ?: continue).sortedBy { it.at }
                val result = sorted.mapIndexedNotNull { i, ts ->
                    val endTime = sorted.getOrNull(i + 1)?.at ?: Double.MAX_VALUE
                    val type = when (ts.type.name.lowercase()) {
                        "intro", "new intro" -> "op"
                        "credits" -> "ed"
                        "recap" -> "recap"
                        else -> return@mapIndexedNotNull null
                    }
                    SkipInterval(startTime = ts.at, endTime = endTime, type = type, provider = "animeskip")
                }
                if (result.isNotEmpty()) return result
            }
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun resolveAnimeSkipShowIds(anilistId: String, clientId: String): List<String> {
        animeSkipShowIdCache[anilistId]?.let { cached ->
            return if (cached == NO_ID) emptyList() else listOf(cached)
        }
        val query = "{ findShowsByExternalId(service: ANILIST, serviceId: \"$anilistId\") { id } }"
        val showIds = try {
            SkipIntroApi.queryAnimeSkip(clientId, query)
                ?.data?.findShowsByExternalId?.map { it.id } ?: emptyList()
        } catch (_: Exception) { emptyList() }

        if (showIds.size == 1) animeSkipShowIdCache[anilistId] = showIds[0]
        else if (showIds.isEmpty()) animeSkipShowIdCache[anilistId] = NO_ID
        return showIds
    }

    suspend fun submitIntro(
        imdbId: String,
        season: Int,
        episode: Int,
        startSec: Double,
        endSec: Double,
        segmentType: String,
    ): Boolean {
        val settings = PlayerSettingsRepository.uiState.value
        val apiKey = settings.introDbApiKey.trim()
        if (!settings.introSubmitEnabled || apiKey.isBlank()) return false

        val request = SubmitIntroRequest(
            imdbId = imdbId,
            season = season,
            episode = episode,
            startSec = startSec,
            endSec = endSec,
            startMs = (startSec * 1000).toLong(),
            endMs = (endSec * 1000).toLong(),
            segmentType = segmentType,
        )

        return SkipIntroApi.submitIntro(apiKey, request)
    }

    suspend fun verifyIntroDbApiKey(apiKey: String): Boolean {
        return SkipIntroApi.verifyIntroDbApiKey(apiKey)
    }

    fun clearCache() {
        cache.clear()
        animeSkipShowIdCache.clear()
    }
}
