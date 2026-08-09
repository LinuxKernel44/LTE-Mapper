package com.ltemapper.app.domain.model

/** One match from a place-name search (country, region, state, city, ...). */
data class GeocodingResult(
    val displayName: String,
    val placeType: String,
    val boundingBox: BoundingBox,
)
