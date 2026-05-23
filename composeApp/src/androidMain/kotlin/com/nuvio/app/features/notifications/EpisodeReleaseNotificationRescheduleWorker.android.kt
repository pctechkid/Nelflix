package com.nuvio.app.features.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nuvio.app.core.auth.AuthStorage
import com.nuvio.app.features.addons.AddonStorage
import com.nuvio.app.features.library.LibraryStorage
import com.nuvio.app.features.profiles.ProfileStorage

class EpisodeReleaseNotificationRescheduleWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        return runCatching {
            initializeNotificationDependencies(applicationContext)
            EpisodeReleaseNotificationsRepository.refreshNow()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    private fun initializeNotificationDependencies(context: Context) {
        AddonStorage.initialize(context)
        AuthStorage.initialize(context)
        LibraryStorage.initialize(context)
        ProfileStorage.initialize(context)
        EpisodeReleaseNotificationsStorage.initialize(context)
        EpisodeReleaseNotificationPlatform.initialize(context)
    }
}
