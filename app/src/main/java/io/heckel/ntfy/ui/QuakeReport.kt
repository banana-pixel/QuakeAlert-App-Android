package io.heckel.ntfy.ui

data class QuakeReport(
    val id: Int,
    val station_id: String,
    val lokasi: String,
    val waktu_kejadian: String,
    val durasi: Double,
    val pga_maks: String,
    val intensitas_maks: String,
    val deskripsi: String,
    val latitude: Double,
    val longitude: Double,
    var isExpanded: Boolean = false
)
