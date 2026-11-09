package hr.abalarm.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hr.abalarm.app.alarm.AlarmNotifications
import hr.abalarm.app.data.DateStyle
import hr.abalarm.app.data.TimeFormat
import hr.abalarm.app.permissions.PermissionHelper
import hr.abalarm.app.ui.AppLocale
import hr.abalarm.app.ui.LocalDateStyle
import hr.abalarm.app.ui.LocalTimeFormat
import hr.abalarm.app.ui.main.MainScreen
import hr.abalarm.app.ui.permissions.PermissionsScreen
import hr.abalarm.app.ui.settings.SettingsScreen
import hr.abalarm.app.ui.theme.AbAlarmTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    /** Odabrani formati vremena i datuma iz postavki – dijele se cijelom UI-ju. */
    private val displayFormatFlow by lazy {
        appContainer.repository.state
            .map { it.settings.timeFormat to it.settings.dateStyle }
            .distinctUntilChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nazivi kanala obavijesti na jeziku aplikacije (aktivnost je već prevedena).
        AlarmNotifications.createChannels(this)
        enableEdgeToEdge()
        setContent {
            val formats by displayFormatFlow.collectAsStateWithLifecycle(
                initialValue = TimeFormat.AUTO to DateStyle.AUTO,
            )
            AbAlarmTheme {
                CompositionLocalProvider(
                    LocalTimeFormat provides formats.first,
                    LocalDateStyle provides formats.second,
                ) {
                    StartupChecks()
                    AppNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Sigurnosna mreža: pri svakom otvaranju aplikacije provjeri da je sljedeći alarm zakazan.
        lifecycleScope.launch {
            // Na Androidu 13+ jezik se može promijeniti i u postavkama sustava → uskladi kopiju.
            val language = AppLocale.current()
            if (appContainer.repository.current().settings.appLanguage != language) {
                appContainer.repository.setAppLanguage(language)
            }
            appContainer.scheduler.reschedule()
        }
    }
}

private object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
}

@Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(onOpenSettings = { nav.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onBack = { nav.popBackStack() })
        }
    }
}

/**
 * Pri pokretanju: (1) jednom zatraži dopuštenje za obavijesti (Android 13+),
 * (2) ako nema dopuštenja za točne alarme, ponudi odlazak u postavke.
 */
@Composable
private fun StartupChecks() {
    val context = LocalContext.current
    val container = context.appContainer
    var showExactDialog by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotificationPermission(context) &&
            !container.repository.current().settings.notificationPermissionAsked
        ) {
            container.repository.setNotificationPermissionAsked()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LifecycleResumeEffect(Unit) {
        showExactDialog = !PermissionHelper.canScheduleExactAlarms(context)
        onPauseOrDispose { }
    }

    if (showExactDialog) {
        AlertDialog(
            onDismissRequest = { showExactDialog = false },
            title = { Text(stringResource(R.string.exact_dialog_title)) },
            text = { Text(stringResource(R.string.exact_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showExactDialog = false
                    PermissionHelper.openExactAlarmSettings(context)
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showExactDialog = false }) { Text(stringResource(R.string.later)) }
            },
        )
    }
}
