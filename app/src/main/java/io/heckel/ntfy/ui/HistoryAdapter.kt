package io.heckel.ntfy.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.heckel.ntfy.R
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains("PAYLOAD_EXPAND")) {
            val report = reports[position]
            updateExpandState(holder, report, animate = false)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = reports[position]
        val intensity = extractRomanNumeral(report.intensitas_maks)
        
        holder.badgeText.text = intensity
        holder.locationText.text = report.lokasi
        holder.stationText.text = report.station_id
        holder.timeText.text = report.waktu_kejadian
        holder.descriptionText.text = report.deskripsi
        holder.pgaDurationText.text = "PGA Max: ${report.pga_maks} | Duration: ${report.durasi}s"

        val intensityValue = romanToInt(intensity)
        val colorRes = when {
            intensityValue <= 3 -> R.color.intensity_green
            intensityValue <= 5 -> R.color.intensity_yellow
            intensityValue <= 7 -> R.color.intensity_orange
            else -> R.color.intensity_red
        }
        val badgeColor = ContextCompat.getColor(holder.itemView.context, colorRes)
        holder.badgeText.backgroundTintList = ColorStateList.valueOf(badgeColor)
        holder.badgeText.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                if (colorRes == R.color.intensity_yellow) R.color.md_theme_onSurface else android.R.color.white
            )
        )

        // Initial expand state without animation
        updateExpandState(holder, report, animate = false)

        holder.itemView.setOnClickListener {
            // Implement Domino/Magnet expansion logic
            report.isExpanded = !report.isExpanded

            // 1. Define the 'Magnet Energy' transition
            val magnetTransition = TransitionSet().apply {
                addTransition(ChangeBounds()) // This moves the cards below like a domino
                addTransition(Fade())
                duration = 400
                interpolator = AccelerateDecelerateInterpolator() // Smooth magnet movement
            }

            // 2. Trigger the push-down on the entire list container
            val parent = holder.itemView.parent as? ViewGroup
            if (parent != null) {
                TransitionManager.beginDelayedTransition(parent, magnetTransition)
            }

            // 3. Change state & Update UI directly (NO notifyItemChanged)
            updateExpandState(holder, report, animate = true)
        }

        holder.viewMapButton.setOnClickListener {
            val uri = Uri.parse("geo:${report.latitude},${report.longitude}?q=${report.latitude},${report.longitude}(${report.lokasi})")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            holder.itemView.context.startActivity(intent)
        }
    }

    private fun updateExpandState(holder: ViewHolder, report: QuakeReport, animate: Boolean) {
        val isExpanded = report.isExpanded
        
        // Update visibility directly
        holder.expandableSection.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.locationText.maxLines = if (isExpanded) 5 else 1
        
        // 4. Animate the Chevron (Arrow) at the bottom center
        val targetRotation = if (isExpanded) 180f else 0f
        if (animate) {
            holder.expandIcon.animate()
                .rotation(targetRotation)
                .setDuration(400) // Match magnet transition duration
                .start()
        } else {
            holder.expandIcon.rotation = targetRotation
        }

        // Performance Optimization: Lazy Mapping & Lifecycle Management
        if (isExpanded) {
            // Delay map initialization until expansion animation is almost finished
            holder.mapView.postDelayed({
                if (report.isExpanded) {
                    initMap(holder.mapView, report)
                }
            }, 400)
        } else {
            // Saat kartu tertutup, hentikan penggunaan CPU oleh peta
            holder.mapView.onPause()
        }
    }

    private fun initMap(mapView: MapView, report: QuakeReport) {
        mapView.onResume()
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(12.0)
        
        val point = GeoPoint(report.latitude, report.longitude)
        mapView.controller.setCenter(point)
        
        mapView.overlays.clear()
        val marker = Marker(mapView)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = report.lokasi
        
        val icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_location_on_red)
        if (icon != null) {
            marker.icon = icon
        }
        
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    override fun getItemCount(): Int = reports.size

    private fun extractRomanNumeral(intensity: String): String {
        val trimmed = intensity.trim()
        val spaceIndex = trimmed.indexOf(' ')
        return if (spaceIndex > 0) trimmed.substring(0, spaceIndex) else trimmed
    }

    private fun romanToInt(roman: String): Int {
        val values = mapOf('I' to 1, 'V' to 5, 'X' to 10)
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
        val pgaDurationText: TextView = view.findViewById(R.id.history_pga_duration)
        val expandableSection: View = view.findViewById(R.id.history_expandable_section)
        val expandIcon: ImageView = view.findViewById(R.id.history_expand_icon)
        val mapView: MapView = view.findViewById(R.id.history_map)
        val viewMapButton: View = view.findViewById(R.id.history_view_map_button)

        init {
            stationText.typeface = Typeface.MONOSPACE
        }
    }
}
