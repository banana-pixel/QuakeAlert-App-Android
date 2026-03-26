package id.my.bananapixel.quakealert.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.msg.Sensor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoverageUiState(
    val stations: List<Sensor> = emptyList(),
    val alertRadiusKm: Int = 10,
    val distanceLabel: String = "10 km"
)

class CoverageViewModel(
    private val api: QuakeAlertApi,
    private val repository: Repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoverageUiState())
    val uiState: StateFlow<CoverageUiState> = _uiState.asStateFlow()

    init {
        // Load initial radius from repository
        val initialRadius = repository.getAlertRadius().coerceIn(10, 500)
        _uiState.update { it.copy(
            alertRadiusKm = initialRadius,
            distanceLabel = "$initialRadius km"
        ) }
        fetchStations()
    }

    fun fetchStations() {
        viewModelScope.launch {
            try {
                // Use the UseCase or Repository here if following strict Clean Architecture,
                // but for now keeping the existing logic from SettingsViewModel.
                val stations = api.getStations()
                _uiState.update { it.copy(stations = stations) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Fail-silent for markers as per original logic
            }
        }
    }

    fun updateRadius(radius: Int) {
        val validatedRadius = radius.coerceIn(10, 500)
        _uiState.update { it.copy(
            alertRadiusKm = validatedRadius,
            distanceLabel = "$validatedRadius km"
        ) }
        repository.setAlertRadius(validatedRadius)
    }
}
