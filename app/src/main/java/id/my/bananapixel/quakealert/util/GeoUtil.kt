package id.my.bananapixel.quakealert.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.Locale

private const val EARTH_RADIUS_KM = 6371.0
const val GEO_TAG_PREFIX = "geo:"

/**
 * Haversine distance in km between two points. Used for quake alert radius.
 */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val latDistance = Math.toRadians(lat2 - lat1)
    val lonDistance = Math.toRadians(lon2 - lon1)
    val sinLat = sin(latDistance / 2)
    val sinLon = sin(lonDistance / 2)
    val a =
        sinLat * sinLat + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sinLon * sinLon
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_KM * c
}

/**
 * Extracts lat/lon from a tags string containing "geo:lat;lon" (e.g. "earthquake,geo:-6.2;106.8").
 */
fun extractGeoCoordinates(tags: String): Pair<Double, Double>? {
    val geoTag = splitTags(tags).firstOrNull { it.trim().startsWith(GEO_TAG_PREFIX) } ?: return null
    val withoutPrefix = geoTag.trim().removePrefix(GEO_TAG_PREFIX)
    val coordinates = withoutPrefix.split(";")
    if (coordinates.size != 2) return null
    val lat = coordinates[0].trim().toDoubleOrNull() ?: return null
    val lon = coordinates[1].trim().toDoubleOrNull() ?: return null
    return Pair(lat, lon)
}

fun formatDistanceKm(distanceKm: Double): String {
    return String.format(Locale.US, "%.1f", distanceKm)
}
