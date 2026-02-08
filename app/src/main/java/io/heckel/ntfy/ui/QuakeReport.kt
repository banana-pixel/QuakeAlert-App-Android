package io.heckel.ntfy.ui

data class QuakeReport(
    val id: Int,
    val waktu_kejadian: String,
    val intensitas_maks: String,
    val lokasi: String,
    val deskripsi: String,
    val pga_maks: String,
    val station_id: String
)
