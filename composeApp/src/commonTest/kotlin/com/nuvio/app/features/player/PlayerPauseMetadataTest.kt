package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerPauseMetadataTest {

    @Test
    fun `intentional pause can show pause metadata`() {
        assertTrue(
            shouldShowAutomaticPauseMetadata(
                shouldPlay = false,
                playbackSnapshot = pausedSnapshot(),
                suppressForScrub = false,
                controlsLocked = false,
                hasPlaybackError = false,
            ),
        )
    }

    @Test
    fun `transient not playing snapshot before eof cannot flash pause metadata`() {
        assertFalse(
            shouldShowAutomaticPauseMetadata(
                shouldPlay = true,
                playbackSnapshot = pausedSnapshot(positionMs = 1_499_900L),
                suppressForScrub = false,
                controlsLocked = false,
                hasPlaybackError = false,
            ),
        )
    }

    @Test
    fun `confirmed eof remains stable across stale non-ended snapshots`() {
        val confirmedEnd = pausedSnapshot(positionMs = 1_500_000L).copy(isEnded = true)
        val staleAfterEnd = pausedSnapshot(positionMs = 1_500_000L)
        var endLatched = false

        endLatched = nextPlaybackEndUiLatch(endLatched, confirmedEnd)
        val firstEndedUi = confirmedEnd.withPlaybackEndUiLatch(endLatched)
        endLatched = nextPlaybackEndUiLatch(endLatched, staleAfterEnd)
        val staleUi = staleAfterEnd.withPlaybackEndUiLatch(endLatched)
        endLatched = nextPlaybackEndUiLatch(endLatched, staleAfterEnd)
        val repeatedStaleUi = staleAfterEnd.withPlaybackEndUiLatch(endLatched)

        assertTrue(endLatched)
        assertTrue(firstEndedUi.isEnded)
        assertTrue(staleUi.isEnded)
        assertTrue(repeatedStaleUi.isEnded)
        assertFalse(
            shouldShowAutomaticPauseMetadata(
                shouldPlay = false,
                playbackSnapshot = staleUi,
                suppressForScrub = false,
                controlsLocked = false,
                hasPlaybackError = false,
            ),
        )
    }

    @Test
    fun `playing snapshot is not forced into ended state`() {
        val resumed = pausedSnapshot(positionMs = 0L).copy(isPlaying = true)
        val endLatched = nextPlaybackEndUiLatch(
            currentlyLatched = true,
            incomingSnapshot = resumed,
        )

        assertFalse(endLatched)
        assertFalse(resumed.withPlaybackEndUiLatch(endLatched).isEnded)
    }

    @Test
    fun `repeated terminal snapshots produce exactly one controls reveal`() {
        val eof = pausedSnapshot(positionMs = 1_500_000L).copy(isEnded = true)
        val stalePaused = eof.copy(isEnded = false)
        val stalePlayingNearEnd = eof.copy(
            isPlaying = true,
            isEnded = false,
            positionMs = 1_499_900L,
        )
        var endLatched = false
        var revealCount = 0

        listOf(eof, stalePaused, stalePlayingNearEnd, eof).forEach { snapshot ->
            val transition = reducePlaybackEndUiState(endLatched, snapshot)
            endLatched = transition.isLatched
            if (transition.enteredEnd) revealCount += 1
        }

        assertTrue(endLatched)
        assertEquals(1, revealCount)
    }

    @Test
    fun `replay resets eof latch and permits one later reveal`() {
        val eof = pausedSnapshot(positionMs = 1_500_000L).copy(isEnded = true)
        val replay = pausedSnapshot(positionMs = 1_000L).copy(isPlaying = true)
        var transition = reducePlaybackEndUiState(false, eof)
        assertTrue(transition.enteredEnd)

        transition = reducePlaybackEndUiState(transition.isLatched, replay)
        assertTrue(transition.resetForReplay)
        assertFalse(transition.isLatched)

        transition = reducePlaybackEndUiState(transition.isLatched, eof)
        assertTrue(transition.enteredEnd)
    }

    @Test
    fun `ended controls never schedule auto hide`() {
        assertFalse(
            shouldSchedulePlaybackControlsAutoHide(
                controlsVisible = true,
                isScrubbingTimeline = false,
                playbackSnapshot = pausedSnapshot(positionMs = 1_500_000L).copy(
                    isPlaying = true,
                    isEnded = true,
                ),
                showParentalGuide = false,
                hasPlaybackError = false,
            ),
        )
    }

    @Test
    fun `terminal loading and suppressed states cannot show pause metadata`() {
        val ended = pausedSnapshot().copy(isEnded = true)
        val loading = pausedSnapshot().copy(isLoading = true)

        assertFalse(shouldShow(ended))
        assertFalse(shouldShow(loading))
        assertFalse(shouldShow(pausedSnapshot(), suppressForScrub = true))
        assertFalse(shouldShow(pausedSnapshot(), controlsLocked = true))
        assertFalse(shouldShow(pausedSnapshot(), hasPlaybackError = true))
    }

    @Test
    fun `resume overlay stays visible while mpv still reports the beginning`() {
        assertFalse(
            shouldRevealResumedPlayback(
                playbackSnapshot = pausedSnapshot(positionMs = 0L),
                initialPositionMs = 600_000L,
                initialProgressFraction = null,
            ),
        )
        assertTrue(
            shouldRevealResumedPlayback(
                playbackSnapshot = pausedSnapshot(positionMs = 598_000L),
                initialPositionMs = 600_000L,
                initialProgressFraction = null,
            ),
        )
    }

    @Test
    fun `fraction resume waits until duration resolves and target is reached`() {
        assertFalse(
            shouldRevealResumedPlayback(
                playbackSnapshot = pausedSnapshot(positionMs = 0L).copy(durationMs = 0L),
                initialPositionMs = 0L,
                initialProgressFraction = 0.5f,
            ),
        )
        assertTrue(
            shouldRevealResumedPlayback(
                playbackSnapshot = pausedSnapshot(positionMs = 748_000L),
                initialPositionMs = 0L,
                initialProgressFraction = 0.5f,
            ),
        )
    }

    private fun shouldShow(
        snapshot: PlayerPlaybackSnapshot,
        suppressForScrub: Boolean = false,
        controlsLocked: Boolean = false,
        hasPlaybackError: Boolean = false,
    ): Boolean = shouldShowAutomaticPauseMetadata(
        shouldPlay = false,
        playbackSnapshot = snapshot,
        suppressForScrub = suppressForScrub,
        controlsLocked = controlsLocked,
        hasPlaybackError = hasPlaybackError,
    )

    private fun pausedSnapshot(positionMs: Long = 300_000L): PlayerPlaybackSnapshot =
        PlayerPlaybackSnapshot(
            isLoading = false,
            isPlaying = false,
            isEnded = false,
            durationMs = 1_500_000L,
            positionMs = positionMs,
        )
}
