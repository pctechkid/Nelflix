package com.nuvio.app.features.notifications

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EpisodeReleaseNotificationsRefreshTest {
    @Test
    fun refreshesOnlyWhenEnabledTrackedShowsActuallyChange() {
        assertTrue(
            shouldRefreshReleaseAlertsAfterLibraryUpdate(
                trackedShowsChanged = true,
                notificationsEnabled = true,
            ),
        )
        assertFalse(
            shouldRefreshReleaseAlertsAfterLibraryUpdate(
                trackedShowsChanged = false,
                notificationsEnabled = true,
            ),
        )
        assertFalse(
            shouldRefreshReleaseAlertsAfterLibraryUpdate(
                trackedShowsChanged = true,
                notificationsEnabled = false,
            ),
        )
    }

    @Test
    fun refreshKeyIsStableAcrossTrackedShowIterationOrder() {
        val first = TrackedFollowedShow("show-1", "series", "2026-08-01")
        val second = TrackedFollowedShow("show-2", "series", "2026-08-02")

        assertEquals(
            episodeReleaseRefreshKey(2, true, "UTC", listOf(first, second)),
            episodeReleaseRefreshKey(2, true, "UTC", listOf(second, first)),
        )
    }

    @Test
    fun refreshKeyChangesForStateThatRequiresNewSchedule() {
        val show = TrackedFollowedShow("show-1", "series", "2026-08-01")
        val baseline = episodeReleaseRefreshKey(2, true, "UTC", listOf(show))

        assertNotEquals(baseline, episodeReleaseRefreshKey(3, true, "UTC", listOf(show)))
        assertNotEquals(baseline, episodeReleaseRefreshKey(2, false, "UTC", listOf(show)))
        assertNotEquals(baseline, episodeReleaseRefreshKey(2, true, "UTC", emptyList()))
    }
}
