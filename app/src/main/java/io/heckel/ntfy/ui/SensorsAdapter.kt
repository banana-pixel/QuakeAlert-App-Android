package io.heckel.ntfy.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.heckel.ntfy.R
import io.heckel.ntfy.msg.SensorStation

class SensorsAdapter : ListAdapter<SensorStation, SensorsAdapter.SensorViewHolder>(DiffCallback) {

    class SensorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val statusBadge: TextView = view.findViewById(R.id.sensor_status_badge)
        private val stationName: TextView = view.findViewById(R.id.sensor_station_name)
        private val stationIdBadge: TextView = view.findViewById(R.id.sensor_station_id_badge)
        private val lastPing: TextView = view.findViewById(R.id.sensor_last_ping)
        private val rssiValue: TextView = view.findViewById(R.id.sensor_rssi_value)
        private val latencyValue: TextView = view.findViewById(R.id.sensor_latency_value)

        fun bind(station: SensorStation) {
            val context = itemView.context

            // Header Section
            stationName.text = "Station"
            lastPing.text = "Last ping: ${station.lastPing}"
            stationIdBadge.text = station.stationId

            // Status Indicator (Rounded Square)
            val isOnline = station.status.equals("online", ignoreCase = true)
            val bgColor = if (isOnline) "#804CAF50" else "#80F44336" // Semi-transparent green/red
            val statusText = if (isOnline) "Online" else "Offline"
            val iconRes = if (isOnline) R.drawable.ic_bolt_white_24dp else R.drawable.ic_warning_white_24dp

            statusBadge.text = statusText
            statusBadge.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor(bgColor))

            // Set icon
            val icon = ContextCompat.getDrawable(context, iconRes)
            statusBadge.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            statusBadge.compoundDrawablePadding = 8

            // Technical Badges
            rssiValue.text = "-53 dBm" // Mocked
            latencyValue.text = station.latency
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

    object DiffCallback : DiffUtil.ItemCallback<SensorStation>() {
        override fun areItemsTheSame(oldItem: SensorStation, newItem: SensorStation): Boolean {
            return oldItem.stationId == newItem.stationId
        }

        override fun areContentsTheSame(oldItem: SensorStation, newItem: SensorStation): Boolean {
            return oldItem == newItem
        }
    }
}
