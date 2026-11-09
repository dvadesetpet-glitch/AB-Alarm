package hr.abalarm.app.ui.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.abalarm.app.R
import hr.abalarm.app.alarm.AlarmService
import hr.abalarm.app.ui.theme.AbAlarmTheme

/** Prikaz preko zaključanog ekrana (full-screen intent) dok alarm zvoni. */
class AlarmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            AbAlarmTheme {
                val label by AlarmService.ringingLabel.collectAsStateWithLifecycle()
                val title by AlarmService.ringingTitle.collectAsStateWithLifecycle()
                val snooze by AlarmService.snooze.collectAsStateWithLifecycle()
                // Alarm je zaustavljen (gumb, obavijest ili automatski) → zatvori ekran.
                LaunchedEffect(label) {
                    if (label == null) finish()
                }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = title ?: stringResource(R.string.alarm_notification_title),
                            fontSize = 40.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = label.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        if (snooze.canSnooze) {
                            OutlinedButton(
                                onClick = { AlarmService.sendAction(this@AlarmActivity, AlarmService.ACTION_SNOOZE) },
                                modifier = Modifier.fillMaxWidth().height(72.dp),
                            ) { Text(stringResource(R.string.action_snooze, snooze.minutes), fontSize = 22.sp) }
                        }
                        Button(
                            onClick = { AlarmService.sendAction(this@AlarmActivity, AlarmService.ACTION_STOP) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().height(88.dp),
                        ) { Text(stringResource(R.string.action_stop), fontSize = 26.sp) }
                    }
                }
            }
        }
    }
}
