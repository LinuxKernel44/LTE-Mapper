package com.ltemapper.app.mapping.geocoding

import com.ltemapper.app.BuildConfig
import com.ltemapper.app.domain.model.BoundingBox
import com.ltemapper.app.domain.model.GeocodingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject

/**
 * Resolves a free-text place name (country, region, state, city, ...) to a bounding box via
 * OpenStreetMap's Nominatim search API — this covers both the "whole country" and "by region"
 * cases from a single lookup, without shipping and maintaining a country-boundary database.
 *
 * Respects Nominatim's usage policy (https://operations.osmfoundation.org/policies/nominatim/):
 * a descriptive User-Agent, and callers only invoke [search] on an explicit user action (a
 * "Search" tap), never per keystroke, so requests stay well under the 1/second fair-use limit.
 */
class NominatimGeocoder @Inject constructor() {

    suspend fun search(query: String, limit: Int = 5): List<GeocodingResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("$BASE_URL?format=json&limit=$limit&addressdetails=0&q=$encoded")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "LTE-Mapper/${BuildConfig.VERSION_NAME} (Android; offline-map-download)")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext emptyList()
            val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            parse(body)
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): List<GeocodingResult> {
        val array = JSONArray(body)
        val results = mutableListOf<GeocodingResult>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val bbox = obj.optJSONArray("boundingbox") ?: continue
            if (bbox.length() != 4) continue
            val south = bbox.optString(0).toDoubleOrNull() ?: continue
            val north = bbox.optString(1).toDoubleOrNull() ?: continue
            val west = bbox.optString(2).toDoubleOrNull() ?: continue
            val east = bbox.optString(3).toDoubleOrNull() ?: continue

            results.add(
                GeocodingResult(
                    displayName = obj.optString("display_name", "Unknown place"),
                    placeType = obj.optString("type", obj.optString("class", "place")),
                    boundingBox = BoundingBox(minLat = south, maxLat = north, minLon = west, maxLon = east),
                ),
            )
        }
        return results
    }

    companion object {
        private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
    }
}
