package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.firebase.authStateFlow
import com.example.core.firebase.configureAppCheck
import com.example.core.firebase.signOutGoogleAndFirebase
import com.example.core.notifications.PrunelleNotificationHelper
import com.example.ui.navigation.PrunelleRoute
import com.example.ui.screens.*
import com.example.ui.theme.PrunelleTheme
import com.example.ui.viewmodel.AiViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.PrunelleViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow

data class NotificationDeepLink(
    val conversationId: String? = null,
    val routeTab: String? = null,
    val openBirthday: Boolean = false
)

class MainActivity : ComponentActivity() {
    private val deepLinkFlow = MutableStateFlow<NotificationDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        extractDeepLink(intent)
        enableEdgeToEdge()
        val app = application as PrunelleApplication

        setContent {
            val mainVm: PrunelleViewModel = viewModel(
                factory = PrunelleViewModel.Factory(app.repository)
            )
            val state by mainVm.uiState.collectAsStateWithLifecycle()
            val pendingDeepLink by deepLinkFlow.collectAsStateWithLifecycle()
            val firebaseUser by remember {
                Firebase.auth.authStateFlow()
            }.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

            LaunchedEffect(firebaseUser?.uid) {
                firebaseUser?.let { user ->
                    mainVm.bindGoogleIdentity(
                        context = applicationContext,
                        uid = user.uid,
                        email = user.email,
                        displayName = user.displayName,
                        avatarUri = user.photoUrl?.toString()
                    )
                }
            }

            PrunelleTheme(themePreference = state.prefs.themePreference) {
                if (firebaseUser == null) {
                    GoogleAuthScreen()
                } else if (state.isLoading && !state.prefs.isLoggedIn) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (!state.prefs.isLoggedIn) {
                    val user = firebaseUser
                    OnboardingScreen(
                        initialName = user?.displayName.orEmpty(),
                        initialAvatarUri = user?.photoUrl?.toString(),
                        onCompleteSetup = { name, username, bio, avatarUri, countryCode, phone, verified, bDay, bMonth, bYear, bPriv ->
                            mainVm.saveAccountAndLogin(
                                displayName = name,
                                username = username,
                                bio = bio,
                                avatarUri = avatarUri,
                                countryCode = countryCode,
                                phoneNumber = phone,
                                isPhoneVerified = verified,
                                birthDay = bDay,
                                birthMonth = bMonth,
                                birthYear = bYear,
                                birthdayPrivacy = bPriv
                            )
                        }
                    )
                } else {
                    val aiVm: AiViewModel = viewModel(
                        factory = AiViewModel.Factory(app.repository)
                    )
                    PrunelleMainShell(
                        app = app,
                        mainVm = mainVm,
                        aiVm = aiVm,
                        pendingDeepLink = pendingDeepLink,
                        onConsumeDeepLink = { deepLinkFlow.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDeepLink(intent)
    }

    private fun extractDeepLink(intent: Intent?) {
        if (intent == null) return
        val convId = intent.getStringExtra(PrunelleNotificationHelper.EXTRA_CONVERSATION_ID)
        val tab = intent.getStringExtra(PrunelleNotificationHelper.EXTRA_ROUTE_TAB)
        val openBirthday = intent.getBooleanExtra(PrunelleNotificationHelper.EXTRA_OPEN_BIRTHDAY, false)
        if (!convId.isNullOrBlank() || !tab.isNullOrBlank() || openBirthday) {
            deepLinkFlow.value = NotificationDeepLink(
                conversationId = convId,
                routeTab = tab,
                openBirthday = openBirthday
            )
        }
    }
}

private data class TopNavItem(
    val label: String,
    val route: PrunelleRoute,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val testTag: String
)

@Composable
private fun PrunelleMainShell(
    app: PrunelleApplication,
    mainVm: PrunelleViewModel,
    aiVm: AiViewModel,
    pendingDeepLink: NotificationDeepLink? = null,
    onConsumeDeepLink: () -> Unit = {}
) {
    val state by mainVm.uiState.collectAsStateWithLifecycle()
    val aiState by aiVm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var activeCall by remember { mutableStateOf<ActiveCallSession?>(null) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(state.prefs.isLoggedIn) {
        if (state.prefs.isLoggedIn) {
            mainVm.checkAndPrepareBirthdays()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !PrunelleNotificationHelper.hasNotificationPermission(context)
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(pendingDeepLink, state.prefs.isLoggedIn) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        if (!state.prefs.isLoggedIn) return@LaunchedEffect
        when {
            !link.conversationId.isNullOrBlank() -> {
                navController.navigate(PrunelleRoute.Chat(link.conversationId))
            }
            link.openBirthday -> {
                mainVm.checkAndPrepareBirthdays(forceShowSelfModal = true)
            }
            link.routeTab == PrunelleNotificationHelper.TAB_CALLS -> {
                navController.navigate(PrunelleRoute.Calls) {
                    popUpTo<PrunelleRoute.Home> { saveState = true }
                    launchSingleTop = true
                }
            }
            link.routeTab == PrunelleNotificationHelper.TAB_UPDATES -> {
                navController.navigate(PrunelleRoute.ChannelsStatuses) {
                    popUpTo<PrunelleRoute.Home> { saveState = true }
                    launchSingleTop = true
                }
            }
            link.routeTab == PrunelleNotificationHelper.TAB_NOTIFICATIONS -> {
                navController.navigate(PrunelleRoute.Notifications)
            }
        }
        onConsumeDeepLink()
    }

    val topTabs = remember {
        listOf(
            TopNavItem("Discussions", PrunelleRoute.Home, Icons.Rounded.ChatBubble, "nav_discussions"),
            TopNavItem("Actus", PrunelleRoute.ChannelsStatuses, Icons.Rounded.Campaign, "nav_channels"),
            TopNavItem("Appels", PrunelleRoute.Calls, Icons.Rounded.Call, "nav_calls"),
            TopNavItem("Prunelle IA", PrunelleRoute.AiHub, Icons.Rounded.AutoAwesome, "nav_ai")
        )
    }

    val showPrimaryNav = currentDestination?.let { dest ->
        dest.hasRoute<PrunelleRoute.Home>() ||
            dest.hasRoute<PrunelleRoute.ChannelsStatuses>() ||
            dest.hasRoute<PrunelleRoute.Calls>() ||
            dest.hasRoute<PrunelleRoute.AiHub>()
    } ?: true

    if (activeCall != null) {
        CallsScreen(
            state = state,
            activeCall = activeCall,
            onStartCall = { activeCall = it },
            onEndCall = { duration ->
                val finished = activeCall!!
                mainVm.recordCall(
                    peerName = finished.peerName,
                    peerId = finished.peerName,
                    isVideo = finished.isVideo,
                    isGroup = finished.isGroup,
                    durationSec = duration
                )
                activeCall = null
            },
            onClearHistory = { mainVm.clearCallHistory() }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            bottomBar = {
                if (showPrimaryNav && !useRail) {
                    NavigationBar {
                        topTabs.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo<PrunelleRoute.Home> { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    if (item.route == PrunelleRoute.Home && state.totalUnreadMessages > 0) {
                                        BadgedBox(badge = { Badge { Text(state.totalUnreadMessages.toString()) } }) {
                                            Icon(item.icon, contentDescription = item.label)
                                        }
                                    } else {
                                        Icon(item.icon, contentDescription = item.label)
                                    }
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (showPrimaryNav && useRail) {
                    NavigationRail {
                        Spacer(modifier = Modifier.height(12.dp))
                        topTabs.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo<PrunelleRoute.Home> { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = PrunelleRoute.Home,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    composable<PrunelleRoute.Home> {
                        ConversationsScreen(
                            state = state,
                            onOpenConversation = { id -> navController.navigate(PrunelleRoute.Chat(id)) },
                            onOpenSearch = { navController.navigate(PrunelleRoute.Search) },
                            onOpenNewConversation = { navController.navigate(PrunelleRoute.NewConversation) },
                            onOpenCreateGroup = { navController.navigate(PrunelleRoute.CreateGroup) },
                            onOpenNotifications = { navController.navigate(PrunelleRoute.Notifications) },
                            onOpenSettings = { navController.navigate(PrunelleRoute.Settings) },
                            onOpenChannelsStatuses = { navController.navigate(PrunelleRoute.ChannelsStatuses) },
                            onOpenAiHub = { navController.navigate(PrunelleRoute.AiHub) },
                            onOpenContacts = { navController.navigate(PrunelleRoute.Contacts) },
                            onOpenSelfBirthday = { mainVm.openSelfBirthdayDialog() },
                            onSendBirthdayGreeting = { contact ->
                                mainVm.sendBirthdayGreeting(contact) { convId ->
                                    navController.navigate(PrunelleRoute.Chat(convId))
                                }
                            }
                        )
                    }

                    composable<PrunelleRoute.ChannelsStatuses> {
                        ChannelsStatusesScreen(
                            state = state,
                            observeChannelPosts = { id -> mainVm.observeChannelPosts(id) },
                            onCreateChannel = { n, d, a -> mainVm.createChannel(n, d, a) },
                            onToggleSubscribe = { mainVm.toggleChannelSubscription(it) },
                            onPublishPost = { chId, content, uri, type -> mainVm.publishChannelPost(chId, content, uri, type) },
                            onDeletePost = { mainVm.deleteChannelPost(it) },
                            onDeleteChannel = { mainVm.deleteChannel(it) },
                            onPublishStatus = { text, uri, isVid, color -> mainVm.publishStatus(text, uri, isVid, color) },
                            onToggleStatusLike = { mainVm.toggleStatusLike(it) },
                            onRecordStatusView = { mainVm.recordStatusView(it) },
                            onDeleteStatus = { mainVm.deleteStatus(it) }
                        )
                    }

                    composable<PrunelleRoute.Calls> {
                        CallsScreen(
                            state = state,
                            activeCall = activeCall,
                            onStartCall = { activeCall = it },
                            onEndCall = { activeCall = null },
                            onClearHistory = { mainVm.clearCallHistory() }
                        )
                    }

                    composable<PrunelleRoute.AiHub> {
                        AiHubScreen(
                            state = aiState,
                            onSendChatPrompt = { prompt, mode -> aiVm.sendPrompt(prompt, mode) },
                            onClearChat = { aiVm.clearChat(it) },
                            onAddMemory = { k, v -> aiVm.addMemory(k, v) },
                            onDeleteMemory = { aiVm.deleteMemory(it) },
                            onClearMemories = { aiVm.clearAllMemories() },
                            onToggleMemoryEnabled = { aiVm.setAiMemoryEnabled(it) },
                            onGenerateImage = { ctx, p, s, r, ref -> aiVm.generateImage(ctx, p, s, r, ref) },
                            onGenerateVideo = { p, d, s, ref -> aiVm.generateVideo(p, d, s, ref) },
                            onToggleFavoriteCreation = { aiVm.toggleCreationFavorite(it) },
                            onDeleteCreation = { aiVm.deleteCreation(it) },
                            onAnalyzeTikTok = { url, uri -> aiVm.analyzeTikTokVideo(app, url, uri) },
                            onSelectTikTokAnalysis = { aiVm.selectTikTokAnalysis(it) },
                            onAskTikTokQuestion = { analysis, q -> aiVm.askTikTokQuestion(analysis, q) },
                            onDeleteTikTokAnalysis = { aiVm.deleteTikTokAnalysis(it) },
                            onDismissError = { aiVm.clearError() }
                        )
                    }

                    composable<PrunelleRoute.Chat> { entry ->
                        val args = entry.toRoute<PrunelleRoute.Chat>()
                        val chatVm: ChatViewModel = viewModel(
                            key = "chat_${args.conversationId}",
                            factory = ChatViewModel.Factory(args.conversationId, app.repository)
                        )
                        val chatState by chatVm.uiState.collectAsStateWithLifecycle()

                        ChatScreen(
                            state = chatState,
                            onBack = { navController.popBackStack() },
                            onSendOrSaveEdit = { chatVm.sendTextOrSaveEdit(it) },
                            onSendMedia = { type, uri, content, name, dur ->
                                chatVm.sendMediaMessage(type, uri, content, name, dur)
                            },
                            onSetReply = { chatVm.setReplyTarget(it) },
                            onSetEditing = { chatVm.setEditingTarget(it) },
                            onToggleReaction = { msg, emoji -> chatVm.toggleReaction(msg, emoji) },
                            onDeleteMessage = { chatVm.deleteMessage(it) },
                            onForwardMessage = { msg, ids -> chatVm.forwardMessage(msg, ids) },
                            onShareExternally = { chatVm.shareMessageExternally(it) },
                            onLoadMoreMessages = { chatVm.loadMoreMessages() },
                            onOpenWallpaperPicker = {
                                navController.navigate(PrunelleRoute.LiveWallpapers(args.conversationId))
                            },
                            onOpenGroupInfo = {
                                navController.navigate(PrunelleRoute.GroupInfo(args.conversationId))
                            },
                            onStartAudioCall = { peer, isGrp ->
                                activeCall = ActiveCallSession(peer, isVideo = false, isGroup = isGrp)
                            },
                            onStartVideoCall = { peer, isGrp ->
                                activeCall = ActiveCallSession(peer, isVideo = true, isGroup = isGrp)
                            },
                            onUpdateMessageStatus = { msg, status -> chatVm.cycleMessageReadStatusDemoCheck(msg, status) },
                            onCreateCustomSticker = { pack, label, uri, overlay, emoji, anim ->
                                mainVm.createCustomSticker(pack, label, uri, overlay, emoji, anim)
                            },
                            onToggleStickerFavorite = { mainVm.toggleStickerFavorite(it) }
                        )
                    }

                    composable<PrunelleRoute.GroupInfo> { entry ->
                        val args = entry.toRoute<PrunelleRoute.GroupInfo>()
                        val chatVm: ChatViewModel = viewModel(
                            key = "chat_info_${args.conversationId}",
                            factory = ChatViewModel.Factory(args.conversationId, app.repository)
                        )
                        val chatState by chatVm.uiState.collectAsStateWithLifecycle()
                        GroupInfoScreen(
                            chatState = chatState,
                            contacts = state.contacts,
                            onBack = { navController.popBackStack() },
                            onUpdateGroup = { t, d, a, inv, med -> chatVm.updateGroupInfo(t, d, a, inv, med) },
                            onAddMember = { chatVm.addGroupMember(it) },
                            onRemoveMember = { chatVm.removeGroupMember(it) },
                            onToggleAdmin = { chatVm.toggleGroupAdmin(it) },
                            onLeaveOrDeleteGroup = {
                                chatVm.leaveOrDeleteConversation {
                                    navController.popBackStack(PrunelleRoute.Home, inclusive = false)
                                }
                            }
                        )
                    }

                    composable<PrunelleRoute.Search> {
                        SearchScreen(
                            repository = app.repository,
                            onBack = { navController.popBackStack() },
                            onOpenConversation = { id -> navController.navigate(PrunelleRoute.Chat(id)) },
                            onOpenProfile = { id -> navController.navigate(PrunelleRoute.UserProfile(id)) }
                        )
                    }

                    composable<PrunelleRoute.Contacts> {
                        ContactsScreen(
                            state = state,
                            onBack = { navController.popBackStack() },
                            onAddContact = { n, u, b, bDay, bMonth, bPriv ->
                                mainVm.addContact(n, u, b, bDay, bMonth, bPriv)
                            },
                            onOpenProfile = { id -> navController.navigate(PrunelleRoute.UserProfile(id)) },
                            onStartChat = { contact ->
                                mainVm.openConversationWithContact(contact) { convId ->
                                    navController.navigate(PrunelleRoute.Chat(convId))
                                }
                            }
                        )
                    }

                    composable<PrunelleRoute.UserProfile> { entry ->
                        val args = entry.toRoute<PrunelleRoute.UserProfile>()
                        val contact = state.contacts.firstOrNull { it.id == args.contactId }
                            ?: state.blockedContacts.firstOrNull { it.id == args.contactId }
                        UserProfileScreen(
                            contact = contact,
                            onBack = { navController.popBackStack() },
                            onSendMessage = { c ->
                                mainVm.openConversationWithContact(c) { convId ->
                                    navController.navigate(PrunelleRoute.Chat(convId))
                                }
                            },
                            onToggleBlock = { id, block -> mainVm.blockUser(id, block) },
                            onReport = { id -> mainVm.reportUser(id) },
                            onUpdateBirthday = { id, d, m, priv -> mainVm.updateContactBirthday(id, d, m, priv) }
                        )
                    }

                    composable<PrunelleRoute.NewConversation> {
                        NewConversationScreen(
                            state = state,
                            onBack = { navController.popBackStack() },
                            onSelectContact = { contact ->
                                mainVm.openConversationWithContact(contact) { convId ->
                                    navController.navigate(PrunelleRoute.Chat(convId))
                                }
                            },
                            onAddAndStartChat = { name, user, bio, bDay, bMonth, bPriv ->
                                mainVm.addContactAndStartChat(name, user, bio, bDay, bMonth, bPriv) { convId ->
                                    navController.navigate(PrunelleRoute.Chat(convId))
                                }
                            },
                            onOpenCreateGroup = { navController.navigate(PrunelleRoute.CreateGroup) }
                        )
                    }

                    composable<PrunelleRoute.CreateGroup> {
                        CreateGroupScreen(
                            state = state,
                            onBack = { navController.popBackStack() },
                            onCreateGroup = { title, desc, avatar, members ->
                                mainVm.createGroup(title, desc, avatar, members) { convId ->
                                    navController.navigate(PrunelleRoute.Chat(convId)) {
                                        popUpTo<PrunelleRoute.Home>()
                                    }
                                }
                            }
                        )
                    }

                    composable<PrunelleRoute.LiveWallpapers> { entry ->
                        val args = entry.toRoute<PrunelleRoute.LiveWallpapers>()
                        LiveWallpapersScreen(
                            state = state,
                            targetConversationId = args.targetConversationId,
                            onBack = { navController.popBackStack() },
                            onApplyGlobalWallpaper = { id, uri -> mainVm.setGlobalWallpaper(id, uri) },
                            onApplyConversationWallpaper = { convId, id, uri ->
                                mainVm.setConversationWallpaper(convId, id, uri)
                            },
                            onUpdatePerformance = { fps, saver -> mainVm.setPerformanceSettings(fps, saver) }
                        )
                    }

                    composable<PrunelleRoute.Settings> {
                        SettingsScreen(
                            state = state,
                            onBack = { navController.popBackStack() },
                            onUpdateProfile = { n, u, b, s, a, bDay, bMonth, bYear, bPriv ->
                                mainVm.updateProfile(n, u, b, s, a, bDay, bMonth, bYear, bPriv)
                            },
                            onSetTheme = { mainVm.setThemePreference(it) },
                            onSetLanguage = { mainVm.setLanguage(it) },
                            onUpdatePrivacy = { r, a, b, o, l, bPriv ->
                                mainVm.updatePrivacy(r, a, b, o, l, bPriv)
                            },
                            onUpdateNotifications = { m, g, c, re, ch, sl, bd ->
                                mainVm.updateNotifications(m, g, c, re, ch, sl, bd)
                            },
                            onSetPerformance = { fps, saver -> mainVm.setPerformanceSettings(fps, saver) },
                            onSetBackendUrl = { mainVm.setBackendUrl(it) },
                            onUnblockUser = { mainVm.blockUser(it, false) },
                            onOpenLiveWallpapers = { navController.navigate(PrunelleRoute.LiveWallpapers(null)) },
                            onLogout = {
                                signOutGoogleAndFirebase(credentialManager, scope) {
                                    mainVm.logout()
                                }
                            }
                        )
                    }

                    composable<PrunelleRoute.Notifications> {
                        NotificationsCenterScreen(
                            state = state,
                            onBack = { navController.popBackStack() },
                            onMarkAllRead = { mainVm.markAllNotificationsRead() },
                            onClearAll = { mainVm.clearNotifications() }
                        )
                    }
                }
            }
        }
    }

    if (state.birthdayState.showSelfBirthdayDialog) {
        BirthdayCelebrationDialog(
            displayName = state.prefs.displayName,
            avatarUri = state.prefs.avatarUri,
            birthdayState = state.birthdayState,
            onRetryAiCard = { mainVm.checkAndPrepareBirthdays(forceShowSelfModal = true) },
            onDismiss = { mainVm.dismissSelfBirthdayDialog() }
        )
    }
}
