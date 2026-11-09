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
) {
    val epochMillis: Long get() = dateTime.toInstant().toEpochMilli()
    val date: LocalDate get() = dateTime.toLocalDate()
    val day: DayOfWeek get() = dateTime.dayOfWeek
    val time: LocalTime get() = dateTime.toLocalTime()
}

/**
 * Pronalazi SAMO sljedeći alarm (lančano zakazivanje). Čista funkcija – bez Android ovisnosti,
 * pa se može testirati unit testovima.
 */
object NextAlarmCalculator {

    /**
     * Dva puna ciklusa (28 dana) + današnji dan: i kad postoji samo jedan alarm u ciklusu
     * a "Pauziraj sutra" ga preskoči, sljedeći se i dalje pronađe.
     */
    private const val SEARCH_DAYS = 29L

    /**
     * @param now trenutni trenutak (alarm mora biti strogo nakon njega)
     * @param skipAtMillis alarm koji točno pada u taj trenutak se preskače ("Pauziraj sutra")
     */
    fun next(
        now: ZonedDateTime,
        anchor: Anchor,
        schedule: Schedule,
        skipAtMillis: Long? = null,
    ): NextAlarm? {
        val zone = now.zone
        val today = now.toLocalDate()
        for (i in 0..SEARCH_DAYS) {
            val date = today.plusDays(i)
            val week = CycleCalculator.weekForDate(date, anchor)
            val alarm = schedule.alarmFor(week, date.dayOfWeek)
            if (!alarm.enabled) continue
            // ZonedDateTime.of sam rješava ljetno/zimsko vrijeme (rupe se pomiču unaprijed).
            val candidate = ZonedDateTime.of(date, alarm.time, zone)
            if (!candidate.isAfter(now)) continue
            if (skipAtMillis != null && candidate.toInstant().toEpochMilli() == skipAtMillis) continue
            return NextAlarm(candidate, week)
        }
        return null
    }
}
