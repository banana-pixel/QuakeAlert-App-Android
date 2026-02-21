package id.my.bananapixel.quakealert.ui.delegates

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.db.Subscription
import id.my.bananapixel.quakealert.service.SubscriberServiceManager
import id.my.bananapixel.quakealert.util.Log
import id.my.bananapixel.quakealert.util.isIgnoringBatteryOptimizations
import androidx.core.net.toUri

private const val ONE_DAY_MILLIS = 86400000L

/**
 * Handles battery, WebSocket, and WebSocket-reconnect banners: click listeners and show/hide logic.
 */
class MainBannersDelegate(
    private val activity: androidx.appcompat.app.AppCompatActivity,
    private val repository: Repository,
    private val appBaseUrl: String?
) {
    private val tag = "MainBannersDelegate"

    fun setupClickListeners(onWsReconnectBannerNeedsUpdate: () -> Unit) {
        if (activity.isDestroyed) return
        // Battery banner
        val batteryBanner = activity.findViewById<View>(R.id.main_banner_battery)
        activity.findViewById<Button>(R.id.main_banner_battery_dontaskagain).setOnClickListener {
            batteryBanner.isVisible = false
            repository.setBatteryOptimizationsRemindTime(Repository.BATTERY_OPTIMIZATIONS_REMIND_TIME_NEVER)
        }
        activity.findViewById<Button>(R.id.main_banner_battery_ask_later).setOnClickListener {
            batteryBanner.isVisible = false
            repository.setBatteryOptimizationsRemindTime(System.currentTimeMillis() + ONE_DAY_MILLIS)
        }
        activity.findViewById<Button>(R.id.main_banner_battery_fix_now).setOnClickListener {
            try {
                activity.startActivity(Intent(ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${activity.packageName}".toUri()))
            } catch (_: ActivityNotFoundException) {
                try {
                    activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    activity.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            activity.findViewById<View>(R.id.main_banner_battery).isVisible = false
        }

        // WebSocket banner
        val wsBanner = activity.findViewById<View>(R.id.main_banner_websocket)
        val wsText = activity.findViewById<TextView>(R.id.main_banner_websocket_text)
        wsText.movementMethod = LinkMovementMethod.getInstance()
        activity.findViewById<Button>(R.id.main_banner_websocket_dontaskagain).setOnClickListener {
            wsBanner.isVisible = false
            repository.setWebSocketRemindTime(Repository.WEBSOCKET_REMIND_TIME_NEVER)
        }
        activity.findViewById<Button>(R.id.main_banner_websocket_remind_later).setOnClickListener {
            wsBanner.isVisible = false
            repository.setWebSocketRemindTime(System.currentTimeMillis() + ONE_DAY_MILLIS)
        }
        activity.findViewById<Button>(R.id.main_banner_websocket_enable).setOnClickListener {
            repository.setConnectionProtocol(Repository.CONNECTION_PROTOCOL_WS)
            SubscriberServiceManager(activity).refresh()
            wsBanner.isVisible = false
            onWsReconnectBannerNeedsUpdate()
        }

        // WebSocket Reconnect banner (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val wsReconnectBanner = activity.findViewById<View>(R.id.main_banner_websocket_reconnect)
            activity.findViewById<TextView>(R.id.main_banner_websocket_reconnect_text).movementMethod = LinkMovementMethod.getInstance()
            activity.findViewById<Button>(R.id.main_banner_websocket_reconnect_dontaskagain).setOnClickListener {
                wsReconnectBanner.isVisible = false
                repository.setWebSocketReconnectRemindTime(Repository.WEBSOCKET_RECONNECT_REMIND_TIME_NEVER)
            }
            activity.findViewById<Button>(R.id.main_banner_websocket_reconnect_remind_later).setOnClickListener {
                wsReconnectBanner.isVisible = false
                repository.setWebSocketReconnectRemindTime(System.currentTimeMillis() + ONE_DAY_MILLIS)
            }
            activity.findViewById<Button>(R.id.main_banner_websocket_reconnect_enable).setOnClickListener {
                activity.startActivity(Intent(ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }
    }

    fun showHideBatteryBanner(subscriptions: List<Subscription>) {
        if (activity.isDestroyed) return
        val hasInstantSubscriptions = subscriptions.count { it.instant } > 0
        val batteryRemindTimeReached = repository.getBatteryOptimizationsRemindTime() < System.currentTimeMillis()
        val ignoringOptimizations = isIgnoringBatteryOptimizations(activity)
        val showBanner = batteryRemindTimeReached && !ignoringOptimizations
        activity.findViewById<View>(R.id.main_banner_battery).isVisible = showBanner
        Log.d(tag, "Battery: ignoring=$ignoringOptimizations; instantSubs=$hasInstantSubscriptions; remindReached=$batteryRemindTimeReached; show=$showBanner")
    }

    fun showHideWebSocketBanner(subscriptions: List<Subscription>) {
        if (activity.isDestroyed) return
        val hasSelfHosted = subscriptions.count { it.baseUrl != appBaseUrl } > 0
        val usingWebSockets = repository.getConnectionProtocol() == Repository.CONNECTION_PROTOCOL_WS
        val wsRemindTimeReached = repository.getWebSocketRemindTime() < System.currentTimeMillis()
        val showBanner = hasSelfHosted && wsRemindTimeReached && !usingWebSockets
        val wsBanner = activity.findViewById<View>(R.id.main_banner_websocket)
        if (showBanner) {
            wsBanner.isVisible = true
            if (!BuildConfig.PAYMENT_LINKS_AVAILABLE) {
                val wsBannerMainText = activity.findViewById<TextView>(R.id.main_banner_websocket_text)
                val raw = activity.getString(R.string.main_banner_websocket_text)
                wsBannerMainText.text = HtmlCompat.fromHtml(raw.replace(Regex("</?a[^>]*>"), ""), HtmlCompat.FROM_HTML_MODE_LEGACY)
            }
        } else {
            wsBanner.isVisible = false
        }
    }

    fun showHideWebSocketReconnectBanner() {
        if (activity.isDestroyed) return
        val wsReconnectBanner = activity.findViewById<View>(R.id.main_banner_websocket_reconnect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val usingWebSockets = repository.getConnectionProtocol() == Repository.CONNECTION_PROTOCOL_WS
            val wsReconnectRemindTimeReached = repository.getWebSocketReconnectRemindTime() < System.currentTimeMillis()
            val canScheduleExactAlarms = (activity.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
            val showBanner = wsReconnectRemindTimeReached && usingWebSockets && !canScheduleExactAlarms
            Log.d(tag, "wsReconnect: remindReached=$wsReconnectRemindTimeReached, usingWs=$usingWebSockets, canExact=$canScheduleExactAlarms")
            wsReconnectBanner.isVisible = showBanner
        } else {
            wsReconnectBanner.isVisible = false
        }
    }
}
