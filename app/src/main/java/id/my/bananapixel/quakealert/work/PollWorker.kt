package id.my.bananapixel.quakealert.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.msg.ApiService
import id.my.bananapixel.quakealert.msg.NotificationDispatcher
import id.my.bananapixel.quakealert.msg.Poller
import id.my.bananapixel.quakealert.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params), KoinComponent {
    private val repository: Repository by inject()

    // IMPORTANT:
    //   Every time the worker is changed, the periodic work has to be REPLACEd.
    //   This is facilitated in the MainActivity using the VERSION below.

    init {
        Log.init(ctx) // Init in all entrypoints
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Polling for new notifications")
            val dispatcher = NotificationDispatcher(applicationContext, repository)
            val api = ApiService(applicationContext)
            val poller = Poller(api, repository)

            val baseUrl = inputData.getString(INPUT_DATA_BASE_URL)
            val topic = inputData.getString(INPUT_DATA_TOPIC)
            val subscriptions = if (baseUrl != null && topic != null) {
                val subscription = repository.getSubscription(baseUrl, topic) ?: return@withContext Result.success()
                listOf(subscription)
            } else {
                repository.getSubscriptions()
            }

            subscriptions.forEach{ subscription ->
                try {
                    val newNotifications = poller.poll(subscription)
                    newNotifications.forEach { notification ->
                        dispatcher.dispatch(subscription, notification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed checking messages: ${e.message}", e)
                }
            }
            Log.d(TAG, "Finished polling for new notifications")
            return@withContext Result.success()
        }
    }

    companion object {
        const val VERSION =  BuildConfig.VERSION_CODE
        const val TAG = "NtfyPollWorker"
        const val WORK_NAME_PERIODIC_ALL = "NtfyPollWorkerPeriodic" // Do not change
        const val WORK_NAME_ONCE_SINGE_PREFIX = "NtfyPollWorkerSingle" // e.g. NtfyPollWorkerSingle_https://ntfy.sh_mytopic
        const val INPUT_DATA_BASE_URL = "baseUrl"
        const val INPUT_DATA_TOPIC = "topic"
    }
}
