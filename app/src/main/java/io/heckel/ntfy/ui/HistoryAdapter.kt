package io.heckel.ntfy.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.heckel.ntfy.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.mapPreview).clear(holder.mapPreview)
        holder.mapPreview.setImageDrawable(null)
        holder.mapPreview.tag = null
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = reports[position]
        holder.mapPreview.tag = item

        val context = holder.itemView.context
        val intensity = extractRomanNumeral(item.intensitas_maks)

        holder.badgeText.text = intensity
        holder.locationText.text = item.lokasi
        holder.stationText.text = item.station_id
        holder.timeText.text = convertUtcToLocal(item.waktu_kejadian)
        holder.descriptionText.text = item.deskripsi

        // Set PGA and DUR values
        holder.pgaValue.text = item.pga_maks
        holder.durValue.text = context.getString(R.string.history_dur_value, item.durasi)

        // Load static OSM preview map for smooth RecyclerView scrolling.
        val mapUrl = buildStaticMapUrl(item.latitude, item.longitude)
        Glide.with(holder.mapPreview)
            .load(mapUrl)
            .centerCrop()
            .into(holder.mapPreview)

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

    private fun buildStaticMapUrl(latitude: Double, longitude: Double): String {
        val latString = String.format(Locale.US, "%.6f", latitude)
        val lonString = String.format(Locale.US, "%.6f", longitude)
        return "https://staticmap.openstreetmap.de/staticmap.php?center=$latString,$lonString&zoom=10&size=320x320&markers=$latString,$lonString,red-pushpin"
    }

    private fun convertUtcToLocal(utcTimeString: String): String {
        if (utcTimeString.isEmpty()) return "---"
        return try {
            // Input format from ESP32: "2026-02-09 13:11:53" (UTC)
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(utcTimeString)

            // Output format: "09 Feb 2026, 21:05:15 WIB" (Dynamic Zone)
            val outputFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss z", Locale.getDefault())
            outputFormat.timeZone = TimeZone.getDefault() // Detects WIB, WITA, etc.

            if (date != null) outputFormat.format(date) else utcTimeString
        } catch (e: Exception) {
            utcTimeString
        }
    }

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
        val mapPreview: ImageView = view.findViewById(R.id.history_map_preview)

        init {
            stationText.typeface = Typeface.MONOSPACE

            mapPreview.setOnClickListener { view ->
                val quake = view.tag as? QuakeReport ?: return@setOnClickListener
                val context = view.context

                val uri = Uri.parse(
                    "geo:${quake.latitude},${quake.longitude}?q=${Uri.encode(quake.lokasi)}"
                )
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.google.android.apps.maps")
                context.startActivity(intent)
            }
        }
    }
}
