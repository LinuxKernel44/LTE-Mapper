package com.ltemapper.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ltemapper.app.domain.model.LOW_ACCURACY_THRESHOLD_METERS
import com.ltemapper.app.domain.model.LocationSample
import com.ltemapper.app.util.hasAnyLocationPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationTrackerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationTracker {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    override fun observeLocation(intervalMillis: Long): Flow<LocationSample> = callbackFlow {
        if (!context.hasAnyLocationPermission()) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // The provider can batch several fixes into one callback; sending only the last
                // one silently drops the earlier points in the batch instead of recording them.
                result.locations.forEach { trySend(it.toSample()) }
            }
        }

        runCatching {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }.onFailure { close(it) }

        awaitClose { client.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): LocationSample? {
        if (!context.hasAnyLocationPermission()) return null
        return suspendCancellableCoroutine { cont ->
            runCatching {
                client.lastLocation
                    .addOnSuccessListener { location -> cont.resume(location?.toSample()) }
                    .addOnFailureListener { cont.resume(null) }
            }.onFailure { cont.resume(null) }
        }
    }

    private fun Location.toSample(): LocationSample = LocationSample(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        altitudeMeters = if (hasAltitude()) altitude else null,
        speedMps = if (hasSpeed()) speed else null,
        bearingDeg = if (hasBearing()) bearing else null,
        timestamp = time,
        isLowAccuracy = hasAccuracy() && accuracy > LOW_ACCURACY_THRESHOLD_METERS,
    )
}
