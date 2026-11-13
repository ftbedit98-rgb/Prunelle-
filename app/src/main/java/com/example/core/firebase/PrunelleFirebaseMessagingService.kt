package com.example.core.firebase

import android.content.Context
import android.util.Log
import com.example.core.notifications.PrunelleNotificationHelper
import com.example.core.worker.WorkSyncScheduler
import com.example.data.local.*
import com.example.data.repository.FirestoreCloudRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PrunelleFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM registration token received")
        serviceScope.launch {
            FcmMessageProcessor.persistAndSyncToken(applicationContext, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        serviceScope.launch {
            FcmMessageProcessor.processIncomingPush(
                context = applicationContext,
                data = remoteMessage.data,
                notificationTitle = remoteMessage.notification?.title,
                notificationBody = remoteMessage.notification?.body,
                remoteMessageId = remoteMessage.messageId
            )
        }
    }

    private companion object {
        const val TAG = "PrunelleFcmService"
    }
}

object FcmMessageProcessor {
    private const val TAG = "FcmMessageProcessor"

    suspend fun syncRegistrationToken(context: Context) {
        val token = runCatching {
            FirebaseMessaging.getInstance().token.await()
        }.onFailure { err ->
            Log.w(TAG, "Unable to fetch FCM token (expected in Robolectric / non-Play devices): ${err.message}")
        }.getOrNull()

        if (!token.isNullOrBlank()) {
            persistAndSyncToken(context, token)
        }
    }

    suspend fun persistAndSyncToken(context: Context, token: String) {
        val prefsRepo = UserPreferencesRepository(context)
        prefsRepo.saveFcmToken(token)
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull() ?: return
        val readReceipts = prefsRepo.userPrefs.first().readReceiptsEnabled
        runCatching {
            val cloudRepo = FirestoreCloudRepository(context)
            cloudRepo.updateFcmToken(token = token, readReceiptsDefault = readReceipts)
        }.onFailure { err ->
            Log.w(TAG, "Failed syncing FCM token to Firestore for uid=$uid: ${err.message}")
        }
    }

    suspend fun processIncomingPush(
        context: Context,
        data: Map<String, String>,
        notificationTitle: String? = null,
        notificationBody: String? = null,
        remoteMessageId: String? = null,
        dbOverride: PrunelleDatabase? = null
    ) {
        val db = dbOverride ?: PrunelleDatabase.getInstance(context)
        val prefsRepo = UserPreferencesRepository(context)
        val prefs = prefsRepo.userPrefs.first()
        val notifDao = db.notificationLogDao()

        val rawType = data["type"].orEmpty().trim().lowercase()

        if (rawType == "sync_pending") {
            WorkSyncScheduler.enqueuePendingSync(context)
            return
        }

        val conversationId = data["conversationId"]?.takeIf { it.isNotBlank() }
        val senderId = data["senderId"]?.takeIf { it.isNotBlank() } ?: "remote_peer"
        val senderName = data["senderName"]?.takeIf { it.isNotBlank() } ?: "Contact Prunelle"
        val messageId = data["messageId"]?.takeIf { it.isNotBlank() }
        val isGroup = data["isGroup"]?.equals("true", ignoreCase = true) == true
        val isVideoCall = data["isVideo"]?.equals("true", ignoreCase = true) == true
        val bodyText = data["body"]?.takeIf { it.isNotBlank() }
            ?: notificationBody?.takeIf { it.isNotBlank() }
            ?: data["content"]?.takeIf { it.isNotBlank() }
            ?: ""

        val eventKey = data["eventKey"]?.takeIf { it.isNotBlank() }
            ?: remoteMessageId?.takeIf { it.isNotBlank() }
            ?: messageId?.let { "fcm_msg_$it" }
            ?: data["callId"]?.let { "fcm_call_$it" }
            ?: data["statusId"]?.let { "fcm_status_$it" }

        if (!eventKey.isNullOrBlank() && notifDao.countByEventKey(eventKey) > 0) {
            return
        }

        val (category, channelId, enabled, title, resolvedBody, routeTab, openBirthday, isCall) = when (rawType) {
            "incoming_call", "call" -> {
                val callTitle = data["title"]?.takeIf { it.isNotBlank() }
                    ?: notificationTitle?.takeIf { it.isNotBlank() }
                    ?: if (isVideoCall) "📹 Appel vidéo entrant" else "📞 Appel audio entrant"
                val callBody = bodyText.ifBlank { "$senderName vous appelle sur Prunelle" }
                recordIncomingCallIfNeeded(db, senderName, senderId, isVideoCall, isGroup)
                PushNotificationSpec(
                    category = "CALL",
                    channelId = PrunelleNotificationHelper.CHANNEL_CALLS,
                    enabled = prefs.notifyCalls,
                    title = callTitle,
                    body = callBody,
                    routeTab = PrunelleNotificationHelper.TAB_CALLS,
                    openBirthday = false,
                    isIncomingCall = true
                )
            }

            "status_update", "status", "status_like" -> {
                val isLike = rawType == "status_like"
                val statusTitle = data["title"]?.takeIf { it.isNotBlank() }
                    ?: notificationTitle?.takeIf { it.isNotBlank() }
                    ?: if (isLike) "❤️ Réaction à votre statut" else "✨ Nouveau statut de $senderName"
                val statusBody = bodyText.ifBlank {
                    if (isLike) "$senderName a aimé votre statut" else "Découvrez le nouveau statut 24h de $senderName"
                }
                persistIncomingStatusIfPresent(db, data, senderId, senderName, bodyText)
                PushNotificationSpec(
                    category = "STATUS_LIKE",
                    channelId = PrunelleNotificationHelper.CHANNEL_UPDATES,
                    enabled = prefs.notifyStatusLikes,
                    title = statusTitle,
                    body = statusBody,
                    routeTab = PrunelleNotificationHelper.TAB_UPDATES,
                    openBirthday = false,
                    isIncomingCall = false
                )
            }

            "channel_post", "channel" -> {
                val chTitle = data["title"]?.takeIf { it.isNotBlank() }
                    ?: notificationTitle?.takeIf { it.isNotBlank() }
                    ?: "📢 ${data["channelName"] ?: "Chaîne Prunelle"}"
                val chBody = bodyText.ifBlank { "Nouvelle publication disponible" }
                PushNotificationSpec(
                    category = "CHANNEL",
                    channelId = PrunelleNotificationHelper.CHANNEL_UPDATES,
                    enabled = prefs.notifyChannels,
                    title = chTitle,
                    body = chBody,
                    routeTab = PrunelleNotificationHelper.TAB_UPDATES,
                    openBirthday = false,
                    isIncomingCall = false
                )
            }

            "birthday" -> {
                val bTitle = data["title"]?.takeIf { it.isNotBlank() }
                    ?: notificationTitle?.takeIf { it.isNotBlank() }
                    ?: "🎂 Joyeux anniversaire !"
                val bBody = bodyText.ifBlank { "Une surprise d'anniversaire Prunelle vous attend." }
                PushNotificationSpec(
                    category = "BIRTHDAY",
                    channelId = PrunelleNotificationHelper.CHANNEL_BIRTHDAYS,
                    enabled = prefs.notifyBirthdays,
                    title = bTitle,
                    body = bBody,
                    routeTab = PrunelleNotificationHelper.TAB_HOME,
                    openBirthday = true,
                    isIncomingCall = false
                )
            }

            "reaction" -> {
                val reactTitle = data["title"]?.takeIf { it.isNotBlank() }
                    ?: notificationTitle?.takeIf { it.isNotBlank() }
                    ?: senderName
                val reactBody = bodyText.ifBlank { "$senderName a réagi à votre message" }
                PushNotificationSpec(
                    category = "REACTION",
                    channelId = PrunelleNotificationHelper.CHANNEL_MESSAGES,
                    enabled = prefs.notifyReactions,
                    title = reactTitle,
                    body = reactBody,
                    routeTab = if (conversationId == null) PrunelleNotificationHelper.TAB_HOME else null,
                    openBirthday = false,
                    isIncomingCall = false
                )
            }

            "group_message" -> {
                val grpTitle = data["title"]?.takeIf { it.isNotBlank() }
                    ?: notificationTitle?.takeIf { it.isNotBlank() }
                    ?: data["groupName"]?.takeIf { it.isNotBlank() }
                    ?: senderName
                val grpBody = bodyText.ifBlank { "$senderName a envoyé un message dans le groupe" }
                persistIncomingMessageIfPresent(db, conversationId, messageId, senderId, senderName, grpBody, isGroup = true)
                PushNotificationSpec(
                    category = "GROUP",
                    channelId = PrunelleNotificationHelper.CHANNEL_MESSAGES,
                    enabled = prefs.notifyGroups,
                    title = grpTitle,
                    body = grpBody,
                    routeTab = if (conversationId == null) PrunelleNotificationHelper.TAB_HOME else null,
                    openBirthday = false,
                    isIncomingCall = false
                )
            }

            else -> {
                // Default: new_message or notification-only FCM payload
                val msgCategory = if (isGroup) "GROUP" else "MESSAGE"
                val isEnabled = if (isGroup) prefs.notifyGroups else prefs.notifyMessages
                val msgTitle = data["title"]?.takeIf { it.isNotBlank() }
                    ?: notificationTitle?.takeIf { it.isNotBlank() }
                    ?: senderName
                val msgBody = bodyText.ifBlank { "Nouveau message reçu" }
                persistIncomingMessageIfPresent(db, conversationId, messageId, senderId, senderName, msgBody, isGroup = isGroup)
                PushNotificationSpec(
                    category = msgCategory,
                    channelId = PrunelleNotificationHelper.CHANNEL_MESSAGES,
                    enabled = isEnabled,
                    title = msgTitle,
                    body = msgBody,
                    routeTab = if (conversationId == null) PrunelleNotificationHelper.TAB_HOME else null,
                    openBirthday = false,
                    isIncomingCall = false
                )
            }
        }

        notifDao.insertLog(
            NotificationLogEntity(
                id = UUID.randomUUID().toString(),
                category = category,
                title = title,
                body = resolvedBody,
                eventKey = eventKey
            )
        )

        if (enabled) {
            val notifId = (eventKey ?: "${category}_${conversationId ?: ""}_$title").hashCode()
            PrunelleNotificationHelper.showNotification(
                context = context,
                channelId = channelId,
                notificationId = notifId,
                title = title,
                body = resolvedBody,
                conversationId = conversationId,
                routeTab = routeTab,
                openBirthday = openBirthday,
                isIncomingCall = isCall
            )
        }
    }

    private suspend fun persistIncomingMessageIfPresent(
        db: PrunelleDatabase,
        conversationId: String?,
        messageId: String?,
        senderId: String,
        senderName: String,
        content: String,
        isGroup: Boolean
    ) {
        if (conversationId.isNullOrBlank() || content.isBlank()) return
        val convDao = db.conversationDao()
        val msgDao = db.messageDao()
        val now = System.currentTimeMillis()
        val existingConv = convDao.getConversation(conversationId)
        val updatedConv = existingConv?.copy(
            lastMessagePreview = content.take(120),
            lastMessageTime = now,
            unreadCount = existingConv.unreadCount + 1,
            lastMessageStatus = ReadStatus.DELIVERED
        ) ?: ConversationEntity(
            id = conversationId,
            title = senderName,
            isGroup = isGroup,
            participantIdsCsv = "me,$senderId",
            lastMessagePreview = content.take(120),
            lastMessageTime = now,
            unreadCount = 1,
            isOnline = true,
            lastMessageStatus = ReadStatus.DELIVERED
        )
        convDao.upsertConversation(updatedConv)
        msgDao.insertMessage(
            MessageEntity(
                id = messageId ?: UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderId = senderId,
                senderName = senderName,
                content = content,
                type = MessageType.TEXT,
                readStatus = ReadStatus.DELIVERED,
                sentAt = now,
                deliveredAt = now
            )
        )
    }

    private suspend fun recordIncomingCallIfNeeded(
        db: PrunelleDatabase,
        peerName: String,
        peerId: String,
        isVideo: Boolean,
        isGroup: Boolean
    ) {
        db.callDao().insertCall(
            CallRecordEntity(
                id = UUID.randomUUID().toString(),
                peerName = peerName,
                peerId = peerId,
                isVideo = isVideo,
                isGroup = isGroup,
                isIncoming = true,
                wasMissed = false,
                durationSeconds = 0
            )
        )
    }

    private suspend fun persistIncomingStatusIfPresent(
        db: PrunelleDatabase,
        data: Map<String, String>,
        authorId: String,
        authorName: String,
        textContent: String
    ) {
        val statusId = data["statusId"]?.takeIf { it.isNotBlank() } ?: return
        if (textContent.isBlank()) return
        val now = System.currentTimeMillis()
        db.statusDao().upsertStatus(
            StatusEntity(
                id = statusId,
                authorId = authorId,
                authorName = authorName,
                textContent = textContent.take(300),
                isVideo = false,
                createdAt = now,
                expiresAt = now + 24 * 60 * 60 * 1000L
            )
        )
    }

    private data class PushNotificationSpec(
        val category: String,
        val channelId: String,
        val enabled: Boolean,
        val title: String,
        val body: String,
        val routeTab: String?,
        val openBirthday: Boolean,
        val isIncomingCall: Boolean
    )
}
