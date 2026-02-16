package id.my.bananapixel.quakealert.db

import android.content.Context
import id.my.bananapixel.quakealert.ui.QuakeReport
import id.my.bananapixel.quakealert.util.HttpUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException

class QuakeRepository(
    private val context: Context,
    private val database: Database
) {
    private val quakeDao = database.quakeHistoryDao()
    private val chatDao = database.chatMessageDao()

    val quakes: Flow<List<QuakeData>> = quakeDao.getAll()

    // Inside QuakeRepository.kt

    suspend fun fetchQuakes(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val reports = executeFetchReports(context)
                val quakeEntities = reports.map { report ->
                    QuakeData(
                        id = report.id.toString(),
                        magnitude = 0.0,
                        place = report.lokasi ?: "Unknown",
                        time = parseQuakeTime(report.waktu_kejadian),
                        description = report.deskripsi ?: "",
                        latitude = report.latitude,
                        longitude = report.longitude,
                        pga = report.pga_maks ?: "0",
                        durasi = report.durasi,
                        station_id = report.station_id ?: "N/A",

                        // --- ADD THIS LINE TO FIX THE ERROR ---
                        intensity = report.intensitas_maks ?: "I"
                    )
                }
                quakeDao.upsertAll(quakeEntities)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Ensure this helper is also in your Repository
    private fun parseQuakeTime(dateString: String?): Long {
        if (dateString.isNullOrEmpty()) return System.currentTimeMillis()
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            format.parse(dateString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    // Add the context parameter
    private suspend fun executeFetchReports(context: Context): List<QuakeReport> {
        val url = "https://quakealert.bananapixel.my.id/laporan"

        // Use the context to build the client (required for certs/preferences)
        val client = HttpUtil.defaultClient(context, url)
        val request = HttpUtil.requestBuilder(url).get().build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val body = response.body?.string() ?: "[]"
                parseReports(body)
            }
        }
    }

    private fun parseReports(jsonBody: String): List<QuakeReport> {
        val jsonArray = JSONArray(jsonBody)
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

    val chatMessages: Flow<List<ChatMessage>> = chatDao.getAll()

    suspend fun saveChatMessage(message: ChatMessage) {
        withContext(Dispatchers.IO) {
            chatDao.insertAll(listOf(message))
        }
    }
}