package io.heckel.ntfy.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.heckel.ntfy.R
import io.heckel.ntfy.msg.Sensor
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SensorsAdapter : ListAdapter<Sensor, SensorsAdapter.SensorViewHolder>(DiffCallback) {

    class SensorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val statusBadge: TextView = view.findViewById(R.id.sensor_status_badge)
        private val stationName: TextView = view.findViewById(R.id.sensor_station_name)
        private val stationIdBadge: TextView = view.findViewById(R.id.sensor_station_id_badge)
        private val lastPing: TextView = view.findViewById(R.id.sensor_last_ping)
        private val rssiValue: TextView = view.findViewById(R.id.sensor_rssi_value)
        private val latencyValue: TextView = view.findViewById(R.id.sensor_latency_value)
        private val location: TextView = view.findViewById(R.id.sensor_location)

        fun bind(sensor: Sensor) {
            val context = itemView.context

            // Header Section
            stationName.text = "Station"
            location.text = sensor.location ?: "Unknown"
            lastPing.text = "Last ping: ${convertUtcToLocal(sensor.lastPing ?: "")}"
            stationIdBadge.text = sensor.stationId ?: "N/A"

            // Status Indicator (Rounded Square)
            val isOnline = sensor.status?.equals("online", ignoreCase = true) == true
            val bgColor = if (isOnline) "#804CAF50" else "#80F44336" // Semi-transparent green/red
            val statusText = if (isOnline) "Online" else "Offline"
            val iconRes = if (isOnline) R.drawable.ic_bolt_white_24dp else R.drawable.ic_warning_white_24dp

            statusBadge.text = statusText
            statusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor(bgColor))

            // Set icon
            val icon = ContextCompat.getDrawable(context, iconRes)
            statusBadge.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            statusBadge.compoundDrawablePadding = 8

            // Technical Badges
            rssiValue.text = sensor.rssi ?: "---"
            latencyValue.text = sensor.latency ?: "---"
        }

        private fun convertUtcToLocal(utcTimeString: String): String {
            if (utcTimeString.isEmpty()) return "---"
            return try {
                // Matches the format from your server.py
                val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = inputFormat.parse(utcTimeString)

                // Clean output: "09 Feb 2026, 17:47"
                val outputFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                outputFormat.timeZone = TimeZone.getDefault()

                if (date != null) outputFormat.format(date) else utcTimeString
            } catch (e: Exception) {
                utcTimeString
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sensor, parent, false)
        return SensorViewHolder(view)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    object DiffCallback : DiffUtil.ItemCallback<Sensor>() {
        override fun areItemsTheSame(oldItem: Sensor, newItem: Sensor): Boolean {
            return oldItem.stationId == newItem.stationId
        }

        override fun areContentsTheSame(oldItem: Sensor, newItem: Sensor): Boolean {
            return oldItem == newItem
        }
    }
}
