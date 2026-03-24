package id.my.bananapixel.quakealert.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.databinding.ActivitySensorSetupBinding
import id.my.bananapixel.quakealert.util.systemDarkThemeOn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale

class SensorSetupActivity : BaseActivity() {

    private lateinit var binding: ActivitySensorSetupBinding
    private val viewModel: SensorSetupViewModel by viewModel()

    // State for Wi-Fi Bottom Sheet
    private var activeBottomSheetDialog: BottomSheetDialog? = null
    private var activeBottomSheetView: View? = null

    // Step index
    private val stepCount = 3
    private val stepUnlocked = BooleanArray(stepCount) { false }.also { it[0] = true }

    // Views inside page holders (resolved lazily after pager inflates)
    private var credentialsHolder: SensorSetupAdapter.CredentialsViewHolder? = null
    private var locationHolder: SensorSetupAdapter.LocationViewHolder? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startNetworkMonitoring()
        } else {
            Toast.makeText(this, "Location permission required to verify Wi-Fi on Android 10+", Toast.LENGTH_LONG).show()
            viewModel.markLocationPermissionDenied()
            viewModel.startNetworkMonitoring()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID

        binding = ActivitySensorSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        val adapter = SensorSetupAdapter()
        binding.sensorSetupViewpager.adapter = adapter
        binding.sensorSetupViewpager.offscreenPageLimit = 2
        binding.sensorSetupViewpager.getChildAt(0)?.overScrollMode = View.OVER_SCROLL_NEVER

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
                updateUI(position, viewModel.uiState.value)
            }
        })

        setupDots()

        binding.sensorSetupBtnNext.setOnClickListener { onNextClicked() }
        binding.sensorSetupBtnBack.setOnClickListener {
            val cur = binding.sensorSetupViewpager.currentItem
            if (cur > 0) binding.sensorSetupViewpager.setCurrentItem(cur - 1, true)
        }

        observeViewModel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                viewModel.startNetworkMonitoring()
            }
        } else {
            viewModel.startNetworkMonitoring()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    updateUI(binding.sensorSetupViewpager.currentItem, state)
                    
                    if (state.setupComplete) {
                        showSuccessDialog()
                    }
                    if (state.errorString != null) {
                        Toast.makeText(this@SensorSetupActivity, state.errorString, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }

                    updateBottomSheetState(state)

                    locationHolder?.apply {
                        if (state.currentLat != null && state.currentLon != null) {
                            if (!isUpdatingFromMap && !isUpdatingFromText) {
                                setLocationProgrammatically(state.currentLat, state.currentLon)
                            }
                            if (state.currentCity != null) {
                                cityValue.text = state.currentCity
                                titleText.text = "Location Found"
                                subtitleText.text = "Confirm coordinates for your sensor."
                            }
                        } else if (state.locationErrorString != null) {
                            titleText.text = state.locationErrorString
                        } else if (state.locationPermissionDenied) {
                            titleText.text = "Permission Denied"
                            subtitleText.text = "Enable location permission to continue."
                        }
                    }
                }
            }
        }
    }

    private fun showSuccessDialog() {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Success")
            .setMessage("Sensor Configured Successfully")
            .setPositiveButton("Done") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun updateUI(position: Int, state: SensorSetupUiState) {
        val isFirst = position == 0

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
            else -> if (state.savingConfig) "Configuring…" else "Finish & Configure Sensor"
        }

        var canAdvance = false
        when (position) {
            0 -> canAdvance = state.isConnectedToQuakeSetup
            1 -> canAdvance = state.currentLat != null && state.currentLon != null
            2 -> canAdvance = !state.savingConfig
        }

        if (canAdvance) {
            stepUnlocked[position] = true
        }
        
        setNextButtonEnabled(canAdvance)

        updateDots(position)
        
        (binding.sensorSetupViewpager.adapter as? SensorSetupAdapter)?.updateViews(state, position)
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

    private fun onNextClicked() {
        val current = binding.sensorSetupViewpager.currentItem
        when (current) {
            0 -> {
                if (!viewModel.uiState.value.isConnectedToQuakeSetup) {
                    Toast.makeText(this, "Connect to QuakeSetup first", Toast.LENGTH_SHORT).show()
                    return
                }
                stepUnlocked[1] = true
                viewModel.fetchLocation(forceGps = false)
                binding.sensorSetupViewpager.setCurrentItem(1, true)
            }
            1 -> {
                if (viewModel.uiState.value.currentLat == null || viewModel.uiState.value.currentLon == null) {
                    Toast.makeText(this, "Still fetching location…", Toast.LENGTH_SHORT).show()
                    return
                }
                stepUnlocked[2] = true
                binding.sensorSetupViewpager.setCurrentItem(2, true)
                viewModel.fetchAvailableNetworksFromEsp32()
            }
            2 -> {
                val ssid = credentialsHolder?.ssidInput?.text?.toString() ?: ""
                val pass = credentialsHolder?.passInput?.text?.toString() ?: ""
                if (ssid.isBlank()) { Toast.makeText(this, "Enter Wi-Fi SSID", Toast.LENGTH_SHORT).show(); return }
                if (pass.isBlank()) { Toast.makeText(this, "Enter Wi-Fi password", Toast.LENGTH_SHORT).show(); return }
                
                viewModel.pushConfigToEsp32(ssid, pass)
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

        updateBottomSheetState(viewModel.uiState.value)
        activeBottomSheetDialog?.show()
    }

    private fun updateBottomSheetState(state: SensorSetupUiState) {
        val view = activeBottomSheetView ?: return
        val dialog = activeBottomSheetDialog ?: return

        val loadingView = view.findViewById<View>(R.id.bottom_sheet_wifi_loading)
        val emptyView = view.findViewById<View>(R.id.bottom_sheet_wifi_empty)
        val recyclerView = view.findViewById<RecyclerView>(R.id.bottom_sheet_wifi_recycler)

        val networks = state.scannedNetworks
        if (networks == null) {
            loadingView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.GONE
        } else if (networks.isEmpty()) {
            loadingView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
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

    inner class SensorSetupAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var wifiHolder: WifiViewHolder? = null
        private val mapViewRefs = mutableListOf<MapView>()

        fun resumeMaps() {
            mapViewRefs.forEach { it.onResume() }
        }

        fun pauseMaps() {
            mapViewRefs.forEach { it.onPause() }
        }
        
        fun updateViews(state: SensorSetupUiState, position: Int) {
            if (position == 0) {
                wifiHolder?.apply {
                    if (state.isConnectedToQuakeSetup) {
                        progressBar.visibility = View.GONE
                        ssidContainer.visibility = View.VISIBLE
                        btnOpenWifi.visibility = View.GONE
                    } else {
                        progressBar.visibility = View.VISIBLE
                        ssidContainer.visibility = View.GONE
                        btnOpenWifi.visibility = View.VISIBLE
                    }
                }
            }
        }

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
            updateViews(viewModel.uiState.value, position)
        }

        inner class WifiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleText: TextView = view.findViewById(R.id.sensor_setup_instruction_title)
            val progressBar: LinearProgressIndicator = view.findViewById(R.id.sensor_setup_progress_bar)
            val ssidContainer: View = view.findViewById(R.id.sensor_setup_ssid_container)
            val btnOpenWifi: com.google.android.material.button.MaterialButton = view.findViewById(R.id.sensor_setup_btn_open_wifi)
            init { 
                view.findViewById<View>(R.id.sensor_setup_btn_close)?.setOnClickListener { finish() } 
                btnOpenWifi.setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            startActivity(android.content.Intent(android.provider.Settings.Panel.ACTION_WIFI))
                        } catch (e: Exception) {
                            startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                        }
                    } else {
                        startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                    }
                }
            }
        }

        inner class LocationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleText: TextView    = view.findViewById(R.id.sensor_setup_location_title)
            val subtitleText: TextView = view.findViewById(R.id.sensor_setup_location_subtitle)
            val latInput: EditText     = view.findViewById(R.id.sensor_setup_lat_input)
            val lonInput: EditText     = view.findViewById(R.id.sensor_setup_lon_input)
            val cityValue: TextView    = view.findViewById(R.id.sensor_setup_city_value)
            val mapView: MapView       = view.findViewById(R.id.sensor_setup_mapview)

            var isUpdatingFromMap = false
            var isUpdatingFromText = false

            fun setLocationProgrammatically(lat: Double, lon: Double) {
                isUpdatingFromMap = true
                latInput.setText(String.format(Locale.US, "%.5f", lat))
                lonInput.setText(String.format(Locale.US, "%.5f", lon))
                mapView.controller.setCenter(GeoPoint(lat, lon))
                isUpdatingFromMap = false
            }

            init { 
                mapViewRefs.add(mapView)
                view.findViewById<View>(R.id.sensor_setup_btn_close)?.setOnClickListener { finish() }

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
                
                mapView.setUseDataConnection(false)
                
                mapView.onResume()

                view.findViewById<View>(R.id.sensor_setup_btn_refresh_location)?.setOnClickListener {
                    Toast.makeText(this@SensorSetupActivity, "Fetching GPS...", Toast.LENGTH_SHORT).show()
                    viewModel.fetchLocation(forceGps = true)
                }

                mapView.setOnTouchListener { v, _ ->
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    latInput.clearFocus()
                    lonInput.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    false
                }

                mapView.addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        if (latInput.hasFocus() || lonInput.hasFocus()) return true
                        val center = mapView.mapCenter
                        if (!isUpdatingFromText) {
                            isUpdatingFromMap = true
                            latInput.setText(String.format(Locale.US, "%.5f", center.latitude))
                            lonInput.setText(String.format(Locale.US, "%.5f", center.longitude))
                            viewModel.processLocation(center.latitude, center.longitude)
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
                            viewModel.processLocation(lat, lon)
                            mapView.controller.setCenter(GeoPoint(lat, lon))
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
            val passToggle: ImageView = view.findViewById(R.id.sensor_setup_pass_toggle)
            
            init { 
                view.findViewById<View>(R.id.sensor_setup_btn_close)?.setOnClickListener { finish() } 
                
                passInput.typeface = android.graphics.Typeface.MONOSPACE
                ssidInput.typeface = android.graphics.Typeface.MONOSPACE
                
                var isPasswordVisible = false
                passToggle.setOnClickListener {
                    isPasswordVisible = !isPasswordVisible

                    if (isPasswordVisible) {
                        passInput.transformationMethod = android.text.method.HideReturnsTransformationMethod.getInstance()
                        passToggle.setImageResource(R.drawable.ic_visibility)
                    } else {
                        passInput.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                        passToggle.setImageResource(R.drawable.ic_visibility_off)
                    }
                    
                    passInput.setSelection(passInput.text.length)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkWiFiConnection()
        (binding.sensorSetupViewpager.adapter as? SensorSetupAdapter)?.resumeMaps()
    }

    override fun onPause() {
        super.onPause()
        (binding.sensorSetupViewpager.adapter as? SensorSetupAdapter)?.pauseMaps()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopNetworkMonitoring()
    }

    companion object { const val TAG = "SensorSetup" }
}