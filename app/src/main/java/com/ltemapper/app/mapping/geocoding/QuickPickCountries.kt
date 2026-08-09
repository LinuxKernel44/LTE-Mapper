package com.ltemapper.app.mapping.geocoding

import com.ltemapper.app.domain.model.BoundingBox
import com.ltemapper.app.domain.model.GeocodingResult

/**
 * One-tap shortcuts for common countries, so downloading doesn't always require a search round
 * trip. Bounding boxes are approximate rectangular envelopes (not precise polygon borders) — that
 * slightly over-covers some borders, which only means a handful of extra tiles get cached, never
 * a correctness problem for tile selection. For anything not listed here, use search instead.
 */
object QuickPickCountries {
    val ALL: List<GeocodingResult> = listOf(
        entry("France", 41.0, 51.5, -5.5, 9.8),
        entry("Germany", 47.2, 55.1, 5.8, 15.1),
        entry("Spain", 35.9, 43.9, -9.5, 4.4),
        entry("Italy", 35.4, 47.1, 6.6, 18.6),
        entry("United Kingdom", 49.8, 61.0, -8.7, 1.8),
        entry("Portugal", 36.8, 42.2, -9.6, -6.1),
        entry("Belgium", 49.4, 51.6, 2.5, 6.5),
        entry("Netherlands", 50.7, 53.6, 3.3, 7.3),
        entry("Switzerland", 45.8, 47.9, 5.9, 10.6),
        entry("Austria", 46.3, 49.1, 9.5, 17.2),
        entry("Luxembourg", 49.4, 50.2, 5.7, 6.6),
        entry("Poland", 49.0, 55.0, 14.0, 24.2),
        entry("Czech Republic", 48.5, 51.1, 12.0, 18.9),
        entry("Sweden", 55.3, 69.1, 10.9, 24.2),
        entry("Norway", 57.9, 71.3, 4.5, 31.3),
        entry("Denmark", 54.5, 57.8, 8.0, 15.2),
        entry("Finland", 59.7, 70.1, 20.5, 31.6),
        entry("Ireland", 51.4, 55.4, -10.7, -5.9),
        entry("Greece", 34.7, 41.8, 19.3, 29.7),
        entry("Morocco", 27.6, 36.0, -13.2, -0.9),
        entry("Algeria", 18.9, 37.1, -8.7, 12.0),
        entry("Tunisia", 30.2, 37.6, 7.5, 11.6),
        entry("Senegal", 12.3, 16.7, -17.6, -11.3),
        entry("Ivory Coast", 4.3, 10.7, -8.6, -2.5),
        entry("Cameroon", 1.6, 13.1, 8.4, 16.2),
        entry("United States (contiguous)", 24.5, 49.4, -125.0, -66.9),
        entry("Canada", 41.7, 83.1, -141.0, -52.6),
        entry("Mexico", 14.5, 32.7, -118.4, -86.7),
        entry("Brazil", -33.8, 5.3, -74.0, -34.8),
        entry("Argentina", -55.1, -21.8, -73.6, -53.6),
        entry("Japan", 24.0, 45.6, 122.9, 145.8),
        entry("China", 18.2, 53.6, 73.5, 134.8),
        entry("India", 6.5, 35.5, 68.1, 97.4),
        entry("Australia", -43.6, -10.7, 113.2, 153.6),
        entry("South Korea", 33.1, 38.6, 125.1, 129.6),
    )

    private fun entry(name: String, south: Double, north: Double, west: Double, east: Double) = GeocodingResult(
        displayName = name,
        placeType = "country",
        boundingBox = BoundingBox(minLat = south, maxLat = north, minLon = west, maxLon = east),
    )
}
