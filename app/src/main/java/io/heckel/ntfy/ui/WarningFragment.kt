package io.heckel.ntfy.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import io.heckel.ntfy.R
import io.heckel.ntfy.app.AlertState
import io.heckel.ntfy.msg.NotificationService

class WarningFragment : Fragment(R.layout.fragment_warning) {

    private lateinit var scanningLayout: LinearLayout
    private lateinit var alertLayout: ScrollView
    private lateinit var radarIcon: ImageView
    private lateinit var alertDetails: TextView
    private lateinit var intensityText: TextView
    private lateinit var safeActionsButton: Button
    private lateinit var safeActionsCard: MaterialCardView
    private lateinit var shareButton: Button
    private lateinit var dismissButton: Button

    private val resetHandler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable {
        AlertState.setActive(false)
    }

    private val quakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NotificationService.ACTION_QUAKE_ALERT) {
                val message = intent.getStringExtra("message") ?: ""
                val title = intent.getStringExtra("title") ?: ""
                val distance = intent.getStringExtra("distance") ?: "Unknown"

                intensityText.text = if (message.contains("IX")) "IX" else if (message.contains("VIII")) "VIII" else "VII"
                alertDetails.text = "Location: $distance away\n$message"

                AlertState.setActive(true)
                scheduleReset()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scanningLayout = view.findViewById(R.id.warning_scanning_layout)
        alertLayout = view.findViewById(R.id.warning_alert_layout)
        radarIcon = view.findViewById(R.id.warning_radar_icon)
        alertDetails = view.findViewById(R.id.warning_alert_details)
        intensityText = view.findViewById(R.id.warning_intensity_text)
        safeActionsButton = view.findViewById(R.id.warning_safe_actions_button)
        safeActionsCard = view.findViewById(R.id.warning_safe_actions_card)
        shareButton = view.findViewById(R.id.warning_share_button)
        dismissButton = view.findViewById(R.id.warning_dismiss_button)

        // Start pulsing animation
        val pulseAnimation = AnimationUtils.loadAnimation(context, R.anim.pulse)
        radarIcon.startAnimation(pulseAnimation)

        AlertState.isAlertActive.observe(viewLifecycleOwner) { isActive ->
            if (isActive) {
                scanningLayout.visibility = View.GONE
                alertLayout.visibility = View.VISIBLE
                radarIcon.clearAnimation()
            } else {
                scanningLayout.visibility = View.VISIBLE
                alertLayout.visibility = View.GONE
                radarIcon.startAnimation(pulseAnimation)
                safeActionsCard.visibility = View.GONE
            }
        }

        AlertState.latestAlert.observe(viewLifecycleOwner) { notification ->
            notification?.let {
                alertDetails.text = it.message
            }
        }

        safeActionsButton.setOnClickListener {
            safeActionsCard.visibility = if (safeActionsCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        shareButton.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "EARTHQUAKE ALERT: ${alertDetails.text}")
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }

        dismissButton.setOnClickListener {
            AlertState.setActive(false)
            resetHandler.removeCallbacks(resetRunnable)
        }
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

    private fun scheduleReset() {
        resetHandler.removeCallbacks(resetRunnable)
        resetHandler.postDelayed(resetRunnable, 10 * 60 * 1000) // 10 minutes
    }
}
