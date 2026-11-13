package com.example

import android.app.Application
import com.example.core.network.NetworkStatusMonitor
import com.example.core.notifications.PrunelleNotificationHelper
import com.example.core.worker.WorkSyncScheduler
import com.example.data.local.PrunelleDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.FirebaseAiRepository
import com.example.data.repository.FirestoreCloudRepository
import com.example.data.repository.PrunelleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PrunelleApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: PrunelleDatabase
        private set
    lateinit var prefsRepository: UserPreferencesRepository
        private set
    lateinit var networkMonitor: NetworkStatusMonitor
        private set
    lateinit var repository: PrunelleRepository
        private set

    // Deferred until first authenticated access after MainActivity.onCreate() has initialized App Check
    private val cloudRepository: FirestoreCloudRepository by lazy {
        FirestoreCloudRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        PrunelleNotificationHelper.ensureChannels(this)
        database = PrunelleDatabase.getInstance(this)
        prefsRepository = UserPreferencesRepository(this)
        networkMonitor = NetworkStatusMonitor(this)
        repository = PrunelleRepository(
            context = this,
            db = database,
            prefsRepo = prefsRepository,
            networkMonitor = networkMonitor,
            aiRepository = FirebaseAiRepository(),
            cloudRepoProvider = { cloudRepository }
        )

        WorkSyncScheduler.scheduleDailyBirthdayCheck(this)
        WorkSyncScheduler.schedulePeriodicPendingSync(this)

        // Automatically trigger WorkManager sync on startup if connected & whenever connectivity transitions to online
        appScope.launch {
            if (networkMonitor.networkStatus.value.isOnline) {
                WorkSyncScheduler.enqueuePendingSync(this@PrunelleApplication)
            }
            networkMonitor.networkStatus
                .map { it.isOnline }
                .distinctUntilChanged()
                .collect { online ->
                    if (online) {
                        WorkSyncScheduler.enqueuePendingSync(this@PrunelleApplication)
                    }
                }
        }
    }
}
