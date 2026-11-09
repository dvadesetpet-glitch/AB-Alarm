package hr.abalarm.app.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import hr.abalarm.app.R

/** Provjere dopuštenja i Intenti prema odgovarajućim zaslonima postavki sustava. */
object PermissionHelper {

    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun canUseFullScreenIntent(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            launch(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context)))
        }
    }

    fun openNotificationSettings(context: Context) {
        launch(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }

    /**
     * Izravan zahtjev za izuzeće od optimizacije baterije (traži REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).
     * Napomena: Google Play ograničava ovo dopuštenje na određene kategorije aplikacija
     * (aplikacije-budilice su među prihvatljivima) – provjeriti aktualnu politiku prije objave.
     */
    @SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
        if (!launch(context, direct)) {
            launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            launch(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context)))
        }
    }

    fun openAppDetails(context: Context) {
        launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
    }

    private fun packageUri(context: Context): Uri = "package:${context.packageName}".toUri()

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/**
 * Proizvođači s agresivnim upravljanjem pozadinom. Upute su tekstualne jer točni nazivi
 * izbornika (i interne Activity klase) razlikuju se po verziji sustava – ne otvaramo
 * nedokumentirane komponente, nego zaslon s detaljima aplikacije.
 */
enum class Manufacturer(@StringRes val instructionsRes: Int?) {
    XIAOMI(R.string.oem_xiaomi),
    HUAWEI(R.string.oem_huawei),
    OPPO(R.string.oem_oppo),
    VIVO(R.string.oem_vivo),
    OTHER(null);

    companion object {
        fun current(): Manufacturer {
            val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
            return when {
                listOf("xiaomi", "redmi", "poco").any { it in m } -> XIAOMI
                listOf("huawei", "honor").any { it in m } -> HUAWEI
                listOf("oppo", "realme", "oneplus").any { it in m } -> OPPO
                listOf("vivo", "iqoo").any { it in m } -> VIVO
                else -> OTHER
            }
        }
    }
}
