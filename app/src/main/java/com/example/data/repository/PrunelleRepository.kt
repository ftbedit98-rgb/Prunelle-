package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.core.birthday.BirthdayContactMatch
import com.example.core.birthday.BirthdayHelper
import com.example.core.media.MediaStorageHelper
import com.example.core.network.NetworkStatus
import com.example.core.network.NetworkStatusMonitor
import com.example.core.notifications.PrunelleNotificationHelper
import com.example.core.worker.WorkSyncScheduler
import com.example.data.local.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File
import java.util.UUID

class PrunelleRepository(
    private val context: Context,
    private val db: PrunelleDatabase,
    val prefsRepo: UserPreferencesRepository,
    val networkMonitor: NetworkStatusMonitor,
    val aiRepository: AiRepository,
    private val cloudRepoProvider: (() -> FirestoreCloudRepository)? = null
) {
    private val contactDao = db.contactDao()
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()
    private val channelDao = db.channelDao()
    private val statusDao = db.statusDao()
    private val stickerDao = db.stickerDao()
    private val callDao = db.callDao()
    private val aiDao = db.aiDao()
    private val tikTokAiDao = db.tikTokAiDao()
    private val notificationLogDao = db.notificationLogDao()

    val tikTokAnalysisRepository = TikTokAnalysisRepository(tikTokAiDao, aiRepository)

    val networkStatus: StateFlow<NetworkStatus> = networkMonitor.networkStatus
    val userPrefs: Flow<UserSessionPrefs> = prefsRepo.userPrefs

    private fun activeCloudRepoIfAuthenticated(): FirestoreCloudRepository? {
        val hasUser = runCatching { Firebase.auth.currentUser != null }.getOrDefault(false)
        if (!hasUser) return null
        return runCatching { cloudRepoProvider?.invoke() }.getOrNull()
    }

    fun observeConversations(): Flow<List<ConversationEntity>> = conversationDao.observeConversations()
    fun observeConversation(id: String): Flow<ConversationEntity?> = conversationDao.observeConversation(id)
    fun observeTotalUnread(): Flow<Int> = conversationDao.observeTotalUnreadCount()
    fun observePendingMessageCount(): Flow<Int> = messageDao.observePendingCount()
    fun observePagedMessages(conversationId: String, limit: Int): Flow<List<MessageEntity>> =
        messageDao.observePagedMessages(conversationId, limit)

    fun observeMessageCount(conversationId: String): Flow<Int> =
        messageDao.observeMessageCount(conversationId)

    fun observeActiveContacts(): Flow<List<ContactEntity>> = contactDao.observeActiveContacts()
    fun observeBlockedContacts(): Flow<List<ContactEntity>> = contactDao.observeBlockedContacts()
    fun searchContacts(query: String): Flow<List<ContactEntity>> = contactDao.searchContacts(query)
    fun searchConversations(query: String): Flow<List<ConversationEntity>> = conversationDao.searchConversations(query)
    fun searchMessages(query: String): Flow<List<MessageEntity>> = messageDao.searchMessages(query)
    fun searchChannels(query: String): Flow<List<ChannelEntity>> = channelDao.searchChannels(query)

    fun observeChannels(): Flow<List<ChannelEntity>> = channelDao.observeChannels()
    fun observeChannelPosts(channelId: String): Flow<List<ChannelPostEntity>> = channelDao.observePosts(channelId)
    fun observeActiveStatuses(): Flow<List<StatusEntity>> = statusDao.observeActiveStatuses(System.currentTimeMillis())
    fun observeStickerPacks(): Flow<List<StickerPackEntity>> = stickerDao.observePacks()
    fun observeStickers(): Flow<List<StickerEntity>> = stickerDao.observeAllStickers()
    fun observeCalls(): Flow<List<CallRecordEntity>> = callDao.observeCalls()
    fun observeAiMessages(mode: String): Flow<List<AiMessageEntity>> = aiDao.observeAiMessages(mode)
    fun observeAiMemories(): Flow<List<AiMemoryEntity>> = aiDao.observeMemories()
    fun observeAiCreations(): Flow<List<AiCreationEntity>> = aiDao.observeCreations()
    fun observeTikTokAnalyses(): Flow<List<TikTokAnalysisEntity>> = tikTokAnalysisRepository.observeHistory()
    fun observeTikTokAnalysis(id: String): Flow<TikTokAnalysisEntity?> = tikTokAnalysisRepository.observeReportById(id)
    fun observeTikTokQa(analysisId: String): Flow<List<TikTokQaEntity>> = tikTokAnalysisRepository.observeFollowUpQa(analysisId)
    fun observeNotificationLogs(): Flow<List<NotificationLogEntity>> = notificationLogDao.observeLogs()
    fun observeUnreadNotifications(): Flow<Int> = notificationLogDao.observeUnreadCount()

    suspend fun syncProfileToCloud() {
        val cloud = activeCloudRepoIfAuthenticated() ?: return
        if (!networkStatus.value.isOnline) return
        val prefs = prefsRepo.userPrefs.first()
        val phoneMasked = if (prefs.phoneNumberPrivate.length >= 2) {
            "${prefs.countryCode} •••• ${prefs.phoneNumberPrivate.takeLast(2)}"
        } else ""
        cloud.saveUserProfileAndPrivateAccount(
            displayName = prefs.displayName.ifBlank { "Utilisateur Prunelle" },
            username = prefs.username.ifBlank { "user_${prefs.userId.take(6)}" },
            bio = prefs.bio,
            statusText = prefs.statusText,
            avatarUri = prefs.avatarUri,
            birthDay = prefs.birthDay,
            birthMonth = prefs.birthMonth,
            birthYear = prefs.birthYear,
            birthdayPrivacy = prefs.birthdayPrivacy,
            timezoneId = prefs.timezoneId,
            countryCode = prefs.countryCode,
            phoneMasked = phoneMasked,
            email = prefs.email.ifBlank { null },
            readReceiptsEnabled = prefs.readReceiptsEnabled
        )
    }

    suspend fun persistAndSaveProfileAndLogin(
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
        val persistedAvatar = if (!avatarUri.isNullOrBlank() && avatarUri.startsWith("content://")) {
            MediaStorageHelper.persistUriToInternalFile(context, avatarUri, "avatars", "my_avatar")
        } else avatarUri

        prefsRepo.saveProfileAndLogin(
            displayName = displayName,
            username = username,
            bio = bio,
            avatarUri = persistedAvatar,
            countryCode = countryCode,
            phoneNumber = phoneNumber,
            isPhoneVerified = isPhoneVerified,
            birthDay = birthDay,
            birthMonth = birthMonth,
            birthYear = birthYear,
            birthdayPrivacy = birthdayPrivacy
        )
        syncProfileToCloud()
        if (birthDay != null && birthMonth != null) {
            WorkSyncScheduler.enqueueImmediateBirthdayCheck(context)
        }
    }

    suspend fun persistAndUpdateProfile(
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
        val persistedAvatar = if (!avatarUri.isNullOrBlank() && avatarUri.startsWith("content://")) {
            MediaStorageHelper.persistUriToInternalFile(context, avatarUri, "avatars", "my_avatar")
        } else avatarUri

        prefsRepo.updateProfile(
            displayName = displayName,
            username = username,
            bio = bio,
            statusText = statusText,
            avatarUri = persistedAvatar,
            birthDay = birthDay,
            birthMonth = birthMonth,
            birthYear = birthYear,
            birthdayPrivacy = birthdayPrivacy
        )
        syncProfileToCloud()
        if (birthDay != null && birthMonth != null) {
            WorkSyncScheduler.enqueueImmediateBirthdayCheck(context)
        }
    }

    suspend fun addContact(
        displayName: String,
        username: String,
        bio: String = "",
        phoneMasked: String = "",
        birthDay: Int? = null,
        birthMonth: Int? = null,
        birthdayPrivacy: PrivacyAudience = PrivacyAudience.CONTACTS
    ): ContactEntity {
        val cleanUsername = username.trim().lowercase().removePrefix("@")
        val contact = ContactEntity(
            id = "usr_${UUID.randomUUID().toString().take(8)}",
            displayName = displayName.trim(),
            username = cleanUsername,
            bio = bio.trim(),
            phoneMasked = phoneMasked.trim(),
            isOnline = networkStatus.value.isOnline,
            birthDay = birthDay?.takeIf { it in 1..31 },
            birthMonth = birthMonth?.takeIf { it in 1..12 },
            birthdayPrivacy = birthdayPrivacy
        )
        contactDao.upsertContact(contact)
        if (networkStatus.value.isOnline) {
            activeCloudRepoIfAuthenticated()?.upsertContact(contact)
        }
        return contact
    }

    suspend fun updateContactBirthday(
        contactId: String,
        birthDay: Int?,
        birthMonth: Int?,
        privacy: PrivacyAudience
    ) {
        val existing = contactDao.getContactById(contactId) ?: return
        val updated = existing.copy(
            birthDay = birthDay?.takeIf { it in 1..31 },
            birthMonth = birthMonth?.takeIf { it in 1..12 },
            birthdayPrivacy = privacy
        )
        contactDao.upsertContact(updated)
        if (networkStatus.value.isOnline) {
            activeCloudRepoIfAuthenticated()?.upsertContact(updated)
        }
    }

    suspend fun startOrGetDirectConversation(contact: ContactEntity): String {
        val convId = "dm_${contact.id}"
        val existing = conversationDao.getConversation(convId)
        if (existing == null) {
            val conv = ConversationEntity(
                id = convId,
                title = contact.displayName,
                isGroup = false,
                avatarUri = contact.avatarUri,
                participantIdsCsv = "me,${contact.id}",
                lastMessagePreview = "Conversation créée",
                lastMessageTime = System.currentTimeMillis(),
                isOnline = contact.isOnline
            )
            conversationDao.upsertConversation(conv)
            if (networkStatus.value.isOnline) {
                activeCloudRepoIfAuthenticated()?.upsertConversation(conv)
            }
        }
        return convId
    }

    suspend fun createGroupConversation(
        title: String,
        description: String,
        avatarUri: String?,
        memberIds: List<String>
    ): String {
        val convId = "grp_${UUID.randomUUID().toString().take(8)}"
        val persistedAvatar = if (!avatarUri.isNullOrBlank() && avatarUri.startsWith("content://")) {
            MediaStorageHelper.persistUriToInternalFile(context, avatarUri, "avatars", "grp_${convId.take(8)}")
        } else avatarUri

        val allMembers = (listOf("me") + memberIds).distinct().joinToString(",")
        val conv = ConversationEntity(
            id = convId,
            title = title.trim(),
            isGroup = true,
            description = description.trim(),
            avatarUri = persistedAvatar,
            participantIdsCsv = allMembers,
            adminIdsCsv = "me",
            lastMessagePreview = "Groupe créé",
            lastMessageTime = System.currentTimeMillis()
        )
        conversationDao.upsertConversation(conv)
        if (networkStatus.value.isOnline) {
            activeCloudRepoIfAuthenticated()?.upsertConversation(conv)
        }
        return convId
    }

    suspend fun updateGroupInfo(
        conversationId: String,
        title: String,
        description: String,
        avatarUri: String?,
        allowMemberInvites: Boolean,
        allowMemberMedia: Boolean
    ) {
        val conv = conversationDao.getConversation(conversationId) ?: return
        val updated = conv.copy(
            title = title.trim(),
            description = description.trim(),
            avatarUri = avatarUri ?: conv.avatarUri,
            allowMemberInvites = allowMemberInvites,
            allowMemberMedia = allowMemberMedia
        )
        conversationDao.upsertConversation(updated)
        if (networkStatus.value.isOnline) {
            activeCloudRepoIfAuthenticated()?.upsertConversation(updated)
        }
    }

    suspend fun addGroupMember(conversationId: String, memberId: String) {
        val conv = conversationDao.getConversation(conversationId) ?: return
        val members = conv.participantIdsCsv.split(",").filter { it.isNotBlank() }.toMutableSet()
        members.add(memberId)
        val updated = conv.copy(participantIdsCsv = members.joinToString(","))
        conversationDao.upsertConversation(updated)
        if (networkStatus.value.isOnline) {
            activeCloudRepoIfAuthenticated()?.upsertConversation(updated)
        }
    }

    suspend fun removeGroupMember(conversationId: String, memberId: String) {
        val conv = conversationDao.getConversation(conversationId) ?: return
        val members = conv.participantIdsCsv.split(",").filter { it.isNotBlank() && it != memberId }
        val admins = conv.adminIdsCsv.split(",").filter { it.isNotBlank() && it != memberId }
        val updated = conv.copy(
            participantIdsCsv = members.joinToString(","),
            adminIdsCsv = admins.joinToString(",")
        )
        conversationDao.upsertConversation(updated)
        if (networkStatus.value.isOnline) {
            activeCloudRepoIfAuthenticated()?.upsertConversation(updated)
        }
    }

    suspend fun toggleGroupAdmin(conversationId: String, memberId: String) {
        val conv = conversationDao.getConversation(conversationId) ?: return
        val admins = conv.adminIdsCsv.split(",").filter { it.isNotBlank() }.toMutableSet()
        if (admins.contains(memberId)) admins.remove(memberId) else admins.add(memberId)
        val updated = conv.copy(adminIdsCsv = admins.joinToString(","))
        conversationDao.upsertConversation(updated)
        if (networkStatus.value.isOnline) {
            activeCloudRepoIfAuthenticated()?.upsertConversation(updated)
        }
    }

    suspend fun deleteOrLeaveConversation(conversationId: String) {
        messageDao.deleteMessagesForConversation(conversationId)
        conversationDao.deleteConversation(conversationId)
        if (networkStatus.value.isOnline) {
            activeCloudRepoIfAuthenticated()?.deleteConversation(conversationId)
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        content: String,
        type: MessageType = MessageType.TEXT,
        mediaUri: String? = null,
        mediaFileName: String? = null,
        mediaDurationMs: Long = 0L,
        replyToMessage: MessageEntity? = null,
        isForwarded: Boolean = false
    ) {
        val prefs = prefsRepo.userPrefs.first()
        val isOnline = networkStatus.value.isOnline
        val now = System.currentTimeMillis()
        val persistedMedia = if (!mediaUri.isNullOrBlank() && mediaUri.startsWith("content://") &&
            (type == MessageType.IMAGE || type == MessageType.VIDEO)
        ) {
            MediaStorageHelper.persistUriToInternalFile(context, mediaUri, "media_cache")
        } else mediaUri

        // Write locally as PENDING first for offline durability and duplicate-free outbox
        val msg = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = prefs.userId,
            senderName = prefs.displayName.ifBlank { "Moi" },
            content = content,
            type = type,
            mediaUri = persistedMedia,
            mediaFileName = mediaFileName,
            mediaDurationMs = mediaDurationMs,
            replyToMessageId = replyToMessage?.id,
            replyToPreview = replyToMessage?.let {
                "${it.senderName}: ${it.content.ifBlank { it.type.name }.take(48)}"
            },
            isForwarded = isForwarded,
            readStatus = ReadStatus.PENDING,
            sentAt = now
        )
        messageDao.insertMessage(msg)

        val previewText = when (type) {
            MessageType.IMAGE -> "📷 Photo"
            MessageType.VIDEO -> "🎬 Vidéo"
            MessageType.VOICE -> "🎤 Message vocal (${(mediaDurationMs / 1000).coerceAtLeast(1)}s)"
            MessageType.FILE -> "📎 ${mediaFileName ?: "Fichier"}"
            MessageType.STICKER -> "✨ Sticker ${content.take(12)}"
            MessageType.TEXT -> content
        }
        val convBefore = conversationDao.getConversation(conversationId)
        convBefore?.let { conv ->
            conversationDao.upsertConversation(
                conv.copy(
                    lastMessagePreview = previewText,
                    lastMessageTime = now,
                    lastMessageStatus = ReadStatus.PENDING
                )
            )
        }

        val cloud = if (isOnline) activeCloudRepoIfAuthenticated() else null
        if (cloud != null) {
            val res = cloud.upsertMessage(msg, statusOverride = ReadStatus.SENT)
            if (res.isSuccess) {
                messageDao.updateReadStatus(msg.id, ReadStatus.SENT, null, null)
                conversationDao.getConversation(conversationId)?.let { conv ->
                    val updatedConv = conv.copy(lastMessageStatus = ReadStatus.SENT)
                    conversationDao.upsertConversation(updatedConv)
                    cloud.upsertConversation(updatedConv)
                }
            } else {
                WorkSyncScheduler.enqueuePendingSync(context)
            }
        } else {
            WorkSyncScheduler.enqueuePendingSync(context)
        }
    }

    suspend fun forwardMessageToConversations(
        message: MessageEntity,
        targetConversationIds: List<String>
    ) {
        targetConversationIds.distinct().forEach { targetId ->
            sendMessage(
                conversationId = targetId,
                content = message.content,
                type = message.type,
                mediaUri = message.mediaUri,
                mediaFileName = message.mediaFileName,
                mediaDurationMs = message.mediaDurationMs,
                isForwarded = true
            )
        }
    }

    fun shareMessageExternally(message: MessageEntity) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (!message.mediaUri.isNullOrBlank() &&
                (message.type == MessageType.IMAGE || message.type == MessageType.VIDEO || message.type == MessageType.FILE)
            ) {
                val uri = runCatching { Uri.parse(message.mediaUri) }.getOrNull()
                if (uri != null && uri.scheme == "content") {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    type = when (message.type) {
                        MessageType.IMAGE -> "image/*"
                        MessageType.VIDEO -> "video/*"
                        else -> "*/*"
                    }
                } else {
                    putExtra(Intent.EXTRA_TEXT, message.content)
                    type = "text/plain"
                }
            } else {
                putExtra(Intent.EXTRA_TEXT, message.content)
                type = "text/plain"
            }
        }
        val chooser = Intent.createChooser(sendIntent, "Partager via").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(chooser) }
    }

    suspend fun editMessage(message: MessageEntity, newContent: String) {
        val updated = message.copy(content = newContent.trim(), isEdited = true)
        messageDao.updateMessage(updated)
        if (networkStatus.value.isOnline) {
            activeCloudRepoIfAuthenticated()?.upsertMessage(updated)
        }
    }

    suspend fun deleteMessage(message: MessageEntity) {
        messageDao.deleteMessage(message.id)
        if (networkStatus.value.isOnline) {
            activeCloudRepoIfAuthenticated()?.deleteMessage(message.id)
        }
    }

    suspend fun toggleReaction(message: MessageEntity, emoji: String) {
        val prefs = prefsRepo.userPrefs.first()
        val json = runCatching { JSONObject(message.reactionsJson) }.getOrElse { JSONObject() }
        val currentUserKey = prefs.userId.ifBlank { "me" }
        val existingEmoji = json.optString(currentUserKey, "")
        if (existingEmoji == emoji) {
            json.remove(currentUserKey)
        } else {
            json.put(currentUserKey, emoji)
        }
        val updated = message.copy(reactionsJson = json.toString())
        messageDao.updateMessage(updated)
        if (networkStatus.value.isOnline) {
            activeCloudRepoIfAuthenticated()?.upsertMessage(updated)
        }
    }

    suspend fun updateMessageStatus(messageId: String, status: ReadStatus) {
        val now = System.currentTimeMillis()
        messageDao.updateReadStatus(
            id = messageId,
            status = status,
            deliveredAt = if (status == ReadStatus.DELIVERED || status == ReadStatus.READ) now else null,
            readAt = if (status == ReadStatus.READ) now else null
        )
    }

    suspend fun markConversationRead(conversationId: String) {
        val prefs = prefsRepo.userPrefs.first()
        conversationDao.clearUnread(conversationId)
        if (prefs.readReceiptsEnabled) {
            messageDao.markConversationMessagesRead(conversationId, prefs.userId, System.currentTimeMillis())
        }
    }

    suspend fun setGlobalWallpaper(wallpaperId: String, customUri: String?) {
        val current = prefsRepo.userPrefs.first().globalWallpaperUri
        val persisted = if (!customUri.isNullOrBlank()) {
            MediaStorageHelper.persistUriToInternalFile(context, customUri, "wallpapers", "global_wallpaper")
        } else {
            MediaStorageHelper.deletePersistedFileIfOwned(context, current)
            null
        }
        prefsRepo.setGlobalWallpaper(wallpaperId, persisted)
    }

    suspend fun setConversationWallpaper(conversationId: String, wallpaperId: String?, customUri: String?) {
        val conv = conversationDao.getConversation(conversationId)
        val persisted = if (!customUri.isNullOrBlank()) {
            MediaStorageHelper.persistUriToInternalFile(context, customUri, "wallpapers", "conv_${conversationId.take(12)}")
        } else {
            MediaStorageHelper.deletePersistedFileIfOwned(context, conv?.customWallpaperUri)
            null
        }
        conversationDao.updateWallpaper(conversationId, wallpaperId, persisted)
    }

    suspend fun blockUser(contactId: String, blocked: Boolean) {
        contactDao.setBlocked(contactId, blocked)
        contactDao.getContactById(contactId)?.let { c ->
            if (networkStatus.value.isOnline) activeCloudRepoIfAuthenticated()?.upsertContact(c)
        }
    }

    suspend fun reportUser(contactId: String) {
        contactDao.markReported(contactId)
        contactDao.setBlocked(contactId, true)
        contactDao.getContactById(contactId)?.let { c ->
            if (networkStatus.value.isOnline) activeCloudRepoIfAuthenticated()?.upsertContact(c)
        }
    }

    // Channels
    suspend fun createChannel(name: String, description: String, avatarUri: String?) {
        val id = "ch_${UUID.randomUUID().toString().take(8)}"
        val channel = ChannelEntity(
            id = id,
            name = name.trim(),
            description = description.trim(),
            avatarUri = avatarUri,
            ownerId = "me",
            isOwnedByMe = true,
            isSubscribed = true,
            subscriberCount = 1
        )
        channelDao.upsertChannel(channel)
        if (networkStatus.value.isOnline) {
            activeCloudRepoIfAuthenticated()?.upsertChannel(channel)
        }
    }

    suspend fun toggleChannelSubscription(channel: ChannelEntity) {
        val next = !channel.isSubscribed
        val delta = if (next) 1 else -1
        channelDao.setSubscription(channel.id, next, delta)
    }

    suspend fun publishChannelPost(channelId: String, content: String, mediaUri: String?, mediaType: MessageType) {
        val postId = UUID.randomUUID().toString()
        channelDao.upsertPost(
            ChannelPostEntity(
                id = postId,
                channelId = channelId,
                content = content.trim(),
                mediaUri = mediaUri,
                mediaType = mediaType
            )
        )
        logAndNotify(
            category = "CHANNEL",
            title = "Nouvelle publication de chaîne",
            body = content.take(80).ifBlank { "Nouveau média publié" },
            channelId = PrunelleNotificationHelper.CHANNEL_UPDATES,
            eventKey = "channel_post_$postId"
        )
    }

    suspend fun deleteChannelPost(postId: String) = channelDao.deletePost(postId)
    suspend fun deleteChannel(channelId: String) = channelDao.deleteChannel(channelId)

    // Statuses
    suspend fun publishStatus(textContent: String, mediaUri: String?, isVideo: Boolean, bgColorHex: Long) {
        val prefs = prefsRepo.userPrefs.first()
        val persisted = if (!mediaUri.isNullOrBlank() && mediaUri.startsWith("content://")) {
            MediaStorageHelper.persistUriToInternalFile(context, mediaUri, "media_cache")
        } else mediaUri
        statusDao.upsertStatus(
            StatusEntity(
                id = UUID.randomUUID().toString(),
                authorId = prefs.userId,
                authorName = prefs.displayName.ifBlank { "Moi" },
                authorAvatarUri = prefs.avatarUri,
                textContent = textContent.trim(),
                mediaUri = persisted,
                isVideo = isVideo,
                backgroundColorHex = bgColorHex
            )
        )
    }

    suspend fun toggleStatusLike(status: StatusEntity) {
        val prefs = prefsRepo.userPrefs.first()
        val meName = prefs.displayName.ifBlank { "Moi" }
        val likers = status.likersCsv.split(",").filter { it.isNotBlank() }.toMutableSet()
        val nextLiked = !status.isLikedByMe
        if (nextLiked) likers.add(meName) else likers.remove(meName)
        val nextCount = if (nextLiked) status.likesCount + 1 else (status.likesCount - 1).coerceAtLeast(0)
        statusDao.upsertStatus(
            status.copy(
                isLikedByMe = nextLiked,
                likesCount = nextCount,
                likersCsv = likers.joinToString(",")
            )
        )
        if (nextLiked) {
            logAndNotify(
                category = "STATUS_LIKE",
                title = "J'aime sur un statut",
                body = "$meName a aimé le statut de ${status.authorName}",
                channelId = PrunelleNotificationHelper.CHANNEL_UPDATES,
                eventKey = "status_like_${status.id}_$meName"
            )
        }
    }

    suspend fun recordStatusView(status: StatusEntity) {
        val prefs = prefsRepo.userPrefs.first()
        val meName = prefs.displayName.ifBlank { "Moi" }
        val viewers = status.viewersCsv.split(",").filter { it.isNotBlank() }.toMutableSet()
        if (viewers.add(meName)) {
            statusDao.upsertStatus(status.copy(viewersCsv = viewers.joinToString(",")))
        }
    }

    suspend fun deleteStatus(statusId: String) = statusDao.deleteStatus(statusId)

    // Stickers
    suspend fun createCustomSticker(
        packName: String,
        label: String,
        imageUri: String?,
        overlayText: String,
        emojiFallback: String,
        isAnimated: Boolean
    ) {
        val persisted = if (!imageUri.isNullOrBlank() && imageUri.startsWith("content://")) {
            MediaStorageHelper.persistUriToInternalFile(context, imageUri, "stickers")
        } else imageUri

        val packId = "pack_personal_${packName.trim().lowercase().replace(" ", "_")}"
        stickerDao.upsertPack(
            StickerPackEntity(
                id = packId,
                name = packName.trim().ifBlank { "Mes Stickers" },
                author = "Moi",
                isPersonal = true,
                category = if (isAnimated) "Animés" else "Personnalisés"
            )
        )
        stickerDao.upsertSticker(
            StickerEntity(
                id = UUID.randomUUID().toString(),
                packId = packId,
                label = label.trim().ifBlank { "Sticker" },
                emojiFallback = emojiFallback.ifBlank { "✨" },
                imageUri = persisted,
                overlayText = overlayText.trim(),
                isAnimated = isAnimated
            )
        )
    }

    suspend fun toggleStickerFavorite(sticker: StickerEntity) =
        stickerDao.setFavorite(sticker.id, !sticker.isFavorite)

    // Calls
    suspend fun recordCall(peerName: String, peerId: String, isVideo: Boolean, isGroup: Boolean, durationSec: Int) {
        callDao.insertCall(
            CallRecordEntity(
                id = UUID.randomUUID().toString(),
                peerName = peerName,
                peerId = peerId,
                isVideo = isVideo,
                isGroup = isGroup,
                durationSeconds = durationSec
            )
        )
    }

    suspend fun clearCallHistory() = callDao.clearHistory()

    // AI (Streaming chat + Creations + TikTok AI)
    suspend fun sendAiPromptStream(
        prompt: String,
        mode: AiChatMode,
        onPartialUpdate: (String) -> Unit
    ) {
        val modeKey = mode.name
        aiDao.insertAiMessage(
            AiMessageEntity(
                id = UUID.randomUUID().toString(),
                mode = modeKey,
                isFromUser = true,
                content = prompt.trim()
            )
        )
        val prefs = prefsRepo.userPrefs.first()
        val memories = if (prefs.aiMemoryEnabled) {
            aiDao.observeMemories().first().map { "${it.keyLabel}: ${it.valueText}" }
        } else emptyList()

        val msgId = UUID.randomUUID().toString()
        val finalText = StringBuilder()
        aiRepository.askAssistantStream(prompt, mode, memories).collect { accumulated ->
            finalText.clear()
            finalText.append(accumulated)
            onPartialUpdate(accumulated)
            aiDao.insertAiMessage(
                AiMessageEntity(
                    id = msgId,
                    mode = modeKey,
                    isFromUser = false,
                    content = accumulated,
                    isPlaceholderNotice = !aiRepository.isCloudAiEnabled
                )
            )
        }
    }

    suspend fun clearAiHistory(mode: AiChatMode) = aiDao.clearAiMessages(mode.name)
    suspend fun addAiMemory(keyLabel: String, valueText: String) {
        aiDao.insertMemory(
            AiMemoryEntity(
                id = UUID.randomUUID().toString(),
                keyLabel = keyLabel.trim(),
                valueText = valueText.trim()
            )
        )
    }
    suspend fun deleteAiMemory(id: String) = aiDao.deleteMemory(id)
    suspend fun clearAllAiMemories() = aiDao.clearAllMemories()

    suspend fun saveAiCreation(creation: AiCreationEntity) = aiDao.insertCreation(creation)
    suspend fun toggleCreationFavorite(creation: AiCreationEntity) =
        aiDao.toggleCreationFavorite(creation.id, !creation.isFavorite)
    suspend fun deleteAiCreation(id: String) = aiDao.deleteCreation(id)

    suspend fun analyzeAndSaveTikTokVideo(
        context: Context,
        tiktokUrl: String?,
        localVideoUri: String?
    ): TikTokAnalysisEntity {
        val report = tikTokAnalysisRepository.analyzeAndSave(context, tiktokUrl, localVideoUri)
        if (networkStatus.value.isOnline) {
            activeCloudRepoIfAuthenticated()?.saveTikTokAnalysis(report)
        }
        return report
    }

    suspend fun deleteTikTokAnalysis(id: String) = tikTokAnalysisRepository.deleteReport(id)
    suspend fun askTikTokFollowUp(analysis: TikTokAnalysisEntity, question: String) {
        tikTokAnalysisRepository.askFollowUp(analysis, question)
    }

    // Birthdays Engine (timezone-aware, once-per-year card generation & notification dedup)
    suspend fun checkSelfBirthdayToday(): Boolean {
        val prefs = prefsRepo.userPrefs.first()
        if (!prefs.isLoggedIn) return false
        val now = BirthdayHelper.nowInZone(prefs.timezoneId)
        return BirthdayHelper.isBirthdayToday(prefs.birthDay, prefs.birthMonth, prefs.timezoneId, now)
    }

    suspend fun ensureSelfBirthdayCelebrationPrepared(): Result<String?> {
        val prefs = prefsRepo.userPrefs.first()
        if (!prefs.isLoggedIn) return Result.success(null)
        val now = BirthdayHelper.nowInZone(prefs.timezoneId)
        val year = now.year
        if (!BirthdayHelper.isBirthdayToday(prefs.birthDay, prefs.birthMonth, prefs.timezoneId, now)) {
            return Result.success(null)
        }

        if (prefs.notifyBirthdays && prefs.lastSelfBirthdayNotifiedYear < year) {
            logAndNotify(
                category = "BIRTHDAY",
                title = "🎂 Joyeux anniversaire ${prefs.displayName.ifBlank { "à toi" }} !",
                body = BirthdayHelper.SELF_BIRTHDAY_MESSAGE,
                channelId = PrunelleNotificationHelper.CHANNEL_BIRTHDAYS,
                eventKey = "bday_self_$year",
                openBirthday = true
            )
            prefsRepo.markSelfBirthdayNotified(year)
        }

        val cached = prefs.cachedBirthdayCardPath
        if (prefs.lastBirthdayCardYear == year && !cached.isNullOrBlank() && File(cached).exists()) {
            return Result.success(cached)
        }

        if (!networkStatus.value.isOnline) {
            return Result.failure(IllegalStateException("Hors ligne : la carte d'anniversaire IA sera générée dès le retour d'Internet."))
        }

        return try {
            val avatarBmp = MediaStorageHelper.loadBitmapFromUriOrPath(context, prefs.avatarUri, maxDim = 768)
            val savedPath = aiRepository.generateBirthdayPortrait(
                context = context,
                displayName = prefs.displayName.ifBlank { "Prunelle" },
                avatarBitmap = avatarBmp,
                year = year
            )
            prefsRepo.saveBirthdayCardGenerated(year, savedPath)
            Result.success(savedPath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("PrunelleBirthday", "Birthday card AI generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun findContactsCelebratingBirthdayToday(): List<BirthdayContactMatch> {
        val prefs = prefsRepo.userPrefs.first()
        val now = BirthdayHelper.nowInZone(prefs.timezoneId)
        val year = now.year
        val contacts = contactDao.getContactsWithBirthdays()
        return contacts.mapNotNull { contact ->
            if (!BirthdayHelper.canNotifyContactBirthday(contact)) return@mapNotNull null
            if (!BirthdayHelper.isBirthdayToday(contact.birthDay, contact.birthMonth, prefs.timezoneId, now)) {
                return@mapNotNull null
            }
            if (prefs.notifyBirthdays && contact.lastBirthdayNotifiedYear < year) {
                logAndNotify(
                    category = "BIRTHDAY",
                    title = "🎉 Anniversaire de ${contact.displayName}",
                    body = "C'est l'anniversaire de ${contact.displayName} aujourd'hui !",
                    channelId = PrunelleNotificationHelper.CHANNEL_BIRTHDAYS,
                    eventKey = "bday_contact_${contact.id}_$year"
                )
                contactDao.markBirthdayNotifiedYear(contact.id, year)
            }
            BirthdayContactMatch(
                contact = contact,
                year = year,
                alreadyGreetedThisYear = contact.birthdayGreetingSentYear == year
            )
        }
    }

    suspend fun sendBirthdayGreetingToContact(contact: ContactEntity, customText: String? = null): String {
        val now = BirthdayHelper.nowInZone(prefsRepo.userPrefs.first().timezoneId)
        val convId = startOrGetDirectConversation(contact)
        val messageText = customText?.trim().orEmpty().ifBlank {
            BirthdayHelper.buildContactGreetingSuggestion(contact.displayName)
        }
        sendMessage(conversationId = convId, content = messageText, type = MessageType.TEXT)
        contactDao.markBirthdayGreetingSentYear(contact.id, now.year)
        return convId
    }

    suspend fun logAndNotify(
        category: String,
        title: String,
        body: String,
        channelId: String = PrunelleNotificationHelper.CHANNEL_MESSAGES,
        eventKey: String? = null,
        conversationId: String? = null,
        routeTab: String? = null,
        openBirthday: Boolean = false
    ) {
        if (!eventKey.isNullOrBlank() && notificationLogDao.countByEventKey(eventKey) > 0) {
            return
        }
        val prefs = prefsRepo.userPrefs.first()
        val enabled = when (category) {
            "MESSAGE" -> prefs.notifyMessages
            "GROUP" -> prefs.notifyGroups
            "CALL" -> prefs.notifyCalls
            "REACTION" -> prefs.notifyReactions
            "CHANNEL" -> prefs.notifyChannels
            "STATUS_LIKE" -> prefs.notifyStatusLikes
            "BIRTHDAY" -> prefs.notifyBirthdays
            else -> true
        }
        notificationLogDao.insertLog(
            NotificationLogEntity(
                id = UUID.randomUUID().toString(),
                category = category,
                title = title,
                body = body,
                eventKey = eventKey
            )
        )
        if (enabled) {
            PrunelleNotificationHelper.showNotification(
                context = context,
                channelId = channelId,
                notificationId = (eventKey ?: UUID.randomUUID().toString()).hashCode(),
                title = title,
                body = body,
                conversationId = conversationId,
                routeTab = routeTab,
                openBirthday = openBirthday,
                isIncomingCall = category == "CALL"
            )
        }
    }

    suspend fun markAllNotificationsRead() = notificationLogDao.markAllRead()
    suspend fun clearNotifications() = notificationLogDao.clearLogs()
}
