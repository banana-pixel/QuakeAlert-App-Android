package id.my.bananapixel.quakealert.db

import id.my.bananapixel.quakealert.ui.QuakeReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [QuakeReportParser]: API JSON -> [QuakeReport] list.
 */
class QuakeReportParserTest {

    @Test
    fun parseReports_emptyArray_returnsEmptyList() {
        val result = QuakeReportParser.parseReports("[]")
        assertEquals(emptyList<QuakeReport>(), result)
    }

    @Test
    fun parseReports_singleItem_parsesCorrectly() {
        val json = """
            [{
                "id": 42,
                "waktu_kejadian": "2026-02-15 13:56:22",
                "intensitas_maks": "III",
                "lokasi": "Jakarta",
                "deskripsi": "Gempa kecil",
                "pga_maks": "0.5",
                "station_id": "ST001",
                "durasi": 10,
                "latitude": -6.2,
                "longitude": 106.8
            }]
        """.trimIndent()
        val result = QuakeReportParser.parseReports(json)
        assertEquals(1, result.size)
        with(result[0]) {
            assertEquals(42, id)
            assertEquals("2026-02-15 13:56:22", waktu_kejadian)
            assertEquals("III", intensitas_maks)
            assertEquals("Jakarta", lokasi)
            assertEquals("Gempa kecil", deskripsi)
            assertEquals("0.5", pga_maks)
            assertEquals("ST001", station_id)
            assertEquals(10, durasi)
            assertEquals(-6.2, latitude, 1e-9)
            assertEquals(106.8, longitude, 1e-9)
        }
    }

    @Test
    fun parseReports_multipleItems_parsesAll() {
        val json = """
            [
                {"id": 1, "waktu_kejadian": "2026-02-15 10:00:00", "intensitas_maks": "II", "lokasi": "A", "deskripsi": "", "pga_maks": "0", "station_id": "S1", "durasi": 5, "latitude": 0.0, "longitude": 0.0},
                {"id": 2, "waktu_kejadian": "2026-02-15 11:00:00", "intensitas_maks": "IV", "lokasi": "B", "deskripsi": "Strong", "pga_maks": "1.2", "station_id": "S2", "durasi": 20, "latitude": -5.0, "longitude": 105.0}
            ]
        """.trimIndent()
        val result = QuakeReportParser.parseReports(json)
        assertEquals(2, result.size)
        assertEquals(1, result[0].id)
        assertEquals("A", result[0].lokasi)
        assertEquals(2, result[1].id)
        assertEquals("B", result[1].lokasi)
        assertEquals("Strong", result[1].deskripsi)
    }

    @Test
    fun parseReports_missingOptionalFields_usesDefaults() {
        val json = """[{"id": 1}]"""
        val result = QuakeReportParser.parseReports(json)
        assertEquals(1, result.size)
        with(result[0]) {
            assertEquals(1, id)
            assertEquals("", waktu_kejadian)
            assertEquals("", intensitas_maks)
            assertEquals("", lokasi)
            assertEquals("", deskripsi)
            assertEquals("", pga_maks)
            assertEquals("", station_id)
            assertEquals(0, durasi)
            // org.json optDouble returns NaN for missing keys
            assert(latitude.isNaN()) { "expected NaN for missing latitude, got $latitude" }
            assert(longitude.isNaN()) { "expected NaN for missing longitude, got $longitude" }
        }
    }

    // --- parseQuakeTime ---

    @Test
    fun parseQuakeTime_validUtc_returnsEpochMillis() {
        // 2026-02-15 13:56:22 UTC -> epoch ms (approx 1771178182000)
        val ms = QuakeReportParser.parseQuakeTime("2026-02-15 13:56:22")
        assertTrue(ms > 1771000000000L && ms < 1772000000000L)
        // 1970-01-01 00:00:00 UTC = 0
        assertEquals(0L, QuakeReportParser.parseQuakeTime("1970-01-01 00:00:00"))
    }

    @Test
    fun parseQuakeTime_nullOrEmpty_returnsCurrentTime() {
        val now = System.currentTimeMillis()
        val resultNull = QuakeReportParser.parseQuakeTime(null)
        val resultEmpty = QuakeReportParser.parseQuakeTime("")
        assertTrue(resultNull >= now - 1000 && resultNull <= now + 1000)
        assertTrue(resultEmpty >= now - 1000 && resultEmpty <= now + 1000)
    }

    @Test
    fun parseQuakeTime_wrongFormat_returnsCurrentTime() {
        val now = System.currentTimeMillis()
        val result = QuakeReportParser.parseQuakeTime("15/02/2026 13:56:22")
        assertTrue(result >= now - 1000 && result <= now + 1000)
    }

    // --- parseReports edge cases ---

    @Test
    fun parseReports_emptyString_returnsEmptyList() {
        assertEquals(emptyList<QuakeReport>(), QuakeReportParser.parseReports(""))
        assertEquals(emptyList<QuakeReport>(), QuakeReportParser.parseReports("   "))
    }

    @Test
    fun parseReports_invalidJson_returnsEmptyList() {
        assertEquals(emptyList<QuakeReport>(), QuakeReportParser.parseReports("not json"))
        assertEquals(emptyList<QuakeReport>(), QuakeReportParser.parseReports("{}}"))
    }

    @Test
    fun parseReports_rootIsObjectNotArray_returnsEmptyList() {
        assertEquals(emptyList<QuakeReport>(), QuakeReportParser.parseReports("{}"))
        assertEquals(emptyList<QuakeReport>(), QuakeReportParser.parseReports("{\"key\":\"value\"}"))
    }
}
