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
import id.my.bananapixel.quakealert.util.ValidationUtil
import id.my.bananapixel.quakealert.util.Log
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val reports: MutableList<QuakeData> = mutableListOf()
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    fun updateData(newReports: List<QuakeData>?) {
        if (newReports == null) {
            Log.w(TAG, "updateData received null list")
            reports.clear()
            notifyDataSetChanged()
            return
        }
        val oldSize = reports.size
        reports.clear()
        reports.addAll(newReports)
        if (oldSize != newReports.size) {
            notifyDataSetChanged()
        } else {
            notifyItemRangeChanged(0, newReports.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quake_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position < 0 || position >= reports.size) {
            Log.w(TAG, "Invalid position $position for adapter size ${reports.size}")
            return
        }
        val item = reports.getOrNull(position) ?: return
        val context = holder.itemView.context

        holder.itemView.tag = item

        // 1. Text Data Binding (with null safety)
        holder.locationText.text = item.place.ifEmpty { "Unknown Location" }
        holder.timeText.text = convertLongToDate(item.time)
        holder.descriptionText.text = MmiDescription.getDescription(
            context, 
            item.intensity.ifEmpty { "I" }
        )
        holder.pgaValue.text = item.pga.ifEmpty { "N/A" }
        holder.durValue.text = "${item.durasi}s"
        holder.stationIdText.text = item.station_id.ifEmpty { "UNKNOWN" }

        // 2. Validate and clean the Badge Text
        val cleanRoman = ValidationUtil.validateIntensity(item.intensity) ?: "I"
        holder.badgeText.text = cleanRoman

        // 3. Apply 3D Background Drawables based on Intensity
        val bgRes = when (cleanRoman) {
            "I", "II" -> R.drawable.bg_circle_3d_green
            "III", "IV" -> R.drawable.bg_circle_3d_yellow
            "V", "VI", "VII" -> R.drawable.bg_circle_3d_orange
            "VIII", "IX", "X", "X+" -> R.drawable.bg_circle_3d_red
            else -> R.drawable.bg_circle_3d_red
        }
        holder.badgeText.setBackgroundResource(bgRes)

        // 4. Handle Text Color for Visibility
        holder.badgeText.setTextColor(
            if (cleanRoman in listOf("III", "IV")) {
                ContextCompat.getColor(context, android.R.color.black)
            } else {
                Color.WHITE
            }
        )

        // 5. Map Image Binding (only if coordinates are valid)
        val validCoordinates = ValidationUtil.validateCoordinates(item.latitude, item.longitude)
        if (validCoordinates != null) {
            val (lat, lon) = validCoordinates
            val mapUrl = "https://maps.geoapify.com/v1/staticmap?" +
                    "style=osm-bright-smooth&width=600&height=400" +
                    "&center=lonlat:$lon,$lat" +
                    "&zoom=7&marker=lonlat:$lon,$lat;color:%23ff0000" +
                    "&apiKey=${BuildConfig.GEOAPIFY_API_KEY}"

            Glide.with(context)
                .load(mapUrl)
                .placeholder(R.drawable.ic_map_logo)
                .error(R.drawable.ic_map_logo)
                .into(holder.mapPreview)
        } else {
            // Show placeholder if coordinates invalid
            holder.mapPreview.setImageResource(R.drawable.ic_map_logo)
        }
    }

    override fun getItemCount(): Int = reports.size

    private fun convertLongToDate(time: Long): String {
        return try {
            val date = Date(time)
            val format = SimpleDateFormat("dd MMM yyyy, HH:mm:ss z", Locale.getDefault())
            format.format(date)
        } catch (e: Exception) {
            Log.w(TAG, "Error formatting date: ${e.message}")
            "Unknown Date"
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val badgeText: TextView = view.findViewById(R.id.history_badge)
        val locationText: TextView = view.findViewById(R.id.history_location)
        val timeText: TextView = view.findViewById(R.id.history_time)
        val descriptionText: TextView = view.findViewById(R.id.history_description)
        val mapPreview: ImageView = view.findViewById(R.id.history_map_preview)
        val pgaValue: TextView = view.findViewById(R.id.history_pga_value)
        val durValue: TextView = view.findViewById(R.id.history_dur_value)
        val stationIdText: TextView = view.findViewById(R.id.history_station)

        init {
            itemView.setOnClickListener { v ->
                val quake = v.tag as? QuakeData ?: return@setOnClickListener
                val context = v.context
                
                val validCoordinates = ValidationUtil.validateCoordinates(quake.latitude, quake.longitude)
                if (validCoordinates != null) {
                    val (lat, lon) = validCoordinates
                    val uri = Uri.parse("geo:$lat,$lon?q=${Uri.encode(quake.place)}")
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

    companion object {
        private const val TAG = "HistoryAdapter"
    }
}