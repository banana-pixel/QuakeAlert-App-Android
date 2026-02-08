package io.heckel.ntfy.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import io.heckel.ntfy.R
import io.heckel.ntfy.app.AlertState

class WarningFragment : Fragment(R.layout.fragment_warning) {

    private lateinit var scanningLayout: LinearLayout
    private lateinit var alertLayout: ScrollView
    private lateinit var radarIcon: ImageView
    private lateinit var alertDetails: TextView
    private lateinit var shareButton: Button
    private lateinit var dismissButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scanningLayout = view.findViewById(R.id.warning_scanning_layout)
        alertLayout = view.findViewById(R.id.warning_alert_layout)
        radarIcon = view.findViewById(R.id.warning_radar_icon)
        alertDetails = view.findViewById(R.id.warning_alert_details)
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
            }
        }

        AlertState.latestAlert.observe(viewLifecycleOwner) { notification ->
            notification?.let {
                alertDetails.text = it.message // Or format specifically if you have magnitude/distance parsed
            }
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
        }
    }
}
