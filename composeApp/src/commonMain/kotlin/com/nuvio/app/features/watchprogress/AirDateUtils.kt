package com.nuvio.app.features.watchprogress

class ReleaseAlertState(
    val isReleaseAlert: Boolean,
    val isNewSeasonRelease: Boolean,
)

fun calculateReleaseAlertState(
    seedLastUpdatedEpochMs: Long,
    seedSeasonNumber: Int?,
    nextSeasonNumber: Int?,
    releaseEpochMs: Long?,
    nowEpochMs: Long = WatchProgressClock.nowEpochMs(),
): ReleaseAlertState {
    val releaseEpoch = releaseEpochMs
        ?: return ReleaseAlertState(isReleaseAlert = false, isNewSeasonRelease = false)
    val hasAired = nowEpochMs >= releaseEpoch
    val isReleaseAlert = hasAired &&
        releaseEpoch > seedLastUpdatedEpochMs
    val isNewSeasonRelease = isReleaseAlert &&
        seedSeasonNumber != null &&
        nextSeasonNumber != null &&
        nextSeasonNumber != seedSeasonNumber

    return ReleaseAlertState(
        isReleaseAlert = isReleaseAlert,
        isNewSeasonRelease = isNewSeasonRelease,
    )
}
