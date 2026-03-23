package id.my.bananapixel.quakealert.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.progressindicator.LinearProgressIndicator
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.databinding.ActivitySensorSetupBinding
import id.my.bananapixel.quakealert.util.systemDarkThemeOn
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent

@Serializable
data class SensorConfigPayload(val ssid: String, val password: String, val lat: Double, val lon: Double)

class SensorSetupActivity : BaseActivity() {

    private lateinit var binding: ActivitySensorSetupBinding
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private val wifiManager by lazy { getSystemService(WIFI_SERVICE) as WifiManager }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isConnectedToQuakeSetup = false
    private var esp32Network: android.net.Network? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLat: Double? = null
    private var currentLon: Double? = null
    private var currentCity: String? = null

    // State for Wi-Fi Bottom Sheet
    private var scannedNetworks: List<String>? = null
    private var activeBottomSheetDialog: BottomSheetDialog? = null
    private var activeBottomSheetView: View? = null

    // Step index — blocks swipe forward from a step until its prerequisite is met
    private val stepCount = 3
    private val stepUnlocked = BooleanArray(stepCount) { false }.also { it[0] = true }

    // Views inside page holders (resolved lazily after pager inflates)
    private var wifiHolder: SensorSetupAdapter.WifiViewHolder? = null
    private var locationHolder: SensorSetupAdapter.LocationViewHolder? = null
    private var credentialsHolder: SensorSetupAdapter.CredentialsViewHolder? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkWiFiConnection()
            startNetworkMonitoring()
        } else {
            Toast.makeText(this, "Location permission required to verify Wi-Fi on Android 10+", Toast.LENGTH_LONG).show()
            startNetworkMonitoring()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OSMDroid configuration
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID

        binding = ActivitySensorSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide bottom buttons and dots when keyboard is open
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeVisible) {
                binding.sensorSetupDotsContainer.visibility = View.GONE
                binding.sensorSetupButtonContainer.visibility = View.GONE
            } else {
                binding.sensorSetupDotsContainer.visibility = View.VISIBLE
                binding.sensorSetupButtonContainer.visibility = View.VISIBLE
            }
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val adapter = SensorSetupAdapter()
        binding.sensorSetupViewpager.adapter = adapter
        binding.sensorSetupViewpager.offscreenPageLimit = 2
        binding.sensorSetupViewpager.getChildAt(0)?.overScrollMode = View.OVER_SCROLL_NEVER

        // Block user from swiping forward past a locked step, and close keyboard on swipe
        binding.sensorSetupViewpager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
                }
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    val current = binding.sensorSetupViewpager.currentItem
                    if (!stepUnlocked[current]) {
                        binding.sensorSetupViewpager.setCurrentItem(current - 1, true)
                        Toast.makeText(this@SensorSetupActivity, "Complete this step first", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onPageSelected(position: Int) {
                updateUI(position)
            }
        })

        setupDots()
        updateUI(0)

        binding.sensorSetupBtnNext.setOnClickListener { onNextClicked() }
        binding.sensorSetupBtnBack.setOnClickListener {
            val cur = binding.sensorSetupViewpager.currentItem
            if (cur > 0) binding.sensorSetupViewpager.setCurrentItem(cur - 1, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                checkWiFiConnection()
                startNetworkMonitoring()
            }
        } else {
            checkWiFiConnection()
            startNetworkMonitoring()
        }
    }

    // ────────────────────────────────────────
    // UI helpers
    // ────────────────────────────────────────

    private fun updateUI(position: Int) {
        val isFirst = position == 0
        val isLast  = position == stepCount - 1

        binding.sensorSetupBtnBack.visibility = if (isFirst) View.GONE else View.VISIBLE

        val nextParams = binding.sensorSetupBtnNext.layoutParams as LinearLayout.LayoutParams
        val backParams = binding.sensorSetupBtnBack.layoutParams as LinearLayout.LayoutParams
        if (isFirst) { nextParams.weight = 1f; backParams.weight = 0f }
        else         { nextParams.weight = 2f; backParams.weight = 1f }
        binding.sensorSetupBtnNext.layoutParams = nextParams
        binding.sensorSetupBtnBack.layoutParams = backParams

        binding.sensorSetupBtnNext.text = when (position) {
            0 -> getString(R.string.sensor_setup_next)
            1 -> "Next: Wi-Fi Config"
            else -> "Finish & Configure Sensor"
        }

        // Enabled state driven by stepUnlocked of NEXT step
        val nextStep = position + 1
        val canAdvance = if (isLast) stepUnlocked[position] else (nextStep < stepCount && stepUnlocked[nextStep]) || stepUnlocked[position]
        setNextButtonEnabled(stepUnlocked[position] && (position < stepCount - 1 || stepUnlocked[position]))
        when (position) {
            0 -> setNextButtonEnabled(isConnectedToQuakeSetup)
            1 -> setNextButtonEnabled(currentLat != null && currentLon != null)
            2 -> setNextButtonEnabled(true)
        }

        updateDots(position)
    }

    private fun setNextButtonEnabled(enabled: Boolean) {
        binding.sensorSetupBtnNext.isEnabled = enabled
        binding.sensorSetupBtnNext.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun setupDots() {
        binding.sensorSetupDotsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        for (i in 0 until stepCount) {
            val dot = View(this)
            val w = if (i == 0) (24 * density).toInt() else (8 * density).toInt()
            val h = (8 * density).toInt()
            val params = LinearLayout.LayoutParams(w, h)
            params.setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
            dot.layoutParams = params
            dot.setBackgroundResource(if (i == 0) R.drawable.bg_onboarding_dot_active else R.drawable.bg_onboarding_dot_inactive)
            binding.sensorSetupDotsContainer.addView(dot)
        }
    }

    private fun updateDots(position: Int) {
        val density = resources.displayMetrics.density
        for (i in 0 until binding.sensorSetupDotsContainer.childCount) {
            val dot = binding.sensorSetupDotsContainer.getChildAt(i)
            val params = dot.layoutParams as LinearLayout.LayoutParams
            if (i == position) {
                params.width  = (24 * density).toInt()
                params.height = (10 * density).toInt()
                dot.setBackgroundResource(R.drawable.bg_onboarding_dot_active)
            } else {
                params.width  = (8 * density).toInt()
                params.height = (8 * density).toInt()
                dot.setBackgroundResource(R.drawable.bg_onboarding_dot_inactive)
            }
            dot.layoutParams = params
        }
    }

    // ────────────────────────────────────────
    // Navigation
    // ────────────────────────────────────────

    private fun onNextClicked() {
        val current = binding.sensorSetupViewpager.currentItem
        when (current) {
            0 -> {
                if (!isConnectedToQuakeSetup) {
                    Toast.makeText(this, "Connect to QuakeSetup first", Toast.LENGTH_SHORT).show()
                    return
                }
                stepUnlocked[1] = true
                fetchLocation(forceGps = false)
                binding.sensorSetupViewpager.setCurrentItem(1, true)
            }
            1 -> {
                if (currentLat == null || currentLon == null) {
                    Toast.makeText(this, "Still fetching location…", Toast.LENGTH_SHORT).show()
                    return
                }
                stepUnlocked[2] = true
                binding.sensorSetupViewpager.setCurrentItem(2, true)
                fetchAvailableNetworksFromEsp32()
            }
            2 -> {
                val ssid = credentialsHolder?.ssidInput?.text?.toString() ?: ""
                val pass = credentialsHolder?.passInput?.text?.toString() ?: ""
                if (ssid.isBlank()) { Toast.makeText(this, "Enter Wi-Fi SSID", Toast.LENGTH_SHORT).show(); return }
                if (pass.isBlank()) { Toast.makeText(this, "Enter Wi-Fi password", Toast.LENGTH_SHORT).show(); return }
                binding.sensorSetupBtnNext.isEnabled = false
                binding.sensorSetupBtnNext.text = "Configuring…"
                lifecycleScope.launch(Dispatchers.IO) {
                    pushConfigToEsp32(currentLat!!, currentLon!!, currentCity ?: "", ssid, pass)
                }
            }
        }
    }

    // ────────────────────────────────────────
    // Wi-Fi monitoring
    // ────────────────────────────────────────

    private fun startNetworkMonitoring() {
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                esp32Network = network; checkWiFiConnection()
            }
            override fun onCapabilitiesChanged(network: android.net.Network, caps: NetworkCapabilities) {
                esp32Network = network; checkWiFiConnection()
            }
            override fun onLost(network: android.net.Network) {
                if (network == esp32Network) esp32Network = null
                if (isConnectedToQuakeSetup) {
                    isConnectedToQuakeSetup = false
                    runOnUiThread { showDisconnectedState() }
                }
            }
        }
        try {
            val req = android.net.NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            connectivityManager.registerNetworkCallback(req, networkCallback!!)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun checkWiFiConnection() {
        try {
            val info = wifiManager.connectionInfo ?: return
            val ssid = info.ssid?.trim('"') ?: ""
            val connected = ssid.equals("QuakeSetup", ignoreCase = true)
            if (connected != isConnectedToQuakeSetup) {
                isConnectedToQuakeSetup = connected
                runOnUiThread { if (connected) showSuccessState() else showDisconnectedState() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread { setNextButtonEnabled(false) }
        }
    }

    private fun showSuccessState() {
        wifiHolder?.apply {
            progressBar.visibility = View.GONE
            ssidContainer.visibility = View.VISIBLE
        }
        setNextButtonEnabled(true)
        updateDots(0)
    }

    private fun showDisconnectedState() {
        wifiHolder?.apply {
            progressBar.visibility = View.VISIBLE
            ssidContainer.visibility = View.GONE
        }
        setNextButtonEnabled(false)
    }

    // ────────────────────────────────────────
    // Location
    // ────────────────────────────────────────

    private fun fetchLocation(forceGps: Boolean = false) {
        if (!forceGps) {
            val prefs = getSharedPreferences("MainPreferences", 0)
            if (prefs.contains("UserLatitude") && prefs.contains("UserLongitude")) {
                val lat = Double.fromBits(prefs.getLong("UserLatitude", 0L))
                val lon = Double.fromBits(prefs.getLong("UserLongitude", 0L))
                val city = prefs.getString("UserCityName", "") ?: "Offline (Coordinates saved)"
                currentLat = lat
                currentLon = lon
                currentCity = city
                locationHolder?.apply {
                    setLocationProgrammatically(lat, lon)
                    cityValue.text = city
                    titleText.text = "Location Found (Cached)"
                    subtitleText.text = "Confirm coordinates for your sensor."
                }
                if (binding.sensorSetupViewpager.currentItem == 1) setNextButtonEnabled(true)
                return
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationHolder?.titleText?.text = "Permission Denied"
            locationHolder?.subtitleText?.text = "Enable location permission to continue."
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) processLocation(loc.latitude, loc.longitude)
            else fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { l -> if (l != null) processLocation(l.latitude, l.longitude) else locationHolder?.titleText?.text = "Location Unknown" }
                .addOnFailureListener { locationHolder?.titleText?.text = "Location Error" }
        }.addOnFailureListener {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { l -> if (l != null) processLocation(l.latitude, l.longitude) }
        }
    }

    private fun processLocation(lat: Double, lon: Double) {
        currentLat = lat; currentLon = lon
        locationHolder?.setLocationProgrammatically(lat, lon)
        resolveCityName(lat, lon)
    }

    private fun resolveCityName(lat: Double, lon: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            val city = try {
                kotlinx.coroutines.withTimeoutOrNull(3000L) {
                    val geo = Geocoder(this@SensorSetupActivity, Locale.getDefault())
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
            } catch (e: Exception) { "Offline (Coordinates saved)" }

            withContext(Dispatchers.Main) {
                currentCity = city
                locationHolder?.apply {
                    cityValue.text = city
                    titleText.text = "Location Found"
                    subtitleText.text = "Confirm coordinates for your sensor."
                }
                if (binding.sensorSetupViewpager.currentItem == 1) setNextButtonEnabled(true)
            }
        }
    }

    // ────────────────────────────────────────
    // ESP32 network scan
    // ────────────────────────────────────────

    private fun fetchAvailableNetworksFromEsp32() {
        scannedNetworks = null
        updateBottomSheetState()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val clientBuilder = OkHttpClient.Builder()
                esp32Network?.let { clientBuilder.socketFactory(it.socketFactory) }
                val client = clientBuilder
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()

                val response = client.newCall(Request.Builder().url("http://192.168.4.1/scan").get().build()).execute()
                val body = response.body?.string()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && body != null) {
                        val arr = org.json.JSONArray(body)
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val s = arr.getString(i)
                            if (s.isNotBlank() && !list.contains(s)) list.add(s)
                        }
                        scannedNetworks = list
                    } else {
                        Toast.makeText(this@SensorSetupActivity, "Scan failed — type SSID manually", Toast.LENGTH_SHORT).show()
                        scannedNetworks = emptyList()
                    }
                    updateBottomSheetState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "ESP32 scan error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SensorSetupActivity, "Network error — type SSID manually", Toast.LENGTH_SHORT).show()
                    scannedNetworks = emptyList()
                    updateBottomSheetState()
                }
            }
        }
    }

    private fun setupWifiSelector() {
        credentialsHolder?.ssidInput?.setOnClickListener {
            showWifiBottomSheet()
        }
    }

    private fun showWifiBottomSheet() {
        if (activeBottomSheetDialog == null) {
            val dialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.bottom_sheet_wifi_networks, null)
            dialog.setContentView(view)

            val manualEntry = view.findViewById<View>(R.id.bottom_sheet_wifi_manual_entry)
            manualEntry.setOnClickListener {
                dialog.dismiss()
                credentialsHolder?.ssidInput?.apply {
                    isCursorVisible = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    inputType = android.text.InputType.TYPE_CLASS_TEXT
                    
                    setOnClickListener(null)
                    setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    
                    requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
            }

            dialog.setOnDismissListener {
                activeBottomSheetDialog = null
                activeBottomSheetView = null
            }

            activeBottomSheetDialog = dialog
            activeBottomSheetView = view
        }

        updateBottomSheetState()
        activeBottomSheetDialog?.show()
    }

    private fun updateBottomSheetState() {
        val view = activeBottomSheetView ?: return
        val dialog = activeBottomSheetDialog ?: return

        val loadingView = view.findViewById<View>(R.id.bottom_sheet_wifi_loading)
        val emptyView = view.findViewById<View>(R.id.bottom_sheet_wifi_empty)
        val recyclerView = view.findViewById<RecyclerView>(R.id.bottom_sheet_wifi_recycler)

        val networks = scannedNetworks
        if (networks == null) {
            // Still loading
            loadingView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.GONE
        } else if (networks.isEmpty()) {
            // Loaded but empty
            loadingView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            // Loaded with data
            loadingView.visibility = View.GONE
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            if (recyclerView.layoutManager == null) {
                recyclerView.layoutManager = LinearLayoutManager(this)
            }
            recyclerView.adapter = WifiNetworkAdapter(networks) { selectedSsid ->
                credentialsHolder?.ssidInput?.setText(selectedSsid)
                dialog.dismiss()
                credentialsHolder?.passInput?.requestFocus()
            }
        }
    }

    private inner class WifiNetworkAdapter(
        private val networks: List<String>,
        private val onNetworkClick: (String) -> Unit
    ) : RecyclerView.Adapter<WifiNetworkAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ssidText: TextView = view.findViewById(R.id.item_wifi_ssid_text)
            init { view.setOnClickListener { onNetworkClick(networks[adapterPosition]) } }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(layoutInflater.inflate(R.layout.item_wifi_network, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.ssidText.text = networks[position]
        }

        override fun getItemCount() = networks.size
    }

    // ────────────────────────────────────────
    // Push config to ESP32
    // ────────────────────────────────────────

    private suspend fun pushConfigToEsp32(lat: Double, lon: Double, city: String, wifiSsid: String, wifiPass: String) {
        try {
            val json = Json.encodeToString(SensorConfigPayload(wifiSsid, wifiPass, lat, lon))
            val body = json.toRequestBody("application/json".toMediaType())
            val clientBuilder = OkHttpClient.Builder()
            esp32Network?.let { clientBuilder.socketFactory(it.socketFactory) }
            val client = clientBuilder.build()
            val response = client.newCall(Request.Builder().url("http://192.168.4.1/config").post(body).build()).execute()

            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    binding.sensorSetupBtnNext.setBackgroundResource(R.drawable.bg_badge_3d_green_small)
                    binding.sensorSetupBtnNext.text = "Setup Complete! ✓"
                    Toast.makeText(this@SensorSetupActivity, "Sensor configured successfully!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@SensorSetupActivity, "ESP32 Error: ${response.code}", Toast.LENGTH_LONG).show()
                    binding.sensorSetupBtnNext.isEnabled = true
                    binding.sensorSetupBtnNext.text = "Finish & Configure Sensor"
                }
            }

            if (response.isSuccessful) {
                kotlinx.coroutines.delay(1500)
                withContext(Dispatchers.Main) { finish() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Push config failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SensorSetupActivity, "Connection failed. Make sure you are on QuakeSetup.", Toast.LENGTH_LONG).show()
                binding.sensorSetupBtnNext.isEnabled = true
                binding.sensorSetupBtnNext.text = "Finish & Configure Sensor"
            }
        }
    }

    private fun getCartoTileSource(context: android.content.Context): XYTileSource {
        val isDark = context.systemDarkThemeOn()
        val path = if (isDark) "dark_all" else "light_all"
        return XYTileSource(
            if (isDark) "Carto Dark Matter" else "Carto Positron",
            0, 20, 256, ".png",
            arrayOf("a", "b", "c", "d").map { subdomain ->
                "https://$subdomain.basemaps.cartocdn.com/rastertiles/$path/"
            }.toTypedArray()
        )
    }

    // ────────────────────────────────────────
    // ViewPager2 Adapter
    // ────────────────────────────────────────

    inner class SensorSetupAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = stepCount
        override fun getItemViewType(position: Int) = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> {
                    val v = inflater.inflate(R.layout.item_sensor_setup_wifi, parent, false)
                    WifiViewHolder(v).also { wifiHolder = it }
                }
                1 -> {
                    val v = inflater.inflate(R.layout.item_sensor_setup_location, parent, false)
                    LocationViewHolder(v).also { locationHolder = it }
                }
                else -> {
                    val v = inflater.inflate(R.layout.item_sensor_setup_credentials, parent, false)
                    CredentialsViewHolder(v).also {
                        credentialsHolder = it
                        setupWifiSelector()
                    }
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            // Views are live-bound via the holder references above
            if (position == 0 && isConnectedToQuakeSetup) showSuccessState()
        }

        inner class WifiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleText: TextView = view.findViewById(R.id.sensor_setup_instruction_title)
            val progressBar: LinearProgressIndicator = view.findViewById(R.id.sensor_setup_progress_bar)
            val ssidContainer: View = view.findViewById(R.id.sensor_setup_ssid_container)
            init { view.findViewById<View>(R.id.sensor_setup_btn_close)?.setOnClickListener { finish() } }
        }

        inner class LocationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleText: TextView    = view.findViewById(R.id.sensor_setup_location_title)
            val subtitleText: TextView = view.findViewById(R.id.sensor_setup_location_subtitle)
            val latInput: EditText     = view.findViewById(R.id.sensor_setup_lat_input)
            val lonInput: EditText     = view.findViewById(R.id.sensor_setup_lon_input)
            val cityValue: TextView    = view.findViewById(R.id.sensor_setup_city_value)
            val mapView: MapView       = view.findViewById(R.id.sensor_setup_mapview)

            private var isUpdatingFromMap = false
            private var isUpdatingFromText = false

            fun setLocationProgrammatically(lat: Double, lon: Double) {
                isUpdatingFromMap = true
                latInput.setText(String.format(Locale.US, "%.5f", lat))
                lonInput.setText(String.format(Locale.US, "%.5f", lon))
                mapView.controller.setCenter(GeoPoint(lat, lon))
                isUpdatingFromMap = false
            }

            init { 
                view.findViewById<View>(R.id.sensor_setup_btn_close)?.setOnClickListener { finish() }

                // Configure MapView
                mapView.setTileSource(getCartoTileSource(this@SensorSetupActivity))
                mapView.setMultiTouchControls(true)
                mapView.setBuiltInZoomControls(false)
                mapView.setTilesScaledToDpi(true)
                mapView.isHorizontalMapRepetitionEnabled = false
                mapView.isVerticalMapRepetitionEnabled = false
                val worldBox = org.osmdroid.util.BoundingBox(85.0, 180.0, -85.0, -180.0)
                mapView.setScrollableAreaLimitDouble(worldBox)
                mapView.minZoomLevel = 3.0
                mapView.controller.setZoom(7.0)
                
                // CRUCIAL: Force OSMDroid to ignore the active (but offline) Wi-Fi connection and read strictly from its internal SQLite settings cache.
                mapView.setUseDataConnection(false)
                
                mapView.onResume() // Force MapView to start rendering immediately since ViewPager lazily loads it

                // Refresh Location button
                view.findViewById<View>(R.id.sensor_setup_btn_refresh_location)?.setOnClickListener {
                    Toast.makeText(this@SensorSetupActivity, "Fetching GPS...", Toast.LENGTH_SHORT).show()
                    fetchLocation(forceGps = true)
                }

                // Prevent ViewPager from swiping when dragging the map
                mapView.setOnTouchListener { v, _ ->
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    latInput.clearFocus()
                    lonInput.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    false
                }

                // Sync Map dragging -> EditTexts
                mapView.addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        if (latInput.hasFocus() || lonInput.hasFocus()) return true
                        val center = mapView.mapCenter
                        if (!isUpdatingFromText) {
                            isUpdatingFromMap = true
                            latInput.setText(String.format(Locale.US, "%.5f", center.latitude))
                            lonInput.setText(String.format(Locale.US, "%.5f", center.longitude))
                            currentLat = center.latitude
                            currentLon = center.longitude
                            
                            val nextBtnEnabled = currentLat != null && currentLon != null
                            if (binding.sensorSetupViewpager.currentItem == 1) setNextButtonEnabled(nextBtnEnabled)

                            isUpdatingFromMap = false
                        }
                        return true
                    }
                    override fun onZoom(event: ZoomEvent?) = false
                })

                val textWatcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (isUpdatingFromMap) return
                        if (!latInput.hasFocus() && !lonInput.hasFocus()) return
                        
                        val latStr = latInput.text.toString()
                        val lonStr = lonInput.text.toString()
                        val lat = latStr.toDoubleOrNull()
                        val lon = lonStr.toDoubleOrNull()
                        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                            isUpdatingFromText = true
                            currentLat = lat
                            currentLon = lon
                            mapView.controller.setCenter(GeoPoint(lat, lon))

                            val nextBtnEnabled = currentLat != null && currentLon != null
                            if (binding.sensorSetupViewpager.currentItem == 1) setNextButtonEnabled(nextBtnEnabled)

                            isUpdatingFromText = false
                        }
                    }
                }
                latInput.addTextChangedListener(textWatcher)
                lonInput.addTextChangedListener(textWatcher)
            }
        }

        inner class CredentialsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ssidInput: EditText = view.findViewById(R.id.sensor_setup_ssid_input)
            val passInput: EditText = view.findViewById(R.id.sensor_setup_pass_input)
            init { view.findViewById<View>(R.id.sensor_setup_btn_close)?.setOnClickListener { finish() } }
        }
    }

    // ────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        checkWiFiConnection()
        locationHolder?.mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        locationHolder?.mapView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
    }

    companion object { const val TAG = "SensorSetup" }
}