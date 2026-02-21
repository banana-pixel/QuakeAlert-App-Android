package id.my.bananapixel.quakealert.domain

/**
 * Server/health status used in WarningFragment and SensorsFragment.
 * Convert to strings/colors only at the UI layer.
 */
enum class ServerHealthStatus {
    HEALTHY,
    CONNECTING,
    WARNING,
    CRITICAL;

    val isConnectionHealthy: Boolean get() = this == HEALTHY || this == WARNING
}

/**
 * Sensor status from API. Use enum for logic; parse from API string at boundary.
 */
enum class SensorStatus(val apiValue: String) {
    ONLINE("online"),
    OFFLINE("offline"),
    UNKNOWN("");

    companion object {
        fun fromApi(apiStatus: String?): SensorStatus = when {
            apiStatus == null || apiStatus.isBlank() -> UNKNOWN
            apiStatus.equals(ONLINE.apiValue, ignoreCase = true) -> ONLINE
            apiStatus.equals(OFFLINE.apiValue, ignoreCase = true) -> OFFLINE
            else -> UNKNOWN
        }
    }
}
