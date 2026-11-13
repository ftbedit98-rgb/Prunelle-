package com.example

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.core.birthday.BirthdayHelper
import com.example.core.firebase.FcmMessageProcessor
import com.example.core.media.MediaStorageHelper
import com.example.core.network.*
import com.example.core.worker.BirthdayCheckEngine
import com.example.core.worker.PendingMessageSyncEngine
import com.example.core.worker.PendingSyncOutcome
import com.example.core.worker.WorkSyncScheduler
import com.example.data.local.*
import com.example.data.repository.AiChatMode
import com.example.data.repository.AiReplyResult
import com.example.data.repository.AiRepository
import com.example.data.repository.PlaceholderAiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `app name is Prunelle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Prunelle", appName)
    }

    @Test
    fun `room pagination returns latest 20 messages and updates readStatus`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PrunelleDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val msgDao = db.messageDao()
            for (i in 1..25) {
                msgDao.insertMessage(
                    MessageEntity(
                        id = "m_$i",
                        conversationId = "conv_1",
                        senderId = "me",
                        senderName = "Moi",
                        content = "Message $i",
                        readStatus = ReadStatus.PENDING,
                        sentAt = i * 1000L
                    )
                )
            }
            val paged = msgDao.observePagedMessages("conv_1", 20).first()
            assertEquals(20, paged.size)
            assertEquals("Message 6", paged.first().content)
            assertEquals("Message 25", paged.last().content)

            val pending = msgDao.getPendingMessages()
            assertEquals(25, pending.size)
            assertEquals(25, pending.map { it.id }.toSet().size)

            msgDao.updateReadStatus("m_25", ReadStatus.READ, 26000L, 27000L)
            val updated = msgDao.observePagedMessages("conv_1", 20).first().last()
            assertEquals(ReadStatus.READ, updated.readStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun `placeholder ai repository clearly labels output and returns pacing curve`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ai = PlaceholderAiRepository()
        val reply = ai.askAssistant("Bonjour", AiChatMode.ASSISTANT, emptyList())
        assertTrue(reply.isPlaceholder)
        assertTrue(reply.text.contains("Firebase"))

        val analysis = ai.analyzeTikTokVideo(context, "https://www.tiktok.com/@test/video/1", null)
        assertTrue(analysis.isPlaceholder)
        assertTrue(analysis.summary.contains("Firebase AI Logic"))
        val pacingPoints = analysis.pacingCurveCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
        assertEquals(8, pacingPoints.size)
    }

    @Test
    fun `tiktok analysis repository persists and retrieves history reports`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PrunelleDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = com.example.data.repository.TikTokAnalysisRepository(
                tikTokAiDao = db.tikTokAiDao(),
                aiRepository = PlaceholderAiRepository()
            )
            val saved = repo.analyzeAndSave(
                context = context,
                tiktokUrl = "https://www.tiktok.com/@prunelle/video/42",
                localVideoUri = null
            )
            val history = repo.observeHistory().first()
            assertEquals(1, history.size)
            assertEquals(saved.id, history.first().id)
            assertTrue(history.first().overallScore in 0..100)
            assertTrue(history.first().hookScore in 0..100)
            assertTrue(history.first().rhythmScore in 0..100)
            assertTrue(history.first().visualsScore in 0..100)

            repo.askFollowUp(saved, "Comment améliorer l'accroche ?")
            val qa = repo.observeFollowUpQa(saved.id).first()
            assertEquals(2, qa.size)
            assertTrue(qa.first().isFromUser)
            assertFalse(qa.last().isFromUser)

            repo.deleteReport(saved.id)
            val afterDelete = repo.observeHistory().first()
            assertTrue(afterDelete.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `birthday helper handles leap year timezone and omits year publicly`() {
        val zone = ZoneId.of("Europe/Paris")
        val nonLeapFeb28 = ZonedDateTime.of(2025, 2, 28, 10, 0, 0, 0, zone)
        val leapFeb29 = ZonedDateTime.of(2024, 2, 29, 10, 0, 0, 0, zone)

        assertTrue(BirthdayHelper.isBirthdayToday(29, 2, "Europe/Paris", nonLeapFeb28))
        assertTrue(BirthdayHelper.isBirthdayToday(29, 2, "Europe/Paris", leapFeb29))
        assertFalse(BirthdayHelper.isBirthdayToday(15, 5, "Europe/Paris", nonLeapFeb28))

        val formatted = BirthdayHelper.formatPublicDayMonth(15, 5)
        assertTrue(formatted.startsWith("15"))
        assertFalse(formatted.contains("199"))
        assertFalse(formatted.contains("200"))

        val privateContact = ContactEntity(
            id = "c1",
            displayName = "Camille",
            username = "camille",
            birthDay = 28,
            birthMonth = 2,
            birthdayPrivacy = PrivacyAudience.NOBODY
        )
        assertFalse(BirthdayHelper.canNotifyContactBirthday(privateContact))

        val visibleContact = privateContact.copy(birthdayPrivacy = PrivacyAudience.CONTACTS)
        assertTrue(BirthdayHelper.canNotifyContactBirthday(visibleContact))
    }

    @Test
    fun `notification log deduplicates by eventKey`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PrunelleDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.notificationLogDao()
            val eventKey = "bday_self_2025"
            assertEquals(0, dao.countByEventKey(eventKey))
            dao.insertLog(
                NotificationLogEntity(
                    id = "n1",
                    category = "BIRTHDAY",
                    title = "Joyeux anniversaire",
                    body = BirthdayHelper.SELF_BIRTHDAY_MESSAGE,
                    eventKey = eventKey
                )
            )
            assertEquals(1, dao.countByEventKey(eventKey))
        } finally {
            db.close()
        }
    }

    @Test
    fun `fcm message processor handles new message, call, status update and deduplicates`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PrunelleDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            // 1. Incoming new_message
            FcmMessageProcessor.processIncomingPush(
                context = context,
                data = mapOf(
                    "type" to "new_message",
                    "conversationId" to "dm_claire",
                    "messageId" to "msg_push_1",
                    "senderId" to "usr_claire",
                    "senderName" to "Claire",
                    "body" to "Salut, tu es dispo ?",
                    "eventKey" to "evt_msg_1"
                ),
                dbOverride = db
            )
            // Duplicate push with same eventKey should be ignored
            FcmMessageProcessor.processIncomingPush(
                context = context,
                data = mapOf(
                    "type" to "new_message",
                    "conversationId" to "dm_claire",
                    "messageId" to "msg_push_1",
                    "senderId" to "usr_claire",
                    "senderName" to "Claire",
                    "body" to "Salut, tu es dispo ?",
                    "eventKey" to "evt_msg_1"
                ),
                dbOverride = db
            )

            val conv = db.conversationDao().getConversation("dm_claire")
            assertNotNull(conv)
            assertEquals(1, conv?.unreadCount)
            assertEquals("Salut, tu es dispo ?", conv?.lastMessagePreview)
            assertEquals(ReadStatus.DELIVERED, conv?.lastMessageStatus)

            // 2. Incoming call
            FcmMessageProcessor.processIncomingPush(
                context = context,
                data = mapOf(
                    "type" to "incoming_call",
                    "callId" to "call_99",
                    "senderId" to "usr_lucas",
                    "senderName" to "Lucas",
                    "isVideo" to "true"
                ),
                dbOverride = db
            )
            val calls = db.callDao().observeCalls().first()
            assertEquals(1, calls.size)
            assertTrue(calls.first().isIncoming)
            assertTrue(calls.first().isVideo)

            // 3. Status update
            FcmMessageProcessor.processIncomingPush(
                context = context,
                data = mapOf(
                    "type" to "status_update",
                    "statusId" to "st_55",
                    "senderId" to "usr_nina",
                    "senderName" to "Nina",
                    "body" to "Coucher de soleil à Lyon ✨"
                ),
                dbOverride = db
            )
            val statuses = db.statusDao().observeActiveStatuses(System.currentTimeMillis()).first()
            assertEquals(1, statuses.size)
            assertEquals("Nina", statuses.first().authorName)

            val logs = db.notificationLogDao().observeLogs().first()
            assertEquals(3, logs.size)
            assertEquals(setOf("MESSAGE", "CALL", "STATUS_LIKE"), logs.map { it.category }.toSet())
        } finally {
            db.close()
        }
    }

    @Test
    fun `birthday check engine triggers self notification, generates card, and respects contact privacy`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PrunelleDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        try {
            val today = ZonedDateTime.now(ZoneId.systemDefault())
            prefsRepo.saveProfileAndLogin(
                displayName = "Alice",
                username = "alice",
                bio = "Test",
                avatarUri = null,
                countryCode = "+33",
                phoneNumber = "0600000000",
                isPhoneVerified = true,
                birthDay = today.dayOfMonth,
                birthMonth = today.monthValue,
                birthYear = 1995,
                birthdayPrivacy = PrivacyAudience.CONTACTS
            )
            prefsRepo.updateNotifications(
                messages = true,
                groups = true,
                calls = true,
                reactions = true,
                channels = true,
                statusLikes = true,
                birthdays = true
            )

            // Contact 1: today, CONTACTS privacy (should notify)
            db.contactDao().upsertContact(
                ContactEntity(
                    id = "c_visible",
                    displayName = "Marc",
                    username = "marc",
                    birthDay = today.dayOfMonth,
                    birthMonth = today.monthValue,
                    birthdayPrivacy = PrivacyAudience.CONTACTS
                )
            )
            // Contact 2: today, NOBODY privacy (should NOT notify)
            db.contactDao().upsertContact(
                ContactEntity(
                    id = "c_hidden",
                    displayName = "Secret",
                    username = "secret",
                    birthDay = today.dayOfMonth,
                    birthMonth = today.monthValue,
                    birthdayPrivacy = PrivacyAudience.NOBODY
                )
            )

            val testAi = object : AiRepository {
                override val isCloudAiEnabled: Boolean = true
                override suspend fun askAssistant(prompt: String, mode: AiChatMode, memoryContext: List<String>) =
                    AiReplyResult("ok", false)
                override fun askAssistantStream(prompt: String, mode: AiChatMode, memoryContext: List<String>): Flow<String> =
                    flowOf("ok")
                override suspend fun generateStudioImage(context: Context, prompt: String, style: String, aspectRatio: String, referenceMediaUri: String?) =
                    throw UnsupportedOperationException()
                override suspend fun generateStudioVideo(prompt: String, durationSeconds: Int, style: String, referenceMediaUri: String?, onProgress: (Float) -> Unit) =
                    throw UnsupportedOperationException()
                override suspend fun analyzeTikTokVideo(context: Context, tiktokUrl: String?, localVideoUri: String?) =
                    throw UnsupportedOperationException()
                override suspend fun askAboutTikTokAnalysis(analysis: TikTokAnalysisEntity, question: String) =
                    AiReplyResult("ok", false)
                override suspend fun generateBirthdayPortrait(context: Context, displayName: String, avatarBitmap: Bitmap?, year: Int): String {
                    val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                    return MediaStorageHelper.saveBitmapToInternalPng(context, bmp, "birthdays", "birthday_card_${year}.png")!!
                }
            }

            val outcome = BirthdayCheckEngine.runCheck(
                context = context,
                db = db,
                prefsRepo = prefsRepo,
                aiRepository = testAi,
                isOnlineOverride = true
            )

            assertTrue(outcome.selfNotified)
            assertEquals(1, outcome.contactsNotified)
            assertFalse(outcome.needsConnectedCardRetry)
            assertNotNull(outcome.cardGeneratedPath)
            assertTrue(File(outcome.cardGeneratedPath!!).exists())

            val updatedPrefs = prefsRepo.userPrefs.first()
            assertEquals(today.year, updatedPrefs.lastBirthdayCardYear)
            assertEquals(outcome.cardGeneratedPath, updatedPrefs.cachedBirthdayCardPath)
        } finally {
            db.close()
        }
    }

    @Test
    fun `workmanager pending sync engine retries when disconnected and dispatches when backend succeeds`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PrunelleDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefsRepo = UserPreferencesRepository(context)
        try {
            runCatching {
                WorkManager.initialize(
                    context,
                    Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).build()
                )
            }
            WorkSyncScheduler.enqueuePendingSync(context)
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WorkSyncScheduler.UNIQUE_SYNC_WORK)
                .get()
            assertTrue(workInfos.isNotEmpty())
            assertTrue(
                workInfos.first().state == WorkInfo.State.ENQUEUED ||
                    workInfos.first().state == WorkInfo.State.BLOCKED ||
                    workInfos.first().state == WorkInfo.State.RUNNING
            )

            db.conversationDao().upsertConversation(
                ConversationEntity(
                    id = "conv_offline",
                    title = "Paul",
                    lastMessagePreview = "Hors ligne...",
                    lastMessageStatus = ReadStatus.PENDING
                )
            )
            db.messageDao().insertMessage(
                MessageEntity(
                    id = "msg_offline_1",
                    conversationId = "conv_offline",
                    senderId = "me",
                    senderName = "Moi",
                    content = "Message écrit hors ligne",
                    readStatus = ReadStatus.PENDING,
                    sentAt = 10_000L
                )
            )

            // Without auth or backend URL, worker returns RETRY and preserves PENDING status
            prefsRepo.setBackendUrl("https://api.prunelle.invalid/")
            val retryResult = PendingMessageSyncEngine.runSync(
                context = context,
                db = db,
                prefsRepo = prefsRepo,
                currentUidOverride = null
            )
            assertEquals(PendingSyncOutcome.RETRY, retryResult)
            assertEquals(1, db.messageDao().getPendingMessages().size)

            // When backend comes online & accepts batch, messages transition cleanly to SENT
            prefsRepo.setBackendUrl("https://sync.prunelle.example/")
            val fakeApi = object : PrunelleApiService {
                override suspend fun checkHealth() = Response.success(ApiStatusDto(true))
                override suspend fun requestPhoneOtp(body: OtpRequestDto) = Response.success(ApiStatusDto(true))
                override suspend fun verifyPhoneOtp(body: OtpVerifyDto) = Response.success(ApiStatusDto(true))
                override suspend fun syncMessages(batch: MessageSyncBatchDto): Response<MessageSyncResponseDto> =
                    Response.success(
                        MessageSyncResponseDto(
                            syncedIds = batch.messages.map { it.id },
                            serverTimestamp = 20_000L
                        )
                    )
                override suspend fun searchUsers(query: String) = Response.success(emptyList<UserLookupDto>())
                override suspend fun acknowledgeMessage(messageId: String, status: Map<String, String>) =
                    Response.success(ApiStatusDto(true))
            }

            val successResult = PendingMessageSyncEngine.runSync(
                context = context,
                db = db,
                prefsRepo = prefsRepo,
                apiServiceFactory = { fakeApi },
                currentUidOverride = null
            )
            assertEquals(PendingSyncOutcome.SUCCESS, successResult)
            assertTrue(db.messageDao().getPendingMessages().isEmpty())
            val syncedMsg = db.messageDao().observePagedMessages("conv_offline", 10).first().first()
            assertEquals(ReadStatus.SENT, syncedMsg.readStatus)
            val syncedConv = db.conversationDao().getConversation("conv_offline")
            assertEquals(ReadStatus.SENT, syncedConv?.lastMessageStatus)
        } finally {
            db.close()
        }
    }
}
