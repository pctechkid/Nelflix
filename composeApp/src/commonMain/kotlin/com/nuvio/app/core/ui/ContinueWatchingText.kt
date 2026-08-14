package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.continueWatchingEpisodeCode
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun localizedContinueWatchingSubtitle(item: ContinueWatchingItem): String {
    val seasonNumber = item.seasonNumber
    val episodeNumber = item.episodeNumber
    val episodeTitle = item.episodeTitle?.takeIf { it.isNotBlank() }

    val episodeCode = continueWatchingEpisodeCode(seasonNumber, episodeNumber)
    val base = when {
        episodeCode != null && item.isNextUp -> listOf(
            stringResource(Res.string.continue_watching_up_next),
            episodeCode,
        ).joinToString(" • ")
        episodeCode != null -> episodeCode
        item.isNextUp ->
            stringResource(Res.string.continue_watching_up_next)
        else ->
            stringResource(Res.string.media_movie)
    }

    return episodeTitle?.let { "$base • $it" } ?: base
}
