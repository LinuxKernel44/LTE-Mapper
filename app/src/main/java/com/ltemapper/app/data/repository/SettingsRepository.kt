package com.ltemapper.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ltemapper.app.domain.model.CustomTileSourceConfig
import com.ltemapper.app.domain.model.MapTileSource
import com.ltemapper.app.domain.model.MappingSettings
import com.ltemapper.app.domain.model.SignalThresholds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "mapping_settings")

private object Keys {
    val MIN_DISTANCE_M = floatPreferencesKey("min_distance_m")
    val MIN_INTERVAL_MS = longPreferencesKey("min_interval_ms")
    val LOCATION_UPDATE_INTERVAL_MS = longPreferencesKey("location_update_interval_ms")
    val BACKGROUND_MAPPING_ENABLED = booleanPreferencesKey("background_mapping_enabled")
    val EXCELLENT_MIN_DBM = intPreferencesKey("excellent_min_dbm")
    val GOOD_MIN_DBM = intPreferencesKey("good_min_dbm")
    val FAIR_MIN_DBM = intPreferencesKey("fair_min_dbm")
    val WEAK_MIN_DBM = intPreferencesKey("weak_min_dbm")
    val NO_SIGNAL_MAX_DBM = intPreferencesKey("no_signal_max_dbm")
    val TILE_SOURCE = stringPreferencesKey("tile_source")
    val CUSTOM_TILE_URL = stringPreferencesKey("custom_tile_url")
    val CUSTOM_TILE_API_KEY = stringPreferencesKey("custom_tile_api_key")
    val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    val settingsFlow: Flow<MappingSettings> = context.dataStore.data.map { prefs ->
        val defaults = MappingSettings.DEFAULT
        MappingSettings(
            minDistanceMeters = prefs[Keys.MIN_DISTANCE_M] ?: defaults.minDistanceMeters,
            minIntervalMillis = prefs[Keys.MIN_INTERVAL_MS] ?: defaults.minIntervalMillis,
            locationUpdateIntervalMillis = prefs[Keys.LOCATION_UPDATE_INTERVAL_MS] ?: defaults.locationUpdateIntervalMillis,
            backgroundMappingEnabled = prefs[Keys.BACKGROUND_MAPPING_ENABLED] ?: defaults.backgroundMappingEnabled,
            signalThresholds = SignalThresholds(
                excellentMinDbm = prefs[Keys.EXCELLENT_MIN_DBM] ?: defaults.signalThresholds.excellentMinDbm,
                goodMinDbm = prefs[Keys.GOOD_MIN_DBM] ?: defaults.signalThresholds.goodMinDbm,
                fairMinDbm = prefs[Keys.FAIR_MIN_DBM] ?: defaults.signalThresholds.fairMinDbm,
                weakMinDbm = prefs[Keys.WEAK_MIN_DBM] ?: defaults.signalThresholds.weakMinDbm,
                noSignalMaxDbm = prefs[Keys.NO_SIGNAL_MAX_DBM] ?: defaults.signalThresholds.noSignalMaxDbm,
            ),
            tileSource = prefs[Keys.TILE_SOURCE]
                ?.let { name -> runCatching { MapTileSource.valueOf(name) }.getOrNull() }
                ?: defaults.tileSource,
            customTileSource = CustomTileSourceConfig(
                urlTemplate = prefs[Keys.CUSTOM_TILE_URL] ?: defaults.customTileSource.urlTemplate,
                apiKey = prefs[Keys.CUSTOM_TILE_API_KEY] ?: defaults.customTileSource.apiKey,
            ),
            downloadOnWifiOnly = prefs[Keys.DOWNLOAD_WIFI_ONLY] ?: defaults.downloadOnWifiOnly,
        )
    }

    suspend fun updateDownloadOnWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.DOWNLOAD_WIFI_ONLY] = wifiOnly }
    }

    suspend fun updateTileSource(source: MapTileSource, custom: CustomTileSourceConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TILE_SOURCE] = source.name
            prefs[Keys.CUSTOM_TILE_URL] = custom.urlTemplate
            prefs[Keys.CUSTOM_TILE_API_KEY] = custom.apiKey
        }
    }

    suspend fun updateMappingKnobs(minDistanceMeters: Float, minIntervalMillis: Long, locationUpdateIntervalMillis: Long, backgroundMappingEnabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MIN_DISTANCE_M] = minDistanceMeters
            prefs[Keys.MIN_INTERVAL_MS] = minIntervalMillis
            prefs[Keys.LOCATION_UPDATE_INTERVAL_MS] = locationUpdateIntervalMillis
            prefs[Keys.BACKGROUND_MAPPING_ENABLED] = backgroundMappingEnabled
        }
    }

    suspend fun updateThresholds(thresholds: SignalThresholds) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EXCELLENT_MIN_DBM] = thresholds.excellentMinDbm
            prefs[Keys.GOOD_MIN_DBM] = thresholds.goodMinDbm
            prefs[Keys.FAIR_MIN_DBM] = thresholds.fairMinDbm
            prefs[Keys.WEAK_MIN_DBM] = thresholds.weakMinDbm
            prefs[Keys.NO_SIGNAL_MAX_DBM] = thresholds.noSignalMaxDbm
        }
    }
}
