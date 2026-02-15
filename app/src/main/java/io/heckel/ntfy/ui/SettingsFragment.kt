package io.heckel.ntfy.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.SeekBar // Import SeekBar
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
import android.graphics.Point

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

    private lateinit var bottomPanel: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        repository = Repository.getInstance(requireContext())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. FIND VIEWS
        mapView = view.findViewById(R.id.mapview)
        bottomPanel = view.findViewById(R.id.bottom_floating_ui)
        val valueView = view.findViewById<TextView>(R.id.alert_radius_value)
        val slider = view.findViewById<SeekBar>(R.id.alert_radius_slider) // Correct Type
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

        // 3. APPLY LIMITS
        val worldBox = org.osmdroid.util.BoundingBox(85.0, 180.0, -85.0, -180.0)
        mapView.setScrollableAreaLimitDouble(worldBox)
        mapView.minZoomLevel = 3.0

//        // 4. ADD OVERLAYS (Scale Bar)
//        val scaleBarOverlay = org.osmdroid.views.overlay.ScaleBarOverlay(mapView).apply {
//            setAlignBottom(true)
//            setScaleBarOffset(20, 20)
//            setTextSize(12f * resources.displayMetrics.density)
//            setUnitsOfMeasure(org.osmdroid.views.overlay.ScaleBarOverlay.UnitsOfMeasure.metric)
//        }
//        mapView.overlays.add(scaleBarOverlay)

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
            tvLocationName.text = String.format(Locale.getDefault(), "%.4f, %.4f", currentLat, currentLon)
        }

        mapView.controller.setZoom(7.0)
        mapView.post { animateToOffset(startPoint) }

        // --- SLIDER LOGIC UPDATED FOR SEEKBAR ---
        val sharedPrefs = requireContext().getSharedPreferences(Repository.SHARED_PREFS_ID, 0)
        val initialRadius = sharedPrefs.getInt(Repository.SHARED_PREFS_ALERT_RADIUS, DEFAULT_ALERT_RADIUS_KM)

        // Convert radius back to progress steps (0-50)
        val initialProgress = (initialRadius / STEP_KM).coerceIn(0, MAX_PROGRESS)

        slider.progress = initialProgress // Use .progress instead of .value
        updateRadiusLabel(valueView, initialProgress)
        updateCenterMarker(startPoint)
        updateMapCircle(initialRadius, startPoint)

        // Handle the slider moving
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val radius = progress * STEP_KM
                updateRadiusLabel(valueView, progress)

                // Only update visual circle while dragging
                val center = GeoPoint(repository.getUserLatitude(), repository.getUserLongitude())
                updateMapCircle(radius, center)

                if (fromUser) {
                    sharedPrefs.edit { putInt(Repository.SHARED_PREFS_ALERT_RADIUS, radius) }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        // ----------------------------------------

        // Handle the buttons
        btnRefresh.setOnClickListener { checkPermissionAndRefresh() }
        // UPDATED: Use the new helper for manual Recenter
        btnRecenter.setOnClickListener {
            val lat = repository.getUserLatitude()
            val lon = repository.getUserLongitude()
            animateToOffset(GeoPoint(lat, lon))
        }
        btnZoomIn.setOnClickListener { mapView.controller.zoomIn() }
        btnZoomOut.setOnClickListener { mapView.controller.zoomOut() }
    }

    private fun animateToOffset(target: GeoPoint) {
        // 1. Calculate the offset (half the height of the bottom panel)
        val offsetPixels = bottomPanel.height / 2

        if (offsetPixels > 0) {
            val projection = mapView.projection

            // 2. Translate the target GeoPoint to screen pixels
            val targetPointPixels = projection.toPixels(target, null)

            // 3. Create a new pixel point that is 'offsetPixels' LOWER (South)
            // This forces the map center to be below the target, pushing the target UP.
            val newCenterPixels = Point(targetPointPixels.x, targetPointPixels.y + offsetPixels)

            // 4. Convert back to GeoPoint
            val newCenterGeoPoint = projection.fromPixels(newCenterPixels.x, newCenterPixels.y)

            // 5. Animate to the adjusted center
            mapView.controller.animateTo(newCenterGeoPoint)
        } else {
            // Fallback if view isn't laid out yet
            mapView.controller.animateTo(target)
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

    private fun updateCenterMarker(center: GeoPoint) {
        if (centerMarker != null) {
            mapView.overlays.remove(centerMarker)
        }

        centerMarker = org.osmdroid.views.overlay.Marker(mapView).apply {
            position = center
            setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_custom_crosshair)
            setInfoWindow(null)
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

                    // USE THE HELPER HERE
                    animateToOffset(newPoint)

                    updateCenterMarker(newPoint)

                    val slider = view?.findViewById<SeekBar>(R.id.alert_radius_slider)
                    val progress = slider?.progress ?: 0
                    val radius = progress * STEP_KM
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
                        address.locality ?: address.subAdminArea ?: address.adminArea
                    } else null
                } catch (e: Exception) { null }
            }

            if (!cityName.isNullOrBlank()) {
                repository.setUserCityName(cityName)
                tvLocationName.text = cityName
            } else {
                repository.setUserCityName("")
                tvLocationName.text = String.format(Locale.getDefault(), "%.4f, %.4f", lat, lon)
            }
        }
    }

    private fun updateRadiusLabel(valueView: TextView, progress: Int) {
        val radius = progress * STEP_KM
        valueView.text = if (radius >= MAX_RADIUS_KM) {
            "Global"
        } else {
            "${radius} km"
        }
    }

    private fun updateMapCircle(radiusKm: Int, center: GeoPoint) {
        if (mapCircle != null) {
            mapView.overlays.remove(mapCircle)
        }

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