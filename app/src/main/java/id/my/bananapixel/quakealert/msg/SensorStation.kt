package id.my.bananapixel.quakealert.msg

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SensorStation(
    @SerialName("station_id") val stationId: String,
    @SerialName("last_ping") val lastPing: Long,
    val latency: String,
    val status: String,
    val location: String,
)
