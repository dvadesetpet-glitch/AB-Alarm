package hr.abalarm.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import hr.abalarm.app.AbAlarmApp
import hr.abalarm.app.alarm.AlarmScheduler
import hr.abalarm.app.data.AlarmRepository
import hr.abalarm.app.data.Anchor
import hr.abalarm.app.data.Schedule
import hr.abalarm.app.data.Week
import hr.abalarm.app.domain.CycleCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.DayOfWeek
import java.time.LocalTime

data class NextAlarmUi(val dateTime: ZonedDateTime, val week: Week)

data class MainUiState(
    val loaded: Boolean = false,
    val anchorSet: Boolean = false,
    /** Tjedan kojem pripada današnji datum (null dok sidro nije postavljeno). */
    val currentWeek: Week? = null,
    val selectedTab: Week = Week.A,
    val today: LocalDate = LocalDate.now(),
    val schedule: Schedule = Schedule(),
    val pauseNext: Boolean = false,
    val nextAlarm: NextAlarmUi? = null,
)

class MainViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    /** Aplikacijski scope: spremanje se dovrši i ako korisnik odmah izađe s ekrana. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    /** null = korisnik još nije ručno birao tab → prikazuje se tekući tjedan. */
    private val manualTab = MutableStateFlow<Week?>(null)

    /** Osvježava "danas" (npr. ako je ekran otvoren preko ponoći). */
    private val todayFlow = flow {
        while (true) {
            emit(LocalDate.now())
            delay(30_000)
        }
    }.distinctUntilChanged()

    val uiState: StateFlow<MainUiState> =
        combine(repository.state, todayFlow, manualTab) { state, today, tab ->
            val anchor = state.settings.anchor
            val currentWeek = anchor?.let { CycleCalculator.weekForDate(today, it) }
            val now = System.currentTimeMillis()
            val pauseActive = state.settings.pauseNext &&
                (state.settings.skipAtMillis == null || state.settings.skipAtMillis > now)
            val next = state.settings.nextAlarmMillis
                ?.takeIf { it > now && anchor != null }
                ?.let {
                    val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                    NextAlarmUi(dt, CycleCalculator.weekForDate(dt.toLocalDate(), anchor!!))
                }
            MainUiState(
                loaded = true,
                anchorSet = anchor != null,
                currentWeek = currentWeek,
                selectedTab = tab ?: currentWeek ?: Week.A,
                today = today,
                schedule = state.schedule,
                pauseNext = pauseActive,
                nextAlarm = next,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun selectTab(week: Week) {
        manualTab.value = week
    }

    fun setTime(week: Week, day: DayOfWeek, time: LocalTime) = launchAndReschedule {
        repository.setAlarm(week, day, time)
    }

    fun clearAlarm(week: Week, day: DayOfWeek) = launchAndReschedule {
        repository.disableAlarm(week, day)
    }

    fun setPauseNext(enabled: Boolean) = launchAndReschedule {
        repository.setPauseNext(enabled)
    }

    /** "Sada je tjedan X" – današnji datum postaje sidro. */
    fun setCurrentWeek(week: Week) = launchAndReschedule {
        manualTab.value = null
        repository.setAnchor(Anchor(LocalDate.now(), week))
    }

    private fun launchAndReschedule(block: suspend () -> Unit) {
        appScope.launch {
            block()
            scheduler.reschedule()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AbAlarmApp).container
                MainViewModel(container.repository, container.scheduler, container.appScope)
            }
        }
    }
}
