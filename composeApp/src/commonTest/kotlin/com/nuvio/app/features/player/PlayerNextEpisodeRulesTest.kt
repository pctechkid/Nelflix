package com.nuvio.app.features.player

import com.nuvio.app.features.player.skip.PlayerNextEpisodeRules
import com.nuvio.app.features.streams.StreamAutoPlayMode
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
    fun nextEpisodeAutoAdvanceRejectsManualOriginAndGuests() {
        assertFalse(
            PlayerNextEpisodeRules.isNextEpisodeAutoAdvanceEligible(
                isSeriesEpisode = true,
                streamAutoPlayMode = StreamAutoPlayMode.FIRST_STREAM,
                streamAutoPlayRegex = "",
                launchedFromManualStreamSelection = true,
                isWatchTogetherGuest = false,
            ),
        )
        assertFalse(
            PlayerNextEpisodeRules.isNextEpisodeAutoAdvanceEligible(
                isSeriesEpisode = true,
                streamAutoPlayMode = StreamAutoPlayMode.FIRST_STREAM,
                streamAutoPlayRegex = "",
                launchedFromManualStreamSelection = false,
                isWatchTogetherGuest = true,
            ),
        )
    }

    @Test
    fun automaticSeriesPlaybackIsEligibleWithoutLegacyToggle() {
        assertTrue(
            PlayerNextEpisodeRules.isNextEpisodeAutoAdvanceEligible(
                isSeriesEpisode = true,
                streamAutoPlayMode = StreamAutoPlayMode.FIRST_STREAM,
                streamAutoPlayRegex = "",
                launchedFromManualStreamSelection = false,
                isWatchTogetherGuest = false,
            ),
        )
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
