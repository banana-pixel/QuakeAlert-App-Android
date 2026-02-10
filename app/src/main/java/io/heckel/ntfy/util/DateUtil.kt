package io.heckel.ntfy.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun convertUtcToLocal(utcTimeString: String): String {
    if (utcTimeString.isEmpty()) return "---"
    return try {
        // Input format from ESP32: "2026-02-09 13:11:53" (UTC)
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(utcTimeString)

        // Output format: "09 Feb 2026, 21:05:15 GMT+07:00"
        val outputFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss ZZZZ", Locale.getDefault())
        outputFormat.timeZone = TimeZone.getTimeZone("GMT+07:00")

        if (date != null) outputFormat.format(date) else utcTimeString
    } catch (e: Exception) {
        utcTimeString
    }
}

fun formatTimestampToLocal(timestamp: Long): String {
    val date = Date(timestamp * 1000)
    val outputFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss ZZZZ", Locale.getDefault())
    outputFormat.timeZone = TimeZone.getTimeZone("GMT+07:00")
    return outputFormat.format(date)
}
