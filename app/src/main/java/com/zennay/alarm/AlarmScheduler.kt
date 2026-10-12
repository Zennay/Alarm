package com.zennay.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

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

    /** After reboot during a smart window, restore *deadline*, never alarm immediately. */
    fun restoreDeadline(c: Context, latestMs: Long) {
        if (latestMs <= System.currentTimeMillis() || !canSchedule(c)) return
        alarmManager(c).setAlarmClock(
            AlarmManager.AlarmClockInfo(latestMs, PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )),
            intent(c, AlarmReceiver.DEADLINE, 102)
        )
    }

    /**
     * While ringing, an independent RTC/elapsed-time wakeup restarts playback
     * if Wear OS kills the foreground service. Rescheduled by the receiver,
     * and cancelled ONLY after genuine charger dismissal.
     *
     * setAndAllowWhileIdle is the fallback when exact alarms are unavailable.
     */
    fun scheduleRecovery(c: Context, delayMs: Long = 30_000L) {
        if (!AlarmStore.isRinging(c)) return
        val am = alarmManager(c)
        val at = SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(10_000L)
        val pending = intent(c, AlarmReceiver.RECOVER, 103)
        try {
            if (canSchedule(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            Log.w("AlarmRecovery", "Exact wakeup denied, trying non-exact recovery", e)
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        }
    }

    fun cancelRecovery(c: Context) {
        alarmManager(c).cancel(intent(c, AlarmReceiver.RECOVER, 103))
    }

    fun cancel(c: Context) {
        cancelRecovery(c)
        val am = alarmManager(c)
        am.cancel(intent(c, AlarmReceiver.START, 101))
        am.cancel(intent(c, AlarmReceiver.DEADLINE, 102))
    }
}
