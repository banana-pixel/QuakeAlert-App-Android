package id.my.bananapixel.quakealert.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.domain.SensorStatus
import id.my.bananapixel.quakealert.msg.Sensor

/**
 * Builds and populates a View used as a MapLibre custom info popup for a sensor marker.
 *
 * The popup is a plain [View] that the caller adds/removes from the map's container manually.
 * This class is a stateless factory — inflate once per Fragment, reuse for any sensor.
 */
class MapStatusCard(private val context: Context) {

    /**
     * Inflates the status card view.
     * Call this once and cache the result; call [bind] each time a different sensor is shown.
     */
    fun inflate(): View =
        LayoutInflater.from(context).inflate(R.layout.map_status_card, null, false)

    /**
     * Populates [cardView] with [sensor] data and wires up the close button.
     *
     * @param cardView  A view previously returned by [inflate].
     * @param sensor    The sensor whose data is shown.
     * @param onClose   Lambda called when the user taps the close button.
     */
    fun bind(cardView: View, sensor: Sensor, onClose: () -> Unit) {
        val stationIdView = cardView.findViewById<TextView>(R.id.map_status_station_id)
        val badgeView = cardView.findViewById<TextView>(R.id.map_status_badge)
        val closeBtn = cardView.findViewById<ImageButton>(R.id.map_status_close)

        stationIdView.text = sensor.stationId ?: "N/A"

        val now = System.currentTimeMillis()
        val lastPingMs = (sensor.lastPing ?: 0L) * 1000L
        val isOnline = SensorStatus.fromApi(sensor.status) == SensorStatus.ONLINE &&
            lastPingMs > 0 && (now - lastPingMs) < 300_000

        badgeView.text = if (isOnline) "Online" else "Offline"
        badgeView.setBackgroundResource(
            if (isOnline) R.drawable.bg_badge_flat_green
            else R.drawable.bg_badge_flat_red
        )
        badgeView.setTextColor(Color.WHITE)

        closeBtn.setOnClickListener { onClose() }
    }
}
