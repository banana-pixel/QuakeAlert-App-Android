package io.heckel.ntfy.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.View
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
import com.google.android.material.slider.Slider
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
    private var centerMarker: org.osmdroid.views.overlay.Marker? = null

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

        // 1. FIND VIEWS FIRST
        mapView = view.findViewById(R.id.mapview)
        val scrollView = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.nested_scroll_view)
        val valueView = view.findViewById<TextView>(R.id.alert_radius_value)
        val slider = view.findViewById<Slider>(R.id.alert_radius_slider)
        tvLocationName = view.findViewById(R.id.tv_location_name)
        val btnRefresh = view.findViewById<MaterialButton>(R.id.btn_refresh_location)
        val btnRecenter = view.findViewById<MaterialButton>(R.id.btn_recenter)
        val btnZoomIn = view.findViewById<MaterialButton>(R.id.btn_zoom_in)
        val btnZoomOut = view.findViewById<MaterialButton>(R.id.btn_zoom_out)

        // 2. CONFIGURE MAP BASICS
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)
        mapView.setTilesScaledToDpi(true)
        mapView.isHorizontalMapRepetitionEnabled = false
        mapView.isVerticalMapRepetitionEnabled = false

        // 3. APPLY LIMITS (No more black space!)
        val worldBox = org.osmdroid.util.BoundingBox(85.0, 180.0, -85.0, -180.0)
        mapView.setScrollableAreaLimitDouble(worldBox)
        mapView.minZoomLevel = 3.0

        // 4. ADD OVERLAYS (Scale Bar)
        val scaleBarOverlay = org.osmdroid.views.overlay.ScaleBarOverlay(mapView).apply {
            setAlignBottom(true)
            setScaleBarOffset(20, 20)
            setTextSize(12f * resources.displayMetrics.density)
            setUnitsOfMeasure(org.osmdroid.views.overlay.ScaleBarOverlay.UnitsOfMeasure.metric)
        }
        mapView.overlays.add(scaleBarOverlay)

        // 5. TOUCH & GESTURES
        mapView.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        // 6. INITIAL DATA LOAD
        val currentLat = repository.getUserLatitude()
        val currentLon = repository.getUserLongitude()
        val currentCity = repository.getUserCityName()
        val startPoint = GeoPoint(currentLat, currentLon)

        if (currentCity.isNotEmpty() && currentCity != "Unknown") {
            tvLocationName.text = currentCity
        } else {
            // Show coordinates if city is empty or "Unknown"
            tvLocationName.text = String.format(Locale.getDefault(), "%.4f, %.4f", currentLat, currentLon)
        }

        mapView.controller.setZoom(7.0)
        mapView.controller.setCenter(startPoint)

        // Setup Slider & Labels
        val sharedPrefs = requireContext().getSharedPreferences(Repository.SHARED_PREFS_ID, 0)
        val initialRadius = sharedPrefs.getInt(Repository.SHARED_PREFS_ALERT_RADIUS, DEFAULT_ALERT_RADIUS_KM)
        val initialValue = (initialRadius / STEP_KM).toFloat().coerceIn(0f, MAX_PROGRESS.toFloat())

        slider.value = initialValue
        updateRadiusLabel(valueView, initialValue.toInt())
        updateCenterMarker(startPoint)
        updateMapCircle(initialRadius, startPoint)

        // Handle the slider moving
        slider.addOnChangeListener { _, value, fromUser ->
            val progress = value.toInt()
            val radius = progress * STEP_KM
            updateRadiusLabel(valueView, progress)
            val center = GeoPoint(repository.getUserLatitude(), repository.getUserLongitude())
            updateMapCircle(radius, center)
            if (fromUser) {
                sharedPrefs.edit { putInt(Repository.SHARED_PREFS_ALERT_RADIUS, radius) }
            }
        }

        // Handle the buttons
        btnRefresh.setOnClickListener { checkPermissionAndRefresh() }
        btnRecenter.setOnClickListener {
            val lat = repository.getUserLatitude()
            val lon = repository.getUserLongitude()
            mapView.controller.animateTo(GeoPoint(lat, lon))
        }
        btnZoomIn.setOnClickListener { mapView.controller.zoomIn() }
        btnZoomOut.setOnClickListener { mapView.controller.zoomOut() }
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

    private fun updateCenterMarker(center: GeoPoint) {
        if (centerMarker != null) {
            mapView.overlays.remove(centerMarker)
        }

        centerMarker = org.osmdroid.views.overlay.Marker(mapView).apply {
            position = center
            setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
            // Use a standard Android crosshair icon
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_custom_crosshair)
            setInfoWindow(null) // Disable the popup when clicked
        }

        mapView.overlays.add(centerMarker)
        mapView.invalidate()
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

                    updateCenterMarker(newPoint)

                    val slider = view?.findViewById<Slider>(R.id.alert_radius_slider)
                    val radius = (slider?.value?.toInt() ?: 0) * STEP_KM
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
                        // REMOVED: ?: "Unknown" fallback here
                        address.locality ?: address.subAdminArea ?: address.adminArea
                    } else null
                } catch (e: Exception) { null }
            }

            // Only save if we actually found a name
            if (!cityName.isNullOrBlank()) {
                repository.setUserCityName(cityName)
                tvLocationName.text = cityName
            } else {
                // If no name, clear the repo and show coordinates
                repository.setUserCityName("")
                tvLocationName.text = String.format(Locale.getDefault(), "%.4f, %.4f", lat, lon)
            }
        }
    }

    private fun updateRadiusLabel(valueView: TextView, progress: Int) {
        val radius = progress * STEP_KM
        valueView.text = if (radius >= MAX_RADIUS_KM) {
            "Alert Radius: Global"
        } else {
            "Alert Radius: ${radius} km"
        }
    }

    private fun updateMapCircle(radiusKm: Int, center: GeoPoint) {
        if (mapCircle != null) {
            mapView.overlays.remove(mapCircle)
        }

        // Always ensure the marker is updated/added so it stays on top
        updateCenterMarker(center)

        if (radiusKm >= MAX_RADIUS_KM) {
            mapView.invalidate()
            return
        }

        mapCircle = Polygon().apply {
            points = Polygon.pointsAsCircle(center, radiusKm * 1000.0)
            fillPaint.color = 0x22FF0000
            outlinePaint.color = 0x88FF0000.toInt()
            outlinePaint.strokeWidth = 8.0f
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
