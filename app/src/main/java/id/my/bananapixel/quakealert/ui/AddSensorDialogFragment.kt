package id.my.bananapixel.quakealert.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.color.MaterialColors
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.databinding.DialogAddSensorBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AddSensorDialogFragment : DialogFragment() {

    // ── ViewBinding ───────────────────────────────────────────────────────────

    private var _binding: DialogAddSensorBinding? = null
    private val binding get() = _binding!!

    // ── Koin ViewModel ────────────────────────────────────────────────────────

    private val viewModel: SensorSetupViewModel by viewModel()

    // ── Location ──────────────────────────────────────────────────────────────

    private lateinit var locationClient: FusedLocationProviderClient
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            viewModel.onLocationReceived(loc.latitude, loc.longitude, loc.accuracy)
        }
    }
    private var gpsStarted = false

    // ── Network ───────────────────────────────────────────────────────────────

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // ── Permissions ───────────────────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            registerNetworkCallback()
        } else {
            Toast.makeText(
                requireContext(),
                "Location & Wi-Fi permissions are required for sensor setup.",
                Toast.LENGTH_LONG
            ).show()
            dismiss()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogAddSensorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        locationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        setupClickListeners()
        observeUiState()
        requestPermissionsIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        stopGps()
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
            networkCallback = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        connectivityManager.bindProcessToNetwork(null)
        _binding = null
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        // Back / up arrow: step 0 dismisses; later steps retreat one page
        binding.btnNavBack.setOnClickListener {
            val flipper = binding.viewFlipperSensorSetup
            when (flipper.displayedChild) {
                0 -> dismiss()
                else -> {
                    if (flipper.displayedChild == 1) stopGps()
                    flipper.displayedChild--
                }
            }
        }

        // Step 1 → 2: advance and start GPS
        binding.btnStep1Continue.setOnClickListener {
            binding.viewFlipperSensorSetup.displayedChild = 1
            startGps()
        }

        // Step 2: POST to ESP32
        binding.btnStep2Send.setOnClickListener { viewModel.postGpsToEsp32() }

        // Step 3: WiFi list selection
        binding.lvWifiScan.setOnItemClickListener { _, _, position, _ ->
            val ssid = viewModel.uiState.value.wifiNetworks.getOrNull(position)?.ssid ?: return@setOnItemClickListener
            viewModel.selectSsid(ssid)
        }

        // Step 3: Finish / push WiFi config
        binding.btnStep3Finish.setOnClickListener {
            val password = binding.etWifiPassword.text?.toString().orEmpty()
            viewModel.postWifiConfig(password)
        }
    }

    // ── State observer ────────────────────────────────────────────────────────

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> applyState(state) }
            }
        }
    }

    private fun applyState(state: SensorSetupUiState) {
        val colorConnected = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorPrimary
        )
        val colorWaiting = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOutline
        )

        // ── Step 1 ────────────────────────────────────────────────────────────
        binding.ivWifiStatusIcon.setColorFilter(
            if (state.isEsp32Connected) colorConnected else colorWaiting,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        if (state.isEsp32Connected) {
            binding.tvStep1Title.text = "Sensor Found!"
            binding.tvStep1Status.text = state.networkStatusText
        }
        binding.btnStep1Continue.isEnabled = state.isEsp32Connected
        binding.btnStep1Continue.alpha = if (state.isEsp32Connected) 1f else 0.5f

        // ── Step 2 ────────────────────────────────────────────────────────────
        binding.chipCoordinates.text = state.coordsText
        binding.tvAccuracy.text = state.accuracyText
        val step2Ready = state.isLocationReady && !state.isPosting
        binding.btnStep2Send.isEnabled = step2Ready
        binding.btnStep2Send.alpha = if (step2Ready) 1f else 0.5f

        // ── Step 3 ────────────────────────────────────────────────────────────
        val displayNames = state.wifiNetworks.map { it.displayName }
        if ((binding.lvWifiScan.adapter as? ArrayAdapter<*>)?.count != displayNames.size) {
            binding.lvWifiScan.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_single_choice,
                displayNames,
            )
        }
        val selectedIdx = state.wifiNetworks.indexOfFirst { it.ssid == state.selectedSsid }
        if (selectedIdx >= 0) binding.lvWifiScan.setItemChecked(selectedIdx, true)

        binding.tvSelectedSsid.text =
            if (state.selectedSsid != null) "Selected: ${state.selectedSsid}" else "Selected: None"

        val step3Ready = state.selectedSsid != null && !state.isPosting
        binding.btnStep3Finish.isEnabled = step3Ready
        binding.btnStep3Finish.alpha = if (step3Ready) 1f else 0.5f

        // ── One-shot event ────────────────────────────────────────────────────
        state.event?.let { event ->
            when (event) {
                is SensorSetupEvent.AdvanceToStep3 -> {
                    binding.viewFlipperSensorSetup.displayedChild = 2
                    stopGps()
                }
                is SensorSetupEvent.SetupComplete -> {
                    Toast.makeText(
                        requireContext(), "Sensor setup complete!", Toast.LENGTH_SHORT
                    ).show()
                    dismiss()
                }
                is SensorSetupEvent.ShowError -> {
                    Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                }
            }
            viewModel.consumeEvent()
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun requestPermissionsIfNeeded() {
        val required = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_WIFI_STATE)
            add(Manifest.permission.CHANGE_WIFI_STATE)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) registerNetworkCallback()
        else permissionLauncher.launch(missing.toTypedArray())
    }

    // ── Network callback (detects ESP32 AP) ───────────────────────────────────

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val bound = connectivityManager.bindProcessToNetwork(network)
                if (bound) {
                    view?.post { viewModel.onEsp32Connected() }
                    connectivityManager.unregisterNetworkCallback(this)
                    networkCallback = null
                }
            }
        }.also { connectivityManager.requestNetwork(request, it) }
    }

    // ── GPS ───────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (gpsStarted) return
        gpsStarted = true
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .build()
        locationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    private fun stopGps() {
        if (!gpsStarted) return
        gpsStarted = false
        locationClient.removeLocationUpdates(locationCallback)
    }
}
