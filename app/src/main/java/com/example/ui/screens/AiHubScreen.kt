package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.media.MediaStorageHelper
import com.example.data.local.AiCreationEntity
import com.example.data.local.AiMessageEntity
import com.example.data.repository.AiChatMode
import com.example.ui.components.FirebaseRequiredBanner
import com.example.ui.components.formatTimeShort
import com.example.ui.viewmodel.AiHubUiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHubScreen(
    state: AiHubUiState,
    onSendChatPrompt: (String, AiChatMode) -> Unit,
    onClearChat: (AiChatMode) -> Unit,
    onAddMemory: (String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClearMemories: () -> Unit,
    onToggleMemoryEnabled: (Boolean) -> Unit,
    onGenerateImage: (Context, String, String, String, String?) -> Unit,
    onGenerateVideo: (String, Int, String, String?) -> Unit,
    onToggleFavoriteCreation: (AiCreationEntity) -> Unit,
    onDeleteCreation: (String) -> Unit,
    onAnalyzeTikTok: (String?, String?) -> Unit,
    onSelectTikTokAnalysis: (String) -> Unit,
    onAskTikTokQuestion: (com.example.data.local.TikTokAnalysisEntity, String) -> Unit,
    onDeleteTikTokAnalysis: (String) -> Unit,
    onDismissError: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Assistant, 1=Ami IA, 2=AI Studio, 3=TikTok AI

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prunelle IA", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Assistant IA") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Ami IA") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("AI Studio") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("TikTok AI") })
            }

            if (selectedTab != 3) {
                state.aiError?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("ai_hub_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismissError) {
                                Icon(Icons.Rounded.Close, contentDescription = "Fermer l'alerte", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> AiChatPane(
                    mode = AiChatMode.ASSISTANT,
                    messages = state.assistantMessages,
                    isGenerating = state.isGeneratingChat,
                    isCloudEnabled = state.isCloudAiEnabled,
                    onSend = { onSendChatPrompt(it, AiChatMode.ASSISTANT) },
                    onClear = { onClearChat(AiChatMode.ASSISTANT) }
                )

                1 -> AiCompanionPane(
                    state = state,
                    onSend = { onSendChatPrompt(it, AiChatMode.COMPANION) },
                    onClear = { onClearChat(AiChatMode.COMPANION) },
                    onAddMemory = onAddMemory,
                    onDeleteMemory = onDeleteMemory,
                    onClearMemories = onClearMemories,
                    onToggleMemoryEnabled = onToggleMemoryEnabled
                )

                2 -> AiStudioPane(
                    state = state,
                    onGenerateImage = onGenerateImage,
                    onGenerateVideo = onGenerateVideo,
                    onToggleFavorite = onToggleFavoriteCreation,
                    onDeleteCreation = onDeleteCreation
                )

                3 -> TikTokAiScreen(
                    state = state,
                    onAnalyze = onAnalyzeTikTok,
                    onSelectAnalysis = onSelectTikTokAnalysis,
                    onAskQuestion = onAskTikTokQuestion,
                    onDeleteAnalysis = onDeleteTikTokAnalysis,
                    onDismissError = onDismissError
                )
            }
        }
    }
}

@Composable
private fun AiChatPane(
    mode: AiChatMode,
    messages: List<AiMessageEntity>,
    isGenerating: Boolean,
    isCloudEnabled: Boolean,
    onSend: (String) -> Unit,
    onClear: () -> Unit
) {
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isCloudEnabled) {
            FirebaseRequiredBanner(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (mode == AiChatMode.COMPANION) "Compagnon IA (transparent & bienveillant)" else "Assistant intelligent Prunelle",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (messages.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Effacer") }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isFromUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (msg.isFromUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Text(
                            text = msg.content,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            if (isGenerating) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Poser une question à Prunelle IA...") },
                modifier = Modifier.weight(1f).testTag("ai_chat_input"),
                shape = RoundedCornerShape(24.dp),
                maxLines = 3
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    onSend(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !isGenerating,
                modifier = Modifier.size(48.dp).testTag("ai_chat_send")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Envoyer à l'IA")
            }
        }
    }
}

@Composable
private fun AiCompanionPane(
    state: AiHubUiState,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    onAddMemory: (String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClearMemories: () -> Unit,
    onToggleMemoryEnabled: (Boolean) -> Unit
) {
    var showMemoryDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mémoire IA (${state.memories.size}) • ${if (state.aiMemoryEnabled) "Active" else "Désactivée"}",
                    style = MaterialTheme.typography.labelLarge
                )
                TextButton(onClick = { showMemoryDialog = true }) {
                    Text("Gérer la mémoire")
                }
            }
        }
        AiChatPane(
            mode = AiChatMode.COMPANION,
            messages = state.companionMessages,
            isGenerating = state.isGeneratingChat,
            isCloudEnabled = state.isCloudAiEnabled,
            onSend = onSend,
            onClear = onClear
        )
    }

    if (showMemoryDialog) {
        var newKey by remember { mutableStateOf("") }
        var newVal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMemoryDialog = false },
            title = { Text("Mémoire de Ami IA") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Autoriser la mémorisation")
                        Switch(checked = state.aiMemoryEnabled, onCheckedChange = onToggleMemoryEnabled)
                    }
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("Sujet (ex: Passion)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newVal,
                        onValueChange = { newVal = it },
                        label = { Text("Préférence à retenir") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onAddMemory(newKey, newVal)
                            newKey = ""
                            newVal = ""
                        },
                        enabled = newKey.isNotBlank() && newVal.isNotBlank()
                    ) {
                        Text("Ajouter un souvenir")
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                        items(state.memories, key = { it.id }) { mem ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${mem.keyLabel} : ${mem.valueText}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                IconButton(onClick = { onDeleteMemory(mem.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer souvenir")
                                }
                            }
                        }
                    }
                    if (state.memories.isNotEmpty()) {
                        TextButton(onClick = onClearMemories) {
                            Text("Tout effacer", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMemoryDialog = false }) { Text("Fermer") }
            }
        )
    }
}

@Composable
private fun AiStudioPane(
    state: AiHubUiState,
    onGenerateImage: (Context, String, String, String, String?) -> Unit,
    onGenerateVideo: (String, Int, String, String?) -> Unit,
    onToggleFavorite: (AiCreationEntity) -> Unit,
    onDeleteCreation: (String) -> Unit
) {
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(0) } // 0 = Photo, 1 = Vidéo
    var prompt by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("Photoréaliste") }
    var aspectRatio by remember { mutableStateOf("16:9") }
    var durationSec by remember { mutableIntStateOf(15) }
    var referenceUri by remember { mutableStateOf<String?>(null) }

    val refPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) referenceUri = uri.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!state.isCloudAiEnabled) {
            FirebaseRequiredBanner(
                notice = "Prunelle AI Studio (Images & Vidéos jusqu'à 60s, 100% gratuit sans abonnement) nécessite l'activation de Firebase AI Logic / modèle vidéo cloud pour produire de vrais rendus."
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Image IA") })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Vidéo IA (5s–60s)") })
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text(if (mode == 0) "Décrivez l'image photoréaliste" else "Décrivez la scène vidéo et les mouvements") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("ai_studio_prompt")
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Photoréaliste", "Cinématographique", "Studio").forEach { s ->
                FilterChip(selected = style == s, onClick = { style = s }, label = { Text(s) })
            }
        }

        if (mode == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1:1", "9:16", "16:9", "4:3").forEach { ratio ->
                    FilterChip(selected = aspectRatio == ratio, onClick = { aspectRatio = ratio }, label = { Text(ratio) })
                }
            }
        } else {
            Text("Durée cible gratuite : ${durationSec}s", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 10, 15, 30, 45, 60).forEach { sec ->
                    FilterChip(selected = durationSec == sec, onClick = { durationSec = sec }, label = { Text("${sec}s") })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    refPicker.launch(
                        PickVisualMediaRequest(
                            if (mode == 0) ActivityResultContracts.PickVisualMedia.ImageOnly
                            else ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (referenceUri == null) "Média de référence" else "Référence prête ✓")
            }
            Button(
                onClick = {
                    if (mode == 0) onGenerateImage(context, prompt, style, aspectRatio, referenceUri)
                    else onGenerateVideo(prompt, durationSec, style, referenceUri)
                },
                enabled = prompt.isNotBlank() && !state.isGeneratingStudio,
                modifier = Modifier.weight(1f).testTag("ai_studio_generate")
            ) {
                Text(if (mode == 0) "Générer image" else "Générer vidéo")
            }
        }

        if (state.isGeneratingStudio) {
            LinearProgressIndicator(progress = { state.studioProgress.coerceAtLeast(0.1f) }, modifier = Modifier.fillMaxWidth())
        }

        Text("Historique des créations (${state.creations.size})", style = MaterialTheme.typography.titleMedium)

        state.creations.forEach { creation ->
            val imgPath = creation.generatedImagePath
            val hasImage = !imgPath.isNullOrBlank() && File(imgPath).exists()
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${creation.creationType} • ${creation.style} (${creation.aspectRatioOrDuration})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row {
                            if (hasImage) {
                                IconButton(
                                    onClick = {
                                        MediaStorageHelper.shareImageFile(
                                            context = context,
                                            filePath = imgPath!!,
                                            chooserTitle = "Partager ma création Prunelle IA",
                                            textCaption = creation.prompt
                                        )
                                    }
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = "Partager l'image")
                                }
                            }
                            IconButton(onClick = { onToggleFavorite(creation) }) {
                                Icon(
                                    if (creation.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = "Favori"
                                )
                            }
                            IconButton(onClick = { onDeleteCreation(creation.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer")
                            }
                        }
                    }
                    if (hasImage) {
                        AsyncImage(
                            model = File(imgPath!!),
                            contentDescription = creation.prompt,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }
                    Text(creation.outputPreviewText, style = MaterialTheme.typography.bodySmall)
                    Text(formatTimeShort(creation.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
