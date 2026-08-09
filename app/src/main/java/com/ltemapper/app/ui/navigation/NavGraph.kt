package com.ltemapper.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ltemapper.app.ui.history.HistoryScreen
import com.ltemapper.app.ui.history.SessionDetailScreen
import com.ltemapper.app.ui.map.MapScreen
import com.ltemapper.app.ui.settings.OfflineMapsScreen
import com.ltemapper.app.ui.settings.PrivacyScreen
import com.ltemapper.app.ui.settings.SettingsScreen
import com.ltemapper.app.ui.statistics.StatisticsScreen

@Composable
fun LteMapperNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Destination.Map.route, modifier = modifier) {
        composable(Destination.Map.route) {
            MapScreen()
        }
        composable(Destination.History.route) {
            HistoryScreen(onOpenSession = { id -> navController.navigate(Destination.SessionDetail.createRoute(id)) })
        }
        composable(
            route = Destination.SessionDetail.route,
            arguments = listOf(navArgument(Destination.SessionDetail.ARG_SESSION_ID) { type = NavType.StringType }),
        ) {
            SessionDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Destination.Statistics.route) {
            StatisticsScreen()
        }
        composable(Destination.Settings.route) {
            SettingsScreen(
                onOpenPrivacy = { navController.navigate(Destination.Privacy.route) },
                onOpenOfflineMaps = { navController.navigate(Destination.OfflineMaps.route) },
            )
        }
        composable(Destination.Privacy.route) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
        composable(Destination.OfflineMaps.route) {
            OfflineMapsScreen(onBack = { navController.popBackStack() })
        }
    }
}
