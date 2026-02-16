package id.my.bananapixel.quakealert.msg

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class SensorStation(
    @SerializedName("station_id") val stationId: String,
    @SerializedName("last_ping") val lastPing: Long,
    val latency: String,
    val status: String,
    val location: String
)
