package io.heckel.ntfy.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.heckel.ntfy.BuildConfig
import io.heckel.ntfy.R
import io.heckel.ntfy.db.QuakeData
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val reports: MutableList<QuakeData> = mutableListOf()
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    fun updateData(newReports: List<QuakeData>) {
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
        holder.itemView.tag = item

        // 1. Text Data Binding
        holder.locationText.text = item.place
        holder.timeText.text = convertLongToDate(item.time)
        holder.descriptionText.text = item.description
        holder.pgaValue.text = item.pga
        holder.durValue.text = "${item.durasi}s"

        // 2. Clean the Badge Text (Show only IV, V, X+, etc.)
        val cleanRoman = item.intensity.split(" ").firstOrNull() ?: "I"
        holder.badgeText.text = cleanRoman

        // 3. Apply Colors based on Intensity
        val colorRes = getIntensityColor(cleanRoman)
        val color = ContextCompat.getColor(context, colorRes)
        holder.badgeText.backgroundTintList = ColorStateList.valueOf(color)

        // 4. Fix 'black' reference and Text Contrast
        if (colorRes == R.color.intensity_yellow) {
            // Use android.R.color.black to avoid unresolved reference errors
            holder.badgeText.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        } else {
            holder.badgeText.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }

        // 5. Map Image Binding
        val mapUrl = "https://maps.geoapify.com/v1/staticmap?" +
                "style=osm-bright-smooth&width=600&height=400" +
                "&center=lonlat:${item.longitude},${item.latitude}" +
                "&zoom=7&marker=lonlat:${item.longitude},${item.latitude};color:%23ff0000" +
                "&apiKey=${BuildConfig.GEOAPIFY_API_KEY}"

        Glide.with(context)
            .load(mapUrl)
            .placeholder(R.drawable.ic_map_logo)
            .error(R.drawable.ic_map_logo)
            .into(holder.mapPreview)
    }

    override fun getItemCount(): Int = reports.size

    // Helper to pick color based on Roman Numeral
    private fun getIntensityColor(roman: String): Int {
        return when (roman.uppercase()) {
            "I", "II" -> R.color.intensity_green
            "III", "IV" -> R.color.intensity_yellow
            "V", "VI", "VII" -> R.color.intensity_orange
            "VIII", "IX", "X", "X+" -> R.color.intensity_red
            else -> R.color.intensity_red // Default to red for safety
        }
    }

    private fun convertLongToDate(time: Long): String {
        val date = Date(time)
        val format = SimpleDateFormat("dd MMM yyyy, HH:mm:ss z", Locale.getDefault())
        return format.format(date)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val badgeText: TextView = view.findViewById(R.id.history_badge)
        val locationText: TextView = view.findViewById(R.id.history_location)
        val timeText: TextView = view.findViewById(R.id.history_time)
        val descriptionText: TextView = view.findViewById(R.id.history_description)
        val mapPreview: ImageView = view.findViewById(R.id.history_map_preview)
        val pgaValue: TextView = view.findViewById(R.id.history_pga_value)
        val durValue: TextView = view.findViewById(R.id.history_dur_value)

        init {
            itemView.setOnClickListener { v ->
                val quake = v.tag as? QuakeData ?: return@setOnClickListener
                val context = v.context
                val uri = Uri.parse("geo:${quake.latitude},${quake.longitude}?q=${Uri.encode(quake.place)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback if Maps app is not installed
                    val webIntent = Intent(Intent.ACTION_VIEW, uri)
                    context.startActivity(webIntent)
                }
            }
        }
    }
}