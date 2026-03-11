package id.my.bananapixel.quakealert.db

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.db.QuakeMapper.toEntity
import id.my.bananapixel.quakealert.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException

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
                // 1. Calculate page number
                val page = when (loadType) {
                    LoadType.REFRESH -> 1
                    LoadType.PREPEND -> return@withContext MediatorResult.Success(endOfPaginationReached = true)
                    LoadType.APPEND -> {
                        val lastItem = state.lastItemOrNull()
                        if (lastItem == null) 1 else (state.pages.size + 1)
                    }
                }

                // 2. Fetch from API
                val reports = api.getLaporan(page = page)

                // 3. Map to entities using the central mapper — skip malformed records
                val validEntities = mutableListOf<QuakeData>()
                var skippedCount = 0
                for (report in reports) {
                    try {
                        validEntities.add(report.toEntity())
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "RemoteMediator: skipping malformed report id=${report.id}: ${e.message}")
                        skippedCount++
                    }
                }
                if (skippedCount > 0) {
                    Log.w(TAG, "RemoteMediator: skipped $skippedCount/${reports.size} malformed record(s) on page $page.")
                }

                // 4. Persist valid records in a transaction
                database.withTransaction {
                    if (loadType == LoadType.REFRESH) {
                        database.quakeHistoryDao().clearAll()
                    }
                    if (validEntities.isNotEmpty()) {
                        database.quakeHistoryDao().upsertAll(validEntities)
                    }
                }

                MediatorResult.Success(endOfPaginationReached = reports.isEmpty())

            } catch (e: CancellationException) {
                // Never swallow coroutine cancellation
                throw e

            } catch (e: IOException) {
                // Network failure — fall back to cached data if available so the UI
                // can still display the last-known list (correct offline behaviour for paging).
                val hasData = database.quakeHistoryDao().count() > 0
                Log.w(TAG, "Network error on page load (hasCache=$hasData): ${e.message}")
                if (hasData) {
                    MediatorResult.Success(endOfPaginationReached = true)
                } else {
                    MediatorResult.Error(e)
                }

            } catch (e: SerializationException) {
                // The API returned an entirely unparseable JSON structure (schema change).
                Log.e(TAG, "Serialization error: API schema may have changed", e)
                MediatorResult.Error(e)

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in RemoteMediator", e)
                MediatorResult.Error(e)
            }
        }
    }

    companion object {
        private const val TAG = "QuakeRemoteMediator"
    }
}