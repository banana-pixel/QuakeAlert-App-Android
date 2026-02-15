package io.heckel.ntfy.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.heckel.ntfy.db.Database
import io.heckel.ntfy.util.Log

class DatabaseCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting database cleanup")
        return try {
            val database = Database.getInstance(applicationContext)
            val chatDao = database.chatMessageDao()
            
            // Calculate threshold for 7 days ago
            val daysToKeep = 7
            val threshold = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            
            chatDao.pruneOldMessages(threshold)
            
            Log.d(TAG, "Database cleanup successful")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Database cleanup failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "NtfyDatabaseCleanup"
    }
}
