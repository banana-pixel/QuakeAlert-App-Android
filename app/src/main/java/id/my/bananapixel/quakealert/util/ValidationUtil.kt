package id.my.bananapixel.quakealert.util

/**
 * Validation utilities for QuakeAlert data.
 * Ensures data integrity at the boundary layer.
 */
object ValidationUtil {

    /**
     * Validates latitude is within valid range (-90 to 90).
     * Returns the value if valid, otherwise returns null.
     */
    fun validateLatitude(lat: Double?): Double? {
        if (lat == null || lat.isNaN()) return null
        return if (lat in -90.0..90.0) lat else null
    }

    /**
     * Validates longitude is within valid range (-180 to 180).
     * Returns the value if valid, otherwise returns null.
     */
    fun validateLongitude(lon: Double?): Double? {
        if (lon == null || lon.isNaN()) return null
        return if (lon in -180.0..180.0) lon else null
    }

    /**
     * Validates both coordinates together.
     * Returns Pair if both valid, otherwise null.
     */
    fun validateCoordinates(lat: Double?, lon: Double?): Pair<Double, Double>? {
        val validLat = validateLatitude(lat) ?: return null
        val validLon = validateLongitude(lon) ?: return null
        return Pair(validLat, validLon)
    }

    /**
     * Validates intensity is a known seismic intensity level (I-X, X+).
     * Returns cleaned intensity or null if invalid.
     */
    fun validateIntensity(intensity: String?): String? {
        if (intensity.isNullOrBlank()) return null
        val cleaned = intensity.trim().uppercase().split(" ").firstOrNull() ?: return null
        val validLevels = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "X+")
        return if (cleaned in validLevels) cleaned else null
    }

    /**
     * Validates duration is a reasonable value (0 to 300 seconds).
     */
    fun validateDuration(duration: Int?): Int? {
        if (duration == null) return null
        return if (duration in 0..300) duration else null
    }

    /**
     * Validates location string is not empty.
     */
    fun validateLocation(location: String?): String? {
        return if (!location.isNullOrBlank()) location.trim() else null
    }

    /**
     * Validates earthquake time is reasonable (not far in future, not too old).
     * Returns the timestamp if valid, otherwise null.
     */
    fun validateEarthquakeTime(timeMillis: Long?): Long? {
        if (timeMillis == null || timeMillis <= 0) return null
        val now = System.currentTimeMillis()
        val oneYearAgo = now - (365 * 24 * 60 * 60 * 1000)
        // Allow times up to 1 year in past, reject future times
        return if (timeMillis in oneYearAgo..now) timeMillis else null
    }
}

/**
 * Extension function to safely convert NaN to null.
 */
fun Double.toValidOrNull(): Double? = if (this.isNaN()) null else this

/**
 * Extension function to safely get relative time string from epoch millis.
 * Example: "2 minutes ago", "just now", "1 hour ago"
 */
fun Long.toRelativeTimeString(): String {
    val now = System.currentTimeMillis()
    val diffMillis = now - this
    
    return when {
        diffMillis < 0 -> "in the future (error)"
        diffMillis < 60_000 -> "just now"
        diffMillis < 60_000 * 60 -> "${diffMillis / 60_000} minutes ago"
        diffMillis < 60_000 * 60 * 24 -> "${diffMillis / (60_000 * 60)} hours ago"
        else -> "${diffMillis / (60_000 * 60 * 24)} days ago"
    }
}
