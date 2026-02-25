package id.my.bananapixel.quakealert.db

import android.content.Context
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.domain.AppError
import id.my.bananapixel.quakealert.domain.AppResult
import id.my.bananapixel.quakealert.ui.QuakeReport
import id.my.bananapixel.quakealert.util.Log
import id.my.bananapixel.quakealert.util.ValidationUtil
import id.my.bananapixel.quakealert.util.toValidOrNull
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
                    // Validate and map report to QuakeData including new sync_time field
                    val latitude = report.latitude.toValidOrNull()
                    val longitude = report.longitude.toValidOrNull()
                    val (lat, lon) = ValidationUtil.validateCoordinates(latitude, longitude) ?: (0.0 to 0.0)
                    val intensity = ValidationUtil.validateIntensity(report.intensitas_maks) ?: "I"
                    val location = ValidationUtil.validateLocation(report.lokasi) ?: "Unknown"
                    val quakeTime = QuakeReportParser.parseQuakeTime(report.waktu_kejadian)
                    val dur = ValidationUtil.validateDuration(runCatching { report.durasi.toInt() }.getOrNull()) ?: 0

                    QuakeData(
                        id = report.id.toString(),
                        magnitude = 0.0,
                        place = location,
                        time = quakeTime,
                        sync_time = System.currentTimeMillis(),
                        description = report.deskripsi,
                        latitude = lat,
                        longitude = lon,
                        pga = report.pga_maks.ifEmpty { "0" },
                        durasi = dur,
                        station_id = report.station_id.ifEmpty { "N/A" },
                        intensity = intensity
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
