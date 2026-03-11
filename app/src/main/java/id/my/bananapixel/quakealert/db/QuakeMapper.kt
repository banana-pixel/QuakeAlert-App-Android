package id.my.bananapixel.quakealert.db

import id.my.bananapixel.quakealert.ui.QuakeReport
import id.my.bananapixel.quakealert.util.ValidationUtil
import id.my.bananapixel.quakealert.util.toValidOrNull

/**
 * Maps a [QuakeReport] API DTO to a [QuakeData] Room entity.
 *
 * **Fail-fast design:** throws [IllegalArgumentException] if any required field is
 * malformed, missing, or out of range. This ensures the Room database only ever
 * stores 100% valid, verified earthquake records.
 *
 * The caller (Repository / RemoteMediator) is responsible for catching this exception
 * and mapping it to the correct [id.my.bananapixel.quakealert.domain.AppError].
 */
object QuakeMapper {

    /**
     * Converts a raw [QuakeReport] from the API to a validated [QuakeData] entity.
     *
     * @throws IllegalArgumentException if any required field (coordinates, time,
     *   intensity, location, duration, PGA, or station ID) is missing or invalid.
     */
    fun QuakeReport.toEntity(): QuakeData {
        // --- Coordinates (strict — no Null Island fallback) ---
        // QuakeReport declares latitude/longitude as Double with NaN defaults.
        // toValidOrNull() converts NaN → null so the strict validator can throw.
        val (lat, lon) = ValidationUtil.requireValidCoordinates(
            lat = latitude.toValidOrNull(),
            lon = longitude.toValidOrNull()
        )

        // --- Earthquake time (strict — no System.currentTimeMillis() fallback) ---
        val quakeTime = ValidationUtil.parseEarthquakeTime(waktu_kejadian)

        // --- Intensity (strict — no "I" fallback) ---
        val validIntensity = ValidationUtil.requireValidIntensity(intensitas_maks)

        // --- Location (strict — no "Unknown" fallback) ---
        val validLocation = ValidationUtil.requireValidLocation(lokasi)

        // --- Duration (strict — no 0 fallback; toInt() errors must surface) ---
        // durasi is a Double in QuakeReport; toInt() may truncate/overflow on extreme
        // values, but NumberFormatException is not possible here. Out-of-range is checked
        // by requireValidDuration.
        val validDuration = ValidationUtil.requireValidDuration(durasi.toInt())

        // --- PGA (required — no "0" default for a safety-critical metric) ---
        if (pga_maks.isBlank()) {
            throw IllegalArgumentException("PGA (pga_maks) is blank for report id=$id")
        }

        // --- Station ID (required — no "N/A" default) ---
        if (station_id.isBlank()) {
            throw IllegalArgumentException("Station ID (station_id) is blank for report id=$id")
        }

        return QuakeData(
            id = id.toString(),
            magnitude = 0.0, // NOTE: magnitude is not provided by this API endpoint; kept as 0.0
            place = validLocation,
            time = quakeTime,
            sync_time = System.currentTimeMillis(),
            description = deskripsi,
            latitude = lat,
            longitude = lon,
            pga = pga_maks,
            durasi = validDuration,
            station_id = station_id,
            intensity = validIntensity
        )
    }
}
