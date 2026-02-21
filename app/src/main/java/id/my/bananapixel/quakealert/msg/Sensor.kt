package id.my.bananapixel.quakealert.msg

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class Sensor(
    @SerialName("RSSI") val rssi: String? = null,
    @SerialName("last_ping") val lastPing: Long? = null,
    val latency: String? = null,
    val location: String? = null,
    @SerialName("station_id") val stationId: String? = null,
    val status: String? = null,
)