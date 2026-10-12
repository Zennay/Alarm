package com.zennay.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val config = AlarmStore.read(context)
        if (!config.enabled) return
        // A reboot cannot covertly shift a one-shot alarm to tomorrow.
        if (config.latestMs <= System.currentTimeMillis()) {
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
