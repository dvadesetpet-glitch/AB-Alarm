package hr.abalarm.app

import android.app.Application
import android.content.Context
import hr.abalarm.app.alarm.AlarmNotifications
import hr.abalarm.app.alarm.AlarmScheduler
import hr.abalarm.app.data.AlarmRepository
import hr.abalarm.app.data.abAlarmDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Ručni DI – za ovaj opseg jednostavnije od Hilta. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository = AlarmRepository(context.abAlarmDataStore)
    val scheduler = AlarmScheduler(context, repository)
}

class AbAlarmApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AlarmNotifications.createChannels(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as AbAlarmApp).container
