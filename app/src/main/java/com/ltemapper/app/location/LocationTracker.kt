package com.ltemapper.app.location

import com.ltemapper.app.domain.model.LocationSample
import kotlinx.coroutines.flow.Flow

interface LocationTracker {
    /** Raw, unfiltered GPS fixes at roughly [intervalMillis]. Callers apply their own
     * min-distance/min-time acceptance policy (see [LocationFilter]) on top of this stream. */
    fun observeLocation(intervalMillis: Long): Flow<LocationSample>

    suspend fun getLastKnownLocation(): LocationSample?
}
