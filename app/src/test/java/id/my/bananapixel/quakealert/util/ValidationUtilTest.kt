package id.my.bananapixel.quakealert.util

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ValidationUtil.
 * Tests coordinate validation, intensity validation, duration validation, etc.
 */
class ValidationUtilTest {

    // ============ Latitude Validation ============
    @Test
    fun validateLatitude_validValues() {
        assertEquals(0.0, ValidationUtil.validateLatitude(0.0))
        assertEquals(45.5, ValidationUtil.validateLatitude(45.5))
        assertEquals(-45.5, ValidationUtil.validateLatitude(-45.5))
        assertEquals(90.0, ValidationUtil.validateLatitude(90.0))
        assertEquals(-90.0, ValidationUtil.validateLatitude(-90.0))
    }

    @Test
    fun validateLatitude_outOfRange() {
        assertNull(ValidationUtil.validateLatitude(90.1))
        assertNull(ValidationUtil.validateLatitude(-90.1))
        assertNull(ValidationUtil.validateLatitude(180.0))
        assertNull(ValidationUtil.validateLatitude(-180.0))
    }

    @Test
    fun validateLatitude_nanAndNull() {
        assertNull(ValidationUtil.validateLatitude(Double.NaN))
        assertNull(ValidationUtil.validateLatitude(null))
    }

    // ============ Longitude Validation ============
    @Test
    fun validateLongitude_validValues() {
        assertEquals(0.0, ValidationUtil.validateLongitude(0.0))
        assertEquals(120.5, ValidationUtil.validateLongitude(120.5))
        assertEquals(-120.5, ValidationUtil.validateLongitude(-120.5))
        assertEquals(180.0, ValidationUtil.validateLongitude(180.0))
        assertEquals(-180.0, ValidationUtil.validateLongitude(-180.0))
    }

    @Test
    fun validateLongitude_outOfRange() {
        assertNull(ValidationUtil.validateLongitude(180.1))
        assertNull(ValidationUtil.validateLongitude(-180.1))
        assertNull(ValidationUtil.validateLongitude(270.0))
    }

    @Test
    fun validateLongitude_nanAndNull() {
        assertNull(ValidationUtil.validateLongitude(Double.NaN))
        assertNull(ValidationUtil.validateLongitude(null))
    }

    // ============ Coordinate Pair Validation ============
    @Test
    fun validateCoordinates_validPair() {
        val result = ValidationUtil.validateCoordinates(-6.9, 110.4)
        assertNotNull(result)
        assertEquals(-6.9, result?.first)
        assertEquals(110.4, result?.second)
    }

    @Test
    fun validateCoordinates_bothInvalid() {
        assertNull(ValidationUtil.validateCoordinates(91.0, 181.0))
        assertNull(ValidationUtil.validateCoordinates(Double.NaN, Double.NaN))
    }

    @Test
    fun validateCoordinates_oneInvalid() {
        assertNull(ValidationUtil.validateCoordinates(45.0, 181.0))
        assertNull(ValidationUtil.validateCoordinates(91.0, 120.0))
    }

    // ============ Intensity Validation ============
    @Test
    fun validateIntensity_validLevels() {
        assertEquals("I", ValidationUtil.validateIntensity("I"))
        assertEquals("II", ValidationUtil.validateIntensity("II"))
        assertEquals("V", ValidationUtil.validateIntensity("V"))
        assertEquals("X", ValidationUtil.validateIntensity("X"))
        assertEquals("X+", ValidationUtil.validateIntensity("X+"))
    }

    @Test
    fun validateIntensity_withSpaces() {
        assertEquals("V", ValidationUtil.validateIntensity("V (Gempa Kuat)"))
        assertEquals("VII", ValidationUtil.validateIntensity("VII (Gempa Sangat Kuat)"))
    }

    @Test
    fun validateIntensity_caseInsensitive() {
        assertEquals("V", ValidationUtil.validateIntensity("v"))
        assertEquals("V", ValidationUtil.validateIntensity("v (strong)"))
    }

    @Test
    fun validateIntensity_invalid() {
        assertNull(ValidationUtil.validateIntensity("XI"))
        assertNull(ValidationUtil.validateIntensity("INVALID"))
        assertNull(ValidationUtil.validateIntensity(""))
        assertNull(ValidationUtil.validateIntensity(null))
    }

    // ============ Duration Validation ============
    @Test
    fun validateDuration_validValues() {
        assertEquals(0, ValidationUtil.validateDuration(0))
        assertEquals(10, ValidationUtil.validateDuration(10))
        assertEquals(60, ValidationUtil.validateDuration(60))
        assertEquals(300, ValidationUtil.validateDuration(300))
    }

    @Test
    fun validateDuration_outOfRange() {
        assertNull(ValidationUtil.validateDuration(-1))
        assertNull(ValidationUtil.validateDuration(301))
        assertNull(ValidationUtil.validateDuration(999))
    }

    @Test
    fun validateDuration_null() {
        assertNull(ValidationUtil.validateDuration(null))
    }

    // ============ Location Validation ============
    @Test
    fun validateLocation_validValues() {
        assertEquals("Jakarta", ValidationUtil.validateLocation("Jakarta"))
        assertEquals("Bandung, West Java", ValidationUtil.validateLocation("Bandung, West Java"))
    }

    @Test
    fun validateLocation_whitespaceHandling() {
        assertEquals("Jakarta", ValidationUtil.validateLocation("  Jakarta  "))
        assertEquals("West Java", ValidationUtil.validateLocation("  West Java  "))
    }

    @Test
    fun validateLocation_invalid() {
        assertNull(ValidationUtil.validateLocation(""))
        assertNull(ValidationUtil.validateLocation("   "))
        assertNull(ValidationUtil.validateLocation(null))
    }

    // ============ Earthquake Time Validation ============
    @Test
    fun validateEarthquakeTime_recentPast() {
        val now = System.currentTimeMillis()
        val oneDayAgo = now - (24 * 60 * 60 * 1000)
        assertEquals(oneDayAgo, ValidationUtil.validateEarthquakeTime(oneDayAgo))
    }

    @Test
    fun validateEarthquakeTime_currentTime() {
        val now = System.currentTimeMillis()
        assertNotNull(ValidationUtil.validateEarthquakeTime(now))
    }

    @Test
    fun validateEarthquakeTime_tooOld() {
        val moreThanOneYearAgo = System.currentTimeMillis() - (400 * 24 * 60 * 60 * 1000)
        assertNull(ValidationUtil.validateEarthquakeTime(moreThanOneYearAgo))
    }

    @Test
    fun validateEarthquakeTime_futureRejected() {
        val future = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
        assertNull(ValidationUtil.validateEarthquakeTime(future))
    }

    @Test
    fun validateEarthquakeTime_invalidValues() {
        assertNull(ValidationUtil.validateEarthquakeTime(-1000))
        assertNull(ValidationUtil.validateEarthquakeTime(0))
        assertNull(ValidationUtil.validateEarthquakeTime(null))
    }

    // ============ Extension Functions ============
    @Test
    fun toValidOrNull_validDouble() {
        assertEquals(45.5, 45.5.toValidOrNull())
        assertEquals(0.0, 0.0.toValidOrNull())
    }

    @Test
    fun toValidOrNull_nanBecomesNull() {
        assertNull(Double.NaN.toValidOrNull())
    }

    @Test
    fun toRelativeTimeString_justNow() {
        val now = System.currentTimeMillis()
        val result = now.toRelativeTimeString()
        assertEquals("just now", result)
    }

    @Test
    fun toRelativeTimeString_minutesAgo() {
        val now = System.currentTimeMillis()
        val fiveMinutesAgo = now - (5 * 60_000)
        val result = fiveMinutesAgo.toRelativeTimeString()
        assertTrue(result.contains("5 minutes ago"))
    }

    @Test
    fun toRelativeTimeString_hoursAgo() {
        val now = System.currentTimeMillis()
        val twoHoursAgo = now - (2 * 60_000 * 60)
        val result = twoHoursAgo.toRelativeTimeString()
        assertTrue(result.contains("2 hours ago"))
    }
}
