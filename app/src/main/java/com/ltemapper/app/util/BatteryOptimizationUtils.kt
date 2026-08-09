package com.ltemapper.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService

/** True once the user has exempted the app from Doze/App Standby battery optimizations. */
fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager: PowerManager = getSystemService() ?: return true
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

/**
 * Opens the system dialog that lets the user exempt this app from battery optimizations.
 * Only ever call this from an explicit user action (e.g. a Settings toggle) — never on launch.
 */
fun Context.requestIgnoreBatteryOptimizationsIntent(): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:$packageName"))
