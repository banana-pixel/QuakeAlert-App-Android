package id.my.bananapixel.quakealert.db

import android.content.Context
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.db.QuakeMapper.toEntity
import id.my.bananapixel.quakealert.domain.AppError
import id.my.bananapixel.quakealert.domain.AppResult
import id.my.bananapixel.quakealert.ui.QuakeReport
import id.my.bananapixel.quakealert.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
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
 *
 * Uses [QuakeMapper] as the single source of truth for DTO → Entity mapping.
 * Malformed individual records are skipped with a logged warning rather than
 * corrupting the database with fake default data.
 */
class QuakeRepositoryImpl(
    private val quakeDao: QuakeHistoryDao,
    private val api: QuakeAlertApi
) : QuakeRepository {

    override val quakes: Flow<List<QuakeData>> = quakeDao.getAll()

    override suspend fun fetchQuakes(context: Context): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val reports = api.getLaporan()
            val (validEntities, skippedCount) = mapReportsToEntities(reports)

            if (skippedCount > 0) {
                Log.w(TAG, "Skipped $skippedCount/${reports.size} malformed report(s) — check logs above for details.")
            }

            if (validEntities.isEmpty() && reports.isNotEmpty()) {
                // Every single record was malformed — surface this as a parse error.
                return@withContext Result.failure(
                    AppError.ParseError(
                        "All ${reports.size} fetched report(s) were malformed and could not be stored."
                    )
                )
            }

            quakeDao.upsertAll(validEntities)
            Log.d(TAG, "Saved ${validEntities.size} valid quake record(s) to DB.")
            Result.success(Unit)

        } catch (e: IOException) {
            Log.e(TAG, "Network error while fetching quakes", e)
            Result.failure(AppError.NetworkError(e.message ?: "Network error"))

        } catch (e: SerializationException) {
            // The API returned a JSON structure we can't deserialize at all (schema change, etc.)
            Log.e(TAG, "Serialization error: API response schema may have changed", e)
            Result.failure(AppError.ParseError(e.message ?: "Serialization error"))

        } catch (e: Exception) {
            // Safety net — rethrow coroutine cancellation, handle everything else.
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Unexpected error while fetching quakes", e)
            Result.failure(AppError.UnknownError(e.message ?: "Unknown error"))
        }
    }

    override suspend fun clearQuakes() = withContext(Dispatchers.IO) {
        quakeDao.clearAll()
    }

    /**
     * Maps a list of raw API reports to valid [QuakeData] entities using [QuakeMapper].
     *
     * Records that fail validation are **skipped** (with a per-record warning log) rather
     * than stored with fake defaults. The caller decides what to do if [skippedCount] > 0.
     *
     * @return Pair(validEntities, skippedCount)
     */
    private fun mapReportsToEntities(reports: List<QuakeReport>): Pair<List<QuakeData>, Int> {
        val valid = mutableListOf<QuakeData>()
        var skipped = 0
        for (report in reports) {
            try {
                valid.add(report.toEntity())
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Skipping malformed report id=${report.id}: ${e.message}")
                skipped++
            }
        }
        return Pair(valid, skipped)
    }

    companion object {
        private const val TAG = "QuakeRepository"
    }
}

