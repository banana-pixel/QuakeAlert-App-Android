package io.heckel.ntfy.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import io.heckel.ntfy.BuildConfig
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import java.util.Locale

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private lateinit var repository: Repository
    private lateinit var mapView: MapView
    private lateinit var tvLocationName: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mapCircle: Polygon? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            refreshLocation()
        } else {
            Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        repository = Repository.getInstance(requireContext())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sharedPrefs = requireContext().getSharedPreferences(Repository.SHARED_PREFS_ID, 0)
        val valueView = view.findViewById<TextView>(R.id.alert_radius_value)
        val seekBar = view.findViewById<SeekBar>(R.id.alert_radius_seekbar)
        tvLocationName = view.findViewById(R.id.tv_location_name)
        val btnRefresh = view.findViewById<MaterialButton>(R.id.btn_refresh_location)

        mapView = view.findViewById(R.id.mapview)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        val currentLat = repository.getUserLatitude()
        val currentLon = repository.getUserLongitude()
        val currentCity = repository.getUserCityName()
        
        if (currentCity.isNotEmpty()) {
            tvLocationName.text = "Current Location: $currentCity"
        } else {
            tvLocationName.text = "Current Location: $currentLat, $currentLon"
        }

        val mapController = mapView.controller
        mapController.setZoom(7.0)
        val startPoint = GeoPoint(currentLat, currentLon)
        mapController.setCenter(startPoint)

        val initialRadius = sharedPrefs.getInt(Repository.SHARED_PREFS_ALERT_RADIUS, DEFAULT_ALERT_RADIUS_KM)
        val initialProgress = (initialRadius / STEP_KM).coerceIn(0, MAX_PROGRESS)
        seekBar.max = MAX_PROGRESS
        seekBar.progress = initialProgress
        updateRadiusLabel(valueView, initialProgress)
        updateMapCircle(initialRadius, startPoint)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val radius = progress * STEP_KM
                updateRadiusLabel(valueView, progress)
                val center = GeoPoint(repository.getUserLatitude(), repository.getUserLongitude())
                updateMapCircle(radius, center)
                if (fromUser) {
                    sharedPrefs.edit {
                        putInt(Repository.SHARED_PREFS_ALERT_RADIUS, radius)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        btnRefresh.setOnClickListener {
            checkPermissionAndRefresh()
        }
    }

    private fun checkPermissionAndRefresh() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                refreshLocation()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun refreshLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude
                    
                    repository.setUserLatitude(lat)
                    repository.setUserLongitude(lon)
                    
                    val newPoint = GeoPoint(lat, lon)
                    mapView.controller.animateTo(newPoint)
                    
                    val radius = (view?.findViewById<SeekBar>(R.id.alert_radius_seekbar)?.progress ?: 0) * STEP_KM
                    updateMapCircle(radius, newPoint)
                    
                    updateCityName(lat, lon)
                } else {
                    Toast.makeText(requireContext(), "Could not get current location", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun updateCityName(lat: Double, lon: Double) {
        lifecycleScope.launch {
            val cityName = withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        address.locality ?: address.subAdminArea ?: address.adminArea ?: "Unknown"
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            
            if (cityName != null) {
                repository.setUserCityName(cityName)
                tvLocationName.text = "Current Location: $cityName"
            } else {
                tvLocationName.text = "Current Location: $lat, $lon"
            }
        }
    }

    private fun updateRadiusLabel(valueView: TextView, progress: Int) {
        val radius = progress * STEP_KM
        valueView.text = if (radius >= MAX_RADIUS_KM) {
            "Radius: Global (All Quakes)"
        } else {
            "Radius: ${radius} km"
        }
    }

    private fun updateMapCircle(radiusKm: Int, center: GeoPoint) {
        if (mapCircle != null) {
            mapView.overlays.remove(mapCircle)
        }
        if (radiusKm >= MAX_RADIUS_KM) {
            mapView.invalidate()
            return
        }
        mapCircle = Polygon().apply {
            points = Polygon.pointsAsCircle(center, radiusKm * 1000.0)
            fillPaint.color = 0x33FF0000 // Semi-transparent red
            outlinePaint.color = 0xFFFF0000.toInt()
            outlinePaint.strokeWidth = 2f
        }
        mapView.overlays.add(mapCircle)
        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    companion object {
        private const val DEFAULT_ALERT_RADIUS_KM = 500
        private const val STEP_KM = 100
        private const val MAX_PROGRESS = 50
        private const val MAX_RADIUS_KM = MAX_PROGRESS * STEP_KM
    }
}
