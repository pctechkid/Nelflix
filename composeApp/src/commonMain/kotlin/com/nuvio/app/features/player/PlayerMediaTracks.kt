package com.nuvio.app.features.player

data class PlayerMediaTracks(
    val audioTracks: List<AudioTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val chapters: List<PlayerChapter> = emptyList(),
)

data class PlayerMediaTrackSnapshot(
    val sourceKey: String,
    val tracks: PlayerMediaTracks = PlayerMediaTracks(),
)

enum class PlayerTrackKind {
    Audio,
    Subtitle,
}

internal fun mergePlayerMediaTrackSnapshot(
    previous: PlayerMediaTrackSnapshot,
    sourceKey: String,
    incoming: PlayerMediaTracks,
): PlayerMediaTrackSnapshot {
    if (previous.sourceKey != sourceKey) {
        return PlayerMediaTrackSnapshot(sourceKey = sourceKey, tracks = incoming)
    }
    return PlayerMediaTrackSnapshot(
        sourceKey = sourceKey,
        tracks = PlayerMediaTracks(
            audioTracks = incoming.audioTracks.ifEmpty { previous.tracks.audioTracks },
            subtitleTracks = incoming.subtitleTracks.ifEmpty { previous.tracks.subtitleTracks },
            chapters = incoming.chapters.ifEmpty { previous.tracks.chapters },
        ),
    )
}

internal fun buildPlayerTrackLabel(
    kind: PlayerTrackKind,
    displayedIndex: Int,
    candidates: List<String?>,
): String = candidates
    .asSequence()
    .mapNotNull { value -> value?.trim()?.takeIf(String::isNotEmpty) }
    .distinct()
    .joinToString(" - ")
    .ifBlank { defaultPlayerTrackLabel(kind, displayedIndex) }

internal fun defaultPlayerTrackLabel(
    kind: PlayerTrackKind,
    displayedIndex: Int,
): String = when (kind) {
    PlayerTrackKind.Audio -> "Audio ${displayedIndex + 1}"
    PlayerTrackKind.Subtitle -> "Subtitle ${displayedIndex + 1}"
}

internal fun playerTrackIdAtDisplayedIndex(
    trackIds: List<String>,
    displayedIndex: Int,
): Int? = trackIds
    .getOrNull(displayedIndex)
    ?.toIntOrNull()

internal fun selectedPlayerTrackIndex(selectedStates: List<Boolean>): Int =
    selectedStates.indexOfFirst { isSelected -> isSelected }

internal fun shouldWaitForEmbeddedSubtitleDiscovery(
    discoveryComplete: Boolean,
    hasEmbeddedMatch: Boolean,
): Boolean = !discoveryComplete || hasEmbeddedMatch

internal fun findPreferredEmbeddedSubtitleTrackIndex(
    tracks: List<SubtitleTrack>,
    targets: List<String>,
): Int {
    if (targets.isEmpty()) return -1

    for ((targetPosition, target) in targets.withIndex()) {
        val normalizedTarget = normalizeLanguageCode(target) ?: continue
        if (normalizedTarget == SubtitleLanguageOption.FORCED) {
            val forcedIndex = tracks.indexOfFirst { track -> !track.isExternal && track.isForced }
            if (forcedIndex >= 0) return forcedIndex
            if (targetPosition == 0) return -1
            continue
        }

        val match = tracks
            .asSequence()
            .filter { track ->
                !track.isExternal &&
                    track.matchesSubtitleLanguage(normalizedTarget) &&
                    track.isPreferredFullSubtitle()
            }
            .sortedWith(
                compareByDescending<SubtitleTrack> { track ->
                    subtitleAccessibilityScoreForText(
                        listOf(track.label, track.language, track.id).joinToString(" "),
                    )
                }.thenBy(SubtitleTrack::index),
            )
            .firstOrNull()
        if (match != null) return match.index
    }

    return -1
}

internal fun subtitleAccessibilityScoreForText(value: String): Int {
    val text = value.lowercase()
    return when {
        text.contains("sdh") -> 4
        text.contains("hearing impaired") || text.contains("hearing-impaired") -> 3
        text.contains("closed caption") || text.contains("closed-caption") -> 2
        Regex("""(^|[^a-z0-9])(cc|hi|hoh)([^a-z0-9]|$)""").containsMatchIn(text) -> 1
        else -> 0
    }
}

internal fun isPreferredFullSubtitleText(
    value: String,
    isForced: Boolean = false,
): Boolean {
    if (isForced) return false
    val text = value.lowercase()
    if (
        "forced" in text ||
        "signs" in text ||
        "songs" in text ||
        "sign/song" in text ||
        "signs & songs" in text ||
        "signs and songs" in text ||
        "lyrics" in text ||
        "karaoke" in text
    ) {
        return false
    }
    if (subtitleAccessibilityScoreForText(text) > 0) return true
    return !Regex("""(^|[^a-z])(dialog|dialogue)([^a-z]|$)""").containsMatchIn(text)
}

private fun SubtitleTrack.matchesSubtitleLanguage(target: String): Boolean =
    languageMatchesPreference(language, target) || languageMatchesPreference(label, target)

private fun SubtitleTrack.isPreferredFullSubtitle(): Boolean =
    isPreferredFullSubtitleText(
        value = listOf(label, language, id).joinToString(" "),
        isForced = isForced,
    )

internal fun normalizePlayerChapters(chapters: List<PlayerChapter>): List<PlayerChapter> =
    chapters
        .sortedBy(PlayerChapter::timeMs)
        .mapIndexed { displayedIndex, chapter ->
            val normalizedTitle = chapter.title
                .trim()
                .takeUnless { title ->
                    title.isEmpty() ||
                        title.equals("unnamed", ignoreCase = true) ||
                        title.equals("(unnamed)", ignoreCase = true)
                }
                ?: "Chapter ${displayedIndex + 1}"
            chapter.copy(
                title = normalizedTitle,
                timeMs = chapter.timeMs.coerceAtLeast(0L),
            )
        }
