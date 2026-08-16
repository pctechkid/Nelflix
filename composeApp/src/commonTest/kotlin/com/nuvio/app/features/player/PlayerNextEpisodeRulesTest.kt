package com.nuvio.app.features.player

import com.nuvio.app.features.player.skip.PlayerNextEpisodeRules
import com.nuvio.app.features.streams.StreamAutoPlayMode
import com.nuvio.app.features.streams.StreamAutoPlaySource
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerNextEpisodeRulesTest {

    @Test
    fun firstStreamModeIsTrueAutomatic() {
        assertTrue(
            PlayerNextEpisodeRules.isTrueAutomaticStreamMode(
                mode = StreamAutoPlayMode.FIRST_STREAM,
                regexPattern = "",
            ),
        )
    }

    @Test
    fun manualModeIsNotTrueAutomatic() {
        assertFalse(
            PlayerNextEpisodeRules.isTrueAutomaticStreamMode(
                mode = StreamAutoPlayMode.MANUAL,
                regexPattern = "1080p",
            ),
        )
    }

    @Test
    fun regexModeRequiresNonBlankValidPattern() {
        assertFalse(
            PlayerNextEpisodeRules.isTrueAutomaticStreamMode(
                mode = StreamAutoPlayMode.REGEX_MATCH,
                regexPattern = "",
            ),
        )
        assertFalse(
            PlayerNextEpisodeRules.isTrueAutomaticStreamMode(
                mode = StreamAutoPlayMode.REGEX_MATCH,
                regexPattern = "[",
            ),
        )
        assertTrue(
            PlayerNextEpisodeRules.isTrueAutomaticStreamMode(
                mode = StreamAutoPlayMode.REGEX_MATCH,
                regexPattern = "1080p|720p",
            ),
        )
    }

    @Test
    fun nextEpisodeAutoAdvanceAllowsManualPlaybackButRejectsGuests() {
        assertTrue(
            PlayerNextEpisodeRules.isNextEpisodeAutoAdvanceEligible(
                isSeriesEpisode = true,
                isWatchTogetherGuest = false,
            ),
        )
        assertFalse(
            PlayerNextEpisodeRules.isNextEpisodeAutoAdvanceEligible(
                isSeriesEpisode = true,
                isWatchTogetherGuest = true,
            ),
        )
    }

    @Test
    fun automaticSeriesPlaybackIsEligibleWithoutLegacyToggle() {
        assertTrue(
            PlayerNextEpisodeRules.isNextEpisodeAutoAdvanceEligible(
                isSeriesEpisode = true,
                isWatchTogetherGuest = false,
            ),
        )
    }

    @Test
    fun manualModeUsesBoundedNextEpisodeFallbackPolicy() {
        val policy = PlayerNextEpisodeRules.nextEpisodeAutoPlayPolicy(
            mode = StreamAutoPlayMode.MANUAL,
            regexPattern = "user pattern",
            source = StreamAutoPlaySource.ALL_SOURCES,
            selectedAddons = setOf("Other"),
            selectedPlugins = setOf("Plugin"),
            preferBingeGroup = true,
            maxFileSizeBytes = null,
            selectionTimeoutSeconds = 8,
        )

        assertEquals(StreamAutoPlayMode.REGEX_MATCH, policy.mode)
        assertEquals("(2160p|4k|1080p)", policy.regexPattern)
        assertEquals(StreamAutoPlaySource.INSTALLED_ADDONS_ONLY, policy.source)
        assertEquals(setOf("Premium", "Plus"), policy.selectedAddons)
        assertEquals(emptySet(), policy.selectedPlugins)
        assertFalse(policy.preferBingeGroup)
        assertEquals(10_000_000_000L, policy.maxFileSizeBytes)
        assertEquals(0, policy.selectionTimeoutSeconds)
    }

    @Test
    fun automaticModePreservesUserNextEpisodePolicy() {
        val policy = PlayerNextEpisodeRules.nextEpisodeAutoPlayPolicy(
            mode = StreamAutoPlayMode.REGEX_MATCH,
            regexPattern = "720p",
            source = StreamAutoPlaySource.ENABLED_PLUGINS_ONLY,
            selectedAddons = setOf("Addon"),
            selectedPlugins = setOf("Plugin"),
            preferBingeGroup = true,
            maxFileSizeBytes = 2_000L,
            selectionTimeoutSeconds = 7,
        )

        assertEquals(StreamAutoPlayMode.REGEX_MATCH, policy.mode)
        assertEquals("720p", policy.regexPattern)
        assertEquals(StreamAutoPlaySource.ENABLED_PLUGINS_ONLY, policy.source)
        assertEquals(setOf("Addon"), policy.selectedAddons)
        assertEquals(setOf("Plugin"), policy.selectedPlugins)
        assertTrue(policy.preferBingeGroup)
        assertEquals(2_000L, policy.maxFileSizeBytes)
        assertEquals(7, policy.selectionTimeoutSeconds)
    }

    @Test
    fun staleSnapshotsFromPreviousEpisodeAreRejectedAfterHandoff() {
        assertFalse(
            isSnapshotFromActivePlayback(
                snapshotGeneration = 3L,
                activeGeneration = 4L,
            ),
        )
        assertTrue(
            isSnapshotFromActivePlayback(
                snapshotGeneration = 4L,
                activeGeneration = 4L,
            ),
        )
    }
}
