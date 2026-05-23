package com.nuvio.app.features.watchprogress

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val DefaultShowUnairedNextUp = false
private val DefaultContinueWatchingSortMode = ContinueWatchingSortMode.STREAMING_STYLE

@Serializable
private data class StoredContinueWatchingPreferences(
    val isVisible: Boolean = true,
    val style: ContinueWatchingSectionStyle = ContinueWatchingSectionStyle.Wide,
    val upNextFromFurthestEpisode: Boolean = true,
    @SerialName("use_episode_thumbnails_in_cw")
    val useEpisodeThumbnails: Boolean = true,
    @SerialName("use_clearlogo_in_cw")
    val useClearlogo: Boolean = true,
    @SerialName("show_unaired_next_up")
    val showUnairedNextUp: Boolean = DefaultShowUnairedNextUp,
    @SerialName("blur_continue_watching_next_up")
    val blurNextUp: Boolean = false,
    val dismissedNextUpKeys: Set<String> = emptySet(),
    val showResumePromptOnLaunch: Boolean = true,
    @SerialName("sort_mode")
    val sortMode: ContinueWatchingSortMode = DefaultContinueWatchingSortMode,
)

object ContinueWatchingPreferencesRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(ContinueWatchingPreferencesUiState())
    val uiState: StateFlow<ContinueWatchingPreferencesUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun clearLocalState() {
        hasLoaded = false
        _uiState.value = ContinueWatchingPreferencesUiState()
    }

    internal fun applyFromSync(
        isVisible: Boolean,
        style: ContinueWatchingSectionStyle,
        upNextFromFurthestEpisode: Boolean,
        useEpisodeThumbnails: Boolean = true,
        useClearlogo: Boolean = true,
        showUnairedNextUp: Boolean = DefaultShowUnairedNextUp,
        blurNextUp: Boolean = false,
        dismissedNextUpKeys: Set<String>,
    ) {
        ensureLoaded()
        _uiState.value = _uiState.value.copy(
            isVisible = true,
            style = ContinueWatchingSectionStyle.Wide,
            upNextFromFurthestEpisode = true,
            useEpisodeThumbnails = true,
            useClearlogo = true,
            showUnairedNextUp = DefaultShowUnairedNextUp,
            blurNextUp = false,
            dismissedNextUpKeys = dismissedNextUpKeys
                .map(String::trim)
                .filter(String::isNotBlank)
                .toSet(),
            sortMode = DefaultContinueWatchingSortMode,
        )
        persist()
    }

    private fun loadFromDisk() {
        hasLoaded = true

        val payload = ContinueWatchingPreferencesStorage.loadPayload().orEmpty().trim()
        if (payload.isEmpty()) {
            _uiState.value = ContinueWatchingPreferencesUiState()
            persist()
            return
        }

        val stored = runCatching {
            json.decodeFromString<StoredContinueWatchingPreferences>(payload)
        }.getOrNull()

        _uiState.value = if (stored != null) {
            ContinueWatchingPreferencesUiState(
                isVisible = true,
                style = ContinueWatchingSectionStyle.Wide,
                upNextFromFurthestEpisode = true,
                useEpisodeThumbnails = true,
                useClearlogo = true,
                showUnairedNextUp = DefaultShowUnairedNextUp,
                blurNextUp = false,
                dismissedNextUpKeys = stored.dismissedNextUpKeys,
                showResumePromptOnLaunch = true,
                sortMode = DefaultContinueWatchingSortMode,
            )
        } else {
            ContinueWatchingPreferencesUiState()
        }
        persist()
    }

    fun setVisible(isVisible: Boolean) {
        ensureLoaded()
        _uiState.value = _uiState.value.copy(isVisible = true)
        persist()
    }

    fun setStyle(style: ContinueWatchingSectionStyle) {
        ensureLoaded()
        _uiState.value = _uiState.value.copy(style = ContinueWatchingSectionStyle.Wide)
        persist()
    }

    fun setUpNextFromFurthestEpisode(enabled: Boolean) {
        ensureLoaded()
        _uiState.value = _uiState.value.copy(upNextFromFurthestEpisode = true)
        persist()
    }

    fun setUseEpisodeThumbnails(enabled: Boolean) {
        ensureLoaded()
        _uiState.value = _uiState.value.copy(useEpisodeThumbnails = true)
        persist()
    }

    fun setUseClearlogo(enabled: Boolean) {
        ensureLoaded()
        _uiState.value = _uiState.value.copy(useClearlogo = true)
        persist()
    }

    fun setShowUnairedNextUp(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.showUnairedNextUp == DefaultShowUnairedNextUp) return
        _uiState.value = _uiState.value.copy(showUnairedNextUp = DefaultShowUnairedNextUp)
        persist()
    }

    fun setBlurNextUp(enabled: Boolean) {
        ensureLoaded()
        _uiState.value = _uiState.value.copy(blurNextUp = false)
        persist()
    }

    fun addDismissedNextUpKey(key: String) {
        ensureLoaded()
        val normalizedKey = key.trim()
        if (normalizedKey.isBlank()) return
        val current = _uiState.value.dismissedNextUpKeys
        if (normalizedKey in current) return
        _uiState.value = _uiState.value.copy(dismissedNextUpKeys = current + normalizedKey)
        persist()
    }

    fun setShowResumePromptOnLaunch(enabled: Boolean) {
        ensureLoaded()
        _uiState.value = _uiState.value.copy(showResumePromptOnLaunch = true)
        persist()
    }

    fun setSortMode(mode: ContinueWatchingSortMode) {
        ensureLoaded()
        if (_uiState.value.sortMode == DefaultContinueWatchingSortMode) return
        _uiState.value = _uiState.value.copy(sortMode = DefaultContinueWatchingSortMode)
        persist()
    }

    fun removeDismissedNextUpKeysForContent(contentId: String) {
        ensureLoaded()
        val normalizedContentId = contentId.trim()
        if (normalizedContentId.isBlank()) return
        val prefix = "$normalizedContentId|"
        val filtered = _uiState.value.dismissedNextUpKeys.filterNot { it.startsWith(prefix) }.toSet()
        if (filtered == _uiState.value.dismissedNextUpKeys) return
        _uiState.value = _uiState.value.copy(dismissedNextUpKeys = filtered)
        persist()
    }

    private fun persist() {
        ContinueWatchingPreferencesStorage.savePayload(
            json.encodeToString(
                StoredContinueWatchingPreferences(
            isVisible = true,
            style = ContinueWatchingSectionStyle.Wide,
            upNextFromFurthestEpisode = true,
            useEpisodeThumbnails = true,
            useClearlogo = true,
            showUnairedNextUp = DefaultShowUnairedNextUp,
            blurNextUp = false,
            dismissedNextUpKeys = _uiState.value.dismissedNextUpKeys,
            showResumePromptOnLaunch = true,
                    sortMode = DefaultContinueWatchingSortMode,
                ),
            ),
        )
    }
}
