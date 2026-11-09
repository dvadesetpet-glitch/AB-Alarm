package hr.abalarm.app.alarm

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import hr.abalarm.app.R
import hr.abalarm.app.permissions.PermissionHelper
import hr.abalarm.app.ui.alarm.AlarmActivity

object AlarmNotifications {
    const val CHANNEL_ALARM = "alarm"
    const val CHANNEL_FALLBACK = "alarm_fallback"
    const val NOTIFICATION_ID_RINGING = 1
    private const val NOTIFICATION_ID_FALLBACK = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        // Glavni kanal: zvuk i vibraciju reproducira AlarmService, pa kanal nema vlastiti zvuk.
        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        // Rezervni kanal (ako se servis ne može pokrenuti): zvuk zvoni sama obavijest.
        val fallback = NotificationChannel(
            CHANNEL_FALLBACK,
            context.getString(R.string.channel_fallback_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_fallback_desc)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(alarm, fallback))
    }

    /** Obavijest dok alarm zvoni (koristi je ForegroundService). */
    fun buildRinging(context: Context, title: String?, label: String, snooze: SnoozeInfo): android.app.Notification {
        val fullScreen = PendingIntent.getActivity(
            context, 0,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title ?: context.getString(R.string.alarm_notification_title))
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                // Kad su odgode potrošene, ostaje samo "Zaustavi".
                if (snooze.canSnooze) {
                    addAction(
                        0,
                        context.getString(R.string.action_snooze, snooze.minutes),
                        AlarmService.actionIntent(context, AlarmService.ACTION_SNOOZE, 1),
                    )
                }
            }
            .addAction(0, context.getString(R.string.action_stop), AlarmService.actionIntent(context, AlarmService.ACTION_STOP, 2))
            .build()
    }

    /** Rezerva ako Android ne dopusti pokretanje ForegroundServicea. */
    @SuppressLint("MissingPermission") // provjereno preko PermissionHelper.hasNotificationPermission
    fun showFallback(context: Context, title: String?, label: String) {
        if (!PermissionHelper.hasNotificationPermission(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_FALLBACK)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title ?: context.getString(R.string.alarm_notification_title))
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FALLBACK, notification)
    }
}
