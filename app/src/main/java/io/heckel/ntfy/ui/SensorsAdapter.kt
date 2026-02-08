package io.heckel.ntfy.ui

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
import io.heckel.ntfy.msg.SensorStation

class SensorsAdapter : ListAdapter<SensorStation, SensorsAdapter.SensorViewHolder>(DiffCallback) {

    class SensorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val stationId: TextView = view.findViewById(R.id.sensor_station_id)
        private val lastPing: TextView = view.findViewById(R.id.sensor_last_ping)
        private val latency: TextView = view.findViewById(R.id.sensor_latency)
        private val statusDot: View = view.findViewById(R.id.sensor_status_dot)
        private val statusText: TextView = view.findViewById(R.id.sensor_status_text)

        fun bind(station: SensorStation) {
            stationId.text = "Station #${station.stationId}"
            lastPing.text = "Last ping: ${station.lastPing}"
            latency.text = "Signal Strength: ${station.latency}"
            statusText.text = station.status

            val color = if (station.status.equals("online", ignoreCase = true)) {
                Color.parseColor("#4CAF50")
            } else {
                Color.parseColor("#F44336")
            }
            statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
            statusText.setTextColor(color)
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
