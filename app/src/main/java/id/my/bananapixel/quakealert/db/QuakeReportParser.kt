package id.my.bananapixel.quakealert.db

import id.my.bananapixel.quakealert.ui.QuakeReport
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val quakeReportJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

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
            quakeReportJson.decodeFromString(ListSerializer(QuakeReport.serializer()), jsonBody)
        } catch (_: kotlinx.serialization.SerializationException) {
            emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
