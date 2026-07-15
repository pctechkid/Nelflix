package com.nuvio.app.features.watchtogether

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.profiles.avatarStorageUrl
import com.nuvio.app.features.profiles.normalizedAvatarUrl
import com.nuvio.app.features.profiles.parseHexColor

private val WatchTogetherRed = Color(0xFFE50914)
private val WatchTogetherDialogBackground = Color(0xEA101010)
private val WatchTogetherFieldBackground = Color(0xFF050505)
private val WatchTogetherPanel = Color.White.copy(alpha = 0.07f)
private val WatchTogetherPanelSoft = Color.White.copy(alpha = 0.10f)
private val WatchTogetherTextMuted = Color(0xFFBDBDBD)
private val WatchTogetherGreen = Color(0xFF39D66F)
private val WatchTogetherDivider = Color.White.copy(alpha = 0.10f)

@Composable
fun WatchTogetherDialog(
    session: WatchTogetherRoomState?,
    joinCode: String,
    isBusy: Boolean,
    errorMessage: String?,
    canUseWatchTogether: Boolean,
    joinOnly: Boolean = false,
    onJoinCodeChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onLeaveRoom: () -> Unit,
    onShareCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var joiningRoom by rememberSaveable(session == null, joinOnly) { mutableStateOf(joinOnly) }
    val clipboardManager = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxHeight < 430.dp
            val panelWidthFraction = if (maxWidth < 620.dp) 0.92f else 0.44f
            val panelMaxWidth = if (compact) 440.dp else 470.dp
            val panelPadding = if (compact) 16.dp else 18.dp
            val panelSpacing = if (compact) 10.dp else 12.dp

            Surface(
                modifier = Modifier
                    .fillMaxWidth(panelWidthFraction)
                    .widthIn(max = panelMaxWidth)
                    .heightIn(max = maxHeight * 0.9f),
                color = WatchTogetherDialogBackground,
                shape = RoundedCornerShape(if (compact) 18.dp else 22.dp),
                tonalElevation = 0.dp,
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(panelPadding),
                    verticalArrangement = Arrangement.spacedBy(panelSpacing),
                ) {
                    WatchTogetherHeader(
                        compact = compact,
                        onDismiss = onDismiss,
                    )

                    when {
                        !canUseWatchTogether -> {
                            Text(
                                text = "Sign in with an account to use Watch Together.",
                                color = WatchTogetherTextMuted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        session != null -> {
                            ActiveRoomContent(
                                session = session,
                                compact = compact,
                                onCopyCode = {
                                    clipboardManager.setText(AnnotatedString(session.roomCode))
                                    NuvioToastController.show("Room code copied")
                                },
                                onShareCode = { onShareCode(session.roomCode) },
                                onLeaveRoom = onLeaveRoom,
                                isBusy = isBusy,
                            )
                        }

                        joiningRoom -> {
                            JoinRoomContent(
                                joinCode = joinCode,
                                errorMessage = errorMessage,
                                isBusy = isBusy,
                                onJoinCodeChange = onJoinCodeChange,
                                onCancel = {
                                    if (joinOnly) {
                                        onDismiss()
                                    } else {
                                        joiningRoom = false
                                        onJoinCodeChange("")
                                    }
                                },
                                onJoinRoom = onJoinRoom,
                            )
                        }

                        else -> {
                            CreateOrJoinContent(
                                errorMessage = errorMessage,
                                isBusy = isBusy,
                                onCreateRoom = onCreateRoom,
                                onJoinClick = { joiningRoom = true },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchTogetherHeader(
    compact: Boolean,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Watch Together",
                color = Color.White,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Stay synced while watching",
                color = WatchTogetherTextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ActiveRoomContent(
    session: WatchTogetherRoomState,
    compact: Boolean,
    isBusy: Boolean,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onLeaveRoom: () -> Unit,
) {
    val members = session.members.ifEmpty {
        session.memberNames.map { name -> WatchTogetherMember(name = name) }
    }
    val memberCount = session.memberCount.coerceAtLeast(members.size.coerceAtLeast(1))
    val roleLabel = if (session.isHost) "HOST" else "JOINED"
    val roleDescription = if (session.isHost) {
        "Your playback controls the room"
    } else {
        "Following host playback"
    }
    val statusLabel = when (session.playbackState) {
        WatchTogetherPlaybackState.Playing -> "In sync"
        WatchTogetherPlaybackState.Paused -> "Paused together"
        WatchTogetherPlaybackState.Loading -> "Preparing stream"
        WatchTogetherPlaybackState.Ended -> "Playback ended"
    }

    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = roleLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(WatchTogetherRed)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = roleDescription,
                color = WatchTogetherTextMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        RoomCodeRow(code = session.roomCode, compact = compact)

        HorizontalDivider(color = WatchTogetherDivider)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = if (compact) 4.dp else 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(WatchTogetherGreen),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = statusLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "$memberCount watching",
                    color = WatchTogetherTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            VerticalDivider(
                modifier = Modifier.height(if (compact) 42.dp else 50.dp),
                color = WatchTogetherDivider,
            )

            Column(
                modifier = Modifier.weight(1.15f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "Members",
                    color = WatchTogetherTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                MemberChips(
                    members = members,
                    memberCount = memberCount,
                    compact = compact,
                )
            }
        }

        HorizontalDivider(color = WatchTogetherDivider)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = if (compact) 3.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayCircle,
                contentDescription = null,
                tint = WatchTogetherTextMuted,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Watching: ${session.contentMetadata.title.ifBlank { session.title }}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onShareCode,
                enabled = !isBusy,
                modifier = Modifier.weight(1.35f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WatchTogetherRed,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF5A1216),
                    disabledContentColor = WatchTogetherTextMuted,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Share Code")
            }

            Button(
                onClick = onCopyCode,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WatchTogetherPanelSoft,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF202020),
                    disabledContentColor = WatchTogetherTextMuted,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Copy")
            }

            Button(
                onClick = onLeaveRoom,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                border = BorderStroke(1.dp, WatchTogetherRed.copy(alpha = 0.5f)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WatchTogetherRed.copy(alpha = 0.12f),
                    contentColor = Color(0xFFFF6F78),
                    disabledContainerColor = Color(0xFF1A1A1A),
                    disabledContentColor = Color(0xFF777777),
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Leave")
            }
        }
    }
}

@Composable
private fun RoomCodeRow(
    code: String,
    compact: Boolean,
) {
    val displayCode = code.ifBlank { "------" }.take(6).padEnd(6, '-')
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 8.dp),
    ) {
        displayCode.forEach { character ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (compact) 42.dp else 48.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(WatchTogetherPanel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = character.toString(),
                    color = Color.White,
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MemberChips(
    members: List<WatchTogetherMember>,
    memberCount: Int,
    compact: Boolean,
) {
    val maxShown = if (compact) 3 else 4
    val displayedMembers = members.take(maxShown)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (displayedMembers.isEmpty()) {
            MemberChip(
                label = memberCount.toString(),
                name = "watching",
                compact = compact,
            )
        } else {
            displayedMembers.forEach { member ->
                MemberChip(
                    member = member,
                    compact = compact,
                )
            }
            if (memberCount > displayedMembers.size) {
                MemberChip(
                    label = "+${memberCount - displayedMembers.size}",
                    name = "more",
                    compact = compact,
                )
            }
        }
    }
}

@Composable
private fun MemberChip(
    member: WatchTogetherMember? = null,
    label: String = "",
    name: String = "",
    compact: Boolean,
) {
    val displayName = member?.displayName ?: name
    val initials = label.ifBlank { displayName.memberInitials() }
    val avatarUrl = member?.avatarImageUrl()
    val avatarColor = member?.avatarColorHex?.let(::parseHexColor) ?: WatchTogetherRed
    val chipName = displayName.substringBefore(' ').take(8).ifBlank { "Member" }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 26.dp else 28.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Text(
            text = chipName,
            color = WatchTogetherTextMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = if (compact) 42.dp else 50.dp),
        )
    }
}

private fun WatchTogetherMember.avatarImageUrl(): String? =
    normalizedAvatarUrl(avatarUrl)
        ?: avatarStoragePath
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::avatarStorageUrl)

private fun String.memberInitials(): String {
    val words = trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
        words.isNotEmpty() -> words[0].first().uppercase()
        else -> "?"
    }
}

@Composable
private fun JoinRoomContent(
    joinCode: String,
    errorMessage: String?,
    isBusy: Boolean,
    onJoinCodeChange: (String) -> Unit,
    onCancel: () -> Unit,
    onJoinRoom: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Enter the room code from your friend.",
            color = WatchTogetherTextMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = joinCode,
            onValueChange = { value ->
                onJoinCodeChange(value.uppercase().filter { it.isLetter() }.take(6))
            },
            label = { Text("Room code") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = WatchTogetherFieldBackground,
                unfocusedContainerColor = WatchTogetherFieldBackground,
                focusedBorderColor = WatchTogetherRed,
                unfocusedBorderColor = Color(0xFF595959),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = WatchTogetherTextMuted,
                cursorColor = WatchTogetherRed,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        WatchTogetherError(errorMessage)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onCancel,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WatchTogetherPanelSoft,
                    contentColor = Color.White,
                ),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onJoinRoom,
                enabled = !isBusy && joinCode.length == 6,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WatchTogetherRed,
                    contentColor = Color.White,
                ),
            ) {
                Text(if (isBusy) "Joining..." else "Join")
            }
        }
    }
}

@Composable
private fun CreateOrJoinContent(
    errorMessage: String?,
    isBusy: Boolean,
    onCreateRoom: () -> Unit,
    onJoinClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Create a room from this stream, or join a room with a code.",
            color = WatchTogetherTextMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        WatchTogetherError(errorMessage)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onCreateRoom,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WatchTogetherRed,
                    contentColor = Color.White,
                ),
            ) {
                Text(if (isBusy) "Creating..." else "Create Room")
            }
            Button(
                onClick = onJoinClick,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WatchTogetherPanelSoft,
                    contentColor = Color.White,
                ),
            ) {
                Text("Join")
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun WatchTogetherError(errorMessage: String?) {
    if (!errorMessage.isNullOrBlank()) {
        Text(
            text = errorMessage,
            color = Color(0xFFFF8A8A),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
