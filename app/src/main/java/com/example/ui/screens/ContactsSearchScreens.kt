package com.example.ui.screens

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.birthday.BirthdayHelper
import com.example.data.local.ContactEntity
import com.example.data.local.PrivacyAudience
import com.example.data.repository.PrunelleRepository
import com.example.ui.components.PrunelleAvatar
import com.example.ui.viewmodel.ChatUiState
import com.example.ui.viewmodel.HomeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: PrunelleRepository,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val contacts by remember(query) { repository.searchContacts(query) }.collectAsStateWithLifecycle(emptyList())
    val conversations by remember(query) { repository.searchConversations(query) }.collectAsStateWithLifecycle(emptyList())
    val messages by remember(query) { repository.searchMessages(query) }.collectAsStateWithLifecycle(emptyList())
    val channels by remember(query) { repository.searchChannels(query) }.collectAsStateWithLifecycle(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Rechercher utilisateurs, groupes, messages...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("global_search_input")
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (contacts.isNotEmpty()) {
                item { Text("Contacts (${contacts.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(contacts, key = { "c_${it.id}" }) { c ->
                    ListItem(
                        headlineContent = { Text(c.displayName) },
                        supportingContent = { Text("@${c.username}") },
                        leadingContent = { PrunelleAvatar(c.displayName, c.avatarUri, size = 42.dp) },
                        modifier = Modifier.clickable { onOpenProfile(c.id) }
                    )
                }
            }
            if (conversations.isNotEmpty()) {
                item { Text("Conversations & Groupes (${conversations.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(conversations, key = { "conv_${it.id}" }) { conv ->
                    ListItem(
                        headlineContent = { Text(conv.title) },
                        supportingContent = { Text(conv.lastMessagePreview) },
                        leadingContent = { PrunelleAvatar(conv.title, conv.avatarUri, size = 42.dp, isGroup = conv.isGroup) },
                        modifier = Modifier.clickable { onOpenConversation(conv.id) }
                    )
                }
            }
            if (channels.isNotEmpty()) {
                item { Text("Chaînes (${channels.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(channels, key = { "ch_${it.id}" }) { ch ->
                    ListItem(
                        headlineContent = { Text(ch.name) },
                        supportingContent = { Text(ch.description) },
                        leadingContent = { PrunelleAvatar(ch.name, ch.avatarUri, size = 42.dp) }
                    )
                }
            }
            if (query.isNotBlank() && messages.isNotEmpty()) {
                item { Text("Messages (${messages.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(messages, key = { "m_${it.id}" }) { msg ->
                    ListItem(
                        headlineContent = { Text(msg.content) },
                        supportingContent = { Text("Par ${msg.senderName}") },
                        modifier = Modifier.clickable { onOpenConversation(msg.conversationId) }
                    )
                }
            }
            if (contacts.isEmpty() && conversations.isEmpty() && channels.isEmpty() && messages.isEmpty()) {
                item {
                    Text(
                        "Aucun résultat trouvé pour « $query ».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    state: HomeUiState,
    onBack: () -> Unit,
    onAddContact: (String, String, String, Int?, Int?, PrivacyAudience) -> Unit,
    onOpenProfile: (String) -> Unit,
    onStartChat: (ContactEntity) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = { Text("Contacts (${state.contacts.size})") },
                actions = {
                    IconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_contact_button")) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = "Ajouter un contact")
                    }
                }
            )
        }
    ) { padding ->
        if (state.contacts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Contacts, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Liste de contacts vide", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tous les contacts de démonstration ont été supprimés. Ajoutez uniquement de vrais utilisateurs Prunelle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { showAddDialog = true }) {
                        Text("Ajouter un contact")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.contacts, key = { it.id }) { contact ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProfile(contact.id) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PrunelleAvatar(contact.displayName, contact.avatarUri, size = 46.dp, isOnline = contact.isOnline)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("@${contact.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onStartChat(contact) }) {
                                Icon(Icons.Rounded.Chat, contentDescription = "Envoyer un message")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onAdd = { name, user, bio, bDay, bMonth, bPriv ->
                onAddContact(name, user, bio, bDay, bMonth, bPriv)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun AddContactDialog(
    onAdd: (String, String, String, Int?, Int?, PrivacyAudience) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var birthDayText by remember { mutableStateOf("") }
    var birthMonthText by remember { mutableStateOf("") }
    var birthdayPrivacy by remember { mutableStateOf(PrivacyAudience.CONTACTS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un contact") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_contact_name")
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' } },
                    label = { Text("Nom d'utilisateur *") },
                    prefix = { Text("@") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_contact_username")
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Note / Bio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Anniversaire (jour & mois uniquement)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = birthDayText,
                        onValueChange = { birthDayText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Jour") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = birthMonthText,
                        onValueChange = { birthMonthText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Mois") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrivacyAudience.entries.forEach { aud ->
                        FilterChip(
                            selected = birthdayPrivacy == aud,
                            onClick = { birthdayPrivacy = aud },
                            label = { Text(aud.labelFr) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = birthDayText.toIntOrNull()?.takeIf { it in 1..31 }
                    val m = birthMonthText.toIntOrNull()?.takeIf { it in 1..12 }
                    onAdd(
                        name,
                        username,
                        bio,
                        if (d != null && m != null) d else null,
                        if (d != null && m != null) m else null,
                        birthdayPrivacy
                    )
                },
                enabled = name.trim().length >= 2 && username.trim().length >= 2,
                modifier = Modifier.testTag("save_new_contact_button")
            ) { Text("Ajouter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConversationScreen(
    state: HomeUiState,
    onBack: () -> Unit,
    onSelectContact: (ContactEntity) -> Unit,
    onAddAndStartChat: (String, String, String, Int?, Int?, PrivacyAudience) -> Unit,
    onOpenCreateGroup: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = { Text("Nouvelle conversation") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    onClick = onOpenCreateGroup,
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.GroupAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Créer un nouveau groupe", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            item {
                Card(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().testTag("new_chat_add_contact_card")
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Ajouter un utilisateur et discuter", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            items(state.contacts, key = { it.id }) { contact ->
                ListItem(
                    headlineContent = { Text(contact.displayName) },
                    supportingContent = { Text("@${contact.username}") },
                    leadingContent = { PrunelleAvatar(contact.displayName, contact.avatarUri, size = 44.dp, isOnline = contact.isOnline) },
                    modifier = Modifier.clickable { onSelectContact(contact) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onAdd = { name, user, bio, bDay, bMonth, bPriv ->
                showAddDialog = false
                onAddAndStartChat(name, user, bio, bDay, bMonth, bPriv)
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    state: HomeUiState,
    onBack: () -> Unit,
    onCreateGroup: (String, String, String?, List<String>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<String?>(null) }
    val selectedMembers = remember { mutableStateListOf<String>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) avatarUri = uri.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = { Text("Créer un groupe") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (avatarUri == null) "Ajouter une photo de groupe" else "Photo sélectionnée ✓")
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nom du groupe *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("group_name_input")
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description du groupe") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Sélectionner des membres (${selectedMembers.size})", style = MaterialTheme.typography.titleSmall)
            if (state.contacts.isEmpty()) {
                Text(
                    "Aucun contact ajouté pour le moment. Vous pouvez créer le groupe maintenant et ajouter des membres ensuite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.contacts.forEach { contact ->
                    val checked = selectedMembers.contains(contact.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) selectedMembers.remove(contact.id) else selectedMembers.add(contact.id)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${contact.displayName} (@${contact.username})")
                    }
                }
            }
            Button(
                onClick = { onCreateGroup(title, description, avatarUri, selectedMembers.toList()) },
                enabled = title.trim().length >= 2,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("create_group_confirm")
            ) {
                Text("Créer le groupe")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    chatState: ChatUiState,
    contacts: List<ContactEntity>,
    onBack: () -> Unit,
    onUpdateGroup: (String, String, String?, Boolean, Boolean) -> Unit,
    onAddMember: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onToggleAdmin: (String) -> Unit,
    onLeaveOrDeleteGroup: () -> Unit
) {
    val conv = chatState.conversation ?: return
    var title by remember(conv.title) { mutableStateOf(conv.title) }
    var description by remember(conv.description) { mutableStateOf(conv.description) }
    var allowInvites by remember(conv.allowMemberInvites) { mutableStateOf(conv.allowMemberInvites) }
    var allowMedia by remember(conv.allowMemberMedia) { mutableStateOf(conv.allowMemberMedia) }
    var showAddMember by remember { mutableStateOf(false) }

    val memberIds = remember(conv.participantIdsCsv) { conv.participantIdsCsv.split(",").filter { it.isNotBlank() } }
    val adminIds = remember(conv.adminIdsCsv) { conv.adminIdsCsv.split(",").filter { it.isNotBlank() }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = { Text("Infos du groupe") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Nom du groupe") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Membres autorisés à inviter")
                Switch(checked = allowInvites, onCheckedChange = { allowInvites = it })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Envoi de médias autorisé")
                Switch(checked = allowMedia, onCheckedChange = { allowMedia = it })
            }

            Button(
                onClick = { onUpdateGroup(title, description, null, allowInvites, allowMedia) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enregistrer les modifications")
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Membres (${memberIds.size})", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showAddMember = true }) { Text("+ Ajouter") }
            }

            memberIds.forEach { memberId ->
                val contact = contacts.firstOrNull { it.id == memberId }
                val label = if (memberId == "me") "Vous" else contact?.displayName ?: memberId
                val isAdmin = adminIds.contains(memberId)
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, fontWeight = FontWeight.Medium)
                            if (isAdmin) Text("Administrateur", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (memberId != "me") {
                            TextButton(onClick = { onToggleAdmin(memberId) }) {
                                Text(if (isAdmin) "Rétrograder" else "Admin")
                            }
                            IconButton(onClick = { onRemoveMember(memberId) }) {
                                Icon(Icons.Rounded.PersonRemove, contentDescription = "Retirer")
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onLeaveOrDeleteGroup,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Quitter / Supprimer le groupe")
            }
        }
    }

    if (showAddMember) {
        val candidates = contacts.filter { !memberIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { showAddMember = false },
            title = { Text("Ajouter un membre") },
            text = {
                Column {
                    if (candidates.isEmpty()) {
                        Text("Aucun autre contact disponible.")
                    } else {
                        candidates.forEach { c ->
                            TextButton(onClick = {
                                onAddMember(c.id)
                                showAddMember = false
                            }) { Text("${c.displayName} (@${c.username})") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddMember = false }) { Text("Fermer") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    contact: ContactEntity?,
    onBack: () -> Unit,
    onSendMessage: (ContactEntity) -> Unit,
    onToggleBlock: (String, Boolean) -> Unit,
    onReport: (String) -> Unit,
    onUpdateBirthday: (String, Int?, Int?, PrivacyAudience) -> Unit = { _, _, _, _ -> }
) {
    var showBirthdayDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = { Text("Profil utilisateur") }
            )
        }
    ) { padding ->
        if (contact == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Utilisateur introuvable")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrunelleAvatar(contact.displayName, contact.avatarUri, size = 96.dp, isOnline = contact.isOnline)
                Text(contact.displayName, style = MaterialTheme.typography.headlineMedium)
                Text("@${contact.username}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = contact.bio.ifBlank { "Aucune biographie renseignée." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (contact.isOnline) "En ligne" else "Statut : ${contact.statusText}",
                    style = MaterialTheme.typography.labelMedium
                )
                if (contact.birthdayPrivacy != PrivacyAudience.NOBODY && contact.birthDay != null && contact.birthMonth != null) {
                    AssistChip(
                        onClick = { showBirthdayDialog = true },
                        leadingIcon = { Icon(Icons.Rounded.Cake, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("Anniversaire : ${BirthdayHelper.formatPublicDayMonth(contact.birthDay, contact.birthMonth)}") }
                    )
                } else {
                    TextButton(onClick = { showBirthdayDialog = true }) {
                        Icon(Icons.Rounded.Cake, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Renseigner la date d'anniversaire")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { onSendMessage(contact) },
                    enabled = !contact.isBlocked,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Envoyer un message")
                }
                OutlinedButton(
                    onClick = { onToggleBlock(contact.id, !contact.isBlocked) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (contact.isBlocked) "Débloquer l'utilisateur" else "Bloquer l'utilisateur")
                }
                TextButton(
                    onClick = { onReport(contact.id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (contact.isReported) "Utilisateur signalé" else "Signaler cet utilisateur")
                }
            }

            if (showBirthdayDialog) {
                var dayText by remember(contact.id) { mutableStateOf(contact.birthDay?.toString().orEmpty()) }
                var monthText by remember(contact.id) { mutableStateOf(contact.birthMonth?.toString().orEmpty()) }
                var privacy by remember(contact.id) { mutableStateOf(contact.birthdayPrivacy) }
                AlertDialog(
                    onDismissRequest = { showBirthdayDialog = false },
                    title = { Text("Anniversaire de ${contact.displayName}") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = dayText,
                                    onValueChange = { dayText = it.filter(Char::isDigit).take(2) },
                                    label = { Text("Jour") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = monthText,
                                    onValueChange = { monthText = it.filter(Char::isDigit).take(2) },
                                    label = { Text("Mois") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PrivacyAudience.entries.forEach { aud ->
                                    FilterChip(selected = privacy == aud, onClick = { privacy = aud }, label = { Text(aud.labelFr) })
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val d = dayText.toIntOrNull()?.takeIf { it in 1..31 }
                            val m = monthText.toIntOrNull()?.takeIf { it in 1..12 }
                            onUpdateBirthday(contact.id, if (d != null && m != null) d else null, if (d != null && m != null) m else null, privacy)
                            showBirthdayDialog = false
                        }) { Text("Enregistrer") }
                    },
                    dismissButton = { TextButton(onClick = { showBirthdayDialog = false }) { Text("Annuler") } }
                )
            }
        }
    }
}
