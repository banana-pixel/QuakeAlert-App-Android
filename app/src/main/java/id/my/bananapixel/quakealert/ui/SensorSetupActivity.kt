package id.my.bananapixel.quakealert.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import id.my.bananapixel.quakealert.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import java.util.Locale

class SensorSetupActivity : AppCompatActivity() {

    private lateinit var connectivityManager: ConnectivityManager
    private var boundNetwork: Network? = null
    private lateinit var locationClient: FusedLocationProviderClient

    // Main Flipper
    private lateinit var viewFlipperSetup: ViewFlipper

    // Step 1
    private lateinit var tvStep1Status: TextView
    private lateinit var btnStep1: TextView

    // Step 2
    private lateinit var tvStep2Coords: TextView
    private lateinit var tvStep2Accuracy: TextView
    private lateinit var btnStep2: TextView
    private var currentLat: Double = 0.0
    private var currentLon: Double = 0.0

    // Step 3
    private lateinit var lvWifiScan: ListView
    private lateinit var tvSelectedSsid: TextView
    private lateinit var etWifiPassword: EditText
    private lateinit var btnPushConfig: TextView

    private var selectedSsid: String? = null

    private val ESP32_BASE_URL = "http://192.168.4.1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_setup)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        locationClient = LocationServices.getFusedLocationProviderClient(this)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        viewFlipperSetup = findViewById(R.id.view_flipper_setup)

        // Step 1 binds
        tvStep1Status = findViewById(R.id.tv_step1_status)
        btnStep1 = findViewById(R.id.btn_step_1)

        // Step 2 binds
        tvStep2Coords = findViewById(R.id.tv_step_2_coords)
        tvStep2Accuracy = findViewById(R.id.tv_step_2_accuracy)
        btnStep2 = findViewById(R.id.btn_step_2)

        // Step 3 binds
        lvWifiScan = findViewById(R.id.lv_wifi_scan)
        tvSelectedSsid = findViewById(R.id.tv_selected_ssid)
        etWifiPassword = findViewById(R.id.et_wifi_password)
        btnPushConfig = findViewById(R.id.btn_push_config)

        btnStep1.setOnClickListener {
            viewFlipperSetup.displayedChild = 1
            fetchGps()
        }

        btnStep2.setOnClickListener {
            pushGpsToEsp32()
        }

        btnPushConfig.setOnClickListener {
            pushWifiConfig()
        }

        requestPermissions()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                if (results.all { it.value }) {
                    waitForEsp32Connection()
                } else {
                    Toast.makeText(this, "Permissions required for setup", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            waitForEsp32Connection()
        }
    }

    private fun waitForEsp32Connection() {
        tvStep1Status.text = "Waiting for 'Quake-Setup' Wi-Fi..."
        
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
            
        connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Ensure we explicitly bind this process to the Wi-Fi network 
                // to prevent HTTP calls routing over 5G/LTE if the AP has no internet
                val isBound = connectivityManager.bindProcessToNetwork(network)
                if (isBound) {
                    boundNetwork = network
                    runOnUiThread {
                        tvStep1Status.text = "Connected to ESP32!"
                        btnStep1.isEnabled = true
                        btnStep1.alpha = 1.0f
                    }
                    connectivityManager.unregisterNetworkCallback(this)
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun fetchGps() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setWaitForAccurateLocation(true)
            .setMaxUpdates(1)
            .build()
            
        locationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                locationClient.removeLocationUpdates(this)
                val location = result.lastLocation
                if (location != null) {
                    currentLat = location.latitude
                    currentLon = location.longitude
                    runOnUiThread {
                        tvStep2Coords.text = String.format(Locale.getDefault(), "%.6f, %.6f", currentLat, currentLon)
                        tvStep2Accuracy.text = "Accuracy: ±${location.accuracy.toInt()} meters"
                        btnStep2.isEnabled = true
                    }
                } else {
                    runOnUiThread {
                        tvStep2Coords.text = "Failed to get GPS"
                        tvStep2Accuracy.text = "Make sure Location is enabled."
                        btnStep2.isEnabled = false
                    }
                }
            }
        }, Looper.getMainLooper())
    }

    private fun pushGpsToEsp32() {
        btnStep2.isEnabled = false
        btnStep2.text = "Sending..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("lat", currentLat)
                    put("lon", currentLon)
                }
                
                val responseCode = postJson("$ESP32_BASE_URL/api/gps", json.toString())
                
                withContext(Dispatchers.Main) {
                    if (responseCode == 200) {
                        Toast.makeText(this@SensorSetupActivity, "GPS Sent!", Toast.LENGTH_SHORT).show()
                        viewFlipperSetup.displayedChild = 2
                        fetchWifiScanList()
                    } else {
                        Toast.makeText(this@SensorSetupActivity, "Failed (HTTP $responseCode)", Toast.LENGTH_SHORT).show()
                        btnStep2.isEnabled = true
                        btnStep2.text = "Send to Sensor ->"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SensorSetupActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnStep2.isEnabled = true
                    btnStep2.text = "Send to Sensor ->"
                }
            }
        }
    }

    private fun fetchWifiScanList() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("$ESP32_BASE_URL/api/wifi_scan")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val networksArray = json.optJSONArray("networks") ?: JSONArray()
                    
                    val ssids = mutableListOf<String>()
                    val validNetworks = mutableListOf<JSONObject>()
                    
                    for (i in 0 until networksArray.length()) {
                        val obj = networksArray.getJSONObject(i)
                        ssids.add(obj.getString("ssid") + " (" + obj.getInt("rssi") + "dBm)")
                        validNetworks.add(obj)
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (ssids.isNotEmpty()) {
                            val adapter = ArrayAdapter(this@SensorSetupActivity, android.R.layout.simple_list_item_1, ssids)
                            lvWifiScan.adapter = adapter
                            
                            lvWifiScan.setOnItemClickListener { _, _, position, _ ->
                                val selectedObject = validNetworks[position]
                                selectedSsid = selectedObject.getString("ssid")
                                onSsidSelected()
                            }
                        } else {
                            Toast.makeText(this@SensorSetupActivity, "No networks found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SensorSetupActivity, "Scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onSsidSelected() {
        tvSelectedSsid.text = "Selected: $selectedSsid"
        etWifiPassword.requestFocus()
    }

    private fun pushWifiConfig() {
        val ssid = selectedSsid
        val pass = etWifiPassword.text.toString()
        
        if (ssid.isNullOrEmpty()) {
            Toast.makeText(this, "Select a Wi-Fi network first", Toast.LENGTH_SHORT).show()
            return
        }

        btnPushConfig.isEnabled = false
        btnPushConfig.text = "Pushing..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("ssid", ssid)
                    put("password", pass)
                    put("reboot", true)
                }
                
                val responseCode = postJson("$ESP32_BASE_URL/api/wifi_config", json.toString())
                
                withContext(Dispatchers.Main) {
                    if (responseCode == 200) {
                        Toast.makeText(this@SensorSetupActivity, "Setup Complete! Device rebooting.", Toast.LENGTH_LONG).show()
                        connectivityManager.bindProcessToNetwork(null)
                        finish()
                    } else {
                        Toast.makeText(this@SensorSetupActivity, "Failed (HTTP $responseCode)", Toast.LENGTH_LONG).show()
                        btnPushConfig.isEnabled = true
                        btnPushConfig.text = "Finish Setup"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SensorSetupActivity, "Config pushed (Device rebooted).", Toast.LENGTH_LONG).show()
                    connectivityManager.bindProcessToNetwork(null)
                    finish()
                }
            }
        }
    }

    private fun postJson(urlString: String, jsonString: String): Int {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        
        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(jsonString)
            writer.flush()
        }
        
        return connection.responseCode
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.bindProcessToNetwork(null)
    }
}
