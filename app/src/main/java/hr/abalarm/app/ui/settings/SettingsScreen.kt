package hr.abalarm.app.ui.settings

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hr.abalarm.app.R
import hr.abalarm.app.data.Anchor
import hr.abalarm.app.data.AppLanguage
import hr.abalarm.app.data.AppSettings
import hr.abalarm.app.data.DateStyle
import hr.abalarm.app.data.TimeFormat
import hr.abalarm.app.data.Week
import hr.abalarm.app.ui.dateFormatter
import hr.abalarm.app.ui.theme.color
import hr.abalarm.app.ui.weekName
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    val ringtonePickerTitle = stringResource(R.string.ringtone)
    var showAnchorEditor by rememberSaveable { mutableStateOf(false) }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val picked = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        // Odabir "Zadano" spremamo kao null → uvijek prati trenutni zadani ton sustava.
        val value = picked?.takeIf { it != Settings.System.DEFAULT_ALARM_ALERT_URI }?.toString()
        viewModel.setRingtone(value)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        val settings = state.settings
        val dates = dateFormatter()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Sada je tjedan: [A] [B] ---
            Text(stringResource(R.string.now_is_week), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Week.entries.forEach { week ->
                    val isCurrent = state.currentWeek == week
                    Button(
                        onClick = { viewModel.setCurrentWeek(week) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCurrent) week.color() else week.color().copy(alpha = 0.35f),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.weight(1f).height(72.dp),
                    ) { Text(weekName(week), fontSize = 22.sp) }
                }
            }
            Text(
                text = settings.anchor?.let {
                    stringResource(R.string.anchor_info, it.date.format(dates), weekName(it.week))
                } ?: stringResource(R.string.anchor_not_set),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- Definiraj tjedan (sekundarno, skriveno): bilo koji datum + A/B ---
            TextButton(onClick = { showAnchorEditor = !showAnchorEditor }) {
                Text(stringResource(R.string.define_week))
            }
            if (showAnchorEditor) {
                AnchorEditor(
                    initial = settings.anchor ?: Anchor(LocalDate.now(), Week.A),
                    onSave = {
                        viewModel.setAnchor(it)
                        showAnchorEditor = false
                    },
                )
            }

            HorizontalDivider()

            // --- Ton alarma ---
            SettingRow(
                title = stringResource(R.string.ringtone),
                subtitle = ringtoneTitle(context, settings.ringtoneUri),
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ringtonePickerTitle)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
                        .putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            settings.ringtoneUri?.toUri() ?: Settings.System.DEFAULT_ALARM_ALERT_URI,
                        )
                    ringtoneLauncher.launch(intent)
                },
            )

            // --- Jezik aplikacije, neovisno o jeziku sustava ---
            DropdownSetting(
                title = stringResource(R.string.app_language),
                options = listOf(
                    AppLanguage.SYSTEM to stringResource(R.string.app_language_system),
                    AppLanguage.HR to stringResource(R.string.language_name_hr),
                    AppLanguage.EN to stringResource(R.string.language_name_en),
                ),
                selected = settings.appLanguage,
                onSelect = viewModel::setAppLanguage,
            )

            // --- Format vremena (12/24 sata), neovisno o jeziku ---
            DropdownSetting(
                title = stringResource(R.string.time_format),
                options = listOf(
                    TimeFormat.AUTO to stringResource(R.string.time_format_auto),
                    TimeFormat.H24 to stringResource(R.string.time_format_24h),
                    TimeFormat.H12 to stringResource(R.string.time_format_12h),
                ),
                selected = settings.timeFormat,
                onSelect = viewModel::setTimeFormat,
            )

            // --- Format datuma, neovisno o jeziku ---
            DropdownSetting(
                title = stringResource(R.string.date_format),
                options = listOf(
                    DateStyle.AUTO to stringResource(R.string.date_format_auto),
                    DateStyle.HR to stringResource(R.string.date_format_hr),
                    DateStyle.EN to stringResource(R.string.date_format_en),
                ),
                selected = settings.dateStyle,
                onSelect = viewModel::setDateStyle,
            )

            // --- Trajanje odgode ---
            DropdownSetting(
                title = stringResource(R.string.snooze_duration),
                options = AppSettings.SNOOZE_OPTIONS.map { it to stringResource(R.string.snooze_option, it) },
                selected = settings.snoozeMinutes,
                onSelect = viewModel::setSnoozeMinutes,
            )

            // --- Ponavljanje odgode (najveći broj odgoda) ---
            DropdownSetting(
                title = stringResource(R.string.snooze_repeat),
                options = AppSettings.SNOOZE_REPEAT_OPTIONS.map { times ->
                    times to if (times == AppSettings.SNOOZE_UNLIMITED) {
                        stringResource(R.string.snooze_repeat_unlimited)
                    } else {
                        pluralStringResource(R.plurals.snooze_repeat_times, times, times)
                    }
                },
                selected = settings.snoozeRepeat,
                onSelect = viewModel::setSnoozeRepeat,
            )

            // --- Vibriranje ---
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.vibrate), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = settings.vibrate, onCheckedChange = viewModel::setVibrate)
            }

            HorizontalDivider()

            SettingRow(title = stringResource(R.string.check_permissions), onClick = onOpenPermissions)
            SettingRow(title = stringResource(R.string.about), onClick = { showAbout = true })
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.ok)) } },
            title = { Text(stringResource(R.string.app_name)) },
            text = { Text(stringResource(R.string.about_text, appVersion(context))) },
        )
    }
}

@Composable
private fun AnchorEditor(initial: Anchor, onSave: (Anchor) -> Unit) {
    val context = LocalContext.current
    var date by rememberSaveable { mutableStateOf(initial.date.toString()) }
    var week by rememberSaveable { mutableStateOf(initial.week) }
    val localDate = LocalDate.parse(date)
    val dates = dateFormatter()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.anchor_editor_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            DatePickerDialog(
                context,
                { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() },
                localDate.year, localDate.monthValue - 1, localDate.dayOfMonth,
            ).show()
        }) {
            Text(stringResource(R.string.anchor_date_button, localDate.format(dates)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Week.entries.forEach { w ->
                OutlinedButton(
                    onClick = { week = w },
                    border = BorderStroke(if (w == week) 3.dp else 1.dp, w.color()),
                ) { Text(weekName(w), color = w.color()) }
            }
        }
        Button(onClick = { onSave(Anchor(localDate, week)) }) {
            Text(stringResource(R.string.save))
        }
    }
}

/** Naslov + padajući izbornik s trenutnim odabirom za jednu postavku. */
@Composable
private fun <T> DropdownSetting(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Box {
            Surface(
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { fieldWidthPx = it.width }
                    .clickable(role = Role.Button) { expanded = true },
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selectedLabel, modifier = Modifier.weight(1f))
                    Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null)
                }
            }
            // Popis je širok kao polje iznad njega.
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(with(density) { fieldWidthPx.toDp() }),
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            expanded = false
                            if (value != selected) onSelect(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun ringtoneTitle(context: Context, uri: String?): String {
    if (uri == null) return context.getString(R.string.ringtone_default)
    return try {
        RingtoneManager.getRingtone(context, uri.toUri())?.getTitle(context)
            ?: context.getString(R.string.ringtone_default)
    } catch (_: Exception) {
        context.getString(R.string.ringtone_default)
    }
}

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (_: Exception) {
    "?"
}
