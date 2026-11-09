package hr.abalarm.app.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import hr.abalarm.app.R
import hr.abalarm.app.data.DateStyle
import hr.abalarm.app.data.TimeFormat
import hr.abalarm.app.data.Week
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Formati datuma i vremena dolaze iz resursa (pattern_time, pattern_date, pattern_date_short),
 * pa svaki jezik ima svoj format: hrvatski "07:00" / "9.11.2026.", engleski "7:00 AM" / "Nov 9, 2026".
 * Format vremena korisnik može nadjačati u postavkama (12/24 sata) – vidi [TimeFormat].
 */

private const val PATTERN_24H = "HH:mm"
private const val PATTERN_12H = "h:mm a"

/** Odabrani format vremena; postavlja ga MainActivity iz postavki. */
val LocalTimeFormat = staticCompositionLocalOf { TimeFormat.AUTO }

/** Odabrani format datuma; postavlja ga MainActivity iz postavki. */
val LocalDateStyle = staticCompositionLocalOf { DateStyle.AUTO }

private val LOCALE_HR: Locale = Locale.forLanguageTag("hr")

/** Uzorak vremena za odabrani format; za AUTO se koristi uzorak jezika. */
fun TimeFormat.pattern(languagePattern: String): String = when (this) {
    TimeFormat.AUTO -> languagePattern
    TimeFormat.H24 -> PATTERN_24H
    TimeFormat.H12 -> PATTERN_12H
}

/** 12-satni format ako uzorak sadrži oznaku AM/PM ("a"). */
fun is24HourPattern(pattern: String): Boolean = !pattern.contains('a')

@Composable
private fun formatter(pattern: String): DateTimeFormatter {
    val locale: Locale = LocalConfiguration.current.locales[0]
    return remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}

@Composable
private fun formatter(@StringRes patternRes: Int): DateTimeFormatter = formatter(stringResource(patternRes))

@Composable
private fun timePattern(): String = LocalTimeFormat.current.pattern(stringResource(R.string.pattern_time))

@Composable
fun timeFormatter(): DateTimeFormatter = formatter(timePattern())

/** Treba li birač vremena biti 24-satni. */
@Composable
fun use24HourClock(): Boolean = is24HourPattern(timePattern())

/** Datum: za AUTO uzorak i jezik aplikacije, inače fiksni uzorak i jezik (zbog naziva mjeseca). */
@Composable
private fun dateFormatter(@StringRes languagePatternRes: Int, hrPattern: String, enPattern: String): DateTimeFormatter =
    when (LocalDateStyle.current) {
        DateStyle.AUTO -> formatter(languagePatternRes)
        DateStyle.HR -> remember(hrPattern) { DateTimeFormatter.ofPattern(hrPattern, LOCALE_HR) }
        DateStyle.EN -> remember(enPattern) { DateTimeFormatter.ofPattern(enPattern, Locale.ENGLISH) }
    }

@Composable
fun dateFormatter(): DateTimeFormatter = dateFormatter(R.string.pattern_date, "d.M.yyyy.", "MMM d, yyyy")

@Composable
fun shortDateFormatter(): DateTimeFormatter = dateFormatter(R.string.pattern_date_short, "d.M.", "MMM d")

/** Za kod izvan Composea (npr. oznaka alarma u scheduleru). */
fun Context.timeFormatter(format: TimeFormat): DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        format.pattern(getString(R.string.pattern_time)),
        resources.configuration.locales[0] ?: Locale.getDefault(),
    )

@Composable
fun dayName(day: DayOfWeek): String = stringArrayResource(R.array.day_names)[day.value - 1]

@Composable
fun weekName(week: Week): String =
    stringResource(if (week == Week.A) R.string.week_a else R.string.week_b)
