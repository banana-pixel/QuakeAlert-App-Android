package id.my.bananapixel.quakealert.domain

import android.content.Context
import id.my.bananapixel.quakealert.db.QuakeRepository
import id.my.bananapixel.quakealert.util.Log

/**
 * Use Case for fetching earthquake reports from the backend API.
 * 
 * Encapsulates the business logic of refreshing earthquake data. This use case:
 * - Calls the repository to fetch fresh earthquake data from the API
 * - Updates the local database cache
 * - Handles errors and converts them to domain-specific AppResult
 * 
 * ## Usage Example:
 * ```kotlin
 * class QuakeHistoryViewModel(repository: QuakeRepository) : ViewModel() {
 *     private val fetchQuakesUseCase = FetchQuakesUseCase(repository)
 *     
 *     fun refreshQuakes(context: Context) = viewModelScope.launch {
 *         val result = fetchQuakesUseCase(context)
 *         if (result.isSuccess) {
 *             // Handle success
 *         } else {
 *             // Handle error
 *         }
 *     }
 * }
 * ```
 * 
 * @property quakeRepository Repository for earthquake data operations
 * @see QuakeRepository
 * @see AppResult
 */
class FetchQuakesUseCase(
    private val quakeRepository: QuakeRepository
) {
    /**
     * Executes the use case to fetch earthquake reports.
     * 
     * @param context Android context required for API calls
     * @return [AppResult]<Unit> indicating success or failure with error details
     */
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
 * Use Case for clearing all cached earthquake records.
 * 
 * This use case removes all earthquake data from the local database cache.
 * Useful for:
 * - Manual database cleanup by user
 * - Resetting app state during development/testing
 * - Clearing outdated data before fresh sync
 * 
 * ## Usage Example:
 * ```kotlin
 * class QuakeHistoryViewModel(repository: QuakeRepository) : ViewModel() {
 *     private val clearQuakesUseCase = ClearQuakesUseCase(repository)
 *     
 *     fun clearQuakes() = viewModelScope.launch {
 *         clearQuakesUseCase()
 *         // Database is now empty
 *     }
 * }
 * ```
 * 
 * @property quakeRepository Repository for earthquake data operations
 * @see QuakeRepository
 */
class ClearQuakesUseCase(
    private val quakeRepository: QuakeRepository
) {
    /**
     * Executes the use case to clear all cached earthquakes.
     * 
     * This is a synchronous operation that deletes all records from the local database.
     * The UI will automatically update via the Flow from repository.quakes.
     */
    suspend operator fun invoke() {
        Log.d(TAG, "Clearing quakes...")
        quakeRepository.clearQuakes()
        Log.d(TAG, "Quakes cleared")
    }

    companion object {
        private const val TAG = "ClearQuakesUseCase"
    }
}

