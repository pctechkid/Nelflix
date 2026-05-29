package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.AddonResource
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.player.skip.SkipIntroButton
import com.nuvio.app.features.player.skip.SkipIntroRepository
import com.nuvio.app.features.player.skip.SkipInterval
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.streams.StreamLinkCacheRepository
import com.nuvio.app.features.streams.StreamUrlResolver
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.trakt.TraktScrobbleRepository
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.WatchProgressPlaybackSession
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import com.nuvio.app.features.watchtogether.WatchTogetherDialog
import com.nuvio.app.features.watchtogether.WatchTogetherContentMetadata
import com.nuvio.app.features.watchtogether.WatchTogetherPlaybackPayload
import com.nuvio.app.features.watchtogether.WatchTogetherPlaybackState
import com.nuvio.app.features.watchtogether.WatchTogetherRepository
import com.nuvio.app.features.watchtogether.WatchTogetherRoomState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.roundToInt

private const val PlaybackProgressPersistIntervalMs = 60_000L
private const val PlayerDoubleTapSeekStepMs = 10_000L
private const val PlayerDoubleTapSeekResetDelayMs = 800L
private const val PlayerLockedOverlayDurationMs = 2_000L
private const val PlayerLeftGestureBoundary = 0.4f
private const val PlayerRightGestureBoundary = 0.6f
private const val PlayerVerticalGestureSensitivity = 1f
private const val WatchTogetherSoftSyncThresholdMs = 450L
private const val WatchTogetherSoftSyncSettledMs = 200L
private const val WatchTogetherHardSyncThresholdMs = 1_100L
private const val WatchTogetherHardSyncCooldownMs = 3_000L
private const val WatchTogetherSyncPollMs = 600L
private const val WatchTogetherFreshUpdateWindowMs = 1_500L
private const val WatchTogetherStaleUpdateWindowMs = 4_000L
private const val WatchTogetherFreshSpeedUpMultiplier = 1.06f
private const val WatchTogetherFreshSlowDownMultiplier = 0.94f
private const val WatchTogetherStaleSpeedUpMultiplier = 1.03f
private const val WatchTogetherStaleSlowDownMultiplier = 0.97f
private val PlayerSliderOverlayGap = 12.dp
private val PlayerTimeRowHeight = 36.dp
private val PlayerActionRowHeight = 50.dp

private fun sliderOverlayBottomPadding(metrics: PlayerLayoutMetrics) =
    metrics.sliderBottomOffset +
        metrics.sliderTouchHeight +
        PlayerTimeRowHeight +
        PlayerActionRowHeight +
        PlayerSliderOverlayGap

private enum class PlayerSideGesture {
    Volume,
}

private enum class PlayerSeekDirection {
    Backward,
    Forward,
}

private enum class PlayerGestureMode {
    HorizontalSeek,
    Volume,
}

private data class PlayerAccumulatedSeekState(
    val direction: PlayerSeekDirection,
    val baselinePositionMs: Long,
    val amountMs: Long,
)

@Composable
fun PlayerScreen(
    title: String,
    sourceUrl: String,
    fallbackRawSourceUrl: String? = null,
    sourceAudioUrl: String? = null,
    sourceHeaders: Map<String, String> = emptyMap(),
    fallbackRawSourceHeaders: Map<String, String> = emptyMap(),
    sourceResponseHeaders: Map<String, String> = emptyMap(),
    providerName: String,
    streamTitle: String,
    streamSubtitle: String?,
    initialBingeGroup: String? = null,
    pauseDescription: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    logo: String? = null,
    poster: String? = null,
    background: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    episodeTitle: String? = null,
    episodeThumbnail: String? = null,
    contentType: String? = null,
    videoId: String? = null,
    parentMetaId: String,
    parentMetaType: String,
    providerAddonId: String? = null,
    initialPositionMs: Long = 0L,
    initialProgressFraction: Float? = null,
    initialWatchTogetherRoom: WatchTogetherRoomState? = null,
) {
    LockPlayerToLandscape()
    val playerSettingsUiState by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val metaScreenSettingsUiState by remember {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchedUiState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchProgressUiState by remember {
        WatchProgressRepository.ensureLoaded()
        WatchProgressRepository.uiState
    }.collectAsStateWithLifecycle()
    val profileState by remember {
        ProfileRepository.state
    }.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val horizontalSafePadding = playerHorizontalSafePadding()
        val metrics = remember(maxWidth) { PlayerLayoutMetrics.fromWidth(maxWidth) }
        val sliderEdgePadding = horizontalSafePadding + metrics.horizontalPadding
        val overlayBottomPadding = sliderOverlayBottomPadding(metrics)
        val scope = rememberCoroutineScope()
        val hapticFeedback = LocalHapticFeedback.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val resizeModeFitLabel = stringResource(Res.string.compose_player_resize_fit)
        val resizeModeFillLabel = stringResource(Res.string.compose_player_resize_fill)
        val resizeModeZoomLabel = stringResource(Res.string.compose_player_resize_zoom)
        val downloadedLabel = stringResource(Res.string.compose_player_downloaded)
        val airsPrefix = stringResource(Res.string.compose_player_airs_prefix)
        val tbaLabel = stringResource(Res.string.compose_player_tba)
        val parentalGuideLabels = ParentalGuideLabels(
            nudity = stringResource(Res.string.parental_nudity),
            violence = stringResource(Res.string.parental_violence),
            profanity = stringResource(Res.string.parental_profanity),
            alcohol = stringResource(Res.string.parental_alcohol),
            frightening = stringResource(Res.string.parental_frightening),
            severe = stringResource(Res.string.parental_severity_severe),
            moderate = stringResource(Res.string.parental_severity_moderate),
            mild = stringResource(Res.string.parental_severity_mild),
        )
        val gestureController = rememberPlayerGestureController()
        var controlsVisible by rememberSaveable { mutableStateOf(true) }
        var playerControlsLocked by rememberSaveable { mutableStateOf(false) }
        // Active playback state (mutable to support source/episode switching)
        var activeTitle by rememberSaveable { mutableStateOf(title) }
        var activeContentType by rememberSaveable { mutableStateOf(contentType ?: parentMetaType) }
        var activeParentMetaId by rememberSaveable { mutableStateOf(parentMetaId) }
        var activeParentMetaType by rememberSaveable { mutableStateOf(parentMetaType) }
        var activeLogo by rememberSaveable { mutableStateOf(logo) }
        var activePoster by rememberSaveable { mutableStateOf(poster) }
        var activeBackground by rememberSaveable { mutableStateOf(background) }
        var activePauseDescription by rememberSaveable { mutableStateOf(pauseDescription) }
        val playerHeaderLogo = activeLogo.takeIf { playerSettingsUiState.useClearlogoInPlayer }
        var activeSourceUrl by rememberSaveable { mutableStateOf(sourceUrl) }
        var activeFallbackRawSourceUrl by rememberSaveable { mutableStateOf(fallbackRawSourceUrl) }
        var activeFallbackAlreadyTried by rememberSaveable(sourceUrl) { mutableStateOf(false) }
        var activeSourceAudioUrl by rememberSaveable { mutableStateOf(sourceAudioUrl) }
        var activeSourceHeaders by remember(sourceUrl, sourceHeaders) {
            mutableStateOf(sanitizePlaybackHeaders(sourceHeaders))
        }
        var activeSourceResponseHeaders by remember(sourceUrl, sourceResponseHeaders) {
            mutableStateOf(sanitizePlaybackResponseHeaders(sourceResponseHeaders))
        }
        var activeFallbackRawSourceHeaders by remember(sourceUrl, fallbackRawSourceHeaders) {
            mutableStateOf(sanitizePlaybackHeaders(fallbackRawSourceHeaders))
        }
        var activeStreamTitle by rememberSaveable { mutableStateOf(streamTitle) }
        var activeStreamSubtitle by rememberSaveable { mutableStateOf(streamSubtitle) }
        var activeProviderName by rememberSaveable { mutableStateOf(providerName) }
        var activeProviderAddonId by rememberSaveable { mutableStateOf(providerAddonId) }
        var currentStreamBingeGroup by rememberSaveable { mutableStateOf(initialBingeGroup) }
        var activeSeasonNumber by rememberSaveable { mutableStateOf(seasonNumber) }
        var activeEpisodeNumber by rememberSaveable { mutableStateOf(episodeNumber) }
        var activeEpisodeTitle by rememberSaveable { mutableStateOf(episodeTitle) }
        var activeEpisodeThumbnail by rememberSaveable { mutableStateOf(episodeThumbnail) }
        var activeVideoId by rememberSaveable { mutableStateOf(videoId) }
        var activeInitialPositionMs by rememberSaveable { mutableStateOf(initialPositionMs) }
        var activeInitialProgressFraction by rememberSaveable { mutableStateOf(initialProgressFraction) }
        var shouldPlay by rememberSaveable(activeSourceUrl) { mutableStateOf(true) }
        var resizeMode by rememberSaveable(playerSettingsUiState.resizeMode) {
            mutableStateOf(playerSettingsUiState.resizeMode)
        }
        var layoutSize by remember { mutableStateOf(IntSize.Zero) }
        var playbackSnapshot by remember { mutableStateOf(PlayerPlaybackSnapshot()) }
        var playerController by remember { mutableStateOf<PlayerEngineController?>(null) }
        var playerControllerSourceUrl by remember { mutableStateOf<String?>(null) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var showWatchTogetherDialog by rememberSaveable { mutableStateOf(false) }
        var watchTogetherJoinCode by rememberSaveable { mutableStateOf("") }
        var watchTogetherSession by remember { mutableStateOf(initialWatchTogetherRoom) }
        var watchTogetherBusy by remember { mutableStateOf(false) }
        var watchTogetherError by remember { mutableStateOf<String?>(null) }
        var watchTogetherMetadataForcedVisible by remember { mutableStateOf(false) }
        var lastWatchTogetherMemberNames by remember { mutableStateOf<List<String>>(emptyList()) }
        var lastAppliedWatchTogetherUpdateMs by remember { mutableStateOf(0L) }
        var lastWatchTogetherHardSyncAtMs by remember { mutableStateOf(0L) }
        var watchTogetherSoftSyncActive by remember { mutableStateOf(false) }
        val keepScreenAwake = errorMessage == null &&
            (playbackSnapshot.isPlaying || (shouldPlay && playbackSnapshot.isLoading))
        EnterImmersivePlayerMode(keepScreenAwake = keepScreenAwake)
        var isScrubbingTimeline by remember { mutableStateOf(false) }
        var scrubbingPositionMs by remember { mutableStateOf<Long?>(null) }
        var pausedOverlayVisible by remember { mutableStateOf(false) }
        var gestureFeedback by remember { mutableStateOf<GestureFeedbackState?>(null) }
        var liveGestureFeedback by remember { mutableStateOf<GestureFeedbackState?>(null) }
        var renderedGestureFeedback by remember { mutableStateOf<GestureFeedbackState?>(null) }
        var lockedOverlayVisible by remember { mutableStateOf(false) }
        var gestureMessageJob by remember { mutableStateOf<Job?>(null) }
        var accumulatedSeekResetJob by remember { mutableStateOf<Job?>(null) }
        var accumulatedSeekState by remember { mutableStateOf<PlayerAccumulatedSeekState?>(null) }
        var initialLoadCompleted by remember(activeSourceUrl) { mutableStateOf(false) }
        var speedBoostRestoreSpeed by remember(activeSourceUrl) { mutableStateOf<Float?>(null) }
        var isHoldToSpeedGestureActive by remember(activeSourceUrl) { mutableStateOf(false) }
        var initialSeekApplied by remember(activeSourceUrl, activeInitialPositionMs, activeInitialProgressFraction) {
            val initialProgressFraction = activeInitialProgressFraction
            mutableStateOf(
                activeInitialPositionMs <= 0L &&
                    (initialProgressFraction == null || initialProgressFraction <= 0f),
            )
        }
        var lastProgressPersistEpochMs by remember(activeSourceUrl) { mutableStateOf(0L) }
        var previousIsPlaying by remember(activeSourceUrl) { mutableStateOf(false) }
        var hasRequestedScrobbleStartForCurrentItem by remember(
            activeSourceUrl,
            activeVideoId,
            activeSeasonNumber,
            activeEpisodeNumber,
        ) { mutableStateOf(false) }
        var hasSentCompletionScrobbleForCurrentItem by remember(
            activeVideoId,
            activeSeasonNumber,
            activeEpisodeNumber,
        ) { mutableStateOf(false) }
        val backdropArtwork = activeBackground ?: activePoster
        val displayedPositionMs = scrubbingPositionMs ?: playbackSnapshot.positionMs
        val isEpisode = activeSeasonNumber != null && activeEpisodeNumber != null
        val currentGestureFeedback = liveGestureFeedback ?: gestureFeedback
        val showManualPauseMetadata = watchTogetherMetadataForcedVisible
        val metadataOverlayVisible = !playerControlsLocked &&
            (pausedOverlayVisible || showManualPauseMetadata) &&
            (!controlsVisible || showManualPauseMetadata)

        LaunchedEffect(currentGestureFeedback) {
            if (currentGestureFeedback != null) {
                renderedGestureFeedback = currentGestureFeedback
            }
        }

        LaunchedEffect(playerController, metadataOverlayVisible) {
            playerController?.setSubtitleVisibility(!metadataOverlayVisible)
        }

        var showSubmitIntroModal by remember { mutableStateOf(false) }
        var submitIntroSegmentType by rememberSaveable { mutableStateOf("intro") }
        var submitIntroStartTimeStr by rememberSaveable { mutableStateOf("00:00") }
        var submitIntroEndTimeStr by rememberSaveable { mutableStateOf("00:00") }
        val metaUiState by MetaDetailsRepository.uiState.collectAsStateWithLifecycle()
        val isSeries = activeParentMetaType == "series"
        val maturityRatingCode = metaUiState.meta
            ?.takeIf { it.id == activeParentMetaId && it.type == activeParentMetaType }
            ?.ageRating
        val maturityGenresLine = metaUiState.meta
            ?.takeIf { it.id == activeParentMetaId && it.type == activeParentMetaType }
            ?.genres
            ?.take(4)
            ?.joinToString(", ")
        var imdbMaturityGenresLine by remember { mutableStateOf<String?>(null) }

        // Skip intro/outro/recap state
        var skipIntervals by remember { mutableStateOf<List<SkipInterval>>(emptyList()) }
        var activeSkipInterval by remember { mutableStateOf<SkipInterval?>(null) }
        var skipIntervalDismissed by remember { mutableStateOf(false) }

        // Parental guide overlay state
        var parentalWarnings by remember { mutableStateOf<List<ParentalWarning>>(emptyList()) }
        var showParentalGuide by remember { mutableStateOf(false) }
        var parentalGuideHasShown by remember { mutableStateOf(false) }
        var playbackStartedForParentalGuide by remember { mutableStateOf(false) }

        ManagePlayerPictureInPicture(
            isPlaying = playbackSnapshot.isPlaying,
            playerSize = layoutSize,
        )

        val playbackSession = remember(
            activeContentType,
            activeParentMetaId,
            activeParentMetaType,
            activeVideoId,
            activeTitle,
            activeLogo,
            activePoster,
            activeBackground,
            activeSeasonNumber,
            activeEpisodeNumber,
            activeEpisodeTitle,
            activeEpisodeThumbnail,
            activeProviderName,
            activeProviderAddonId,
            activeStreamTitle,
            activeStreamSubtitle,
            activePauseDescription,
            activeSourceUrl,
            activeSourceAudioUrl,
        ) {
            WatchProgressPlaybackSession(
                contentType = activeContentType,
                parentMetaId = activeParentMetaId,
                parentMetaType = activeParentMetaType,
                videoId = activeVideoId?.takeIf { it.isNotBlank() } ?: buildPlaybackVideoId(
                    parentMetaId = activeParentMetaId,
                    seasonNumber = activeSeasonNumber,
                    episodeNumber = activeEpisodeNumber,
                    fallbackVideoId = activeVideoId,
                ),
                title = activeTitle,
                logo = activeLogo,
                poster = activePoster,
                background = activeBackground,
                seasonNumber = activeSeasonNumber,
                episodeNumber = activeEpisodeNumber,
                episodeTitle = activeEpisodeTitle,
                episodeThumbnail = activeEpisodeThumbnail,
                providerName = activeProviderName,
                providerAddonId = activeProviderAddonId,
                lastStreamTitle = activeStreamTitle,
                lastStreamSubtitle = activeStreamSubtitle,
                pauseDescription = activePauseDescription,
                lastSourceUrl = activeSourceUrl,
            )
        }

        fun currentPlaybackProgressPercent(snapshot: PlayerPlaybackSnapshot = playbackSnapshot): Float {
            val duration = snapshot.durationMs.takeIf { it > 0L } ?: return 0f
            return ((snapshot.positionMs.toFloat() / duration.toFloat()) * 100f)
                .coerceIn(0f, 100f)
        }

        suspend fun currentTraktScrobbleItem() = TraktScrobbleRepository.buildItem(
            contentType = activeContentType,
            parentMetaId = activeParentMetaId,
            videoId = activeVideoId,
            title = activeTitle,
            seasonNumber = activeSeasonNumber,
            episodeNumber = activeEpisodeNumber,
            episodeTitle = activeEpisodeTitle,
        )

        fun emitTraktScrobbleStart() {
            if (hasRequestedScrobbleStartForCurrentItem) return
            hasRequestedScrobbleStartForCurrentItem = true

            scope.launch {
                val item = currentTraktScrobbleItem()
                if (item == null) {
                    hasRequestedScrobbleStartForCurrentItem = false
                    return@launch
                }
                TraktScrobbleRepository.scrobbleStart(
                    item = item,
                    progressPercent = currentPlaybackProgressPercent(),
                )
            }
        }

        fun emitTraktScrobbleStop(progressPercent: Float? = null) {
            val provided = progressPercent
            if (!hasRequestedScrobbleStartForCurrentItem && (provided ?: 0f) < 80f) return

            val percent = provided ?: currentPlaybackProgressPercent()
            scope.launch {
                val item = currentTraktScrobbleItem() ?: return@launch
                TraktScrobbleRepository.scrobbleStop(
                    item = item,
                    progressPercent = percent,
                )
            }
            hasRequestedScrobbleStartForCurrentItem = false
        }

        fun emitStopScrobbleForCurrentProgress() {
            val progressPercent = currentPlaybackProgressPercent()
            if (progressPercent >= 1f && progressPercent < 80f) {
                emitTraktScrobbleStop(progressPercent)
                return
            }

            if (progressPercent >= 80f && !hasSentCompletionScrobbleForCurrentItem) {
                hasSentCompletionScrobbleForCurrentItem = true
                emitTraktScrobbleStop(progressPercent)
            }
        }

        fun tryShowParentalGuide() {
            if (
                !parentalGuideHasShown &&
                (parentalWarnings.isNotEmpty() || !maturityRatingCode.isNullOrBlank()) &&
                !playbackStartedForParentalGuide
            ) {
                playbackStartedForParentalGuide = true
                controlsVisible = true
                showParentalGuide = true
                parentalGuideHasShown = true
            }
        }

        fun normalizedWatchTogetherMemberNames(names: List<String>): List<String> =
            names.mapNotNull { name -> name.trim().takeIf { it.isNotBlank() } }
                .distinct()

        fun summarizeWatchTogetherMemberNames(names: List<String>, maxShown: Int = 3): String {
            val cleaned = normalizedWatchTogetherMemberNames(names)
            if (cleaned.isEmpty()) return "someone"
            return when {
                cleaned.size <= maxShown -> cleaned.joinToString(", ")
                else -> {
                    val extraCount = cleaned.size - maxShown
                    val suffix = if (extraCount == 1) "other" else "others"
                    "${cleaned.take(maxShown).joinToString(", ")} +$extraCount $suffix"
                }
            }
        }

        fun toastWatchTogetherMemberChange(names: List<String>, action: String) {
            val cleaned = normalizedWatchTogetherMemberNames(names)
            if (cleaned.isEmpty()) return
            val verb = if (cleaned.size == 1) "has" else "have"
            NuvioToastController.show("${summarizeWatchTogetherMemberNames(cleaned)} $verb $action the room")
        }

        fun toastSelfJoinedWatchTogether(room: WatchTogetherRoomState) {
            val selfName = profileState.activeProfile?.name?.takeIf { it.isNotBlank() }
            val hostName = normalizedWatchTogetherMemberNames(room.memberNames)
                .firstOrNull { name -> selfName == null || !name.equals(selfName, ignoreCase = true) }
                ?.takeIf { !it.equals("Host", ignoreCase = true) }
            val target = hostName?.let { "$it's room" } ?: "the room"
            NuvioToastController.show("You have joined $target")
        }

        fun toastSelfLeftWatchTogether() {
            NuvioToastController.show("You have left the room")
        }

        fun isWatchTogetherRoomGone(message: String): Boolean =
            message.contains("Room not found", ignoreCase = true) ||
                message.contains("Room ended", ignoreCase = true) ||
                message.contains("room was ended", ignoreCase = true) ||
                message.contains("room has ended", ignoreCase = true) ||
                message.contains("0 rows", ignoreCase = true) ||
                message.contains("no rows", ignoreCase = true)

        fun handleWatchTogetherRoomEndedByHost(room: WatchTogetherRoomState? = watchTogetherSession) {
            val hostName = normalizedWatchTogetherMemberNames(room?.memberNames.orEmpty()).firstOrNull()
                ?: lastWatchTogetherMemberNames.firstOrNull()
                ?: "Host"
            NuvioToastController.show("$hostName has ended the room")
            watchTogetherSession = null
            watchTogetherError = null
            watchTogetherMetadataForcedVisible = false
            watchTogetherJoinCode = ""
            lastWatchTogetherMemberNames = emptyList()
            watchTogetherSoftSyncActive = false
            playerController?.setPlaybackSpeed(1f)
        }

        fun syncWatchTogetherMembers(room: WatchTogetherRoomState, announceChanges: Boolean = true) {
            val nextNames = normalizedWatchTogetherMemberNames(room.memberNames)
            val previousNames = normalizedWatchTogetherMemberNames(lastWatchTogetherMemberNames)
            fun dismissHostWaitingDialogIfGuestJoined() {
                if (room.isHost && showWatchTogetherDialog && nextNames.size > 1) {
                    showWatchTogetherDialog = false
                }
            }

            if (previousNames.isEmpty()) {
                lastWatchTogetherMemberNames = nextNames
                dismissHostWaitingDialogIfGuestJoined()
                return
            }

            if (announceChanges) {
                val joined = nextNames.filterNot(previousNames::contains)
                val left = previousNames.filterNot(nextNames::contains)
                if (joined.isNotEmpty()) toastWatchTogetherMemberChange(joined, "joined")
                if (left.isNotEmpty()) toastWatchTogetherMemberChange(left, "left")
                if (joined.isNotEmpty()) dismissHostWaitingDialogIfGuestJoined()
            }

            lastWatchTogetherMemberNames = nextNames
        }

        suspend fun resolveParentalGuideImdbId(): String? {
            val candidates = listOf(activeParentMetaId, activeVideoId)
            candidates.firstNotNullOfOrNull(::extractParentalGuideImdbId)?.let { return it }
            val tmdbId = candidates.firstNotNullOfOrNull(::extractParentalGuideTmdbId) ?: return null
            return TmdbService.tmdbToImdb(
                tmdbId = tmdbId,
                mediaType = activeContentType,
            )
        }

        fun flushWatchProgress() {
            emitStopScrobbleForCurrentProgress()
            WatchProgressRepository.flushPlaybackProgress(
                session = playbackSession,
                snapshot = playbackSnapshot,
            )
        }

        fun leaveWatchTogetherRoom(showSelfToast: Boolean = true) {
            val roomId = watchTogetherSession?.roomId
            if (showSelfToast && watchTogetherSession != null) {
                toastSelfLeftWatchTogether()
            }
            watchTogetherSession = null
            watchTogetherError = null
            watchTogetherJoinCode = ""
            watchTogetherMetadataForcedVisible = false
            lastWatchTogetherMemberNames = emptyList()
            if (roomId != null) {
                scope.launch { WatchTogetherRepository.leaveRoom(roomId) }
            }
        }

        val onBackWithProgress = remember(onBack, playbackSession, playbackSnapshot, watchTogetherSession) {
            {
                flushWatchProgress()
                if (watchTogetherSession != null) {
                    leaveWatchTogetherRoom()
                }
                onBack()
            }
        }

        PlatformBackHandler(enabled = true) {
            onBackWithProgress()
        }

        val latestWatchTogetherSession = rememberUpdatedState(watchTogetherSession)
        val latestBackgroundWatchTogetherExit = rememberUpdatedState {
            if (latestWatchTogetherSession.value != null) {
                flushWatchProgress()
                leaveWatchTogetherRoom(showSelfToast = false)
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    latestBackgroundWatchTogetherExit.value()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        var showAudioModal by remember { mutableStateOf(false) }
        var showSubtitleModal by remember { mutableStateOf(false) }
        var showSubtitleSyncOverlay by remember { mutableStateOf(false) }
        var showChaptersModal by remember { mutableStateOf(false) }
        var audioTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
        var subtitleTracks by remember { mutableStateOf<List<SubtitleTrack>>(emptyList()) }
        var chapters by remember { mutableStateOf<List<PlayerChapter>>(emptyList()) }
        val effectiveSkipIntervals = remember(skipIntervals, chapters, playbackSnapshot.durationMs) {
            mergeSkipIntervals(
                apiIntervals = skipIntervals,
                chapterIntervals = buildChapterSkipIntervals(
                    chapters = chapters,
                    durationMs = playbackSnapshot.durationMs,
                ),
            )
        }
        var selectedAudioIndex by remember { mutableStateOf(-1) }
        var selectedSubtitleIndex by remember { mutableStateOf(-1) }
        var selectedAddonSubtitleId by remember { mutableStateOf<String?>(null) }
        var subtitleDelayMs by remember { mutableStateOf(0L) }
        var useCustomSubtitles by remember { mutableStateOf(false) }
        var preferredAudioSelectionApplied by rememberSaveable(sourceUrl) { mutableStateOf(false) }
        var preferredSubtitleSelectionApplied by rememberSaveable(sourceUrl) { mutableStateOf(false) }
        var activeSubtitleTab by remember { mutableStateOf(SubtitleTab.BuiltIn) }
        val addonsUiState by AddonRepository.uiState.collectAsStateWithLifecycle()
        val addonSubtitles by SubtitleRepository.addonSubtitles.collectAsStateWithLifecycle()
        val isLoadingAddonSubtitles by SubtitleRepository.isLoading.collectAsStateWithLifecycle()
        val activeAddonSubtitleType = activeContentType
        val addonSubtitleFetchKey = remember(
            addonsUiState.addons,
            activeAddonSubtitleType,
            activeVideoId,
        ) {
            buildAddonSubtitleFetchKey(
                addons = addonsUiState.addons,
                type = activeAddonSubtitleType,
                videoId = activeVideoId,
            )
        }
        var autoFetchedAddonSubtitlesForKey by rememberSaveable(activeSourceUrl, activeVideoId) {
            mutableStateOf<String?>(null)
        }
        val selectedChapterIndex = remember(chapters, displayedPositionMs) {
            chapters.indexOfLast { chapter -> displayedPositionMs >= chapter.timeMs }
        }

        fun refreshTracks() {
            val ctrl = playerController ?: return
            audioTracks = ctrl.getAudioTracks()
            subtitleTracks = ctrl.getSubtitleTracks()
            chapters = ctrl.getChapters()
            subtitleDelayMs = ctrl.getSubtitleDelayMs()
            val selectedAudio = audioTracks.firstOrNull { it.isSelected }
            if (selectedAudio != null) selectedAudioIndex = selectedAudio.index
            val selectedSub = subtitleTracks.firstOrNull { it.isSelected }
            if (selectedSub != null && !useCustomSubtitles) selectedSubtitleIndex = selectedSub.index

            if (!preferredAudioSelectionApplied) {
                val preferredAudioTargets = resolvePreferredAudioLanguageTargets(
                    preferredAudioLanguage = playerSettingsUiState.preferredAudioLanguage,
                    secondaryPreferredAudioLanguage = playerSettingsUiState.secondaryPreferredAudioLanguage,
                    deviceLanguages = DeviceLanguagePreferences.preferredLanguageCodes(),
                )
                if (preferredAudioTargets.isEmpty()) {
                    preferredAudioSelectionApplied = true
                } else if (audioTracks.isNotEmpty()) {
                    val preferredAudioIndex = findPreferredTrackIndex(
                        tracks = audioTracks,
                        targets = preferredAudioTargets,
                        language = { track -> track.language },
                    )
                    if (preferredAudioIndex >= 0 && preferredAudioIndex != selectedAudioIndex) {
                        playerController?.selectAudioTrack(preferredAudioIndex)
                        selectedAudioIndex = preferredAudioIndex
                    }
                    preferredAudioSelectionApplied = true
                }
            }

            if (!preferredSubtitleSelectionApplied) {
                val preferredSubtitleTargets = resolvePreferredSubtitleLanguageTargets(
                    preferredSubtitleLanguage = playerSettingsUiState.preferredSubtitleLanguage,
                    secondaryPreferredSubtitleLanguage = playerSettingsUiState.secondaryPreferredSubtitleLanguage,
                    deviceLanguages = DeviceLanguagePreferences.preferredLanguageCodes(),
                )

                if (preferredSubtitleTargets.isEmpty()) {
                    if (selectedSubtitleIndex != -1 || subtitleTracks.any { it.isSelected }) {
                        playerController?.selectSubtitleTrack(-1)
                    }
                    selectedSubtitleIndex = -1
                    selectedAddonSubtitleId = null
                    useCustomSubtitles = false
                    preferredSubtitleSelectionApplied = true
                } else if (subtitleTracks.isNotEmpty()) {
                    val preferredSubtitleIndex = findPreferredSubtitleTrackIndex(
                        tracks = subtitleTracks,
                        targets = preferredSubtitleTargets,
                    )
                    if (preferredSubtitleIndex >= 0 && preferredSubtitleIndex != selectedSubtitleIndex) {
                        playerController?.selectSubtitleTrack(preferredSubtitleIndex)
                        selectedSubtitleIndex = preferredSubtitleIndex
                        selectedAddonSubtitleId = null
                        useCustomSubtitles = false
                    } else if (
                        preferredSubtitleIndex < 0 &&
                        normalizeLanguageCode(playerSettingsUiState.preferredSubtitleLanguage) == SubtitleLanguageOption.FORCED
                    ) {
                        if (selectedSubtitleIndex != -1 || subtitleTracks.any { it.isSelected }) {
                            playerController?.selectSubtitleTrack(-1)
                        }
                        selectedSubtitleIndex = -1
                        selectedAddonSubtitleId = null
                        useCustomSubtitles = false
                    }
                    preferredSubtitleSelectionApplied = true
                }
            }

        }

        fun showGestureFeedback(feedback: GestureFeedbackState) {
            gestureMessageJob?.cancel()
            gestureFeedback = feedback
            gestureMessageJob = scope.launch {
                delay(900)
                gestureFeedback = null
            }
        }

        fun showGestureMessage(message: String) {
            showGestureFeedback(GestureFeedbackState(message = message))
        }

        fun clearLiveGestureFeedback() {
            liveGestureFeedback = null
        }

        fun revealLockedOverlay() {
            controlsVisible = false
            lockedOverlayVisible = true
        }

        fun lockPlayerControls() {
            playerControlsLocked = true
            controlsVisible = false
            lockedOverlayVisible = false
            pausedOverlayVisible = false
            isScrubbingTimeline = false
            scrubbingPositionMs = null
            gestureMessageJob?.cancel()
            gestureFeedback = null
            liveGestureFeedback = null
            renderedGestureFeedback = null
            showAudioModal = false
            showSubtitleModal = false
            showSubtitleSyncOverlay = false
        }

        fun unlockPlayerControls() {
            playerControlsLocked = false
            lockedOverlayVisible = false
            controlsVisible = true
        }

        fun showSeekFeedback(direction: PlayerSeekDirection, amountMs: Long) {
            val seconds = amountMs / 1000L
            if (seconds <= 0L) return
            showGestureFeedback(
                GestureFeedbackState(
                    messageRes = if (direction == PlayerSeekDirection.Forward) {
                        Res.string.compose_player_seek_feedback_forward
                    } else {
                        Res.string.compose_player_seek_feedback_backward
                    },
                    messageArgs = listOf(seconds),
                    icon = if (direction == PlayerSeekDirection.Forward) {
                        GestureFeedbackIcon.SeekForward
                    } else {
                        GestureFeedbackIcon.SeekBackward
                    },
                ),
            )
        }

        fun showHorizontalSeekPreview(previewPositionMs: Long, baselinePositionMs: Long) {
            val deltaMs = previewPositionMs - baselinePositionMs
            val direction = if (deltaMs < 0L) PlayerSeekDirection.Backward else PlayerSeekDirection.Forward
            liveGestureFeedback = GestureFeedbackState(
                message = formatPlaybackTime(previewPositionMs),
                icon = if (direction == PlayerSeekDirection.Forward) {
                    GestureFeedbackIcon.SeekForward
                } else {
                    GestureFeedbackIcon.SeekBackward
                },
                secondaryMessageRes = if (deltaMs >= 0L) {
                    Res.string.compose_player_seek_delta_forward
                } else {
                    Res.string.compose_player_seek_delta_backward
                },
                secondaryMessageArgs = listOf((abs(deltaMs) / 1000f).roundToInt()),
                secondaryMessageColor = if (direction == PlayerSeekDirection.Forward) {
                    Color(0xFF6EE7A8)
                } else {
                    Color(0xFFFF9A76)
                },
            )
        }

        fun showVolumeFeedback(level: PlayerAudioLevel) {
            val percentage = (level.fraction.coerceIn(0f, 1f) * 100f).roundToInt()
            showGestureFeedback(
                GestureFeedbackState(
                    messageRes = if (level.isMuted) {
                        Res.string.compose_player_muted
                    } else {
                        Res.string.compose_player_volume_level
                    },
                    messageArgs = if (level.isMuted) emptyList() else listOf("$percentage%"),
                    icon = if (level.isMuted) GestureFeedbackIcon.VolumeMuted else GestureFeedbackIcon.Volume,
                    isDanger = level.isMuted,
                ),
            )
        }

        fun togglePlayback() {
            if (playbackSnapshot.isPlaying) {
                shouldPlay = false
                playerController?.pause()
            } else {
                if (playbackSnapshot.isEnded) {
                    playerController?.seekTo(0L)
                }
                shouldPlay = true
                playerController?.play()
            }
            controlsVisible = true
        }

        fun seekBy(offsetMs: Long) {
            playerController?.seekBy(offsetMs)
            controlsVisible = true
            when {
                offsetMs > 0L -> showSeekFeedback(PlayerSeekDirection.Forward, offsetMs)
                offsetMs < 0L -> showSeekFeedback(PlayerSeekDirection.Backward, abs(offsetMs))
            }
        }

        fun currentWatchTogetherPayload(): WatchTogetherPlaybackPayload =
            WatchTogetherPlaybackPayload(
                title = activeTitle,
                contentMetadata = WatchTogetherContentMetadata(
                    contentType = activeContentType,
                    parentMetaId = activeParentMetaId,
                    parentMetaType = activeParentMetaType,
                    videoId = activeVideoId,
                    title = activeTitle,
                    logo = activeLogo,
                    poster = activePoster,
                    background = activeBackground,
                    seasonNumber = activeSeasonNumber,
                    episodeNumber = activeEpisodeNumber,
                    episodeTitle = activeEpisodeTitle,
                    episodeThumbnail = activeEpisodeThumbnail,
                    pauseDescription = activePauseDescription,
                ),
                sourceUrl = activeSourceUrl,
                sourceHeaders = activeSourceHeaders,
                streamTitle = activeStreamTitle,
                providerName = activeProviderName,
                playbackState = when {
                    playbackSnapshot.isEnded -> WatchTogetherPlaybackState.Ended
                    playbackSnapshot.isLoading -> WatchTogetherPlaybackState.Loading
                    playbackSnapshot.isPlaying -> WatchTogetherPlaybackState.Playing
                    else -> WatchTogetherPlaybackState.Paused
                },
                positionMs = playbackSnapshot.positionMs,
                durationMs = playbackSnapshot.durationMs,
                playbackSpeed = playbackSnapshot.playbackSpeed,
            )

        fun watchTogetherMessage(error: Throwable, fallback: String): String {
            val message = error.message.orEmpty()
            return when {
                message.contains("Room not found", ignoreCase = true) -> "Room not found."
                message.contains("Authentication required", ignoreCase = true) -> "Sign in to use Watch Together."
                message.contains("Only the host", ignoreCase = true) -> "Only the host can control this room."
                else -> fallback
            }
        }

        fun createWatchTogetherRoom() {
            if (watchTogetherBusy) return
            watchTogetherBusy = true
            watchTogetherError = null
            scope.launch {
                val result = WatchTogetherRepository.createRoom(
                    profileId = ProfileRepository.activeProfileId,
                    payload = currentWatchTogetherPayload(),
                )
                result
                    .onSuccess { room ->
                        val displayName = profileState.activeProfile?.name?.takeIf { it.isNotBlank() } ?: "Host"
                        val namedRoom = WatchTogetherRepository.joinRoom(
                            roomCode = room.roomCode,
                            profileId = ProfileRepository.activeProfileId,
                            displayName = displayName,
                        ).getOrElse { room }
                        watchTogetherSession = namedRoom
                        watchTogetherJoinCode = namedRoom.roomCode
                        watchTogetherMetadataForcedVisible = false
                        syncWatchTogetherMembers(namedRoom, announceChanges = false)
                    }
                    .onFailure { error -> watchTogetherError = watchTogetherMessage(error, "Could not create room.") }
                watchTogetherBusy = false
            }
        }

        fun applyWatchTogetherState(room: WatchTogetherRoomState) {
            if (room.isHost || room.updatedAtMs <= lastAppliedWatchTogetherUpdateMs) return
            lastAppliedWatchTogetherUpdateMs = room.updatedAtMs
            syncWatchTogetherMembers(room)

            if (room.sourceUrl.isBlank()) {
                watchTogetherError = "This room does not have a playable stream yet."
                return
            }

            val metadata = room.contentMetadata
            if (metadata.parentMetaId.isNotBlank()) {
                activeTitle = metadata.title.ifBlank { room.title.ifBlank { activeTitle } }
                activeContentType = metadata.contentType.ifBlank { metadata.parentMetaType.ifBlank { activeContentType } }
                activeParentMetaId = metadata.parentMetaId
                activeParentMetaType = metadata.parentMetaType.ifBlank { activeContentType }
                activeLogo = metadata.logo
                activePoster = metadata.poster
                activeBackground = metadata.background
                activePauseDescription = metadata.pauseDescription
                activeSeasonNumber = metadata.seasonNumber
                activeEpisodeNumber = metadata.episodeNumber
                activeEpisodeTitle = metadata.episodeTitle
                activeEpisodeThumbnail = metadata.episodeThumbnail
                activeVideoId = metadata.videoId
            } else if (room.title.isNotBlank()) {
                activeTitle = room.title
            }

            if (room.sourceUrl.isNotBlank() && room.sourceUrl != activeSourceUrl) {
                playerController?.pause()
                playerController = null
                playerControllerSourceUrl = null
                errorMessage = null
                activeSourceUrl = room.sourceUrl
                activeSourceAudioUrl = null
                activeFallbackRawSourceUrl = null
                activeFallbackAlreadyTried = false
                activeSourceHeaders = sanitizePlaybackHeaders(room.sourceHeaders)
                activeSourceResponseHeaders = emptyMap()
                activeFallbackRawSourceHeaders = emptyMap()
                activeStreamTitle = room.streamTitle.ifBlank { activeStreamTitle }
                activeProviderName = room.providerName.ifBlank { activeProviderName }
                activeInitialPositionMs = room.expectedPositionMs
                activeInitialProgressFraction = null
                initialLoadCompleted = false
                shouldPlay = room.playbackState == WatchTogetherPlaybackState.Playing
                return
            }

            val controller = playerController ?: return
            if (playbackSnapshot.isLoading) return

            val targetPositionMs = room.expectedPositionMs
            val driftMs = playbackSnapshot.positionMs - targetPositionMs
            val absoluteDriftMs = abs(driftMs)
            val nowMs = WatchProgressClock.nowEpochMs()
            val roomUpdateAgeMs = (nowMs - room.serverNowMs).coerceAtLeast(0L)
            val roomUpdateIsFresh = roomUpdateAgeMs <= WatchTogetherFreshUpdateWindowMs
            val roomUpdateIsStale = roomUpdateAgeMs > WatchTogetherStaleUpdateWindowMs
            val softSpeedUpMultiplier = if (roomUpdateIsFresh) {
                WatchTogetherFreshSpeedUpMultiplier
            } else {
                WatchTogetherStaleSpeedUpMultiplier
            }
            val softSlowDownMultiplier = if (roomUpdateIsFresh) {
                WatchTogetherFreshSlowDownMultiplier
            } else {
                WatchTogetherStaleSlowDownMultiplier
            }
            if (
                roomUpdateIsFresh &&
                absoluteDriftMs > WatchTogetherHardSyncThresholdMs &&
                nowMs - lastWatchTogetherHardSyncAtMs > WatchTogetherHardSyncCooldownMs
            ) {
                lastWatchTogetherHardSyncAtMs = nowMs
                watchTogetherSoftSyncActive = false
                controller.seekTo(targetPositionMs)
                controller.setPlaybackSpeed(room.playbackSpeed)
            } else if (room.playbackState == WatchTogetherPlaybackState.Playing && !roomUpdateIsStale) {
                val targetSpeed = when {
                    absoluteDriftMs <= WatchTogetherSoftSyncSettledMs -> room.playbackSpeed
                    driftMs < -WatchTogetherSoftSyncThresholdMs -> (room.playbackSpeed * softSpeedUpMultiplier).coerceAtMost(4f)
                    driftMs > WatchTogetherSoftSyncThresholdMs -> (room.playbackSpeed * softSlowDownMultiplier).coerceAtLeast(0.25f)
                    watchTogetherSoftSyncActive -> room.playbackSpeed
                    else -> playbackSnapshot.playbackSpeed
                }
                if (abs(playbackSnapshot.playbackSpeed - targetSpeed) > 0.01f) {
                    controller.setPlaybackSpeed(targetSpeed)
                }
                watchTogetherSoftSyncActive = targetSpeed != room.playbackSpeed
            } else if (!roomUpdateIsStale && (watchTogetherSoftSyncActive || abs(playbackSnapshot.playbackSpeed - room.playbackSpeed) > 0.01f)) {
                watchTogetherSoftSyncActive = false
                controller.setPlaybackSpeed(room.playbackSpeed)
            }

            if (roomUpdateIsStale) {
                watchTogetherError = null
            } else if (watchTogetherError == "Watch Together sync is unstable. Waiting for fresher updates.") {
                watchTogetherError = null
            }

            when (room.playbackState) {
                WatchTogetherPlaybackState.Playing -> {
                    shouldPlay = true
                    if (!playbackSnapshot.isPlaying) controller.play()
                }
                WatchTogetherPlaybackState.Paused,
                WatchTogetherPlaybackState.Ended -> {
                    shouldPlay = false
                    if (playbackSnapshot.isPlaying) controller.pause()
                }
                WatchTogetherPlaybackState.Loading -> Unit
            }
        }

        fun joinWatchTogetherRoom() {
            if (watchTogetherBusy || watchTogetherJoinCode.isBlank()) return
            watchTogetherBusy = true
            watchTogetherError = null
            scope.launch {
                val displayName = profileState.activeProfile?.name?.takeIf { it.isNotBlank() } ?: "You"
                val result = WatchTogetherRepository.joinRoom(
                    roomCode = watchTogetherJoinCode,
                    profileId = ProfileRepository.activeProfileId,
                    displayName = displayName,
                )
                result
                    .onSuccess { room ->
                        watchTogetherSession = room
                        showWatchTogetherDialog = false
                        watchTogetherMetadataForcedVisible = false
                        syncWatchTogetherMembers(room, announceChanges = false)
                        toastSelfJoinedWatchTogether(room)
                        runCatching { applyWatchTogetherState(room) }
                            .onFailure { error ->
                                watchTogetherError = watchTogetherMessage(error, "Could not switch to the room stream.")
                                showWatchTogetherDialog = true
                            }
                    }
                    .onFailure { error -> watchTogetherError = watchTogetherMessage(error, "Could not join room.") }
                watchTogetherBusy = false
            }
        }

        fun handleDoubleTapSeek(direction: PlayerSeekDirection) {
            val currentPositionMs = playbackSnapshot.positionMs.coerceAtLeast(0L)
            val nextState = if (accumulatedSeekState?.direction == direction) {
                accumulatedSeekState!!.copy(amountMs = accumulatedSeekState!!.amountMs + PlayerDoubleTapSeekStepMs)
            } else {
                PlayerAccumulatedSeekState(
                    direction = direction,
                    baselinePositionMs = currentPositionMs,
                    amountMs = PlayerDoubleTapSeekStepMs,
                )
            }
            accumulatedSeekState = nextState

            val maxDurationMs = playbackSnapshot.durationMs.takeIf { it > 0L }
            val targetPositionMs = when (direction) {
                PlayerSeekDirection.Backward -> {
                    (nextState.baselinePositionMs - nextState.amountMs).coerceAtLeast(0L)
                }

                PlayerSeekDirection.Forward -> {
                    val unclamped = nextState.baselinePositionMs + nextState.amountMs
                    maxDurationMs?.let { unclamped.coerceAtMost(it) } ?: unclamped
                }
            }
            playerController?.seekTo(targetPositionMs)
            showSeekFeedback(direction, nextState.amountMs)

            accumulatedSeekResetJob?.cancel()
            accumulatedSeekResetJob = scope.launch {
                delay(PlayerDoubleTapSeekResetDelayMs)
                accumulatedSeekState = null
            }
        }

        fun cycleResizeMode() {
            val nextMode = resizeMode.next()
            resizeMode = nextMode
            PlayerSettingsRepository.setResizeMode(nextMode)
            showGestureMessage(
                when (nextMode) {
                    PlayerResizeMode.Fit -> resizeModeFitLabel
                    PlayerResizeMode.Fill -> resizeModeFillLabel
                    PlayerResizeMode.Zoom -> resizeModeZoomLabel
                },
            )
            controlsVisible = true
        }

        fun cyclePlaybackSpeed() {
            val speeds = listOf(1f, 1.25f, 1.5f, 2f)
            val current = playbackSnapshot.playbackSpeed
            val next = speeds.firstOrNull { it > current + 0.01f } ?: speeds.first()
            playerController?.setPlaybackSpeed(next)
            showGestureMessage(formatPlaybackSpeedLabel(next))
            controlsVisible = true
        }

        fun activateHoldToSpeed() {
            if (!playerSettingsUiState.holdToSpeedEnabled) return
            val controller = playerController ?: return
            if (speedBoostRestoreSpeed != null) return

            val targetSpeed = playerSettingsUiState.holdToSpeedValue
            val currentSpeed = playbackSnapshot.playbackSpeed
            if (abs(currentSpeed - targetSpeed) < 0.01f) return

            isHoldToSpeedGestureActive = true
            speedBoostRestoreSpeed = currentSpeed
            controller.setPlaybackSpeed(targetSpeed)
            liveGestureFeedback = GestureFeedbackState(
                message = formatPlaybackSpeedLabel(targetSpeed),
                icon = GestureFeedbackIcon.Speed,
            )
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        fun deactivateHoldToSpeed() {
            isHoldToSpeedGestureActive = false
            val restoreSpeed = speedBoostRestoreSpeed ?: return
            playerController?.setPlaybackSpeed(restoreSpeed)
            speedBoostRestoreSpeed = null
            liveGestureFeedback = null
        }

        val onSurfaceTap = rememberUpdatedState { offset: Offset ->
            if (playerControlsLocked) {
                revealLockedOverlay()
                return@rememberUpdatedState
            }
            if (showManualPauseMetadata) {
                watchTogetherMetadataForcedVisible = false
                return@rememberUpdatedState
            }
            if (!playbackSnapshot.isPlaying && !playbackSnapshot.isLoading && playbackSnapshot.durationMs > 0L) {
                if (pausedOverlayVisible && !controlsVisible) {
                    pausedOverlayVisible = false
                    controlsVisible = true
                } else if (controlsVisible) {
                    controlsVisible = false
                    pausedOverlayVisible = true
                } else {
                    pausedOverlayVisible = true
                }
                return@rememberUpdatedState
            }
            val centerStart = layoutSize.width * PlayerLeftGestureBoundary
            val centerEnd = layoutSize.width * PlayerRightGestureBoundary
            if (controlsVisible && offset.x in centerStart..centerEnd) {
                controlsVisible = false
            } else {
                controlsVisible = !controlsVisible
            }
        }
        val onSurfaceDoubleTap = rememberUpdatedState { offset: Offset ->
            if (playerControlsLocked) {
                revealLockedOverlay()
                return@rememberUpdatedState
            }
            when {
                offset.x < layoutSize.width * PlayerLeftGestureBoundary -> {
                    handleDoubleTapSeek(PlayerSeekDirection.Backward)
                }

                offset.x > layoutSize.width * PlayerRightGestureBoundary -> {
                    handleDoubleTapSeek(PlayerSeekDirection.Forward)
                }

                else -> controlsVisible = !controlsVisible
            }
        }
        val activateHoldToSpeedState = rememberUpdatedState(::activateHoldToSpeed)
        val deactivateHoldToSpeedState = rememberUpdatedState(::deactivateHoldToSpeed)
        val showHorizontalSeekPreviewState = rememberUpdatedState(::showHorizontalSeekPreview)
        val showVolumeFeedbackState = rememberUpdatedState(::showVolumeFeedback)
        val clearLiveGestureFeedbackState = rememberUpdatedState(::clearLiveGestureFeedback)
        val revealLockedOverlayState = rememberUpdatedState(::revealLockedOverlay)
        val isHoldToSpeedGestureActiveState = rememberUpdatedState(isHoldToSpeedGestureActive)
        val playerControlsLockedState = rememberUpdatedState(playerControlsLocked)
        val currentPositionMsState = rememberUpdatedState(playbackSnapshot.positionMs.coerceAtLeast(0L))
        val currentDurationMsState = rememberUpdatedState(playbackSnapshot.durationMs)
        val commitHorizontalSeekState = rememberUpdatedState { targetPositionMs: Long ->
            playerController?.seekTo(targetPositionMs)
        }

        fun fetchAddonSubtitlesForActiveItem() {
            val type = activeAddonSubtitleType.takeIf { it.isNotBlank() } ?: return
            val videoId = activeVideoId?.takeIf { it.isNotBlank() } ?: return
            SubtitleRepository.fetchAddonSubtitles(type, videoId)
        }

        fun resolveFallbackRawSource(): Boolean {
            val rawUrl = activeFallbackRawSourceUrl?.takeIf { it.isNotBlank() } ?: return false
            if (activeFallbackAlreadyTried || rawUrl == activeSourceUrl) return false
            activeFallbackAlreadyTried = true
            scope.launch {
                val currentPositionMs = playbackSnapshot.positionMs
                    .takeIf { it > 0L }
                    ?: activeInitialPositionMs
                val resolved = StreamUrlResolver.resolve(rawUrl, activeFallbackRawSourceHeaders)
                val resolvedUrl = resolved.url.takeIf { it.isNotBlank() } ?: return@launch
                val resolvedHeaders = sanitizePlaybackHeaders(resolved.requestHeaders)
                if (playerSettingsUiState.streamReuseLastLinkEnabled && activeVideoId != null) {
                    val cacheKey = StreamLinkCacheRepository.contentKey(
                        type = activeContentType,
                        videoId = activeVideoId!!,
                        parentMetaId = activeParentMetaId,
                        season = activeSeasonNumber,
                        episode = activeEpisodeNumber,
                    )
                    StreamLinkCacheRepository.save(
                        contentKey = cacheKey,
                        url = resolvedUrl,
                        rawUrl = rawUrl,
                        streamName = activeStreamTitle,
                        addonName = activeProviderName,
                        addonId = activeProviderAddonId.orEmpty(),
                        requestHeaders = resolvedHeaders,
                        responseHeaders = activeSourceResponseHeaders,
                        bingeGroup = currentStreamBingeGroup,
                    )
                }
                errorMessage = null
                activeSourceUrl = resolvedUrl
                activeSourceHeaders = resolvedHeaders
                activeInitialPositionMs = currentPositionMs
                activeInitialProgressFraction = null
                shouldPlay = true
                controlsVisible = true
            }
            return true
        }

        LaunchedEffect(activeSourceUrl, activeSourceAudioUrl, activeSourceHeaders, activeSourceResponseHeaders) {
            errorMessage = null
            playerController = null
            playerControllerSourceUrl = null
            playbackSnapshot = PlayerPlaybackSnapshot()
            isScrubbingTimeline = false
            scrubbingPositionMs = null
            liveGestureFeedback = null
            renderedGestureFeedback = null
            lockedOverlayVisible = false
            initialLoadCompleted = false
            lastProgressPersistEpochMs = 0L
            previousIsPlaying = false
            accumulatedSeekResetJob?.cancel()
            accumulatedSeekResetJob = null
            accumulatedSeekState = null
            speedBoostRestoreSpeed = null
            preferredAudioSelectionApplied = false
            preferredSubtitleSelectionApplied = false
            SubtitleRepository.clear()
            WatchProgressRepository.ensureLoaded()
        }

        LaunchedEffect(activeSourceUrl, activeFallbackRawSourceUrl) {
            if (activeFallbackRawSourceUrl.isNullOrBlank() || activeFallbackAlreadyTried) return@LaunchedEffect
            delay(10_000L)
            if (!initialLoadCompleted && errorMessage == null && playbackSnapshot.isLoading) {
                resolveFallbackRawSource()
            }
        }

        LaunchedEffect(activeSourceUrl, addonSubtitleFetchKey) {
            val fetchKey = addonSubtitleFetchKey ?: return@LaunchedEffect
            if (autoFetchedAddonSubtitlesForKey == fetchKey) return@LaunchedEffect
            autoFetchedAddonSubtitlesForKey = fetchKey
            fetchAddonSubtitlesForActiveItem()
        }

        LaunchedEffect(addonSubtitles, isLoadingAddonSubtitles, preferredSubtitleSelectionApplied, selectedSubtitleIndex, useCustomSubtitles) {
            if (isLoadingAddonSubtitles || useCustomSubtitles || selectedSubtitleIndex != -1) return@LaunchedEffect
            val preferredSubtitleTargets = resolvePreferredSubtitleLanguageTargets(
                preferredSubtitleLanguage = playerSettingsUiState.preferredSubtitleLanguage,
                secondaryPreferredSubtitleLanguage = playerSettingsUiState.secondaryPreferredSubtitleLanguage,
                deviceLanguages = DeviceLanguagePreferences.preferredLanguageCodes(),
            )
            val addonSubtitle = findPreferredAddonSubtitle(
                subtitles = addonSubtitles,
                targets = preferredSubtitleTargets,
            ) ?: return@LaunchedEffect
            selectedAddonSubtitleId = addonSubtitle.id
            useCustomSubtitles = true
            playerController?.setSubtitleUri(addonSubtitle.url)
        }

        LaunchedEffect(playbackSnapshot.isLoading, playerController) {
            if (!playbackSnapshot.isLoading && playerController != null) {
                refreshTracks()
            }
        }

        LaunchedEffect(
            playerController,
            playbackSnapshot.isLoading,
            preferredAudioSelectionApplied,
            preferredSubtitleSelectionApplied,
        ) {
            if (playerController == null || playbackSnapshot.isLoading) {
                return@LaunchedEffect
            }
            if (preferredAudioSelectionApplied && preferredSubtitleSelectionApplied) {
                return@LaunchedEffect
            }

            repeat(10) {
                refreshTracks()
                if (preferredAudioSelectionApplied && preferredSubtitleSelectionApplied) {
                    return@LaunchedEffect
                }
                delay(300)
            }
        }

        LaunchedEffect(
            playerController,
            playerControllerSourceUrl,
            playbackSnapshot.isLoading,
            playbackSnapshot.durationMs,
            activeInitialPositionMs,
            activeInitialProgressFraction,
            initialSeekApplied,
        ) {
            val controller = playerController ?: return@LaunchedEffect
            if (playerControllerSourceUrl != activeSourceUrl) {
                return@LaunchedEffect
            }
            if (initialSeekApplied || playbackSnapshot.isLoading) {
                return@LaunchedEffect
            }

            val progressFraction = activeInitialProgressFraction
                ?.takeIf { it > 0f }
                ?.coerceIn(0f, 1f)
            val targetPositionMs = when {
                activeInitialPositionMs > 0L -> activeInitialPositionMs
                progressFraction != null && playbackSnapshot.durationMs > 0L -> {
                    (playbackSnapshot.durationMs.toDouble() * progressFraction.toDouble()).toLong()
                }
                progressFraction != null -> return@LaunchedEffect
                else -> 0L
            }
            if (targetPositionMs <= 0L) {
                initialSeekApplied = true
                return@LaunchedEffect
            }

            controller.seekTo(targetPositionMs)
            initialSeekApplied = true
        }

        LaunchedEffect(
            controlsVisible,
            isScrubbingTimeline,
            playbackSnapshot.isPlaying,
            playbackSnapshot.isLoading,
            showParentalGuide,
            errorMessage,
        ) {
            if (
                !controlsVisible ||
                isScrubbingTimeline ||
                !playbackSnapshot.isPlaying ||
                playbackSnapshot.isLoading ||
                showParentalGuide ||
                errorMessage != null
            ) {
                return@LaunchedEffect
            }
            delay(3500)
            controlsVisible = false
        }

        LaunchedEffect(playerControlsLocked, lockedOverlayVisible) {
            if (!playerControlsLocked || !lockedOverlayVisible) {
                return@LaunchedEffect
            }
            delay(PlayerLockedOverlayDurationMs)
            lockedOverlayVisible = false
        }

        LaunchedEffect(playbackSnapshot.isPlaying, playbackSnapshot.isLoading, playbackSnapshot.durationMs, errorMessage) {
            pausedOverlayVisible = false
            if (playbackSnapshot.isPlaying || playbackSnapshot.isLoading || playbackSnapshot.durationMs <= 0L || errorMessage != null) {
                return@LaunchedEffect
            }
            delay(1000)
            controlsVisible = false
            pausedOverlayVisible = true
        }

        LaunchedEffect(
            playbackSnapshot.positionMs,
            playbackSnapshot.isPlaying,
            playbackSnapshot.isLoading,
            playbackSnapshot.isEnded,
            playbackSnapshot.durationMs,
        ) {
            if (playbackSnapshot.isEnded) {
                flushWatchProgress()
                previousIsPlaying = false
                return@LaunchedEffect
            }

            if (previousIsPlaying && !playbackSnapshot.isPlaying && !playbackSnapshot.isLoading) {
                flushWatchProgress()
            }

            if (!previousIsPlaying && playbackSnapshot.isPlaying) {
                emitTraktScrobbleStart()
            }

            if (!playbackSnapshot.isLoading) {
                previousIsPlaying = playbackSnapshot.isPlaying
            }

            if (!playbackSnapshot.isPlaying) {
                return@LaunchedEffect
            }

            val now = WatchProgressClock.nowEpochMs()
            if (now - lastProgressPersistEpochMs < PlaybackProgressPersistIntervalMs) {
                return@LaunchedEffect
            }
            lastProgressPersistEpochMs = now
            WatchProgressRepository.upsertPlaybackProgress(
                session = playbackSession,
                snapshot = playbackSnapshot,
            )
        }

        // Fetch parental guide when the playable item changes.
        LaunchedEffect(activeVideoId, activeSeasonNumber, activeEpisodeNumber, activeParentMetaId, activeParentMetaType) {
            parentalWarnings = emptyList()
            imdbMaturityGenresLine = null
            showParentalGuide = false
            parentalGuideHasShown = false
            playbackStartedForParentalGuide = false

            val imdbId = resolveParentalGuideImdbId() ?: run {
                if (playbackSnapshot.isPlaying) {
                    tryShowParentalGuide()
                }
                return@LaunchedEffect
            }
            val guide = ParentalGuideRepository.getParentalGuide(imdbId) ?: return@LaunchedEffect
            parentalWarnings = buildParentalWarnings(guide, parentalGuideLabels)
            imdbMaturityGenresLine = guide.genres.takeIf { it.isNotEmpty() }?.joinToString(", ")

            if (playbackSnapshot.isPlaying) {
                tryShowParentalGuide()
            }
        }

        LaunchedEffect(playbackSnapshot.isPlaying, parentalWarnings, maturityRatingCode) {
            if (playbackSnapshot.isPlaying) {
                tryShowParentalGuide()
            }
        }

        // Fetch skip intervals when episode changes
        LaunchedEffect(activeVideoId, activeSeasonNumber, activeEpisodeNumber) {
            skipIntervals = emptyList()
            activeSkipInterval = null
            skipIntervalDismissed = false

            val season = activeSeasonNumber
            val episode = activeEpisodeNumber
            if (season == null || episode == null) return@LaunchedEffect

            launch {
                val ids = listOfNotNull(activeVideoId, activeParentMetaId)
                val imdbId = resolveParentalGuideImdbId()
                val malId = ids.firstNotNullOfOrNull { id -> extractProviderId(id, "mal") ?: extractProviderId(id, "myanimelist") }
                val anilistId = ids.firstNotNullOfOrNull { id -> extractProviderId(id, "anilist") }
                val intervals = when {
                    imdbId != null -> SkipIntroRepository.getSkipIntervals(
                        imdbId = imdbId,
                        season = season,
                        episode = episode,
                    )
                    malId != null -> SkipIntroRepository.getSkipIntervalsForMal(
                        malId = malId,
                        episode = episode,
                    )
                    anilistId != null -> SkipIntroRepository.getSkipIntervalsForAnilist(
                        anilistId = anilistId,
                        episode = episode,
                        season = season,
                    )
                    else -> emptyList()
                }
                skipIntervals = intervals
            }
        }

        // Update active skip interval based on playback position
        LaunchedEffect(playbackSnapshot.positionMs, effectiveSkipIntervals) {
            if (effectiveSkipIntervals.isEmpty()) {
                activeSkipInterval = null
                return@LaunchedEffect
            }
            val positionSec = playbackSnapshot.positionMs / 1000.0
            val current = effectiveSkipIntervals.firstOrNull { interval ->
                positionSec >= interval.startTime && positionSec < interval.endTime
            }
            if (current != activeSkipInterval) {
                activeSkipInterval = current
                if (current != null) skipIntervalDismissed = false
            }
        }

        DisposableEffect(playbackSession.videoId, activeSourceUrl, activeSourceAudioUrl) {
            onDispose {
                flushWatchProgress()
            }
        }

        LaunchedEffect(watchTogetherSession?.roomId, watchTogetherSession?.isHost) {
            while (true) {
                val room = watchTogetherSession ?: return@LaunchedEffect
                if (room.isHost) {
                    delay(WatchTogetherSyncPollMs)
                    WatchTogetherRepository.pushState(room.roomId, currentWatchTogetherPayload())
                        .onSuccess {
                            watchTogetherSession = it
                            syncWatchTogetherMembers(it)
                            runCatching { applyWatchTogetherState(it) }
                                .onFailure { watchTogetherError = null }
                        }
                        .onFailure { watchTogetherError = null }
                } else {
                    delay(WatchTogetherSyncPollMs)
                    WatchTogetherRepository.refreshRoom(room.roomId)
                        .onSuccess { refreshed ->
                            if (refreshed.roomClosed) {
                                handleWatchTogetherRoomEndedByHost(refreshed)
                                return@onSuccess
                            }
                            watchTogetherSession = refreshed
                            syncWatchTogetherMembers(refreshed)
                            runCatching { applyWatchTogetherState(refreshed) }
                                .onFailure { watchTogetherError = null }
                        }
                        .onFailure { error ->
                            val message = watchTogetherMessage(error, "Watch Together sync failed.")
                            watchTogetherError = null
                            if (isWatchTogetherRoomGone(message)) {
                                handleWatchTogetherRoomEndedByHost()
                            }
                        }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { layoutSize = it }
                .pointerInput(layoutSize) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            deactivateHoldToSpeedState.value()
                        },
                        onTap = { offset -> onSurfaceTap.value(offset) },
                        onDoubleTap = { offset -> onSurfaceDoubleTap.value(offset) },
                        onLongPress = {
                            if (playerControlsLockedState.value) {
                                revealLockedOverlayState.value()
                            } else {
                                activateHoldToSpeedState.value()
                            }
                        },
                    )
                }
                .pointerInput(gestureController, layoutSize) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (playerControlsLockedState.value) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                            }
                            return@awaitEachGesture
                        }
                        val controller = gestureController
                        val width = size.width.toFloat().takeIf { it > 0f } ?: return@awaitEachGesture
                        val height = size.height.toFloat().takeIf { it > 0f } ?: return@awaitEachGesture
                        val region = when {
                            down.position.x > width * PlayerRightGestureBoundary -> PlayerSideGesture.Volume
                            else -> null
                        }

                        val initialVolume = if (region == PlayerSideGesture.Volume) {
                            controller?.currentVolume()
                        } else {
                            null
                        }

                        var totalDx = 0f
                        var totalDy = 0f
                        var gestureMode: PlayerGestureMode? = null
                        val horizontalSeekBaselineMs = currentPositionMsState.value
                        var horizontalSeekPreviewMs = horizontalSeekBaselineMs

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val delta = change.position - change.previousPosition
                            totalDx += delta.x
                            totalDy += delta.y

                            if (gestureMode == null) {
                                val holdToSpeedActive = isHoldToSpeedGestureActiveState.value
                                val horizontalDominant =
                                    !holdToSpeedActive &&
                                        abs(totalDx) > viewConfiguration.touchSlop &&
                                        abs(totalDx) > abs(totalDy)
                                val verticalDominant =
                                    !holdToSpeedActive &&
                                        abs(totalDy) > viewConfiguration.touchSlop &&
                                        abs(totalDy) > abs(totalDx)

                                gestureMode = when {
                                    horizontalDominant -> {
                                        deactivateHoldToSpeedState.value()
                                        PlayerGestureMode.HorizontalSeek
                                    }

                                    verticalDominant && region == PlayerSideGesture.Volume && initialVolume != null -> {
                                        PlayerGestureMode.Volume
                                    }

                                    else -> null
                                }

                                if (gestureMode == null) {
                                    continue
                                }
                            }

                            when (gestureMode) {
                                PlayerGestureMode.HorizontalSeek -> {
                                    val sensitivitySeconds = when {
                                        currentDurationMsState.value >= 3_600_000L -> 120f
                                        currentDurationMsState.value >= 1_800_000L -> 90f
                                        else -> 60f
                                    }
                                    val previewOffsetMs =
                                        ((totalDx / width) * sensitivitySeconds * 1000f).roundToLong()
                                    val unclampedPreviewMs = horizontalSeekBaselineMs + previewOffsetMs
                                    horizontalSeekPreviewMs = currentDurationMsState.value
                                        .takeIf { it > 0L }
                                        ?.let { durationMs ->
                                            unclampedPreviewMs.coerceIn(0L, durationMs)
                                        }
                                        ?: unclampedPreviewMs.coerceAtLeast(0L)
                                    showHorizontalSeekPreviewState.value(
                                        horizontalSeekPreviewMs,
                                        horizontalSeekBaselineMs,
                                    )
                                }

                                PlayerGestureMode.Volume -> {
                                    val gestureDeltaFraction =
                                        (-totalDy / height) * PlayerVerticalGestureSensitivity
                                    controller?.setVolume((initialVolume?.fraction ?: 0f) + gestureDeltaFraction)
                                        ?.let(showVolumeFeedbackState.value)
                                }

                                null -> Unit
                            }
                            change.consume()
                        }

                        if (gestureMode == PlayerGestureMode.HorizontalSeek && !isHoldToSpeedGestureActiveState.value) {
                            commitHorizontalSeekState.value(horizontalSeekPreviewMs)
                            clearLiveGestureFeedbackState.value()
                        }
                    }
                },
        ) {
            PlatformPlayerSurface(
                sourceUrl = activeSourceUrl,
                sourceAudioUrl = activeSourceAudioUrl,
                sourceHeaders = activeSourceHeaders,
                sourceResponseHeaders = activeSourceResponseHeaders,
                modifier = Modifier.fillMaxSize(),
                playWhenReady = shouldPlay,
                resizeMode = resizeMode,
                onControllerReady = { controller ->
                    playerController = controller
                    playerControllerSourceUrl = activeSourceUrl
                },
                onSnapshot = { snapshot ->
                    playbackSnapshot = snapshot
                    if (!snapshot.isLoading) {
                        initialLoadCompleted = true
                    }
                    if (snapshot.isEnded) {
                        shouldPlay = false
                        controlsVisible = !playerControlsLocked
                    }
                },
                onError = { message ->
                    errorMessage = message
                    if (message != null) {
                        if (!resolveFallbackRawSource()) {
                            controlsVisible = !playerControlsLocked
                            val currentVideoId = activeVideoId
                            if (currentVideoId != null) {
                                val cacheKey = StreamLinkCacheRepository.contentKey(
                                    type = activeContentType,
                                    videoId = currentVideoId,
                                    parentMetaId = activeParentMetaId,
                                    season = activeSeasonNumber,
                                    episode = activeEpisodeNumber,
                                )
                                StreamLinkCacheRepository.remove(cacheKey)
                            }
                        }
                    }
                },
            )

            AnimatedVisibility(
                visible = metadataOverlayVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 220)),
                exit = fadeOut(animationSpec = tween(durationMillis = 180)),
                modifier = Modifier.zIndex(3f),
            ) {
                PauseMetadataOverlay(
                    title = activeTitle,
                    logo = activeLogo,
                    isEpisode = isEpisode,
                    seasonNumber = activeSeasonNumber,
                    episodeNumber = activeEpisodeNumber,
                    episodeTitle = activeEpisodeTitle,
                    pauseDescription = activePauseDescription ?: activeStreamSubtitle,
                    metrics = metrics,
                    horizontalSafePadding = horizontalSafePadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(
                visible = (controlsVisible || showParentalGuide || showManualPauseMetadata) && !playerControlsLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(if (showManualPauseMetadata) 4f else 0f),
            ) {
                PlayerControlsShell(
                    title = activeTitle,
                    logo = playerHeaderLogo,
                    streamTitle = activeStreamTitle,
                    seasonNumber = activeSeasonNumber,
                    episodeNumber = activeEpisodeNumber,
                    episodeTitle = activeEpisodeTitle,
                    playbackSnapshot = playbackSnapshot,
                    displayedPositionMs = displayedPositionMs,
                    metrics = metrics,
                    resizeMode = resizeMode,
                    isLocked = playerControlsLocked,
                    showPlaybackControls = controlsVisible && !showManualPauseMetadata,
                    metadataInfoOnly = showManualPauseMetadata,
                    onLockToggle = {
                        if (playerControlsLocked) {
                            unlockPlayerControls()
                        } else {
                            lockPlayerControls()
                        }
                    },
                    onBack = onBackWithProgress,
                    onTogglePlayback = ::togglePlayback,
                    onSeekBack = { seekBy(-10_000L) },
                    onSeekForward = { seekBy(10_000L) },
                    onResizeModeClick = ::cycleResizeMode,
                    onSpeedClick = ::cyclePlaybackSpeed,
                    onSilenceSkipClick = { playerController?.toggleSilenceSkip() },
                    onSubtitleClick = {
                        refreshTracks()
                        showSubtitleSyncOverlay = false
                        showSubtitleModal = true
                    },
                    onAudioClick = {
                        refreshTracks()
                        showAudioModal = true
                    },
                    onChaptersClick = if (chapters.isNotEmpty()) {
                        {
                            refreshTracks()
                            showChaptersModal = true
                        }
                    } else {
                        null
                    },
                    onSourcesClick = null,
                    onWatchTogetherClick = { showWatchTogetherDialog = true },
                    onWatchTogetherInfoClick = { watchTogetherMetadataForcedVisible = !watchTogetherMetadataForcedVisible },
                    onSubmitIntroClick = if (isSeries && playerSettingsUiState.introSubmitEnabled && playerSettingsUiState.introDbApiKey.isNotBlank()) { { showSubmitIntroModal = true } } else null,
                    maturityRatingCode = maturityRatingCode,
                    maturityGenresLine = imdbMaturityGenresLine ?: maturityGenresLine,
                    parentalWarnings = parentalWarnings,
                    showParentalGuide = showParentalGuide,
                    onParentalGuideAnimationComplete = { showParentalGuide = false },
                    onScrubChange = { positionMs ->
                        isScrubbingTimeline = true
                        scrubbingPositionMs = positionMs
                    },
                    onScrubFinished = { positionMs ->
                        isScrubbingTimeline = false
                        scrubbingPositionMs = null
                        playerController?.seekTo(positionMs)
                    },
                    horizontalSafePadding = horizontalSafePadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(
                visible = playerControlsLocked && lockedOverlayVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LockedPlayerOverlay(
                    playbackSnapshot = playbackSnapshot,
                    displayedPositionMs = displayedPositionMs,
                    metrics = metrics,
                    horizontalSafePadding = horizontalSafePadding,
                    onUnlock = ::unlockPlayerControls,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(
                visible = playerSettingsUiState.showLoadingOverlay && !initialLoadCompleted && errorMessage == null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                OpeningOverlay(
                    artwork = backdropArtwork,
                    logo = activeLogo,
                    title = activeTitle,
                    onBack = onBackWithProgress,
                    horizontalSafePadding = horizontalSafePadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(
                visible = currentGestureFeedback != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    renderedGestureFeedback?.let { feedback ->
                        GestureFeedbackPill(
                            feedback = feedback,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Top))
                                .padding(horizontal = horizontalSafePadding)
                                .padding(top = 40.dp),
                        )
                    }
                }
            }

            // Skip intro/recap/outro button
            if (!playerControlsLocked) {
                SkipIntroButton(
                    interval = activeSkipInterval,
                    dismissed = skipIntervalDismissed,
                    controlsVisible = controlsVisible,
                    onSkip = {
                        val interval = activeSkipInterval ?: return@SkipIntroButton
                        playerController?.seekTo((interval.endTime * 1000).toLong())
                        skipIntervalDismissed = true
                    },
                    onDismiss = { skipIntervalDismissed = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = sliderEdgePadding, bottom = overlayBottomPadding),
                )
            }

            if (errorMessage != null) {
                ErrorModal(
                    message = errorMessage.orEmpty(),
                    onDismiss = onBackWithProgress,
                )
            }

            AudioTrackModal(
                visible = showAudioModal,
                audioTracks = audioTracks,
                selectedIndex = selectedAudioIndex,
                onTrackSelected = { index ->
                    selectedAudioIndex = index
                    playerController?.selectAudioTrack(index)
                    scope.launch {
                        delay(200)
                        showAudioModal = false
                    }
                },
                onDismiss = { showAudioModal = false },
            )

            PlayerChaptersModal(
                visible = showChaptersModal,
                chapters = chapters,
                selectedIndex = selectedChapterIndex,
                onChapterSelected = { index ->
                    playerController?.selectChapter(index)
                    scope.launch {
                        delay(200)
                        showChaptersModal = false
                    }
                },
                onDismiss = { showChaptersModal = false },
            )

            SubtitleModal(
                visible = showSubtitleModal,
                activeTab = activeSubtitleTab,
                subtitleTracks = subtitleTracks,
                selectedSubtitleIndex = selectedSubtitleIndex,
                addonSubtitles = addonSubtitles,
                selectedAddonSubtitleId = selectedAddonSubtitleId,
                isLoadingAddonSubtitles = isLoadingAddonSubtitles,
                onTabSelected = { activeSubtitleTab = it },
                onBuiltInTrackSelected = { index ->
                    val wasCustom = useCustomSubtitles
                    selectedSubtitleIndex = index
                    selectedAddonSubtitleId = null
                    useCustomSubtitles = false
                    if (wasCustom) {
                        playerController?.clearExternalSubtitleAndSelect(index)
                    } else {
                        playerController?.selectSubtitleTrack(index)
                    }
                },
                onAddonSubtitleSelected = { addon ->
                    selectedAddonSubtitleId = addon.id
                    selectedSubtitleIndex = -1
                    useCustomSubtitles = true
                    playerController?.setSubtitleUri(addon.url)
                },
                onFetchAddonSubtitles = ::fetchAddonSubtitlesForActiveItem,
                onSubtitleSyncClick = {
                    showSubtitleModal = false
                    showSubtitleSyncOverlay = true
                },
                onDismiss = { showSubtitleModal = false },
            )

            SubtitleSyncOverlay(
                visible = showSubtitleSyncOverlay,
                delayMs = subtitleDelayMs,
                onDelayChanged = { delayMs ->
                    subtitleDelayMs = delayMs
                    playerController?.setSubtitleDelayMs(delayMs)
                },
                onClose = { showSubtitleSyncOverlay = false },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = horizontalSafePadding + 18.dp, bottom = overlayBottomPadding + 42.dp),
            )

            val season = activeSeasonNumber
            val episode = activeEpisodeNumber
            val imdbId = activeVideoId?.split(":")?.firstOrNull()?.takeIf { it.startsWith("tt") }
                ?: activeParentMetaId.takeIf { it.startsWith("tt") }
                ?: metaUiState.meta?.id?.takeIf { it.startsWith("tt") }

            if (showSubmitIntroModal && season != null && episode != null && !imdbId.isNullOrBlank()) {
                com.nuvio.app.features.player.skip.SubmitIntroDialog(
                    imdbId = imdbId,
                    season = season,
                    episode = episode,
                    currentTimeSec = (displayedPositionMs / 1000.0),
                    segmentType = submitIntroSegmentType,
                    onSegmentTypeChange = { submitIntroSegmentType = it },
                    startTimeStr = submitIntroStartTimeStr,
                    onStartTimeChange = { submitIntroStartTimeStr = it },
                    endTimeStr = submitIntroEndTimeStr,
                    onEndTimeChange = { submitIntroEndTimeStr = it },
                    onDismiss = { showSubmitIntroModal = false },
                    onSuccess = {
                        submitIntroStartTimeStr = "00:00"
                        submitIntroEndTimeStr = "00:00"
                        submitIntroSegmentType = "intro"
                        showSubmitIntroModal = false
                    }
                )
            }

            if (showWatchTogetherDialog) {
                WatchTogetherDialog(
                    session = watchTogetherSession,
                    joinCode = watchTogetherJoinCode,
                    isBusy = watchTogetherBusy,
                    errorMessage = watchTogetherError,
                    canUseWatchTogether = WatchTogetherRepository.canUseWatchTogether(),
                    onJoinCodeChange = { watchTogetherJoinCode = it },
                    onCreateRoom = ::createWatchTogetherRoom,
                    onJoinRoom = ::joinWatchTogetherRoom,
                    onLeaveRoom = ::leaveWatchTogetherRoom,
                    onDismiss = { showWatchTogetherDialog = false },
                )
            }
        }
    }
}

private fun buildAddonSubtitleFetchKey(
    addons: List<ManagedAddon>,
    type: String?,
    videoId: String?,
): String? {
    val normalizedType = type?.takeIf { it.isNotBlank() } ?: return null
    val normalizedVideoId = videoId?.takeIf { it.isNotBlank() } ?: return null
    val compatibleSubtitleAddons = addons.mapNotNull { addon ->
        val manifest = addon.manifest ?: return@mapNotNull null
        val supportsSubtitles = manifest.resources.any { resource ->
            resource.isCompatibleSubtitleResource(
                type = normalizedType,
                videoId = normalizedVideoId,
            )
        }
        if (!supportsSubtitles) return@mapNotNull null
        "${manifest.id}:${manifest.transportUrl}"
    }

    if (compatibleSubtitleAddons.isEmpty()) return null
    return buildString {
        append(normalizedType)
        append('|')
        append(normalizedVideoId)
        append('|')
        append(compatibleSubtitleAddons.sorted().joinToString("|"))
    }
}

private fun AddonResource.isCompatibleSubtitleResource(type: String, videoId: String): Boolean {
    val isSubtitleResource = name.equals("subtitles", ignoreCase = true) ||
        name.equals("subtitle", ignoreCase = true)
    if (!isSubtitleResource) return false

    val requestType = if (type.equals("tv", ignoreCase = true)) "series" else type
    val typeMatches = types.isEmpty() || types.any { it.equals(requestType, ignoreCase = true) }
    if (!typeMatches) return false

    return idPrefixes.isEmpty() || idPrefixes.any { prefix -> videoId.startsWith(prefix) }
}

private fun <T> findPreferredTrackIndex(
    tracks: List<T>,
    targets: List<String>,
    language: (T) -> String?,
): Int {
    if (targets.isEmpty()) return -1
    for (target in targets) {
        val matchIndex = tracks.indexOfFirst { track ->
            languageMatchesPreference(
                trackLanguage = language(track),
                targetLanguage = target,
            )
        }
        if (matchIndex >= 0) {
            return matchIndex
        }
    }
    return -1
}

private fun findPreferredSubtitleTrackIndex(
    tracks: List<SubtitleTrack>,
    targets: List<String>,
): Int {
    if (targets.isEmpty()) return -1

    for ((targetPosition, target) in targets.withIndex()) {
        val normalizedTarget = normalizeLanguageCode(target) ?: continue
        if (normalizedTarget == SubtitleLanguageOption.FORCED) {
            val forcedIndex = tracks.indexOfFirst { it.isForced }
            if (forcedIndex >= 0) return forcedIndex
            if (targetPosition == 0) return -1
            continue
        }

        val matchingTracks = tracks.filter { track ->
            languageMatchesPreference(
                trackLanguage = track.language,
                targetLanguage = normalizedTarget,
            ) && track.isFullSubtitle()
        }
        val matchIndex = matchingTracks
            .sortedWith(compareByDescending<SubtitleTrack> { it.accessibilitySubtitleScore() })
            .firstOrNull()
            ?.index
        if (matchIndex != null && matchIndex >= 0) return matchIndex
    }

    return -1
}

private fun findPreferredAddonSubtitle(
    subtitles: List<AddonSubtitle>,
    targets: List<String>,
): AddonSubtitle? {
    if (targets.isEmpty()) return null
    for (target in targets) {
        val normalizedTarget = normalizeLanguageCode(target) ?: continue
        if (normalizedTarget == SubtitleLanguageOption.FORCED) continue
        val matches = subtitles.filter { subtitle ->
            languageMatchesPreference(
                trackLanguage = subtitle.language,
                targetLanguage = normalizedTarget,
            ) && subtitle.isFullSubtitle()
        }
        matches
            .sortedWith(compareByDescending<AddonSubtitle> { it.accessibilitySubtitleScore() })
            .firstOrNull()
            ?.let { return it }
    }
    return null
}

private fun SubtitleTrack.isFullSubtitle(): Boolean {
    if (isForced) return false
    val text = listOf(label, language, id).joinToString(" ").lowercase()
    return !text.contains("forced") &&
        !text.contains("signs") &&
        !text.contains("songs") &&
        !text.contains("sign/song") &&
        !text.contains("signs & songs") &&
        !text.contains("signs and songs")
}

private fun AddonSubtitle.isFullSubtitle(): Boolean {
    val text = listOf(id, language, display, url).joinToString(" ").lowercase()
    return !text.contains("forced") &&
        !text.contains("signs") &&
        !text.contains("songs") &&
        !text.contains("sign/song") &&
        !text.contains("signs & songs") &&
        !text.contains("signs and songs")
}

private fun buildChapterSkipIntervals(
    chapters: List<PlayerChapter>,
    durationMs: Long,
): List<SkipInterval> {
    if (chapters.isEmpty()) return emptyList()
    val sortedChapters = chapters
        .filter { it.timeMs >= 0L }
        .sortedBy { it.timeMs }
    if (sortedChapters.isEmpty()) return emptyList()

    return sortedChapters.mapIndexedNotNull { index, chapter ->
        val skipType = chapter.skipChapterType() ?: return@mapIndexedNotNull null
        val nextChapterMs = sortedChapters.getOrNull(index + 1)?.timeMs
        val fallbackEndMs = if (skipType == "outro" && durationMs > 0L) {
            durationMs
        } else {
            chapter.timeMs + 90_000L
        }
        val rawEndMs = nextChapterMs ?: fallbackEndMs
        val boundedEndMs = if (durationMs > 0L) rawEndMs.coerceAtMost(durationMs) else rawEndMs
        val maxEndMs = if (skipType == "outro") {
            if (durationMs > 0L) durationMs else chapter.timeMs + 300_000L
        } else {
            chapter.timeMs + 180_000L
        }
        val endMs = boundedEndMs.coerceAtMost(maxEndMs)
        if (endMs - chapter.timeMs < 5_000L) return@mapIndexedNotNull null
        SkipInterval(
            startTime = chapter.timeMs / 1000.0,
            endTime = endMs / 1000.0,
            type = skipType,
            provider = "chapters",
        )
    }
}

private fun mergeSkipIntervals(
    apiIntervals: List<SkipInterval>,
    chapterIntervals: List<SkipInterval>,
): List<SkipInterval> {
    if (apiIntervals.isEmpty()) return chapterIntervals
    if (chapterIntervals.isEmpty()) return apiIntervals
    val merged = apiIntervals.toMutableList()
    val apiTypes = apiIntervals.map { it.type.canonicalSkipType() }.toSet()
    chapterIntervals.forEach { chapterInterval ->
        if (chapterInterval.type.canonicalSkipType() in apiTypes) return@forEach
        val overlapsExisting = merged.any { existing ->
            val overlapStart = maxOf(existing.startTime, chapterInterval.startTime)
            val overlapEnd = minOf(existing.endTime, chapterInterval.endTime)
            overlapEnd > overlapStart
        }
        if (!overlapsExisting) merged += chapterInterval
    }
    return merged.sortedBy { it.startTime }
}

private fun String.canonicalSkipType(): String =
    when (lowercase()) {
        "op", "mixed-op" -> "intro"
        "ed", "mixed-ed", "credits" -> "outro"
        else -> lowercase()
    }

private fun PlayerChapter.skipChapterType(): String? {
    val normalized = title.trim().lowercase()
    if (normalized.isBlank()) return null
    val isIntro = normalized.contains("intro") ||
        normalized.contains("opening") ||
        Regex("""(^|[^a-z0-9])op(\s*\d+)?([^a-z0-9]|$)""").containsMatchIn(normalized)
    if (isIntro) return "intro"

    val isOutro = normalized.contains("outro") ||
        normalized.contains("ending") ||
        normalized.contains("credits") ||
        normalized.contains("credit roll") ||
        normalized.contains("end credit") ||
        normalized.contains("end credits") ||
        normalized.contains("closing") ||
        normalized.contains("closing theme") ||
        normalized.contains("post-credits") ||
        normalized.contains("post credits") ||
        normalized.contains("after credits") ||
        normalized.contains("staff roll") ||
        normalized.contains("cast") ||
        Regex("""(^|[^a-z0-9])ed(\s*\d+)?([^a-z0-9]|$)""").containsMatchIn(normalized)
    return if (isOutro) "outro" else null
}

private fun extractProviderId(value: String?, provider: String): String? {
    val normalized = value?.trim()?.takeIf(String::isNotBlank) ?: return null
    val prefix = "$provider:"
    if (normalized.startsWith(prefix, ignoreCase = true)) {
        return normalized.substringAfter(':').substringBefore(':').substringBefore('/').takeIf(String::isNotBlank)
    }
    val tokens = normalized.split(':', '/', '|')
    val providerIndex = tokens.indexOfFirst { it.equals(provider, ignoreCase = true) }
    return tokens.getOrNull(providerIndex + 1)?.takeIf(String::isNotBlank)
}

private fun SubtitleTrack.accessibilitySubtitleScore(): Int =
    accessibilitySubtitleScoreForText(listOf(label, language, id).joinToString(" "))

private fun AddonSubtitle.accessibilitySubtitleScore(): Int =
    accessibilitySubtitleScoreForText(listOf(id, language, display, url).joinToString(" "))

private fun accessibilitySubtitleScoreForText(value: String): Int {
    val text = value.lowercase()
    return when {
        text.contains("sdh") -> 4
        text.contains("hearing impaired") || text.contains("hearing-impaired") -> 3
        text.contains("closed caption") || text.contains("closed-caption") -> 2
        Regex("""(^|[^a-z0-9])(cc|hi|hoh)([^a-z0-9]|$)""").containsMatchIn(text) -> 1
        else -> 0
    }
}
