package id.my.bananapixel.quakealert.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
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
import java.util.Locale

class AddSensorDialogFragment : DialogFragment() {

    private lateinit var connectivityManager: ConnectivityManager
    private var boundNetwork: Network? = null
    private lateinit var locationClient: FusedLocationProviderClient

    // ViewFlipper and Step Views
    private lateinit var viewFlipperSetup: ViewFlipper
    
    // Step 1 - WiFi Connection
    private lateinit var tvStep1Status: TextView
    private lateinit var btnStep1: TextView
    private lateinit var ivNetworkIcon: ImageView
    private lateinit var tvNetworkStatus: TextView

    // Step 2 - GPS Location
    private lateinit var tvStep2Coords: TextView
    private lateinit var tvStep2Accuracy: TextView
    private lateinit var btnStep2: TextView
    private var currentLat: Double = 0.0
    private var currentLon: Double = 0.0

    // Step 3 - Wi-Fi Setup
    private lateinit var lvWifiScan: ListView
    private lateinit var tvSelectedSsid: TextView
    private lateinit var etWifiPassword: EditText
    private lateinit var btnPushConfig: TextView

    private var selectedSsid: String? = null

    private val ESP32_BASE_URL = "http://192.168.4.1"

    override fun onCreateDialog(savedInstanceState: Bundle?) = super.onCreateDialog(savedInstanceState).apply {
        setStyle(STYLE_NORMAL, R.style.Theme_Material3_DayNight_Dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_add_sensor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        locationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Header
        view.findViewById<ImageView>(R.id.btn_close_sensor_dialog).setOnClickListener {
            dismiss()
        }

        // ViewFlipper
        viewFlipperSetup = view.findViewById(R.id.view_flipper_sensor_setup)

        // Step 1
        tvStep1Status = view.findViewById(R.id.tv_step1_status)
        ivNetworkIcon = view.findViewById(R.id.iv_network_icon)
        tvNetworkStatus = view.findViewById(R.id.tv_network_status)
        btnStep1 = view.findViewById(R.id.btn_step_1)

        // Step 2
        tvStep2Coords = view.findViewById(R.id.tv_step_2_coords)
        tvStep2Accuracy = view.findViewById(R.id.tv_step_2_accuracy)
        btnStep2 = view.findViewById(R.id.btn_step_2)

        // Step 3
        lvWifiScan = view.findViewById(R.id.lv_wifi_scan)
        tvSelectedSsid = view.findViewById(R.id.tv_selected_ssid)
        etWifiPassword = view.findViewById(R.id.et_wifi_password)
        btnPushConfig = view.findViewById(R.id.btn_push_config)

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
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                if (results.all { it.value }) {
                    waitForEsp32Connection()
                } else {
                    Toast.makeText(requireContext(), "Permissions required for setup", Toast.LENGTH_LONG).show()
                    dismiss()
                }
            }
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            waitForEsp32Connection()
        }
    }

    private fun waitForEsp32Connection() {
        tvNetworkStatus.text = "Waiting for ESP32 network..."
        updateNetworkIcon(false)

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val isBound = connectivityManager.bindProcessToNetwork(network)
                if (isBound) {
                    boundNetwork = network
                    view?.let { v ->
                        v.post {
                            tvNetworkStatus.text = "✓ Connected to ESP32!"
                            updateNetworkIcon(true)
                            btnStep1.isEnabled = true
                            btnStep1.alpha = 1.0f
                        }
                    }
                    connectivityManager.unregisterNetworkCallback(this)
                }
            }
        })
    }

    private fun updateNetworkIcon(isConnected: Boolean) {
        ivNetworkIcon.setImageResource(
            if (isConnected) R.drawable.ic_wifi_on_24dp else R.drawable.ic_wifi_off_24dp
        )
        ivNetworkIcon.setColorFilter(
            if (isConnected) 0xFF4CAF50.toInt() else 0xFFFF9800.toInt(),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
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
                    view?.let { v ->
                        v.post {
                            tvStep2Coords.text = String.format(Locale.getDefault(), "%.6f, %.6f", currentLat, currentLon)
                            tvStep2Accuracy.text = "Accuracy: ±${location.accuracy.toInt()} meters"
                            btnStep2.isEnabled = true
                        }
                    }
                } else {
                    view?.let { v ->
                        v.post {
                            tvStep2Coords.text = "Failed to get GPS"
                            tvStep2Accuracy.text = "Make sure Location is enabled."
                            btnStep2.isEnabled = false
                        }
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
                        Toast.makeText(requireContext(), "GPS Sent!", Toast.LENGTH_SHORT).show()
                        viewFlipperSetup.displayedChild = 2
                        fetchWifiScanList()
                    } else {
                        Toast.makeText(requireContext(), "Failed (HTTP $responseCode)", Toast.LENGTH_SHORT).show()
                        btnStep2.isEnabled = true
                        btnStep2.text = "Send to Sensor ->"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, ssids)
                            lvWifiScan.adapter = adapter

                            lvWifiScan.setOnItemClickListener { _, _, position, _ ->
                                val selectedObject = validNetworks[position]
                                selectedSsid = selectedObject.getString("ssid")
                                onSsidSelected()
                            }
                        } else {
                            Toast.makeText(requireContext(), "No networks found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(requireContext(), "Select a Wi-Fi network first", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(requireContext(), "Setup Complete! Device rebooting.", Toast.LENGTH_LONG).show()
                        connectivityManager.bindProcessToNetwork(null)
                        dismiss()
                    } else {
                        Toast.makeText(requireContext(), "Failed (HTTP $responseCode)", Toast.LENGTH_LONG).show()
                        btnPushConfig.isEnabled = true
                        btnPushConfig.text = "Finish Setup"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Config pushed (Device rebooted).", Toast.LENGTH_LONG).show()
                    connectivityManager.bindProcessToNetwork(null)
                    dismiss()
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

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        connectivityManager.bindProcessToNetwork(null)
    }
}
