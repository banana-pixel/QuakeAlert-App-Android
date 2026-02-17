package id.my.bananapixel.quakealert.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.Locale

class LocationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    init {
        Log.init(ctx)
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return@withContext Result.failure()
            }

            val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                return@withContext Result.success()
            }

            val location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) ?: fetchSingleUpdate(locationManager)
            if (location != null) {
                val repository = Repository.getInstance(applicationContext)
                repository.setUserLatitude(location.latitude)
                repository.setUserLongitude(location.longitude)
                resolveAndSavePlaceName(repository, location.latitude, location.longitude)
            }

            return@withContext Result.success()
        }
    }

    /** Same behavior as Settings "Update location": reverse geocode and save city/place name. */
    private fun resolveAndSavePlaceName(repository: Repository, lat: Double, lon: Double) {
        if (!Geocoder.isPresent()) return
        try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(applicationContext, Locale.getDefault()).getFromLocation(lat, lon, 1)
            val cityName = addresses?.firstOrNull()?.let { addr ->
                addr.locality ?: addr.subAdminArea ?: addr.adminArea
            }
            repository.setUserCityName(cityName?.takeIf { it.isNotBlank() } ?: "")
        } catch (_: Exception) {
            repository.setUserCityName("")
        }
    }

    private suspend fun fetchSingleUpdate(locationManager: LocationManager): Location? {
        return suspendCancellableCoroutine { continuation ->
            @Suppress("DEPRECATION")
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
            }
            try {
                @Suppress("DEPRECATION")
                locationManager.requestSingleUpdate(
                    LocationManager.NETWORK_PROVIDER,
                    listener,
                    Looper.getMainLooper()
                )
            } catch (_: SecurityException) {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }
        }
    }

    companion object {
        const val TAG = "NtfyLocationWorker"
        const val WORK_NAME_PERIODIC = "NtfyLocationWorkerPeriodic"
    }
}
