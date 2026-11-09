package hr.abalarm.app.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import hr.abalarm.app.R
import hr.abalarm.app.appContainer
import hr.abalarm.app.permissions.Manufacturer
import hr.abalarm.app.permissions.PermissionHelper
import kotlinx.coroutines.launch

private val OkGreen = Color(0xFF2E7D32)
private val MissingRed = Color(0xFFC62828)

/** Čarobnjak: korak po korak kroz sva dopuštenja važna za pouzdan alarm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Povećava se pri svakom povratku na ekran (npr. iz postavki sustava) → ponovna provjera.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        // Možda je upravo odobreno dopuštenje za točne alarme → ponovno zakaži.
        context.appContainer.let { c -> c.appScope.launch { c.scheduler.reschedule() } }
        onPauseOrDispose { }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        if (!granted) PermissionHelper.openNotificationSettings(context)
    }

    val exactOk = remember(refresh) { PermissionHelper.canScheduleExactAlarms(context) }
    val notifOk = remember(refresh) { PermissionHelper.hasNotificationPermission(context) }
    val batteryOk = remember(refresh) { PermissionHelper.isIgnoringBatteryOptimizations(context) }
    val fullScreenOk = remember(refresh) { PermissionHelper.canUseFullScreenIntent(context) }
    val manufacturer = remember { Manufacturer.current() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.check_permissions)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.permissions_intro), style = MaterialTheme.typography.bodyMedium)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionStep(
                    number = 1,
                    title = stringResource(R.string.perm_exact_title),
                    description = stringResource(R.string.perm_exact_desc),
                    granted = exactOk,
                    onFix = { PermissionHelper.openExactAlarmSettings(context) },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionStep(
                    number = 2,
                    title = stringResource(R.string.perm_notif_title),
                    description = stringResource(R.string.perm_notif_desc),
                    granted = notifOk,
                    onFix = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
            PermissionStep(
                number = 3,
                title = stringResource(R.string.perm_battery_title),
                description = stringResource(R.string.perm_battery_desc),
                granted = batteryOk,
                onFix = { PermissionHelper.openBatteryOptimizationSettings(context) },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PermissionStep(
                    number = 4,
                    title = stringResource(R.string.perm_fullscreen_title),
                    description = stringResource(R.string.perm_fullscreen_desc),
                    granted = fullScreenOk,
                    onFix = { PermissionHelper.openFullScreenIntentSettings(context) },
                )
            }

            val oemText = manufacturer.instructionsRes
            if (oemText != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.perm_oem_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(oemText), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { PermissionHelper.openAppDetails(context) }) {
                            Text(stringResource(R.string.open_app_settings))
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.perm_oem_other), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PermissionStep(
    number: Int,
    title: String,
    description: String,
    granted: Boolean,
    onFix: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$number. $title", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(if (granted) R.string.perm_ok else R.string.perm_missing),
                    color = if (granted) OkGreen else MissingRed,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (!granted) {
                Button(onClick = onFix) { Text(stringResource(R.string.perm_fix)) }
            }
        }
    }
}
