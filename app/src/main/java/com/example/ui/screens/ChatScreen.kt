package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.media.VoicePlayerManager
import com.example.core.media.VoiceRecorderManager
import com.example.data.local.MessageEntity
import com.example.data.local.MessageType
import com.example.data.local.ReadStatus
import com.example.ui.components.*
import com.example.ui.viewmodel.ChatUiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onSendOrSaveEdit: (String) -> Unit,
    onSendMedia: (MessageType, String?, String, String?, Long) -> Unit,
    onSetReply: (MessageEntity?) -> Unit,
    onSetEditing: (MessageEntity?) -> Unit,
    onToggleReaction: (MessageEntity, String) -> Unit,
    onDeleteMessage: (MessageEntity) -> Unit,
    onForwardMessage: (MessageEntity, List<String>) -> Unit,
    onShareExternally: (MessageEntity) -> Unit,
    onLoadMoreMessages: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onOpenGroupInfo: () -> Unit,
    onStartAudioCall: (String, Boolean) -> Unit,
    onStartVideoCall: (String, Boolean) -> Unit,
    onUpdateMessageStatus: (MessageEntity, ReadStatus) -> Unit,
    onCreateCustomSticker: (String, String, String?, String, String, Boolean) -> Unit,
    onToggleStickerFavorite: (com.example.data.local.StickerEntity) -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showEmojiBar by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var selectedMessageActions by remember { mutableStateOf<MessageEntity?>(null) }
    var forwardTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var infoTarget by remember { mutableStateOf<MessageEntity?>(null) }

    // Voice recorder & player
    val recorder = remember { VoiceRecorderManager(context) }
    val player = remember { VoicePlayerManager() }
    var isRecording by remember { mutableStateOf(false) }
    var recordingElapsedSec by remember { mutableIntStateOf(0) }
    var playingVoiceId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            recorder.stopRecording(discard = true)
            player.stop()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingElapsedSec = 0
            while (isRecording) {
                delay(1000L)
                recordingElapsedSec++
            }
        }
    }

    LaunchedEffect(state.editingTarget) {
        state.editingTarget?.let { inputText = it.content }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val type = if (mime.startsWith("video/")) MessageType.VIDEO else MessageType.IMAGE
            onSendMedia(type, uri.toString(), "", null, 0L)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
            } ?: "Document"
            onSendMedia(MessageType.FILE, uri.toString(), fileName, fileName, 0L)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (recorder.startRecording() != null) {
                isRecording = true
            }
        }
    }

    val conv = state.conversation
    val activeWallpaperId = conv?.customWallpaperId ?: state.prefs.globalWallpaperId
    val activeWallpaperUri = conv?.customWallpaperUri ?: state.prefs.globalWallpaperUri

    Box(modifier = Modifier.fillMaxSize()) {
        // Per-conversation 4K/120fps-adaptive animated wallpaper
        LiveWallpaperBackground(
            wallpaperId = activeWallpaperId,
            customUri = activeWallpaperUri,
            targetFps = state.prefs.targetFps,
            dataSaverEnabled = state.prefs.dataSaverEnabled,
            dimAlpha = 0.25f,
            modifier = Modifier.fillMaxSize()
        )

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (conv?.isGroup == true) onOpenGroupInfo()
                            }
                        ) {
                            PrunelleAvatar(
                                name = conv?.title ?: "Discussion",
                                avatarUri = conv?.avatarUri,
                                size = 38.dp,
                                isOnline = conv?.isOnline ?: false,
                                isGroup = conv?.isGroup ?: false
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = conv?.title ?: "Conversation",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when {
                                        !state.networkStatus.isOnline -> "Hors ligne — file d'attente active"
                                        conv?.isGroup == true -> "Groupe • Toucher pour infos"
                                        conv?.isOnline == true -> "En ligne"
                                        else -> "Prunelle"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            onStartAudioCall(conv?.title ?: "Contact", conv?.isGroup ?: false)
                        }) {
                            Icon(Icons.Rounded.Call, contentDescription = "Appel audio")
                        }
                        IconButton(onClick = {
                            onStartVideoCall(conv?.title ?: "Contact", conv?.isGroup ?: false)
                        }) {
                            Icon(Icons.Rounded.Videocam, contentDescription = "Appel vidéo")
                        }
                        IconButton(
                            onClick = onOpenWallpaperPicker,
                            modifier = Modifier.testTag("chat_wallpaper_button")
                        ) {
                            Icon(Icons.Rounded.Wallpaper, contentDescription = "Fond d'écran animé")
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 4.dp,
                    modifier = Modifier.navigationBarsPadding().imePadding()
                ) {
                    Column {
                        state.replyTarget?.let { reply ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Réponse à ${reply.senderName} : ${reply.content.take(40)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onSetReply(null) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Annuler")
                                }
                            }
                        }
                        state.editingTarget?.let {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Modification du message", style = MaterialTheme.typography.bodySmall)
                                IconButton(
                                    onClick = {
                                        onSetEditing(null)
                                        inputText = ""
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Annuler")
                                }
                            }
                        }

                        if (showEmojiBar) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf("😊", "❤️", "😂", "🔥", "👏", "🙌", "✨", "💜").forEach { emoji ->
                                    Text(
                                        text = emoji,
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier
                                            .clickable { inputText += emoji }
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }

                        if (isRecording) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = {
                                    recorder.stopRecording(discard = true)
                                    isRecording = false
                                }) {
                                    Text("Annuler", color = MaterialTheme.colorScheme.error)
                                }
                                Text(
                                    text = "🔴 Enregistrement ${recordingElapsedSec}s",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Button(onClick = {
                                    val result = recorder.stopRecording(discard = false)
                                    isRecording = false
                                    if (result != null) {
                                        onSendMedia(MessageType.VOICE, result.first, "Message vocal", null, result.second)
                                    }
                                }) {
                                    Text("Envoyer")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    IconButton(
                                        onClick = { showAttachMenu = true },
                                        modifier = Modifier.testTag("chat_attach_button")
                                    ) {
                                        Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Pièce jointe")
                                    }
                                    DropdownMenu(
                                        expanded = showAttachMenu,
                                        onDismissRequest = { showAttachMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Photo ou Vidéo") },
                                            leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                                            onClick = {
                                                showAttachMenu = false
                                                mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Document / Fichier") },
                                            leadingIcon = { Icon(Icons.Rounded.AttachFile, contentDescription = null) },
                                            onClick = {
                                                showAttachMenu = false
                                                filePicker.launch(arrayOf("*/*"))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Stickers Prunelle") },
                                            leadingIcon = { Icon(Icons.Rounded.EmojiEmotions, contentDescription = null) },
                                            onClick = {
                                                showAttachMenu = false
                                                showStickers = true
                                            }
                                        )
                                    }
                                }

                                IconButton(onClick = { showEmojiBar = !showEmojiBar }) {
                                    Icon(Icons.Rounded.SentimentSatisfiedAlt, contentDescription = "Emojis")
                                }

                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    placeholder = { Text("Message Prunelle...") },
                                    maxLines = 4,
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("chat_message_input")
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                if (inputText.isNotBlank()) {
                                    FilledIconButton(
                                        onClick = {
                                            onSendOrSaveEdit(inputText)
                                            inputText = ""
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("chat_send_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Envoyer")
                                    }
                                } else {
                                    FilledTonalIconButton(
                                        onClick = {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                if (recorder.startRecording() != null) isRecording = true
                                            } else {
                                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("chat_voice_button")
                                    ) {
                                        Icon(Icons.Rounded.Mic, contentDescription = "Message vocal")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            if (state.isLoading) {
                ChatMessagesSkeleton(modifier = Modifier.padding(padding))
            } else if (state.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "Écrivez votre premier message ci-dessous ✨",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    if (state.hasMoreMessages) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                FilledTonalButton(onClick = onLoadMoreMessages) {
                                    Text("Charger les 20 messages précédents")
                                }
                            }
                        }
                    }

                    items(state.messages, key = { it.id }) { msg ->
                        val isOutgoing = msg.senderId == state.prefs.userId
                        MessageBubbleItem(
                            message = msg,
                            isOutgoing = isOutgoing,
                            isGroup = conv?.isGroup == true,
                            isPlayingVoice = playingVoiceId == msg.id,
                            onToggleVoicePlay = {
                                val path = msg.mediaUri ?: return@MessageBubbleItem
                                val started = player.playOrToggle(context, msg.id, path) {
                                    playingVoiceId = null
                                }
                                playingVoiceId = if (started) msg.id else null
                            },
                            onLongPressOrTapActions = { selectedMessageActions = msg },
                            onQuickReact = { emoji -> onToggleReaction(msg, emoji) }
                        )
                    }
                }
            }
        }
    }

    if (showStickers) {
        StickerPickerSheet(
            stickers = state.stickers,
            onSelectSticker = { sticker ->
                onSendMedia(MessageType.STICKER, sticker.imageUri, sticker.emojiFallback, sticker.label, 0L)
            },
            onToggleFavorite = onToggleStickerFavorite,
            onCreateCustomSticker = onCreateCustomSticker,
            onDismiss = { showStickers = false }
        )
    }

    selectedMessageActions?.let { msg ->
        AlertDialog(
            onDismissRequest = { selectedMessageActions = null },
            title = { Text("Actions sur le message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("❤️", "😂", "😮", "👍", "🔥").forEach { emoji ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    onToggleReaction(msg, emoji)
                                    selectedMessageActions = null
                                }
                            ) {
                                Text(emoji, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    TextButton(onClick = {
                        onSetReply(msg)
                        selectedMessageActions = null
                    }) { Text("Répondre") }
                    TextButton(onClick = {
                        forwardTarget = msg
                        selectedMessageActions = null
                    }) { Text("Partager / Transférer") }
                    if (msg.type == MessageType.TEXT) {
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Prunelle", msg.content))
                            selectedMessageActions = null
                        }) { Text("Copier le texte") }
                        if (msg.senderId == state.prefs.userId) {
                            TextButton(onClick = {
                                onSetEditing(msg)
                                selectedMessageActions = null
                            }) { Text("Modifier") }
                        }
                    }
                    TextButton(onClick = {
                        infoTarget = msg
                        selectedMessageActions = null
                    }) { Text("Infos de livraison & lecture") }
                    TextButton(onClick = {
                        onDeleteMessage(msg)
                        selectedMessageActions = null
                    }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageActions = null }) { Text("Fermer") }
            }
        )
    }

    forwardTarget?.let { msg ->
        ForwardShareDialog(
            message = msg,
            conversations = state.allConversations.filter { it.id != conv?.id },
            onForwardToPrunelle = { ids -> onForwardMessage(msg, ids) },
            onShareExternally = { onShareExternally(msg) },
            onDismiss = { forwardTarget = null }
        )
    }

    infoTarget?.let { msg ->
        AlertDialog(
            onDismissRequest = { infoTarget = null },
            title = { Text("Détails du message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("État : ${msg.readStatus.name}")
                    Text("Envoyé à : ${formatTimeShort(msg.sentAt)}")
                    Text("Livré à : ${msg.deliveredAt?.let { formatTimeShort(it) } ?: "Non confirmé"}")
                    Text("Lu à : ${msg.readAt?.let { formatTimeShort(it) } ?: "Non lu"}")
                    if (msg.senderId == state.prefs.userId) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = msg.readStatus == ReadStatus.DELIVERED,
                                onClick = {
                                    onUpdateMessageStatus(msg, ReadStatus.DELIVERED)
                                    infoTarget = null
                                },
                                label = { Text("Marquer livré") }
                            )
                            FilterChip(
                                selected = msg.readStatus == ReadStatus.READ,
                                onClick = {
                                    onUpdateMessageStatus(msg, ReadStatus.READ)
                                    infoTarget = null
                                },
                                label = { Text("Marquer lu") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoTarget = null }) { Text("Fermer") }
            }
        )
    }
}
