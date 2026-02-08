package io.heckel.ntfy.ui

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Repository

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sharedPrefs = requireContext().getSharedPreferences(Repository.SHARED_PREFS_ID, 0)
        val valueView = view.findViewById<TextView>(R.id.alert_radius_value)
        val seekBar = view.findViewById<SeekBar>(R.id.alert_radius_seekbar)

        val initialRadius = sharedPrefs.getInt(Repository.SHARED_PREFS_ALERT_RADIUS, DEFAULT_ALERT_RADIUS_KM)
        val initialProgress = (initialRadius / STEP_KM).coerceIn(0, MAX_PROGRESS)
        seekBar.max = MAX_PROGRESS
        seekBar.progress = initialProgress
        updateRadiusLabel(valueView, initialProgress)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateRadiusLabel(valueView, progress)
                if (fromUser) {
                    sharedPrefs.edit {
                        putInt(Repository.SHARED_PREFS_ALERT_RADIUS, progress * STEP_KM)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun updateRadiusLabel(valueView: TextView, progress: Int) {
        val radius = progress * STEP_KM
        valueView.text = if (radius >= MAX_RADIUS_KM) {
            "Radius: Global (All Quakes)"
        } else {
            "Radius: ${radius} km"
        }
    }

    companion object {
        private const val DEFAULT_ALERT_RADIUS_KM = 500
        private const val STEP_KM = 100
        private const val MAX_PROGRESS = 50
        private const val MAX_RADIUS_KM = MAX_PROGRESS * STEP_KM
    }
}
