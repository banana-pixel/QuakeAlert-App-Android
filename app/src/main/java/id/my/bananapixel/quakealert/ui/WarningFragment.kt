package id.my.bananapixel.quakealert.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.domain.ServerHealthStatus
import id.my.bananapixel.quakealert.util.EMERGENCY_TOPIC
import id.my.bananapixel.quakealert.domain.SensorStatus
import id.my.bananapixel.quakealert.app.AlertState
import id.my.bananapixel.quakealert.msg.NotificationService
import id.my.bananapixel.quakealert.msg.Sensor
import id.my.bananapixel.quakealert.util.formatTimestampToLocal
import com.google.android.material.floatingactionbutton.FloatingActionButton
import id.my.bananapixel.quakealert.app.Application as App
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import id.my.bananapixel.quakealert.db.Repository
import okhttp3.*
import java.io.IOException

class WarningFragment : Fragment(R.layout.fragment_warning) {

    private lateinit var scanningLayout: LinearLayout
    private lateinit var alertLayout: ScrollView
    private lateinit var radarIcon: ImageView
    private lateinit var radarContainer: FrameLayout
    private lateinit var statusPill: ConstraintLayout
    private lateinit var statusDot: View
    private lateinit var statusPillText: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var alertDetails: TextView
    private lateinit var alertTime: TextView
    private lateinit var intensityText: TextView
    private lateinit var safeActionsButton: Button
    private lateinit var safeActionsCard: MaterialCardView
    private lateinit var shareButton: Button
    private lateinit var dismissButton: Button
    private lateinit var viewLogsButton: FloatingActionButton

    private val client = OkHttpClient()
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRefreshRunnable = object : Runnable {
        override fun run() {
            if (scanningLayout.visibility == View.VISIBLE) fetchServerStatus()
            statusHandler.postDelayed(this, 3000)
        }
    }

    private var backgroundAnimator: ValueAnimator? = null
    private val resetHandler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable { AlertState.setActive(false) }

    private val timeUpdateHandler = Handler(Looper.getMainLooper())
    private var timeUpdateRunnable: Runnable? = null
    private var alertTimestamp: Long = 0L

    private val quakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NotificationService.ACTION_QUAKE_ALERT) {
                val message = intent.getStringExtra("message") ?: ""
                val distance = intent.getStringExtra("distance") ?: "Unknown"
                val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis() / 1000)

                AlertState.setAlertFromRaw(message, distance, timestamp)
                scheduleReset()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- FIXED IDs TO MATCH FINAL XML ---

        // 1. Scanning Layout: Uses the standard ID again
        scanningLayout = view.findViewById(R.id.warning_scanning_layout)

        // 2. Alert Layout: Uses the standard ID again
        alertLayout = view.findViewById(R.id.warning_alert_layout)

        // 3. FAB: Uses the "Magic ID" so MainActivity lifts it above the Nav Bar
        viewLogsButton = view.findViewById(R.id.bottom_floating_ui)

        // ------------------------------------

        radarIcon = view.findViewById(R.id.warning_radar_icon)
        radarContainer = view.findViewById(R.id.warning_radar_container)
        statusPill = view.findViewById(R.id.warning_status_pill)
        statusDot = view.findViewById(R.id.warning_status_dot)
        statusPillText = view.findViewById(R.id.warning_status_pill_text)
        statusTitle = view.findViewById(R.id.warning_status_title)
        statusSubtitle = view.findViewById(R.id.warning_status_subtitle)
        alertDetails = view.findViewById(R.id.warning_alert_details)
        alertTime = view.findViewById(R.id.warning_alert_time)
        intensityText = view.findViewById(R.id.warning_intensity_text)
        safeActionsButton = view.findViewById(R.id.warning_safe_actions_button)
        safeActionsCard = view.findViewById(R.id.warning_safe_actions_card)
        shareButton = view.findViewById(R.id.warning_share_button)
        dismissButton = view.findViewById(R.id.warning_dismiss_button)

        val pulseAnimation = AnimationUtils.loadAnimation(context, R.anim.pulse)

        AlertState.isAlertActive.observe(viewLifecycleOwner) { active ->
            updateVisibility(active, pulseAnimation)
        }

        AlertState.latestAlert.observe(viewLifecycleOwner) { notification ->
            notification?.let {
                updateAlertContent(it.message, AlertState.latestDistance.value, it.timestamp)
            }
        }

        AlertState.latestDistance.observe(viewLifecycleOwner) { distance ->
            AlertState.latestAlert.value?.let {
                updateAlertContent(it.message, distance, it.timestamp)
            }
        }

        setupListeners()

        val savedNotification = AlertState.latestAlert.value
        val isCurrentlyActive = AlertState.isAlertActive.value ?: false

        if (isCurrentlyActive && savedNotification != null) {
            updateAlertContent(
                savedNotification.message,
                AlertState.latestDistance.value,
                savedNotification.timestamp
            )
        }
    }

    private fun updateAlertContent(message: String, distance: String?, timestamp: Long) {
        this.alertTimestamp = timestamp

        stopTimeUpdater()
        startTimeUpdater()

        val cleanMessage = message.split("\n")
            .filterNot { it.contains("Waktu:", ignoreCase = true) }
            .joinToString("\n").trim()

        val intensityRegex = "Intensitas\\s*:\\s*([IVX]+)".toRegex(RegexOption.IGNORE_CASE)
        val match = intensityRegex.find(cleanMessage)
        val extractedIntensity = match?.groupValues?.get(1) ?: "?"

        intensityText.text = extractedIntensity
        intensityText.contentDescription = getString(R.string.warning_intensity_content_description) + ", " + extractedIntensity

        alertDetails.text = if (!distance.isNullOrBlank()) {
            "Location: $distance km away\n$cleanMessage"
        } else {
            cleanMessage
        }

        val alertSummary = if (!distance.isNullOrBlank()) {
            getString(R.string.warning_screen_alert_content_description) + ". " + distance + " km away."
        } else {
            getString(R.string.warning_screen_alert_content_description)
        }
        alertLayout.contentDescription = alertSummary
    }

    private fun updateVisibility(active: Boolean, pulseAnimation: Animation) {
        if (active) {
            scanningLayout.visibility = View.GONE
            alertLayout.visibility = View.VISIBLE
            radarIcon.clearAnimation()
            statusHandler.removeCallbacks(statusRefreshRunnable)
        } else {
            scanningLayout.visibility = View.VISIBLE
            alertLayout.visibility = View.GONE
            stopBackgroundWarningAnimation()
            radarIcon.startAnimation(pulseAnimation)
            safeActionsCard.visibility = View.GONE
            updateWarningStatus(ServerHealthStatus.CONNECTING, 0, 0)
            statusHandler.removeCallbacks(statusRefreshRunnable)
            warmupThenStartPolling()
        }
    }

    /**
     * Run one silent request to establish the connection (DNS, TCP, TLS), then start the 3s status polling.
     * Matches SensorsFragment: first visible request reuses the warm connection so the shown status/latency is representative.
     */
    private fun warmupThenStartPolling() {
        val ctx = context ?: return
        val baseUrl = BuildConfig.APP_BASE_URL.trimEnd('/')
        val request = Request.Builder().url("$baseUrl/stations").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    if (isAdded && scanningLayout.visibility == View.VISIBLE) {
                        statusHandler.post(statusRefreshRunnable)
                    }
                }
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
                activity?.runOnUiThread {
                    if (isAdded && scanningLayout.visibility == View.VISIBLE) {
                        statusHandler.post(statusRefreshRunnable)
                    }
                }
            }
        })
    }

    private fun fetchServerStatus() {
        val ctx = context ?: return
        val baseUrl = BuildConfig.APP_BASE_URL.trimEnd('/')
        val startTime = System.currentTimeMillis()
        val request = Request.Builder().url("$baseUrl/stations").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    if (isAdded && scanningLayout.visibility == View.VISIBLE) {
                        val latency = (System.currentTimeMillis() - startTime).toInt()
                        updateWarningStatus(ServerHealthStatus.CRITICAL, latency, 0)
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val latency = (System.currentTimeMillis() - startTime).toInt()
                response.use {
                    if (!response.isSuccessful) {
                        activity?.runOnUiThread {
                            if (isAdded && scanningLayout.visibility == View.VISIBLE) {
                                updateWarningStatus(ServerHealthStatus.CRITICAL, latency, 0)
                            }
                        }
                        return
                    }
                    val bodyString = response.body?.string()
                    if (bodyString != null && bodyString.trim().startsWith("[")) {
                        try {
                            val stations: List<Sensor> = Json.decodeFromString(ListSerializer(Sensor.serializer()), bodyString)
                            val onlineCount = stations.count { SensorStatus.fromApi(it.status) == SensorStatus.ONLINE }
                            val status = if (latency > 300) ServerHealthStatus.WARNING else ServerHealthStatus.HEALTHY
                            activity?.runOnUiThread {
                                if (isAdded && scanningLayout.visibility == View.VISIBLE) {
                                    updateWarningStatus(status, latency, onlineCount)
                                }
                            }
                        } catch (e: Exception) {
                            activity?.runOnUiThread {
                                if (isAdded && scanningLayout.visibility == View.VISIBLE) {
                                    updateWarningStatus(ServerHealthStatus.CRITICAL, latency, 0)
                                }
                            }
                        }
                    } else {
                        activity?.runOnUiThread {
                            if (isAdded && scanningLayout.visibility == View.VISIBLE) {
                                updateWarningStatus(ServerHealthStatus.CRITICAL, latency, 0)
                            }
                        }
                    }
                }
            }
        })
    }

    /**
     * Updates the 3D status pill, title, subtitle, radar tint and container to match server/sensor state.
     * Status: HEALTHY, CONNECTING, WARNING, CRITICAL. Same semantics as SensorsFragment.
     * Strings and colors are applied only at this UI boundary.
     */
    private fun updateWarningStatus(status: ServerHealthStatus, latency: Int, onlineCount: Int) {
        if (!::statusPill.isInitialized) return
        statusPill.setBackgroundResource(
            when (status) {
                ServerHealthStatus.HEALTHY -> R.drawable.bg_pill_3d_green2
                ServerHealthStatus.CONNECTING -> R.drawable.bg_pill_3d_blue
                ServerHealthStatus.WARNING -> R.drawable.bg_pill_3d_orange
                ServerHealthStatus.CRITICAL -> R.drawable.bg_pill_3d_red
            }
        )
        statusDot.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        statusPillText.setTextColor(Color.WHITE)

        val pillText: String
        val titleRes: Int
        val subtitleRes: Int
        val titleColor: Int
        val radarTint: String
        val circleBg: Int

        when (status) {
            ServerHealthStatus.HEALTHY -> {
                pillText = if (onlineCount == 0) {
                    getString(R.string.warning_status_no_sensors)
                } else {
                    getString(R.string.warning_status_pill_healthy, onlineCount)
                }
                titleRes = if (onlineCount == 0) R.string.warning_status_no_sensors else R.string.warning_status_sensors_active
                subtitleRes = if (onlineCount == 0) R.string.warning_subtitle_no_sensors else R.string.warning_subtitle_monitoring
                titleColor = if (onlineCount == 0) Color.parseColor("#FF9800") else Color.parseColor("#4CAF50")
                radarTint = if (onlineCount == 0) "#FF9800" else "#4CAF50"
                circleBg = if (onlineCount == 0) R.drawable.bg_circle_3d_orange else R.drawable.bg_circle_3d_green
            }
            ServerHealthStatus.CONNECTING -> {
                pillText = getString(R.string.warning_status_pill_connecting)
                titleRes = R.string.warning_status_connecting
                subtitleRes = R.string.warning_subtitle_connecting
                titleColor = Color.parseColor("#2196F3")
                radarTint = "#2196F3"
                circleBg = R.drawable.bg_circle_3d_green
            }
            ServerHealthStatus.WARNING -> {
                pillText = getString(R.string.warning_status_unstable)
                titleRes = R.string.warning_status_unstable
                subtitleRes = R.string.warning_subtitle_unstable
                titleColor = Color.parseColor("#FF9800")
                radarTint = "#FF9800"
                circleBg = R.drawable.bg_circle_3d_orange
            }
            ServerHealthStatus.CRITICAL -> {
                pillText = getString(R.string.warning_status_server_offline)
                titleRes = R.string.warning_status_server_offline
                subtitleRes = R.string.warning_subtitle_server_offline
                titleColor = Color.parseColor("#F44336")
                radarTint = "#F44336"
                circleBg = R.drawable.bg_circle_3d_red
            }
        }
        statusPillText.text = pillText
        statusTitle.text = getString(titleRes)
        statusTitle.setTextColor(titleColor)
        statusSubtitle.text = getString(subtitleRes)
        radarIcon.setColorFilter(Color.parseColor(radarTint))
        radarContainer.setBackgroundResource(circleBg)
    }

    private fun setupListeners() {
        safeActionsButton.setOnClickListener {
            safeActionsCard.visibility =
                if (safeActionsCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        shareButton.setOnClickListener {
            val shareText = "🚨 EARTHQUAKE ALERT 🚨\nIntensity: ${intensityText.text}\n${alertDetails.text}\nTime: ${alertTime.text}"
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, "Share Alert"))
        }

        dismissButton.setOnClickListener {
            AlertState.setActive(false)
            resetHandler.removeCallbacks(resetRunnable)
            // Stop the insistent alert sound (same as when user dismisses the notification)
            Repository.getInstance(requireContext()).mediaPlayer.apply {
                if (isPlaying) stop()
                reset()
            }
        }

        viewLogsButton.setOnClickListener {
            val topic = EMERGENCY_TOPIC
            val baseUrl = BuildConfig.APP_BASE_URL
            val host = android.net.Uri.parse(baseUrl).host ?: baseUrl

            viewLogsButton.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
                viewLogsButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                viewLifecycleOwner.lifecycleScope.launch {
                    val repository = (requireActivity().application as App).repository
                    val subscription = withContext(Dispatchers.IO) {
                        repository.getSubscription(baseUrl, topic)
                    }

                    val uriString = "ntfy://$host/$topic"
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uriString)).apply {
                        `package` = requireContext().packageName
                        if (subscription != null) {
                            putExtra("subscription_id", subscription.id)
                        }
                    }
                    startActivity(intent)
                }
            }.start()
        }
    }

    private fun startBackgroundWarningAnimation(view: View) {
        // Disabled to preserve gradient background
        /*
        if (backgroundAnimator != null) return
        val colorFrom = ContextCompat.getColor(requireContext(), R.color.intensity_red)
        val colorTo = ColorUtils.setAlphaComponent(colorFrom, 160)
        backgroundAnimator = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator -> view.setBackgroundColor(animator.animatedValue as Int) }
            start()
        }
        */
    }

    private fun stopBackgroundWarningAnimation() {
        backgroundAnimator?.cancel()
        backgroundAnimator = null
    }

    private fun scheduleReset() {
        resetHandler.removeCallbacks(resetRunnable)
        resetHandler.postDelayed(resetRunnable, 10 * 60 * 1000)
    }

    private fun startTimeUpdater() {
        timeUpdateRunnable = object : Runnable {
            override fun run() {
                if (alertTimestamp > 0) {
                    val timeAgo = android.text.format.DateUtils.getRelativeTimeSpanString(
                        alertTimestamp * 1000L,
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS
                    )
                    val exactTime = formatTimestampToLocal(alertTimestamp)
                    alertTime.text = "$exactTime\n($timeAgo)"
                }
                timeUpdateHandler.postDelayed(this, 60 * 1000L)
            }
        }
        timeUpdateHandler.post(timeUpdateRunnable!!)
    }

    private fun stopTimeUpdater() {
        timeUpdateRunnable?.let { timeUpdateHandler.removeCallbacks(it) }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(NotificationService.ACTION_QUAKE_ALERT)
        requireContext().registerReceiver(quakeReceiver, filter, Context.RECEIVER_EXPORTED)
        if (::scanningLayout.isInitialized && scanningLayout.visibility == View.VISIBLE) {
            statusHandler.removeCallbacks(statusRefreshRunnable)
            warmupThenStartPolling()
        }
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(quakeReceiver)
        statusHandler.removeCallbacks(statusRefreshRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBackgroundWarningAnimation()
        stopTimeUpdater()
    }
}