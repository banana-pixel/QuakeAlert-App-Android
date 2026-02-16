package id.my.bananapixel.quakealert.db

import id.my.bananapixel.quakealert.ui.QuakeReport
import org.json.JSONArray
import org.json.JSONException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parses the quake reports API JSON response into [QuakeReport] list.
 * Used by [QuakeRemoteMediator]; extracted for unit testing.
 */
object QuakeReportParser {

    /**
     * Parses API datetime string "yyyy-MM-dd HH:mm:ss" (UTC) to epoch millis.
     * Returns current time on parse failure or null/empty input.
     */
    fun parseQuakeTime(dateString: String?): Long {
        if (dateString.isNullOrEmpty()) return System.currentTimeMillis()
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(dateString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Expects a JSON array of objects with: id, waktu_kejadian, intensitas_maks, lokasi,
     * deskripsi, pga_maks, station_id, durasi, latitude, longitude.
     * Returns empty list for null, empty string, or invalid JSON.
     */
    fun parseReports(jsonBody: String): List<QuakeReport> {
        if (jsonBody.isBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(jsonBody)
            parseReportsFromArray(jsonArray)
        } catch (e: JSONException) {
            emptyList()
        }
    }

    internal fun parseReportsFromArray(jsonArray: JSONArray): List<QuakeReport> {
        val reports = mutableListOf<QuakeReport>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            reports.add(
                QuakeReport(
                    id = item.optInt("id"),
                    waktu_kejadian = item.optString("waktu_kejadian"),
                    intensitas_maks = item.optString("intensitas_maks"),
                    lokasi = item.optString("lokasi"),
                    deskripsi = item.optString("deskripsi"),
                    pga_maks = item.optString("pga_maks"),
                    station_id = item.optString("station_id"),
                    durasi = item.optInt("durasi"),
                    latitude = item.optDouble("latitude"),
                    longitude = item.optDouble("longitude")
                )
            )
        }
        return reports
    }
}
