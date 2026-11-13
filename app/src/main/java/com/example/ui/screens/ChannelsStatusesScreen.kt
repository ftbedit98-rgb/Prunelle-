package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.ChannelEntity
import com.example.data.local.ChannelPostEntity
import com.example.data.local.MessageType
import com.example.data.local.StatusEntity
import com.example.ui.components.PrunelleAvatar
import com.example.ui.components.formatTimeShort
import com.example.ui.viewmodel.HomeUiState
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsStatusesScreen(
    state: HomeUiState,
    observeChannelPosts: (String) -> Flow<List<ChannelPostEntity>>,
    onCreateChannel: (String, String, String?) -> Unit,
    onToggleSubscribe: (ChannelEntity) -> Unit,
    onPublishPost: (String, String, String?, MessageType) -> Unit,
    onDeletePost: (String) -> Unit,
    onDeleteChannel: (String) -> Unit,
    onPublishStatus: (String, String?, Boolean, Long) -> Unit,
    onToggleStatusLike: (StatusEntity) -> Unit,
    onRecordStatusView: (StatusEntity) -> Unit,
    onDeleteStatus: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Statuts, 1 = Chaînes
    var showNewStatusDialog by remember { mutableStateOf(false) }
    var viewingStatus by remember { mutableStateOf<StatusEntity?>(null) }
    var showNewChannelDialog by remember { mutableStateOf(false) }
    var activeChannel by remember { mutableStateOf<ChannelEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Actus & Chaînes", fontWeight = FontWeight.Bold) },
                actions = {
                    if (selectedTab == 0) {
                        FilledTonalButton(
                            onClick = { showNewStatusDialog = true },
                            modifier = Modifier.padding(end = 12.dp).testTag("create_status_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Statut")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { showNewChannelDialog = true },
                            modifier = Modifier.padding(end = 12.dp).testTag("create_channel_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chaîne")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Statuts 24h (${state.statuses.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Chaînes (${state.channels.size})") }
                )
            }

            if (selectedTab == 0) {
                if (state.statuses.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.AutoStories,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(54.dp)
                            )
                            Text("Aucun statut actif (24h)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Publiez une photo, une vidéo ou un texte éphémère. Vos contacts pourront consulter et aimer vos statuts.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { showNewStatusDialog = true }) {
                                Text("Publier un statut")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.statuses, key = { it.id }) { status ->
                            StatusCard(
                                status = status,
                                isMine = status.authorId == state.prefs.userId,
                                onOpen = {
                                    onRecordStatusView(status)
                                    viewingStatus = status
                                },
                                onToggleLike = { onToggleStatusLike(status) },
                                onDelete = { onDeleteStatus(status.id) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.channels, key = { it.id }) { channel ->
                        ChannelCard(
                            channel = channel,
                            onOpen = { activeChannel = channel },
                            onToggleSubscribe = { onToggleSubscribe(channel) }
                        )
                    }
                }
            }
        }
    }

    if (showNewStatusDialog) {
        NewStatusDialog(
            onPublish = { text, uri, isVideo, color ->
                onPublishStatus(text, uri, isVideo, color)
                showNewStatusDialog = false
            },
            onDismiss = { showNewStatusDialog = false }
        )
    }

    viewingStatus?.let { st ->
        // Keep synced with latest status state
        val liveStatus = state.statuses.firstOrNull { it.id == st.id } ?: st
        StatusViewerDialog(
            status = liveStatus,
            onToggleLike = { onToggleStatusLike(liveStatus) },
            onDismiss = { viewingStatus = null }
        )
    }

    if (showNewChannelDialog) {
        CreateChannelDialog(
            onCreate = { name, desc, avatar ->
                onCreateChannel(name, desc, avatar)
                showNewChannelDialog = false
            },
            onDismiss = { showNewChannelDialog = false }
        )
    }

    activeChannel?.let { channel ->
        val posts by observeChannelPosts(channel.id).collectAsStateWithLifecycle(initialValue = emptyList())
        ChannelDetailDialog(
            channel = channel,
            posts = posts,
            onPublishPost = { content, uri, type -> onPublishPost(channel.id, content, uri, type) },
            onDeletePost = onDeletePost,
            onDeleteChannel = {
                onDeleteChannel(channel.id)
                activeChannel = null
            },
            onDismiss = { activeChannel = null }
        )
    }
}

@Composable
private fun StatusCard(
    status: StatusEntity,
    isMine: Boolean,
    onOpen: () -> Unit,
    onToggleLike: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(status.backgroundColorHex)),
                contentAlignment = Alignment.Center
            ) {
                if (!status.mediaUri.isNullOrBlank()) {
                    AsyncImage(
                        model = status.mediaUri,
                        contentDescription = status.authorName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = status.textContent.take(2).ifBlank { "P" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(status.authorName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = status.textContent.ifBlank { if (status.isVideo) "Vidéo • 24h" else "Photo • 24h" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = "Publié à ${formatTimeShort(status.createdAt)} • ${status.likesCount} J'aime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onToggleLike, modifier = Modifier.testTag("like_status_${status.id}")) {
                Icon(
                    imageVector = if (status.isLikedByMe) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Aimer ce statut",
                    tint = if (status.isLikedByMe) Color(0xFFE03E65) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isMine) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer")
                }
            }
        }
    }
}

@Composable
private fun StatusViewerDialog(
    status: StatusEntity,
    onToggleLike: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(status.authorName, style = MaterialTheme.typography.titleMedium)
                    Text(formatTimeShort(status.createdAt), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(status.backgroundColorHex)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!status.mediaUri.isNullOrBlank()) {
                        AsyncImage(
                            model = status.mediaUri,
                            contentDescription = "Statut",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (status.textContent.isNotBlank()) {
                        Text(
                            text = status.textContent,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vues : ${status.viewersCsv.split(",").filter { it.isNotBlank() }.size}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    FilledTonalButton(onClick = onToggleLike) {
                        Icon(
                            imageVector = if (status.isLikedByMe) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = if (status.isLikedByMe) Color(0xFFE03E65) else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${status.likesCount} J'aime")
                    }
                }
                if (status.likersCsv.isNotBlank()) {
                    Text(
                        text = "Aimé par : ${status.likersCsv}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

@Composable
private fun NewStatusDialog(
    onPublish: (String, String?, Boolean, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<String?>(null) }
    var isVideo by remember { mutableStateOf(false) }
    val palette = listOf(0xFF4F264DL, 0xFF1F4E5FL, 0xFF5A3327L, 0xFF2B3A4AL)
    var selectedColor by remember { mutableLongStateOf(palette.first()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            mediaUri = uri.toString()
            isVideo = uri.toString().contains("video")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau statut (24h)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Texte ou légende") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PermMedia, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (mediaUri == null) "Joindre photo ou vidéo" else "Média sélectionné ✓")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    palette.forEach { colorHex ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublish(text, mediaUri, isVideo, selectedColor) },
                enabled = text.isNotBlank() || mediaUri != null
            ) {
                Text("Publier")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun ChannelCard(
    channel: ChannelEntity,
    onOpen: () -> Unit,
    onToggleSubscribe: () -> Unit
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrunelleAvatar(
                name = channel.name,
                avatarUri = channel.avatarUri,
                size = 52.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(channel.name, style = MaterialTheme.typography.titleMedium)
                    if (channel.isOwnedByMe) {
                        AssistChip(onClick = {}, label = { Text("Ma chaîne") })
                    }
                }
                Text(
                    text = channel.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Text(
                    text = "${channel.subscriberCount} abonné(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!channel.isOwnedByMe) {
                FilledTonalButton(onClick = onToggleSubscribe) {
                    Text(if (channel.isSubscribed) "Abonné" else "Suivre")
                }
            }
        }
    }
}

@Composable
private fun CreateChannelDialog(
    onCreate: (String, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) avatarUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Créer une chaîne Prunelle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (avatarUri == null) "Choisir une photo de chaîne" else "Photo sélectionnée ✓")
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom de la chaîne *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description, avatarUri) },
                enabled = name.trim().length >= 2
            ) {
                Text("Créer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun ChannelDetailDialog(
    channel: ChannelEntity,
    posts: List<ChannelPostEntity>,
    onPublishPost: (String, String?, MessageType) -> Unit,
    onDeletePost: (String) -> Unit,
    onDeleteChannel: () -> Unit,
    onDismiss: () -> Unit
) {
    var postText by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) mediaUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(channel.name, style = MaterialTheme.typography.titleLarge)
                    Text("${channel.subscriberCount} abonné(s)", style = MaterialTheme.typography.labelSmall)
                }
                if (channel.isOwnedByMe) {
                    IconButton(onClick = onDeleteChannel) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer la chaîne", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(posts, key = { it.id }) { post ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!post.mediaUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = post.mediaUri,
                                        contentDescription = "Média de chaîne",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(10.dp))
                                    )
                                }
                                Text(post.content, style = MaterialTheme.typography.bodyMedium)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${formatTimeShort(post.createdAt)} • ${post.viewsCount} vue(s)",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (channel.isOwnedByMe) {
                                        TextButton(onClick = { onDeletePost(post.id) }) {
                                            Text("Supprimer", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (channel.isOwnedByMe) {
                    OutlinedTextField(
                        value = postText,
                        onValueChange = { postText = it },
                        placeholder = { Text("Publier une annonce...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }) {
                            Text(if (mediaUri == null) "Média" else "Média ✓")
                        }
                        Button(
                            onClick = {
                                onPublishPost(postText, mediaUri, if (mediaUri != null) MessageType.IMAGE else MessageType.TEXT)
                                postText = ""
                                mediaUri = null
                            },
                            enabled = postText.isNotBlank() || mediaUri != null
                        ) {
                            Text("Publier")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}
