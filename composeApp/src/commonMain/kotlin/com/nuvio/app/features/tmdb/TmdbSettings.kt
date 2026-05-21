package com.nuvio.app.features.tmdb

data class TmdbSettings(
    val enabled: Boolean = true,
    val apiKey: String = DefaultNelflixTmdbApiKey,
    val language: String = "en",
    val useTrailers: Boolean = true,
    val useArtwork: Boolean = true,
    val useBasicInfo: Boolean = true,
    val useDetails: Boolean = true,
    val useCredits: Boolean = true,
    val useProductions: Boolean = true,
    val useNetworks: Boolean = true,
    val useEpisodes: Boolean = true,
    val useSeasonPosters: Boolean = true,
    val useMoreLikeThis: Boolean = true,
    val useCollections: Boolean = true,
) {
    val hasApiKey: Boolean
        get() = apiKey.isNotBlank()
}

const val DefaultNelflixTmdbApiKey = "be81ce98c6eee5edb31216413be5fcda"
