package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrunelleConverters {
    @TypeConverter
    fun fromMessageType(value: MessageType): String = value.name

    @TypeConverter
    fun toMessageType(value: String): MessageType =
        runCatching { MessageType.valueOf(value) }.getOrDefault(MessageType.TEXT)

    @TypeConverter
    fun fromReadStatus(value: ReadStatus): String = value.name

    @TypeConverter
    fun toReadStatus(value: String): ReadStatus =
        runCatching { ReadStatus.valueOf(value) }.getOrDefault(ReadStatus.SENT)

    @TypeConverter
    fun fromPrivacyAudience(value: PrivacyAudience): String = value.name

    @TypeConverter
    fun toPrivacyAudience(value: String): PrivacyAudience =
        runCatching { PrivacyAudience.valueOf(value) }.getOrDefault(PrivacyAudience.CONTACTS)
}

@Database(
    entities = [
        ContactEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ChannelEntity::class,
        ChannelPostEntity::class,
        StatusEntity::class,
        StickerPackEntity::class,
        StickerEntity::class,
        CallRecordEntity::class,
        AiMessageEntity::class,
        AiMemoryEntity::class,
        AiCreationEntity::class,
        TikTokAnalysisEntity::class,
        TikTokQaEntity::class,
        NotificationLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(PrunelleConverters::class)
abstract class PrunelleDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun channelDao(): ChannelDao
    abstract fun statusDao(): StatusDao
    abstract fun stickerDao(): StickerDao
    abstract fun callDao(): CallDao
    abstract fun aiDao(): AiDao
    abstract fun tikTokAiDao(): TikTokAiDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        @Volatile
        private var INSTANCE: PrunelleDatabase? = null

        fun getInstance(context: Context): PrunelleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrunelleDatabase::class.java,
                    "prunelle_secure.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                seedInitialOwnedResources(getInstance(context))
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * Preserves ONLY owned Prunelle Channels & curated Sticker Packs.
         * Intentionally seeds ZERO fake demo contacts or fake test conversations.
         */
        private suspend fun seedInitialOwnedResources(db: PrunelleDatabase) {
            val now = System.currentTimeMillis()
            val channelDao = db.channelDao()
            channelDao.upsertChannel(
                ChannelEntity(
                    id = "channel_prunelle_officiel",
                    name = "Prunelle Annonces",
                    description = "Nouveautés officielles, confidentialité et mises à jour de Prunelle.",
                    ownerId = "me",
                    isOwnedByMe = true,
                    isSubscribed = true,
                    subscriberCount = 1,
                    createdAt = now - 3600_000L
                )
            )
            channelDao.upsertPost(
                ChannelPostEntity(
                    id = "post_welcome_1",
                    channelId = "channel_prunelle_officiel",
                    content = "Bienvenue sur votre chaîne Prunelle. Publiez ici vos annonces, photos, vidéos et notes vocales.",
                    createdAt = now - 3500_000L
                )
            )

            val stickerDao = db.stickerDao()
            stickerDao.upsertPack(
                StickerPackEntity(
                    id = "pack_prunelle_essentiels",
                    name = "Prunelle Essentiels",
                    author = "Prunelle",
                    isPersonal = false,
                    isDownloaded = true,
                    category = "Classique",
                    createdAt = now
                )
            )
            val defaults = listOf(
                Triple("stk_1", "Salut", "👋"),
                Triple("stk_2", "Bravo", "👏"),
                Triple("stk_3", "Cœur", "💜"),
                Triple("stk_4", "Rire", "😂"),
                Triple("stk_5", "Feu", "🔥"),
                Triple("stk_6", "Validé", "✅")
            )
            defaults.forEach { (id, label, emoji) ->
                stickerDao.upsertSticker(
                    StickerEntity(
                        id = id,
                        packId = "pack_prunelle_essentiels",
                        label = label,
                        emojiFallback = emoji,
                        isAnimated = false
                    )
                )
            }
        }
    }
}
