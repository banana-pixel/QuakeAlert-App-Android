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
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.util.ValidationUtil
import id.my.bananapixel.quakealert.util.distanceKm
import android.content.SharedPreferences
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
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
 * @param appRepository Repository for general app settings
 * @param sharedPrefs SharedPreferences instance for reactive settings
 * @param fetchQuakesUseCase Use case for fetching quakes (injected for testability)
 * @param clearQuakesUseCase Use case for clearing quakes (injected for testability)
 */
@OptIn(FlowPreview::class)
class QuakeHistoryViewModel(
    private val quakeRepository: QuakeRepository,
    private val appRepository: Repository,
    private val sharedPrefs: SharedPreferences,
    private val fetchQuakesUseCase: FetchQuakesUseCase = FetchQuakesUseCase(quakeRepository),
    private val clearQuakesUseCase: ClearQuakesUseCase = ClearQuakesUseCase(quakeRepository)
) : ViewModel() {

    private val _isNearbyFilterActive = MutableStateFlow(false)
    val isNearbyFilterActive: StateFlow<Boolean> = _isNearbyFilterActive.asStateFlow()
    
    val currentAlertRadius: StateFlow<Int> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == Repository.SHARED_PREFS_ALERT_RADIUS) {
                trySend(sp.getInt(Repository.SHARED_PREFS_ALERT_RADIUS, 500))
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        // Emit initial value
        trySend(sharedPrefs.getInt(Repository.SHARED_PREFS_ALERT_RADIUS, 500))
        awaitClose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    .debounce(300L)
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = sharedPrefs.getInt(Repository.SHARED_PREFS_ALERT_RADIUS, 500)
    )

    // UI observes quake data directly
    val quakes: Flow<List<QuakeData>> = combine(
        quakeRepository.quakes,
        _isNearbyFilterActive,
        currentAlertRadius
    ) { list, isNearby, alertRadius ->
        if (!isNearby) return@combine list
        
        val currentLat = appRepository.getUserLatitude()
        val currentLon = appRepository.getUserLongitude()
        
        if (currentLat.isNaN() || currentLon.isNaN()) return@combine list
        
        val radiusDouble = alertRadius.toDouble()
        
        list.filter { quake ->
            val validCoordinates = ValidationUtil.validateCoordinates(quake.latitude, quake.longitude)
            if (validCoordinates != null) {
                val distance = distanceKm(currentLat, currentLon, validCoordinates.first, validCoordinates.second)
                distance <= radiusDouble
            } else {
                false
            }
        }
    }

    fun setNearbyFilter(isActive: Boolean) {
        _isNearbyFilterActive.value = isActive
    }

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
