package hr.abalarm.app.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import hr.abalarm.app.appContainer
import kotlinx.coroutines.launch

/**
 * AlarmManager zaboravlja alarme nakon restarta, a promjena vremena/zone mijenja izračun.
 * U svim tim slučajevima ponovno izračunaj i zakaži sljedeći alarm.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val pending = goAsync()
        val container = context.appContainer
        container.appScope.launch {
            try {
                container.scheduler.reschedule()
            } catch (e: Exception) {
                Log.e("BootReceiver", "Ponovno zakazivanje nije uspjelo", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
