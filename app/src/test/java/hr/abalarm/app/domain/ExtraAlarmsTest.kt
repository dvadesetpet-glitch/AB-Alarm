package hr.abalarm.app.domain

import hr.abalarm.app.data.Anchor
import hr.abalarm.app.data.DayAlarm
import hr.abalarm.app.data.ExtraAlarm
import hr.abalarm.app.data.ExtraAlarmCodec
import hr.abalarm.app.data.Schedule
import hr.abalarm.app.data.Week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ExtraAlarmsTest {

    private val zone = ZoneId.of("Europe/Zagreb")
    private fun at(s: String): ZonedDateTime = ZonedDateTime.of(LocalDateTime.parse(s), zone)

    // Ponedjeljak 9.11.2026. je Tjedan A (ujutro); 16.11. je Tjedan B (popodne).
    private val anchor = Anchor(LocalDate.parse("2026-11-09"), Week.A)

    private fun extra(id: String, time: String, label: String) = ExtraAlarm(id, LocalTime.parse(time), label)

    /** Ujutro: škola 06:30 + trening 17:00. Popodne: škola 11:00 + jutarnji engleski 08:30. */
    private val schedule = Schedule(
        weekA = Schedule.emptyWeek() + (DayOfWeek.MONDAY to DayAlarm(LocalTime.of(6, 30), true)),
        weekB = Schedule.emptyWeek() + (DayOfWeek.MONDAY to DayAlarm(LocalTime.of(11, 0), true)),
        extrasA = mapOf(DayOfWeek.MONDAY to listOf(extra("t", "17:00", "Trening"))),
        extrasB = mapOf(DayOfWeek.MONDAY to listOf(extra("e", "08:30", "Engleski"))),
    )

    @Test fun nakonGlavnogDolaziDodatniIstiDan() {
        val next = NextAlarmCalculator.next(at("2026-11-09T07:00"), anchor, schedule)!!
        assertEquals(at("2026-11-09T17:00"), next.dateTime)
        assertEquals("Trening", next.extraLabel)
        assertTrue(next.isExtra)
    }

    @Test fun jutarnjaAktivnostPrijeSkoleUPopodnevnomTjednu() {
        val next = NextAlarmCalculator.next(at("2026-11-16T06:00"), anchor, schedule)!!
        assertEquals(at("2026-11-16T08:30"), next.dateTime)
        assertEquals("Engleski", next.extraLabel)
        assertEquals(Week.B, next.week)
    }

    @Test fun dodatniAlarmiSuVezaniUzSmjenu() {
        // Trening postoji samo u tjednu Ujutro; nakon škole 16.11. (Popodne) nema ništa do 23.11.
        val next = NextAlarmCalculator.next(at("2026-11-16T12:00"), anchor, schedule)!!
        assertEquals(at("2026-11-23T06:30"), next.dateTime)
        assertFalse(next.isExtra)
    }

    @Test fun pauzaPreskaceSamoGlavniAlarm() {
        val now = at("2026-11-09T05:00")
        val main = NextAlarmCalculator.next(now, anchor, schedule, includeExtras = false)!!
        assertEquals(at("2026-11-09T06:30"), main.dateTime)
        val next = NextAlarmCalculator.next(now, anchor, schedule, skipMainAtMillis = main.epochMillis)!!
        // Školski alarm preskočen, trening isti dan i dalje zvoni.
        assertEquals(at("2026-11-09T17:00"), next.dateTime)
        assertEquals("Trening", next.extraLabel)
    }

    @Test fun istoVrijemePrednostImaGlavni() {
        val s = schedule.copy(extrasA = mapOf(DayOfWeek.MONDAY to listOf(extra("x", "06:30", "Isto"))))
        val next = NextAlarmCalculator.next(at("2026-11-09T05:00"), anchor, s)!!
        assertNull(next.extraLabel)
    }

    @Test fun samoDodatniBezGlavnog() {
        val s = Schedule(extrasA = mapOf(DayOfWeek.WEDNESDAY to listOf(extra("p", "18:00", "Ples"))))
        val next = NextAlarmCalculator.next(at("2026-11-09T05:00"), anchor, s)!!
        assertEquals(at("2026-11-11T18:00"), next.dateTime)
    }

    @Test fun zapisUTekstINatrag() {
        val list = listOf(extra("b", "18:00", "Engleski"), extra("a", "16:30", "Trening"))
        val decoded = ExtraAlarmCodec.decode(ExtraAlarmCodec.encode(list))
        assertEquals(listOf("a", "b"), decoded.map { it.id }) // sortirano po vremenu
        assertEquals("Trening", decoded[0].label)
    }

    @Test fun nazivSeCistiOdPosebnihZnakova() {
        val encoded = ExtraAlarmCodec.encode(listOf(extra("a", "16:30", "Tre|ning\nnovi red")))
        val decoded = ExtraAlarmCodec.decode(encoded)
        assertEquals(1, decoded.size)
        assertEquals("Tre ning novi red", decoded[0].label)
    }

    @Test fun neispravniRedciSePreskacu() {
        val decoded = ExtraAlarmCodec.decode("a|16:30|Trening\nlose\nb|25:99|X\n|10:00|bez id")
        assertEquals(listOf("a"), decoded.map { it.id })
    }

    @Test fun praznoJePrazno() {
        assertTrue(ExtraAlarmCodec.decode(null).isEmpty())
        assertTrue(ExtraAlarmCodec.decode("").isEmpty())
    }
}
