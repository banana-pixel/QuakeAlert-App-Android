package io.heckel.ntfy.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.heckel.ntfy.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class HistoryAdapter(
    private val reports: MutableList<QuakeReport> = mutableListOf()
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    fun updateData(newReports: List<QuakeReport>) {
        reports.clear()
        reports.addAll(newReports)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quake_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = reports[position]
        val context = holder.itemView.context
        val intensity = extractRomanNumeral(report.intensitas_maks)
        
        holder.badgeText.text = intensity
        holder.locationText.text = report.lokasi
        holder.stationText.text = report.station_id
        holder.timeText.text = report.waktu_kejadian
        holder.descriptionText.text = report.deskripsi

        // Set PGA and DUR values
        holder.pgaValue.text = report.pga_maks
        holder.durValue.text = context.getString(R.string.history_dur_value, report.durasi)

        // Setup MapView
        holder.mapView.onResume() // Briefly resume to update
        val mapController = holder.mapView.controller
        mapController.setZoom(10.0)
        val startPoint = GeoPoint(report.lintang, report.bujur)
        mapController.setCenter(startPoint)

        holder.mapView.overlays.clear()
        val marker = Marker(holder.mapView)
        marker.position = startPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

        // Attempt to set a red marker icon if available, otherwise default
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_foreground) // Placeholder
        // Since I don't have a guaranteed red marker resource, I'll use the default for now
        // but normally you'd use something like ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
        // and apply a color filter.

        holder.mapView.overlays.add(marker)
        holder.mapView.invalidate()
        holder.mapView.onPause() // "Freeze" the map as requested

        val intensityValue = romanToInt(intensity)
        val colorRes = when {
            intensityValue <= 3 -> R.color.intensity_green
            intensityValue <= 5 -> R.color.intensity_yellow
            intensityValue <= 7 -> R.color.intensity_orange
            else -> R.color.intensity_red
        }
        val badgeColor = ContextCompat.getColor(context, colorRes)
        holder.badgeText.backgroundTintList = ColorStateList.valueOf(badgeColor)
        holder.badgeText.setTextColor(
            ContextCompat.getColor(
                context,
                if (colorRes == R.color.intensity_yellow) R.color.md_theme_onSurface else android.R.color.white
            )
        )
    }

    override fun getItemCount(): Int = reports.size

    private fun extractRomanNumeral(intensity: String): String {
        val trimmed = intensity.trim()
        val spaceIndex = trimmed.indexOf(' ')
        return if (spaceIndex > 0) trimmed.substring(0, spaceIndex) else trimmed
    }

    private fun romanToInt(roman: String): Int {
        val values = mapOf(
            'I' to 1,
            'V' to 5,
            'X' to 10
        )
        var total = 0
        var previous = 0
        roman.uppercase().forEach { char ->
            val value = values[char] ?: 0
            if (value > previous) {
                total += value - 2 * previous
            } else {
                total += value
            }
            previous = value
        }
        return total
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val badgeText: TextView = view.findViewById(R.id.history_badge)
        val locationText: TextView = view.findViewById(R.id.history_location)
        val stationText: TextView = view.findViewById(R.id.history_station)
        val timeText: TextView = view.findViewById(R.id.history_time)
        val descriptionText: TextView = view.findViewById(R.id.history_description)
        val pgaValue: TextView = view.findViewById(R.id.history_pga_value)
        val durValue: TextView = view.findViewById(R.id.history_dur_value)
        val mapView: MapView = view.findViewById(R.id.history_map)

        init {
            stationText.typeface = Typeface.MONOSPACE
            
            // Performance Optimization: Initialize MapView only once
            mapView.setMultiTouchControls(false)
            mapView.setBuiltInZoomControls(false)
            mapView.isClickable = false
            mapView.isEnabled = false
        }
    }
}
