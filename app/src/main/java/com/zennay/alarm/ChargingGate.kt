package com.zennay.alarm

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Only a confirmed charging/full battery state with an attached power source
 * can satisfy an active alarm. 'Plugged in' alone might mean charging is paused.
 */
object ChargingGate {
    fun isCharging(plugged: Int, status: Int): Boolean =
        plugged != 0 && (status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL)

    fun shouldDismiss(ringing: Boolean, plugged: Int, status: Int): Boolean =
        ringing && isCharging(plugged, status)

    fun fromIntent(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return false
        return isCharging(
            intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        )
    }

    /**
     * Read the sticky platform battery state. No activity, internet or runtime
     * permission is needed. Returns false when the system cannot report status.
     */
    fun isChargingNow(context: Context): Boolean =
        fromIntent(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
}
