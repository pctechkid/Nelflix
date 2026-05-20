package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.notifications.DefaultEpisodeReleaseTimezoneId
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationPlatform
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsUiState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_notifications_disabled_in_app
import nuvio.composeapp.generated.resources.settings_notifications_episode_release_alerts
import nuvio.composeapp.generated.resources.settings_notifications_episode_release_alerts_description
import nuvio.composeapp.generated.resources.settings_notifications_permission_disabled
import nuvio.composeapp.generated.resources.settings_notifications_scheduled_count
import nuvio.composeapp.generated.resources.settings_notifications_section_alerts
import nuvio.composeapp.generated.resources.settings_notifications_section_test
import nuvio.composeapp.generated.resources.settings_notifications_send_test
import nuvio.composeapp.generated.resources.settings_notifications_sending_test
import nuvio.composeapp.generated.resources.settings_notifications_test_for_title
import nuvio.composeapp.generated.resources.settings_notifications_test_requires_saved_show
import nuvio.composeapp.generated.resources.settings_notifications_test_title
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.notificationsSettingsContent(
    isTablet: Boolean,
    uiState: EpisodeReleaseNotificationsUiState,
) {
    item {
        NotificationTimezoneSettings(
            isTablet = isTablet,
            uiState = uiState,
        )
    }

    item {
        SettingsSection(
            title = stringResource(Res.string.settings_notifications_section_alerts),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_notifications_episode_release_alerts),
                    description = stringResource(Res.string.settings_notifications_episode_release_alerts_description),
                    checked = uiState.isEnabled,
                    enabled = !uiState.isLoading,
                    isTablet = isTablet,
                    onCheckedChange = EpisodeReleaseNotificationsRepository::setEnabled,
                )
            }
        }
    }

    item {
        SettingsSection(
            title = stringResource(Res.string.settings_notifications_section_test),
            isTablet = isTablet,
        ) {
            NotificationTestCard(
                uiState = uiState,
            )
        }
    }
}

@Composable
private fun NotificationTimezoneSettings(
    isTablet: Boolean,
    uiState: EpisodeReleaseNotificationsUiState,
) {
    var showTimezoneDialog by remember { mutableStateOf(false) }

    SettingsSection(
        title = "RELEASE TIMEZONE",
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = "Release timezone",
                description = uiState.timezoneId.ifBlank { DefaultEpisodeReleaseTimezoneId },
                isTablet = isTablet,
                onClick = { showTimezoneDialog = true },
            )
        }
    }

    if (showTimezoneDialog) {
        TimezoneSelectionDialog(
            selectedTimezoneId = uiState.timezoneId.ifBlank { DefaultEpisodeReleaseTimezoneId },
            onSave = EpisodeReleaseNotificationsRepository::setTimezoneId,
            onDismiss = { showTimezoneDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimezoneSelectionDialog(
    selectedTimezoneId: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val timezones = remember {
        EpisodeReleaseNotificationPlatform.availableTimezoneIds()
            .let { ids ->
                if (DefaultEpisodeReleaseTimezoneId in ids) ids else listOf(DefaultEpisodeReleaseTimezoneId) + ids
            }
            .distinct()
    }
    var selected by remember(selectedTimezoneId) {
        mutableStateOf(selectedTimezoneId.ifBlank { DefaultEpisodeReleaseTimezoneId })
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Release timezone",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(timezones, key = { it }) { timezone ->
                        val isSelected = timezone == selected
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = timezone },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = timezone,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(onClick = { onSave(selected) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationTestCard(
    uiState: EpisodeReleaseNotificationsUiState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(Res.string.settings_notifications_test_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = uiState.testTargetTitle?.let { title ->
                        stringResource(Res.string.settings_notifications_test_for_title, title)
                    } ?: stringResource(Res.string.settings_notifications_test_requires_saved_show),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (uiState.isEnabled) {
                        stringResource(Res.string.settings_notifications_scheduled_count, uiState.scheduledCount)
                    } else {
                        stringResource(Res.string.settings_notifications_disabled_in_app)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = EpisodeReleaseNotificationsRepository::sendTestNotification,
                enabled = !uiState.isSendingTest && !uiState.isLoading && uiState.testTargetTitle != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    if (uiState.isSendingTest) {
                        stringResource(Res.string.settings_notifications_sending_test)
                    } else {
                        stringResource(Res.string.settings_notifications_send_test)
                    },
                )
            }

            uiState.statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (!uiState.permissionGranted) {
                Text(
                    text = stringResource(Res.string.settings_notifications_permission_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
