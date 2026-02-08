package io.heckel.ntfy.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.color.DynamicColors
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.db.Subscription
import io.heckel.ntfy.util.Log
import io.heckel.ntfy.util.randomSubscriptionId
import io.heckel.ntfy.work.LocationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit

class Application : Application() {
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository by lazy {
        val repository = Repository.getInstance(applicationContext)
        if (repository.getRecordLogs()) {
            Log.setRecord(true)
        }
        repository
    }

    override fun onCreate() {
        super.onCreate()
        if (repository.getDynamicColorsEnabled()) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
        scheduleLocationWorker()
        addEmergencySubscription()
    }

    private fun addEmergencySubscription() {
        ioScope.launch {
            val appBaseUrl = getString(R.string.app_base_url)
            val emergencyTopic = "peringatan_gempa_darurat_xyz"
            val existing = repository.getSubscription(appBaseUrl, emergencyTopic)
            if (existing == null) {
                val subscription = Subscription(
                    id = randomSubscriptionId(),
                    baseUrl = appBaseUrl,
                    topic = emergencyTopic,
                    instant = true,
                    dedicatedChannels = false,
                    mutedUntil = 0,
                    minPriority = Repository.MIN_PRIORITY_USE_GLOBAL,
                    autoDelete = Repository.AUTO_DELETE_USE_GLOBAL,
                    insistent = Repository.INSISTENT_MAX_PRIORITY_USE_GLOBAL,
                    lastNotificationId = null,
                    icon = null,
                    upAppId = null,
                    upConnectorToken = null,
                    displayName = "Emergency Alerts",
                    totalCount = 0,
                    newCount = 0,
                    lastActive = Date().time / 1000
                )
                repository.addSubscription(subscription)
            }
        }
    }

    private fun scheduleLocationWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<LocationWorker>(LOCATION_WORKER_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag(LocationWorker.TAG)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            LocationWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    companion object {
        private const val LOCATION_WORKER_INTERVAL_HOURS = 6L
    }
}
