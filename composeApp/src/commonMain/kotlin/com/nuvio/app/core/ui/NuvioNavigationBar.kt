package com.nuvio.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Stable
class NuvioNavBarScrollState {
    var labelVisibility by mutableFloatStateOf(0f)
        private set

    fun expand() {
        labelVisibility = 0f
    }

    fun collapse() {
        labelVisibility = 0f
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            return Offset.Zero
        }
    }
}

@Composable
fun rememberNuvioNavBarScrollState(): NuvioNavBarScrollState = remember { NuvioNavBarScrollState() }

@Composable
fun NuvioNavigationBar(
    modifier: Modifier = Modifier,
    scrollState: NuvioNavBarScrollState? = null,
    hazeState: HazeState? = null,
    content: @Composable NuvioNavigationBarScope.() -> Unit,
) {
    val labelFraction = scrollState?.labelVisibility ?: 0f
    val bottomSafePadding = nuvioBottomNavigationBarInsets()
        .asPaddingValues()
        .calculateBottomPadding()
    val horizontalPadding = 28.dp + (58.dp - 28.dp) * (1f - labelFraction)
    val pillShape = RoundedCornerShape(999.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                bottom = bottomSafePadding + nuvioBottomNavigationExtraVerticalPadding + 8.dp,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .fillMaxWidth()
                .clip(pillShape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(state = hazeState) {
                            blurRadius = 24.dp
                        }
                    } else {
                        Modifier
                    },
                )
                .background(Color(0xFF1C1C1E).copy(alpha = if (hazeState != null) 0.55f else 0.82f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NuvioNavigationBarScopeImpl(this, labelFraction).content()
            }
        }
    }
}

interface NuvioNavigationBarScope {
    @Composable
    fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        label: String? = null,
    )

    @Composable
    fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: DrawableResource,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        label: String? = null,
    )

    @Composable
    fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        label: String? = null,
        content: @Composable () -> Unit,
    )
}

private class NuvioNavigationBarScopeImpl(
    private val rowScope: androidx.compose.foundation.layout.RowScope,
    private val labelFraction: Float,
) : NuvioNavigationBarScope {

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier,
        label: String?,
    ) {
        val iconColor by animateColorAsState(
            targetValue = if (selected) Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val selectedBackground by animateColorAsState(
            targetValue = if (selected) {
                Color.White.copy(alpha = 0.18f)
            } else {
                Color.Transparent
            },
        )
        with(rowScope) {
            Column(
                modifier = modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(selectedBackground)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    modifier = Modifier.size(26.dp),
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconColor,
                )
                NavItemLabel(
                    label = label,
                    labelFraction = labelFraction,
                    selected = selected,
                    color = iconColor,
                )
            }
        }
    }

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: DrawableResource,
        contentDescription: String?,
        modifier: Modifier,
        label: String?,
    ) {
        val iconColor by animateColorAsState(
            targetValue = if (selected) Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val selectedBackground by animateColorAsState(
            targetValue = if (selected) {
                Color.White.copy(alpha = 0.18f)
            } else {
                Color.Transparent
            },
        )
        with(rowScope) {
            Column(
                modifier = modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(selectedBackground)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    modifier = Modifier.size(26.dp),
                    painter = painterResource(icon),
                    contentDescription = contentDescription,
                    tint = iconColor,
                )
                NavItemLabel(
                    label = label,
                    labelFraction = labelFraction,
                    selected = selected,
                    color = iconColor,
                )
            }
        }
    }

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier,
        label: String?,
        content: @Composable () -> Unit,
    ) {
        val iconColor by animateColorAsState(
            targetValue = if (selected) Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val selectedBackground by animateColorAsState(
            targetValue = if (selected) {
                Color.White.copy(alpha = 0.18f)
            } else {
                Color.Transparent
            },
        )
        with(rowScope) {
            Column(
                modifier = modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(selectedBackground)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
                NavItemLabel(
                    label = label,
                    labelFraction = labelFraction,
                    selected = selected,
                    color = iconColor,
                )
            }
        }
    }
}

@Composable
private fun NavItemLabel(
    label: String?,
    labelFraction: Float,
    selected: Boolean,
    color: Color,
) {
    if (label.isNullOrBlank() || labelFraction <= 0.02f) return
    Spacer(modifier = Modifier.height(3.dp * labelFraction))
    Box(
        modifier = Modifier
            .height(13.dp * labelFraction)
            .alpha(labelFraction * if (selected) 1f else 0.74f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                lineHeight = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
