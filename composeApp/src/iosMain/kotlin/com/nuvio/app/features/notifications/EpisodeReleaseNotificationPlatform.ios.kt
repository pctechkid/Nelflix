package com.nuvio.app.features.notifications

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.time.resolveDeviceLocalScheduledEpisodeReleaseEpochMs
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNNotificationAttachment
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
internal actual object EpisodeReleaseNotificationPlatform {
    private const val scheduledIdsKey = "episode_release_notification_scheduled_ids"
    private const val attachmentDirectoryName = "episode_release_notification_attachments"
    private val httpClient = HttpClient(Darwin) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }
    }

    actual suspend fun notificationsAuthorized(): Boolean = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            continuation.resume(
                status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional,
            )
        }
    }

    actual suspend fun requestAuthorization(): Boolean = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ ->
            continuation.resume(granted)
        }
    }

    actual fun availableTimezoneIds(): List<String> = listOf(DefaultEpisodeReleaseTimezoneId, "UTC")

    actual fun exactAlarmsAllowed(): Boolean = true

    actual fun openExactAlarmSettings(): Boolean = false

    actual fun resolveReleaseTriggerEpochMs(rawReleaseValue: String?, timezoneId: String): Long? {
        return resolveDeviceLocalScheduledEpisodeReleaseEpochMs(
            raw = rawReleaseValue,
            dateOnlyHour = EpisodeReleaseNotificationHour,
            dateOnlyMinute = EpisodeReleaseNotificationMinute,
        )
    }

    actual fun formatReleaseTriggerLabel(epochMs: Long, timezoneId: String): String {
        val formatter = NSDateFormatter().apply {
            dateFormat = "MMM d, yyyy h:mm a"
        }
        return formatter.stringFromDate(
            NSDate(timeIntervalSince1970 = epochMs.toDouble() / 1000.0),
        )
    }

    actual suspend fun scheduleEpisodeReleaseNotifications(requests: List<EpisodeReleaseNotificationRequest>) {
        clearScheduledEpisodeReleaseNotifications()

        val center = UNUserNotificationCenter.currentNotificationCenter()
        val scheduledIds = mutableListOf<String>()

        requests.forEach { request ->
            val triggerEpochMs = request.triggerAtEpochMs
                ?: resolveDeviceLocalScheduledEpisodeReleaseEpochMs(
                    raw = request.rawReleaseValue ?: request.releaseDateIso,
                    dateOnlyHour = EpisodeReleaseNotificationHour,
                    dateOnlyMinute = EpisodeReleaseNotificationMinute,
                )
                ?: return@forEach
            val delaySeconds = (triggerEpochMs / 1000.0) - NSDate().timeIntervalSince1970
            if (delaySeconds <= 0.0) return@forEach

            val content = buildNotificationContent(request)
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
                timeInterval = delaySeconds,
                repeats = false,
            )
            val notificationRequest = UNNotificationRequest.requestWithIdentifier(
                identifier = request.requestId,
                content = content,
                trigger = trigger,
            )
            center.addNotificationRequest(notificationRequest) { _ -> }
            scheduledIds += request.requestId
        }

        NSUserDefaults.standardUserDefaults.setObject(
            scheduledIds.joinToString(separator = "|"),
            forKey = ProfileScopedKey.of(scheduledIdsKey),
        )
    }

    actual suspend fun clearScheduledEpisodeReleaseNotifications() {
        val identifiers = trackedScheduledIds()
        if (identifiers.isNotEmpty()) {
            UNUserNotificationCenter.currentNotificationCenter()
                .removePendingNotificationRequestsWithIdentifiers(identifiers)
        }
        NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(scheduledIdsKey))
    }

    private fun trackedScheduledIds(): List<String> =
        NSUserDefaults.standardUserDefaults
            .stringForKey(ProfileScopedKey.of(scheduledIdsKey))
            ?.split('|')
            ?.filter { value -> value.isNotBlank() }
            .orEmpty()

    private suspend fun buildNotificationContent(request: EpisodeReleaseNotificationRequest): UNMutableNotificationContent =
        UNMutableNotificationContent().apply {
            setTitle(request.notificationTitle)
            setBody(request.notificationBody)
            setUserInfo(mapOf("deeplink" to request.deepLinkUrl))
            attachmentFor(request)?.let { attachment ->
                setAttachments(listOf(attachment))
            }
        }

    private suspend fun attachmentFor(request: EpisodeReleaseNotificationRequest): UNNotificationAttachment? {
        val imageUrl = request.backdropUrl?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val localUrl = downloadBackdropToTemporaryFile(
            requestId = request.requestId,
            imageUrl = imageUrl,
        ) ?: return null

        return UNNotificationAttachment.attachmentWithIdentifier(
            request.requestId,
            localUrl,
            null as Map<Any?, *>?,
            null,
        )
    }

    private suspend fun downloadBackdropToTemporaryFile(
        requestId: String,
        imageUrl: String,
    ): NSURL? {
        val bytes: ByteArray = runCatching {
            httpClient.get(imageUrl).body<ByteArray>()
        }.getOrNull() ?: return null

        val directoryPath = NSTemporaryDirectory().trimEnd('/') + "/" + attachmentDirectoryName
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = directoryPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

        val fileExtension = imageUrl.substringAfterLast('.', "jpg")
            .substringBefore('?')
            .takeIf { extension -> extension.length in 2..5 }
            ?: "jpg"
        val filePath = "$directoryPath/$requestId.$fileExtension"
        val fileUrl = NSURL.fileURLWithPath(filePath)
        val wrote = bytes.writeToFile(filePath)
        if (!wrote) return null
        return fileUrl
    }

    private fun ByteArray.writeToFile(path: String): Boolean =
        usePinned { pinned ->
            val file = fopen(path, "wb") ?: return false
            try {
                val written = fwrite(
                    pinned.addressOf(0),
                    1.convert(),
                    size.convert(),
                    file,
                )
                written.toLong() == size.toLong()
            } finally {
                fclose(file)
            }
        }

}
