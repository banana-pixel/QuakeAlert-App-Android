package id.my.bananapixel.quakealert.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [distanceKm], [extractGeoCoordinates], [formatDistanceKm].
 */
class GeoUtilTest {

    // --- distanceKm (Haversine) ---

    @Test
    fun distanceKm_samePoint_returnsZero() {
        assertEquals(0.0, distanceKm(0.0, 0.0, 0.0, 0.0), 1e-6)
        assertEquals(0.0, distanceKm(-6.2, 106.8, -6.2, 106.8), 1e-6)
    }

    @Test
    fun distanceKm_knownPair_approximatelyCorrect() {
        // Two points ~1 degree apart in Indonesia; Haversine should give ~100–200 km
        val d = distanceKm(-6.2, 106.8, -7.2, 107.8)
        assertTrue(d >= 100.0 && d <= 200.0)
    }

    @Test
    fun distanceKm_equatorOneDegreeLongitude_about111km() {
        val d = distanceKm(0.0, 0.0, 0.0, 1.0)
        assertEquals(111.0, d, 2.0)
    }

    @Test
    fun distanceKm_equatorOneDegreeLatitude_about111km() {
        val d = distanceKm(0.0, 0.0, 1.0, 0.0)
        assertEquals(111.0, d, 2.0)
    }

    // --- extractGeoCoordinates ---

    @Test
    fun extractGeoCoordinates_validGeoTag_returnsPair() {
        assertEquals(Pair(-6.2, 106.8), extractGeoCoordinates("geo:-6.2;106.8"))
        assertEquals(Pair(-6.2, 106.8), extractGeoCoordinates("earthquake,geo:-6.2;106.8"))
        assertEquals(Pair(-6.2, 106.8), extractGeoCoordinates("earthquake,geo:-6.2;106.8,warning"))
    }

    @Test
    fun extractGeoCoordinates_withSpaces_returnsPair() {
        assertEquals(Pair(-6.2, 106.8), extractGeoCoordinates("earthquake, geo:-6.2;106.8"))
        assertEquals(Pair(-6.2, 106.8), extractGeoCoordinates("geo: -6.2 ; 106.8"))
    }

    @Test
    fun extractGeoCoordinates_noGeoTag_returnsNull() {
        assertNull(extractGeoCoordinates("earthquake,warning"))
        assertNull(extractGeoCoordinates(""))
    }

    @Test
    fun extractGeoCoordinates_wrongSeparator_returnsNull() {
        assertNull(extractGeoCoordinates("geo:-6.2,106.8"))
        assertNull(extractGeoCoordinates("geo:-6.2"))
    }

    @Test
    fun extractGeoCoordinates_nonNumeric_returnsNull() {
        assertNull(extractGeoCoordinates("geo:abc;106.8"))
        assertNull(extractGeoCoordinates("geo:-6.2;def"))
    }

    // --- formatDistanceKm ---

    @Test
    fun formatDistanceKm_formatsOneDecimal() {
        assertEquals("12.3", formatDistanceKm(12.34))
        assertEquals("0.0", formatDistanceKm(0.0))
        assertEquals("100.5", formatDistanceKm(100.456))
    }
}
