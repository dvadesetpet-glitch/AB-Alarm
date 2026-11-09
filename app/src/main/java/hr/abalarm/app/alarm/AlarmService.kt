package hr.abalarm.app.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import hr.abalarm.app.R
import hr.abalarm.app.appContainer
import hr.abalarm.app.data.AppLanguage
import hr.abalarm.app.ui.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Kratkotrajni ForegroundService: reproducira ton (+ vibracija), prikazuje obavijest s
 * "Odgodi 5 min" / "Zaustavi" i gasi se nakon akcije.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopAlarm() }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var currentLabel: String = ""
    private var currentTitle: String? = null
    private var currentLanguage: AppLanguage = AppLanguage.SYSTEM
    private var currentSnooze: SnoozeInfo = SnoozeInfo.DEFAULT

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                currentLabel = intent.getStringExtra(EXTRA_LABEL).orEmpty()
                currentTitle = intent.getStringExtra(EXTRA_TITLE)
                currentLanguage = intent.getStringExtra(EXTRA_LANGUAGE)
                    ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
                    ?: AppLanguage.SYSTEM
                currentSnooze = SnoozeInfo.from(intent)
                // startForeground mora biti pozvan odmah nakon startForegroundService().
                ServiceCompat.startForeground(
                    this,
                    AlarmNotifications.NOTIFICATION_ID_RINGING,
                    AlarmNotifications.buildRinging(AppLocale.localizedContext(this, currentLanguage), currentTitle, currentLabel, currentSnooze),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    } else 0,
                )
                _snooze.value = currentSnooze
                _ringingTitle.value = currentTitle
                _ringingLabel.value = currentLabel
                startRinging()
            }
            ACTION_SNOOZE -> {
                // Ograničenje odgoda: ako su potrošene, "Odgodi" samo zaustavlja (gumb se ionako ne prikazuje).
                if (currentSnooze.canSnooze) {
                    appContainer.scheduler.scheduleSnooze(
                        currentSnooze.next(),
                        currentTitle ?: AppLocale.localizedContext(this, currentLanguage)
                            .getString(R.string.alarm_notification_title),
                        currentLabel,
                        currentLanguage,
                    )
                }
                stopAlarm()
            }
            ACTION_STOP -> stopAlarm()
            else -> stopAlarm()
        }
        return START_NOT_STICKY
    }

    private fun startRinging() {
        stopPlayback()
        scope.launch {
            val settings = appContainer.repository.current().settings
            startSound(settings.ringtoneUri?.let(Uri::parse))
            if (settings.vibrate) startVibration()
        }
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, AUTO_STOP_MS)
    }

    private fun startSound(custom: Uri?) {
        val candidates = listOfNotNull(
            custom,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                    prepare()
                    start()
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Ne mogu reproducirati $uri", e)
                player?.release()
                player = null
            }
        }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (v == null || !v.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
        vibrator = v
    }

    private fun stopPlayback() {
        player?.run {
            try { stop() } catch (_: IllegalStateException) { }
            release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun stopAlarm() {
        handler.removeCallbacks(autoStop)
        stopPlayback()
        _ringingLabel.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        stopPlayback()
        _ringingLabel.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        const val ACTION_RING = "hr.abalarm.app.action.RING"
        const val ACTION_SNOOZE = "hr.abalarm.app.action.SNOOZE"
        const val ACTION_STOP = "hr.abalarm.app.action.STOP"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LANGUAGE = "language"

        /** Sigurnosno gašenje ako nitko ne reagira (da ton ne svira satima). */
        private const val AUTO_STOP_MS = 10 * 60 * 1000L

        private val _ringingLabel = MutableStateFlow<String?>(null)

        /** Oznaka alarma koji trenutno zvoni ili null – prati je AlarmActivity. */
        val ringingLabel: StateFlow<String?> = _ringingLabel.asStateFlow()

        private val _ringingTitle = MutableStateFlow<String?>(null)

        /** Naslov alarma koji zvoni (null = zadani "Vrijeme je za ustajanje!"). */
        val ringingTitle: StateFlow<String?> = _ringingTitle.asStateFlow()

        private val _snooze = MutableStateFlow(SnoozeInfo.DEFAULT)

        /** Odgoda za alarm koji trenutno zvoni (trajanje, preostale odgode) – za AlarmActivity. */
        val snooze: StateFlow<SnoozeInfo> = _snooze.asStateFlow()

        fun startRinging(context: Context, title: String?, label: String, language: AppLanguage, snooze: SnoozeInfo) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_LANGUAGE, language.name)
                .let(snooze::writeTo)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // npr. ForegroundServiceStartNotAllowedException (Android 12+) ako alarm nije bio točan
                Log.e(TAG, "ForegroundService nije pokrenut, prikazujem rezervnu obavijest", e)
                AlarmNotifications.showFallback(AppLocale.localizedContext(context, language), title, label)
            }
        }

        fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                context, requestCode,
                Intent(context, AlarmService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** Za gumbe u AlarmActivity (aplikacija je tada u prvom planu). */
        fun sendAction(context: Context, action: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }
    }
}
