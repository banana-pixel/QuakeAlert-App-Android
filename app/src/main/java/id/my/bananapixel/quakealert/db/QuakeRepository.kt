package id.my.bananapixel.quakealert.db

import android.content.Context
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.domain.AppError
import id.my.bananapixel.quakealert.domain.AppResult
import id.my.bananapixel.quakealert.ui.QuakeReport
import id.my.bananapixel.quakealert.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Repository interface for Quake data operations.
 * Single responsibility: manage quake history data.
 */
interface QuakeRepository {
    /**
     * Observes quake history as a Flow.
     * Stays populated even when offline (local DB as SSOT).
     */
    val quakes: Flow<List<QuakeData>>
    
    /**
     * Fetches quake reports from API and updates local DB.
     * @return [AppResult] with success or failure containing AppError
     */
    suspend fun fetchQuakes(context: Context): AppResult<Unit>
    
    /**
     * Clear all quakes from local DB.
     */
    suspend fun clearQuakes()
}

/**
 * Default implementation of QuakeRepository.
 */
class QuakeRepositoryImpl(
    private val quakeDao: QuakeHistoryDao
) : QuakeRepository {

    override val quakes: Flow<List<QuakeData>> = quakeDao.getAll()

    override suspend fun fetchQuakes(context: Context): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val reports = executeFetchReports(context)
            val quakeEntities = reports.mapNotNull { report ->
                try {
                    QuakeData(
                        id = report.id.toString(),
                        magnitude = 0.0,
                        place = report.lokasi.ifEmpty { "Unknown" },
                        time = QuakeReportParser.parseQuakeTime(report.waktu_kejadian),
                        description = report.deskripsi,
                        latitude = report.latitude.orZeroIfNaN(),
                        longitude = report.longitude.orZeroIfNaN(),
                        pga = report.pga_maks.ifEmpty { "0" },
                        durasi = runCatching { report.durasi.toInt().coerceIn(0, Int.MAX_VALUE) }.getOrDefault(0),
                        station_id = report.station_id.ifEmpty { "N/A" },
                        intensity = report.intensitas_maks.ifEmpty { "I" }
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed quake report: ${e.message}")
                    null
                }
            }
            quakeDao.upsertAll(quakeEntities)
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(AppError.NetworkError(e.message ?: "Network error"))
        } catch (e: Exception) {
            Result.failure(AppError.ParseError(e.message ?: "Parse error"))
        }
    }

    override suspend fun clearQuakes() = withContext(Dispatchers.IO) {
        quakeDao.clearAll()
    }

    private suspend fun executeFetchReports(context: Context): List<QuakeReport> {
        val baseUrl = id.my.bananapixel.quakealert.BuildConfig.APP_BASE_URL.trimEnd('/')
        val api = QuakeAlertApi.create(context, baseUrl)
        val body = api.getLaporan()
        return QuakeReportParser.parseReports(body)
    }

    private fun Double.orZeroIfNaN(): Double = if (this.isNaN()) 0.0 else this

    companion object {
        private const val TAG = "QuakeRepository"
    }
}
