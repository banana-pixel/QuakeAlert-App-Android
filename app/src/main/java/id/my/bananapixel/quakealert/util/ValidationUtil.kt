package id.my.bananapixel.quakealert.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Validation utilities for QuakeAlert data.
 * All validation functions FAIL LOUDLY — they throw [IllegalArgumentException] on bad input
 * rather than returning null defaults that can be silently substituted with fake data.
 *
 * The nullable variants (returning null) are preserved only for callers that perform their
 * own null-branching (e.g. display layer). Repository/Mapper code must use the throwing variants.
 */
object ValidationUtil {

    // -------------------------------------------------------------------------
    // STRICT (THROWING) VARIANTS — use in Repository / Mapper layer
    // -------------------------------------------------------------------------

    /**
     * Parses a UTC earthquake time string ("yyyy-MM-dd HH:mm:ss") to epoch millis.
     * @throws IllegalArgumentException if the string is null/blank, unparseable,
     *   more than one year in the past, OR in the future.
     */
    fun parseEarthquakeTime(dateString: String?): Long {
        if (dateString.isNullOrBlank()) {
            throw IllegalArgumentException("Earthquake time is null or blank")
        }
        val parsed: Long = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(dateString)?.time
                ?: throw IllegalArgumentException("Date parser returned null for: '$dateString'")
        } catch (e: ParseException) {
            throw IllegalArgumentException("Unparseable earthquake time: '$dateString'", e)
        }
        val now = System.currentTimeMillis()
        if (parsed > now) {
            throw IllegalArgumentException(
                "Earthquake time '$dateString' ($parsed ms) is in the future (now=$now ms)"
            )
        }
        val oneYearAgo = now - (365L * 24 * 60 * 60 * 1000)
        if (parsed < oneYearAgo) {
            throw IllegalArgumentException(
                "Earthquake time '$dateString' ($parsed ms) is more than one year old"
            )
        }
        return parsed
    }

    /**
     * Validates and returns latitude.
     * @throws IllegalArgumentException if null, NaN, or outside [-90, 90].
     */
    fun requireValidLatitude(lat: Double?): Double {
        if (lat == null || lat.isNaN()) {
            throw IllegalArgumentException("Latitude is null or NaN")
        }
        if (lat !in -90.0..90.0) {
            throw IllegalArgumentException("Latitude out of range: $lat (expected -90 to 90)")
        }
        return lat
    }

    /**
     * Validates and returns longitude.
     * @throws IllegalArgumentException if null, NaN, or outside [-180, 180].
     */
    fun requireValidLongitude(lon: Double?): Double {
        if (lon == null || lon.isNaN()) {
            throw IllegalArgumentException("Longitude is null or NaN")
        }
        if (lon !in -180.0..180.0) {
            throw IllegalArgumentException("Longitude out of range: $lon (expected -180 to 180)")
        }
        return lon
    }

    /**
     * Validates both coordinates together.
     * @throws IllegalArgumentException if either coordinate is invalid.
     */
    fun requireValidCoordinates(lat: Double?, lon: Double?): Pair<Double, Double> {
        val validLat = requireValidLatitude(lat)
        val validLon = requireValidLongitude(lon)
        return Pair(validLat, validLon)
    }

    /**
     * Validates seismic intensity (I – X+).
     * @throws IllegalArgumentException if null, blank, or unrecognized.
     */
    fun requireValidIntensity(intensity: String?): String {
        if (intensity.isNullOrBlank()) {
            throw IllegalArgumentException("Intensity is null or blank")
        }
        val cleaned = intensity.trim().uppercase().split(" ").firstOrNull()
            ?: throw IllegalArgumentException("Intensity string could not be tokenized: '$intensity'")
        val validLevels = setOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "X+")
        if (cleaned !in validLevels) {
            throw IllegalArgumentException("Unrecognized intensity level: '$cleaned' (from '$intensity')")
        }
        return cleaned
    }

    /**
     * Validates duration is in [0, 300] seconds.
     * @throws IllegalArgumentException if null or out of range.
     */
    fun requireValidDuration(duration: Int?): Int {
        if (duration == null) {
            throw IllegalArgumentException("Duration is null")
        }
        if (duration !in 0..300) {
            throw IllegalArgumentException("Duration out of range: $duration (expected 0-300 s)")
        }
        return duration
    }

    /**
     * Validates location string is not blank.
     * @throws IllegalArgumentException if null or blank.
     */
    fun requireValidLocation(location: String?): String {
        if (location.isNullOrBlank()) {
            throw IllegalArgumentException("Location is null or blank")
        }
        return location.trim()
    }

    // -------------------------------------------------------------------------
    // NULLABLE VARIANTS — preserved for UI / display layer only
    // -------------------------------------------------------------------------

    /** Returns null if invalid (use only in UI/display-layer logic). */
    fun validateLatitude(lat: Double?): Double? {
        if (lat == null || lat.isNaN()) return null
        return if (lat in -90.0..90.0) lat else null
    }

    /** Returns null if invalid (use only in UI/display-layer logic). */
    fun validateLongitude(lon: Double?): Double? {
        if (lon == null || lon.isNaN()) return null
        return if (lon in -180.0..180.0) lon else null
    }

    /** Returns null if either coordinate is invalid (use only in UI/display-layer logic). */
    fun validateCoordinates(lat: Double?, lon: Double?): Pair<Double, Double>? {
        val validLat = validateLatitude(lat) ?: return null
        val validLon = validateLongitude(lon) ?: return null
        return Pair(validLat, validLon)
    }

    /** Returns null if invalid (use only in UI/display-layer logic). */
    fun validateIntensity(intensity: String?): String? {
        if (intensity.isNullOrBlank()) return null
        val cleaned = intensity.trim().uppercase().split(" ").firstOrNull() ?: return null
        val validLevels = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "X+")
        return if (cleaned in validLevels) cleaned else null
    }

    /** Returns null if invalid (use only in UI/display-layer logic). */
    fun validateDuration(duration: Int?): Int? {
        if (duration == null) return null
        return if (duration in 0..300) duration else null
    }

    /** Returns null if invalid (use only in UI/display-layer logic). */
    fun validateLocation(location: String?): String? {
        return if (!location.isNullOrBlank()) location.trim() else null
    }

    /** Returns null if invalid (use only in UI/display-layer logic). */
    fun validateEarthquakeTime(timeMillis: Long?): Long? {
        if (timeMillis == null || timeMillis <= 0) return null
        val now = System.currentTimeMillis()
        val oneYearAgo = now - (365 * 24 * 60 * 60 * 1000L)
        return if (timeMillis in oneYearAgo..now) timeMillis else null
    }
}

/**
 * Extension: safely convert NaN Double to null.
 */
fun Double.toValidOrNull(): Double? = if (this.isNaN()) null else this

/**
 * Extension: human-readable relative time string from epoch millis.
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
