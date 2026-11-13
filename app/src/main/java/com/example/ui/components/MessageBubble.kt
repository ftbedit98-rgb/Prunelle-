package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.MessageType
import com.example.data.local.ReadStatus
import org.json.JSONObject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleItem(
    message: MessageEntity,
    isOutgoing: Boolean,
    isGroup: Boolean,
    isPlayingVoice: Boolean,
    onToggleVoicePlay: () -> Unit,
    onLongPressOrTapActions: () -> Unit,
    onQuickReact: (String) -> Unit
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isOutgoing) 20.dp else 6.dp,
        bottomEnd = if (isOutgoing) 6.dp else 20.dp
    )
    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    }
    val textColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val reactions = remember(message.reactionsJson) {
        runCatching {
            val obj = JSONObject(message.reactionsJson)
            obj.keys().asSequence().map { obj.getString(it) }.toList()
        }.getOrDefault(emptyList())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (message.type == MessageType.STICKER) MaterialTheme.colorScheme.surface.copy(alpha = 0f) else bubbleColor,
            shape = bubbleShape,
            tonalElevation = if (message.type == MessageType.STICKER) 0.dp else 2.dp,
            modifier = Modifier
                .widthIn(max = 296.dp)
                .combinedClickable(
                    onClick = onLongPressOrTapActions,
                    onLongClick = onLongPressOrTapActions
                )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (message.type == MessageType.STICKER) 4.dp else 12.dp,
                    vertical = if (message.type == MessageType.STICKER) 4.dp else 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isGroup && !isOutgoing) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (message.isForwarded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Shortcut,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = textColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Transféré",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                }

                if (!message.replyToPreview.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = message.replyToPreview,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }

                when (message.type) {
                    MessageType.IMAGE -> {
                        if (!message.mediaUri.isNullOrBlank()) {
                            AsyncImage(
                                model = message.mediaUri,
                                contentDescription = "Photo partagée",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 210.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        }
                        if (message.content.isNotBlank()) {
                            Text(text = message.content, style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    }

                    MessageType.VIDEO -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (!message.mediaUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = message.mediaUri,
                                        contentDescription = "Vidéo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Icon(
                                    Icons.Rounded.PlayCircleFilled,
                                    contentDescription = "Vidéo",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                        if (message.content.isNotBlank()) {
                            Text(text = message.content, style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    }

                    MessageType.VOICE -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = onToggleVoicePlay,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = if (isPlayingVoice) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlayingVoice) "Pause" else "Lire le vocal",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Column {
                                Text(
                                    text = if (isPlayingVoice) "Lecture en cours..." else "Message vocal",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = textColor
                                )
                                Text(
                                    text = "${(message.mediaDurationMs / 1000L).coerceAtLeast(1L)}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }

                    MessageType.FILE -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, tint = textColor)
                            Text(
                                text = message.mediaFileName ?: message.content.ifBlank { "Document" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    MessageType.STICKER -> {
                        if (!message.mediaUri.isNullOrBlank()) {
                            AsyncImage(
                                model = message.mediaUri,
                                contentDescription = message.content,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(116.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                        } else {
                            Text(text = message.content.ifBlank { "✨" }, fontSize = 54.sp)
                        }
                    }

                    MessageType.TEXT -> {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.isEdited) {
                        Text(
                            text = "Modifié •",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.65f)
                        )
                    }
                    Text(
                        text = formatTimeShort(message.sentAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                    if (isOutgoing) {
                        ReadReceiptTicks(status = message.readStatus)
                    }
                }
            }
        }

        if (reactions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .padding(top = 2.dp, start = 6.dp, end = 6.dp)
                    .clickable { reactions.firstOrNull()?.let(onQuickReact) }
            ) {
                Text(
                    text = reactions.joinToString(" "),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ForwardShareDialog(
    message: MessageEntity,
    conversations: List<ConversationEntity>,
    onForwardToPrunelle: (List<String>) -> Unit,
    onShareExternally: () -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partager / Transférer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        onShareExternally()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Partager vers une autre application")
                }

                Text(
                    text = "Transférer dans Prunelle :",
                    style = MaterialTheme.typography.titleSmall
                )

                if (conversations.isEmpty()) {
                    Text(
                        text = "Aucune autre conversation disponible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(conversations, key = { it.id }) { conv ->
                            val checked = selectedIds.contains(conv.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) selectedIds.remove(conv.id) else selectedIds.add(conv.id)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                PrunelleAvatar(
                                    name = conv.title,
                                    avatarUri = conv.avatarUri,
                                    size = 36.dp,
                                    isGroup = conv.isGroup
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(conv.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedIds.isNotEmpty()) {
                        onForwardToPrunelle(selectedIds.toList())
                    }
                    onDismiss()
                },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text("Transférer (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
