package hr.abalarm.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import hr.abalarm.app.AbAlarmApp
import hr.abalarm.app.alarm.AlarmScheduler
import hr.abalarm.app.data.AlarmRepository
import hr.abalarm.app.data.Anchor
import hr.abalarm.app.data.AppLanguage
import hr.abalarm.app.data.AppSettings
import hr.abalarm.app.data.DateStyle
import hr.abalarm.app.data.TimeFormat
import hr.abalarm.app.data.Week
import hr.abalarm.app.domain.CycleCalculator
import hr.abalarm.app.ui.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class SettingsUiState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val currentWeek: Week? = null,
)

class SettingsViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    /** Aplikacijski scope: spremanje se dovrši i ako korisnik odmah izađe s ekrana. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = repository.state
        .map { state ->
            SettingsUiState(
                loaded = true,
                settings = state.settings,
                currentWeek = state.settings.anchor?.let { CycleCalculator.weekForDate(LocalDate.now(), it) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** "Sada je tjedan X": današnji datum postaje sidro. */
    fun setCurrentWeek(week: Week) = setAnchor(Anchor(LocalDate.now(), week))

    /** Promjena sidra poništava zakazani alarm i sve računa ponovno. */
    fun setAnchor(anchor: Anchor) {
        appScope.launch {
            repository.setAnchor(anchor)
            scheduler.reschedule()
        }
    }

    fun setRingtone(uri: String?) {
        appScope.launch { repository.setRingtone(uri) }
    }

    fun setVibrate(enabled: Boolean) {
        appScope.launch { repository.setVibrate(enabled) }
    }

    /** Ponovno zakazivanje: trajanje se sprema u zakazani alarm (tekst gumba u obavijesti). */
    fun setSnoozeMinutes(minutes: Int) {
        appScope.launch {
            repository.setSnoozeMinutes(minutes)
            scheduler.reschedule()
        }
    }

    /** Ponovno zakazivanje: ograničenje se sprema u zakazani alarm. */
    fun setSnoozeRepeat(times: Int) {
        appScope.launch {
            repository.setSnoozeRepeat(times)
            scheduler.reschedule()
        }
    }

    fun setDateStyle(style: DateStyle) {
        appScope.launch { repository.setDateStyle(style) }
    }

    /** Sprema jezik, ponovno zakazuje alarm (tekst obavijesti) i primjenjuje jezik na UI. */
    fun setAppLanguage(language: AppLanguage) {
        appScope.launch {
            repository.setAppLanguage(language)
            scheduler.reschedule()
            withContext(Dispatchers.Main) { AppLocale.apply(language) }
        }
    }

    /** Mijenja i tekst u obavijesti sljedećeg alarma, pa se alarm ponovno zakazuje. */
    fun setTimeFormat(format: TimeFormat) {
        appScope.launch {
            repository.setTimeFormat(format)
            scheduler.reschedule()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as AbAlarmApp).container
                SettingsViewModel(c.repository, c.scheduler, c.appScope)
            }
        }
    }
}
