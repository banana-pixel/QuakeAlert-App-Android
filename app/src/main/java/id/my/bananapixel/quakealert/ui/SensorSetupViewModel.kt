package id.my.bananapixel.quakealert.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// ─── Domain models ────────────────────────────────────────────────────────────

data class WifiNetworkItem(val ssid: String, val rssi: Int) {
    val displayName: String get() = "$ssid  (${rssi} dBm)"
}

// ─── One-shot UI events ───────────────────────────────────────────────────────

sealed class SensorSetupEvent {
    /** GPS posted successfully – advance to step 3 */
    object AdvanceToStep3 : SensorSetupEvent()
    /** Setup complete – dismiss the dialog */
    object SetupComplete : SensorSetupEvent()
    /** Show a Toast/Snackbar with this message */
    data class ShowError(val message: String) : SensorSetupEvent()
}

// ─── UI state ─────────────────────────────────────────────────────────────────

data class SensorSetupUiState(
    // Step 1 – WiFi connect
    val isEsp32Connected: Boolean = false,
    val networkStatusText: String = "Waiting for 'Quake-Setup' Wi-Fi…",

    // Step 2 – GPS
    val coordsText: String = "Fetching…",
    val accuracyText: String = "Accuracy: ±— meters",
    val isLocationReady: Boolean = false,
    val lat: Double = 0.0,
    val lon: Double = 0.0,

    // Step 3 – WiFi config
    val wifiNetworks: List<WifiNetworkItem> = emptyList(),
    val selectedSsid: String? = null,
    val isLoadingNetworks: Boolean = false,

    // Common
    val isPosting: Boolean = false,
    val event: SensorSetupEvent? = null,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class SensorSetupViewModel : ViewModel() {

    private companion object {
        const val ESP32 = "http://192.168.4.1"
        const val TIMEOUT_MS = 6_000
    }

    private val _uiState = MutableStateFlow(SensorSetupUiState())
    val uiState: StateFlow<SensorSetupUiState> = _uiState.asStateFlow()

    // ── Step 1 ────────────────────────────────────────────────────────────────

    /** Call from Fragment when ConnectivityManager reports the ESP32 AP is up. */
    fun onEsp32Connected() {
        _uiState.update {
            it.copy(
                isEsp32Connected = true,
                networkStatusText = "✓ Connected to Quake-Setup!"
            )
        }
    }

    // ── Step 2 ────────────────────────────────────────────────────────────────

    /** Call from Fragment after LocationCallback delivers a result. */
    fun onLocationReceived(lat: Double, lon: Double, accuracy: Float) {
        _uiState.update {
            it.copy(
                lat = lat,
                lon = lon,
                coordsText = String.format(Locale.getDefault(), "%.6f,  %.6f", lat, lon),
                accuracyText = "Accuracy: ±${accuracy.toInt()} meters",
                isLocationReady = true,
            )
        }
    }

    fun onLocationFailed() {
        _uiState.update {
            it.copy(
                coordsText = "Failed to get GPS",
                accuracyText = "Make sure Location is enabled.",
                isLocationReady = false,
            )
        }
    }

    /** POST GPS coords to ESP32, then automatically trigger a Wi-Fi scan. */
    fun postGpsToEsp32() {
        if (_uiState.value.isPosting) return
        val lat = _uiState.value.lat
        val lon = _uiState.value.lon
        _uiState.update { it.copy(isPosting = true) }

        viewModelScope.launch {
            try {
                val body = JSONObject().apply { put("lat", lat); put("lon", lon) }.toString()
                val code = withContext(Dispatchers.IO) { postJson("$ESP32/api/gps", body) }
                if (code == 200) {
                    _uiState.update { it.copy(isPosting = false) }
                    fetchWifiScan()
                    _uiState.update { it.copy(event = SensorSetupEvent.AdvanceToStep3) }
                } else {
                    _uiState.update {
                        it.copy(
                            isPosting = false,
                            event = SensorSetupEvent.ShowError("Server returned HTTP $code"),
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPosting = false,
                        event = SensorSetupEvent.ShowError(e.message ?: "Network error"),
                    )
                }
            }
        }
    }

    // ── Step 3 ────────────────────────────────────────────────────────────────

    fun selectSsid(ssid: String) {
        _uiState.update { it.copy(selectedSsid = ssid) }
    }

    fun postWifiConfig(password: String) {
        val ssid = _uiState.value.selectedSsid ?: return
        if (_uiState.value.isPosting) return
        _uiState.update { it.copy(isPosting = true) }

        viewModelScope.launch {
            try {
                val body = JSONObject().apply {
                    put("ssid", ssid)
                    put("password", password)
                    put("reboot", true)
                }.toString()
                withContext(Dispatchers.IO) { postJson("$ESP32/api/wifi_config", body) }
                // Device will reboot – connection closes; we treat any outcome as success
            } catch (_: Exception) { /* expected – device reboots */ }
            _uiState.update {
                it.copy(isPosting = false, event = SensorSetupEvent.SetupComplete)
            }
        }
    }

    /** Consume a one-shot event after the Fragment has handled it. */
    fun consumeEvent() = _uiState.update { it.copy(event = null) }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun fetchWifiScan() {
        _uiState.update { it.copy(isLoadingNetworks = true) }
        viewModelScope.launch {
            try {
                val networks = withContext(Dispatchers.IO) {
                    val conn = URL("$ESP32/api/wifi_scan").openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    val list = mutableListOf<WifiNetworkItem>()
                    if (conn.responseCode == 200) {
                        val arr = JSONObject(conn.inputStream.bufferedReader().readText())
                            .optJSONArray("networks") ?: JSONArray()
                        repeat(arr.length()) { i ->
                            val obj = arr.getJSONObject(i)
                            list += WifiNetworkItem(obj.getString("ssid"), obj.getInt("rssi"))
                        }
                    }
                    list
                }
                _uiState.update { it.copy(wifiNetworks = networks, isLoadingNetworks = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingNetworks = false,
                        event = SensorSetupEvent.ShowError("Wi-Fi scan failed: ${e.message}"),
                    )
                }
            }
        }
    }

    private fun postJson(endpoint: String, json: String): Int {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        OutputStreamWriter(conn.outputStream).use { it.write(json); it.flush() }
        return conn.responseCode
    }
}
