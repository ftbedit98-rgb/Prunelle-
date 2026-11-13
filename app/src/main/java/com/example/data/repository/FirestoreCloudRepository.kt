package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.*
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String? = null,
    val statusText: String? = null,
    val avatarUri: String? = null,
    val birthDay: Long? = null,
    val birthMonth: Long? = null,
    val birthdayPrivacy: String = PrivacyAudience.CONTACTS.name,
    val timezoneId: String = "Europe/Paris",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudPrivateAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val countryCode: String? = null,
    val phoneMasked: String? = null,
    val email: String? = null,
    val birthYear: Long? = null,
    val readReceiptsEnabled: Boolean = true,
    val fcmToken: String? = null,
    val fcmTokenUpdatedAtMs: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudContact(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String? = null,
    val statusText: String? = null,
    val isBlocked: Boolean = false,
    val isReported: Boolean = false,
    val birthDay: Long? = null,
    val birthMonth: Long? = null,
    val birthdayPrivacy: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudConversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val isGroup: Boolean = false,
    val description: String? = null,
    val participantIdsCsv: String? = null,
    val adminIdsCsv: String? = null,
    val lastMessagePreview: String? = null,
    val lastMessageTimeMs: Long = 0L,
    val unreadCount: Long = 0L,
    val lastMessageStatus: String = ReadStatus.SENT.name,
    val customWallpaperId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val content: String = "",
    val messageType: String = MessageType.TEXT.name,
    val mediaFileName: String? = null,
    val mediaDurationMs: Long? = null,
    val readStatus: String = ReadStatus.SENT.name,
    val sentAtMs: Long = 0L,
    val isEdited: Boolean? = null,
    val isForwarded: Boolean? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudChannel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val isOwnedByMe: Boolean = false,
    val isSubscribed: Boolean = true,
    val subscriberCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudTikTokAnalysis(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceLabel: String = "",
    val sourceUrl: String? = null,
    val durationFormatted: String? = null,
    val resolutionFormatted: String? = null,
    val overallScore: Long = 0L,
    val hookScore: Long = 0L,
    val rhythmScore: Long = 0L,
    val visualsScore: Long = 0L,
    val summary: String = "",
    val hashtagsCsv: String? = null,
    val pacingCurveCsv: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class FirestoreCloudRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("L'utilisateur doit être connecté avec Google pour accéder à Firestore.")

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    // --- Profile & PII ---

    suspend fun saveUserProfileAndPrivateAccount(
        displayName: String,
        username: String,
        bio: String,
        statusText: String,
        avatarUri: String?,
        birthDay: Int?,
        birthMonth: Int?,
        birthYear: Int?,
        birthdayPrivacy: PrivacyAudience,
        timezoneId: String,
        countryCode: String,
        phoneMasked: String,
        email: String?,
        readReceiptsEnabled: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val profileRef = userDoc(uid)
        val existingSnap = profileRef.get().await()
        val profilePayload = buildMap<String, Any> {
            put("userId", uid)
            put("displayName", displayName.trim().take(80).ifBlank { "Utilisateur Prunelle" })
            put("username", username.trim().lowercase().removePrefix("@").take(40).ifBlank { "user_${uid.take(6)}" })
            if (bio.isNotBlank()) put("bio", bio.trim().take(240))
            if (statusText.isNotBlank()) put("statusText", statusText.trim().take(120))
            if (!avatarUri.isNullOrBlank() && avatarUri.length <= 500) put("avatarUri", avatarUri)
            if (birthDay != null && birthDay in 1..31) put("birthDay", birthDay.toLong())
            if (birthMonth != null && birthMonth in 1..12) put("birthMonth", birthMonth.toLong())
            put("birthdayPrivacy", birthdayPrivacy.name)
            put("timezoneId", timezoneId.take(64).ifBlank { "Europe/Paris" })
            put("updatedAt", FieldValue.serverTimestamp())
            if (!existingSnap.exists()) {
                put("createdAt", FieldValue.serverTimestamp())
            }
        }
        try {
            if (existingSnap.exists()) {
                profileRef.update(profilePayload).await()
            } else {
                profileRef.set(profilePayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingSnap.exists()) OperationType.UPDATE else OperationType.CREATE, profileRef.path)
            throw e
        }

        val privateRef = profileRef.collection("private").document("account")
        val existingPriv = privateRef.get().await()
        val privPayload = buildMap<String, Any> {
            put("userId", uid)
            if (countryCode.isNotBlank()) put("countryCode", countryCode.take(12))
            if (phoneMasked.isNotBlank()) put("phoneMasked", phoneMasked.take(32))
            if (!email.isNullOrBlank()) put("email", email.take(160))
            if (birthYear != null && birthYear in 1900..2100) put("birthYear", birthYear.toLong())
            put("readReceiptsEnabled", readReceiptsEnabled)
            put("updatedAt", FieldValue.serverTimestamp())
            if (!existingPriv.exists()) {
                put("createdAt", FieldValue.serverTimestamp())
            }
        }
        try {
            if (existingPriv.exists()) {
                privateRef.update(privPayload).await()
            } else {
                privateRef.set(privPayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingPriv.exists()) OperationType.UPDATE else OperationType.CREATE, privateRef.path)
            throw e
        }
    }

    suspend fun getUserProfile(): Result<CloudUserProfile?> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        try {
            docRef.get().await().toObject(CloudUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun getPrivateAccount(): Result<CloudPrivateAccount?> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("private").document("account")
        try {
            docRef.get().await().toObject(CloudPrivateAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun updateFcmToken(token: String, readReceiptsDefault: Boolean = true): Result<Unit> = runCatching {
        val cleanToken = token.trim().take(512)
        require(cleanToken.isNotBlank()) { "FCM token cannot be blank" }
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("private").document("account")
        val existingPriv = docRef.get().await()
        try {
            if (existingPriv.exists()) {
                docRef.update(
                    mapOf(
                        "fcmToken" to cleanToken,
                        "fcmTokenUpdatedAtMs" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "readReceiptsEnabled" to readReceiptsDefault,
                        "fcmToken" to cleanToken,
                        "fcmTokenUpdatedAtMs" to System.currentTimeMillis(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingPriv.exists()) OperationType.UPDATE else OperationType.CREATE,
                docRef.path
            )
            throw e
        }
    }

    // --- Contacts ---

    suspend fun upsertContact(contact: ContactEntity): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("contacts").document(contact.id)
        val existing = docRef.get().await()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("displayName", contact.displayName.take(80).ifBlank { "Contact" })
            put("username", contact.username.take(40).ifBlank { "contact" })
            if (contact.bio.isNotBlank()) put("bio", contact.bio.take(240))
            if (contact.statusText.isNotBlank()) put("statusText", contact.statusText.take(120))
            put("isBlocked", contact.isBlocked)
            put("isReported", contact.isReported)
            contact.birthDay?.takeIf { it in 1..31 }?.let { put("birthDay", it.toLong()) }
            contact.birthMonth?.takeIf { it in 1..12 }?.let { put("birthMonth", it.toLong()) }
            put("birthdayPrivacy", contact.birthdayPrivacy.name)
            put("updatedAt", FieldValue.serverTimestamp())
            if (!existing.exists()) {
                put("createdAt", FieldValue.serverTimestamp())
            }
        }
        try {
            if (existing.exists()) docRef.update(payload).await() else docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existing.exists()) OperationType.UPDATE else OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeContacts(userId: String): Flow<List<CloudContact>> = flow {
        val col = userDoc(userId).collection("contacts")
        emitAll(
            col.snapshots()
                .map { snap -> snap.toObjects(CloudContact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, col.path)
                    }
                    throw error
                }
        )
    }

    // --- Conversations ---

    suspend fun upsertConversation(conv: ConversationEntity): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("conversations").document(conv.id)
        val existing = docRef.get().await()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("title", conv.title.take(120).ifBlank { "Discussion" })
            put("isGroup", conv.isGroup)
            if (conv.description.isNotBlank()) put("description", conv.description.take(300))
            if (conv.participantIdsCsv.isNotBlank()) put("participantIdsCsv", conv.participantIdsCsv.take(1000))
            if (conv.adminIdsCsv.isNotBlank()) put("adminIdsCsv", conv.adminIdsCsv.take(500))
            if (conv.lastMessagePreview.isNotBlank()) put("lastMessagePreview", conv.lastMessagePreview.take(300))
            put("lastMessageTimeMs", conv.lastMessageTime.coerceAtLeast(0L))
            put("unreadCount", conv.unreadCount.coerceIn(0, 100000).toLong())
            put("lastMessageStatus", conv.lastMessageStatus.name)
            conv.customWallpaperId?.takeIf { it.isNotBlank() }?.let { put("customWallpaperId", it.take(80)) }
            put("updatedAt", FieldValue.serverTimestamp())
            if (!existing.exists()) {
                put("createdAt", FieldValue.serverTimestamp())
            }
        }
        try {
            if (existing.exists()) docRef.update(payload).await() else docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existing.exists()) OperationType.UPDATE else OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("conversations").document(conversationId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Messages ---

    suspend fun upsertMessage(msg: MessageEntity, statusOverride: ReadStatus? = null): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("messages").document(msg.id)
        val existing = docRef.get().await()
        val effectiveStatus = statusOverride ?: msg.readStatus
        try {
            if (existing.exists()) {
                val updatePayload = mapOf(
                    "content" to msg.content.take(4000),
                    "readStatus" to effectiveStatus.name,
                    "isEdited" to msg.isEdited,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = buildMap<String, Any> {
                    put("userId", uid)
                    put("conversationId", msg.conversationId)
                    put("senderId", msg.senderId.ifBlank { uid }.take(128))
                    put("senderName", msg.senderName.ifBlank { "Moi" }.take(80))
                    put("content", msg.content.take(4000))
                    put("messageType", msg.type.name)
                    msg.mediaFileName?.takeIf { it.isNotBlank() }?.let { put("mediaFileName", it.take(200)) }
                    if (msg.mediaDurationMs >= 0L) put("mediaDurationMs", msg.mediaDurationMs)
                    put("readStatus", effectiveStatus.name)
                    put("sentAtMs", msg.sentAt.coerceAtLeast(0L))
                    put("isEdited", msg.isEdited)
                    put("isForwarded", msg.isForwarded)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(createPayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existing.exists()) OperationType.UPDATE else OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeMessages(userId: String): Flow<List<CloudMessage>> = flow {
        val col = userDoc(userId).collection("messages")
        emitAll(
            col.snapshots()
                .map { snap -> snap.toObjects(CloudMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, col.path)
                    }
                    throw error
                }
        )
    }

    suspend fun getMessageById(messageId: String): Result<CloudMessage?> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("messages").document(messageId)
        try {
            docRef.get().await().toObject(CloudMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun deleteMessage(messageId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("messages").document(messageId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Channels ---

    suspend fun upsertChannel(channel: ChannelEntity): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("channels").document(channel.id)
        val existing = docRef.get().await()
        try {
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "name" to channel.name.take(100).ifBlank { "Chaîne" },
                        "description" to channel.description.take(500),
                        "isSubscribed" to channel.isSubscribed,
                        "subscriberCount" to channel.subscriberCount.coerceAtLeast(0).toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to channel.name.take(100).ifBlank { "Chaîne" },
                        "description" to channel.description.take(500),
                        "isOwnedByMe" to channel.isOwnedByMe,
                        "isSubscribed" to channel.isSubscribed,
                        "subscriberCount" to channel.subscriberCount.coerceAtLeast(0).toLong(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existing.exists()) OperationType.UPDATE else OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // --- TikTok AI Reports ---

    suspend fun saveTikTokAnalysis(report: TikTokAnalysisEntity): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("tiktokAnalyses").document(report.id)
        val existing = docRef.get().await()
        try {
            if (existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("summary", report.summary.take(2000))
                    if (report.hashtagsCsv.isNotBlank()) put("hashtagsCsv", report.hashtagsCsv.take(400))
                    if (report.pacingCurveCsv.isNotBlank()) put("pacingCurveCsv", report.pacingCurveCsv.take(200))
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(payload).await()
            } else {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("sourceLabel", report.sourceLabel.take(160).ifBlank { "Vidéo TikTok" })
                    report.sourceUrl?.takeIf { it.isNotBlank() }?.let { put("sourceUrl", it.take(500)) }
                    if (report.durationFormatted.isNotBlank()) put("durationFormatted", report.durationFormatted.take(40))
                    if (report.resolutionFormatted.isNotBlank()) put("resolutionFormatted", report.resolutionFormatted.take(40))
                    put("overallScore", report.overallScore.coerceIn(0, 100).toLong())
                    put("hookScore", report.hookScore.coerceIn(0, 100).toLong())
                    put("rhythmScore", report.rhythmScore.coerceIn(0, 100).toLong())
                    put("visualsScore", report.visualsScore.coerceIn(0, 100).toLong())
                    put("summary", report.summary.take(2000))
                    if (report.hashtagsCsv.isNotBlank()) put("hashtagsCsv", report.hashtagsCsv.take(400))
                    if (report.pacingCurveCsv.isNotBlank()) put("pacingCurveCsv", report.pacingCurveCsv.take(200))
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existing.exists()) OperationType.UPDATE else OperationType.CREATE, docRef.path)
            throw e
        }
    }
}
