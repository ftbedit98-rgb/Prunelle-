package com.example.core.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.example.PrunelleApplication
import com.example.core.birthday.BirthdayHelper
import com.example.core.media.MediaStorageHelper
import com.example.core.network.ApiClient
import com.example.core.network.MessageSyncBatchDto
import com.example.core.network.MessageSyncDto
import com.example.core.network.PrunelleApiService
import com.example.core.notifications.PrunelleNotificationHelper
import com.example.data.local.NotificationLogEntity
import com.example.data.local.PrunelleDatabase
import com.example.data.local.ReadStatus
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.AiRepository
import com.example.data.repository.FirebaseAiRepository
import com.example.data.repository.FirestoreCloudRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class PendingSyncOutcome {
    SUCCESS,
    RETRY
}

object PendingMessageSyncEngine {
    suspend fun runSync(
        context: Context,
        db: PrunelleDatabase = PrunelleDatabase.getInstance(context),
        prefsRepo: UserPreferencesRepository = UserPreferencesRepository(context),
        apiServiceFactory: ((String) -> PrunelleApiService)? = null,
        cloudRepoOverride: FirestoreCloudRepository? = null,
        currentUidOverride: String? = null
    ): PendingSyncOutcome {
        val messageDao = db.messageDao()
        val conversationDao = db.conversationDao()
        val prefs = prefsRepo.userPrefs.first()

        val pending = messageDao.getPendingMessages()
        if (pending.isEmpty()) return PendingSyncOutcome.SUCCESS

        val currentUid = currentUidOverride ?: runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        val hasCustomBackend = prefs.backendBaseUrl.isNotBlank() &&
            !prefs.backendBaseUrl.contains(".invalid")

        var anySyncFailed = false
        var backendSyncedIds: Set<String> = emptySet()

        if (hasCustomBackend) {
            val apiResult = runCatching {
                val api = apiServiceFactory?.invoke(prefs.backendBaseUrl)
                    ?: ApiClient.create(prefs.backendBaseUrl)
                val batch = MessageSyncBatchDto(
                    messages = pending.map {
                        MessageSyncDto(
                            id = it.id,
                            conversationId = it.conversationId,
                            senderId = it.senderId,
                            content = it.content,
                            type = it.type.name,
                            sentAt = it.sentAt,
                            mediaUri = it.mediaUri
                        )
                    }
                )
                api.syncMessages(batch)
            }
            val response = apiResult.getOrNull()
            if (apiResult.isFailure || response?.isSuccessful != true) {
                anySyncFailed = true
            } else {
                val returned = response.body()?.syncedIds.orEmpty().toSet()
                backendSyncedIds = if (returned.isNotEmpty()) returned else pending.map { it.id }.toSet()
            }
        }

        val cloudRepo = cloudRepoOverride ?: if (currentUid != null) {
            runCatching { FirestoreCloudRepository(context) }.getOrNull()
        } else null

        if (cloudRepo != null) {
            val touchedConvIds = mutableSetOf<String>()
            pending.forEach { msg ->
                val writeRes = cloudRepo.upsertMessage(msg, statusOverride = ReadStatus.SENT)
                if (writeRes.isSuccess) {
                    messageDao.updateReadStatus(
                        id = msg.id,
                        status = ReadStatus.SENT,
                        deliveredAt = null,
                        readAt = null
                    )
                    touchedConvIds.add(msg.conversationId)
                } else {
                    anySyncFailed = true
                }
            }
            touchedConvIds.forEach { convId ->
                conversationDao.getConversation(convId)?.let { conv ->
                    val updatedConv = conv.copy(lastMessageStatus = ReadStatus.SENT)
                    conversationDao.upsertConversation(updatedConv)
                    cloudRepo.upsertConversation(updatedConv)
                }
            }
        } else if (hasCustomBackend && !anySyncFailed) {
            val touchedConvIds = mutableSetOf<String>()
            pending.filter { it.id in backendSyncedIds }.forEach { msg ->
                messageDao.updateReadStatus(
                    id = msg.id,
                    status = ReadStatus.SENT,
                    deliveredAt = null,
                    readAt = null
                )
                touchedConvIds.add(msg.conversationId)
            }
            touchedConvIds.forEach { convId ->
                conversationDao.getConversation(convId)?.let { conv ->
                    conversationDao.upsertConversation(
                        conv.copy(lastMessageStatus = ReadStatus.SENT)
                    )
                }
            }
        } else if (cloudRepo == null && !hasCustomBackend) {
            // Keep messages queued in PENDING until user authenticates with Google or configures a backend
            return PendingSyncOutcome.RETRY
        }

        return if (anySyncFailed) PendingSyncOutcome.RETRY else PendingSyncOutcome.SUCCESS
    }
}

data class BirthdayCheckOutcome(
    val selfNotified: Boolean,
    val cardGeneratedPath: String?,
    val contactsNotified: Int,
    val needsConnectedCardRetry: Boolean
)

object BirthdayCheckEngine {
    private const val TAG = "BirthdayCheckEngine"

    fun isDeviceOnline(context: Context): Boolean = runCatching {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    suspend fun runCheck(
        context: Context,
        db: PrunelleDatabase = PrunelleDatabase.getInstance(context),
        prefsRepo: UserPreferencesRepository = UserPreferencesRepository(context),
        aiRepository: AiRepository = (context.applicationContext as? PrunelleApplication)?.repository?.aiRepository
            ?: FirebaseAiRepository(),
        isOnlineOverride: Boolean? = null
    ): BirthdayCheckOutcome {
        val prefs = prefsRepo.userPrefs.first()
        if (!prefs.isLoggedIn) {
            return BirthdayCheckOutcome(
                selfNotified = false,
                cardGeneratedPath = null,
                contactsNotified = 0,
                needsConnectedCardRetry = false
            )
        }

        val now = BirthdayHelper.nowInZone(prefs.timezoneId)
        val year = now.year
        val notifDao = db.notificationLogDao()

        var selfNotified = false
        var cardPath: String? = null
        var needsConnectedRetry = false

        if (BirthdayHelper.isBirthdayToday(prefs.birthDay, prefs.birthMonth, prefs.timezoneId, now)) {
            if (prefs.notifyBirthdays && prefs.lastSelfBirthdayNotifiedYear < year) {
                val eventKey = "bday_self_$year"
                if (notifDao.countByEventKey(eventKey) == 0) {
                    val title = "🎂 Joyeux anniversaire ${prefs.displayName.ifBlank { "à toi" }} !"
                    val body = BirthdayHelper.SELF_BIRTHDAY_MESSAGE
                    notifDao.insertLog(
                        NotificationLogEntity(
                            id = UUID.randomUUID().toString(),
                            category = "BIRTHDAY",
                            title = title,
                            body = body,
                            eventKey = eventKey
                        )
                    )
                    PrunelleNotificationHelper.showNotification(
                        context = context,
                        channelId = PrunelleNotificationHelper.CHANNEL_BIRTHDAYS,
                        notificationId = eventKey.hashCode(),
                        title = title,
                        body = body,
                        routeTab = PrunelleNotificationHelper.TAB_HOME,
                        openBirthday = true
                    )
                    prefsRepo.markSelfBirthdayNotified(year)
                    selfNotified = true
                }
            }

            // Ensure personalized birthday card is generated and cached even when triggered in background
            val cached = prefs.cachedBirthdayCardPath
            if (prefs.lastBirthdayCardYear == year && !cached.isNullOrBlank() && File(cached).exists()) {
                cardPath = cached
            } else {
                val online = isOnlineOverride ?: isDeviceOnline(context)
                if (online) {
                    try {
                        val avatarBmp = MediaStorageHelper.loadBitmapFromUriOrPath(context, prefs.avatarUri, maxDim = 768)
                        val generated = aiRepository.generateBirthdayPortrait(
                            context = context,
                            displayName = prefs.displayName.ifBlank { "Prunelle" },
                            avatarBitmap = avatarBmp,
                            year = year
                        )
                        prefsRepo.saveBirthdayCardGenerated(year, generated)
                        cardPath = generated
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Background birthday card generation failed, will retry when connected: ${e.message}")
                        needsConnectedRetry = true
                    }
                } else {
                    needsConnectedRetry = true
                }
            }
        }

        var contactsNotified = 0
        val contacts = db.contactDao().getContactsWithBirthdays()
        contacts.forEach { contact ->
            if (prefs.notifyBirthdays &&
                BirthdayHelper.canNotifyContactBirthday(contact) &&
                BirthdayHelper.isBirthdayToday(contact.birthDay, contact.birthMonth, prefs.timezoneId, now) &&
                contact.lastBirthdayNotifiedYear < year
            ) {
                val eventKey = "bday_contact_${contact.id}_$year"
                if (notifDao.countByEventKey(eventKey) == 0) {
                    val title = "🎉 Anniversaire de ${contact.displayName}"
                    val body = "C'est l'anniversaire de ${contact.displayName} aujourd'hui ! Envoyez-lui un message sur Prunelle."
                    notifDao.insertLog(
                        NotificationLogEntity(
                            id = UUID.randomUUID().toString(),
                            category = "BIRTHDAY",
                            title = title,
                            body = body,
                            eventKey = eventKey
                        )
                    )
                    PrunelleNotificationHelper.showNotification(
                        context = context,
                        channelId = PrunelleNotificationHelper.CHANNEL_BIRTHDAYS,
                        notificationId = eventKey.hashCode(),
                        title = title,
                        body = body,
                        routeTab = PrunelleNotificationHelper.TAB_HOME
                    )
                    db.contactDao().markBirthdayNotifiedYear(contact.id, year)
                    contactsNotified++
                }
            }
        }

        return BirthdayCheckOutcome(
            selfNotified = selfNotified,
            cardGeneratedPath = cardPath,
            contactsNotified = contactsNotified,
            needsConnectedCardRetry = needsConnectedRetry
        )
    }
}

class SyncPendingMessagesWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return when (PendingMessageSyncEngine.runSync(applicationContext)) {
            PendingSyncOutcome.SUCCESS -> Result.success()
            PendingSyncOutcome.RETRY -> Result.retry()
        }
    }
}

class BirthdayCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outcome = BirthdayCheckEngine.runCheck(applicationContext)
        if (outcome.needsConnectedCardRetry) {
            WorkSyncScheduler.enqueueConnectedBirthdayCardGeneration(applicationContext)
        }
        return Result.success()
    }
}

object WorkSyncScheduler {
    const val UNIQUE_SYNC_WORK = "prunelle_sync_pending_messages"
    const val UNIQUE_PERIODIC_SYNC_WORK = "prunelle_periodic_sync_pending"
    const val UNIQUE_BIRTHDAY_WORK = "prunelle_daily_birthday_check"
    const val UNIQUE_BIRTHDAY_CARD_CONNECTED_WORK = "prunelle_birthday_card_connected"

    fun enqueuePendingSync(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncPendingMessagesWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_SYNC_WORK,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }.onFailure { err ->
            Log.w("WorkSyncScheduler", "WorkManager enqueuePendingSync skipped: ${err.message}")
        }
    }

    fun schedulePeriodicPendingSync(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncPendingMessagesWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_SYNC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }.onFailure { err ->
            Log.w("WorkSyncScheduler", "WorkManager schedulePeriodicPendingSync skipped: ${err.message}")
        }
    }

    fun scheduleDailyBirthdayCheck(context: Context) {
        runCatching {
            val request = PeriodicWorkRequestBuilder<BirthdayCheckWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_BIRTHDAY_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }.onFailure { err ->
            Log.w("WorkSyncScheduler", "WorkManager scheduleDailyBirthdayCheck skipped: ${err.message}")
        }
    }

    fun enqueueImmediateBirthdayCheck(context: Context) {
        runCatching {
            val request = OneTimeWorkRequestBuilder<BirthdayCheckWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "prunelle_immediate_birthday_check",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }.onFailure { err ->
            Log.w("WorkSyncScheduler", "WorkManager enqueueImmediateBirthdayCheck skipped: ${err.message}")
        }
    }

    fun enqueueConnectedBirthdayCardGeneration(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<BirthdayCheckWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_BIRTHDAY_CARD_CONNECTED_WORK,
                ExistingWorkPolicy.KEEP,
                request
            )
        }.onFailure { err ->
            Log.w("WorkSyncScheduler", "WorkManager enqueueConnectedBirthdayCardGeneration skipped: ${err.message}")
        }
    }
}
