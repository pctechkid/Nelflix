package com.nuvio.app.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.features.home.FeaturedProductionEntity
import com.nuvio.app.features.tmdb.TmdbEntityHeader
import com.nuvio.app.features.tmdb.TmdbMetadataService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_view_all
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeFeaturedProductionsSection(
    entries: List<FeaturedProductionEntity>,
    modifier: Modifier = Modifier,
    sectionPadding: Dp? = null,
    onEntityClick: (FeaturedProductionEntity) -> Unit,
    onViewAllClick: () -> Unit,
) {
    if (entries.isEmpty()) return

    var headers by remember { mutableStateOf<Map<String, TmdbEntityHeader>>(emptyMap()) }

    LaunchedEffect(entries) {
        headers = coroutineScope {
            entries.map { entity ->
                async {
                    entity.key to TmdbMetadataService.fetchEntityHeader(
                        entityKind = entity.kind,
                        entityId = entity.id,
                        fallbackName = entity.name,
                    )
                }
            }.awaitAll()
                .mapNotNull { (key, header) -> header?.let { key to it } }
                .toMap()
        }
    }

    if (sectionPadding != null) {
        HomeFeaturedProductionsRow(
            entries = entries,
            headers = headers,
            modifier = modifier,
            sectionPadding = sectionPadding,
            onEntityClick = onEntityClick,
            onViewAllClick = onViewAllClick,
        )
    } else {
        BoxWithConstraints(modifier = modifier) {
            HomeFeaturedProductionsRow(
                entries = entries,
                headers = headers,
                modifier = Modifier,
                sectionPadding = homeSectionHorizontalPaddingForWidth(maxWidth.value),
                onEntityClick = onEntityClick,
                onViewAllClick = onViewAllClick,
            )
        }
    }
}

@Composable
private fun HomeFeaturedProductionsRow(
    entries: List<FeaturedProductionEntity>,
    headers: Map<String, TmdbEntityHeader>,
    modifier: Modifier,
    sectionPadding: Dp,
    onEntityClick: (FeaturedProductionEntity) -> Unit,
    onViewAllClick: () -> Unit,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = sectionPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = entries, key = { it.key }) { entity ->
            FeaturedProductionTile(
                entity = entity,
                header = headers[entity.key],
                onClick = { onEntityClick(entity) },
            )
        }

        item(key = "featured-production-view-all") {
            FeaturedProductionViewAllTile(onClick = onViewAllClick)
        }
    }
}

@Composable
fun FeaturedProductionTile(
    entity: FeaturedProductionEntity,
    header: TmdbEntityHeader?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val logo = header?.logo
    val displayName = header?.name ?: entity.name

    Box(
        modifier = modifier
            .width(92.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEDEDED))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!logo.isNullOrBlank()) {
            AsyncImage(
                model = logo,
                contentDescription = displayName,
                modifier = Modifier
                    .width(68.dp)
                    .height(24.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Color(0xFF202020),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FeaturedProductionViewAllTile(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(92.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.home_view_all),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
