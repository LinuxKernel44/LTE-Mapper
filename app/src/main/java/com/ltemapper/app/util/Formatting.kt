package com.ltemapper.app.util

import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDistance(meters: Double): String = if (meters >= 1000) {
    String.format(Locale.US, "%.2f km", meters / 1000.0)
} else {
    String.format(Locale.US, "%.0f m", meters)
}

fun formatDuration(millis: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

fun formatDbm(value: Int?): String = value?.let { "$it dBm" } ?: "N/A"
fun formatDb(value: Int?): String = value?.let { "$it dB" } ?: "N/A"
fun formatOrNa(value: Any?): String = value?.toString() ?: "N/A"

fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}
