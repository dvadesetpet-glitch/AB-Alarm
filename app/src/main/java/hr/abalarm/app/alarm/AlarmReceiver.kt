package hr.abalarm.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import hr.abalarm.app.appContainer
import hr.abalarm.app.data.AppLanguage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Prima okidanje alarma iz AlarmManagera: pokreće zvonjavu i ODMAH zakazuje sljedeći alarm (lanac).
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        // Stariji zakazani alarmi nemaju naslov → servis koristi zadani.
        val title = intent.getStringExtra(EXTRA_TITLE)
        val language = intent.getStringExtra(EXTRA_LANGUAGE)
            ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
            ?: AppLanguage.SYSTEM
        val snooze = SnoozeInfo.from(intent)
        when (intent.action) {
            ACTION_ALARM_FIRED -> {
                AlarmService.startRinging(context, title, label, language, snooze)

                // Sljedeći alarm računamo od trenutka NAKON ovog, da se isti alarm ne zakaže ponovno.
                val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, 0L)
                val nowMillis = maxOf(System.currentTimeMillis(), triggerAt + 1_000L)
                val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())

                val pending = goAsync()
                val container = context.appContainer
                container.appScope.launch {
                    try {
                        container.scheduler.reschedule(now)
                    } catch (e: Exception) {
                        Log.e(TAG, "Zakazivanje sljedećeg alarma nije uspjelo", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_SNOOZE_FIRED -> AlarmService.startRinging(context, title, label, language, snooze)
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_ALARM_FIRED = "hr.abalarm.app.action.ALARM_FIRED"
        const val ACTION_SNOOZE_FIRED = "hr.abalarm.app.action.SNOOZE_FIRED"
        const val EXTRA_LABEL = "label"
        const val EXTRA_TITLE = "title"
        const val EXTRA_LANGUAGE = "language"
        const val EXTRA_TRIGGER_AT = "trigger_at"
    }
}
