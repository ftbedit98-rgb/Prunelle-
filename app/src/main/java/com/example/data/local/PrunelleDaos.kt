package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE isBlocked = 0 ORDER BY displayName ASC")
    fun observeActiveContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isBlocked = 1 ORDER BY displayName ASC")
    fun observeBlockedContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE displayName LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%'")
    fun searchContacts(query: String): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContact(contact: ContactEntity)

    @Query("UPDATE contacts SET isBlocked = :blocked WHERE id = :id")
    suspend fun setBlocked(id: String, blocked: Boolean)

    @Query("UPDATE contacts SET isReported = 1 WHERE id = :id")
    suspend fun markReported(id: String)

    @Query("SELECT * FROM contacts WHERE isBlocked = 0 AND birthDay IS NOT NULL AND birthMonth IS NOT NULL")
    suspend fun getContactsWithBirthdays(): List<ContactEntity>

    @Query("UPDATE contacts SET lastBirthdayNotifiedYear = :year WHERE id = :id")
    suspend fun markBirthdayNotifiedYear(id: String, year: Int)

    @Query("UPDATE contacts SET birthdayGreetingSentYear = :year WHERE id = :id")
    suspend fun markBirthdayGreetingSentYear(id: String, year: Int)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContact(id: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, lastMessageTime DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun observeConversation(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversation(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' OR lastMessagePreview LIKE '%' || :query || '%' ORDER BY lastMessageTime DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :id")
    suspend fun clearUnread(id: String)

    @Query("UPDATE conversations SET customWallpaperId = :wallpaperId, customWallpaperUri = :wallpaperUri WHERE id = :id")
    suspend fun updateWallpaper(id: String, wallpaperId: String?, wallpaperUri: String?)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM conversations")
    fun observeTotalUnreadCount(): Flow<Int>
}

@Dao
interface MessageDao {
    // Paginated observation: fetches only the latest `limit` messages (20 per page step)
    @Query("SELECT * FROM (SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sentAt DESC LIMIT :limit) ORDER BY sentAt ASC")
    fun observePagedMessages(conversationId: String, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    fun observeMessageCount(conversationId: String): Flow<Int>

    @Query("SELECT * FROM messages WHERE readStatus = 'PENDING' ORDER BY sentAt ASC")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE readStatus = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY sentAt DESC LIMIT 40")
    fun searchMessages(query: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET readStatus = :status, deliveredAt = COALESCE(deliveredAt, :deliveredAt), readAt = COALESCE(readAt, :readAt) WHERE id = :id")
    suspend fun updateReadStatus(id: String, status: ReadStatus, deliveredAt: Long?, readAt: Long?)

    @Query("UPDATE messages SET readStatus = 'READ', readAt = COALESCE(readAt, :now) WHERE conversationId = :conversationId AND senderId != :currentUserId AND readStatus != 'READ'")
    suspend fun markConversationMessagesRead(conversationId: String, currentUserId: String, now: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY isOwnedByMe DESC, createdAt DESC")
    fun observeChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchChannels(query: String): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannel(channel: ChannelEntity)

    @Query("UPDATE channels SET isSubscribed = :subscribed, subscriberCount = subscriberCount + :delta WHERE id = :channelId")
    suspend fun setSubscription(channelId: String, subscribed: Boolean, delta: Int)

    @Query("SELECT * FROM channel_posts WHERE channelId = :channelId ORDER BY createdAt DESC")
    fun observePosts(channelId: String): Flow<List<ChannelPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPost(post: ChannelPostEntity)

    @Query("DELETE FROM channel_posts WHERE id = :postId")
    suspend fun deletePost(postId: String)

    @Query("DELETE FROM channels WHERE id = :channelId")
    suspend fun deleteChannel(channelId: String)
}

@Dao
interface StatusDao {
    @Query("SELECT * FROM statuses WHERE expiresAt > :now ORDER BY createdAt DESC")
    fun observeActiveStatuses(now: Long): Flow<List<StatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStatus(status: StatusEntity)

    @Query("DELETE FROM statuses WHERE id = :id")
    suspend fun deleteStatus(id: String)
}

@Dao
interface StickerDao {
    @Query("SELECT * FROM sticker_packs ORDER BY isPersonal DESC, createdAt DESC")
    fun observePacks(): Flow<List<StickerPackEntity>>

    @Query("SELECT * FROM stickers ORDER BY isFavorite DESC, createdAt DESC")
    fun observeAllStickers(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE packId = :packId ORDER BY createdAt DESC")
    fun observeStickersByPack(packId: String): Flow<List<StickerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPack(pack: StickerPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSticker(sticker: StickerEntity)

    @Query("UPDATE stickers SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM stickers WHERE id = :id")
    suspend fun deleteSticker(id: String)
}

@Dao
interface CallDao {
    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    fun observeCalls(): Flow<List<CallRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallRecordEntity)

    @Query("DELETE FROM call_records")
    suspend fun clearHistory()
}

@Dao
interface AiDao {
    @Query("SELECT * FROM ai_messages WHERE mode = :mode ORDER BY createdAt ASC")
    fun observeAiMessages(mode: String): Flow<List<AiMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiMessage(msg: AiMessageEntity)

    @Query("DELETE FROM ai_messages WHERE mode = :mode")
    suspend fun clearAiMessages(mode: String)

    @Query("SELECT * FROM ai_memories ORDER BY createdAt DESC")
    fun observeMemories(): Flow<List<AiMemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: AiMemoryEntity)

    @Query("DELETE FROM ai_memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("DELETE FROM ai_memories")
    suspend fun clearAllMemories()

    @Query("SELECT * FROM ai_creations ORDER BY createdAt DESC")
    fun observeCreations(): Flow<List<AiCreationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreation(creation: AiCreationEntity)

    @Query("UPDATE ai_creations SET isFavorite = :favorite WHERE id = :id")
    suspend fun toggleCreationFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM ai_creations WHERE id = :id")
    suspend fun deleteCreation(id: String)
}

@Dao
interface TikTokAiDao {
    @Query("SELECT * FROM tiktok_analyses ORDER BY createdAt DESC")
    fun observeAnalyses(): Flow<List<TikTokAnalysisEntity>>

    @Query("SELECT * FROM tiktok_analyses WHERE id = :id LIMIT 1")
    fun observeAnalysisById(id: String): Flow<TikTokAnalysisEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: TikTokAnalysisEntity)

    @Query("DELETE FROM tiktok_analyses WHERE id = :id")
    suspend fun deleteAnalysis(id: String)

    @Query("SELECT * FROM tiktok_qa WHERE analysisId = :analysisId ORDER BY createdAt ASC")
    fun observeQa(analysisId: String): Flow<List<TikTokQaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQa(qa: TikTokQaEntity)
}

@Dao
interface NotificationLogDao {
    @Query("SELECT * FROM notification_logs ORDER BY createdAt DESC LIMIT 100")
    fun observeLogs(): Flow<List<NotificationLogEntity>>

    @Query("SELECT COUNT(*) FROM notification_logs WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_logs WHERE eventKey = :eventKey")
    suspend fun countByEventKey(eventKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: NotificationLogEntity)

    @Query("UPDATE notification_logs SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notification_logs")
    suspend fun clearLogs()
}
