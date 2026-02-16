package id.my.bananapixel.quakealert.db

import android.content.Context
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import id.my.bananapixel.quakealert.ui.QuakeReport
import id.my.bananapixel.quakealert.util.HttpUtil
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
                val quakeEntities = reports.map { report ->
                    QuakeData(
                        id = report.id.toString(),
                        magnitude = 0.0,
                        place = report.lokasi ?: "Unknown",
                        time = QuakeReportParser.parseQuakeTime(report.waktu_kejadian),
                        description = report.deskripsi ?: "",
                        latitude = report.latitude,
                        longitude = report.longitude,
                        pga = report.pga_maks ?: "0",
                        durasi = report.durasi,
                        station_id = report.station_id ?: "N/A",
                        intensity = report.intensitas_maks ?: "I" // Fixes missing parameter
                    )
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

    // --- API fetch ---
    private suspend fun fetchReportsFromApi(page: Int): List<QuakeReport> {
        val url = "https://quakealert.bananapixel.my.id/laporan?page=$page"

        // Now this call is legal because the parent function is also suspending
        val client = HttpUtil.defaultClient(context, url)

        val request = HttpUtil.requestBuilder(url).get().build()

        // Note: It is safe to use execute() here because 'load()' wraps this
        // whole process in Dispatchers.IO
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response: ${response.code}")
            val body = response.body?.string() ?: "[]"
            return QuakeReportParser.parseReports(body)
        }
    }
}