package com.ltemapper.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String) {
    data object Map : Destination("map")
    data object History : Destination("history")
    data object SessionDetail : Destination("history/{sessionId}") {
        fun createRoute(sessionId: Long) = "history/$sessionId"
        const val ARG_SESSION_ID = "sessionId"
    }
    data object Statistics : Destination("statistics")
    data object Settings : Destination("settings")
    data object Privacy : Destination("settings/privacy")
    data object OfflineMaps : Destination("settings/offline-maps")
}

data class TopLevelDestination(
    val destination: Destination,
    val label: String,
    val icon: ImageVector,
)

val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(Destination.Map, "Map", Icons.Filled.Map),
    TopLevelDestination(Destination.History, "History", Icons.Filled.History),
    TopLevelDestination(Destination.Statistics, "Statistics", Icons.Filled.BarChart),
    TopLevelDestination(Destination.Settings, "Settings", Icons.Filled.Settings),
)
