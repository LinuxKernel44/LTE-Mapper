package com.ltemapper.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ltemapper.app.ui.navigation.LteMapperNavHost
import com.ltemapper.app.ui.navigation.TOP_LEVEL_DESTINATIONS
import com.ltemapper.app.ui.permissions.PermissionScreen
import com.ltemapper.app.ui.theme.LteMapperTheme
import com.ltemapper.app.util.REQUIRED_MAPPING_PERMISSIONS
import com.ltemapper.app.util.hasPermission
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LteMapperTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var missingPermissions by remember {
        mutableStateOf(REQUIRED_MAPPING_PERMISSIONS.filterNot { context.hasPermission(it) }.toSet())
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        missingPermissions = REQUIRED_MAPPING_PERMISSIONS.filterNot { context.hasPermission(it) }.toSet()
    }

    if (missingPermissions.isNotEmpty()) {
        PermissionScreen(
            missingPermissions = missingPermissions,
            onRequestPermissions = { launcher.launch(REQUIRED_MAPPING_PERMISSIONS.toTypedArray()) },
            onOpenSettings = { openAppSettings(context) },
        )
    } else {
        MainScaffold()
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            val isTopLevel = currentRoute == null || TOP_LEVEL_DESTINATIONS.any { it.destination.route == currentRoute }
            if (isTopLevel) {
                NavigationBar {
                    TOP_LEVEL_DESTINATIONS.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.destination.route,
                            onClick = {
                                navController.navigate(item.destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        LteMapperNavHost(navController = navController, modifier = Modifier.padding(padding))
    }
}
