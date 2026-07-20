package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.addon_title
import nuvio.composeapp.generated.resources.compose_player_built_in
import nuvio.composeapp.generated.resources.compose_player_fetch_subtitles
import nuvio.composeapp.generated.resources.compose_player_none
import nuvio.composeapp.generated.resources.compose_player_subtitles
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubtitleModal(
    visible: Boolean,
    activeTab: SubtitleTab,
    subtitleTracks: List<SubtitleTrack>,
    selectedSubtitleIndex: Int,
    addonSubtitles: List<AddonSubtitle>,
    selectedAddonSubtitleId: String?,
    isLoadingAddonSubtitles: Boolean,
    onTabSelected: (SubtitleTab) -> Unit,
    onBuiltInTrackSelected: (Int) -> Unit,
    onAddonSubtitleSelected: (AddonSubtitle) -> Unit,
    onFetchAddonSubtitles: () -> Unit,
    onSubtitleSyncClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                )
                .background(colorScheme.scrim.copy(alpha = 0.56f)),
            contentAlignment = Alignment.Center,
        ) {
            val maxH = maxHeight

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(300)) { it / 3 } + fadeIn(tween(300)),
                exit = slideOutVertically(tween(250)) { it / 3 } + fadeOut(tween(250)),
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth(0.88f)
                        .heightIn(max = maxH * 0.92f)
                        .clip(RoundedCornerShape(PlayerPanelCornerRadius))
                        .background(PlayerPanelBackground)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {},
                        ),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(Res.string.compose_player_subtitles),
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            TextButton(onClick = onSubtitleSyncClick) {
                                Text(
                                    text = "Sync",
                                    color = PlayerPanelSelectedBackground,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        SubtitleTabBar(
                            activeTab = activeTab,
                            onTabSelected = onTabSelected,
                        )

                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp)
                                .padding(bottom = 14.dp),
                        ) {
                            when (activeTab) {
                                SubtitleTab.BuiltIn -> BuiltInSubtitleList(
                                    tracks = subtitleTracks,
                                    selectedIndex = selectedSubtitleIndex,
                                    onTrackSelected = onBuiltInTrackSelected,
                                )
                                SubtitleTab.Addons -> AddonSubtitleList(
                                    addons = addonSubtitles,
                                    selectedId = selectedAddonSubtitleId,
                                    isLoading = isLoadingAddonSubtitles,
                                    onSubtitleSelected = onAddonSubtitleSelected,
                                    onFetch = onFetchAddonSubtitles,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleTabBar(
    activeTab: SubtitleTab,
    onTabSelected: (SubtitleTab) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SubtitleTab.entries.forEach { tab ->
            val isSelected = tab == activeTab
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) PlayerPanelSelectedBackground else PlayerPanelRowBackground,
                animationSpec = tween(250),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(PlayerPanelRowCornerRadius))
                    .background(bgColor)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (tab) {
                        SubtitleTab.BuiltIn -> stringResource(Res.string.compose_player_built_in)
                        SubtitleTab.Addons -> stringResource(Res.string.addon_title)
                    },
                    color = if (isSelected) Color.White else PlayerPanelSecondaryText,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun BuiltInSubtitleList(
    tracks: List<SubtitleTrack>,
    selectedIndex: Int,
    onTrackSelected: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val isNoneSelected = selectedIndex == -1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PlayerPanelRowCornerRadius))
                .background(
                    if (isNoneSelected) PlayerPanelSelectedBackground
                    else PlayerPanelRowBackground
                )
                .clickable { onTrackSelected(-1) }
                .padding(vertical = 9.dp, horizontal = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.compose_player_none),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (isNoneSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
        }

        tracks.forEach { track ->
            val isSelected = track.index == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PlayerPanelRowCornerRadius))
                    .background(if (isSelected) PlayerPanelSelectedBackground else PlayerPanelRowBackground)
                    .clickable { onTrackSelected(track.index) }
                    .padding(vertical = 9.dp, horizontal = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = localizedTrackDisplayName(
                        label = track.label,
                        language = track.language,
                        index = track.index,
                        isForced = track.isForced,
                    ),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddonSubtitleList(
    addons: List<AddonSubtitle>,
    selectedId: String?,
    isLoading: Boolean,
    onSubtitleSelected: (AddonSubtitle) -> Unit,
    onFetch: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            NuvioLoadingIndicator(
                color = colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        return
    }

    if (addons.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PlayerPanelRowCornerRadius))
                .background(PlayerPanelRowBackground)
                .clickable(onClick = onFetch)
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.then(
                    Modifier.padding()
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    tint = PlayerPanelSecondaryText,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = stringResource(Res.string.compose_player_fetch_subtitles),
                    color = PlayerPanelSecondaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        addons.forEach { sub ->
            val isSelected = sub.id == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PlayerPanelRowCornerRadius))
                    .background(if (isSelected) PlayerPanelSelectedBackground else PlayerPanelRowBackground)
                    .clickable { onSubtitleSelected(sub) }
                    .padding(vertical = 7.dp, horizontal = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = languageLabelForCode(sub.language),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 5.dp),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(17.dp)
                            .padding(end = 2.dp),
                    )
                }
            }
        }
    }
}
