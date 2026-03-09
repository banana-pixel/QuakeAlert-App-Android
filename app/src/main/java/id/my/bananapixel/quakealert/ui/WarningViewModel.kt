package id.my.bananapixel.quakealert.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.domain.SensorStatus
import id.my.bananapixel.quakealert.domain.ServerHealthStatus
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
        } catch (e: Exception) {
            val latency = (System.currentTimeMillis() - startTime).toInt()
            _uiState.update {
                it.copy(
                    status = ServerHealthStatus.CRITICAL,
                    latency = latency,
                    onlineCount = 0
                )
            }
        }
    }
}
