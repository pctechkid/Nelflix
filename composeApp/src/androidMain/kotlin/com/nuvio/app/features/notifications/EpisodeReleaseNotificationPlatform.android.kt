package com.nuvio.app.features.notifications

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal actual object EpisodeReleaseNotificationPlatform {
    private const val permissionRequestCode = 4607
    private const val platformPreferencesName = "nuvio_episode_release_notifications_platform"
    private const val scheduledIdsKey = "scheduled_episode_release_ids"
    private const val launchPermissionPromptedKey = "launch_notification_permission_prompted"
    private const val workTag = "episode_release_notifications"
    private const val alarmAction = "com.nelflix.ronnel.EPISODE_RELEASE_NOTIFICATION"
    internal const val channelId = "episode_release_notifications_high"
    internal const val workerRequestIdKey = "request_id"
    internal const val workerTitleKey = "title"
    internal const val workerBodyKey = "body"
    internal const val workerDeepLinkKey = "deep_link"
    internal const val workerBackdropUrlKey = "backdrop_url"

    private var appContext: Context? = null
    private var currentActivity: ComponentActivity? = null
    private var pendingPermissionContinuation: kotlin.coroutines.Continuation<Boolean>? = null
    private val httpClient by lazy {
        HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        ensureNotificationChannel()
    }

    fun bindActivity(activity: ComponentActivity) {
        currentActivity = activity
    }

    fun unbindActivity(activity: ComponentActivity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }

    fun handlePermissionRequestResult(
        requestCode: Int,
        grantResults: IntArray,
    ): Boolean {
        if (requestCode != permissionRequestCode) return false
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        pendingPermissionContinuation?.resume(granted)
        pendingPermissionContinuation = null
        return true
    }

    actual suspend fun notificationsAuthorized(): Boolean {
        val context = appContext ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionState = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            if (permissionState != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    actual suspend fun requestAuthorization(): Boolean {
        val context = appContext ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ensureNotificationChannel()
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        val permissionState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            ensureNotificationChannel()
            return true
        }

        val activity = currentActivity ?: return false
        return suspendCancellableCoroutine { continuation ->
            pendingPermissionContinuation = continuation
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                permissionRequestCode,
            )
        }
    }

    suspend fun requestAuthorizationOnFirstLaunch() {
        val context = appContext ?: return
        val preferences = context.getSharedPreferences(platformPreferencesName, Context.MODE_PRIVATE)
        if (preferences.getBoolean(launchPermissionPromptedKey, false)) return
        preferences.edit().putBoolean(launchPermissionPromptedKey, true).apply()
        requestAuthorization()
    }

    actual fun availableTimezoneIds(): List<String> =
        TimeZone.getAvailableIDs().toList().sorted()

    actual fun exactAlarmsAllowed(): Boolean {
        val context = appContext ?: return false
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    actual fun openExactAlarmSettings(): Boolean {
        val context = currentActivity ?: appContext ?: return false
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    },
                )
            }
            add(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                },
            )
        }

        val launchContext = currentActivity ?: context
        intents.forEach { intent ->
            val launchIntent = intent.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent.resolveActivity(context.packageManager) != null) {
                runCatching {
                    launchContext.startActivity(launchIntent)
                }.onSuccess {
                    return true
                }
            }
        }
        return false
    }

    actual fun resolveReleaseTriggerEpochMs(rawReleaseValue: String?, timezoneId: String): Long? =
        releaseTriggerAtEpochMs(rawReleaseValue, timezoneId)

    actual fun formatReleaseTriggerLabel(epochMs: Long, timezoneId: String): String {
        val zoneId = ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US)
        return Instant.ofEpochMilli(epochMs)
            .atZone(zoneId)
            .format(formatter)
    }

    actual suspend fun scheduleEpisodeReleaseNotifications(requests: List<EpisodeReleaseNotificationRequest>) {
        val context = appContext ?: return
        ensureNotificationChannel()

        withContext(Dispatchers.IO) {
            val workManager = WorkManager.getInstance(context)
            cancelScheduledAlarms(context)
            cancelTrackedWork(workManager)

            val nowEpochMs = System.currentTimeMillis()
            val scheduledIds = mutableListOf<String>()

            requests.forEach { request ->
                val triggerAtEpochMs = request.triggerAtEpochMs
                    ?: triggerAtEpochMs(request.releaseDateIso, request.timezoneId)
                    ?: return@forEach
                val rawInitialDelayMs = triggerAtEpochMs - nowEpochMs
                if (rawInitialDelayMs < -EpisodeReleaseNotificationScheduleGraceMs) return@forEach
                val initialDelayMs = rawInitialDelayMs.coerceAtLeast(0L)

                val scheduledExactly = scheduleExactAlarmIfAllowed(
                    context = context,
                    request = request,
                    triggerAtEpochMs = triggerAtEpochMs,
                )
                if (!scheduledExactly) {
                    scheduleFallbackWork(
                        workManager = workManager,
                        request = request,
                        initialDelayMs = initialDelayMs,
                    )
                }

                scheduledIds += request.requestId
            }

            preferences(context)
                .edit()
                .putStringSet(scheduledIdsKey, scheduledIds.toSet())
                .apply()
        }
    }

    actual suspend fun clearScheduledEpisodeReleaseNotifications() {
        val context = appContext ?: return
        withContext(Dispatchers.IO) {
            val workManager = WorkManager.getInstance(context)
            cancelScheduledAlarms(context)
            cancelTrackedWork(workManager)
            preferences(context)
                .edit()
                .remove(scheduledIdsKey)
                .apply()
        }
    }

    internal suspend fun buildNotification(
        context: Context,
        request: EpisodeReleaseNotificationRequest,
    ): android.app.Notification {
        val pendingIntent = buildPendingIntent(context, request)
        val backdropBitmap = loadBackdropBitmap(request.backdropUrl)
        val appIconBitmap = BitmapFactory.decodeResource(context.resources, com.nuvio.app.R.drawable.nelflix_splash_logo)

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.nuvio.app.R.drawable.ic_stat_nelflix)
            .setColor(Color.rgb(229, 9, 20))
            .setContentTitle(request.notificationTitle)
            .setContentText(request.notificationBody)
            .setStyle(
                backdropBitmap?.let { bitmap ->
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(appIconBitmap)
                        .setSummaryText(request.notificationBody)
                } ?: NotificationCompat.BigTextStyle().bigText(request.notificationBody),
            )
            .setLargeIcon(appIconBitmap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
    }

    internal suspend fun loadBackdropBitmap(backdropUrl: String?): Bitmap? {
        val imageUrl = backdropUrl?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return runCatching {
            val bytes: ByteArray = httpClient.get(imageUrl).body()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun buildPendingIntent(
        context: Context,
        request: EpisodeReleaseNotificationRequest,
    ): PendingIntent {
        val launchIntent = Intent(context, com.nuvio.app.MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(request.deepLinkUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            kotlin.math.abs(request.requestId.hashCode()),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleExactAlarmIfAllowed(
        context: Context,
        request: EpisodeReleaseNotificationRequest,
        triggerAtEpochMs: Long,
    ): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false

        val pendingIntent = buildAlarmPendingIntent(
            context = context,
            request = request,
            flags = PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val scheduled = runCatching {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(
                        triggerAtEpochMs,
                        buildPendingIntent(context, request),
                    ),
                    pendingIntent,
                )
            }.isSuccess
            if (scheduled) return true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }

        return runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtEpochMs,
                        pendingIntent,
                    )
                }
                else -> {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtEpochMs,
                        pendingIntent,
                    )
                }
            }
        }.isSuccess
    }

    private fun scheduleFallbackWork(
        workManager: WorkManager,
        request: EpisodeReleaseNotificationRequest,
        initialDelayMs: Long,
    ) {
        val workRequest = OneTimeWorkRequestBuilder<EpisodeReleaseNotificationWorker>()
            .setInputData(buildWorkerInputData(request))
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .addTag(workTag)
            .build()

        awaitOperation(
            workManager.enqueueUniqueWork(
                uniqueWorkName(request.requestId),
                ExistingWorkPolicy.REPLACE,
                workRequest,
            ),
        )
    }

    private fun buildWorkerInputData(request: EpisodeReleaseNotificationRequest): Data =
        Data.Builder()
            .putString(workerRequestIdKey, request.requestId)
            .putString(workerTitleKey, request.notificationTitle)
            .putString(workerBodyKey, request.notificationBody)
            .putString(workerDeepLinkKey, request.deepLinkUrl)
            .putString(workerBackdropUrlKey, request.backdropUrl)
            .build()

    private fun buildAlarmPendingIntent(
        context: Context,
        request: EpisodeReleaseNotificationRequest,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, EpisodeReleaseNotificationAlarmReceiver::class.java).apply {
            action = alarmAction
            data = android.net.Uri.parse("nelflix://episode-release/${abs(request.requestId.hashCode())}")
            putExtra(workerRequestIdKey, request.requestId)
            putExtra(workerTitleKey, request.notificationTitle)
            putExtra(workerBodyKey, request.notificationBody)
            putExtra(workerDeepLinkKey, request.deepLinkUrl)
            putExtra(workerBackdropUrlKey, request.backdropUrl)
        }
        return PendingIntent.getBroadcast(
            context,
            abs(request.requestId.hashCode()),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelScheduledAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        preferences(context)
            .getStringSet(scheduledIdsKey, emptySet())
            .orEmpty()
            .forEach { requestId ->
                val request = EpisodeReleaseNotificationRequest(
                    requestId = requestId,
                    notificationTitle = "",
                    notificationBody = "",
                    releaseDateIso = "",
                    deepLinkUrl = "",
                )
                val pendingIntent = buildAlarmPendingIntent(
                    context = context,
                    request = request,
                    flags = PendingIntent.FLAG_NO_CREATE,
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
    }

    private fun cancelTrackedWork(workManager: WorkManager) {
        val context = appContext ?: return
        preferences(context)
            .getStringSet(scheduledIdsKey, emptySet())
            .orEmpty()
            .forEach { requestId ->
                awaitOperation(workManager.cancelUniqueWork(uniqueWorkName(requestId)))
            }
    }

    private fun awaitOperation(operation: Operation) {
        operation.result.get()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(platformPreferencesName, Context.MODE_PRIVATE)

    private fun resolveZoneId(timezoneId: String): ZoneId =
        timezoneId.trim()
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()

    private fun releaseTriggerAtEpochMs(rawReleaseValue: String?, timezoneId: String): Long? = runCatching {
        val value = rawReleaseValue?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        val zoneId = resolveZoneId(timezoneId)

        parseReleaseWallTime(value, zoneId)?.plusHours(EpisodeReleaseNotificationDelayHours)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            ?: LocalDate.parse(value.substringBefore('T').substringBefore(' ')).let { date ->
                date.atTime(EpisodeReleaseNotificationHour, EpisodeReleaseNotificationMinute)
                    .plusHours(EpisodeReleaseNotificationDelayHours)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
    }.getOrNull()

    private fun parseReleaseWallTime(value: String, zoneId: ZoneId): LocalDateTime? {
        if (!value.contains('T') && !value.contains(' ')) return null
        val normalized = value.replace(' ', 'T')
        return runCatching { LocalDateTime.parse(normalized.substringBeforeLast('Z')) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime() }.getOrNull()
            ?: parseInstantRelease(value, zoneId)?.atZone(zoneId)?.toLocalDateTime()
    }

    private fun parseInstantRelease(value: String, zoneId: ZoneId): Instant? {
        if (!value.contains('T') && !value.contains(' ')) return null
        val normalized = value.replace(' ', 'T')
        return runCatching { Instant.parse(normalized) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(normalized.substringBeforeLast('Z'))
                    .atZone(zoneId)
                    .toInstant()
            }.getOrNull()
    }

    private fun triggerAtEpochMs(releaseDateIso: String, timezoneId: String): Long? = runCatching {
        LocalDate.parse(releaseDateIso)
            .atTime(EpisodeReleaseNotificationHour, EpisodeReleaseNotificationMinute)
            .plusHours(EpisodeReleaseNotificationDelayHours)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    private fun ensureNotificationChannel() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val existingChannel = notificationManager.getNotificationChannel(channelId)
        if (existingChannel != null) return

        val channel = NotificationChannel(
            channelId,
            runBlocking { getString(Res.string.notifications_channel_episode_releases_name) },
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = runBlocking { getString(Res.string.notifications_channel_episode_releases_description) }
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            enableLights(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

}

private fun uniqueWorkName(requestId: String): String = "episode_release_notifications:$requestId"
