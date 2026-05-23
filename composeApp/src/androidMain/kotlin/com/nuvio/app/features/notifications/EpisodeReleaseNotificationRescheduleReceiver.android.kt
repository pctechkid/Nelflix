package com.nuvio.app.features.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class EpisodeReleaseNotificationRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in supportedActions) return

        val request = OneTimeWorkRequestBuilder<EpisodeReleaseNotificationRescheduleWorker>()
            .addTag(rescheduleWorkName)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            rescheduleWorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        const val rescheduleWorkName = "episode_release_notifications_reschedule"

        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
