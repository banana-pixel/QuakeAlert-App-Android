package id.my.bananapixel.quakealert.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.QuakeData
import id.my.bananapixel.quakealert.util.MmiDescription
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
        holder.descriptionText.text = MmiDescription.getDescription(context, item.intensity)
        holder.pgaValue.text = item.pga
        holder.durValue.text = "${item.durasi}s"
        holder.stationIdText.text = item.station_id

        // 2. Clean the Badge Text (Extract "X+" from "X+ (Ekstrem)")
        val cleanRoman = item.intensity.split(" ").firstOrNull()?.uppercase() ?: "I"
        holder.badgeText.text = cleanRoman

        // 3. Apply 3D Background Drawables based on Intensity
        // Instead of tinting, we swap the whole drawable to keep 3D effects
        val bgRes = when (cleanRoman) {
            "I", "II" -> R.drawable.bg_circle_3d_green
            "III", "IV" -> R.drawable.bg_circle_3d_yellow
            "V", "VI", "VII" -> R.drawable.bg_circle_3d_orange
            "VIII", "IX", "X", "X+" -> R.drawable.bg_circle_3d_red
            else -> R.drawable.bg_circle_3d_red
        }
        holder.badgeText.setBackgroundResource(bgRes)

        // 4. Handle Text Color for Visibility
        if (cleanRoman == "III" || cleanRoman == "IV") {
            // Black text is more readable on yellow
            holder.badgeText.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        } else {
            // White text for green, orange, and red
            holder.badgeText.setTextColor(Color.WHITE)
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
        val stationIdText: TextView = view.findViewById(R.id.history_station) // Corrected ID

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
                    val webIntent = Intent(Intent.ACTION_VIEW, uri)
                    context.startActivity(webIntent)
                }
            }
        }
    }
}