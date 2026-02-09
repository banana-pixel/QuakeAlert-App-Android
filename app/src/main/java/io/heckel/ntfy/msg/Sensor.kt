package io.heckel.ntfy.msg

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class Sensor(
    @SerializedName("RSSI") val rssi: String?,
    @SerializedName("last_ping") val lastPing: String?,
    val latency: String?,
    val location: String?,
    @SerializedName("station_id") val stationId: String?,
    val status: String?
)
