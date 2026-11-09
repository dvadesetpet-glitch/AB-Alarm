package hr.abalarm.app.domain

import hr.abalarm.app.data.Anchor
import hr.abalarm.app.data.DayAlarm
import hr.abalarm.app.data.Schedule
import hr.abalarm.app.data.Week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NextAlarmCalculatorTest {

    private val zone = ZoneId.of("Europe/Zagreb")

    // Ponedjeljak 9.11.2026. je Tjedan A.
    private val anchor = Anchor(LocalDate.parse("2026-11-09"), Week.A)

    private val weekdays = DayOfWeek.entries.filter { it.value <= 5 }

    /** Tjedan A: radni dani 06:30 (jutarnja smjena); Tjedan B: radni dani 10:00 (popodnevna). */
    private val schedule = Schedule(
        weekA = Schedule.emptyWeek() + weekdays.associateWith { DayAlarm(LocalTime.of(6, 30), true) },
        weekB = Schedule.emptyWeek() + weekdays.associateWith { DayAlarm(LocalTime.of(10, 0), true) },
    )

    private fun at(s: String): ZonedDateTime = ZonedDateTime.of(LocalDateTime.parse(s), zone)

    @Test fun istiDanPrijeAlarma() {
        val next = NextAlarmCalculator.next(at("2026-11-09T05:00"), anchor, schedule)!!
        assertEquals(at("2026-11-09T06:30"), next.dateTime)
        assertEquals(Week.A, next.week)
    }

    @Test fun istiDanNakonAlarmaIdeNaSutra() {
        val next = NextAlarmCalculator.next(at("2026-11-09T06:30"), anchor, schedule)!!
        assertEquals(at("2026-11-10T06:30"), next.dateTime)
    }

    @Test fun petakNavecerPreskaceVikendIPrelaziUTjedanB() {
        val next = NextAlarmCalculator.next(at("2026-11-13T20:00"), anchor, schedule)!!
        assertEquals(at("2026-11-16T10:00"), next.dateTime)
        assertEquals(Week.B, next.week)
    }

    @Test fun vikendJeZasebnoKonfiguriran() {
        val withSaturdayB = schedule.copy(weekB = schedule.weekB + (DayOfWeek.SATURDAY to DayAlarm(LocalTime.of(9, 0), true)))
        // Subota 14.11. je Tjedan A → nema alarma; subota 21.11. je Tjedan B → 09:00
        val next = NextAlarmCalculator.next(at("2026-11-20T12:00"), anchor, withSaturdayB)!!
        assertEquals(at("2026-11-21T09:00"), next.dateTime)
    }

    @Test fun pauzaPreskaceSamoJedanAlarm() {
        val now = at("2026-11-09T05:00")
        val first = NextAlarmCalculator.next(now, anchor, schedule)!!
        val afterSkip = NextAlarmCalculator.next(now, anchor, schedule, skipAtMillis = first.epochMillis)!!
        assertEquals(at("2026-11-10T06:30"), afterSkip.dateTime)
    }

    @Test fun nemaAlarma() {
        assertNull(NextAlarmCalculator.next(at("2026-11-09T05:00"), anchor, Schedule()))
    }

    @Test fun samoJedanAlarmUCiklusuNalaziSeIZa13Dana() {
        // Samo ponedjeljak Tjedna A; u utorak tjedna A sljedeći je ponedjeljak za 13 dana.
        val only = Schedule(weekA = Schedule.emptyWeek() + (DayOfWeek.MONDAY to DayAlarm(LocalTime.of(7, 0), true)))
        val next = NextAlarmCalculator.next(at("2026-11-10T08:00"), anchor, only)!!
        assertEquals(at("2026-11-23T07:00"), next.dateTime)
    }

    @Test fun ljetnoVrijemeRupaSePomiceUnaprijed() {
        // 29.3.2026. u 02:00 sat skače na 03:00 (Europe/Zagreb). Alarm 02:30 zvoni u 03:30.
        val sundayAnchor = Anchor(LocalDate.parse("2026-03-29"), Week.A)
        val s = Schedule(weekA = Schedule.emptyWeek() + (DayOfWeek.SUNDAY to DayAlarm(LocalTime.of(2, 30), true)))
        val next = NextAlarmCalculator.next(at("2026-03-29T00:00"), sundayAnchor, s)!!
        assertEquals(LocalTime.of(3, 30), next.time)
    }
}
