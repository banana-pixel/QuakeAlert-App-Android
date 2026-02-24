package id.my.bananapixel.quakealert.domain

import android.content.Context
import id.my.bananapixel.quakealert.db.QuakeRepository
import id.my.bananapixel.quakealert.util.Log

/**
 * UseCase for fetching quake reports from the backend.
 * Encapsulates the business logic of refreshing quake data.
 */
class FetchQuakesUseCase(
    private val quakeRepository: QuakeRepository
) {
    suspend operator fun invoke(context: Context): AppResult<Unit> {
        Log.d(TAG, "Fetching quakes...")
        val result = quakeRepository.fetchQuakes(context)
        return if (result.isSuccess) {
            Log.d(TAG, "Quakes fetched successfully")
            Result.success(Unit)
        } else {
            val error = result.exceptionOrNull()
            Log.e(TAG, "Failed to fetch quakes: ${error?.message}")
            Result.failure(error ?: AppError.UnknownError("Unknown fetch error"))
        }
    }

    companion object {
        private const val TAG = "FetchQuakesUseCase"
    }
}

/**
 * UseCase for clearing all cached quakes.
 */
class ClearQuakesUseCase(
    private val quakeRepository: QuakeRepository
) {
    suspend operator fun invoke() {
        Log.d(TAG, "Clearing quakes...")
        quakeRepository.clearQuakes()
        Log.d(TAG, "Quakes cleared")
    }

    companion object {
        private const val TAG = "ClearQuakesUseCase"
    }
}
