package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.CallRecordEntity
import com.example.ui.components.PrunelleAvatar
import com.example.ui.components.formatTimeShort
import com.example.ui.viewmodel.HomeUiState
import kotlinx.coroutines.delay

data class ActiveCallSession(
    val peerName: String,
    val isVideo: Boolean,
    val isGroup: Boolean,
    val isIncomingRing: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
    state: HomeUiState,
    activeCall: ActiveCallSession?,
    onStartCall: (ActiveCallSession) -> Unit,
    onEndCall: (durationSeconds: Int) -> Unit,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    var micMuted by remember { mutableStateOf(false) }
    var cameraEnabled by remember { mutableStateOf(true) }
    var speakerOn by remember { mutableStateOf(false) }
    var callAnswered by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableIntStateOf(0) }

    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(activeCall) {
        if (activeCall != null) {
            callAnswered = !activeCall.isIncomingRing
            cameraEnabled = activeCall.isVideo
            micMuted = false
            elapsedSec = 0
            val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (activeCall.isVideo) needed.add(Manifest.permission.CAMERA)
            val missing = needed.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                callPermissionsLauncher.launch(missing.toTypedArray())
            }
        }
    }

    LaunchedEffect(activeCall, callAnswered) {
        if (activeCall != null && callAnswered) {
            while (true) {
                delay(1000L)
                elapsedSec++
            }
        }
    }

    if (activeCall != null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (activeCall.isVideo) "Appel vidéo Prunelle" else "Appel audio Prunelle",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    PrunelleAvatar(
                        name = activeCall.peerName,
                        avatarUri = null,
                        size = 104.dp,
                        isGroup = activeCall.isGroup
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(activeCall.peerName, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (!callAnswered) "Appel entrant..." else String.format("%02d:%02d", elapsedSec / 60, elapsedSec % 60),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (cameraEnabled && activeCall.isVideo)
                                "Caméra locale prête • Signalisation WebRTC distante requiert un serveur/Firebase"
                            else
                                "Audio prêt • Signalisation WebRTC distante requiert un serveur/Firebase",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                if (!callAnswered) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FloatingActionButton(
                            onClick = { onEndCall(0) },
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Rounded.CallEnd, contentDescription = "Refuser")
                        }
                        FloatingActionButton(
                            onClick = { callAnswered = true },
                            containerColor = Color(0xFF27AE60),
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = "Répondre")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = { micMuted = !micMuted },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                if (micMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                contentDescription = "Micro"
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { cameraEnabled = !cameraEnabled },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                if (cameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                                contentDescription = "Caméra"
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { speakerOn = !speakerOn },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                if (speakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown,
                                contentDescription = "Haut-parleur"
                            )
                        }
                        FloatingActionButton(
                            onClick = { onEndCall(elapsedSec) },
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                            modifier = Modifier.testTag("end_call_button")
                        ) {
                            Icon(Icons.Rounded.CallEnd, contentDescription = "Terminer l'appel")
                        }
                    }
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appels", fontWeight = FontWeight.Bold) },
                actions = {
                    if (state.calls.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Effacer l'historique")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.contacts.isNotEmpty()) {
                Text("Démarrer un appel avec un contact", style = MaterialTheme.typography.titleSmall)
                state.contacts.take(4).forEach { contact ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PrunelleAvatar(contact.displayName, contact.avatarUri, size = 44.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(contact.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = {
                                onStartCall(ActiveCallSession(contact.displayName, isVideo = false, isGroup = false))
                            }) {
                                Icon(Icons.Rounded.Call, contentDescription = "Appel audio")
                            }
                            IconButton(onClick = {
                                onStartCall(ActiveCallSession(contact.displayName, isVideo = true, isGroup = false))
                            }) {
                                Icon(Icons.Rounded.Videocam, contentDescription = "Appel vidéo")
                            }
                        }
                    }
                }
            }

            Text("Historique des appels", style = MaterialTheme.typography.titleSmall)

            if (state.calls.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Aucun appel enregistré", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ajoutez un contact ou ouvrez une conversation pour lancer un appel audio ou vidéo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.calls, key = { it.id }) { call ->
                        CallHistoryRow(call = call, onRecall = {
                            onStartCall(ActiveCallSession(call.peerName, call.isVideo, call.isGroup))
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun CallHistoryRow(call: CallRecordEntity, onRecall: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrunelleAvatar(call.peerName, null, size = 44.dp, isGroup = call.isGroup)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(call.peerName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${if (call.isVideo) "Vidéo" else "Audio"} • ${formatTimeShort(call.timestamp)} • ${call.durationSeconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRecall) {
                Icon(
                    if (call.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                    contentDescription = "Rappeler"
                )
            }
        }
    }
}
