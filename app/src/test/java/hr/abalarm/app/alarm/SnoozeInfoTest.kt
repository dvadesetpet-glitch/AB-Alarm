package hr.abalarm.app.alarm

import hr.abalarm.app.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeInfoTest {

    @Test fun triOdgodePaKraj() {
        var s = SnoozeInfo(minutes = 5, limit = 3, count = 0)
        repeat(3) {
            assertTrue(s.canSnooze)
            s = s.next()
        }
        assertEquals(3, s.count)
        assertFalse(s.canSnooze)
    }

    @Test fun jednaOdgoda() {
        val s = SnoozeInfo(minutes = 10, limit = 1, count = 0)
        assertTrue(s.canSnooze)
        assertFalse(s.next().canSnooze)
    }

    @Test fun neogranicenoUvijekDopusta() {
        var s = SnoozeInfo(minutes = 5, limit = AppSettings.SNOOZE_UNLIMITED, count = 0)
        repeat(100) { s = s.next() }
        assertTrue(s.canSnooze)
    }

    @Test fun noviAlarmKreceOdNule() {
        val settings = AppSettings(snoozeMinutes = 15, snoozeRepeat = 5)
        assertEquals(SnoozeInfo(15, 5, 0), SnoozeInfo.forNewAlarm(settings))
    }
}
