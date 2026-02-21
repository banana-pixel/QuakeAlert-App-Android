package id.my.bananapixel.quakealert.db

import android.content.Context
import androidx.paging.ExperimentalPagingApi
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.ui.QuakeReport
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

@OptIn(ExperimentalPagingApi::class)
class QuakeRemoteMediator(
    private val context: Context,
    private val database: Database
) : RemoteMediator<Int, QuakeData>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, QuakeData>
    ): MediatorResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Calculate Page Number
                val page = when (loadType) {
                    LoadType.REFRESH -> 1
                    LoadType.PREPEND -> return@withContext MediatorResult.Success(endOfPaginationReached = true)
                    LoadType.APPEND -> {
                        val lastItem = state.lastItemOrNull()
                        // If no items, we might need page 1, otherwise increment page count
                        if (lastItem == null) 1 else (state.pages.size + 1)
                    }
                }

                // 2. Fetch data safely
                val reports = fetchReportsFromApi(page)

                // 3. Map to Entity (Fixes 'intensity' and 'time' errors)
                val quakeEntities = reports.mapNotNull { report ->
                    try {
                        QuakeData(
                            id = report.id.toString(),
                            magnitude = 0.0,
                            place = report.lokasi.ifEmpty { "Unknown" },
                            time = QuakeReportParser.parseQuakeTime(report.waktu_kejadian),
                            description = report.deskripsi,
                            latitude = report.latitude.let { if (it.isNaN()) 0.0 else it },
                            longitude = report.longitude.let { if (it.isNaN()) 0.0 else it },
                            pga = report.pga_maks.ifEmpty { "0" },
                            durasi = runCatching { report.durasi.toInt().coerceIn(0, Int.MAX_VALUE) }.getOrDefault(0),
                            station_id = report.station_id.ifEmpty { "N/A" },
                            intensity = report.intensitas_maks.ifEmpty { "I" }
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                // 4. Save to Database
                if (quakeEntities.isNotEmpty()) {
                    database.withTransaction {
                        if (loadType == LoadType.REFRESH) {
                            database.quakeHistoryDao().clearAll() // Ensure you have a clearAll() or similar
                        }
                        database.quakeHistoryDao().upsertAll(quakeEntities)
                    }
                }

                MediatorResult.Success(endOfPaginationReached = reports.isEmpty())
            } catch (e: IOException) {
                // Return Success if we have cached data, so the app doesn't crash offline
                val hasData = database.quakeHistoryDao().count() > 0
                if (hasData) {
                    MediatorResult.Success(endOfPaginationReached = true)
                } else {
                    MediatorResult.Error(e)
                }
            } catch (e: Exception) {
                MediatorResult.Error(e)
            }
        }
    }

    private suspend fun fetchReportsFromApi(page: Int): List<QuakeReport> {
        val baseUrl = BuildConfig.APP_BASE_URL.trimEnd('/')
        val api = QuakeAlertApi.create(context, baseUrl)
        val body = api.getLaporan(page = page)
        return QuakeReportParser.parseReports(body.ifEmpty { "[]" })
    }
}