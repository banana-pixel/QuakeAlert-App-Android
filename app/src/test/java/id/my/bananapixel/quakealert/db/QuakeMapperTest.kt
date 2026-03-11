package id.my.bananapixel.quakealert.db

import id.my.bananapixel.quakealert.db.QuakeMapper.toEntity
import id.my.bananapixel.quakealert.ui.QuakeReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.test.assertFailsWith
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for [QuakeMapper.toEntity].
 *
 * These tests verify that the mapper FAILS LOUDLY on malformed data rather than
 * silently injecting dangerous defaults (Null Island, fake timestamps, etc.).
 *
 * Each "strict failure" test asserts [IllegalArgumentException] is thrown — confirming
 * that corrupt data can never reach the Room database.
 */
class QuakeMapperTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns a [QuakeReport] that passes all validation rules.
     * Override individual fields in each test to inject one specific bad value.
     *
     * The date is generated dynamically to always be "yesterday at noon UTC"
     * so it remains valid regardless of when the test suite runs.
     */
    private fun createValidQuakeReport(
        id: Int = 42,
        waktu_kejadian: String = yesterdayUtcDateString(),
        intensitas_maks: String = "IV",
        lokasi: String = "Jakarta, DKI Jakarta",
        deskripsi: String = "Gempa dirasakan di beberapa wilayah.",
        pga_maks: String = "12.34",
        station_id: String = "JKTA01",
        durasi: Double = 15.0,
        latitude: Double = -6.2,
        longitude: Double = 106.8
    ): QuakeReport = QuakeReport(
        id = id,
        waktu_kejadian = waktu_kejadian,
        intensitas_maks = intensitas_maks,
        lokasi = lokasi,
        deskripsi = deskripsi,
        pga_maks = pga_maks,
        station_id = station_id,
        durasi = durasi,
        latitude = latitude,
        longitude = longitude
    )

    /**
     * Produces "yyyy-MM-dd HH:mm:ss" for yesterday at 12:00:00 UTC.
     * This is always within the valid window (not future, not > 1 year old).
     */
    private fun yesterdayUtcDateString(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return String.format(
            "%04d-%02d-%02d 12:00:00",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Produces "yyyy-MM-dd HH:mm:ss" for tomorrow at 12:00:00 UTC.
     * This is always in the future and must be rejected.
     */
    private fun tomorrowUtcDateString(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return String.format(
            "%04d-%02d-%02d 12:00:00",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Happy Path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `valid report maps to QuakeData without throwing`() {
        val report = createValidQuakeReport()
        val entity = report.toEntity()

        assertNotNull(entity)
    }

    @Test
    fun `valid report id is mapped as string`() {
        val entity = createValidQuakeReport(id = 99).toEntity()
        assertEquals("99", entity.id)
    }

    @Test
    fun `valid report coordinates are preserved exactly`() {
        val entity = createValidQuakeReport(latitude = -6.2, longitude = 106.8).toEntity()
        assertEquals(-6.2, entity.latitude, 0.0001)
        assertEquals(106.8, entity.longitude, 0.0001)
    }

    @Test
    fun `valid report time is non-zero epoch`() {
        val entity = createValidQuakeReport().toEntity()
        assert(entity.time > 0L) { "Expected a valid epoch timestamp, got ${entity.time}" }
    }

    @Test
    fun `valid report intensity is preserved`() {
        val entity = createValidQuakeReport(intensitas_maks = "VII").toEntity()
        assertEquals("VII", entity.intensity)
    }

    @Test
    fun `valid report location is trimmed`() {
        val entity = createValidQuakeReport(lokasi = "  Bandung  ").toEntity()
        assertEquals("Bandung", entity.place)
    }

    @Test
    fun `valid report pga is preserved verbatim`() {
        val entity = createValidQuakeReport(pga_maks = "45.6").toEntity()
        assertEquals("45.6", entity.pga)
    }

    @Test
    fun `valid report station_id is preserved`() {
        val entity = createValidQuakeReport(station_id = "BNDG02").toEntity()
        assertEquals("BNDG02", entity.station_id)
    }

    @Test
    fun `magnitude is always hardcoded to 0_0 because API does not supply it`() {
        val entity = createValidQuakeReport().toEntity()
        assertEquals(0.0, entity.magnitude, 0.0)
    }

    @Test
    fun `valid intensity with extra description text is cleaned and accepted`() {
        // The API sometimes returns "V (Gempa Kuat)" — the mapper should accept this.
        val entity = createValidQuakeReport(intensitas_maks = "V (Gempa Kuat)").toEntity()
        assertEquals("V", entity.intensity)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Strict Failure: Date Corruption
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `unparseable date string throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException>(
            message = "Expected IllegalArgumentException for unparseable date"
        ) {
            createValidQuakeReport(waktu_kejadian = "Not a date").toEntity()
        }
    }

    @Test
    fun `future date string throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException>(
            message = "Expected IllegalArgumentException for a future date"
        ) {
            createValidQuakeReport(waktu_kejadian = tomorrowUtcDateString()).toEntity()
        }
    }

    @Test
    fun `blank date string throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            createValidQuakeReport(waktu_kejadian = "").toEntity()
        }
    }

    @Test
    fun `date more than one year in past throws IllegalArgumentException`() {
        // 400 days ago is safely outside the 1-year window
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.DAY_OF_YEAR, -400)
        val oldDate = String.format(
            "%04d-%02d-%02d 00:00:00",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        assertFailsWith<IllegalArgumentException>(
            message = "Expected IllegalArgumentException for a date older than 1 year"
        ) {
            createValidQuakeReport(waktu_kejadian = oldDate).toEntity()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Strict Failure: Coordinate Corruption
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `latitude out of bounds (too high) throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            createValidQuakeReport(latitude = 100.0).toEntity()
        }
    }

    @Test
    fun `latitude out of bounds (too low) throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            createValidQuakeReport(latitude = -91.0).toEntity()
        }
    }

    @Test
    fun `longitude out of bounds (too high) throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            createValidQuakeReport(longitude = 200.0).toEntity()
        }
    }

    @Test
    fun `longitude out of bounds (too low) throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            createValidQuakeReport(longitude = -181.0).toEntity()
        }
    }

    @Test
    fun `NaN latitude throws IllegalArgumentException`() {
        // Double.NaN is the default in QuakeReport — simulates a missing coord from API.
        assertFailsWith<IllegalArgumentException>(
            message = "NaN latitude must be rejected to prevent Null Island storage"
        ) {
            createValidQuakeReport(latitude = Double.NaN).toEntity()
        }
    }

    @Test
    fun `NaN longitude throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException>(
            message = "NaN longitude must be rejected to prevent Null Island storage"
        ) {
            createValidQuakeReport(longitude = Double.NaN).toEntity()
        }
    }

    @Test
    fun `both coordinates NaN throws IllegalArgumentException`() {
        // Simulates a report where the API returns no coordinates at all.
        assertFailsWith<IllegalArgumentException> {
            createValidQuakeReport(latitude = Double.NaN, longitude = Double.NaN).toEntity()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Strict Failure: Missing Required Strings
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `empty pga_maks throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException>(
            message = "Empty PGA must be rejected — it is a safety-critical metric"
        ) {
            createValidQuakeReport(pga_maks = "").toEntity()
        }
    }

    @Test
    fun `blank pga_maks (spaces only) throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            createValidQuakeReport(pga_maks = "   ").toEntity()
        }
    }

    @Test
    fun `empty station_id throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException>(
            message = "Empty station_id must be rejected — tracing requires a real station"
        ) {
            createValidQuakeReport(station_id = "").toEntity()
        }
    }

    @Test
    fun `blank station_id (spaces only) throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            createValidQuakeReport(station_id = "   ").toEntity()
        }
    }

    @Test
    fun `empty lokasi throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException>(
            message = "Empty location must be rejected — user cannot be shown a blank place"
        ) {
            createValidQuakeReport(lokasi = "").toEntity()
        }
    }

    @Test
    fun `blank lokasi (spaces only) throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            createValidQuakeReport(lokasi = "   ").toEntity()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Strict Failure: Invalid Intensity
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `unrecognized intensity string throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException>(
            message = "Non-standard intensity 'XYZ' must be rejected"
        ) {
            createValidQuakeReport(intensitas_maks = "XYZ").toEntity()
        }
    }

    @Test
    fun `intensity XI (above maximum) throws IllegalArgumentException`() {
        // The valid scale caps at X+ — XI is not a recognized level.
        assertFailsWith<IllegalArgumentException> {
            createValidQuakeReport(intensitas_maks = "XI").toEntity()
        }
    }

    @Test
    fun `empty intensity string throws IllegalArgumentException`() {
        // The default for missing intensity from API is "" — must be rejected.
        assertFailsWith<IllegalArgumentException> {
            createValidQuakeReport(intensitas_maks = "").toEntity()
        }
    }

    @Test
    fun `numeric intensity string throws IllegalArgumentException`() {
        // API might return "5" instead of "V" — must be rejected.
        assertFailsWith<IllegalArgumentException> {
            createValidQuakeReport(intensitas_maks = "5").toEntity()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Strict Failure: Invalid Duration
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `negative duration throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException>(
            message = "Negative duration is physically impossible and must be rejected"
        ) {
            createValidQuakeReport(durasi = -1.0).toEntity()
        }
    }

    @Test
    fun `duration above 300_seconds throws IllegalArgumentException`() {
        // 301 seconds exceeds the validated upper bound.
        assertFailsWith<IllegalArgumentException>(
            message = "Duration above 300 s is considered implausible and must be rejected"
        ) {
            createValidQuakeReport(durasi = 301.0).toEntity()
        }
    }

    @Test
    fun `impossibly large duration throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            createValidQuakeReport(durasi = 9999.0).toEntity()
        }
    }

    @Test
    fun `zero duration is valid and does not throw`() {
        // A recorded quake with 0 s duration is unusual but within the valid range [0, 300].
        val entity = createValidQuakeReport(durasi = 0.0).toEntity()
        assertEquals(0, entity.durasi)
    }

    @Test
    fun `maximum valid duration (300 s) does not throw`() {
        val entity = createValidQuakeReport(durasi = 300.0).toEntity()
        assertEquals(300, entity.durasi)
    }
}
