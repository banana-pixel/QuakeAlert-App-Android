package id.my.bananapixel.quakealert.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.domain.ServerHealthStatus
import id.my.bananapixel.quakealert.msg.Sensor
import id.my.bananapixel.quakealert.util.Log
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
            // Fetch raw list from API and sort immediately: Online sensors bubble to the top, tied elements are sorted alphabetically.
            val rawStations = api.getStations()
            val sortedStations = rawStations.sortedWith(
                compareByDescending<Sensor> { it.status.equals("online", ignoreCase = true) }
                    .thenBy { it.stationId ?: "" }
            )
            
            val latency = (System.currentTimeMillis() - startTime).toInt()
            val status = if (latency > 300) ServerHealthStatus.WARNING else ServerHealthStatus.HEALTHY

            _uiState.update {
                it.copy(
                    stations = sortedStations,
                    status = status,
                    latency = latency,
                    isError = false,
                    isEmpty = sortedStations.isEmpty(),
                    hasReceivedFirstResult = true
                )
            }
        } catch (e: java.io.IOException) {
            // Standard network failure (timeout, no connectivity, server unreachable).
            val latency = (System.currentTimeMillis() - startTime).toInt()
            Log.w(TAG, "Network error fetching sensor stations: ${e.message}")
            _uiState.update {
                it.copy(
                    status = ServerHealthStatus.CRITICAL,
                    latency = latency,
                    isError = true,
                    isEmpty = false,
                    hasReceivedFirstResult = true
                )
            }
        } catch (e: Exception) {
            // Non-network failure (serialization bug, NPE, etc.) — log the full stack trace
            // so developers can distinguish a server outage from a code-level bug.
            val latency = (System.currentTimeMillis() - startTime).toInt()
            Log.e(TAG, "Unexpected internal error while fetching sensor stations — this is likely a bug in parsing/mapping code, NOT a server issue.", e)
            _uiState.update {
                it.copy(
                    status = ServerHealthStatus.CRITICAL,
                    latency = latency,
                    isError = true,
                    isEmpty = false,
                    hasReceivedFirstResult = true
                )
            }
        }
    }

    companion object {
        private const val TAG = "SensorsViewModel"
    }
}
