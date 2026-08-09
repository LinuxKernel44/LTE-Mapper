package com.ltemapper.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasAnyLocationPermission(): Boolean =
    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

fun Context.hasFineLocationPermission(): Boolean = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

fun Context.hasPhoneStatePermission(): Boolean = hasPermission(Manifest.permission.READ_PHONE_STATE)

fun Context.hasBackgroundLocationPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        true
    }

fun Context.hasNotificationPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        true
    }

/** Permissions required before mapping can start at all (foreground-only). */
val REQUIRED_MAPPING_PERMISSIONS: List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.READ_PHONE_STATE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}
