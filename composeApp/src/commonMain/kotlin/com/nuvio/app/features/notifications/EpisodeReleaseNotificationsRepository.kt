package com.nuvio.app.features.notifications

import co.touchlab.kermit.Logger
import com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.LibraryUiState
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.trakt.TraktPlatformClock
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.concurrent.Volatile
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.serialization.json.Json

object EpisodeReleaseNotificationsRepository {
    private const val metadataFetchConcurrency = 4

    private val log = Logger.withTag("EpisodeReleaseNotifications")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val refreshMutex = Mutex()

    private val _uiState = MutableStateFlow(EpisodeReleaseNotificationsUiState())
    val uiState: StateFlow<EpisodeReleaseNotificationsUiState> = _uiState.asStateFlow()

    @Volatile
    private var hasLoaded = false
    @Volatile
    private var trackedShowsByKey: Map<String, TrackedFollowedShow> = emptyMap()
    @Volatile
    private var lastScheduledRequests: List<EpisodeReleaseNotificationRequest> = emptyList()

    init {
        scope.launch {
            LibraryRepository.uiState.collectLatest { state ->
                if (!hasLoaded) return@collectLatest

                val changed = reconcileTrackedShows(state)
                if (changed) {
                    persist()
                }

                if (_uiState.value.isEnabled) {
                    refreshScheduledNotifications()
                }
            }
        }
    }

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
        scope.launch {
            syncAuthorizationState(refreshIfEnabled = true)
        }
    }

    fun onProfileChanged() {
        loadFromDisk()
        scope.launch {
            syncAuthorizationState(refreshIfEnabled = true)
        }
    }

    fun clearLocalState() {
        hasLoaded = false
        trackedShowsByKey = emptyMap()
        _uiState.value = EpisodeReleaseNotificationsUiState()
        scope.launch {
            runCatching { EpisodeReleaseNotificationPlatform.clearScheduledEpisodeReleaseNotifications() }
                .onFailure { error ->
                    log.w { "Failed to clear scheduled episode release notifications: ${error.message}" }
                }
        }
    }

    internal fun applyFromSyncEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.isEnabled) return

        _uiState.value = _uiState.value.copy(
            isEnabled = true,
            isLoading = false,
            statusMessage = null,
            errorMessage = null,
        )
        persist()

        scope.launch {
            refreshScheduledNotifications()
        }
    }

    fun setEnabled(enabled: Boolean) {
        ensureLoaded()
        scope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
            )

            val granted = runCatching { EpisodeReleaseNotificationPlatform.requestAuthorization() }
                .onFailure { error ->
                    log.e(error) { "Failed to request episode release notification permission" }
                }
                .getOrDefault(false)

            if (!granted) {
                _uiState.value = _uiState.value.copy(
                    isEnabled = false,
                    isLoading = false,
                    permissionGranted = false,
                    scheduledCount = 0,
                    statusMessage = null,
                    errorMessage = getString(Res.string.settings_notifications_permission_disabled),
                )
                persist()
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isEnabled = true,
                isLoading = false,
                permissionGranted = true,
                statusMessage = null,
                errorMessage = null,
            )
            persist()
            refreshScheduledNotifications()
        }
    }

    fun refreshAsync() {
        ensureLoaded()
        scope.launch {
            refreshScheduledNotifications()
        }
    }

    fun openExactAlarmSettings() {
        ensureLoaded()
        val opened = EpisodeReleaseNotificationPlatform.openExactAlarmSettings()
        _uiState.value = _uiState.value.copy(
            statusMessage = if (opened) {
                "Opened Android exact alarm permission settings. Enable alarms and reminders, then return to Nelflix and press Reschedule Release Alerts Now."
            } else {
                "Unable to open exact alarm settings. Open Android Settings -> Apps -> Nelflix -> Alarms & reminders and enable it manually."
            },
            errorMessage = null,
        )
    }

    fun rescheduleReleaseAlertsNow() {
        ensureLoaded()
        scope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                statusMessage = null,
                errorMessage = null,
            )

            runCatching {
                refreshScheduledNotifications()
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Release alerts rescheduled.",
                    errorMessage = null,
                )
            }.onFailure { error ->
                log.e(error) { "Failed to manually reschedule release alerts" }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = null,
                    errorMessage = "Failed to reschedule release alerts.",
                )
            }
        }
    }

    suspend fun refreshNow() {
        ensureLoaded()
        refreshScheduledNotifications()
    }

    private fun loadFromDisk() {
        hasLoaded = true

        val payload = EpisodeReleaseNotificationsStorage.loadPayload().orEmpty().trim()
        val stored = payload.takeIf { it.isNotEmpty() }
            ?.let { rawPayload ->
                runCatching {
                    json.decodeFromString<StoredEpisodeReleaseNotificationsPayload>(rawPayload)
                }.onFailure { error ->
                    log.w { "Failed to decode episode release notifications payload: ${error.message}" }
                }.getOrNull()
            }

        trackedShowsByKey = buildMap {
            stored?.followedShows.orEmpty().forEach { trackedShow ->
                put(buildTrackedShowKey(trackedShow.contentType, trackedShow.contentId), trackedShow)
            }
        }

        _uiState.value = EpisodeReleaseNotificationsUiState(
            isEnabled = true,
            permissionGranted = false,
            scheduledCount = 0,
            timezoneId = DefaultEpisodeReleaseTimezoneId,
            errorMessage = null,
        )
        persist()
    }

    private fun persist() {
        EpisodeReleaseNotificationsStorage.savePayload(
            json.encodeToString(
                StoredEpisodeReleaseNotificationsPayload(
                    enabled = _uiState.value.isEnabled,
                    followedShows = trackedShowsByKey.values
                        .sortedWith(compareBy(TrackedFollowedShow::contentType, TrackedFollowedShow::contentId)),
                    timezoneId = DefaultEpisodeReleaseTimezoneId,
                ),
            ),
        )
    }

    fun setTimezoneId(timezoneId: String) {
        ensureLoaded()
        _uiState.value = _uiState.value.copy(timezoneId = DefaultEpisodeReleaseTimezoneId)
        persist()
        refreshAsync()
    }

    private suspend fun syncAuthorizationState(refreshIfEnabled: Boolean) {
        val granted = runCatching { EpisodeReleaseNotificationPlatform.notificationsAuthorized() }
            .onFailure { error ->
                log.w { "Failed to read episode release notification permission: ${error.message}" }
            }
            .getOrDefault(false)

        _uiState.value = _uiState.value.copy(
            permissionGranted = granted,
            errorMessage = when {
                _uiState.value.isEnabled && !granted -> "System notifications are currently disabled for NELFLIX."
                else -> _uiState.value.errorMessage
            },
        )

        if (refreshIfEnabled && _uiState.value.isEnabled) {
            refreshScheduledNotifications()
        }
    }

    private fun reconcileTrackedShows(state: LibraryUiState): Boolean {
        if (!state.isLoaded) return false

        val seriesItems = state.items.filter { item -> isReleaseAlertLibraryType(item.type) }
        val nextTrackedShows = linkedMapOf<String, TrackedFollowedShow>()

        seriesItems.forEach { item ->
            val key = buildTrackedShowKey(item.type, item.id)
            nextTrackedShows[key] = trackedShowsByKey[key]
                ?: TrackedFollowedShow(
                    contentId = item.id,
                    contentType = item.type,
                    followedOnIsoDate = inferFollowedOnIsoDate(item),
                )
        }

        val changed = nextTrackedShows != trackedShowsByKey
        if (changed) {
            trackedShowsByKey = nextTrackedShows.toMap()
        }
        return changed
    }

    private fun inferFollowedOnIsoDate(item: LibraryItem): String {
        if (item.savedAtEpochMs >= MinReasonableSavedAtEpochMs) {
            return EpisodeReleaseNotificationsClock.isoDateFromEpochMs(item.savedAtEpochMs)
        }
        return CurrentDateProvider.todayIsoDate()
    }

    private suspend fun refreshScheduledNotifications() {
        refreshMutex.withLock {
            LibraryRepository.ensureLoaded()

            val currentLibraryState = LibraryRepository.uiState.value
            val trackedShowsChanged = reconcileTrackedShows(currentLibraryState)
            if (trackedShowsChanged) {
                persist()
            }

            val permissionGranted = runCatching { EpisodeReleaseNotificationPlatform.notificationsAuthorized() }
                .onFailure { error ->
                    log.w { "Failed to refresh episode release notification permission: ${error.message}" }
                }
                .getOrDefault(false)

            if (!_uiState.value.isEnabled || !permissionGranted) {
                runCatching { EpisodeReleaseNotificationPlatform.clearScheduledEpisodeReleaseNotifications() }
                    .onFailure { error ->
                        log.w { "Failed to clear scheduled episode release notifications: ${error.message}" }
                    }
                lastScheduledRequests = emptyList()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    permissionGranted = permissionGranted,
                    scheduledCount = 0,
                    expectedAlerts = emptyList(),
                    errorMessage = if (_uiState.value.isEnabled && !permissionGranted) {
                        "System notifications are currently disabled for NELFLIX."
                    } else {
                        null
                    },
                )
                return
            }

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                permissionGranted = true,
                errorMessage = null,
            )

            if (trackedShowsByKey.isEmpty()) {
                runCatching { EpisodeReleaseNotificationPlatform.clearScheduledEpisodeReleaseNotifications() }
                lastScheduledRequests = emptyList()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    scheduledCount = 0,
                    expectedAlerts = emptyList(),
                    errorMessage = null,
                )
                return
            }

            AddonRepository.initialize()
            withTimeoutOrNull(10_000L) {
                AddonRepository.awaitManifestsLoaded()
            }

            val semaphore = Semaphore(metadataFetchConcurrency)
            val nowEpochMs = TraktPlatformClock.nowEpochMs()
            val requests = trackedShowsByKey.values.map { trackedShow ->
                scope.async {
                    semaphore.withPermit {
                        buildRequestsForShow(trackedShow)
                    }
                }
            }.awaitAll().flatten()
                .filter { request ->
                    (request.triggerAtEpochMs ?: Long.MIN_VALUE) >= nowEpochMs - EpisodeReleaseNotificationScheduleGraceMs
                }

            runCatching {
                EpisodeReleaseNotificationPlatform.scheduleEpisodeReleaseNotifications(requests)
            }.onFailure { error ->
                log.e(error) { "Failed to schedule episode release notifications" }
            }

            lastScheduledRequests = requests.sortedBy { it.triggerAtEpochMs ?: Long.MAX_VALUE }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                permissionGranted = true,
                scheduledCount = requests.size,
                expectedAlerts = lastScheduledRequests
                    .map { request ->
                        EpisodeReleaseAlertPreview(
                            requestId = request.requestId,
                            title = request.notificationTitle,
                            body = request.notificationBody,
                            triggerTimeLabel = request.triggerTimeLabel.orEmpty(),
                            imageUrl = request.backdropUrl,
                            deepLinkUrl = request.deepLinkUrl,
                        )
                    },
                errorMessage = null,
            )
        }
    }

    private suspend fun buildRequestsForShow(trackedShow: TrackedFollowedShow): List<EpisodeReleaseNotificationRequest> {
        val meta = runCatching {
            MetaDetailsRepository.fetchNotificationReleaseMeta(
                type = trackedShow.contentType,
                id = trackedShow.contentId,
            )
        }.onFailure { error ->
            log.w { "Failed to resolve metadata for ${trackedShow.contentType}:${trackedShow.contentId}: ${error.message}" }
        }.getOrNull() ?: return emptyList()

        val showTitle = meta.name.ifBlank { trackedShow.contentId }
        val settings = _uiState.value
        if (isMovieLibraryType(trackedShow.contentType)) {
            val releaseDate = releaseDateIso(meta.released) ?: return emptyList()
            val triggerAtEpochMs = EpisodeReleaseNotificationPlatform.resolveReleaseTriggerEpochMs(
                rawReleaseValue = meta.released,
                timezoneId = settings.timezoneId,
            ) ?: return emptyList()
            if (releaseDate < trackedShow.followedOnIsoDate) return emptyList()

            return listOf(
                EpisodeReleaseNotificationRequest(
                    requestId = buildEpisodeReleaseNotificationId(
                        profileId = ProfileRepository.activeProfileId,
                        contentType = trackedShow.contentType,
                        contentId = trackedShow.contentId,
                        episodeId = "movie:${meta.released.orEmpty()}",
                        releaseDateIso = releaseDate,
                    ),
                    notificationTitle = showTitle,
                    notificationBody = "Movie premiere",
                    releaseDateIso = releaseDate,
                    triggerAtEpochMs = triggerAtEpochMs,
                    triggerTimeLabel = EpisodeReleaseNotificationPlatform.formatReleaseTriggerLabel(
                        epochMs = triggerAtEpochMs,
                        timezoneId = settings.timezoneId,
                    ),
                    deepLinkUrl = buildMetaDeepLinkUrl(
                        type = trackedShow.contentType,
                        id = trackedShow.contentId,
                    ),
                    timezoneId = settings.timezoneId,
                    backdropUrl = meta.poster ?: meta.background,
                ),
            )
        }

        return meta.videos.mapNotNull { episode ->
            val releaseDate = releaseDateIso(episode.released) ?: return@mapNotNull null
            val triggerAtEpochMs = EpisodeReleaseNotificationPlatform.resolveReleaseTriggerEpochMs(
                rawReleaseValue = episode.released,
                timezoneId = settings.timezoneId,
            ) ?: return@mapNotNull null
            if (releaseDate < trackedShow.followedOnIsoDate) return@mapNotNull null
            if (episode.season == null && episode.episode == null) return@mapNotNull null
            val episodeBody = buildEpisodeReleaseNotificationBody(
                seasonNumber = episode.season,
                episodeNumber = episode.episode,
                episodeTitle = episode.title,
            )

            EpisodeReleaseNotificationRequest(
                requestId = buildEpisodeReleaseNotificationId(
                    profileId = ProfileRepository.activeProfileId,
                    contentType = trackedShow.contentType,
                    contentId = trackedShow.contentId,
                    episodeId = episode.id,
                    releaseDateIso = releaseDate,
                ),
                notificationTitle = showTitle,
                notificationBody = episodeBody,
                releaseDateIso = releaseDate,
                triggerAtEpochMs = triggerAtEpochMs,
                triggerTimeLabel = EpisodeReleaseNotificationPlatform.formatReleaseTriggerLabel(
                    epochMs = triggerAtEpochMs,
                    timezoneId = settings.timezoneId,
                ),
                deepLinkUrl = buildMetaDeepLinkUrl(
                    type = trackedShow.contentType,
                    id = trackedShow.contentId,
                ),
                timezoneId = settings.timezoneId,
                backdropUrl = episode.thumbnail ?: episode.seasonPoster ?: meta.background ?: meta.poster,
            )
        }
    }
}
