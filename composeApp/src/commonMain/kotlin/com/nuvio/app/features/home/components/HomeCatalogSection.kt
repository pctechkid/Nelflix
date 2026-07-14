package com.nuvio.app.features.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioAnimatedWatchedBadge
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.bebas_neue_regular
import org.jetbrains.compose.resources.Font

@Composable
fun HomeCatalogRowSection(
    section: HomeCatalogSection,
    modifier: Modifier = Modifier,
    entries: List<MetaPreview> = section.items,
    watchedKeys: Set<String> = emptySet(),
    sectionPadding: Dp? = null,
    onViewAllClick: (() -> Unit)? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    if (sectionPadding != null) {
        HomeCatalogRowSectionContent(
            section = section,
            entries = entries,
            watchedKeys = watchedKeys,
            modifier = modifier.fillMaxWidth(),
            sectionPadding = sectionPadding,
            onViewAllClick = onViewAllClick,
            onPosterClick = onPosterClick,
            onPosterLongClick = onPosterLongClick,
        )
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            HomeCatalogRowSectionContent(
                section = section,
                entries = entries,
                watchedKeys = watchedKeys,
                modifier = Modifier.fillMaxWidth(),
                sectionPadding = homeSectionHorizontalPaddingForWidth(maxWidth.value),
                onViewAllClick = onViewAllClick,
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
            )
        }
    }
}

@Composable
private fun HomeCatalogRowSectionContent(
    section: HomeCatalogSection,
    entries: List<MetaPreview>,
    watchedKeys: Set<String>,
    modifier: Modifier,
    sectionPadding: Dp,
    onViewAllClick: (() -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val homeCatalogSettings by remember {
        HomeCatalogSettingsRepository.snapshot()
        HomeCatalogSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val isRankedCatalog = remember(section.title) {
        section.title.contains("Top 10", ignoreCase = true)
    }

    if (isRankedCatalog) {
        val rankedEntries = remember(entries) {
            entries.mapIndexed { index, item ->
                RankedHomeCatalogEntry(
                    item = item,
                    rank = index + 1,
                )
            }
        }
        NuvioShelfSection(
            title = section.title,
            entries = rankedEntries,
            modifier = modifier,
            headerHorizontalPadding = sectionPadding,
            rowContentPadding = PaddingValues(horizontal = sectionPadding),
            itemSpacing = 10.dp,
            showHeaderAccent = !homeCatalogSettings.hideCatalogUnderline,
            onViewAllClick = onViewAllClick,
            viewAllPillSize = NuvioViewAllPillSize.Compact,
            viewAllIconOnly = true,
            key = { rankedEntry -> rankedEntry.item.stableKey() },
        ) { rankedEntry ->
            val item = rankedEntry.item
            HomeRankedPosterCard(
                rank = rankedEntry.rank,
                item = item,
                isWatched = WatchingState.isPosterWatched(
                    watchedKeys = watchedKeys,
                    item = item,
                ),
                onClick = onPosterClick?.let { { it(item) } },
                onLongClick = onPosterLongClick?.let { { it(item) } },
            )
        }
    } else {
        NuvioShelfSection(
            title = section.title,
            entries = entries,
            modifier = modifier,
            headerHorizontalPadding = sectionPadding,
            rowContentPadding = PaddingValues(horizontal = sectionPadding),
            showHeaderAccent = !homeCatalogSettings.hideCatalogUnderline,
            onViewAllClick = onViewAllClick,
            viewAllPillSize = NuvioViewAllPillSize.Compact,
            viewAllIconOnly = true,
            key = { item -> item.stableKey() },
        ) { item ->
            HomePosterCard(
                item = item,
                useLandscapeBackdropMode = posterCardStyle.catalogLandscapeModeEnabled,
                isWatched = WatchingState.isPosterWatched(
                    watchedKeys = watchedKeys,
                    item = item,
                ),
                onClick = onPosterClick?.let { { it(item) } },
                onLongClick = onPosterLongClick?.let { { it(item) } },
            )
        }
    }
}

private data class RankedHomeCatalogEntry(
    val item: MetaPreview,
    val rank: Int,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeRankedPosterCard(
    rank: Int,
    item: MetaPreview,
    isWatched: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val posterWidth = posterCardStyle.widthDp.dp
    val rankDigitCount = rank.toString().length
    val posterStartOffset = when {
        rankDigitCount >= 3 -> 72.dp
        rankDigitCount == 2 -> 54.dp
        else -> 42.dp
    }
    val numberXOffset = when {
        rankDigitCount >= 3 -> (-18).dp
        rankDigitCount == 2 -> (-8).dp
        else -> 0.dp
    }
    val numberFontScale = when {
        rankDigitCount >= 3 -> 0.78f
        rankDigitCount == 2 -> 0.88f
        else -> 1.0f
    }
    val rankFontSize = (posterCardStyle.widthDp * numberFontScale).sp
    val rankFontFamily = FontFamily(
        Font(Res.font.bebas_neue_regular, FontWeight.Bold, FontStyle.Normal),
    )
    val cardShape = RoundedCornerShape(5.dp)
    val cardWidth = posterStartOffset + posterWidth

    Box(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(cardWidth.value / (posterCardStyle.widthDp / RankedPosterAspectRatio))
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick,
            ),
    ) {
        Text(
            text = rank.toString(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = numberXOffset, y = RankedNumberBottomCorrection),
            style = MaterialTheme.typography.displayLarge.copy(
                color = Color.White.copy(alpha = 0.96f),
                fontFamily = rankFontFamily,
                fontSize = rankFontSize,
                lineHeight = rankFontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                drawStyle = Stroke(width = 5.2f),
            ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = posterStartOffset)
                .width(posterWidth)
                .aspectRatio(RankedPosterAspectRatio)
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 0.6.dp,
                    color = Color.White.copy(alpha = 0.16f),
                    shape = cardShape,
                ),
        ) {
            if (!item.poster.isNullOrBlank()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = item.name,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            NuvioAnimatedWatchedBadge(
                isVisible = isWatched,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }
    }
}

private const val RankedPosterAspectRatio = 0.675f
private val RankedNumberBottomCorrection = 28.dp
