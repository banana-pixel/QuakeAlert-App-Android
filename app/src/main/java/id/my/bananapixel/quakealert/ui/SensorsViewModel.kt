package id.my.bananapixel.quakealert.ui

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.domain.ServerHealthStatus
import id.my.bananapixel.quakealert.msg.Sensor
import id.my.bananapixel.quakealert.util.Log
import id.my.bananapixel.quakealert.util.distanceKm
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SensorsUiState(
    val stations: List<Sensor> = emptyList(),
    val status: ServerHealthStatus = ServerHealthStatus.CONNECTING,
    val latency: Int = 0,
    val isError: Boolean = false,
    val isEmpty: Boolean = false,
    val hasReceivedFirstResult: Boolean = false
)

@OptIn(FlowPreview::class)
class SensorsViewModel(
    private val api: QuakeAlertApi,
    private val appRepository: Repository,
    private val sharedPrefs: SharedPreferences
) : ViewModel() {

    private val _isNearbyFilterActive = MutableStateFlow(false)
    val isNearbyFilterActive: StateFlow<Boolean> = _isNearbyFilterActive.asStateFlow()

    private val _rawStations = MutableStateFlow<List<Sensor>>(emptyList())
    
    val currentAlertRadius: StateFlow<Int> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == Repository.SHARED_PREFS_ALERT_RADIUS) {
                trySend(sp.getInt(Repository.SHARED_PREFS_ALERT_RADIUS, 500))
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
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

    private val _uiState = MutableStateFlow(SensorsUiState())
    val uiState: StateFlow<SensorsUiState> = combine(
        _uiState,
        _rawStations,
        _isNearbyFilterActive,
        currentAlertRadius
    ) { state, rawList, isNearby, alertRadius ->
        val filteredList = if (!isNearby) {
            rawList
        } else {
            val currentLat = appRepository.getUserLatitude()
            val currentLon = appRepository.getUserLongitude()
            
            if (currentLat.isNaN() || currentLon.isNaN()) {
                rawList
            } else {
                val radiusDouble = alertRadius.toDouble()
                rawList.filter { sensor ->
                    val sLat = sensor.latitude
                    val sLon = sensor.longitude
                    if (sLat != null && sLon != null) {
                        val dist = distanceKm(currentLat, currentLon, sLat, sLon)
                        dist <= radiusDouble
                    } else {
                        false
                    }
                }
            }
        }
        
        state.copy(
            stations = filteredList,
            isEmpty = filteredList.isEmpty() && state.hasReceivedFirstResult
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SensorsUiState())

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                fetchData()
                delay(3000)
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(status = ServerHealthStatus.CONNECTING) }
        viewModelScope.launch {
            fetchData()
        }
    }

    fun setNearbyFilter(isActive: Boolean) {
        _isNearbyFilterActive.value = isActive
    }

    private suspend fun fetchData() {
        val startTime = System.currentTimeMillis()
        try {
            val rawStations = api.getStations()
            val sortedStations = rawStations.sortedWith(
                compareByDescending<Sensor> { it.status.equals("online", ignoreCase = true) }
                    .thenBy { it.stationId ?: "" }
            )
            
            val latency = (System.currentTimeMillis() - startTime).toInt()
            val status = if (latency > 300) ServerHealthStatus.WARNING else ServerHealthStatus.HEALTHY

            _rawStations.value = sortedStations
            _uiState.update {
                it.copy(
                    status = status,
                    latency = latency,
                    isError = false,
                    hasReceivedFirstResult = true
                )
            }
        } catch (e: java.io.IOException) {
            val latency = (System.currentTimeMillis() - startTime).toInt()
            Log.w(TAG, "Network error fetching sensor stations: ${e.message}")
            _uiState.update {
                it.copy(
                    status = ServerHealthStatus.CRITICAL,
                    latency = latency,
                    isError = true,
                    hasReceivedFirstResult = true
                )
            }
        } catch (e: Exception) {
            val latency = (System.currentTimeMillis() - startTime).toInt()
            Log.e(TAG, "Unexpected internal error while fetching sensor stations", e)
            _uiState.update {
                it.copy(
                    status = ServerHealthStatus.CRITICAL,
                    latency = latency,
                    isError = true,
                    hasReceivedFirstResult = true
                )
            }
        }
    }

    companion object {
        private const val TAG = "SensorsViewModel"
    }
}
