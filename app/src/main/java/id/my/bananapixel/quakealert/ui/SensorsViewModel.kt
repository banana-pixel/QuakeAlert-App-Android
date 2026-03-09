package id.my.bananapixel.quakealert.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.domain.ServerHealthStatus
import id.my.bananapixel.quakealert.msg.Sensor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

class SensorsViewModel(
    private val api: QuakeAlertApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(SensorsUiState())
    val uiState: StateFlow<SensorsUiState> = _uiState.asStateFlow()

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

    private suspend fun fetchData() {
        val startTime = System.currentTimeMillis()
        try {
            val stations = api.getStations()
            val latency = (System.currentTimeMillis() - startTime).toInt()
            val status = if (latency > 300) ServerHealthStatus.WARNING else ServerHealthStatus.HEALTHY
            
            _uiState.update {
                it.copy(
                    stations = stations,
                    status = status,
                    latency = latency,
                    isError = false,
                    isEmpty = stations.isEmpty(),
                    hasReceivedFirstResult = true
                )
            }
        } catch (e: Exception) {
            val latency = (System.currentTimeMillis() - startTime).toInt()
            _uiState.update {
                it.copy(
                    status = ServerHealthStatus.CRITICAL,
                    latency = latency,
                    isError = true,
                    isEmpty = false, // When error occurs, we show error container, not empty container
                    hasReceivedFirstResult = true
                )
            }
        }
    }
}
