package id.my.bananapixel.quakealert.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.msg.Sensor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val stations: List<Sensor> = emptyList()
)

class SettingsViewModel(
    private val api: QuakeAlertApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        fetchStations()
    }

    fun fetchStations() {
        viewModelScope.launch {
            try {
                val stations = api.getStations()
                _uiState.update { it.copy(stations = stations) }
            } catch (e: Exception) {
                // Settings uses fail-silent logic for markers
            }
        }
    }
}
