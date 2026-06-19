package id.my.bananapixel.quakealert.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.msg.Sensor
import id.my.bananapixel.quakealert.util.systemDarkThemeOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class CoverageFragment : Fragment(R.layout.fragment_coverage) {

    private val viewModel: CoverageViewModel by viewModel()
    private val repository: Repository by inject()

    // ---------------------------------------------------------------------------
    // MapLibre
    // ---------------------------------------------------------------------------
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null

    /** Source/layer IDs — must be unique and stable across style reloads. */
    private companion object {
        const val SOURCE_SENSORS   = "source-sensors"
        const val SOURCE_CIRCLE    = "source-circle"
        const val SOURCE_USER_LOCATION = "source-user-location"
        const val LAYER_SENSORS    = "layer-sensors"
        const val LAYER_CIRCLE     = "layer-circle"
        const val LAYER_USER_LOCATION = "layer-user-location"
        const val IMAGE_SENSOR_DOT = "image-sensor-dot"

        // CARTO raster tile base
        const val MAP_TILE_BASE_URL = "basemaps.cartocdn.com/rastertiles"
        val MAP_TILE_SUBDOMAINS = arrayOf("a", "b", "c", "d")
    }

    // ---------------------------------------------------------------------------
    // Popup
    // ---------------------------------------------------------------------------
    private lateinit var statusCard: MapStatusCard
    private var popupView: View? = null
    private var popupContainer: FrameLayout? = null

    // ---------------------------------------------------------------------------
    // Other UI
    // ---------------------------------------------------------------------------
    private lateinit var tvLocationName: TextView
    private lateinit var bottomPanel: View
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentCenter = LatLng(
        Repository.DEFAULT_MAP_CENTER_LAT,
        Repository.DEFAULT_MAP_CENTER_LON
    )

    // ---------------------------------------------------------------------------
    // Permission
    // ---------------------------------------------------------------------------
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) refreshLocation() else
            Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MapLibre.getInstance() must be called before any MapView is inflated.
        // An empty string is fine for raster tile sources (no API key required).
        MapLibre.getInstance(requireContext())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. FIND VIEWS
        mapView        = view.findViewById(R.id.mapview)
        bottomPanel    = view.findViewById(R.id.bottom_floating_ui)
        tvLocationName = view.findViewById(R.id.tv_location_name)
        val valueView  = view.findViewById<TextView>(R.id.tv_distance_value)
        val slider     = view.findViewById<SeekBar>(R.id.alert_radius_slider)
        val btnRefresh  = view.findViewById<MaterialButton>(R.id.btn_refresh_location)
        val btnRecenter = view.findViewById<MaterialButton>(R.id.btn_recenter)
        val btnZoomIn   = view.findViewById<MaterialButton>(R.id.btn_zoom_in)
        val btnZoomOut  = view.findViewById<MaterialButton>(R.id.btn_zoom_out)

        // 2. POPUP SUPPORT — a FrameLayout overlay on top of the MapView
        statusCard = MapStatusCard(requireContext())
        popupContainer = FrameLayout(requireContext()).also { fl ->
            fl.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            fl.isClickable = false  // let touches fall through when popup is hidden
            (mapView.parent as? ViewGroup)?.addView(fl)
        }

        // 3. RESOLVE SAVED LOCATION
        if (repository.isUserLocationSet()) {
            currentCenter = LatLng(repository.getUserLatitude(), repository.getUserLongitude())
        }
        val currentCity = repository.getUserCityName()
        tvLocationName.text = when {
            !repository.isUserLocationSet() -> getString(R.string.settings_earthquake_location_not_set)
            currentCity.isNotEmpty() && currentCity != "Unknown" -> currentCity
            else -> "%.4f, %.4f".format(currentCenter.latitude, currentCenter.longitude)
        }

        // 4. INITIALISE MAP
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            applyStyle(map)

            val density = resources.displayMetrics.density
            val bottomPadding = (180 * density).toInt()
            map.setPadding(0, 0, 0, bottomPadding)

            // Initial camera position — offset upwards so the bottom panel doesn't cover it
            map.cameraPosition = CameraPosition.Builder()
                .target(currentCenter)
                .zoom(6.0)
                .build()
        }

        // 5. TOUCH — prevent ViewPager2 from stealing horizontal swipes
        mapView.setOnTouchListener { v, _ ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
        bottomPanel.setOnTouchListener { _, _ -> true }

        // 6. SLIDER
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) viewModel.updateRadius(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                seekBar?.parent?.requestDisallowInterceptTouchEvent(true)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.parent?.requestDisallowInterceptTouchEvent(false)
            }
        })

        // 7. BUTTONS
        btnRefresh.setOnClickListener { checkPermissionAndRefresh() }
        btnRecenter.setOnClickListener {
            mapLibreMap?.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(currentCenter).build()
                ), 400
            )
        }
        btnZoomIn.setOnClickListener {
            mapLibreMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        btnZoomOut.setOnClickListener {
            mapLibreMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }

        // 8. OBSERVE UI STATE
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateSensorMarkers(state.stations)

                    if (slider.progress != state.alertRadiusKm) {
                        slider.progress = state.alertRadiusKm
                    }
                    valueView.text = state.distanceLabel
                    updateRadiusCircle(state.alertRadiusKm, currentCenter)
                    updateUserLocationMarker(currentCenter)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // MapLibre style
    // ---------------------------------------------------------------------------

    /**
     * Applies a CARTO raster tile style and registers all sources and layers.
     * Also called on configuration change (dark/light mode switch).
     */
    private fun applyStyle(map: MapLibreMap) {
        val isDark   = requireContext().systemDarkThemeOn()
        val path     = if (isDark) "dark_all" else "light_all"
        val tileUrls = MAP_TILE_SUBDOMAINS.map { "https://$it.$MAP_TILE_BASE_URL/$path/{z}/{x}/{y}.png" }

        map.setStyle(Style.Builder().fromJson(buildCartoStyleJson(tileUrls, isDark))) { style ->
            addSensorDotImage(style)
            registerSources(style)
            registerLayers(style)
            setupMarkerClickListener(map, style)

            // Populate on style load
            updateSensorMarkers(viewModel.uiState.value.stations)
            updateRadiusCircle(viewModel.uiState.value.alertRadiusKm, currentCenter)
            updateUserLocationMarker(currentCenter)
        }
    }

    /**
     * Builds a minimal MapLibre style JSON string that uses CARTO raster tiles.
     */
    private fun buildCartoStyleJson(tileUrls: List<String>, isDark: Boolean): String {
        val urlsJson = tileUrls.joinToString(",") { "\"$it\"" }
        val bgColor  = if (isDark) "#121212" else "#F0F0F0"
        return """
        {
          "version": 8,
          "name": "${if (isDark) "Carto Dark" else "Carto Light"}",
          "sources": {
            "carto-raster": {
              "type": "raster",
              "tiles": [$urlsJson],
              "tileSize": 256,
              "attribution": "© CARTO © OpenStreetMap contributors"
            }
          },
          "layers": [
            {
              "id": "background",
              "type": "background",
              "paint": { "background-color": "$bgColor" }
            },
            {
              "id": "carto-tiles",
              "type": "raster",
              "source": "carto-raster"
            }
          ]
        }
        """.trimIndent()
    }

    /** Generates a small filled circle bitmap and registers it as a symbol image. */
    private fun addSensorDotImage(style: Style) {
        val size = 28
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF5350") // red dot
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint)
        style.addImage(IMAGE_SENSOR_DOT, bmp)
    }

    /** Registers empty GeoJSON sources for sensors and the radius circle. */
    private fun registerSources(style: Style) {
        style.addSource(GeoJsonSource(SOURCE_SENSORS, FeatureCollection.fromFeatures(emptyList())))
        style.addSource(GeoJsonSource(SOURCE_CIRCLE,  FeatureCollection.fromFeatures(emptyList())))
        style.addSource(GeoJsonSource(SOURCE_USER_LOCATION, FeatureCollection.fromFeatures(emptyList())))
    }

    /** Adds rendering layers on top of the base raster tiles. */
    private fun registerLayers(style: Style) {
        // Radius circle outline
        style.addLayer(
            LineLayer(LAYER_CIRCLE, SOURCE_CIRCLE).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#F44336")),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineOpacity(0.7f)
            )
        )
        // Sensor dot layer
        style.addLayer(
            CircleLayer(LAYER_SENSORS, SOURCE_SENSORS).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(Color.parseColor("#EF5350")),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        )
        // User location dot layer
        style.addLayer(
            CircleLayer(LAYER_USER_LOCATION, SOURCE_USER_LOCATION).withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(Color.parseColor("#2196F3")), // Blue dot
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        )
    }

    // ---------------------------------------------------------------------------
    // Sensor markers
    // ---------------------------------------------------------------------------

    /**
     * Replaces the sensor GeoJSON source with fresh [stations] data.
     * Each station is a GeoJSON Feature with properties for click handling.
     */
    private fun updateSensorMarkers(stations: List<Sensor>) {
        val style = mapLibreMap?.style ?: return
        val features = stations.mapNotNull { sensor ->
            val lat = sensor.latitude ?: return@mapNotNull null
            val lon = sensor.longitude ?: return@mapNotNull null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(lon, lat)).also { f ->
                f.addStringProperty("stationId", sensor.stationId ?: "")
                f.addStringProperty("status",    sensor.status    ?: "")
                f.addNumberProperty("lastPing",  (sensor.lastPing ?: 0L).toDouble())
            }
        }
        (style.getSource(SOURCE_SENSORS) as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // ---------------------------------------------------------------------------
    // Radius circle (GeoJSON LineString approximation)
    // ---------------------------------------------------------------------------

    private fun updateRadiusCircle(radiusKm: Int, center: LatLng) {
        val style = mapLibreMap?.style ?: return
        val points      = buildCirclePoints(center, radiusKm * 1000.0)
        val lineString  = LineString.fromLngLats(points)
        val feature     = Feature.fromGeometry(lineString)
        (style.getSource(SOURCE_CIRCLE) as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
    }

    private fun updateUserLocationMarker(center: LatLng) {
        val style = mapLibreMap?.style ?: return
        val feature = Feature.fromGeometry(Point.fromLngLat(center.longitude, center.latitude))
        (style.getSource(SOURCE_USER_LOCATION) as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
    }

    /**
     * Approximates a geodesic circle with [steps] points at [radiusMeters] from [center].
     */
    private fun buildCirclePoints(center: LatLng, radiusMeters: Double, steps: Int = 64): List<Point> {
        val lat = Math.toRadians(center.latitude)
        val lon = Math.toRadians(center.longitude)
        val d   = radiusMeters / 6_371_000.0 // angular distance in radians

        return (0..steps).map { i ->
            val bearing = Math.toRadians(i * 360.0 / steps)
            val pLat = asin(sin(lat) * cos(d) + cos(lat) * sin(d) * cos(bearing))
            val pLon = lon + atan2(
                sin(bearing) * sin(d) * cos(lat),
                cos(d) - sin(lat) * sin(pLat)
            )
            Point.fromLngLat(Math.toDegrees(pLon), Math.toDegrees(pLat))
        }
    }

    // ---------------------------------------------------------------------------
    // Marker click → popup
    // ---------------------------------------------------------------------------

    private fun setupMarkerClickListener(map: MapLibreMap, @Suppress("UNUSED_PARAMETER") style: Style) {
        map.addOnMapClickListener { latLng ->
            val screenPoint = map.projection.toScreenLocation(latLng)
            val features    = map.queryRenderedFeatures(screenPoint, LAYER_SENSORS)
            if (features.isNotEmpty()) {
                val f = features[0]
                val sensor = Sensor(
                    stationId = f.getStringProperty("stationId"),
                    status    = f.getStringProperty("status"),
                    lastPing  = f.getNumberProperty("lastPing")?.toLong(),
                    latitude  = latLng.latitude,
                    longitude = latLng.longitude
                )
                showPopup(sensor)
                true
            } else {
                hidePopup()
                false
            }
        }
    }

    private fun showPopup(sensor: Sensor) {
        val container = popupContainer ?: return
        hidePopup() // remove any stale popup first

        val cardView = statusCard.inflate()
        statusCard.bind(cardView, sensor) { hidePopup() }

        popupView = cardView
        container.addView(cardView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        })
        container.isClickable = true
    }

    private fun hidePopup() {
        popupView?.let { popupContainer?.removeView(it) }
        popupView = null
        popupContainer?.isClickable = false
    }

    // ---------------------------------------------------------------------------
    // Location
    // ---------------------------------------------------------------------------

    private fun checkPermissionAndRefresh() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            refreshLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun refreshLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude
                    repository.setUserLatitude(lat)
                    repository.setUserLongitude(lon)
                    currentCenter = LatLng(lat, lon)
                    mapLibreMap?.animateCamera(
                        CameraUpdateFactory.newLatLng(currentCenter), 400
                    )
                    val progress = view?.findViewById<SeekBar>(R.id.alert_radius_slider)?.progress ?: 0
                    updateRadiusCircle(progress, currentCenter)
                    updateUserLocationMarker(currentCenter)
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
                tvLocationName.text = "%.4f, %.4f".format(lat, lon)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Dark/light mode switch → re-apply tile source
    // ---------------------------------------------------------------------------

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        mapLibreMap?.let { applyStyle(it) }
    }

    // ---------------------------------------------------------------------------
    // MapLibre lifecycle forwarding — ALL 8 hooks are required
    // ---------------------------------------------------------------------------

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
