package com.nuvio.app.features.player

import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.player.skip.NextEpisodeThresholdMode
import com.nuvio.app.features.streams.StreamAutoPlayMode
import com.nuvio.app.features.streams.StreamAutoPlaySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val DefaultPreferredSubtitleLanguage = "en"
private const val DefaultStreamReuseLastLinkEnabled = true
private const val DefaultStreamReuseLastLinkCacheHours = 168
private const val DefaultNelflixHoldSpeed = 10f
private const val DefaultNelflixAnimeSkipClientId = "co7yoH241AnjAG70LIkkXPzDbJlMQUPJ"

data class PlayerSettingsUiState(
    val showLoadingOverlay: Boolean = true,
    val resizeMode: PlayerResizeMode = PlayerResizeMode.Fit,
    val holdToSpeedEnabled: Boolean = true,
    val holdToSpeedValue: Float = DefaultNelflixHoldSpeed,
    val useClearlogoInPlayer: Boolean = false,
    val externalPlayerEnabled: Boolean = false,
    val externalPlayerId: String? = ExternalPlayerPlatform.defaultPlayerId(),
    val preferredAudioLanguage: String = AudioLanguageOption.DEFAULT,
    val secondaryPreferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String = DefaultPreferredSubtitleLanguage,
    val secondaryPreferredSubtitleLanguage: String? = null,
    val subtitleStyle: SubtitleStyleState = SubtitleStyleState.DEFAULT,
    val streamReuseLastLinkEnabled: Boolean = DefaultStreamReuseLastLinkEnabled,
    val streamReuseLastLinkCacheHours: Int = DefaultStreamReuseLastLinkCacheHours,
    val decoderPriority: Int = 1,
    val mapDV7ToHevc: Boolean = false,
    val tunnelingEnabled: Boolean = false,
    val mpvHardwareDecodingEnabled: Boolean = true,
    val mpvFontsDirectoryUri: String? = null,
    val mpvConfigDirectoryUri: String? = null,
    val mpvConf: String = "",
    val mpvInputConf: String = "",
    val mpvDemuxerMaxBytesMiB: Int = DefaultMpvDemuxerMaxBytesMiB,
    val streamAutoPlayMode: StreamAutoPlayMode = StreamAutoPlayMode.MANUAL,
    val streamAutoPlaySource: StreamAutoPlaySource = StreamAutoPlaySource.ALL_SOURCES,
    val streamAutoPlaySelectedAddons: Set<String> = emptySet(),
    val streamAutoPlaySelectedPlugins: Set<String> = emptySet(),
    val streamAutoPlayRegex: String = "",
    val streamAutoPlayTimeoutSeconds: Int = 3,
    val skipIntroEnabled: Boolean = true,
    val animeSkipEnabled: Boolean = true,
    val animeSkipClientId: String = DefaultNelflixAnimeSkipClientId,
    val introDbApiKey: String = "",
    val introSubmitEnabled: Boolean = false,
    val streamAutoPlayNextEpisodeEnabled: Boolean = false,
    val streamAutoPlayPreferBingeGroup: Boolean = true,
    val nextEpisodeThresholdMode: NextEpisodeThresholdMode = NextEpisodeThresholdMode.PERCENTAGE,
    val nextEpisodeThresholdPercent: Float = 99f,
    val nextEpisodeThresholdMinutesBeforeEnd: Float = 2f,
    val useLibass: Boolean = false,
    val libassRenderType: String = "CUES",
)

object PlayerSettingsRepository {
    private val _uiState = MutableStateFlow(PlayerSettingsUiState())
    val uiState: StateFlow<PlayerSettingsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var showLoadingOverlay = true
    private var resizeMode = PlayerResizeMode.Fit
    private var holdToSpeedEnabled = true
    private var holdToSpeedValue = DefaultNelflixHoldSpeed
    private var useClearlogoInPlayer = false
    private var externalPlayerEnabled = false
    private var externalPlayerId: String? = ExternalPlayerPlatform.defaultPlayerId()
    private var preferredAudioLanguage = AudioLanguageOption.DEFAULT
    private var secondaryPreferredAudioLanguage: String? = null
    private var preferredSubtitleLanguage = DefaultPreferredSubtitleLanguage
    private var secondaryPreferredSubtitleLanguage: String? = null
    private var subtitleStyle = SubtitleStyleState.DEFAULT
    private var streamReuseLastLinkEnabled = DefaultStreamReuseLastLinkEnabled
    private var streamReuseLastLinkCacheHours = DefaultStreamReuseLastLinkCacheHours
    private var decoderPriority = 1
    private var mapDV7ToHevc = false
    private var tunnelingEnabled = false
    private var mpvHardwareDecodingEnabled = true
    private var mpvFontsDirectoryUri: String? = null
    private var mpvConfigDirectoryUri: String? = null
    private var mpvConf = DefaultMpvConf
    private var mpvInputConf = ""
    private var mpvDemuxerMaxBytesMiB = DefaultMpvDemuxerMaxBytesMiB
    private var streamAutoPlayMode = StreamAutoPlayMode.MANUAL
    private var streamAutoPlaySource = StreamAutoPlaySource.ALL_SOURCES
    private var streamAutoPlaySelectedAddons: Set<String> = emptySet()
    private var streamAutoPlaySelectedPlugins: Set<String> = emptySet()
    private var streamAutoPlayRegex = ""
    private var streamAutoPlayTimeoutSeconds = 3
    private var skipIntroEnabled = true
    private var animeSkipEnabled = true
    private var animeSkipClientId = DefaultNelflixAnimeSkipClientId
    private var introDbApiKey = ""
    private var introSubmitEnabled = false
    private var streamAutoPlayNextEpisodeEnabled = false
    private var streamAutoPlayPreferBingeGroup = true
    private var nextEpisodeThresholdMode = NextEpisodeThresholdMode.PERCENTAGE
    private var nextEpisodeThresholdPercent = 99f
    private var nextEpisodeThresholdMinutesBeforeEnd = 2f
    private var useLibass = false
    private var libassRenderType = "CUES"

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun clearLocalState() {
        hasLoaded = false
        showLoadingOverlay = true
        resizeMode = PlayerResizeMode.Fit
        holdToSpeedEnabled = true
        holdToSpeedValue = DefaultNelflixHoldSpeed
        useClearlogoInPlayer = false
        externalPlayerEnabled = false
        externalPlayerId = ExternalPlayerPlatform.defaultPlayerId()
        preferredAudioLanguage = AudioLanguageOption.DEFAULT
        secondaryPreferredAudioLanguage = null
        preferredSubtitleLanguage = DefaultPreferredSubtitleLanguage
        secondaryPreferredSubtitleLanguage = null
        subtitleStyle = SubtitleStyleState.DEFAULT
        streamReuseLastLinkEnabled = DefaultStreamReuseLastLinkEnabled
        streamReuseLastLinkCacheHours = DefaultStreamReuseLastLinkCacheHours
        decoderPriority = 1
        mapDV7ToHevc = false
        tunnelingEnabled = false
        mpvHardwareDecodingEnabled = true
        mpvFontsDirectoryUri = null
        mpvConfigDirectoryUri = null
        mpvConf = DefaultMpvConf
        mpvInputConf = ""
        mpvDemuxerMaxBytesMiB = DefaultMpvDemuxerMaxBytesMiB
        streamAutoPlayMode = StreamAutoPlayMode.MANUAL
        streamAutoPlaySource = StreamAutoPlaySource.ALL_SOURCES
        streamAutoPlaySelectedAddons = emptySet()
        streamAutoPlaySelectedPlugins = emptySet()
        streamAutoPlayRegex = ""
        streamAutoPlayTimeoutSeconds = 3
        skipIntroEnabled = true
        animeSkipEnabled = true
        animeSkipClientId = DefaultNelflixAnimeSkipClientId
        introDbApiKey = ""
        introSubmitEnabled = false
        streamAutoPlayNextEpisodeEnabled = false
        streamAutoPlayPreferBingeGroup = true
        nextEpisodeThresholdMode = NextEpisodeThresholdMode.PERCENTAGE
        nextEpisodeThresholdPercent = 99f
        nextEpisodeThresholdMinutesBeforeEnd = 2f
        useLibass = false
        libassRenderType = "CUES"
        publish()
    }

    private fun loadFromDisk() {
        hasLoaded = true
        showLoadingOverlay = PlayerSettingsStorage.loadShowLoadingOverlay() ?: true
        resizeMode = PlayerSettingsStorage.loadResizeMode()
            ?.let { runCatching { PlayerResizeMode.valueOf(it) }.getOrNull() }
            ?: PlayerResizeMode.Fit
        holdToSpeedEnabled = PlayerSettingsStorage.loadHoldToSpeedEnabled() ?: true
        holdToSpeedValue = DefaultNelflixHoldSpeed
        PlayerSettingsStorage.saveHoldToSpeedValue(DefaultNelflixHoldSpeed)
        useClearlogoInPlayer = PlayerSettingsStorage.loadUseClearlogoInPlayer() ?: false
        externalPlayerEnabled = PlayerSettingsStorage.loadExternalPlayerEnabled() ?: false
        externalPlayerId = PlayerSettingsStorage.loadExternalPlayerId()
            ?: ExternalPlayerPlatform.defaultPlayerId()
        preferredAudioLanguage =
            normalizeLanguageCode(PlayerSettingsStorage.loadPreferredAudioLanguage())
                ?: AudioLanguageOption.DEFAULT
        secondaryPreferredAudioLanguage =
            normalizeLanguageCode(PlayerSettingsStorage.loadSecondaryPreferredAudioLanguage())
        preferredSubtitleLanguage =
            normalizeLanguageCode(PlayerSettingsStorage.loadPreferredSubtitleLanguage())
                ?: DefaultPreferredSubtitleLanguage
        secondaryPreferredSubtitleLanguage =
            normalizeLanguageCode(PlayerSettingsStorage.loadSecondaryPreferredSubtitleLanguage())
        subtitleStyle = SubtitleStyleState(
            textColor = subtitleColorFromStorage(PlayerSettingsStorage.loadSubtitleTextColor())
                ?: SubtitleStyleState.DEFAULT.textColor,
            outlineEnabled = PlayerSettingsStorage.loadSubtitleOutlineEnabled()
                ?: SubtitleStyleState.DEFAULT.outlineEnabled,
            fontSizeSp = PlayerSettingsStorage.loadSubtitleFontSizeSp()
                ?: SubtitleStyleState.DEFAULT.fontSizeSp,
            bottomOffset = PlayerSettingsStorage.loadSubtitleBottomOffset()
                ?: SubtitleStyleState.DEFAULT.bottomOffset,
        )
        streamReuseLastLinkEnabled = DefaultStreamReuseLastLinkEnabled
        streamReuseLastLinkCacheHours = DefaultStreamReuseLastLinkCacheHours
        PlayerSettingsStorage.saveStreamReuseLastLinkEnabled(DefaultStreamReuseLastLinkEnabled)
        PlayerSettingsStorage.saveStreamReuseLastLinkCacheHours(DefaultStreamReuseLastLinkCacheHours)
        decoderPriority = PlayerSettingsStorage.loadDecoderPriority() ?: 1
        mapDV7ToHevc = PlayerSettingsStorage.loadMapDV7ToHevc() ?: false
        tunnelingEnabled = PlayerSettingsStorage.loadTunnelingEnabled() ?: false
        mpvHardwareDecodingEnabled = true
        PlayerSettingsStorage.saveMpvHardwareDecodingEnabled(true)
        mpvFontsDirectoryUri = PlayerSettingsStorage.loadMpvFontsDirectoryUri()
        mpvConfigDirectoryUri = PlayerSettingsStorage.loadMpvConfigDirectoryUri()
        val storedMpvConf = PlayerSettingsStorage.loadMpvConf()
        mpvConf = storedMpvConf?.takeIf { it.isNotBlank() } ?: DefaultMpvConf
        if (storedMpvConf.isNullOrBlank() ||
            storedMpvConf == LegacyDefaultMpvConf ||
            storedMpvConf == LegacyGpuNextDefaultMpvConf ||
            storedMpvConf == LegacyForwardOnlyDefaultMpvConf
        ) {
            mpvConf = DefaultMpvConf
            PlayerSettingsStorage.saveMpvConf(DefaultMpvConf)
        }
        mpvInputConf = PlayerSettingsStorage.loadMpvInputConf() ?: ""
        mpvDemuxerMaxBytesMiB = PlayerSettingsStorage.loadMpvDemuxerMaxBytesMiB()
            ?.coerceIn(128, 4096)
            ?: DefaultMpvDemuxerMaxBytesMiB
        PlayerSettingsStorage.saveMpvDemuxerMaxBytesMiB(mpvDemuxerMaxBytesMiB)
        streamAutoPlayMode = StreamAutoPlayMode.MANUAL
        PlayerSettingsStorage.saveStreamAutoPlayMode(StreamAutoPlayMode.MANUAL.name)
        streamAutoPlaySource = PlayerSettingsStorage.loadStreamAutoPlaySource()
            ?.let { runCatching { StreamAutoPlaySource.valueOf(it) }.getOrNull() }
            ?: StreamAutoPlaySource.ALL_SOURCES
        streamAutoPlaySelectedAddons = PlayerSettingsStorage.loadStreamAutoPlaySelectedAddons() ?: emptySet()
        streamAutoPlaySelectedPlugins = PlayerSettingsStorage.loadStreamAutoPlaySelectedPlugins() ?: emptySet()
        if (!AppFeaturePolicy.pluginsEnabled) {
            val normalizedSource = normalizeStreamAutoPlaySource(streamAutoPlaySource)
            if (normalizedSource != streamAutoPlaySource) {
                streamAutoPlaySource = normalizedSource
                PlayerSettingsStorage.saveStreamAutoPlaySource(normalizedSource.name)
            }
            if (streamAutoPlaySelectedPlugins.isNotEmpty()) {
                streamAutoPlaySelectedPlugins = emptySet()
                PlayerSettingsStorage.saveStreamAutoPlaySelectedPlugins(emptySet())
            }
        }
        streamAutoPlayRegex = PlayerSettingsStorage.loadStreamAutoPlayRegex() ?: ""
        streamAutoPlayTimeoutSeconds = PlayerSettingsStorage.loadStreamAutoPlayTimeoutSeconds() ?: 3
        skipIntroEnabled = true
        animeSkipEnabled = true
        animeSkipClientId = DefaultNelflixAnimeSkipClientId
        PlayerSettingsStorage.saveSkipIntroEnabled(true)
        PlayerSettingsStorage.saveAnimeSkipEnabled(true)
        PlayerSettingsStorage.saveAnimeSkipClientId(DefaultNelflixAnimeSkipClientId)
        introDbApiKey = PlayerSettingsStorage.loadIntroDbApiKey() ?: ""
        introSubmitEnabled = PlayerSettingsStorage.loadIntroSubmitEnabled() ?: false
        streamAutoPlayNextEpisodeEnabled = PlayerSettingsStorage.loadStreamAutoPlayNextEpisodeEnabled() ?: false
        streamAutoPlayPreferBingeGroup = PlayerSettingsStorage.loadStreamAutoPlayPreferBingeGroup() ?: true
        nextEpisodeThresholdMode = PlayerSettingsStorage.loadNextEpisodeThresholdMode()
            ?.let { runCatching { NextEpisodeThresholdMode.valueOf(it) }.getOrNull() }
            ?: NextEpisodeThresholdMode.PERCENTAGE
        nextEpisodeThresholdPercent = PlayerSettingsStorage.loadNextEpisodeThresholdPercent() ?: 99f
        nextEpisodeThresholdMinutesBeforeEnd = PlayerSettingsStorage.loadNextEpisodeThresholdMinutesBeforeEnd() ?: 2f
        useLibass = PlayerSettingsStorage.loadUseLibass() ?: false
        libassRenderType = PlayerSettingsStorage.loadLibassRenderType() ?: "CUES"
        publish()
    }

    fun setShowLoadingOverlay(enabled: Boolean) {
        ensureLoaded()
        if (showLoadingOverlay == enabled) return
        showLoadingOverlay = enabled
        publish()
        PlayerSettingsStorage.saveShowLoadingOverlay(enabled)
    }

    fun setResizeMode(mode: PlayerResizeMode) {
        ensureLoaded()
        if (resizeMode == mode) return
        resizeMode = mode
        publish()
        PlayerSettingsStorage.saveResizeMode(mode.name)
    }

    fun setHoldToSpeedEnabled(enabled: Boolean) {
        ensureLoaded()
        if (holdToSpeedEnabled == enabled) return
        holdToSpeedEnabled = enabled
        publish()
        PlayerSettingsStorage.saveHoldToSpeedEnabled(enabled)
    }

    fun setHoldToSpeedValue(speed: Float) {
        ensureLoaded()
        val normalized = DefaultNelflixHoldSpeed
        if (holdToSpeedValue == normalized) return
        holdToSpeedValue = normalized
        publish()
        PlayerSettingsStorage.saveHoldToSpeedValue(normalized)
    }

    fun setUseClearlogoInPlayer(enabled: Boolean) {
        ensureLoaded()
        if (useClearlogoInPlayer == enabled) return
        useClearlogoInPlayer = enabled
        publish()
        PlayerSettingsStorage.saveUseClearlogoInPlayer(enabled)
    }

    fun setExternalPlayerEnabled(enabled: Boolean) {
        ensureLoaded()
        if (enabled && externalPlayerId.isNullOrBlank()) {
            externalPlayerId = ExternalPlayerPlatform.defaultPlayerId()
                ?: ExternalPlayerPlatform.availablePlayers().firstOrNull()?.id
            PlayerSettingsStorage.saveExternalPlayerId(externalPlayerId)
        }
        if (externalPlayerEnabled == enabled) {
            publish()
            return
        }
        externalPlayerEnabled = enabled
        publish()
        PlayerSettingsStorage.saveExternalPlayerEnabled(enabled)
    }

    fun setExternalPlayerId(playerId: String?) {
        ensureLoaded()
        val normalized = playerId?.takeIf { it.isNotBlank() }
        if (externalPlayerId == normalized) return
        externalPlayerId = normalized
        publish()
        PlayerSettingsStorage.saveExternalPlayerId(normalized)
    }

    fun setPreferredAudioLanguage(language: String) {
        ensureLoaded()
        val normalized = normalizeLanguageCode(language) ?: AudioLanguageOption.DEVICE
        if (preferredAudioLanguage == normalized) return
        preferredAudioLanguage = normalized
        publish()
        PlayerSettingsStorage.savePreferredAudioLanguage(normalized)
    }

    fun setSecondaryPreferredAudioLanguage(language: String?) {
        ensureLoaded()
        val normalized = normalizeLanguageCode(language)
        if (secondaryPreferredAudioLanguage == normalized) return
        secondaryPreferredAudioLanguage = normalized
        publish()
        PlayerSettingsStorage.saveSecondaryPreferredAudioLanguage(normalized)
    }

    fun setPreferredSubtitleLanguage(language: String) {
        ensureLoaded()
        val normalized = normalizeLanguageCode(language) ?: SubtitleLanguageOption.NONE
        if (preferredSubtitleLanguage == normalized) return
        preferredSubtitleLanguage = normalized
        publish()
        PlayerSettingsStorage.savePreferredSubtitleLanguage(normalized)
    }

    fun setSecondaryPreferredSubtitleLanguage(language: String?) {
        ensureLoaded()
        val normalized = normalizeLanguageCode(language)
        if (secondaryPreferredSubtitleLanguage == normalized) return
        secondaryPreferredSubtitleLanguage = normalized
        publish()
        PlayerSettingsStorage.saveSecondaryPreferredSubtitleLanguage(normalized)
    }

    fun setSubtitleStyle(style: SubtitleStyleState) {
        ensureLoaded()
        if (subtitleStyle == style) return
        subtitleStyle = style
        publish()
        PlayerSettingsStorage.saveSubtitleTextColor(style.textColor.toStorageHexString())
        PlayerSettingsStorage.saveSubtitleOutlineEnabled(style.outlineEnabled)
        PlayerSettingsStorage.saveSubtitleFontSizeSp(style.fontSizeSp)
        PlayerSettingsStorage.saveSubtitleBottomOffset(style.bottomOffset)
    }

    fun setStreamReuseLastLinkEnabled(enabled: Boolean) {
        ensureLoaded()
        if (streamReuseLastLinkEnabled == DefaultStreamReuseLastLinkEnabled) return
        streamReuseLastLinkEnabled = DefaultStreamReuseLastLinkEnabled
        publish()
        PlayerSettingsStorage.saveStreamReuseLastLinkEnabled(DefaultStreamReuseLastLinkEnabled)
    }

    fun setStreamReuseLastLinkCacheHours(hours: Int) {
        ensureLoaded()
        if (streamReuseLastLinkCacheHours == DefaultStreamReuseLastLinkCacheHours) return
        streamReuseLastLinkCacheHours = DefaultStreamReuseLastLinkCacheHours
        publish()
        PlayerSettingsStorage.saveStreamReuseLastLinkCacheHours(DefaultStreamReuseLastLinkCacheHours)
    }

    fun setDecoderPriority(priority: Int) {
        ensureLoaded()
        if (decoderPriority == priority) return
        decoderPriority = priority
        publish()
        PlayerSettingsStorage.saveDecoderPriority(priority)
    }

    fun setMapDV7ToHevc(enabled: Boolean) {
        ensureLoaded()
        if (mapDV7ToHevc == enabled) return
        mapDV7ToHevc = enabled
        publish()
        PlayerSettingsStorage.saveMapDV7ToHevc(enabled)
    }

    fun setTunnelingEnabled(enabled: Boolean) {
        ensureLoaded()
        if (tunnelingEnabled == enabled) return
        tunnelingEnabled = enabled
        publish()
        PlayerSettingsStorage.saveTunnelingEnabled(enabled)
    }

    fun setMpvHardwareDecodingEnabled(enabled: Boolean) {
        ensureLoaded()
        if (mpvHardwareDecodingEnabled == enabled) return
        mpvHardwareDecodingEnabled = enabled
        publish()
        PlayerSettingsStorage.saveMpvHardwareDecodingEnabled(enabled)
    }

    fun setMpvFontsDirectoryUri(uri: String?) {
        ensureLoaded()
        val normalized = uri?.takeIf { it.isNotBlank() }
        if (mpvFontsDirectoryUri == normalized) return
        mpvFontsDirectoryUri = normalized
        publish()
        PlayerSettingsStorage.saveMpvFontsDirectoryUri(normalized)
    }

    fun setMpvConfigDirectoryUri(uri: String?) {
        ensureLoaded()
        val normalized = uri?.takeIf { it.isNotBlank() }
        if (mpvConfigDirectoryUri == normalized) return
        mpvConfigDirectoryUri = normalized
        publish()
        PlayerSettingsStorage.saveMpvConfigDirectoryUri(normalized)
    }

    fun setMpvConf(value: String) {
        ensureLoaded()
        if (mpvConf == value) return
        mpvConf = value
        publish()
        PlayerSettingsStorage.saveMpvConf(value)
    }

    fun setMpvInputConf(value: String) {
        ensureLoaded()
        if (mpvInputConf == value) return
        mpvInputConf = value
        publish()
        PlayerSettingsStorage.saveMpvInputConf(value)
    }

    fun setMpvDemuxerMaxBytesMiB(value: Int) {
        ensureLoaded()
        val normalized = value.coerceIn(128, 4096)
        if (mpvDemuxerMaxBytesMiB == normalized) return
        mpvDemuxerMaxBytesMiB = normalized
        publish()
        PlayerSettingsStorage.saveMpvDemuxerMaxBytesMiB(normalized)
    }

    fun setStreamAutoPlayMode(mode: StreamAutoPlayMode) {
        ensureLoaded()
        if (streamAutoPlayMode == StreamAutoPlayMode.MANUAL) return
        streamAutoPlayMode = StreamAutoPlayMode.MANUAL
        publish()
        PlayerSettingsStorage.saveStreamAutoPlayMode(StreamAutoPlayMode.MANUAL.name)
    }

    fun setStreamAutoPlaySource(source: StreamAutoPlaySource) {
        ensureLoaded()
        val normalizedSource = normalizeStreamAutoPlaySource(source)
        if (streamAutoPlaySource == normalizedSource) return
        streamAutoPlaySource = normalizedSource
        publish()
        PlayerSettingsStorage.saveStreamAutoPlaySource(normalizedSource.name)
    }

    fun setStreamAutoPlaySelectedAddons(addons: Set<String>) {
        ensureLoaded()
        if (streamAutoPlaySelectedAddons == addons) return
        streamAutoPlaySelectedAddons = addons
        publish()
        PlayerSettingsStorage.saveStreamAutoPlaySelectedAddons(addons)
    }

    fun setStreamAutoPlaySelectedPlugins(plugins: Set<String>) {
        ensureLoaded()
        val normalizedPlugins = if (AppFeaturePolicy.pluginsEnabled) plugins else emptySet()
        if (streamAutoPlaySelectedPlugins == normalizedPlugins) return
        streamAutoPlaySelectedPlugins = normalizedPlugins
        publish()
        PlayerSettingsStorage.saveStreamAutoPlaySelectedPlugins(normalizedPlugins)
    }

    fun setStreamAutoPlayRegex(regex: String) {
        ensureLoaded()
        if (streamAutoPlayRegex == regex) return
        streamAutoPlayRegex = regex
        publish()
        PlayerSettingsStorage.saveStreamAutoPlayRegex(regex)
    }

    fun setStreamAutoPlayTimeoutSeconds(seconds: Int) {
        ensureLoaded()
        if (streamAutoPlayTimeoutSeconds == seconds) return
        streamAutoPlayTimeoutSeconds = seconds
        publish()
        PlayerSettingsStorage.saveStreamAutoPlayTimeoutSeconds(seconds)
    }

    fun setSkipIntroEnabled(enabled: Boolean) {
        ensureLoaded()
        if (skipIntroEnabled) return
        skipIntroEnabled = true
        publish()
        PlayerSettingsStorage.saveSkipIntroEnabled(true)
    }

    fun setAnimeSkipEnabled(enabled: Boolean) {
        ensureLoaded()
        if (animeSkipEnabled) return
        animeSkipEnabled = true
        publish()
        PlayerSettingsStorage.saveAnimeSkipEnabled(true)
    }

    fun setAnimeSkipClientId(clientId: String) {
        ensureLoaded()
        if (animeSkipClientId == DefaultNelflixAnimeSkipClientId) return
        animeSkipClientId = DefaultNelflixAnimeSkipClientId
        publish()
        PlayerSettingsStorage.saveAnimeSkipClientId(DefaultNelflixAnimeSkipClientId)
    }

    fun setIntroDbApiKey(apiKey: String) {
        ensureLoaded()
        if (introDbApiKey == apiKey) return
        introDbApiKey = apiKey
        publish()
        PlayerSettingsStorage.saveIntroDbApiKey(apiKey)
    }

    fun setIntroSubmitEnabled(enabled: Boolean) {
        ensureLoaded()
        if (introSubmitEnabled == enabled) return
        introSubmitEnabled = enabled
        publish()
        PlayerSettingsStorage.saveIntroSubmitEnabled(enabled)
    }

    fun setStreamAutoPlayNextEpisodeEnabled(enabled: Boolean) {
        ensureLoaded()
        if (streamAutoPlayNextEpisodeEnabled == enabled) return
        streamAutoPlayNextEpisodeEnabled = enabled
        publish()
        PlayerSettingsStorage.saveStreamAutoPlayNextEpisodeEnabled(enabled)
    }

    fun setStreamAutoPlayPreferBingeGroup(enabled: Boolean) {
        ensureLoaded()
        if (streamAutoPlayPreferBingeGroup == enabled) return
        streamAutoPlayPreferBingeGroup = enabled
        publish()
        PlayerSettingsStorage.saveStreamAutoPlayPreferBingeGroup(enabled)
    }

    fun setNextEpisodeThresholdMode(mode: NextEpisodeThresholdMode) {
        ensureLoaded()
        if (nextEpisodeThresholdMode == mode) return
        nextEpisodeThresholdMode = mode
        publish()
        PlayerSettingsStorage.saveNextEpisodeThresholdMode(mode.name)
    }

    fun setNextEpisodeThresholdPercent(percent: Float) {
        ensureLoaded()
        if (nextEpisodeThresholdPercent == percent) return
        nextEpisodeThresholdPercent = percent
        publish()
        PlayerSettingsStorage.saveNextEpisodeThresholdPercent(percent)
    }

    fun setNextEpisodeThresholdMinutesBeforeEnd(minutes: Float) {
        ensureLoaded()
        if (nextEpisodeThresholdMinutesBeforeEnd == minutes) return
        nextEpisodeThresholdMinutesBeforeEnd = minutes
        publish()
        PlayerSettingsStorage.saveNextEpisodeThresholdMinutesBeforeEnd(minutes)
    }

    fun setUseLibass(enabled: Boolean) {
        ensureLoaded()
        if (useLibass == enabled) return
        useLibass = enabled
        publish()
        PlayerSettingsStorage.saveUseLibass(enabled)
    }

    fun setLibassRenderType(renderType: String) {
        ensureLoaded()
        if (libassRenderType == renderType) return
        libassRenderType = renderType
        publish()
        PlayerSettingsStorage.saveLibassRenderType(renderType)
    }

    private fun publish() {
        _uiState.value = PlayerSettingsUiState(
            showLoadingOverlay = showLoadingOverlay,
            resizeMode = resizeMode,
            holdToSpeedEnabled = holdToSpeedEnabled,
            holdToSpeedValue = holdToSpeedValue,
            useClearlogoInPlayer = useClearlogoInPlayer,
            externalPlayerEnabled = externalPlayerEnabled,
            externalPlayerId = externalPlayerId,
            preferredAudioLanguage = preferredAudioLanguage,
            secondaryPreferredAudioLanguage = secondaryPreferredAudioLanguage,
            preferredSubtitleLanguage = preferredSubtitleLanguage,
            secondaryPreferredSubtitleLanguage = secondaryPreferredSubtitleLanguage,
            subtitleStyle = subtitleStyle,
            streamReuseLastLinkEnabled = streamReuseLastLinkEnabled,
            streamReuseLastLinkCacheHours = streamReuseLastLinkCacheHours,
            decoderPriority = decoderPriority,
            mapDV7ToHevc = mapDV7ToHevc,
            tunnelingEnabled = tunnelingEnabled,
            mpvHardwareDecodingEnabled = mpvHardwareDecodingEnabled,
            mpvFontsDirectoryUri = mpvFontsDirectoryUri,
            mpvConfigDirectoryUri = mpvConfigDirectoryUri,
            mpvConf = mpvConf,
            mpvInputConf = mpvInputConf,
            mpvDemuxerMaxBytesMiB = mpvDemuxerMaxBytesMiB,
            streamAutoPlayMode = streamAutoPlayMode,
            streamAutoPlaySource = streamAutoPlaySource,
            streamAutoPlaySelectedAddons = streamAutoPlaySelectedAddons,
            streamAutoPlaySelectedPlugins = streamAutoPlaySelectedPlugins,
            streamAutoPlayRegex = streamAutoPlayRegex,
            streamAutoPlayTimeoutSeconds = streamAutoPlayTimeoutSeconds,
            skipIntroEnabled = skipIntroEnabled,
            animeSkipEnabled = animeSkipEnabled,
            animeSkipClientId = animeSkipClientId,
            introDbApiKey = introDbApiKey,
            introSubmitEnabled = introSubmitEnabled,
            streamAutoPlayNextEpisodeEnabled = streamAutoPlayNextEpisodeEnabled,
            streamAutoPlayPreferBingeGroup = streamAutoPlayPreferBingeGroup,
            nextEpisodeThresholdMode = nextEpisodeThresholdMode,
            nextEpisodeThresholdPercent = nextEpisodeThresholdPercent,
            nextEpisodeThresholdMinutesBeforeEnd = nextEpisodeThresholdMinutesBeforeEnd,
            useLibass = useLibass,
            libassRenderType = libassRenderType,
        )
    }

    private fun normalizeStreamAutoPlaySource(source: StreamAutoPlaySource): StreamAutoPlaySource {
        return if (!AppFeaturePolicy.pluginsEnabled && source == StreamAutoPlaySource.ENABLED_PLUGINS_ONLY) {
            StreamAutoPlaySource.ALL_SOURCES
        } else {
            source
        }
    }
}

const val DefaultMpvDemuxerMaxBytesMiB = 1024
const val DefaultMpvDemuxerMaxBackBytesMiB = 128

const val DefaultMpvConf = """vo=gpu
save-position-on-quit
volume=100
volume-max=100
alang=jpn,jp,jap,Japanese,eng,en,enUS,en-US,English
audio-file-auto=fuzzy
sub-ass-override=force
sub-visibility=yes
sub-font="Helvetica"
sub-font-size=35
sub-border-style=opaque-box
sub-line-spacing=12.5
sub-ass-line-spacing=12.5
sub-shadow-offset=1
sub-back-color="#82000000"
sub-color="#ffffff"
sub-border-size=0
sub-pos=93
sub-ass-force-style=FontName=Helvetica
slang=eng,en,enUS,en-US,English
vd-lavc-dr=no
demuxer-max-bytes=1024MiB
demuxer-max-back-bytes=128MiB
cache=yes
blend-subtitles=yes
osd-font-size=18
sub-auto=fuzzy
sub-ass-force-style=Bold=0,Italic=0
sub-bold=no
sub-italic=no
"""

private const val LegacyGpuNextDefaultMpvConf = """vo=gpu-next
save-position-on-quit
volume=100
volume-max=100
alang=jpn,jp,jap,Japanese,eng,en,enUS,en-US,English
audio-file-auto=fuzzy
sub-ass-override=force
sub-visibility=yes
sub-font="Helvetica"
sub-font-size=35
sub-border-style=opaque-box
sub-line-spacing=12.5
sub-ass-line-spacing=12.5
sub-shadow-offset=1
sub-back-color="#82000000"
sub-color="#ffffff"
sub-border-size=0
sub-pos=93
sub-ass-force-style=FontName=Helvetica
slang=eng,en,enUS,en-US,English
demuxer-max-bytes=1024MiB
demuxer-max-back-bytes=128MiB
cache=yes
blend-subtitles=yes
osd-font-size=18
sub-auto=fuzzy
sub-ass-force-style=Bold=0,Italic=0
sub-bold=no
sub-italic=no
"""

private const val LegacyForwardOnlyDefaultMpvConf = """vo=gpu-next
save-position-on-quit
volume=100
volume-max=100
alang=jpn,jp,jap,Japanese,eng,en,enUS,en-US,English
audio-file-auto=fuzzy
sub-ass-override=force
sub-visibility=yes
sub-font="Helvetica"
sub-font-size=35
sub-border-style=opaque-box
sub-line-spacing=12.5
sub-ass-line-spacing=12.5
sub-shadow-offset=1
sub-back-color="#82000000"
sub-color="#ffffff"
sub-border-size=0
sub-pos=93
sub-ass-force-style=FontName=Helvetica
slang=eng,en,enUS,en-US,English
demuxer-max-bytes=1024MiB
cache=yes
blend-subtitles=yes
osd-font-size=18
sub-auto=fuzzy
sub-ass-force-style=Bold=0,Italic=0
sub-bold=no
sub-italic=no
"""

private const val LegacyDefaultMpvConf = """vo=gpu-next
save-position-on-quit
volume=100
volume-max=100
alang=jpn,jp,jap,Japanese,eng,en,enUS,en-US,English
audio-file-auto=fuzzy
sub-ass-override=force
sub-visibility=yes
sub-font="Helvetica"
sub-font-size=35
sub-border-style=opaque-box
sub-line-spacing=12.5
sub-ass-line-spacing=12.5
sub-shadow-offset=1
sub-back-color="#82000000"
sub-color="#ffffff"
sub-border-size=0
sub-pos=93
sub-ass-force-style=FontName=Helvetica
slang=eng,en,enUS,en-US,English
demuxer-max-bytes=2000MiB
cache=yes
blend-subtitles=yes
osd-font-size=18
sub-auto=fuzzy
sub-ass-force-style=Bold=0,Italic=0
sub-bold=no
sub-italic=no
"""
