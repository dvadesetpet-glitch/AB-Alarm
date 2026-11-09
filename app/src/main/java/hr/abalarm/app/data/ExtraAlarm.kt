package hr.abalarm.app.data

import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Dodatni alarm za jedan dan u smjeni (trening, strani jezik…). Zvoni kao glavni alarm,
 * ali ga "Pauziraj sutra" ne preskače.
 */
data class ExtraAlarm(
    val id: String,
    val time: LocalTime,
    val label: String,
) {
    companion object {
        const val MAX_PER_DAY = 5
        const val MAX_LABEL_LENGTH = 30
    }
}

/**
 * Zapis popisa dodatnih alarma u jedan string (DataStore Preferences nema liste):
 * jedan alarm po retku, polja "id|HH:mm|naziv". Naziv se čisti od '|' i prijeloma retka.
 */
object ExtraAlarmCodec {

    fun encode(alarms: List<ExtraAlarm>): String =
        alarms.joinToString("\n") { "${it.id}|${it.time}|${cleanLabel(it.label)}" }

    fun decode(value: String?): List<ExtraAlarm> {
        if (value.isNullOrBlank()) return emptyList()
        return value.lines().mapNotNull { line ->
            val parts = line.split('|', limit = 3)
            if (parts.size != 3 || parts[0].isBlank()) return@mapNotNull null
            val time = try { LocalTime.parse(parts[1]) } catch (_: DateTimeParseException) { null }
                ?: return@mapNotNull null
            ExtraAlarm(id = parts[0], time = time, label = parts[2])
        }.sortedBy { it.time }
    }

    fun cleanLabel(label: String): String =
        label.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim().take(ExtraAlarm.MAX_LABEL_LENGTH)
}
