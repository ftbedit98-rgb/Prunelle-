package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ContactEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.StatusEntity
import com.example.ui.components.*
import com.example.ui.viewmodel.HomeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    state: HomeUiState,
    onOpenConversation: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNewConversation: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChannelsStatuses: () -> Unit,
    onOpenAiHub: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenSelfBirthday: () -> Unit = {},
    onSendBirthdayGreeting: (ContactEntity) -> Unit = {}
) {
    var showFabMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Prunelle",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        NetworkStatusBanner(
                            status = state.networkStatus,
                            pendingSyncCount = state.pendingSyncCount,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier.testTag("home_search_button")
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Rechercher")
                    }
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("home_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (state.unreadNotificationsCount > 0) {
                                    Badge { Text(state.unreadNotificationsCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.NotificationsNone, contentDescription = "Notifications")
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        PrunelleAvatar(
                            name = state.prefs.displayName.ifBlank { "Moi" },
                            avatarUri = state.prefs.avatarUri,
                            size = 32.dp
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showFabMenu = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("new_chat_fab")
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Nouvelle discussion")
                }
                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Nouvelle conversation") },
                        leadingIcon = { Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null) },
                        onClick = {
                            showFabMenu = false
                            onOpenNewConversation()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nouveau groupe") },
                        leadingIcon = { Icon(Icons.Rounded.GroupAdd, contentDescription = null) },
                        onClick = {
                            showFabMenu = false
                            onOpenCreateGroup()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Mes contacts") },
                        leadingIcon = { Icon(Icons.Rounded.Contacts, contentDescription = null) },
                        onClick = {
                            showFabMenu = false
                            onOpenContacts()
                        }
                    )
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            ConversationListSkeleton(
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                // Statuses 24h strip
                item {
                    StatusesStrip(
                        statuses = state.statuses,
                        myAvatarUri = state.prefs.avatarUri,
                        myName = state.prefs.displayName.ifBlank { "Moi" },
                        onOpenStatuses = onOpenChannelsStatuses
                    )
                }

                // Quick action banner for Prunelle Channels & AI
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AssistChip(
                            onClick = onOpenChannelsStatuses,
                            label = { Text("Mes chaînes (${state.channels.size})") },
                            leadingIcon = { Icon(Icons.Rounded.Campaign, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        AssistChip(
                            onClick = onOpenAiHub,
                            label = { Text("Prunelle IA & TikTok") },
                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }

                // Self birthday banner if today is the user's birthday
                if (state.birthdayState.isSelfBirthdayToday) {
                    item {
                        Card(
                            onClick = onOpenSelfBirthday,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .testTag("self_birthday_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Cake,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Joyeux anniversaire !",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Touchez pour ouvrir votre carte Prunelle",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                                TextButton(onClick = onOpenSelfBirthday) {
                                    Text("Voir")
                                }
                            }
                        }
                    }
                }

                // Contacts celebrating their birthday today
                if (state.birthdayState.contactsWithBirthdayToday.isNotEmpty()) {
                    items(
                        state.birthdayState.contactsWithBirthdayToday,
                        key = { "bday_${it.contact.id}" }
                    ) { match ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .testTag("contact_birthday_banner_${match.contact.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Celebration,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Anniversaire de ${match.contact.displayName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "Envoyez-lui un vœu rapide en 1 clic",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { onSendBirthdayGreeting(match.contact) },
                                    enabled = !match.alreadyGreetedThisYear
                                ) {
                                    Text(if (match.alreadyGreetedThisYear) "Souhaité ✓" else "Souhaiter")
                                }
                            }
                        }
                    }
                }

                if (state.conversations.isEmpty()) {
                    item {
                        EmptyConversationsView(
                            onStartConversation = onOpenNewConversation,
                            onCreateGroup = onOpenCreateGroup,
                            onOpenAi = onOpenAiHub
                        )
                    }
                } else {
                    items(state.conversations, key = { it.id }) { conv ->
                        ConversationRow(
                            conversation = conv,
                            onClick = { onOpenConversation(conv.id) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 82.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusesStrip(
    statuses: List<StatusEntity>,
    myAvatarUri: String?,
    myName: String,
    onOpenStatuses: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(68.dp)
                    .clickable(onClick = onOpenStatuses)
                    .testTag("add_status_shortcut")
            ) {
                Box {
                    PrunelleAvatar(name = myName, avatarUri = myAvatarUri, size = 56.dp)
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "Publier un statut",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Statut",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        items(statuses.take(10), key = { it.id }) { status ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(68.dp)
                    .clickable(onClick = onOpenStatuses)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(2.5.dp)
                ) {
                    PrunelleAvatar(
                        name = status.authorName,
                        avatarUri = status.authorAvatarUri,
                        size = 53.dp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = status.authorName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("conversation_row_${conversation.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PrunelleAvatar(
            name = conversation.title,
            avatarUri = conversation.avatarUri,
            size = 54.dp,
            isOnline = conversation.isOnline,
            isGroup = conversation.isGroup
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTimeShort(conversation.lastMessageTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (conversation.unreadCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReadReceiptTicks(
                    status = conversation.lastMessageStatus,
                    modifier = Modifier.padding(end = 5.dp)
                )
                Text(
                    text = conversation.lastMessagePreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationsView(
    onStartConversation: () -> Unit,
    onCreateGroup: () -> Unit,
    onOpenAi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Text(
            text = "Aucune conversation pour le moment",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Votre espace Prunelle est propre et sans contacts factices. Ajoutez un contact réel, créez un groupe ou explorez vos chaînes et l'assistant IA.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = onStartConversation,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("empty_new_conversation_button")
        ) {
            Icon(Icons.Rounded.PersonAdd, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Démarrer une conversation")
        }
        OutlinedButton(
            onClick = onCreateGroup,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Rounded.GroupAdd, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Créer un groupe")
        }
        TextButton(onClick = onOpenAi) {
            Text("Découvrir Prunelle IA & TikTok AI")
        }
    }
}
