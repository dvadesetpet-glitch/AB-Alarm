package hr.abalarm.app.domain

import hr.abalarm.app.data.Anchor
import hr.abalarm.app.data.Week
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Izračun kojem tjednu (A/B) pripada neki datum.
 *
 * Važno: oba datuma (sidro i traženi datum) se najprije svedu na ponedjeljak svog
 * kalendarskog tjedna. Tek onda se računa razlika u tjednima s Math.floorDiv.
 *
 * Zašto: formula floorDiv(datum - sidro, 7) na "sirovim" datumima pomiče granicu
 * tjedna na dan sidra. Npr. sidro = srijeda (Tjedan A) → sljedeći ponedjeljak je
 * razlika 5 dana → floorDiv(5, 7) = 0 → Tjedan A, što je pogrešno (to je već Tjedan B).
 * Normalizacijom na ponedjeljak razlika je uvijek višekratnik od 7, pa sidro smije
 * biti bilo koji dan u tjednu.
 */
object CycleCalculator {

    fun weekForDate(date: LocalDate, anchor: Anchor): Week {
        val offset = weekOffset(date, anchor.date)
        // floorMod daje 0 ili 1 i za negativne pomake (datumi prije sidra).
        return if (Math.floorMod(offset, 2L) == 0L) anchor.week else anchor.week.other()
    }

    /** Broj kalendarskih tjedana (pon–ned) između sidra i datuma; negativan za datume prije sidra. */
    fun weekOffset(date: LocalDate, anchorDate: LocalDate): Long {
        val dateMonday = mondayOf(date)
        val anchorMonday = mondayOf(anchorDate)
        val diffDays = ChronoUnit.DAYS.between(anchorMonday, dateMonday)
        return Math.floorDiv(diffDays, 7L)
    }

    fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
