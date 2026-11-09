package hr.abalarm.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Tjedan u dvotjednom ciklusu. */
enum class Week {
    A, B;

    fun other(): Week = if (this == A) B else A
}

/** Format prikaza vremena, neovisno o jeziku. */
enum class TimeFormat {
    /** Prema jeziku aplikacije (hr: 07:00, en: 7:00 AM). */
    AUTO,
    H24,
    H12,
}

/** Format prikaza datuma, neovisno o jeziku. */
enum class DateStyle {
    /** Prema jeziku aplikacije. */
    AUTO,
    /** 9.11.2026. */
    HR,
    /** Nov 9, 2026 */
    EN,
}

/** Jezik aplikacije, neovisno o jeziku sustava. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    HR("hr"),
    EN("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag && it != SYSTEM } ?: SYSTEM
    }
}

/**
 * Sidro ciklusa: bilo koji datum (ne nužno ponedjeljak) i oznaka kojem tjednu taj datum pripada.
 */
data class Anchor(val date: LocalDate, val week: Week)

/**
 * Alarm za jedan dan u tjednu. Vrijeme se pamti i kad je alarm isključen,
 * kako bi birač vremena sljedeći put krenuo od zadnje postavljenog vremena.
 */
data class DayAlarm(
    val time: LocalTime = DEFAULT_TIME,
    val enabled: Boolean = false,
) {
    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(7, 0)
    }
}

/** Raspored za oba tjedna: 2 × 7 dana, potpuno neovisno. */
data class Schedule(
    val weekA: Map<DayOfWeek, DayAlarm> = emptyWeek(),
    val weekB: Map<DayOfWeek, DayAlarm> = emptyWeek(),
) {
    fun alarmFor(week: Week, day: DayOfWeek): DayAlarm =
        (if (week == Week.A) weekA else weekB)[day] ?: DayAlarm()

    fun daysOf(week: Week): Map<DayOfWeek, DayAlarm> = if (week == Week.A) weekA else weekB

    companion object {
        fun emptyWeek(): Map<DayOfWeek, DayAlarm> = DayOfWeek.entries.associateWith { DayAlarm() }
    }
}

/**
 * Globalne postavke. Ton zvona i vibriranje vrijede za sve alarme
 * (u postavkama postoji jedan birač tona i jedan prekidač vibriranja).
 */
data class AppSettings(
    val anchor: Anchor? = null,
    /** null = zadani ton alarma sustava. */
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val timeFormat: TimeFormat = TimeFormat.AUTO,
    val dateStyle: DateStyle = DateStyle.AUTO,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    /** Trajanje odgode u minutama (jedna od [SNOOZE_OPTIONS]). */
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    /** Najveći broj odgoda jednog alarma; [SNOOZE_UNLIMITED] = bez ograničenja. */
    val snoozeRepeat: Int = SNOOZE_UNLIMITED,
    /** "Pauziraj sutra" – preskoči sljedeći alarm. */
    val pauseNext: Boolean = false,
    /** Trenutak (epoch ms) alarma koji se preskače; postavlja ga scheduler. */
    val skipAtMillis: Long? = null,
    /** Trenutak (epoch ms) sljedećeg zakazanog alarma – samo za prikaz. */
    val nextAlarmMillis: Long? = null,
    val notificationPermissionAsked: Boolean = false,
) {
    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 5
        val SNOOZE_OPTIONS = listOf(5, 10, 15, 20, 30)
        const val SNOOZE_UNLIMITED = 0
        val SNOOZE_REPEAT_OPTIONS = listOf(1, 3, 5, 10, SNOOZE_UNLIMITED)
    }
}

data class AppState(
    val schedule: Schedule = Schedule(),
    val settings: AppSettings = AppSettings(),
)
