package id.my.bananapixel.quakealert.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for quake alert logic: tag splitting, earthquake tag detection, display priority.
 */
class AlertLogicTest {

    @Test
    fun splitTags_emptyOrNull_returnsEmptyList() {
        assertEquals(emptyList<String>(), splitTags(null))
        assertEquals(emptyList<String>(), splitTags(""))
    }

    @Test
    fun splitTags_singleTag_returnsOneElement() {
        assertEquals(listOf("earthquake"), splitTags("earthquake"))
    }

    @Test
    fun splitTags_commaSeparated_returnsList() {
        assertEquals(listOf("earthquake", "warning"), splitTags("earthquake,warning"))
    }

    @Test
    fun splitTags_withSpaces_noTrim() {
        // Current behavior: split by comma only, no trim on split parts
        assertEquals(listOf("earthquake", " warning"), splitTags("earthquake, warning"))
    }

    @Test
    fun hasEarthquakeTag_matchingTag_returnsTrue() {
        assertTrue(hasEarthquakeTag("earthquake"))
        assertTrue(hasEarthquakeTag("EARTHQUAKE"))
        assertTrue(hasEarthquakeTag("Earthquake"))
        assertTrue(hasEarthquakeTag("earthquake,warning"))
        assertTrue(hasEarthquakeTag("warning,earthquake"))
        assertTrue(hasEarthquakeTag(" earthquake "))
    }

    @Test
    fun hasEarthquakeTag_noMatchingTag_returnsFalse() {
        assertFalse(hasEarthquakeTag(null))
        assertFalse(hasEarthquakeTag(""))
        assertFalse(hasEarthquakeTag("warning"))
        assertFalse(hasEarthquakeTag("quake"))
        assertFalse(hasEarthquakeTag("warning,quake"))
    }

    @Test
    fun quakeDisplayPriority_nullDistance_returnsNotificationPriority() {
        assertEquals(3, quakeDisplayPriority(null, 100.0, 3))
        assertEquals(5, quakeDisplayPriority(null, 100.0, 5))
    }

    @Test
    fun quakeDisplayPriority_withinRadius_returnsMaxPriority() {
        assertEquals(PRIORITY_MAX, quakeDisplayPriority(10.0, 100.0, 3))
        assertEquals(PRIORITY_MAX, quakeDisplayPriority(0.0, 100.0, 1))
        assertEquals(PRIORITY_MAX, quakeDisplayPriority(99.9, 100.0, 2))
    }

    @Test
    fun quakeDisplayPriority_outsideRadius_returnsMinPriority() {
        assertEquals(PRIORITY_MIN, quakeDisplayPriority(101.0, 100.0, 5))
        assertEquals(PRIORITY_MIN, quakeDisplayPriority(500.0, 100.0, 5))
    }

    @Test
    fun quakeDisplayPriority_exactlyOnRadius_returnsMaxPriority() {
        assertEquals(PRIORITY_MAX, quakeDisplayPriority(100.0, 100.0, 3))
    }
}
