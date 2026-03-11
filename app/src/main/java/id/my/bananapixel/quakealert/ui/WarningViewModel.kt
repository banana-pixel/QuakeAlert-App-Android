package id.my.bananapixel.quakealert.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.domain.SensorStatus
import id.my.bananapixel.quakealert.domain.ServerHealthStatus
import id.my.bananapixel.quakealert.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class WarningUiState(
    val status: ServerHealthStatus = ServerHealthStatus.CONNECTING,
    val latency: Int = 0,
    val onlineCount: Int = 0
)

class WarningViewModel(
    private val api: QuakeAlertApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(WarningUiState())
    val uiState: StateFlow<WarningUiState> = _uiState.asStateFlow()

    private var isPollingActive = false

    fun startPolling() {
        if (isPollingActive) return
        isPollingActive = true
        viewModelScope.launch {
            while (isActive && isPollingActive) {
                fetchServerStatus()
                delay(3000)
            }
        }
    }

    fun stopPolling() {
        isPollingActive = false
    }

    private suspend fun fetchServerStatus() {
        val startTime = System.currentTimeMillis()
        try {
            val stations = api.getStations()
            val latency = (System.currentTimeMillis() - startTime).toInt()
            val onlineCount = stations.count { SensorStatus.fromApi(it.status) == SensorStatus.ONLINE }
            val status = if (latency > 300) ServerHealthStatus.WARNING else ServerHealthStatus.HEALTHY

            _uiState.update {
                it.copy(
                    status = status,
                    latency = latency,
                    onlineCount = onlineCount
                )
            }
        } catch (e: java.io.IOException) {
            // Standard network failure (timeout, no connectivity, server unreachable).
            val latency = (System.currentTimeMillis() - startTime).toInt()
            Log.w(TAG, "Network error fetching server status: ${e.message}")
            _uiState.update {
                it.copy(
                    status = ServerHealthStatus.CRITICAL,
                    latency = latency,
                    onlineCount = 0
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Non-network failure (serialization bug, NPE, etc.) — log the full stack trace
            // so developers can distinguish a server outage from a code-level bug.
            val latency = (System.currentTimeMillis() - startTime).toInt()
            Log.e(TAG, "Unexpected error in WarningViewModel.fetchServerStatus — check if this is a parsing/mapping bug, not a server issue.", e)
            _uiState.update {
                it.copy(
                    status = ServerHealthStatus.CRITICAL,
                    latency = latency,
                    onlineCount = 0
                )
            }
        }
    }

    companion object {
        private const val TAG = "WarningViewModel"
    }
}
