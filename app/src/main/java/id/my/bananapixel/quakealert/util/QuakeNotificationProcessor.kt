package id.my.bananapixel.quakealert.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.app.AlertState
import id.my.bananapixel.quakealert.db.Notification
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.db.Subscription
import id.my.bananapixel.quakealert.domain.IntentActions
import id.my.bananapixel.quakealert.ui.MainActivity
import id.my.bananapixel.quakealert.util.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Random

data class QuakeProcessResult(
    val title: String,
    val displayPriority: Int,
    val distanceLabel: String?
)

class QuakeNotificationProcessor(private val context: Context) : KoinComponent {
    private val repository: Repository by inject()

    fun process(subscription: Subscription, notification: Notification, appBaseUrl: String): QuakeProcessResult {
        val baseTitle = formatTitle(appBaseUrl, subscription, notification)
        val geoCoordinates = extractGeoCoordinates(notification.tags)
        val sharedPrefs = context.getSharedPreferences(Repository.SHARED_PREFS_ID, Context.MODE_PRIVATE)
        val alertRadiusKm = sharedPrefs.getInt(Repository.SHARED_PREFS_ALERT_RADIUS, DEFAULT_ALERT_RADIUS_KM).toDouble()
        
        val (distance, distanceLabel) = if (repository.isUserLocationSet()) {
            val userLat = repository.getUserLatitude()
            val userLon = repository.getUserLongitude()
            val dist = geoCoordinates?.let { (lat, lon) -> distanceKm(userLat, userLon, lat, lon) }
            Pair(dist, dist?.let { formatDistanceKm(it) })
        } else {
            Pair(null, null)
        }
        
        val displayPriority = quakeDisplayPriority(distance, alertRadiusKm, notification.priority)
        val title = when {
            distanceLabel == null -> baseTitle
            distance != null && distance > alertRadiusKm -> context.getString(R.string.notification_silent_quake, distanceLabel)
            else -> context.getString(R.string.notification_danger_quake, distanceLabel)
        }

        // Trigger global alert only for earthquake-tagged messages (red warning page)
        if (displayPriority == PRIORITY_MAX && hasEarthquakeTag(notification.tags)) {
            AlertState.setAlertData(notification, distanceLabel)
            val intent = Intent(ACTION_QUAKE_ALERT).apply {
                putExtra("message", notification.message)
                putExtra("title", title)
                putExtra("distance", distanceLabel)
                putExtra("timestamp", notification.timestamp)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }

        return QuakeProcessResult(title, displayPriority, distanceLabel)
    }

    fun getWarningIntent(subscription: Subscription, notification: Notification, distance: String?): PendingIntent? {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = IntentActions.OPEN_WARNING_PAGE
            putExtra("message", notification.message)
            putExtra("distance", distance)
            putExtra(MainActivity.EXTRA_SUBSCRIPTION_ID, subscription.id)

            // SINGLE_TOP ensures we don't restart the app if it's already open
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            context,
            Random().nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_QUAKE_ALERT = "id.my.bananapixel.quakealert.QUAKE_ALERT"
        const val DEFAULT_ALERT_RADIUS_KM = 500
    }
}
