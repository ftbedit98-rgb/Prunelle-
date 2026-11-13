package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.birthday.BirthdayContactMatch
import com.example.core.firebase.FcmMessageProcessor
import com.example.core.network.NetworkStatus
import com.example.core.worker.WorkSyncScheduler
import com.example.data.local.*
import com.example.data.repository.PrunelleRepository
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BirthdayUiState(
    val isSelfBirthdayToday: Boolean = false,
    val birthdayCardPath: String? = null,
    val birthdayCardLoading: Boolean = false,
    val birthdayCardError: String? = null,
    val showSelfBirthdayDialog: Boolean = false,
    val contactsWithBirthdayToday: List<BirthdayContactMatch> = emptyList()
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val conversations: List<ConversationEntity> = emptyList(),
    val contacts: List<ContactEntity> = emptyList(),
    val blockedContacts: List<ContactEntity> = emptyList(),
    val channels: List<ChannelEntity> = emptyList(),
    val statuses: List<StatusEntity> = emptyList(),
    val stickerPacks: List<StickerPackEntity> = emptyList(),
    val stickers: List<StickerEntity> = emptyList(),
    val calls: List<CallRecordEntity> = emptyList(),
    val notificationLogs: List<NotificationLogEntity> = emptyList(),
    val unreadNotificationsCount: Int = 0,
    val totalUnreadMessages: Int = 0,
    val pendingSyncCount: Int = 0,
    val networkStatus: NetworkStatus = NetworkStatus(),
    val prefs: UserSessionPrefs = UserSessionPrefs(),
    val birthdayState: BirthdayUiState = BirthdayUiState()
)

class PrunelleViewModel(
    val repository: PrunelleRepository
) : ViewModel() {

    private val _initialHydrationDone = MutableStateFlow(false)
    private val _birthdayState = MutableStateFlow(BirthdayUiState())
    private var birthdayAutoShownThisSession = false

    init {
        viewModelScope.launch {
            delay(220)
            _initialHydrationDone.value = true
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        _initialHydrationDone,
        repository.observeConversations(),
        repository.observeActiveContacts(),
        repository.observeBlockedContacts(),
        repository.observeChannels(),
        repository.observeActiveStatuses(),
        repository.observeStickerPacks(),
        repository.observeStickers(),
        repository.observeCalls(),
        repository.observeNotificationLogs(),
        repository.observeUnreadNotifications(),
        repository.observeTotalUnread(),
        repository.observePendingMessageCount(),
        repository.networkStatus,
        repository.userPrefs,
        _birthdayState
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        HomeUiState(
            isLoading = !(values[0] as Boolean),
            conversations = values[1] as List<ConversationEntity>,
            contacts = values[2] as List<ContactEntity>,
            blockedContacts = values[3] as List<ContactEntity>,
            channels = values[4] as List<ChannelEntity>,
            statuses = values[5] as List<StatusEntity>,
            stickerPacks = values[6] as List<StickerPackEntity>,
            stickers = values[7] as List<StickerEntity>,
            calls = values[8] as List<CallRecordEntity>,
            notificationLogs = values[9] as List<NotificationLogEntity>,
            unreadNotificationsCount = values[10] as Int,
            totalUnreadMessages = values[11] as Int,
            pendingSyncCount = values[12] as Int,
            networkStatus = values[13] as NetworkStatus,
            prefs = values[14] as UserSessionPrefs,
            birthdayState = values[15] as BirthdayUiState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HomeUiState()
    )

    fun bindGoogleIdentity(
        context: Context,
        uid: String,
        email: String?,
        displayName: String?,
        avatarUri: String?
    ) {
        viewModelScope.launch {
            repository.prefsRepo.bindAuthenticatedIdentity(uid, email, displayName, avatarUri)
            val prefs = repository.userPrefs.first()
            if (prefs.isLoggedIn) {
                repository.syncProfileToCloud()
            }
            FcmMessageProcessor.syncRegistrationToken(context.applicationContext)
            WorkSyncScheduler.enqueuePendingSync(context.applicationContext)
        }
    }

    fun checkAndPrepareBirthdays(forceShowSelfModal: Boolean = false) {
        viewModelScope.launch {
            val isSelfToday = repository.checkSelfBirthdayToday()
            val contactMatches = repository.findContactsCelebratingBirthdayToday()
            val current = _birthdayState.value
            _birthdayState.value = current.copy(
                isSelfBirthdayToday = isSelfToday,
                contactsWithBirthdayToday = contactMatches,
                birthdayCardLoading = isSelfToday && (current.birthdayCardPath == null),
                birthdayCardError = null,
                showSelfBirthdayDialog = forceShowSelfModal ||
                    (isSelfToday && !birthdayAutoShownThisSession)
            )
            if (isSelfToday) {
                birthdayAutoShownThisSession = true
                val res = repository.ensureSelfBirthdayCelebrationPrepared()
                _birthdayState.value = _birthdayState.value.copy(
                    birthdayCardLoading = false,
                    birthdayCardPath = res.getOrNull() ?: _birthdayState.value.birthdayCardPath,
                    birthdayCardError = res.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun openSelfBirthdayDialog() {
        _birthdayState.value = _birthdayState.value.copy(showSelfBirthdayDialog = true)
    }

    fun dismissSelfBirthdayDialog() {
        _birthdayState.value = _birthdayState.value.copy(showSelfBirthdayDialog = false)
    }

    fun sendBirthdayGreeting(contact: ContactEntity, customText: String? = null, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val convId = repository.sendBirthdayGreetingToContact(contact, customText)
            val refreshed = repository.findContactsCelebratingBirthdayToday()
            _birthdayState.value = _birthdayState.value.copy(contactsWithBirthdayToday = refreshed)
            onReady(convId)
        }
    }

    fun saveAccountAndLogin(
        displayName: String,
        username: String,
        bio: String,
        avatarUri: String?,
        countryCode: String,
        phoneNumber: String,
        isPhoneVerified: Boolean,
        birthDay: Int? = null,
        birthMonth: Int? = null,
        birthYear: Int? = null,
        birthdayPrivacy: PrivacyAudience = PrivacyAudience.CONTACTS
    ) {
        viewModelScope.launch {
            repository.persistAndSaveProfileAndLogin(
                displayName = displayName,
                username = username,
                bio = bio,
                avatarUri = avatarUri,
                countryCode = countryCode,
                phoneNumber = phoneNumber,
                isPhoneVerified = isPhoneVerified,
                birthDay = birthDay,
                birthMonth = birthMonth,
                birthYear = birthYear,
                birthdayPrivacy = birthdayPrivacy
            )
            checkAndPrepareBirthdays()
        }
    }

    fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        statusText: String,
        avatarUri: String?,
        birthDay: Int? = null,
        birthMonth: Int? = null,
        birthYear: Int? = null,
        birthdayPrivacy: PrivacyAudience = PrivacyAudience.CONTACTS
    ) {
        viewModelScope.launch {
            repository.persistAndUpdateProfile(
                displayName = displayName,
                username = username,
                bio = bio,
                statusText = statusText,
                avatarUri = avatarUri,
                birthDay = birthDay,
                birthMonth = birthMonth,
                birthYear = birthYear,
                birthdayPrivacy = birthdayPrivacy
            )
            checkAndPrepareBirthdays()
        }
    }

    fun addContactAndStartChat(
        displayName: String,
        username: String,
        bio: String,
        birthDay: Int? = null,
        birthMonth: Int? = null,
        birthdayPrivacy: PrivacyAudience = PrivacyAudience.CONTACTS,
        onConversationReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            val contact = repository.addContact(
                displayName = displayName,
                username = username,
                bio = bio,
                birthDay = birthDay,
                birthMonth = birthMonth,
                birthdayPrivacy = birthdayPrivacy
            )
            val convId = repository.startOrGetDirectConversation(contact)
            checkAndPrepareBirthdays()
            onConversationReady(convId)
        }
    }

    fun addContact(
        displayName: String,
        username: String,
        bio: String = "",
        birthDay: Int? = null,
        birthMonth: Int? = null,
        birthdayPrivacy: PrivacyAudience = PrivacyAudience.CONTACTS
    ) {
        viewModelScope.launch {
            repository.addContact(
                displayName = displayName,
                username = username,
                bio = bio,
                birthDay = birthDay,
                birthMonth = birthMonth,
                birthdayPrivacy = birthdayPrivacy
            )
            checkAndPrepareBirthdays()
        }
    }

    fun updateContactBirthday(
        contactId: String,
        birthDay: Int?,
        birthMonth: Int?,
        birthdayPrivacy: PrivacyAudience
    ) {
        viewModelScope.launch {
            repository.updateContactBirthday(contactId, birthDay, birthMonth, birthdayPrivacy)
            checkAndPrepareBirthdays()
        }
    }

    fun openConversationWithContact(contact: ContactEntity, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val convId = repository.startOrGetDirectConversation(contact)
            onReady(convId)
        }
    }

    fun createGroup(
        title: String,
        description: String,
        avatarUri: String?,
        memberIds: List<String>,
        onReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.createGroupConversation(title, description, avatarUri, memberIds)
            onReady(id)
        }
    }

    fun blockUser(contactId: String, blocked: Boolean) {
        viewModelScope.launch { repository.blockUser(contactId, blocked) }
    }

    fun reportUser(contactId: String) {
        viewModelScope.launch { repository.reportUser(contactId) }
    }

    fun createChannel(name: String, description: String, avatarUri: String?) {
        viewModelScope.launch { repository.createChannel(name, description, avatarUri) }
    }

    fun toggleChannelSubscription(channel: ChannelEntity) {
        viewModelScope.launch { repository.toggleChannelSubscription(channel) }
    }

    fun observeChannelPosts(channelId: String): Flow<List<ChannelPostEntity>> =
        repository.observeChannelPosts(channelId)

    fun publishChannelPost(channelId: String, content: String, mediaUri: String?, mediaType: MessageType) {
        viewModelScope.launch { repository.publishChannelPost(channelId, content, mediaUri, mediaType) }
    }

    fun deleteChannelPost(postId: String) {
        viewModelScope.launch { repository.deleteChannelPost(postId) }
    }

    fun deleteChannel(channelId: String) {
        viewModelScope.launch { repository.deleteChannel(channelId) }
    }

    fun publishStatus(textContent: String, mediaUri: String?, isVideo: Boolean, bgColorHex: Long) {
        viewModelScope.launch { repository.publishStatus(textContent, mediaUri, isVideo, bgColorHex) }
    }

    fun toggleStatusLike(status: StatusEntity) {
        viewModelScope.launch { repository.toggleStatusLike(status) }
    }

    fun recordStatusView(status: StatusEntity) {
        viewModelScope.launch { repository.recordStatusView(status) }
    }

    fun deleteStatus(statusId: String) {
        viewModelScope.launch { repository.deleteStatus(statusId) }
    }

    fun createCustomSticker(
        packName: String,
        label: String,
        imageUri: String?,
        overlayText: String,
        emojiFallback: String,
        isAnimated: Boolean
    ) {
        viewModelScope.launch {
            repository.createCustomSticker(packName, label, imageUri, overlayText, emojiFallback, isAnimated)
        }
    }

    fun toggleStickerFavorite(sticker: StickerEntity) {
        viewModelScope.launch { repository.toggleStickerFavorite(sticker) }
    }

    fun recordCall(peerName: String, peerId: String, isVideo: Boolean, isGroup: Boolean, durationSec: Int) {
        viewModelScope.launch { repository.recordCall(peerName, peerId, isVideo, isGroup, durationSec) }
    }

    fun clearCallHistory() {
        viewModelScope.launch { repository.clearCallHistory() }
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch { repository.prefsRepo.setThemePreference(theme) }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch { repository.prefsRepo.setLanguage(code) }
    }

    fun setGlobalWallpaper(wallpaperId: String, customUri: String?) {
        viewModelScope.launch { repository.setGlobalWallpaper(wallpaperId, customUri) }
    }

    fun setConversationWallpaper(conversationId: String, wallpaperId: String?, customUri: String?) {
        viewModelScope.launch { repository.setConversationWallpaper(conversationId, wallpaperId, customUri) }
    }

    fun setPerformanceSettings(targetFps: Int, dataSaver: Boolean) {
        viewModelScope.launch { repository.prefsRepo.setPerformanceSettings(targetFps, dataSaver) }
    }

    fun updatePrivacy(
        readReceipts: Boolean,
        avatarPrivacy: PrivacyAudience,
        bioPrivacy: PrivacyAudience,
        onlineVisible: Boolean,
        lastSeenVisible: Boolean,
        birthdayPrivacy: PrivacyAudience = PrivacyAudience.CONTACTS
    ) {
        viewModelScope.launch {
            repository.prefsRepo.updatePrivacy(
                readReceipts = readReceipts,
                avatarPrivacy = avatarPrivacy,
                bioPrivacy = bioPrivacy,
                onlineVisible = onlineVisible,
                lastSeenVisible = lastSeenVisible,
                birthdayPrivacy = birthdayPrivacy
            )
            repository.syncProfileToCloud()
        }
    }

    fun updateNotifications(
        messages: Boolean,
        groups: Boolean,
        calls: Boolean,
        reactions: Boolean,
        channels: Boolean,
        statusLikes: Boolean,
        birthdays: Boolean = true
    ) {
        viewModelScope.launch {
            repository.prefsRepo.updateNotifications(messages, groups, calls, reactions, channels, statusLikes, birthdays)
        }
    }

    fun setBackendUrl(url: String) {
        viewModelScope.launch { repository.prefsRepo.setBackendUrl(url) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { repository.markAllNotificationsRead() }
    }

    fun clearNotifications() {
        viewModelScope.launch { repository.clearNotifications() }
    }

    fun logout() {
        viewModelScope.launch { repository.prefsRepo.logout() }
    }

    class Factory(private val repository: PrunelleRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PrunelleViewModel(repository) as T
        }
    }
}
