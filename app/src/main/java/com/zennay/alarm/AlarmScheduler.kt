package com.zennay.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {
    private fun alarmManager(c: Context) = c.getSystemService(AlarmManager::class.java)
    private fun intent(c: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, request, Intent(c, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    fun canSchedule(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager(c).canScheduleExactAlarms()

    fun schedule(c: Context, config: AlarmConfig) {
        require(config.enabled && config.earliestMs > System.currentTimeMillis())
        check(canSchedule(c)) { "Geef de app toestemming voor exacte alarmen." }
        cancel(c)
        val am = alarmManager(c)
        val show = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(config.earliestMs, show),
            intent(c, AlarmReceiver.START, 101)
        )
        if (config.mode == AlarmMode.SMART) {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, config.latestMs,
                intent(c, AlarmReceiver.DEADLINE, 102)
            )
        }
    }

    fun cancel(c: Context) {
        val am = alarmManager(c)
        am.cancel(intent(c, AlarmReceiver.START, 101))
        am.cancel(intent(c, AlarmReceiver.DEADLINE, 102))
    }
}
