package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.birthday.BirthdayHelper
import com.example.data.local.PrivacyAudience
import com.example.ui.components.PrunelleAvatar
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.HomeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: HomeUiState,
    onBack: () -> Unit,
    onUpdateProfile: (String, String, String, String, String?, Int?, Int?, Int?, PrivacyAudience) -> Unit,
    onSetTheme: (ThemePreference) -> Unit,
    onSetLanguage: (String) -> Unit,
    onUpdatePrivacy: (Boolean, PrivacyAudience, PrivacyAudience, Boolean, Boolean, PrivacyAudience) -> Unit,
    onUpdateNotifications: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onSetPerformance: (Int, Boolean) -> Unit,
    onSetBackendUrl: (String) -> Unit,
    onUnblockUser: (String) -> Unit,
    onOpenLiveWallpapers: () -> Unit,
    onLogout: () -> Unit
) {
    val prefs = state.prefs
    var showProfileDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showNotifDialog by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = { Text("Paramètres", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                onClick = { showProfileDialog = true },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrunelleAvatar(prefs.displayName.ifBlank { "Moi" }, prefs.avatarUri, size = 60.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(prefs.displayName.ifBlank { "Profil Prunelle" }, style = MaterialTheme.typography.titleLarge)
                        Text("@${prefs.username.ifBlank { "utilisateur" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = buildString {
                                append(prefs.statusText)
                                if (prefs.birthDay != null && prefs.birthMonth != null) {
                                    append(" • 🎂 ${BirthdayHelper.formatPublicDayMonth(prefs.birthDay, prefs.birthMonth)}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (prefs.email.isNotBlank()) {
                            Text(prefs.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Rounded.Edit, contentDescription = "Modifier le profil")
                }
            }

            SettingsTile(Icons.Rounded.Lock, "Confidentialité & Accusés de lecture", "Visibilité profil, anniversaire, présence") {
                showPrivacyDialog = true
            }
            SettingsTile(Icons.Rounded.Security, "Sécurité & Compte Google", prefs.email.ifBlank { "Numéro masqué par défaut • Protection locale" }) {
                showPrivacyDialog = true
            }
            SettingsTile(Icons.Rounded.Notifications, "Notifications", "Messages, groupes, appels, anniversaires, chaînes") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                showNotifDialog = true
            }
            SettingsTile(Icons.Rounded.Wallpaper, "Mon fond d'écran animé", "Bibliothèque 4K/120fps & Import galerie") {
                onOpenLiveWallpapers()
            }

            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Apparence", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = prefs.themePreference == ThemePreference.SYSTEM,
                            onClick = { onSetTheme(ThemePreference.SYSTEM) },
                            label = { Text("Système") }
                        )
                        FilterChip(
                            selected = prefs.themePreference == ThemePreference.LIGHT,
                            onClick = { onSetTheme(ThemePreference.LIGHT) },
                            label = { Text("Clair") }
                        )
                        FilterChip(
                            selected = prefs.themePreference == ThemePreference.DARK,
                            onClick = { onSetTheme(ThemePreference.DARK) },
                            label = { Text("Sombre") }
                        )
                    }
                    Text("Langue", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = prefs.languageCode == "fr", onClick = { onSetLanguage("fr") }, label = { Text("Français") })
                        FilterChip(selected = prefs.languageCode == "en", onClick = { onSetLanguage("en") }, label = { Text("English") })
                    }
                }
            }

            SettingsTile(Icons.Rounded.Storage, "Données, stockage & Hors ligne", "Économie de données, cache local, URL API") {
                showStorageDialog = true
            }
            SettingsTile(Icons.Rounded.Block, "Utilisateurs bloqués", "${state.blockedContacts.size} bloqué(s)") {
                showBlockedDialog = true
            }
            SettingsTile(Icons.Rounded.HelpOutline, "Aide & À propos de Prunelle", "Version 1.0 • Confidentialité") {
                showAboutDialog = true
            }

            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onLogout,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("logout_button")
            ) {
                Icon(Icons.Rounded.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Déconnexion")
            }
        }
    }

    if (showProfileDialog) {
        var name by remember { mutableStateOf(prefs.displayName) }
        var user by remember { mutableStateOf(prefs.username) }
        var bio by remember { mutableStateOf(prefs.bio) }
        var status by remember { mutableStateOf(prefs.statusText) }
        var avatar by remember { mutableStateOf(prefs.avatarUri) }
        var bDayText by remember { mutableStateOf(prefs.birthDay?.toString().orEmpty()) }
        var bMonthText by remember { mutableStateOf(prefs.birthMonth?.toString().orEmpty()) }
        var bYearText by remember { mutableStateOf(prefs.birthYear?.toString().orEmpty()) }
        var bPrivacy by remember { mutableStateOf(prefs.birthdayPrivacy) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) avatar = uri.toString()
        }
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Modifier mon profil") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("Changer la photo de profil")
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom") }, singleLine = true)
                    OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Nom d'utilisateur") }, singleLine = true)
                    OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Biographie") })
                    OutlinedTextField(value = status, onValueChange = { status = it }, label = { Text("Statut") }, singleLine = true)

                    Text("Anniversaire (année strictement privée)", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = bDayText,
                            onValueChange = { bDayText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Jour") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = bMonthText,
                            onValueChange = { bMonthText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Mois") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = bYearText,
                            onValueChange = { bYearText = it.filter(Char::isDigit).take(4) },
                            label = { Text("Année") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PrivacyAudience.entries.forEach { aud ->
                            FilterChip(selected = bPrivacy == aud, onClick = { bPrivacy = aud }, label = { Text(aud.labelFr) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val d = bDayText.toIntOrNull()?.takeIf { it in 1..31 }
                    val m = bMonthText.toIntOrNull()?.takeIf { it in 1..12 }
                    val y = bYearText.toIntOrNull()?.takeIf { it in 1900..2025 }
                    onUpdateProfile(
                        name,
                        user,
                        bio,
                        status,
                        avatar,
                        if (d != null && m != null) d else null,
                        if (d != null && m != null) m else null,
                        y,
                        bPrivacy
                    )
                    showProfileDialog = false
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { showProfileDialog = false }) { Text("Annuler") } }
        )
    }

    if (showPrivacyDialog) {
        var readReceipts by remember { mutableStateOf(prefs.readReceiptsEnabled) }
        var onlineVisible by remember { mutableStateOf(prefs.onlinePresenceVisible) }
        var lastSeen by remember { mutableStateOf(prefs.lastSeenVisible) }
        var avatarAud by remember { mutableStateOf(prefs.avatarPrivacy) }
        var bioAud by remember { mutableStateOf(prefs.bioPrivacy) }
        var bdayAud by remember { mutableStateOf(prefs.birthdayPrivacy) }

        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Confidentialité & Sécurité") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Accusés de lecture (coches bleues)")
                        Switch(checked = readReceipts, onCheckedChange = { readReceipts = it })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Afficher présence en ligne")
                        Switch(checked = onlineVisible, onCheckedChange = { onlineVisible = it })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Afficher dernière activité")
                        Switch(checked = lastSeen, onCheckedChange = { lastSeen = it })
                    }
                    Text("Photo visible par : ${avatarAud.labelFr}", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PrivacyAudience.entries.forEach { aud ->
                            FilterChip(selected = avatarAud == aud, onClick = { avatarAud = aud }, label = { Text(aud.labelFr) })
                        }
                    }
                    Text("Anniversaire visible par : ${bdayAud.labelFr}", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PrivacyAudience.entries.forEach { aud ->
                            FilterChip(selected = bdayAud == aud, onClick = { bdayAud = aud }, label = { Text(aud.labelFr) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdatePrivacy(readReceipts, avatarAud, bioAud, onlineVisible, lastSeen, bdayAud)
                    showPrivacyDialog = false
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("Fermer") } }
        )
    }

    if (showNotifDialog) {
        var nMsg by remember { mutableStateOf(prefs.notifyMessages) }
        var nGrp by remember { mutableStateOf(prefs.notifyGroups) }
        var nCall by remember { mutableStateOf(prefs.notifyCalls) }
        var nReact by remember { mutableStateOf(prefs.notifyReactions) }
        var nCh by remember { mutableStateOf(prefs.notifyChannels) }
        var nLike by remember { mutableStateOf(prefs.notifyStatusLikes) }
        var nBirthday by remember { mutableStateOf(prefs.notifyBirthdays) }

        AlertDialog(
            onDismissRequest = { showNotifDialog = false },
            title = { Text("Préférences de notifications") },
            text = {
                Column {
                    NotifSwitchRow("Messages privés", nMsg) { nMsg = it }
                    NotifSwitchRow("Messages de groupe", nGrp) { nGrp = it }
                    NotifSwitchRow("Appels entrants", nCall) { nCall = it }
                    NotifSwitchRow("Réactions", nReact) { nReact = it }
                    NotifSwitchRow("Publications de chaînes", nCh) { nCh = it }
                    NotifSwitchRow("J'aime sur mes statuts", nLike) { nLike = it }
                    NotifSwitchRow("Anniversaires", nBirthday) { nBirthday = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateNotifications(nMsg, nGrp, nCall, nReact, nCh, nLike, nBirthday)
                    showNotifDialog = false
                }) { Text("Enregistrer") }
            }
        )
    }

    if (showStorageDialog) {
        var dataSaver by remember { mutableStateOf(prefs.dataSaverEnabled) }
        var backendUrl by remember { mutableStateOf(prefs.backendBaseUrl) }
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text("Données, Hors ligne & Backend") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Économie de données (30 FPS)")
                        Switch(checked = dataSaver, onCheckedChange = { dataSaver = it })
                    }
                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it },
                        label = { Text("URL API Backend Prunelle (Retrofit)") },
                        singleLine = true
                    )
                    Text(
                        "Les messages envoyés hors ligne sont mis en file d'attente via WorkManager et synchronisés dès le retour d'Internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSetPerformance(prefs.targetFps, dataSaver)
                    onSetBackendUrl(backendUrl)
                    showStorageDialog = false
                }) { Text("Enregistrer") }
            }
        )
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text("Utilisateurs bloqués") },
            text = {
                Column {
                    if (state.blockedContacts.isEmpty()) {
                        Text("Aucun utilisateur bloqué.")
                    } else {
                        state.blockedContacts.forEach { user ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(user.displayName)
                                TextButton(onClick = { onUnblockUser(user.id) }) { Text("Débloquer") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBlockedDialog = false }) { Text("Fermer") } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("À propos de Prunelle") },
            text = {
                Text(
                    "Prunelle v1.0\nApplication de messagerie moderne avec mode hors ligne WorkManager, fonds d'écran animés 4K/120fps, chaînes, statuts 24h, stickers personnalisés et Prunelle IA.\n\nPour activer le cloud temps réel (Firestore/FCM) et Gemini IA, activez Firebase dans AI Studio."
                )
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Fermer") } }
        )
    }
}

@Composable
private fun SettingsTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NotifSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsCenterScreen(
    state: HomeUiState,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit,
    onClearAll: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = { Text("Notifications") },
                actions = {
                    if (state.notificationLogs.isNotEmpty()) {
                        TextButton(onClick = onMarkAllRead) { Text("Tout lire") }
                        IconButton(onClick = onClearAll) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Effacer")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.notificationLogs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Aucune notification récente", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.notificationLogs, key = { it.id }) { log ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(log.title, style = MaterialTheme.typography.titleSmall)
                                Text(formatTimeShort(log.createdAt), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(log.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
