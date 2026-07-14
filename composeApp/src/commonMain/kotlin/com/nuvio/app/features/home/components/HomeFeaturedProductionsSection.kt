package com.nuvio.app.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.delay
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
    var resolvedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(entries) {
        resolvedKeys = emptySet()
        headers = coroutineScope {
            entries.map { entity ->
                async {
                    var header: TmdbEntityHeader? = null
                    for (attempt in 0..2) {
                        header = TmdbMetadataService.fetchEntityHeader(
                            entityKind = entity.kind,
                            entityId = entity.id,
                            fallbackName = entity.name,
                        )
                        if (!header?.logo.isNullOrBlank()) break
                        if (attempt < 2) delay(350L * (attempt + 1))
                    }
                    entity.key to header
                }
            }.awaitAll()
                .mapNotNull { (key, header) -> header?.let { key to it } }
                .toMap()
        }
        resolvedKeys = entries.mapTo(mutableSetOf()) { it.key }
    }

    if (sectionPadding != null) {
        HomeFeaturedProductionsRow(
            entries = entries,
            headers = headers,
            resolvedKeys = resolvedKeys,
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
                resolvedKeys = resolvedKeys,
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
    resolvedKeys: Set<String>,
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
                isLoading = entity.key !in resolvedKeys,
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
    isLoading: Boolean = false,
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
        } else if (isLoading) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFFD2D2D2)),
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
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = stringResource(Res.string.home_view_all),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(26.dp),
        )
    }
}
