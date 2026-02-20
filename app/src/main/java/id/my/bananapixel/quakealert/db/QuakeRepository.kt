package id.my.bananapixel.quakealert.db

import android.content.Context
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.ui.QuakeReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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
                        place = report.lokasi.ifEmpty { "Unknown" },
                        time = QuakeReportParser.parseQuakeTime(report.waktu_kejadian),
                        description = report.deskripsi,
                        latitude = report.latitude.let { if (it.isNaN()) 0.0 else it },
                        longitude = report.longitude.let { if (it.isNaN()) 0.0 else it },
                        pga = report.pga_maks.ifEmpty { "0" },
                        durasi = report.durasi,
                        station_id = report.station_id.ifEmpty { "N/A" },
                        intensity = report.intensitas_maks.ifEmpty { "I" }
                    )
                }
                quakeDao.upsertAll(quakeEntities)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun executeFetchReports(context: Context): List<QuakeReport> = withContext(Dispatchers.IO) {
        val baseUrl = "https://quakealert.bananapixel.my.id"
        val api = QuakeAlertApi.create(context, baseUrl)
        val body = api.getLaporan()
        QuakeReportParser.parseReports(body.ifEmpty { "[]" })
    }

    val chatMessages: Flow<List<ChatMessage>> = chatDao.getAll()

    suspend fun saveChatMessage(message: ChatMessage) {
        withContext(Dispatchers.IO) {
            chatDao.insertAll(listOf(message))
        }
    }
}