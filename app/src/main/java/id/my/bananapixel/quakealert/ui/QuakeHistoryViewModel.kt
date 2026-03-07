package id.my.bananapixel.quakealert.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.QuakeData
import id.my.bananapixel.quakealert.db.QuakeRepository
import id.my.bananapixel.quakealert.domain.AppError
import id.my.bananapixel.quakealert.domain.ClearQuakesUseCase
import id.my.bananapixel.quakealert.domain.FetchQuakesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Load state for quake history refresh. */
sealed class QuakeLoadState {
    data object Idle : QuakeLoadState()
    data object Loading : QuakeLoadState()
    data object Success : QuakeLoadState()
    data class Error(val message: String) : QuakeLoadState()
}

/**
 * ViewModel for quake history. Manages fetching and observing quake data.
 * NOTE: This replaces the ad-hoc quake logic that was mixed into SubscriptionsViewModel.
 * 
 * @param quakeRepository Repository for earthquake data
 * @param fetchQuakesUseCase Use case for fetching quakes (injected for testability)
 * @param clearQuakesUseCase Use case for clearing quakes (injected for testability)
 */
class QuakeHistoryViewModel(
    private val quakeRepository: QuakeRepository,
    private val fetchQuakesUseCase: FetchQuakesUseCase = FetchQuakesUseCase(quakeRepository),
    private val clearQuakesUseCase: ClearQuakesUseCase = ClearQuakesUseCase(quakeRepository)
) : ViewModel() {

    // UI observes quake data directly
    val quakes: Flow<List<QuakeData>> = quakeRepository.quakes

    // Loading state
    private val _quakeLoadState = MutableStateFlow<QuakeLoadState>(QuakeLoadState.Idle)
    val quakeLoadState: StateFlow<QuakeLoadState> = _quakeLoadState.asStateFlow()

    /**
     * Refresh quake data from the backend.
     */
    fun refreshQuakes(context: Context) = viewModelScope.launch {
        _quakeLoadState.value = QuakeLoadState.Loading
        val result = fetchQuakesUseCase(context)
        _quakeLoadState.value = if (result.isSuccess) {
            QuakeLoadState.Success
        } else {
            val error = result.exceptionOrNull()
            val message = when (error) {
                is AppError.NetworkError -> context.getString(R.string.error_connection_message)
                is AppError.ParseError -> context.getString(R.string.error_generic_message)
                else -> context.getString(R.string.error_generic_message)
            }
            QuakeLoadState.Error(message)
        }
    }

    /**
     * Clear all cached quakes.
     */
    fun clearAllQuakes() = viewModelScope.launch {
        clearQuakesUseCase()
    }
}
