package hr.abalarm.app.alarm

import android.content.Intent
import hr.abalarm.app.data.AppSettings

/**
 * Stanje odgode koje putuje s alarmom (Intent extras): trajanje, najveći broj odgoda
 * i koliko je puta ovaj alarm već odgođen.
 */
data class SnoozeInfo(
    val minutes: Int,
    /** Najveći broj odgoda; [AppSettings.SNOOZE_UNLIMITED] = bez ograničenja. */
    val limit: Int,
    /** Koliko je puta ovaj alarm već odgođen. */
    val count: Int,
) {
    val canSnooze: Boolean
        get() = limit == AppSettings.SNOOZE_UNLIMITED || count < limit

    /** Stanje za sljedeću (odgođenu) zvonjavu. */
    fun next(): SnoozeInfo = copy(count = count + 1)

    fun writeTo(intent: Intent): Intent = intent
        .putExtra(EXTRA_MINUTES, minutes)
        .putExtra(EXTRA_LIMIT, limit)
        .putExtra(EXTRA_COUNT, count)

    companion object {
        private const val EXTRA_MINUTES = "snooze_minutes"
        private const val EXTRA_LIMIT = "snooze_limit"
        private const val EXTRA_COUNT = "snooze_count"

        val DEFAULT = SnoozeInfo(AppSettings.DEFAULT_SNOOZE_MINUTES, AppSettings.SNOOZE_UNLIMITED, 0)

        fun forNewAlarm(settings: AppSettings) = SnoozeInfo(settings.snoozeMinutes, settings.snoozeRepeat, 0)

        fun from(intent: Intent?): SnoozeInfo = if (intent == null) DEFAULT else SnoozeInfo(
            minutes = intent.getIntExtra(EXTRA_MINUTES, DEFAULT.minutes),
            limit = intent.getIntExtra(EXTRA_LIMIT, DEFAULT.limit),
            count = intent.getIntExtra(EXTRA_COUNT, 0),
        )
    }
}
