package io.heckel.ntfy.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import io.heckel.ntfy.BuildConfig
import io.heckel.ntfy.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

class HistoryAdapter(
    private val reports: MutableList<QuakeReport> = mutableListOf()
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    companion object {
        private const val TAG = "HistoryAdapter"
    }

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

        // 1. Build the raw String URL using your Geoapify logic
        val mapUrl = buildStaticMapUrl(item.latitude, item.longitude)

        // 2. Wrap it in a GlideUrl with a User-Agent header
        val glideUrl = GlideUrl(mapUrl, LazyHeaders.Builder()
            .addHeader("User-Agent", "QuakeAlert-Android/1.0")
            .build())

        // 3. Single Glide call to handle loading, placeholders, and logging
        Glide.with(holder.mapPreview.context)
            .load(glideUrl)
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(R.drawable.ic_warning_amber_24dp)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.e(TAG, "Glide Load Failed for Geoapify URL: $mapUrl", e)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "Glide Load Success for Geoapify URL: $mapUrl")
                    return false
                }
            })
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
        val apiKey = BuildConfig.GEOAPIFY_API_KEY
        // Geoapify uses lonlat order (Longitude first, then Latitude)
        return "https://maps.geoapify.com/v1/staticmap?" +
                "style=osm-bright-smooth" +
                "&width=600&height=400" +
                "&center=lonlat:$longitude,$latitude" +
                "&zoom=14" +
                "&marker=lonlat:$longitude,$latitude;type:material;color:%23ff0000;icon:bolt" +
                "&apiKey=$apiKey"
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
