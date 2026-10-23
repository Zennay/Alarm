package com.zennay.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RECOVER) {
            // A time boundary is never a reason to stop. Only resume alarms
            // which are still persistently ringing; charge dismissal clears it.
            if (AlarmStore.isRinging(context)) {
                AlarmScheduler.scheduleRecovery(context)
                WakeService.send(context, WakeService.RING)
            }
            return
        }
        val saved = AlarmStore.read(context)
        if (!saved.enabled) return
        val receivedAt = System.currentTimeMillis()
        when (intent.action) {
            START -> {
                // PendingIntent action/request codes are reused after a user
                // reschedules. An in-flight START from the old alarm must
                // never awaken the newly armed alarm before its actual start.
                if (receivedAt < saved.earliestMs) return
                if (saved.mode == AlarmMode.SMART && receivedAt < saved.latestMs) {
                    WakeService.send(context, WakeService.MONITOR)
                } else {
                    WakeService.send(context, WakeService.RING)
                }
            }
            DEADLINE -> {
                // A stale deadline broadcast can outlive the cancelled old
                // slot. Ring only once the CURRENT schedule's deadline is due.
                if (receivedAt >= saved.latestMs) {
                    WakeService.send(context, WakeService.RING)
                }
            }
        }
    }
    companion object {
        const val START = "com.zennay.alarm.START"
        const val DEADLINE = "com.zennay.alarm.DEADLINE"
        const val RECOVER = "com.zennay.alarm.RECOVER"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val config = AlarmStore.read(context)
        if (!config.enabled) return
        // Clock sync (TIME_SET) and timezone changes must NEVER dismiss an
        // already-ringing alarm merely because its original minute passed.
        if (AlarmStore.isRinging(context)) {
            AlarmScheduler.scheduleRecovery(context)
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                WakeService.send(context, WakeService.RING)
            }
            return
        }
        val nowMs = System.currentTimeMillis()
        // During an active SMART window, a manual clock correction can move
        // real time past the deadline before the scheduled RTC broadcast
        // arrives. Never silently disable an actively tracked wakeup.
        // After a cold reboot, keep the existing one-shot expiry behaviour:
        // a stale alarm must not unexpectedly ring hours after boot.
        val correctedClock = intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        if (AlarmStore.isTracking(context) && nowMs >= config.latestMs && correctedClock) {
            WakeService.send(context, WakeService.RING)
            return
        }
        // Expire an overdue not-ringing one-shot only when no tracked
        // clock-correction deadline requires immediate ringing.
        if (AlarmContinuationPolicy.shouldExpire(
                nowMs, config.latestMs, alreadyRinging = false)) {
            // No confirmed ringing/charging receipt exists after an elapsed
            // deadline. Do not silently label that wake as delivered on the
            // next Watch screen; retain the original instant for review.
            AlarmStore.expireUnconfirmed(context)
            return
        }
        if (AlarmScheduler.canSchedule(context)) {
            if (config.earliestMs > System.currentTimeMillis()) {
                AlarmScheduler.schedule(context, config)
            } else {
                // Reboot during smart window: skip early prediction, keep latest alarm.
                AlarmScheduler.restoreDeadline(context, config.latestMs)
            }
        }
    }
}
