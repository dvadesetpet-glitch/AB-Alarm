package hr.abalarm.app.domain

import hr.abalarm.app.data.Anchor
import hr.abalarm.app.data.Schedule
import hr.abalarm.app.data.Week
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Jedan konkretan budući alarm. */
data class NextAlarm(
    val dateTime: ZonedDateTime,
    val week: Week,
    /** Naziv dodatnog alarma (npr. "Trening"); null = glavni (školski) alarm. */
    val extraLabel: String? = null,
) {
    val isExtra: Boolean get() = extraLabel != null
    val epochMillis: Long get() = dateTime.toInstant().toEpochMilli()
    val date: LocalDate get() = dateTime.toLocalDate()
    val day: DayOfWeek get() = dateTime.dayOfWeek
    val time: LocalTime get() = dateTime.toLocalTime()
}

/**
 * Pronalazi SAMO sljedeći alarm (lančano zakazivanje), glavni ili dodatni.
 * Čista funkcija – bez Android ovisnosti, pa se može testirati unit testovima.
 */
object NextAlarmCalculator {

    /**
     * Dva puna ciklusa (28 dana) + današnji dan: i kad postoji samo jedan alarm u ciklusu
     * a "Pauziraj sutra" ga preskoči, sljedeći se i dalje pronađe.
     */
    private const val SEARCH_DAYS = 29L

    /**
     * @param now trenutni trenutak (alarm mora biti strogo nakon njega)
     * @param skipMainAtMillis GLAVNI alarm koji točno pada u taj trenutak se preskače
     *   ("Pauziraj sutra"); dodatni alarmi se nikad ne preskaču
     * @param includeExtras false = samo glavni alarmi (za određivanje koji se alarm pauzira)
     */
    fun next(
        now: ZonedDateTime,
        anchor: Anchor,
        schedule: Schedule,
        skipMainAtMillis: Long? = null,
        includeExtras: Boolean = true,
    ): NextAlarm? {
        val zone = now.zone
        val today = now.toLocalDate()
        for (i in 0..SEARCH_DAYS) {
            val date = today.plusDays(i)
            val week = CycleCalculator.weekForDate(date, anchor)
            val candidates = buildList {
                val main = schedule.alarmFor(week, date.dayOfWeek)
                if (main.enabled) {
                    // ZonedDateTime.of sam rješava ljetno/zimsko vrijeme (rupe se pomiču unaprijed).
                    val at = ZonedDateTime.of(date, main.time, zone)
                    if (skipMainAtMillis == null || at.toInstant().toEpochMilli() != skipMainAtMillis) {
                        add(NextAlarm(at, week))
                    }
                }
                if (includeExtras) {
                    schedule.extrasFor(week, date.dayOfWeek).forEach { extra ->
                        add(NextAlarm(ZonedDateTime.of(date, extra.time, zone), week, extra.label))
                    }
                }
            }
            // Najraniji alarm tog dana koji je još u budućnosti; kod istog vremena prednost ima glavni.
            val next = candidates
                .filter { it.dateTime.isAfter(now) }
                .minWithOrNull(compareBy<NextAlarm> { it.dateTime }.thenBy { it.isExtra })
            if (next != null) return next
        }
        return null
    }
}
