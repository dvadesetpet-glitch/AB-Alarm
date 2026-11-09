package hr.abalarm.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import hr.abalarm.app.MainActivity
import hr.abalarm.app.R
import hr.abalarm.app.data.AlarmRepository
import hr.abalarm.app.data.AppLanguage
import hr.abalarm.app.data.TimeFormat
import hr.abalarm.app.data.Week
import hr.abalarm.app.domain.NextAlarm
import hr.abalarm.app.domain.NextAlarmCalculator
import hr.abalarm.app.ui.AppLocale
import hr.abalarm.app.ui.timeFormatter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

/**
 * Lančano zakazivanje: u AlarmManageru je uvijek zakazan najviše JEDAN alarm iz rasporeda
 * (+ eventualno jedna odgoda). Svaki put kad se alarm oglasi, AlarmReceiver poziva
 * [reschedule] koji izračuna sljedeći. setRepeating() se namjerno ne koristi.
 *
 * Koristi se setAlarmClock(): sustav ga tretira kao budilicu – izvršava se točno i u Doze
 * modu, ne podliježe ograničenjima učestalosti kao setExactAndAllowWhileIdle(), a u statusnoj
 * traci i na zaključanom zaslonu prikazuje se ikona budilice sa sljedećim alarmom.
 */
class AlarmScheduler(
    private val context: Context,
    private val repository: AlarmRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    /**
     * Poništi zakazani alarm, izračunaj sljedeći i zakaži ga.
     * Poziva se nakon svake promjene rasporeda/sidra, nakon oglašavanja alarma i nakon restarta.
     */
    suspend fun reschedule(now: ZonedDateTime = ZonedDateTime.now()): NextAlarm? = mutex.withLock {
        cancelChain()

        val state = repository.current()
        val anchor = state.settings.anchor
        if (anchor == null) {
            repository.setNextAlarm(null)
            return@withLock null
        }
        val schedule = state.schedule
        val nowMillis = now.toInstant().toEpochMilli()

        var pause = state.settings.pauseNext
        var skipAt = state.settings.skipAtMillis

        // Preskočeni alarm je već prošao → "Pauziraj sutra" je odrađen, resetiraj zastavicu.
        if (pause && skipAt != null && nowMillis >= skipAt) {
            repository.finishPause()
            pause = false
            skipAt = null
        }
        // Pauza je upravo uključena → zapamti koji se točno alarm preskače.
        if (pause && skipAt == null) {
            // Pauza vrijedi samo za glavni (školski) alarm, ne za dodatne.
            val first = NextAlarmCalculator.next(now, anchor, schedule, includeExtras = false)
            if (first != null) {
                skipAt = first.epochMillis
                repository.setSkipAt(first.epochMillis)
            }
        }

        val next = NextAlarmCalculator.next(now, anchor, schedule, skipMainAtMillis = if (pause) skipAt else null)
        if (next != null) {
            val language = state.settings.appLanguage
            scheduleAlarmClock(
                next.epochMillis,
                chainPendingIntent(
                    triggerAt = next.epochMillis,
                    language = language,
                    snooze = SnoozeInfo.forNewAlarm(state.settings),
                    title = titleFor(next, language),
                    label = labelFor(next, state.settings.timeFormat, language),
                ),
            )
        }
        repository.setNextAlarm(next?.epochMillis)
        Log.d(TAG, "Sljedeći alarm: ${next?.dateTime ?: "nema"} (preskače se: $skipAt)")
        next
    }

    /**
     * Odgoda (snooze) – zaseban PendingIntent, ne dira lanac. Također ide kroz setAlarmClock(),
     * pa se odgođeni alarm prikazuje u statusnoj traci i okida točno i u Doze modu.
     */
    fun scheduleSnooze(snooze: SnoozeInfo, title: String, label: String, language: AppLanguage) {
        val triggerAt = System.currentTimeMillis() + snooze.minutes * 60_000L
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_SNOOZE_FIRED)
            .putExtra(AlarmReceiver.EXTRA_TITLE, title)
            .putExtra(AlarmReceiver.EXTRA_LABEL, label)
            .putExtra(AlarmReceiver.EXTRA_LANGUAGE, language.name)
            .let(snooze::writeTo)
            .putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, triggerAt)
        val pi = PendingIntent.getBroadcast(context, REQUEST_SNOOZE, intent, PI_FLAGS)
        scheduleAlarmClock(triggerAt, pi)
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun scheduleAlarmClock(triggerAtMillis: Long, pi: PendingIntent) {
        if (canScheduleExactAlarms()) {
            // showIntent: dodir na ikonu budilice u sustavu otvara aplikaciju.
            val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showAppPendingIntent())
            alarmManager.setAlarmClock(info, pi)
        } else {
            // Bez dopuštenja za točne alarme: neprecizni alarm (može kasniti!). Korisnika
            // na ovo upozoravamo pri pokretanju i u čarobnjaku za dopuštenja.
            Log.w(TAG, "Nema dopuštenja za točne alarme – koristim neprecizni alarm")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun showAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        context, REQUEST_SHOW,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PI_FLAGS,
    )

    private fun cancelChain() {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_ALARM_FIRED)
        val existing = PendingIntent.getBroadcast(
            context, REQUEST_CHAIN, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (existing != null) {
            alarmManager.cancel(existing)
            existing.cancel()
        }
    }

    private fun chainPendingIntent(
        triggerAt: Long,
        language: AppLanguage,
        snooze: SnoozeInfo,
        title: String,
        label: String,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_ALARM_FIRED)
            .putExtra(AlarmReceiver.EXTRA_TITLE, title)
            .putExtra(AlarmReceiver.EXTRA_LABEL, label)
            .putExtra(AlarmReceiver.EXTRA_LANGUAGE, language.name)
            .let(snooze::writeTo)
            .putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, triggerAt)
        return PendingIntent.getBroadcast(context, REQUEST_CHAIN, intent, PI_FLAGS)
    }

    /** Naslov obavijesti: buđenje za školu ili dodatna aktivnost. */
    private fun titleFor(next: NextAlarm, language: AppLanguage): String =
        AppLocale.localizedContext(context, language).getString(
            if (next.isExtra) R.string.alarm_extra_title else R.string.alarm_notification_title,
        )

    /**
     * Oznaka alarma na jeziku aplikacije (sprema se u PendingIntent pri zakazivanju):
     * "Ujutro · Ponedjeljak 07:00" ili za dodatni alarm "Trening · Ponedjeljak 16:30".
     */
    private fun labelFor(next: NextAlarm, timeFormat: TimeFormat, language: AppLanguage): String {
        val ctx = AppLocale.localizedContext(context, language)
        val days = ctx.resources.getStringArray(R.array.day_names)
        val name = next.extraLabel?.ifBlank { ctx.getString(R.string.extra_default_label) }
            ?: ctx.getString(if (next.week == Week.A) R.string.week_a else R.string.week_b)
        val time = next.time.format(ctx.timeFormatter(timeFormat))
        return ctx.getString(R.string.alarm_label_format, name, days[next.day.value - 1], time)
    }

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val REQUEST_CHAIN = 100
        private const val REQUEST_SNOOZE = 101
        private const val REQUEST_SHOW = 102
        private const val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
