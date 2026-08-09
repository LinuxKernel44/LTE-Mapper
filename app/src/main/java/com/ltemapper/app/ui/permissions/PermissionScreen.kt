package com.ltemapper.app.ui.permissions

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ltemapper.app.R

data class PermissionRationale(
    val permission: String,
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
)

val MAPPING_PERMISSION_RATIONALES = listOf(
    PermissionRationale(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Icons.Filled.LocationOn,
        R.string.permission_location_title,
        R.string.permission_location_rationale,
    ),
    PermissionRationale(
        Manifest.permission.READ_PHONE_STATE,
        Icons.Filled.SignalCellularAlt,
        R.string.permission_phone_state_title,
        R.string.permission_phone_state_rationale,
    ),
    PermissionRationale(
        Manifest.permission.POST_NOTIFICATIONS,
        Icons.Filled.Notifications,
        R.string.permission_notifications_title,
        R.string.permission_notifications_rationale,
    ),
)

@Composable
fun PermissionScreen(
    missingPermissions: Set<String>,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val relevant = MAPPING_PERMISSION_RATIONALES.filter { it.permission in missingPermissions || missingPermissions.isEmpty() }
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringRes(R.string.permission_rationale_title), style = MaterialTheme.typography.titleLarge)
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(relevant) { rationale ->
                    PermissionCard(rationale)
                }
            }
            Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                Text(stringRes(R.string.permission_grant))
            }
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringRes(R.string.permission_open_settings))
            }
        }
    }
}

@Composable
private fun PermissionCard(rationale: PermissionRationale) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(rationale.icon, contentDescription = null)
            Text(stringRes(rationale.titleRes), style = MaterialTheme.typography.titleMedium)
            Text(stringRes(rationale.bodyRes), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id = id)
