package hr.abalarm.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

val Context.abAlarmDataStore: DataStore<Preferences> by preferencesDataStore(name = "ab_alarm")

/**
 * Jedini izvor istine za raspored i postavke (DataStore Preferences).
 */
class AlarmRepository(private val dataStore: DataStore<Preferences>) {

    val state: Flow<AppState> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::toState)

    suspend fun current(): AppState = state.first()

    suspend fun setAlarm(week: Week, day: DayOfWeek, time: LocalTime) {
        dataStore.edit {
            it[timeKey(week, day)] = time.toString() // ISO "HH:mm"
            it[enabledKey(week, day)] = true
        }
    }

    suspend fun disableAlarm(week: Week, day: DayOfWeek) {
        dataStore.edit { it[enabledKey(week, day)] = false }
    }

    /** Novo sidro; poništava i eventualno već izračunati "preskoči" trenutak. */
    suspend fun setAnchor(anchor: Anchor) {
        dataStore.edit {
            it[KEY_ANCHOR_DATE] = anchor.date.toString()
            it[KEY_ANCHOR_WEEK] = anchor.week.name
            it.remove(KEY_SKIP_AT)
        }
    }

    suspend fun setRingtone(uri: String?) {
        dataStore.edit { if (uri == null) it.remove(KEY_RINGTONE) else it[KEY_RINGTONE] = uri }
    }

    suspend fun setVibrate(enabled: Boolean) {
        dataStore.edit { it[KEY_VIBRATE] = enabled }
    }

    suspend fun setTimeFormat(format: TimeFormat) {
        dataStore.edit { it[KEY_TIME_FORMAT] = format.name }
    }

    suspend fun setDateStyle(style: DateStyle) {
        dataStore.edit { it[KEY_DATE_STYLE] = style.name }
    }

    /** Kopija odabranog jezika – za tekstove obavijesti na Androidu < 13 (vidi AppLocale). */
    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { it[KEY_APP_LANGUAGE] = language.name }
    }

    suspend fun setSnoozeMinutes(minutes: Int) {
        dataStore.edit { it[KEY_SNOOZE_MINUTES] = minutes }
    }

    suspend fun setSnoozeRepeat(times: Int) {
        dataStore.edit { it[KEY_SNOOZE_REPEAT] = times }
    }

    suspend fun setPauseNext(enabled: Boolean) {
        dataStore.edit {
            it[KEY_PAUSE_NEXT] = enabled
            it.remove(KEY_SKIP_AT)
        }
    }

    suspend fun setSkipAt(millis: Long) {
        dataStore.edit { it[KEY_SKIP_AT] = millis }
    }

    /** Preskočeni alarm je prošao – resetiraj zastavicu "Pauziraj sutra". */
    suspend fun finishPause() {
        dataStore.edit {
            it[KEY_PAUSE_NEXT] = false
            it.remove(KEY_SKIP_AT)
        }
    }

    suspend fun setNextAlarm(millis: Long?) {
        dataStore.edit { if (millis == null) it.remove(KEY_NEXT_ALARM) else it[KEY_NEXT_ALARM] = millis }
    }

    suspend fun setNotificationPermissionAsked() {
        dataStore.edit { it[KEY_NOTIF_ASKED] = true }
    }

    private fun toState(p: Preferences): AppState {
        fun week(w: Week) = DayOfWeek.entries.associateWith { day ->
            DayAlarm(
                time = p[timeKey(w, day)]?.let(::parseTime) ?: DayAlarm.DEFAULT_TIME,
                enabled = p[enabledKey(w, day)] ?: false,
            )
        }

        val anchorDate = p[KEY_ANCHOR_DATE]?.let(::parseDate)
        val anchorWeek = p[KEY_ANCHOR_WEEK]?.let { name -> Week.entries.firstOrNull { it.name == name } }
        val anchor = if (anchorDate != null && anchorWeek != null) Anchor(anchorDate, anchorWeek) else null

        return AppState(
            schedule = Schedule(weekA = week(Week.A), weekB = week(Week.B)),
            settings = AppSettings(
                anchor = anchor,
                ringtoneUri = p[KEY_RINGTONE],
                vibrate = p[KEY_VIBRATE] ?: true,
                timeFormat = p[KEY_TIME_FORMAT]
                    ?.let { name -> TimeFormat.entries.firstOrNull { it.name == name } }
                    ?: TimeFormat.AUTO,
                dateStyle = p[KEY_DATE_STYLE]
                    ?.let { name -> DateStyle.entries.firstOrNull { it.name == name } }
                    ?: DateStyle.AUTO,
                snoozeMinutes = p[KEY_SNOOZE_MINUTES]
                    ?.takeIf { it in AppSettings.SNOOZE_OPTIONS }
                    ?: AppSettings.DEFAULT_SNOOZE_MINUTES,
                snoozeRepeat = p[KEY_SNOOZE_REPEAT]
                    ?.takeIf { it in AppSettings.SNOOZE_REPEAT_OPTIONS }
                    ?: AppSettings.SNOOZE_UNLIMITED,
                appLanguage = p[KEY_APP_LANGUAGE]
                    ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
                    ?: AppLanguage.SYSTEM,
                pauseNext = p[KEY_PAUSE_NEXT] ?: false,
                skipAtMillis = p[KEY_SKIP_AT],
                nextAlarmMillis = p[KEY_NEXT_ALARM],
                notificationPermissionAsked = p[KEY_NOTIF_ASKED] ?: false,
            ),
        )
    }

    private fun parseTime(s: String): LocalTime? =
        try { LocalTime.parse(s) } catch (_: DateTimeParseException) { null }

    private fun parseDate(s: String): LocalDate? =
        try { LocalDate.parse(s) } catch (_: DateTimeParseException) { null }

    private companion object {
        val KEY_ANCHOR_DATE = stringPreferencesKey("anchor_date")
        val KEY_ANCHOR_WEEK = stringPreferencesKey("anchor_week")
        val KEY_RINGTONE = stringPreferencesKey("ringtone_uri")
        val KEY_VIBRATE = booleanPreferencesKey("vibrate")
        val KEY_TIME_FORMAT = stringPreferencesKey("time_format")
        val KEY_DATE_STYLE = stringPreferencesKey("date_style")
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val KEY_SNOOZE_REPEAT = intPreferencesKey("snooze_repeat")
        val KEY_PAUSE_NEXT = booleanPreferencesKey("pause_next")
        val KEY_SKIP_AT = longPreferencesKey("skip_at_millis")
        val KEY_NEXT_ALARM = longPreferencesKey("next_alarm_millis")
        val KEY_NOTIF_ASKED = booleanPreferencesKey("notif_permission_asked")

        fun timeKey(week: Week, day: DayOfWeek) = stringPreferencesKey("time_${week.name}_${day.name}")
        fun enabledKey(week: Week, day: DayOfWeek) = booleanPreferencesKey("enabled_${week.name}_${day.name}")
    }
}
