package id.my.bananapixel.quakealert.ui

import android.app.Application
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import kotlin.coroutines.resume

@Serializable
data class SensorConfigPayload(val ssid: String, val password: String, val lat: Double, val lon: Double)

data class SensorSetupUiState(
    val isConnectedToQuakeSetup: Boolean = false,
    val currentLat: Double? = null,
    val currentLon: Double? = null,
    val currentCity: String? = null,
    val scannedNetworks: List<String>? = null,
    val setupComplete: Boolean = false,
    val errorString: String? = null,
    val loadingLocation: Boolean = false,
    val locationPermissionDenied: Boolean = false,
    val savingConfig: Boolean = false,
    val locationErrorString: String? = null
)

class SensorSetupViewModel(
    application: Application,
    private val okHttpClient: OkHttpClient
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SensorSetupUiState())
    val uiState: StateFlow<SensorSetupUiState> = _uiState.asStateFlow()

    private val connectivityManager = application.getSystemService(ConnectivityManager::class.java)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var esp32Network: Network? = null

    private var fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(application)

    fun startNetworkMonitoring() {
        if (networkCallback != null) return

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                esp32Network = network
                checkWiFiConnection()
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                esp32Network = network
                checkWiFiConnection()
            }
            override fun onLost(network: Network) {
                if (network == esp32Network) {
                    esp32Network = null
                }
                if (_uiState.value.isConnectedToQuakeSetup) {
                    _uiState.update { it.copy(isConnectedToQuakeSetup = false) }
                }
            }
        }
        try {
            val req = android.net.NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            connectivityManager.registerNetworkCallback(req, networkCallback!!)
            checkWiFiConnection()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
    }

    fun stopNetworkMonitoring() {
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        networkCallback = null
    }

    fun checkWiFiConnection() {
        try {
            val wifiManager = getApplication<Application>().getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo ?: return
            val ssid = info.ssid?.trim('"') ?: ""
            val connected = ssid.equals("QuakeSetup", ignoreCase = true)
            _uiState.update { it.copy(isConnectedToQuakeSetup = connected) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            _uiState.update { it.copy(isConnectedToQuakeSetup = false) }
        }
    }

    fun markLocationPermissionDenied() {
        _uiState.update { it.copy(locationPermissionDenied = true) }
    }

    fun fetchLocation(forceGps: Boolean = false) {
        val prefs = getApplication<Application>().getSharedPreferences("MainPreferences", 0)
        if (!forceGps && prefs.contains("UserLatitude") && prefs.contains("UserLongitude")) {
            val lat = Double.fromBits(prefs.getLong("UserLatitude", 0L))
            val lon = Double.fromBits(prefs.getLong("UserLongitude", 0L))
            val city = prefs.getString("UserCityName", "") ?: "Offline (Coordinates saved)"
            _uiState.update { it.copy(currentLat = lat, currentLon = lon, currentCity = city, loadingLocation = false, locationErrorString = null) }
            return
        }

        _uiState.update { it.copy(loadingLocation = true, locationPermissionDenied = false, locationErrorString = null) }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    processLocation(loc.latitude, loc.longitude)
                } else {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { l -> 
                            if (l != null) processLocation(l.latitude, l.longitude) 
                            else _uiState.update { it.copy(loadingLocation = false, locationErrorString = "Location Unknown") }
                        }
                        .addOnFailureListener { 
                            _uiState.update { it.copy(loadingLocation = false, locationErrorString = "Location Error") }
                        }
                }
            }.addOnFailureListener {
                try {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { l -> 
                            if (l != null) processLocation(l.latitude, l.longitude) 
                            else _uiState.update { it.copy(loadingLocation = false, locationErrorString = "Location Unknown") }
                        }
                        .addOnFailureListener {
                            _uiState.update { it.copy(loadingLocation = false, locationErrorString = "Location Error") }
                        }
                } catch (e: SecurityException) {
                    _uiState.update { it.copy(locationPermissionDenied = true, loadingLocation = false) }
                }
            }
        } catch (e: SecurityException) {
            _uiState.update { it.copy(locationPermissionDenied = true, loadingLocation = false) }
        }
    }

    fun processLocation(lat: Double, lon: Double) {
        _uiState.update { it.copy(currentLat = lat, currentLon = lon, locationErrorString = null) }
        resolveCityName(lat, lon)
    }

    private fun resolveCityName(lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val city = try {
                kotlinx.coroutines.withTimeoutOrNull(3000L) {
                    val geo = Geocoder(getApplication(), Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        suspendCancellableCoroutine { cont ->
                            geo.getFromLocation(lat, lon, 1) { addrs ->
                                if (cont.isActive) cont.resume(addrs.firstOrNull()?.locality ?: "Unknown City")
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        geo.getFromLocation(lat, lon, 1)?.firstOrNull()?.locality ?: "Unknown City"
                    }
                } ?: "Offline (Coordinates saved)"
            } catch (e: Exception) { 
                if (e is CancellationException) throw e
                "Offline (Coordinates saved)" 
            }

            _uiState.update { it.copy(currentCity = city, loadingLocation = false) }
        }
    }

    fun fetchAvailableNetworksFromEsp32() {
        _uiState.update { it.copy(scannedNetworks = null, errorString = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val clientBuilder = okHttpClient.newBuilder()
                esp32Network?.let { clientBuilder.socketFactory(it.socketFactory) }
                val client = clientBuilder
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()

                val response = client.newCall(Request.Builder().url("http://192.168.4.1/scan").get().build()).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val arr = org.json.JSONArray(body)
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val s = arr.getString(i)
                        if (s.isNotBlank() && !list.contains(s)) list.add(s)
                    }
                    _uiState.update { it.copy(scannedNetworks = list) }
                } else {
                    _uiState.update { it.copy(scannedNetworks = emptyList(), errorString = "Scan failed — type SSID manually") }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SensorSetup", "ESP32 scan error", e)
                _uiState.update { it.copy(scannedNetworks = emptyList(), errorString = "Network error — type SSID manually") }
            }
        }
    }

    fun pushConfigToEsp32(ssid: String, pass: String) {
        val lat = _uiState.value.currentLat ?: return
        val lon = _uiState.value.currentLon ?: return

        _uiState.update { it.copy(savingConfig = true, errorString = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = Json.encodeToString(SensorConfigPayload(ssid, pass, lat, lon))
                val body = json.toRequestBody("application/json".toMediaType())
                
                val clientBuilder = okHttpClient.newBuilder()
                esp32Network?.let { clientBuilder.socketFactory(it.socketFactory) }
                val client = clientBuilder.build()
                
                val response = client.newCall(Request.Builder().url("http://192.168.4.1/config").post(body).build()).execute()

                if (response.isSuccessful) {
                    _uiState.update { it.copy(setupComplete = true, savingConfig = false) }
                } else {
                    _uiState.update { it.copy(errorString = "ESP32 Error: ${response.code}", savingConfig = false) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SensorSetup", "Push config failed", e)
                _uiState.update { it.copy(errorString = "Connection failed. Make sure you are on QuakeSetup.", savingConfig = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorString = null) }
    }
}
