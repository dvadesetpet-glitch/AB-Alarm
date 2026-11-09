package hr.abalarm.app.ui.main

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hr.abalarm.app.R
import hr.abalarm.app.data.DayAlarm
import hr.abalarm.app.data.ExtraAlarm
import hr.abalarm.app.data.Week
import hr.abalarm.app.ui.dayName
import hr.abalarm.app.ui.shortDateFormatter
import hr.abalarm.app.ui.theme.color
import hr.abalarm.app.ui.theme.lightColor
import hr.abalarm.app.ui.timeFormatter
import hr.abalarm.app.ui.use24HourClock
import hr.abalarm.app.ui.weekName
import java.time.DayOfWeek
import java.time.LocalTime

/** Dodatni alarm koji se upravo dodaje (extra == null) ili uređuje. */
private data class ExtraEdit(val week: Week, val day: DayOfWeek, val extra: ExtraAlarm?)

@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val use24h = use24HourClock()

    // Rasklopljeni dani, ključ "A_MONDAY" (preživljava rotaciju ekrana).
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var editing by remember { mutableStateOf<ExtraEdit?>(null) }

    // Birač vremena s kontekstom aktivnosti (radi i kad se poziva iz dijaloga).
    val pickTime: (LocalTime, (LocalTime) -> Unit) -> Unit = { initial, onPicked ->
        TimePickerDialog(context, { _, h, m -> onPicked(LocalTime.of(h, m)) }, initial.hour, initial.minute, use24h).show()
    }

    Scaffold(
        topBar = {
            WeekTabs(
                selected = state.selectedTab,
                currentWeek = state.currentWeek,
                onSelect = viewModel::selectTab,
                onOpenSettings = onOpenSettings,
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        val week = state.selectedTab
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!state.anchorSet) {
                item { WhichWeekCard(onChoose = viewModel::setCurrentWeek) }
            }
            items(DayOfWeek.entries, key = { it.name }) { day ->
                val alarm = state.schedule.alarmFor(week, day)
                val key = "${week.name}_${day.name}"
                val isExpanded = key in expanded
                DayRow(
                    day = day,
                    alarm = alarm,
                    extras = state.schedule.extrasFor(week, day),
                    week = week,
                    isToday = state.currentWeek == week && state.today.dayOfWeek == day,
                    expanded = isExpanded,
                    onToggleExpanded = {
                        expanded = if (isExpanded) expanded - key else expanded + key
                    },
                    onClick = { pickTime(alarm.time) { viewModel.setTime(week, day, it) } },
                    onLongClick = {
                        if (alarm.enabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.clearAlarm(week, day)
                        }
                    },
                    onAddExtra = { editing = ExtraEdit(week, day, null) },
                    onEditExtra = { editing = ExtraEdit(week, day, it) },
                    onRemoveExtra = { viewModel.removeExtra(week, day, it.id) },
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                PauseRow(checked = state.pauseNext, onChange = viewModel::setPauseNext)
                NextAlarmText(state)
                Text(
                    text = stringResource(R.string.hint_long_press),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    editing?.let { edit ->
        ExtraAlarmDialog(
            isNew = edit.extra == null,
            // Novi dodatni alarm: u jutarnjem tjednu popodne, u popodnevnom tjednu ujutro.
            initialTime = edit.extra?.time ?: if (edit.week == Week.A) LocalTime.of(16, 0) else LocalTime.of(9, 0),
            initialLabel = edit.extra?.label.orEmpty(),
            accent = edit.week.color(),
            pickTime = pickTime,
            onSave = { time, label ->
                if (edit.extra == null) viewModel.addExtra(edit.week, edit.day, time, label)
                else viewModel.updateExtra(edit.week, edit.day, edit.extra.id, time, label)
                editing = null
            },
            onDelete = edit.extra?.let { extra ->
                {
                    viewModel.removeExtra(edit.week, edit.day, extra.id)
                    editing = null
                }
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun WeekTabs(
    selected: Week,
    currentWeek: Week?,
    onSelect: (Week) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(shadowElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Week.entries.forEach { week ->
                val isSelected = week == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .background(
                            color = if (isSelected) week.color() else week.lightColor(),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable(role = Role.Tab) { onSelect(week) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = weekName(week),
                        color = if (isSelected) Color.White else week.color(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                    if (week == currentWeek) {
                        Text(
                            text = stringResource(R.string.this_week),
                            color = if (isSelected) Color.White else week.color(),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings_title),
                )
            }
        }
    }
}

/**
 * Jedan dan: glavni alarm (dodir = vrijeme, dugi pritisak = brisanje) i strelica desno
 * koja rasklapa dodatne alarme. Sklopljen dan prikazuje dodatne alarme sitnim slovima.
 */
@Composable
private fun DayRow(
    day: DayOfWeek,
    alarm: DayAlarm,
    extras: List<ExtraAlarm>,
    week: Week,
    isToday: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAddExtra: () -> Unit,
    onEditExtra: (ExtraAlarm) -> Unit,
    onRemoveExtra: (ExtraAlarm) -> Unit,
) {
    val weight = if (isToday) FontWeight.Bold else FontWeight.Normal
    val times = timeFormatter()
    val defaultLabel = stringResource(R.string.extra_default_label)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) week.lightColor() else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .heightIn(min = 60.dp)
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = dayName(day),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = weight,
                    )
                    if (!expanded && extras.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.extra_summary,
                                extras.joinToString(" · ") { "${it.time.format(times)} ${it.label.ifBlank { defaultLabel }}" },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = if (alarm.enabled) alarm.time.format(times) else stringResource(R.string.no_alarm),
                    fontSize = 26.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (alarm.enabled) week.color() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_down),
                    contentDescription = stringResource(if (expanded) R.string.extra_collapse else R.string.extra_expand),
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
        }

        if (expanded) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            extras.forEach { extra ->
                val label = extra.label.ifBlank { defaultLabel }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditExtra(extra) }
                        .padding(start = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = extra.time.format(times),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = week.color(),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = { onRemoveExtra(extra) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.extra_remove, label),
                        )
                    }
                }
            }
            if (extras.size < ExtraAlarm.MAX_PER_DAY) {
                TextButton(onClick = onAddExtra, modifier = Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.extra_add), color = week.color())
                }
            } else {
                Text(
                    text = stringResource(R.string.extra_max_reached),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 12.dp),
                )
            }
        }
    }
}

/** Dodavanje / uređivanje dodatnog alarma: vrijeme + naziv (s gotovim prijedlozima). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraAlarmDialog(
    isNew: Boolean,
    initialTime: LocalTime,
    initialLabel: String,
    accent: Color,
    pickTime: (LocalTime, (LocalTime) -> Unit) -> Unit,
    onSave: (LocalTime, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var time by rememberSaveable { mutableStateOf(initialTime.toString()) }
    var label by rememberSaveable { mutableStateOf(initialLabel) }
    val localTime = LocalTime.parse(time)
    val times = timeFormatter()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.extra_dialog_add else R.string.extra_dialog_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pickTime(localTime) { time = it.toString() } }) {
                    Text(stringResource(R.string.extra_time, localTime.format(times)), color = accent, fontSize = 18.sp)
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { if (it.length <= ExtraAlarm.MAX_LABEL_LENGTH) label = it.replace("\n", " ") },
                    label = { Text(stringResource(R.string.extra_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stringArrayResource(R.array.extra_suggestions).forEach { suggestion ->
                        SuggestionChip(onClick = { label = suggestion }, label = { Text(suggestion) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(localTime, label.trim()) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun WhichWeekCard(onChoose: (Week) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.which_week_question), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.which_week_explanation),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Week.entries.forEach { week ->
                    Button(
                        onClick = { onChoose(week) },
                        colors = ButtonDefaults.buttonColors(containerColor = week.color()),
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text(weekName(week), fontSize = 18.sp) }
                }
            }
        }
    }
}

@Composable
private fun PauseRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pause_tomorrow), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.pause_tomorrow_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun NextAlarmText(state: MainUiState) {
    val next = state.nextAlarm
    val times = timeFormatter()
    val shortDates = shortDateFormatter()
    val defaultLabel = stringResource(R.string.extra_default_label)
    val text = if (next == null) {
        stringResource(R.string.next_alarm_none)
    } else {
        stringResource(
            R.string.next_alarm_format,
            dayName(next.dateTime.dayOfWeek),
            next.dateTime.format(shortDates),
            next.dateTime.format(times),
            // Dodatni alarm prikazuje svoj naziv, glavni smjenu.
            next.extraLabel?.ifBlank { defaultLabel } ?: weekName(next.week),
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}
