package io.heckel.ntfy.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.format.DateUtils // Import this
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

            // --- 1. Header Section & Relative Time ---
            stationName.text = "Station" // Consider using sensor.name if available
            location.text = sensor.location ?: "Unknown"
            stationIdBadge.text = sensor.stationId ?: "N/A"

            // NEW: Calculate "5 mins ago" dynamically
            val lastPingSeconds = sensor.lastPing ?: 0L

            if (lastPingSeconds > 0) {
                val now = System.currentTimeMillis()
                val lastPingMs = lastPingSeconds * 1000
                val diffMs = now - lastPingMs

                // Custom Logic: Show seconds if less than 1 minute
                val timeText = when {
                    diffMs < 0 -> "Just now" // Handle if server clock is slightly ahead
                    diffMs < 60_000 -> "${diffMs / 1000}s ago" // e.g., "15s ago"
                    else -> DateUtils.getRelativeTimeSpanString(
                        lastPingMs,
                        now,
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                }

                lastPing.text = "Last ping: $timeText"
            } else {
                lastPing.text = "Last ping: Never"
            }

            // --- 2. Status Indicator (Online/Offline) ---
            // You can keep using the server's status string, OR calculate it yourself based on time
            // Example: Force offline if older than 5 mins (300,000ms)
            val isLive = (System.currentTimeMillis() - (lastPingSeconds * 1000)) < 300_000

            // Fallback to server status if you prefer
            // val isOnline = sensor.status?.equals("online", ignoreCase = true) == true

            val isOnline = isLive // Using the time-based calculation is usually more accurate

            val bgColor = if (isOnline) "#804CAF50" else "#80F44336" // Semi-transparent green/red
            val statusText = if (isOnline) "Online" else "Offline"
            val iconRes = if (isOnline) R.drawable.ic_bolt_white_24dp else R.drawable.ic_warning_white_24dp

            statusBadge.text = statusText
            statusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor(bgColor))

            // Set icon
            val icon = ContextCompat.getDrawable(context, iconRes)
            statusBadge.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            statusBadge.compoundDrawablePadding = 8

            // --- 3. Technical Badges ---
            rssiValue.text = sensor.rssi ?: "---"
            latencyValue.text = sensor.latency ?: "---"
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