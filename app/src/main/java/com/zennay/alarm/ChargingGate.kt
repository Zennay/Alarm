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

    data class State(val plugged: Int, val status: Int) {
        val charging: Boolean get() = ChargingGate.isCharging(plugged, status)
    }

    fun stateFromIntent(intent: Intent?): State {
        if (intent?.action != Intent.ACTION_BATTERY_CHANGED)
            return State(0, BatteryManager.BATTERY_STATUS_UNKNOWN)
        return State(
            intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        )
    }

    fun stateNow(context: Context): State =
        stateFromIntent(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))

    fun fromIntent(intent: Intent?): Boolean = stateFromIntent(intent).charging

    /**
     * Read the sticky platform battery state. No activity, internet or runtime
     * permission is needed. Returns false when the system cannot report status.
     */
    fun isChargingNow(context: Context): Boolean = stateNow(context).charging
}

/**
 * Reject transient/old battery broadcasts: charging is accepted only after
 * consecutive system snapshots remain positive for the required duration.
 * elapsedRealtime rather than wall-clock time ignores minute/time changes.
 */
class ChargingConfirmation(private val confirmMs: Long = 2500L) {
    init { require(confirmMs > 0) }
    private var firstSeenAt: Long? = null

    fun observe(charging: Boolean, elapsedMs: Long): Boolean {
        if (!charging) {
            firstSeenAt = null
            return false
        }
        val first = firstSeenAt
        if (first == null || elapsedMs < first) {
            firstSeenAt = elapsedMs
            return false
        }
        return elapsedMs - first >= confirmMs
    }

    fun reset() { firstSeenAt = null }
}
