package id.my.bananapixel.quakealert.msg

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class Sensor(
    @SerializedName("RSSI") val rssi: String?,

    // FIX 1: Use "last_ping" because that is what server.py sends
    // FIX 2: Use Long because we just updated server.py to send a number
    @SerializedName("last_ping") val lastPing: Long?,

    val latency: String?,
    val location: String?,
    @SerializedName("station_id") val stationId: String?,
    val status: String?
)