package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerMediaTracksTest {
    @Test
    fun `late tracks replace the initial empty snapshot`() {
        val incoming = PlayerMediaTracks(
            audioTracks = listOf(audioTrack(index = 0, label = "English")),
            subtitleTracks = listOf(subtitleTrack(index = 0, label = "English")),
        )

        val result = mergePlayerMediaTrackSnapshot(
            previous = PlayerMediaTrackSnapshot(sourceKey = "source-a"),
            sourceKey = "source-a",
            incoming = incoming,
        )

        assertEquals(incoming, result.tracks)
    }

    @Test
    fun `transient empty refresh does not erase known tracks for the same source`() {
        val known = PlayerMediaTracks(
            audioTracks = listOf(audioTrack(index = 0, label = "English")),
            subtitleTracks = listOf(subtitleTrack(index = 0, label = "English")),
            chapters = listOf(PlayerChapter(index = 0, title = "Opening", timeMs = 0L)),
        )

        val result = mergePlayerMediaTrackSnapshot(
            previous = PlayerMediaTrackSnapshot(sourceKey = "source-a", tracks = known),
            sourceKey = "source-a",
            incoming = PlayerMediaTracks(),
        )

        assertEquals(known, result.tracks)
    }

    @Test
    fun `source change clears tracks from the previous stream`() {
        val previous = PlayerMediaTrackSnapshot(
            sourceKey = "source-a",
            tracks = PlayerMediaTracks(audioTracks = listOf(audioTrack(0, "English"))),
        )

        val result = mergePlayerMediaTrackSnapshot(
            previous = previous,
            sourceKey = "source-b",
            incoming = PlayerMediaTracks(),
        )

        assertEquals("source-b", result.sourceKey)
        assertTrue(result.tracks.audioTracks.isEmpty())
    }

    @Test
    fun `unnamed tracks receive type-specific labels`() {
        assertEquals(
            "Audio 1",
            buildPlayerTrackLabel(PlayerTrackKind.Audio, 0, listOf(null, " ", null)),
        )
        assertEquals(
            "Subtitle 2",
            buildPlayerTrackLabel(PlayerTrackKind.Subtitle, 1, emptyList()),
        )
    }

    @Test
    fun `displayed track index resolves the stable mpv track id`() {
        assertEquals(42, playerTrackIdAtDisplayedIndex(listOf("17", "42"), displayedIndex = 1))
        assertEquals(null, playerTrackIdAtDisplayedIndex(listOf("17", "42"), displayedIndex = 2))
        assertEquals(null, playerTrackIdAtDisplayedIndex(listOf("external"), displayedIndex = 0))
    }

    @Test
    fun `selected track mapping represents none as minus one`() {
        assertEquals(1, selectedPlayerTrackIndex(listOf(false, true, false)))
        assertEquals(-1, selectedPlayerTrackIndex(listOf(false, false)))
        assertEquals(-1, selectedPlayerTrackIndex(emptyList()))
    }

    @Test
    fun `chapters are chronological and normalize unnamed titles by displayed order`() {
        val normalized = normalizePlayerChapters(
            listOf(
                PlayerChapter(index = 8, title = "(unnamed)", timeMs = 30_000L),
                PlayerChapter(index = 3, title = "Opening", timeMs = 0L),
                PlayerChapter(index = 5, title = " unnamed ", timeMs = 15_000L),
                PlayerChapter(index = 9, title = "Finale", timeMs = 45_000L),
            ),
        )

        assertEquals(listOf(3, 5, 8, 9), normalized.map(PlayerChapter::index))
        assertEquals(listOf("Opening", "Chapter 2", "Chapter 3", "Finale"), normalized.map(PlayerChapter::title))
        assertEquals(listOf(0L, 15_000L, 30_000L, 45_000L), normalized.map(PlayerChapter::timeMs))
    }

    @Test
    fun `all unnamed chapters receive stable sequential names`() {
        val normalized = normalizePlayerChapters(
            listOf(
                PlayerChapter(index = 0, title = "", timeMs = 0L),
                PlayerChapter(index = 1, title = "   ", timeMs = 5_000L),
                PlayerChapter(index = 2, title = "UNNAMED", timeMs = 10_000L),
            ),
        )

        assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 3"), normalized.map(PlayerChapter::title))
    }

    @Test
    fun `embedded English SDH outranks normal and partial English tracks`() {
        val tracks = listOf(
            subtitleTrack(0, "English Signs & Songs", language = "en"),
            subtitleTrack(1, "English Dialogue", language = "en"),
            subtitleTrack(2, "English", language = "en"),
            subtitleTrack(3, "English SDH", language = "en"),
        )

        assertEquals(3, findPreferredEmbeddedSubtitleTrackIndex(tracks, listOf("en")))
    }

    @Test
    fun `normal embedded English wins when SDH is unavailable`() {
        val tracks = listOf(
            subtitleTrack(0, "English Forced", language = "en", isForced = true),
            subtitleTrack(1, "English Signs", language = "en"),
            subtitleTrack(2, "English Dialogue", language = "en"),
            subtitleTrack(3, "English", language = "en"),
        )

        assertEquals(3, findPreferredEmbeddedSubtitleTrackIndex(tracks, listOf("en")))
    }

    @Test
    fun `embedded language can be inferred from the track label`() {
        val tracks = listOf(subtitleTrack(0, "English SDH", language = null))

        assertEquals(0, findPreferredEmbeddedSubtitleTrackIndex(tracks, listOf("en")))
    }

    @Test
    fun `partial or external English tracks allow addon fallback`() {
        val tracks = listOf(
            subtitleTrack(0, "English Forced", language = "en", isForced = true),
            subtitleTrack(1, "English Signs & Songs", language = "en"),
            subtitleTrack(2, "English", language = "en", isExternal = true),
        )

        assertEquals(-1, findPreferredEmbeddedSubtitleTrackIndex(tracks, listOf("en")))
    }

    @Test
    fun `addon fallback waits for discovery and remains blocked when embedded match exists`() {
        assertTrue(shouldWaitForEmbeddedSubtitleDiscovery(discoveryComplete = false, hasEmbeddedMatch = false))
        assertTrue(shouldWaitForEmbeddedSubtitleDiscovery(discoveryComplete = true, hasEmbeddedMatch = true))
        assertEquals(
            false,
            shouldWaitForEmbeddedSubtitleDiscovery(discoveryComplete = true, hasEmbeddedMatch = false),
        )
    }

    private fun audioTrack(index: Int, label: String): AudioTrack =
        AudioTrack(index = index, id = "a$index", label = label)

    private fun subtitleTrack(
        index: Int,
        label: String,
        language: String? = null,
        isForced: Boolean = false,
        isExternal: Boolean = false,
    ): SubtitleTrack = SubtitleTrack(
        index = index,
        id = "s$index",
        label = label,
        language = language,
        isForced = isForced,
        isExternal = isExternal,
    )
}
