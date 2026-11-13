package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.StickerEntity

@Composable
fun StickerPickerSheet(
    stickers: List<StickerEntity>,
    onSelectSticker: (StickerEntity) -> Unit,
    onToggleFavorite: (StickerEntity) -> Unit,
    onCreateCustomSticker: (
        packName: String,
        label: String,
        imageUri: String?,
        overlayText: String,
        emojiFallback: String,
        isAnimated: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreator by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    val filtered = remember(stickers, search) {
        if (search.isBlank()) stickers
        else stickers.filter { it.label.contains(search, ignoreCase = true) || it.overlayText.contains(search, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stickers Prunelle")
                FilledTonalButton(onClick = { showCreator = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Créer")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Rechercher un sticker...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { sticker ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable {
                                    onSelectSticker(sticker)
                                    onDismiss()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (!sticker.imageUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = sticker.imageUri,
                                        contentDescription = sticker.label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(text = sticker.emojiFallback, fontSize = 34.sp)
                                }
                                if (sticker.overlayText.isNotBlank()) {
                                    Text(
                                        text = sticker.overlayText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleFavorite(sticker) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (sticker.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "Favori",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )

    if (showCreator) {
        StickerCreatorDialog(
            onCreate = { pack, label, uri, overlay, emoji, animated ->
                onCreateCustomSticker(pack, label, uri, overlay, emoji, animated)
                showCreator = false
            },
            onDismiss = { showCreator = false }
        )
    }
}

@Composable
fun StickerCreatorDialog(
    onCreate: (
        packName: String,
        label: String,
        imageUri: String?,
        overlayText: String,
        emojiFallback: String,
        isAnimated: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var packName by remember { mutableStateOf("Mes Stickers") }
    var label by remember { mutableStateOf("") }
    var overlayText by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("✨") }
    var mediaUri by remember { mutableStateOf<String?>(null) }
    var isAnimated by remember { mutableStateOf(false) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            mediaUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Créer un sticker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (mediaUri != null) {
                        AsyncImage(
                            model = mediaUri,
                            contentDescription = "Aperçu sticker",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                            Text("Choisir une photo ou vidéo", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (overlayText.isNotBlank()) {
                        Text(
                            text = overlayText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = packName,
                    onValueChange = { packName = it },
                    label = { Text("Nom du pack") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nom du sticker") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = overlayText,
                    onValueChange = { overlayText = it },
                    label = { Text("Texte sur le sticker") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("✨", "💜", "😂", "🔥", "🎉").forEach { emoji ->
                        FilterChip(
                            selected = selectedEmoji == emoji,
                            onClick = { selectedEmoji = emoji },
                            label = { Text(emoji) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Marquer comme animé", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = isAnimated, onCheckedChange = { isAnimated = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(packName, label.ifBlank { "Sticker" }, mediaUri, overlayText, selectedEmoji, isAnimated)
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
