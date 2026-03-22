package id.my.bananapixel.quakealert.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.databinding.ActivitySensorSetupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import kotlin.coroutines.resume

@Serializable
data class SensorConfigPayload(val ssid: String, val password: String, val lat: Double, val lon: Double)

enum class SetupStep {
    WIFI, LOCATION, CREDENTIALS
}

/**
 * SensorSetupActivity manages the sensor setup wizard flow.
 * Step 1 is WiFi connection to "QuakeSetup" SSID.
 * 
 * Features:
 * - Requests ACCESS_FINE_LOCATION permission (required on Android 10+ to read SSID)
 * - Monitors WiFi connection changes via ConnectivityManager.NetworkCallback
 * - Enables Next button when connected to "QuakeSetup" SSID
 * - Shows success indicator (checkmark) upon successful connection
 */
class SensorSetupActivity : BaseActivity() {

    private lateinit var binding: ActivitySensorSetupBinding
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private val wifiManager by lazy { getSystemService(WIFI_SERVICE) as WifiManager }
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isConnectedToQuakeSetup = false

    private var currentStep = SetupStep.WIFI
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLat: Double? = null
    private var currentLon: Double? = null
    private var currentCity: String? = null

    /**
     * Location permission launcher for Android 10+
     */
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, check current WiFi connection
            checkWiFiConnection()
            // Start monitoring network changes
            startNetworkMonitoring()
        } else {
            // Permission denied, show message
            Toast.makeText(
                this,
                "Location permission is required to verify WiFi connection on Android 10+",
                Toast.LENGTH_LONG
            ).show()
            // Still start monitoring (will work on older Android versions)
            startNetworkMonitoring()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivitySensorSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()
        setupListeners()
        
        // Request location permission if needed (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                // Permission already granted
                checkWiFiConnection()
                startNetworkMonitoring()
            }
        } else {
            // Android 9 and below don't require location permission for WiFi info
            checkWiFiConnection()
            startNetworkMonitoring()
        }
    }

    private fun setupUI() {
        // Set title and step indicator
        binding.sensorSetupTitle.text = getString(R.string.sensor_setup_title)
        binding.sensorSetupStep.text = getString(R.string.sensor_setup_step_wifi)
        
        // Set instruction texts
        binding.sensorSetupInstructionTitle.text = getString(R.string.sensor_setup_wifi_title)
        binding.sensorSetupInstructionDetail.text = getString(R.string.sensor_setup_wifi_instructions)
        
        // Set SSID value
        binding.sensorSetupSsidValue.text = getString(R.string.sensor_setup_wifi_ssid)
        
        // Initially disable the Next button
        setNextButtonEnabled(false)
    }

    private fun setupListeners() {
        binding.sensorSetupBtnNext.setOnClickListener {
            onNextClicked()
        }
    }

    /**
     * Start monitoring WiFi network changes using ConnectivityManager.NetworkCallback.
     * This is the recommended approach for API 21+ and follows best practices.
     */
    private var esp32Network: android.net.Network? = null

    private fun startNetworkMonitoring() {
        // Remove old callback if it exists
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                super.onAvailable(network)
                esp32Network = network
                checkWiFiConnection()
            }

            override fun onCapabilitiesChanged(
                network: android.net.Network,
                networkCapabilities: android.net.NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                esp32Network = network
                checkWiFiConnection()
            }

            override fun onLost(network: android.net.Network) {
                super.onLost(network)
                if (network == esp32Network) {
                    esp32Network = null
                }
                // Network lost, disable button and hide success state
                if (isConnectedToQuakeSetup) {
                    isConnectedToQuakeSetup = false
                    runOnUiThread {
                        setNextButtonEnabled(false)
                        showDisconnectedState()
                    }
                }
            }
        }

        try {
            val networkRequest = android.net.NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Check if device is connected to "QuakeSetup" SSID.
     * Uses WifiManager.getConnectionInfo() for direct access.
     */
    private fun checkWiFiConnection() {
        try {
            val connectionInfo = wifiManager.connectionInfo
            if (connectionInfo != null) {
                // Get SSID, removing quotes if present
                val ssid = connectionInfo.ssid?.trim('"') ?: ""
                val isConnected = ssid.equals("Quake-Setup", ignoreCase = true)
                
                // Update state if changed
                if (isConnected != isConnectedToQuakeSetup) {
                    isConnectedToQuakeSetup = isConnected
                    runOnUiThread {
                        if (isConnected) {
                            setNextButtonEnabled(true)
                            showSuccessState()
                        } else {
                            setNextButtonEnabled(false)
                            showDisconnectedState()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // If permission denied or other error, disable button
            e.printStackTrace()
            runOnUiThread {
                setNextButtonEnabled(false)
            }
        }
    }

    /**
     * Update button state and enable/disable based on connection.
     */
    private fun setNextButtonEnabled(enabled: Boolean) {
        binding.sensorSetupBtnNext.apply {
            this.isEnabled = enabled
            this.alpha = if (enabled) 1.0f else 0.5f
        }
    }

    /**
     * Show visual feedback when successfully connected to QuakeSetup.
     */
    private fun showSuccessState() {
        // Hide WiFi info section
        binding.sensorSetupInstructionTitle.visibility = View.GONE
        binding.sensorSetupInstructionDetail.visibility = View.GONE
        
        // Hide SSID display container
        val ssidContainer = binding.root.findViewById<View>(R.id.sensor_setup_ssid_label)?.parent
        if (ssidContainer is View) {
            ssidContainer.visibility = View.GONE
        }
        
        // Show success container
        binding.sensorSetupSuccessContainer.visibility = View.VISIBLE
    }

    /**
     * Show visual feedback when disconnected.
     */
    private fun showDisconnectedState() {
        // Show WiFi info section
        binding.sensorSetupInstructionTitle.visibility = View.VISIBLE
        binding.sensorSetupInstructionDetail.visibility = View.VISIBLE
        
        // Show SSID display container
        val ssidContainer = binding.root.findViewById<View>(R.id.sensor_setup_ssid_label)?.parent
        if (ssidContainer is View) {
            ssidContainer.visibility = View.VISIBLE
        }
        
        // Hide success container
        binding.sensorSetupSuccessContainer.visibility = View.GONE
    }

    /**
     * Handle Next button click.
     */
    private fun onNextClicked() {
        when (currentStep) {
            SetupStep.WIFI -> {
                if (!isConnectedToQuakeSetup) {
                    Toast.makeText(this, "Not connected to Quake-Setup", Toast.LENGTH_SHORT).show()
                    return
                }
                transitionToLocationStep()
            }
            SetupStep.LOCATION -> {
                if (currentLat == null || currentLon == null || currentCity == null) {
                    Toast.makeText(this, "Still fetching location...", Toast.LENGTH_SHORT).show()
                    return
                }
                transitionToCredentialsStep()
            }
            SetupStep.CREDENTIALS -> {
                val ssid = findViewById<android.widget.EditText>(R.id.sensor_setup_ssid_input)?.text?.toString() ?: ""
                val pass = findViewById<android.widget.EditText>(R.id.sensor_setup_pass_input)?.text?.toString() ?: ""
                
                if (ssid.isBlank()) {
                    Toast.makeText(this, "Please enter your WiFi SSID", Toast.LENGTH_SHORT).show()
                    return
                }
                if (pass.isBlank()) {
                    Toast.makeText(this, "Please enter your WiFi Password", Toast.LENGTH_SHORT).show()
                    return
                }
                
                binding.sensorSetupBtnNext.isEnabled = false
                binding.sensorSetupBtnNext.text = "Configuring..."
                
                lifecycleScope.launch(Dispatchers.IO) { 
                    pushConfigToEsp32(currentLat!!, currentLon!!, currentCity!!, ssid, pass) 
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check when activity resumes
        checkWiFiConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister network callback to prevent memory leaks
        networkCallback?.let { 
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun transitionToLocationStep() {
        currentStep = SetupStep.LOCATION
        binding.sensorSetupStep.text = "Step 2: Location Config"
        binding.sensorSetupTitle.text = "Fetching Coordinates"
        
        binding.sensorSetupWifiContainer.visibility = View.GONE
        binding.sensorSetupLocationContainer.visibility = View.VISIBLE
        
        setNextButtonEnabled(false)
        binding.sensorSetupBtnNext.text = "Confirm & Send"
        
        fetchLocation()
    }

    private fun transitionToCredentialsStep() {
        currentStep = SetupStep.CREDENTIALS
        binding.sensorSetupStep.text = "Step 3: Home Network"
        binding.sensorSetupTitle.text = "Connect to WiFi"
        
        binding.sensorSetupLocationContainer.visibility = View.GONE
        findViewById<View>(R.id.sensor_setup_credentials_container)?.visibility = View.VISIBLE
        
        binding.sensorSetupBtnNext.text = "Finish & Configure Sensor"
    }

    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED 
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission not granted.", Toast.LENGTH_LONG).show()
            binding.sensorSetupLocationTitle.text = "Permission Denied"
            binding.sensorSetupLocationSubtitle.text = "Please enable location permission to continue."
            return
        }
        
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    currentLat = location.latitude
                    currentLon = location.longitude
                    
                    binding.sensorSetupLatlonValue.text = "${String.format(Locale.getDefault(), "%.4f", currentLat)}, ${String.format(Locale.getDefault(), "%.4f", currentLon)}"
                    
                    resolveCityName(location.latitude, location.longitude)
                } else {
                    binding.sensorSetupLocationTitle.text = "Location Unknown"
                    binding.sensorSetupLocationSubtitle.text = "Could not get current location."
                }
            }
            .addOnFailureListener {
                binding.sensorSetupLocationTitle.text = "Location Error"
                binding.sensorSetupLocationSubtitle.text = it.localizedMessage ?: "Failed to get location."
            }
    }

    private fun resolveCityName(lat: Double, lon: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@SensorSetupActivity, Locale.getDefault())
                val city = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(lat, lon, 1) { addresses ->
                            val locality = addresses.firstOrNull()?.locality ?: "Unknown City"
                            if (continuation.isActive) continuation.resume(locality)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.locality ?: "Unknown City"
                }

                withContext(Dispatchers.Main) {
                    currentCity = city
                    binding.sensorSetupCityValue.text = city
                    binding.sensorSetupLocationTitle.text = "Location Found"
                    binding.sensorSetupLocationSubtitle.text = "Please confirm the location for the sensor."
                    setNextButtonEnabled(true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    currentCity = "Unknown City"
                    binding.sensorSetupCityValue.text = "Unknown City (Error)"
                    binding.sensorSetupLocationTitle.text = "Location Found"
                    setNextButtonEnabled(true)
                }
            }
        }
    }

    private suspend fun pushConfigToEsp32(lat: Double, lon: Double, city: String, wifiSsid: String, wifiPass: String) {
        try {
            val payload = SensorConfigPayload(wifiSsid, wifiPass, lat, lon)
            val jsonString = Json.encodeToString(payload)
            
            val requestBody = jsonString.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("http://192.168.4.1/config")
                .post(requestBody)
                .build()
                
            val clientBuilder = OkHttpClient.Builder()
            esp32Network?.let {
                clientBuilder.socketFactory(it.socketFactory)
            }
            val client = clientBuilder.build()
            
            val response = client.newCall(request).execute()
            
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    binding.sensorSetupBtnNext.setBackgroundResource(R.drawable.bg_badge_3d_green_small)
                    binding.sensorSetupBtnNext.text = "Setup Completed!"
                    Toast.makeText(this@SensorSetupActivity, "Sensor configured successfully!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@SensorSetupActivity, "ESP32 Error: ${response.code}", Toast.LENGTH_LONG).show()
                    binding.sensorSetupBtnNext.isEnabled = true
                    binding.sensorSetupBtnNext.text = "Finish & Configure Sensor"
                }
            }
            
            if (response.isSuccessful) {
                kotlinx.coroutines.delay(1000)
                withContext(Dispatchers.Main) { finish() }
            }
        } catch (e: Exception) {
            Log.e("SensorSetup", "Failed to push config", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SensorSetupActivity, "Connection failed. Make sure you are connected to Quake-Setup.", Toast.LENGTH_LONG).show()
                binding.sensorSetupBtnNext.isEnabled = true
                binding.sensorSetupBtnNext.text = "Finish & Configure Sensor"
            }
        }
    }
}
