package id.my.bananapixel.quakealert.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.app.AlertState
import id.my.bananapixel.quakealert.msg.NotificationService
import id.my.bananapixel.quakealert.util.formatTimestampToLocal
import com.google.android.material.floatingactionbutton.FloatingActionButton
import id.my.bananapixel.quakealert.app.Application as App
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import id.my.bananapixel.quakealert.db.Repository

class WarningFragment : Fragment(R.layout.fragment_warning) {

    private lateinit var scanningLayout: LinearLayout
    private lateinit var alertLayout: ScrollView
    private lateinit var radarIcon: ImageView
    private lateinit var alertDetails: TextView
    private lateinit var alertTime: TextView
    private lateinit var intensityText: TextView
    private lateinit var safeActionsButton: Button
    private lateinit var safeActionsCard: MaterialCardView
    private lateinit var shareButton: Button
    private lateinit var dismissButton: Button
    private lateinit var viewLogsButton: FloatingActionButton

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
        alertDetails.text = if (!distance.isNullOrBlank()) {
            "Location: $distance km away\n$cleanMessage"
        } else {
            cleanMessage
        }
    }

    private fun updateVisibility(active: Boolean, pulseAnimation: Animation) {
        if (active) {
            scanningLayout.visibility = View.GONE
            alertLayout.visibility = View.VISIBLE
            radarIcon.clearAnimation()

            // NOTE: We comment this out because setting background color
            // will override your nice Red Gradient drawable.
            // startBackgroundWarningAnimation(alertLayout)

        } else {
            scanningLayout.visibility = View.VISIBLE
            alertLayout.visibility = View.GONE
            stopBackgroundWarningAnimation()
            radarIcon.startAnimation(pulseAnimation)
            safeActionsCard.visibility = View.GONE
        }
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
            val topic = "peringatan_gempa_darurat_xyz"
            val baseUrl = "quakealert.bananapixel.my.id"

            viewLogsButton.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
                viewLogsButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                viewLifecycleOwner.lifecycleScope.launch {
                    val repository = (requireActivity().application as App).repository
                    val subscription = withContext(Dispatchers.IO) {
                        repository.getSubscription("https://$baseUrl", topic)
                    }

                    val uriString = "ntfy://$baseUrl/$topic"
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
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(quakeReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBackgroundWarningAnimation()
        stopTimeUpdater()
    }
}