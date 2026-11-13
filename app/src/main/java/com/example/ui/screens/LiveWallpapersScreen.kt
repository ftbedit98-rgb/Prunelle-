package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.LiveWallpaperBackground
import com.example.ui.components.LiveWallpaperCatalog
import com.example.ui.components.queryDeviceMaxRefreshHz
import com.example.ui.viewmodel.HomeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveWallpapersScreen(
    state: HomeUiState,
    targetConversationId: String?,
    onBack: () -> Unit,
    onApplyGlobalWallpaper: (String, String?) -> Unit,
    onApplyConversationWallpaper: (String, String?, String?) -> Unit,
    onUpdatePerformance: (Int, Boolean) -> Unit
) {
    val context = LocalContext.current
    val deviceMaxHz = remember { queryDeviceMaxRefreshHz(context) }
    val targetConv = remember(state.conversations, targetConversationId) {
        state.conversations.firstOrNull { it.id == targetConversationId }
    }

    var selectedCategory by remember { mutableStateOf("Tous") }
    var search by remember { mutableStateOf("") }
    var previewPresetId by remember(targetConv?.id) {
        mutableStateOf(targetConv?.customWallpaperId ?: state.prefs.globalWallpaperId)
    }
    var previewCustomUri by remember(targetConv?.id) {
        mutableStateOf(
            if (targetConversationId != null) targetConv?.customWallpaperUri
            else state.prefs.globalWallpaperUri
        )
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            previewCustomUri = uri.toString()
        }
    }

    val filtered = remember(selectedCategory, search) {
        LiveWallpaperCatalog.presets.filter { preset ->
            (selectedCategory == "Tous" || preset.category == selectedCategory) &&
                (search.isBlank() || preset.title.contains(search, ignoreCase = true) || preset.category.contains(search, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = {
                    Text(
                        if (targetConversationId != null) "Fond de cette conversation"
                        else "Prunelle Live Wallpapers"
                    )
                },
                actions = {
                    if (previewCustomUri != null) {
                        IconButton(
                            onClick = {
                                previewCustomUri = null
                                previewPresetId = LiveWallpaperCatalog.presets.first().id
                                if (targetConversationId != null) {
                                    onApplyConversationWallpaper(targetConversationId, null, null)
                                } else {
                                    onApplyGlobalWallpaper(previewPresetId, null)
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer le fond personnalisé")
                        }
                    }
                    if (targetConversationId != null) {
                        TextButton(onClick = {
                            onApplyConversationWallpaper(targetConversationId, null, null)
                            onBack()
                        }) {
                            Text("Réinitialiser")
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Live Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                LiveWallpaperBackground(
                    wallpaperId = previewPresetId,
                    customUri = previewCustomUri,
                    targetFps = state.prefs.targetFps,
                    dataSaverEnabled = state.prefs.dataSaverEnabled,
                    dimAlpha = 0.15f,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (state.prefs.dataSaverEnabled) {
                            "Mode éco batterie (30 FPS)"
                        } else {
                            "Écran : ${deviceMaxHz} Hz max • Cible : ${state.prefs.targetFps.coerceAtMost(deviceMaxHz)} FPS"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Button(
                    onClick = {
                        if (targetConversationId != null) {
                            onApplyConversationWallpaper(
                                targetConversationId,
                                if (previewCustomUri == null) previewPresetId else null,
                                previewCustomUri
                            )
                        } else {
                            onApplyGlobalWallpaper(previewPresetId, previewCustomUri)
                        }
                        onBack()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .testTag("apply_wallpaper_button")
                ) {
                    Text("Appliquer")
                }
            }

            // Import from Gallery + Performance FPS Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        galleryPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("wallpaper_from_gallery_button")
                ) {
                    Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Depuis ma galerie")
                }
                listOf(30, 60, 120).forEach { fps ->
                    val supported = fps <= deviceMaxHz || fps == 60
                    FilterChip(
                        selected = state.prefs.targetFps == fps,
                        onClick = { onUpdatePerformance(fps, state.prefs.dataSaverEnabled) },
                        enabled = supported,
                        label = { Text("${fps}fps") }
                    )
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Rechercher un paysage...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LiveWallpaperCatalog.categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.id }) { preset ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                            .clickable {
                                previewCustomUri = null
                                previewPresetId = preset.id
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LiveWallpaperBackground(
                                wallpaperId = preset.id,
                                customUri = null,
                                targetFps = 30,
                                dataSaverEnabled = true,
                                dimAlpha = 0.2f,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(preset.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                    Text(preset.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
