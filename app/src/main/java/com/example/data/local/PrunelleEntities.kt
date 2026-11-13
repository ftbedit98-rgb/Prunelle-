package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageType {
    TEXT, IMAGE, VIDEO, FILE, VOICE, STICKER
}

enum class ReadStatus {
    PENDING,   // Queued locally while offline (clock icon)
    SENT,      // 1 gray check
    DELIVERED, // 2 gray checks
    READ       // 2 blue checks
}

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val username: String,
    val bio: String = "",
    val statusText: String = "Disponible",
    val avatarUri: String? = null,
    val phoneMasked: String = "",
    val isOnline: Boolean = false,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false,
    val isReported: Boolean = false,
    val isAddedContact: Boolean = true,
    val birthDay: Int? = null,
    val birthMonth: Int? = null,
    val birthYear: Int? = null,
    val birthdayPrivacy: PrivacyAudience = PrivacyAudience.CONTACTS,
    val lastBirthdayNotifiedYear: Int = 0,
    val birthdayGreetingSentYear: Int = 0
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val isGroup: Boolean = false,
    val description: String = "",
    val avatarUri: String? = null,
    val participantIdsCsv: String = "",
    val adminIdsCsv: String = "",
    val allowMemberInvites: Boolean = true,
    val allowMemberMedia: Boolean = true,
    val lastMessagePreview: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val lastMessageStatus: ReadStatus = ReadStatus.SENT,
    val customWallpaperId: String? = null,
    val customWallpaperUri: String? = null,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId", "sentAt"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val mediaUri: String? = null,
    val mediaFileName: String? = null,
    val mediaDurationMs: Long = 0L,
    val replyToMessageId: String? = null,
    val replyToPreview: String? = null,
    val isForwarded: Boolean = false,
    val isEdited: Boolean = false,
    val readStatus: ReadStatus = ReadStatus.SENT,
    val sentAt: Long = System.currentTimeMillis(),
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val reactionsJson: String = "{}" // Map<emoji, count/user> serialized as JSON
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val avatarUri: String? = null,
    val ownerId: String,
    val isOwnedByMe: Boolean = false,
    val isSubscribed: Boolean = true,
    val subscriberCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "channel_posts",
    indices = [Index(value = ["channelId", "createdAt"])]
)
data class ChannelPostEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val content: String,
    val mediaUri: String? = null,
    val mediaType: MessageType = MessageType.TEXT,
    val reactionsJson: String = "{}",
    val viewsCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "statuses")
data class StatusEntity(
    @PrimaryKey val id: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUri: String? = null,
    val textContent: String = "",
    val mediaUri: String? = null,
    val isVideo: Boolean = false,
    val backgroundColorHex: Long = 0xFF4F264DL,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000L,
    val viewersCsv: String = "",
    val likersCsv: String = "",
    val isLikedByMe: Boolean = false,
    val likesCount: Int = 0
)

@Entity(tableName = "sticker_packs")
data class StickerPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val author: String,
    val isPersonal: Boolean = false,
    val isDownloaded: Boolean = true,
    val category: String = "Classique",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "stickers",
    indices = [Index(value = ["packId"])]
)
data class StickerEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val label: String,
    val emojiFallback: String,
    val imageUri: String? = null,
    val overlayText: String = "",
    val isAnimated: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey val id: String,
    val peerName: String,
    val peerId: String,
    val isVideo: Boolean,
    val isGroup: Boolean = false,
    val isIncoming: Boolean = false,
    val wasMissed: Boolean = false,
    val durationSeconds: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_messages")
data class AiMessageEntity(
    @PrimaryKey val id: String,
    val mode: String, // "ASSISTANT" or "COMPANION"
    val isFromUser: Boolean,
    val content: String,
    val isPlaceholderNotice: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_memories")
data class AiMemoryEntity(
    @PrimaryKey val id: String,
    val keyLabel: String,
    val valueText: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_creations")
data class AiCreationEntity(
    @PrimaryKey val id: String,
    val creationType: String, // "IMAGE" or "VIDEO"
    val prompt: String,
    val style: String,
    val aspectRatioOrDuration: String,
    val referenceMediaUri: String? = null,
    val generatedImagePath: String? = null,
    val outputPreviewText: String,
    val isPlaceholder: Boolean = true,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tiktok_analyses")
data class TikTokAnalysisEntity(
    @PrimaryKey val id: String,
    val sourceLabel: String,
    val sourceUrl: String?,
    val localVideoUri: String?,
    val durationFormatted: String,
    val resolutionFormatted: String,
    val overallScore: Int,
    val hookScore: Int = 78,
    val rhythmScore: Int = 74,
    val visualsScore: Int = 76,
    val summary: String,
    val hookAnalysis: String,
    val pacingAnalysis: String,
    val visualsAnalysis: String = "",
    val strengthsCsv: String,
    val weaknessesCsv: String,
    val priorityFixesCsv: String,
    val retentionTipsCsv: String,
    val suggestedTitlesCsv: String,
    val suggestedDescriptionsCsv: String,
    val hashtagsCsv: String,
    val pacingCurveCsv: String = "",
    val isPlaceholder: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tiktok_qa",
    indices = [Index(value = ["analysisId", "createdAt"])]
)
data class TikTokQaEntity(
    @PrimaryKey val id: String,
    val analysisId: String,
    val isFromUser: Boolean,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notification_logs")
data class NotificationLogEntity(
    @PrimaryKey val id: String,
    val category: String, // MESSAGE, GROUP, CALL, REACTION, CHANNEL, STATUS_LIKE, BIRTHDAY
    val title: String,
    val body: String,
    val eventKey: String? = null,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
