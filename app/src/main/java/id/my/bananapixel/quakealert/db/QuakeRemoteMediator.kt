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
import id.my.bananapixel.quakealert.util.ValidationUtil
import id.my.bananapixel.quakealert.util.toValidOrNull
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

@OptIn(ExperimentalPagingApi::class)
class QuakeRemoteMediator(
    private val database: Database,
    private val api: QuakeAlertApi
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
                        if (lastItem == null) 1 else (state.pages.size + 1)
                    }
                }

                // 2. Fetch data safely
                val reports = fetchReportsFromApi(page)

                // 3. Map to Entity (with validation)
                val quakeEntities = buildList {
                    for (report in reports) {
                        // Validate coordinates
                        val (lat, lon) = ValidationUtil.validateCoordinates(
                            report.latitude.toValidOrNull(), 
                            report.longitude.toValidOrNull()
                        ) ?: (0.0 to 0.0)
                        
                        // Validate intensity
                        val intensity = ValidationUtil.validateIntensity(report.intensitas_maks) ?: "I"
                        
                        // Validate location
                        val location = ValidationUtil.validateLocation(report.lokasi) ?: "Unknown"
                        
                        // Validate earthquake time
                        val dateString = report.waktu_kejadian
                        val earthquakeTime = if (dateString.isNullOrEmpty()) System.currentTimeMillis() else {
                            try {
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                                    timeZone = TimeZone.getTimeZone("UTC")
                                }.parse(dateString)?.time ?: System.currentTimeMillis()
                            } catch (e: Exception) {
                                System.currentTimeMillis()
                            }
                        }
                        if (!ValidationUtil.validateEarthquakeTime(earthquakeTime).let { it != null }) {
                            continue // Skip invalid times
                        }
                        
                        // Validate duration
                        val duration = ValidationUtil.validateDuration(
                            runCatching { report.durasi.toInt() }.getOrNull()
                        ) ?: 0
                        
                        add(
                            QuakeData(
                                id = report.id.toString(),
                                magnitude = 0.0,
                                place = location,
                                time = earthquakeTime,
                                sync_time = System.currentTimeMillis(),
                                description = report.deskripsi,
                                latitude = lat,
                                longitude = lon,
                                pga = report.pga_maks.ifEmpty { "0" },
                                durasi = duration,
                                station_id = report.station_id.ifEmpty { "N/A" },
                                intensity = intensity
                            )
                        )
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
                    return@withContext MediatorResult.Success(endOfPaginationReached = true)
                } else {
                    return@withContext MediatorResult.Error(e)
                }
            } catch (e: SerializationException) {
                return@withContext MediatorResult.Error(e)
            } catch (e: Exception) {
                return@withContext MediatorResult.Error(e)
            }
        }
    }

    private suspend fun fetchReportsFromApi(page: Int): List<QuakeReport> {
        return api.getLaporan(page = page)
    }
}