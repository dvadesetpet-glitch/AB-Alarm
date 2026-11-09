package hr.abalarm.app.ui.main

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hr.abalarm.app.R
import hr.abalarm.app.data.DayAlarm
import hr.abalarm.app.data.Week
import hr.abalarm.app.ui.dayName
import hr.abalarm.app.ui.use24HourClock
import hr.abalarm.app.ui.shortDateFormatter
import hr.abalarm.app.ui.timeFormatter
import hr.abalarm.app.ui.theme.color
import hr.abalarm.app.ui.theme.lightColor
import hr.abalarm.app.ui.weekName
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val use24h = use24HourClock()

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
                DayRow(
                    day = day,
                    alarm = alarm,
                    week = week,
                    isToday = state.currentWeek == week && state.today.dayOfWeek == day,
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> viewModel.setTime(week, day, LocalTime.of(h, m)) },
                            alarm.time.hour,
                            alarm.time.minute,
                            use24h,
                        ).show()
                    },
                    onLongClick = {
                        if (alarm.enabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.clearAlarm(week, day)
                        }
                    },
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

@Composable
private fun DayRow(
    day: DayOfWeek,
    alarm: DayAlarm,
    week: Week,
    isToday: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val weight = if (isToday) FontWeight.Bold else FontWeight.Normal
    val times = timeFormatter()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) week.lightColor() else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dayName(day),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = weight,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (alarm.enabled) alarm.time.format(times) else stringResource(R.string.no_alarm),
                fontSize = 26.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                color = if (alarm.enabled) week.color() else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
    val text = if (next == null) {
        stringResource(R.string.next_alarm_none)
    } else {
        stringResource(
            R.string.next_alarm_format,
            dayName(next.dateTime.dayOfWeek),
            next.dateTime.format(shortDates),
            next.dateTime.format(times),
            weekName(next.week),
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
