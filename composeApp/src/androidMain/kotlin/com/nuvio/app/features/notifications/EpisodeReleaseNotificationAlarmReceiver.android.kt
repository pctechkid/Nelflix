package com.nuvio.app.features.notifications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

class EpisodeReleaseNotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                showNotification(context.applicationContext, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun showNotification(context: Context, intent: Intent) {
        EpisodeReleaseNotificationPlatform.initialize(context)
        if (!EpisodeReleaseNotificationPlatform.notificationsAuthorized()) return

        val requestId = intent.getStringExtra(EpisodeReleaseNotificationPlatform.workerRequestIdKey)
            ?: return
        val title = intent.getStringExtra(EpisodeReleaseNotificationPlatform.workerTitleKey)
            ?: return
        val body = intent.getStringExtra(EpisodeReleaseNotificationPlatform.workerBodyKey)
            ?: return
        val deepLink = intent.getStringExtra(EpisodeReleaseNotificationPlatform.workerDeepLinkKey)
            ?: return
        val backdropUrl = intent.getStringExtra(EpisodeReleaseNotificationPlatform.workerBackdropUrlKey)

        val request = EpisodeReleaseNotificationRequest(
            requestId = requestId,
            notificationTitle = title,
            notificationBody = body,
            releaseDateIso = "",
            deepLinkUrl = deepLink,
            backdropUrl = backdropUrl,
        )

        val notification = EpisodeReleaseNotificationPlatform.buildNotification(
            context = context,
            request = request,
        )

        NotificationManagerCompat.from(context)
            .notify(abs(requestId.hashCode()), notification)
    }
}
