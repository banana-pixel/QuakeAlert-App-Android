package id.my.bananapixel.quakealert.ui

import android.graphics.Color
import android.widget.ImageButton
import android.widget.TextView
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.domain.SensorStatus
import id.my.bananapixel.quakealert.msg.Sensor
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow

/**
 * Map Status Card: SEIS ID, Online/Offline, close button.
 * No map recenter on tap (handled by marker's setPanToView(false)).
 */
class MapStatusCard(
    layoutResId: Int,
    mapView: MapView
) : InfoWindow(layoutResId, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return
        val sensor = marker.relatedObject as? Sensor ?: return

        val stationIdView = mView.findViewById<TextView>(R.id.map_status_station_id)
        val badgeView = mView.findViewById<TextView>(R.id.map_status_badge)
        val closeBtn = mView.findViewById<ImageButton>(R.id.map_status_close)

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

        closeBtn.setOnClickListener { close() }
    }

    override fun onClose() {}
}
