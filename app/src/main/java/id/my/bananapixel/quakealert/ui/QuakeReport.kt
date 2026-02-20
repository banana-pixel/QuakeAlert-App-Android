package id.my.bananapixel.quakealert.ui

import kotlinx.serialization.Serializable

@Serializable
data class QuakeReport(
    val id: Int = 0,
    val waktu_kejadian: String = "",
    val intensitas_maks: String = "",
    val lokasi: String = "",
    val deskripsi: String = "",
    val pga_maks: String = "",
    val station_id: String = "",
    val durasi: Double = 0.0,
    val latitude: Double = Double.NaN,
    val longitude: Double = Double.NaN
)
