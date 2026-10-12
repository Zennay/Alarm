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
        when (intent.action) {
            START -> {
                if (saved.mode == AlarmMode.SMART && System.currentTimeMillis() < saved.latestMs) {
                    WakeService.send(context, WakeService.MONITOR)
                } else {
                    WakeService.send(context, WakeService.RING)
                }
            }
            DEADLINE -> WakeService.send(context, WakeService.RING)
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
        // For an alarm not yet ringing, an expired one-shot can be cleared.
        if (AlarmContinuationPolicy.shouldExpire(
                System.currentTimeMillis(), config.latestMs, alreadyRinging = false)) {
            AlarmStore.setDisabled(context)
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
