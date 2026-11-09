package hr.abalarm.app.domain

import hr.abalarm.app.data.Anchor
import hr.abalarm.app.data.Week
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CycleCalculatorTest {

    private fun week(date: String, anchorDate: String, anchorWeek: Week): Week =
        CycleCalculator.weekForDate(LocalDate.parse(date), Anchor(LocalDate.parse(anchorDate), anchorWeek))

    // Sidro: srijeda 11.11.2026., Tjedan A (namjerno NIJE ponedjeljak).
    private val wed = "2026-11-11"

    @Test fun datumJednakSidru() = assertEquals(Week.A, week(wed, wed, Week.A))

    @Test fun sidroPlusJedanDan() = assertEquals(Week.A, week("2026-11-12", wed, Week.A))

    @Test fun sidroMinusJedanDan() = assertEquals(Week.A, week("2026-11-10", wed, Week.A))

    @Test fun sidroPlusSedamDana() = assertEquals(Week.B, week("2026-11-18", wed, Week.A))

    @Test fun sidroMinusSedamDana() = assertEquals(Week.B, week("2026-11-04", wed, Week.A))

    @Test fun sidroPlusOsamDana() = assertEquals(Week.B, week("2026-11-19", wed, Week.A))

    @Test fun sidroMinusOsamDana() = assertEquals(Week.B, week("2026-11-03", wed, Week.A))

    @Test fun sidroPlusCetrnaestDana() = assertEquals(Week.A, week("2026-11-25", wed, Week.A))

    @Test fun sidroMinusCetrnaestDana() = assertEquals(Week.A, week("2026-10-28", wed, Week.A))

    /** Ključni slučaj: sirova formula floorDiv(5, 7) = 0 dala bi A; ispravno je B (novi tjedan). */
    @Test fun ponedjeljakNakonSrijedeJeNoviTjedan() = assertEquals(Week.B, week("2026-11-16", wed, Week.A))

    @Test fun nedjeljaIstogTjednaKaoSrijeda() = assertEquals(Week.A, week("2026-11-15", wed, Week.A))

    @Test fun ponedjeljakIstogTjednaKaoSrijeda() = assertEquals(Week.A, week("2026-11-09", wed, Week.A))

    @Test fun nedjeljaPrijeSrijedeJeProsliTjedan() = assertEquals(Week.B, week("2026-11-08", wed, Week.A))

    /** Sidro u nedjelju: dan kasnije (ponedjeljak) je već drugi tjedan. */
    @Test fun sidroNedjeljaPlusJedanDan() = assertEquals(Week.B, week("2026-11-16", "2026-11-15", Week.A))

    @Test fun sidroNedjeljaMinusSestDana() = assertEquals(Week.A, week("2026-11-09", "2026-11-15", Week.A))

    @Test fun sidroTjedanBObrceRezultat() {
        assertEquals(Week.B, week(wed, wed, Week.B))
        assertEquals(Week.A, week("2026-11-18", wed, Week.B))
        assertEquals(Week.A, week("2026-11-04", wed, Week.B))
    }

    @Test fun prijelazPrekoGraniceMjeseca() {
        // Sidro pet 27.11.2026. A → pon 30.11. je B, uto 1.12. je B, pon 7.12. je A
        assertEquals(Week.B, week("2026-11-30", "2026-11-27", Week.A))
        assertEquals(Week.B, week("2026-12-01", "2026-11-27", Week.A))
        assertEquals(Week.A, week("2026-12-07", "2026-11-27", Week.A))
    }

    @Test fun prijelazPrekoGraniceGodine() {
        // Sidro sri 30.12.2026. A → čet 31.12. A, pet 1.1.2027. A, pon 4.1.2027. B, pon 11.1.2027. A
        val anchor = "2026-12-30"
        assertEquals(Week.A, week("2026-12-31", anchor, Week.A))
        assertEquals(Week.A, week("2027-01-01", anchor, Week.A))
        assertEquals(Week.B, week("2027-01-04", anchor, Week.A))
        assertEquals(Week.A, week("2027-01-11", anchor, Week.A))
        // i unatrag preko granice godine (sidro pon 4.1.2027. A)
        assertEquals(Week.B, week("2026-12-31", "2027-01-04", Week.A)) // tjedan od 28.12.
        assertEquals(Week.A, week("2026-12-27", "2027-01-04", Week.A)) // tjedan od 21.12.
    }

    @Test fun prijestupnaGodina() {
        // 2028. je prijestupna; pon 28.2.2028. je A
        val anchor = "2028-02-28"
        assertEquals(Week.A, week("2028-02-29", anchor, Week.A))
        assertEquals(Week.A, week("2028-03-05", anchor, Week.A)) // nedjelja
        assertEquals(Week.B, week("2028-03-06", anchor, Week.A)) // ponedjeljak
        // sidro na sam 29.2.
        assertEquals(Week.B, week("2028-03-07", "2028-02-29", Week.A))
        assertEquals(Week.B, week("2028-02-22", "2028-02-29", Week.A))
    }

    @Test fun dalekaProslostIBuducnost() {
        // 52 tjedna = paran broj → isti tjedan; 53 tjedna → suprotan
        assertEquals(Week.A, week("2027-11-10", wed, Week.A)) // +52 tjedna
        assertEquals(Week.B, week("2027-11-17", wed, Week.A)) // +53 tjedna
        assertEquals(Week.A, week("2025-11-12", wed, Week.A)) // -52 tjedna
        assertEquals(Week.B, week("2025-11-05", wed, Week.A)) // -53 tjedna
    }

    @Test fun pomakUTjednimaJeNegativanPrijeSidra() {
        assertEquals(-1L, CycleCalculator.weekOffset(LocalDate.parse("2026-11-08"), LocalDate.parse(wed)))
        assertEquals(0L, CycleCalculator.weekOffset(LocalDate.parse("2026-11-09"), LocalDate.parse(wed)))
        assertEquals(1L, CycleCalculator.weekOffset(LocalDate.parse("2026-11-16"), LocalDate.parse(wed)))
    }
}
