package com.nuvio.app.features.tmdb

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TmdbSettingsRepository {
    private val _uiState = MutableStateFlow(TmdbSettings())
    val uiState: StateFlow<TmdbSettings> = _uiState.asStateFlow()

    private var hasLoaded = false

    private var enabled = true
    private var apiKey = DefaultNelflixTmdbApiKey
    private var language = "en"
    private var useTrailers = true
    private var useArtwork = true
    private var useBasicInfo = true
    private var useDetails = true
    private var useCredits = true
    private var useProductions = true
    private var useNetworks = true
    private var useEpisodes = true
    private var useSeasonPosters = true
    private var useMoreLikeThis = true
    private var useCollections = true

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun snapshot(): TmdbSettings {
        ensureLoaded()
        return _uiState.value
    }

    fun setEnabled(value: Boolean) {
        ensureLoaded()
        if (enabled) return
        enabled = true
        publish()
        TmdbSettingsStorage.saveEnabled(true)
    }

    fun setApiKey(value: String) {
        ensureLoaded()
        val normalized = DefaultNelflixTmdbApiKey
        if (apiKey == normalized && enabled) return
        apiKey = normalized
        enabled = true
        publish()
        TmdbSettingsStorage.saveApiKey(normalized)
        TmdbSettingsStorage.saveEnabled(true)
    }

    fun setLanguage(value: String) {
        ensureLoaded()
        val normalized = "en"
        if (language == normalized) return
        language = normalized
        publish()
        TmdbSettingsStorage.saveLanguage(normalized)
    }

    fun setUseTrailers(value: Boolean) = setBoolean(
        current = useTrailers,
        next = value,
        update = { useTrailers = it },
        persist = TmdbSettingsStorage::saveUseTrailers,
    )

    fun setUseArtwork(value: Boolean) = setBoolean(
        current = useArtwork,
        next = value,
        update = { useArtwork = it },
        persist = TmdbSettingsStorage::saveUseArtwork,
    )

    fun setUseBasicInfo(value: Boolean) = setBoolean(
        current = useBasicInfo,
        next = value,
        update = { useBasicInfo = it },
        persist = TmdbSettingsStorage::saveUseBasicInfo,
    )

    fun setUseDetails(value: Boolean) = setBoolean(
        current = useDetails,
        next = value,
        update = { useDetails = it },
        persist = TmdbSettingsStorage::saveUseDetails,
    )

    fun setUseCredits(value: Boolean) = setBoolean(
        current = useCredits,
        next = value,
        update = { useCredits = it },
        persist = TmdbSettingsStorage::saveUseCredits,
    )

    fun setUseProductions(value: Boolean) = setBoolean(
        current = useProductions,
        next = value,
        update = { useProductions = it },
        persist = TmdbSettingsStorage::saveUseProductions,
    )

    fun setUseNetworks(value: Boolean) = setBoolean(
        current = useNetworks,
        next = value,
        update = { useNetworks = it },
        persist = TmdbSettingsStorage::saveUseNetworks,
    )

    fun setUseEpisodes(value: Boolean) = setBoolean(
        current = useEpisodes,
        next = value,
        update = { useEpisodes = it },
        persist = TmdbSettingsStorage::saveUseEpisodes,
    )

    fun setUseSeasonPosters(value: Boolean) = setBoolean(
        current = useSeasonPosters,
        next = value,
        update = { useSeasonPosters = it },
        persist = TmdbSettingsStorage::saveUseSeasonPosters,
    )

    fun setUseMoreLikeThis(value: Boolean) = setBoolean(
        current = useMoreLikeThis,
        next = value,
        update = { useMoreLikeThis = it },
        persist = TmdbSettingsStorage::saveUseMoreLikeThis,
    )

    fun setUseCollections(value: Boolean) = setBoolean(
        current = useCollections,
        next = value,
        update = { useCollections = it },
        persist = TmdbSettingsStorage::saveUseCollections,
    )

    private fun setBoolean(
        current: Boolean,
        next: Boolean,
        update: (Boolean) -> Unit,
        persist: (Boolean) -> Unit,
    ) {
        ensureLoaded()
        if (current) return
        update(true)
        publish()
        persist(true)
    }

    private fun loadFromDisk() {
        hasLoaded = true
        apiKey = DefaultNelflixTmdbApiKey
        enabled = true
        language = "en"
        useTrailers = true
        useArtwork = true
        useBasicInfo = true
        useDetails = true
        useCredits = true
        useProductions = true
        useNetworks = true
        useEpisodes = true
        useSeasonPosters = true
        useMoreLikeThis = true
        useCollections = true
        persistForcedDefaults()
        publish()
    }

    private fun persistForcedDefaults() {
        TmdbSettingsStorage.saveApiKey(DefaultNelflixTmdbApiKey)
        TmdbSettingsStorage.saveEnabled(true)
        TmdbSettingsStorage.saveLanguage("en")
        TmdbSettingsStorage.saveUseTrailers(true)
        TmdbSettingsStorage.saveUseArtwork(true)
        TmdbSettingsStorage.saveUseBasicInfo(true)
        TmdbSettingsStorage.saveUseDetails(true)
        TmdbSettingsStorage.saveUseCredits(true)
        TmdbSettingsStorage.saveUseProductions(true)
        TmdbSettingsStorage.saveUseNetworks(true)
        TmdbSettingsStorage.saveUseEpisodes(true)
        TmdbSettingsStorage.saveUseSeasonPosters(true)
        TmdbSettingsStorage.saveUseMoreLikeThis(true)
        TmdbSettingsStorage.saveUseCollections(true)
    }

    private fun publish() {
        _uiState.value = TmdbSettings(
            enabled = enabled,
            apiKey = apiKey,
            language = language,
            useTrailers = useTrailers,
            useArtwork = useArtwork,
            useBasicInfo = useBasicInfo,
            useDetails = useDetails,
            useCredits = useCredits,
            useProductions = useProductions,
            useNetworks = useNetworks,
            useEpisodes = useEpisodes,
            useSeasonPosters = useSeasonPosters,
            useMoreLikeThis = useMoreLikeThis,
            useCollections = useCollections,
        )
    }
}

internal fun normalizeLanguage(value: String?): String {
    val trimmed = value?.trim()?.replace('_', '-') ?: return ""
    return trimmed.takeIf { it.isNotBlank() } ?: ""
}
