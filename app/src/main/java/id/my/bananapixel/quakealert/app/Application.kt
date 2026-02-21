package id.my.bananapixel.quakealert.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.color.DynamicColors
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.db.Subscription
import id.my.bananapixel.quakealert.util.EMERGENCY_TOPIC
import id.my.bananapixel.quakealert.util.Log
import id.my.bananapixel.quakealert.util.randomSubscriptionId
import id.my.bananapixel.quakealert.work.LocationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import id.my.bananapixel.quakealert.di.appModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import java.util.Date
import java.util.concurrent.TimeUnit

class Application : Application(), KoinComponent {
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: Repository by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@Application)
            modules(appModule)
        }
        if (repository.getRecordLogs()) {
            Log.setRecord(true)
        }
        if (repository.getDynamicColorsEnabled()) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
        scheduleLocationWorker()
        addEmergencySubscription()
    }

    private fun addEmergencySubscription() {
        ioScope.launch {
            val appBaseUrl = BuildConfig.APP_BASE_URL
            val emergencyTopic = EMERGENCY_TOPIC
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
