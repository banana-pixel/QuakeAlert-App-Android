
package io.heckel.ntfy.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.heckel.ntfy.BuildConfig
import io.heckel.ntfy.R
import io.heckel.ntfy.db.QuakeData
import java.text.SimpleDateFormat
import java.util.*

class QuakePagingAdapter : PagingDataAdapter<QuakeData, QuakePagingAdapter.ViewHolder>(QuakeDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quake_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        holder.bind(item)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val badgeText: TextView = view.findViewById(R.id.history_badge)
        private val locationText: TextView = view.findViewById(R.id.history_location)
        private val timeText: TextView = view.findViewById(R.id.history_time)
        private val mapPreview: ImageView = view.findViewById(R.id.history_map_preview)

        fun bind(item: QuakeData) {
            val context = itemView.context
            locationText.text = item.place
            timeText.text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.time))
            
            // Shimmer and Image Loading with Glide
            val mapUrl = "https://maps.geoapify.com/v1/staticmap?style=osm-bright-smooth&width=600&height=400&center=lonlat:${item.magnitude},${item.magnitude}&zoom=7&apiKey=${BuildConfig.GEOAPIFY_API_KEY}"

            Glide.with(context)
                .load(mapUrl)
                .apply(RequestOptions()
                    .placeholder(R.drawable.bg_pill_green) // Use a placeholder that can act as a shimmer container or simple bg
                    .error(R.drawable.ic_cancel_gray_24dp) // Placeholder for broken links
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // Custom Disk Cache for offline support
                    .override(Target.SIZE_ORIGINAL) // Glide handles downsampling automatically based on ImageView size if not specified, but we can be explicit
                    .centerCrop())
                .into(mapPreview)
        }
    }

    object QuakeDiffCallback : DiffUtil.ItemCallback<QuakeData>() {
        override fun areItemsTheSame(oldItem: QuakeData, newItem: QuakeData): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: QuakeData, newItem: QuakeData): Boolean = oldItem == newItem
    }
}
