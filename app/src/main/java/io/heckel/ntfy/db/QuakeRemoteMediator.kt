package io.heckel.ntfy.db

import android.content.Context
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import io.heckel.ntfy.ui.QuakeReport
import io.heckel.ntfy.util.HttpUtil
import org.json.JSONArray
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
                        time = parseQuakeTime(report.waktu_kejadian), // Uses helper function correctly
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

    // --- Helper Functions (Must be OUTSIDE 'load') ---

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

    // Add the 'suspend' keyword here
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
            return parseReports(body)
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
}