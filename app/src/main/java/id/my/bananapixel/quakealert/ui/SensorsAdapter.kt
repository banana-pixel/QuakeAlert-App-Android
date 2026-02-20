package id.my.bananapixel.quakealert.ui

import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.msg.Sensor

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

            // --- 1. Header Section & Station Info ---
            stationName.text = "Station" // Consider using sensor.name if added to model
            location.text = sensor.location ?: "Unknown"
            stationIdBadge.text = sensor.stationId ?: "N/A"

            // --- 2. Dynamic Relative Time Calculation ---
            val lastPingSeconds = sensor.lastPing ?: 0L
            val now = System.currentTimeMillis()
            val lastPingMs = lastPingSeconds * 1000L // Use Long to prevent overflow

            if (lastPingSeconds > 0) {
                val diffMs = now - lastPingMs

                val timeText = when {
                    diffMs < 0 -> "Just now"
                    diffMs < 60_000 -> "${diffMs / 1000}s ago" // Show seconds for fresh pings
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

            // --- 3. Status Badge Logic (Drawable Swapping) ---
            // An item is online ONLY if server says so AND it pinged in the last 5 mins (300k ms)
            val isOnline = sensor.status?.equals("online", ignoreCase = true) == true &&
                    (now - lastPingMs) < 300_000

            // Update Text
            statusBadge.text = if (isOnline) "Online" else "Offline"

            // Swap Background Drawables (No tinting needed)
            val bgRes = if (isOnline)
                R.drawable.bg_badge_3d_green_small
            else
                R.drawable.bg_badge_3d_red_small

            statusBadge.setBackgroundResource(bgRes)

            // Update Icons
            val iconRes = if (isOnline) R.drawable.ic_bolt_white_24dp else R.drawable.ic_warning_white_24dp
            val icon = ContextCompat.getDrawable(context, iconRes)
            statusBadge.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            statusBadge.compoundDrawablePadding = 8

            // Ensure text color is white for 3D backgrounds
            statusBadge.setTextColor(Color.WHITE)

            // --- 4. Technical Badges ---
            rssiValue.text = sensor.rssi ?: "---"
            val rawLatency = sensor.latency
            latencyValue.text = when {
                rawLatency.isNullOrBlank() -> "---"
                rawLatency.contains("ms", ignoreCase = true) -> rawLatency
                else -> "$rawLatency ms"
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

    companion object {
        object DiffCallback : DiffUtil.ItemCallback<Sensor>() {
            override fun areItemsTheSame(oldItem: Sensor, newItem: Sensor): Boolean {
                return oldItem.stationId == newItem.stationId
            }

            override fun areContentsTheSame(oldItem: Sensor, newItem: Sensor): Boolean {
                // IMPORTANT: Must check status and lastPing to trigger badge updates
                return oldItem.status == newItem.status &&
                        oldItem.lastPing == newItem.lastPing &&
                        oldItem.rssi == newItem.rssi &&
                        oldItem.latency == newItem.latency &&
                        oldItem.location == newItem.location
            }
        }
    }
}