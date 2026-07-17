package com.nuvio.app.features.watching.sync

import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlinx.serialization.Serializable

data class ProgressSyncRecord(
    val contentId: String,
    val contentType: String,
    val videoId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val position: Long = 0L,
    val duration: Long = 0L,
    val lastWatched: Long = 0L,
    val displayMetadata: ProgressDisplayMetadata? = null,
)

@Serializable
data class ProgressDisplayMetadata(
    val title: String? = null,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val pauseDescription: String? = null,
)

interface ProgressSyncAdapter {
    suspend fun pull(profileId: Int): List<ProgressSyncRecord>

    suspend fun push(
        profileId: Int,
        entries: Collection<WatchProgressEntry>,
    )

    suspend fun delete(
        profileId: Int,
        entries: Collection<WatchProgressEntry>,
    )
}
