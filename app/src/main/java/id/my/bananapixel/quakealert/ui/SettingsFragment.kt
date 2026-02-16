package id.my.bananapixel.quakealert.ui

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
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import id.my.bananapixel.quakealert.util.systemDarkThemeOn
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import java.util.Locale
import android.graphics.Point
import android.view.ViewTreeObserver

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

        // 2. CONFIGURE MAP BASICS (CARTO basemaps: Positron for light, Dark Matter for dark)
        mapView.setTileSource(getCartoTileSource(requireContext()))
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

        // Wait for layout, then center with a subtle zoom-in animation
        // Use zoom 7 for getOffsetCenter so it matches recenter (projection depends on zoom)
        mapView.controller.setZoom(7.0)
        mapView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                mapView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                mapView.controller.setCenter(startPoint)
                val targetCenter = getOffsetCenter(startPoint)
                mapView.controller.setCenter(targetCenter)
                mapView.controller.setZoom(6.0)
                mapView.controller.animateTo(targetCenter, 7.0, 450L)
            }
        })

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

    /** Returns the map center GeoPoint so the target appears above the bottom panel. */
    private fun getOffsetCenter(target: GeoPoint): GeoPoint {
        val offsetPixels = bottomPanel.height / 2
        if (offsetPixels <= 0) return target
        val projection = mapView.projection
        val targetPointPixels = projection.toPixels(target, null)
        val newCenterPixels = Point(targetPointPixels.x, targetPointPixels.y + offsetPixels)
        return projection.fromPixels(newCenterPixels.x, newCenterPixels.y) as GeoPoint
    }

    private fun animateToOffset(target: GeoPoint) {
        val targetCenter = getOffsetCenter(target)
        mapView.controller.animateTo(targetCenter, mapView.zoomLevelDouble, 400L)
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

    private fun getCartoTileSource(context: Context): XYTileSource {
        val isDark = context.systemDarkThemeOn()
        return if (isDark) {
            XYTileSource(
                "Carto Dark Matter",
                0, 20, 256, ".png",
                arrayOf(
                    "https://a.basemaps.cartocdn.com/rastertiles/dark_all/",
                    "https://b.basemaps.cartocdn.com/rastertiles/dark_all/",
                    "https://c.basemaps.cartocdn.com/rastertiles/dark_all/",
                    "https://d.basemaps.cartocdn.com/rastertiles/dark_all/"
                )
            )
        } else {
            XYTileSource(
                "Carto Positron",
                0, 20, 256, ".png",
                arrayOf(
                    "https://a.basemaps.cartocdn.com/rastertiles/light_all/",
                    "https://b.basemaps.cartocdn.com/rastertiles/light_all/",
                    "https://c.basemaps.cartocdn.com/rastertiles/light_all/",
                    "https://d.basemaps.cartocdn.com/rastertiles/light_all/"
                )
            )
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::mapView.isInitialized) {
            mapView.setTileSource(getCartoTileSource(requireContext()))
            mapView.invalidate()
        }
    }

    companion object {
        private const val DEFAULT_ALERT_RADIUS_KM = 500
        private const val STEP_KM = 100
        private const val MAX_PROGRESS = 50
        private const val MAX_RADIUS_KM = MAX_PROGRESS * STEP_KM
    }
}