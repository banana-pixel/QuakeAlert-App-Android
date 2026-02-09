package io.heckel.ntfy.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
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
        val item = reports[position]
        val context = holder.itemView.context
        val intensity = extractRomanNumeral(item.intensitas_maks)
        
        holder.badgeText.text = intensity
        holder.locationText.text = item.lokasi
        holder.stationText.text = item.station_id
        holder.timeText.text = item.waktu_kejadian
        holder.descriptionText.text = item.deskripsi

        // Set PGA and DUR values
        holder.pgaValue.text = item.pga_maks
        holder.durValue.text = context.getString(R.string.history_dur_value, item.durasi)

        // Setup MapView
        val point = GeoPoint(item.latitude, item.longitude)
        holder.mapView.controller.setCenter(point)
        holder.mapView.controller.setZoom(10.0)

        // Add Marker
        holder.mapView.overlays.clear()
        val marker = Marker(holder.mapView)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        
        // Use the red marker icon as requested
        val icon = ContextCompat.getDrawable(context, R.drawable.ic_location_on_red)
        marker.icon = icon

        holder.mapView.overlays.add(marker)
        
        // "Static" & Performant Map
        holder.mapView.setBuiltInZoomControls(false)
        holder.mapView.setMultiTouchControls(false)

        // Task 1: Absolute Touch Lock
        holder.mapView.setOnTouchListener { _, _ -> true }

        // Task 2: Google Maps Intent
        holder.mapView.setOnClickListener {
            val gmmIntentUri = Uri.parse("geo:${item.latitude},${item.longitude}?q=${Uri.encode(item.lokasi)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            context.startActivity(mapIntent)
        }

        // Task 3: Visual Polish
        holder.mapView.isClickable = true

        holder.mapView.invalidate()
        holder.mapView.onPause() // Lifecycle Optimization

        // Intensity Styling
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
        }
    }
}
