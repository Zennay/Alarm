package com.zennay.alarm

import android.os.BatteryManager
import org.junit.Assert.*
import org.junit.Test

class ChargingGateTest {
    @Test fun shortChargingGlitchMustNotStopAlarm() {
        val gate = ChargingConfirmation(2500L)
        assertFalse(gate.observe(false, 0L))
        assertFalse(gate.observe(true, 1000L))
        assertFalse(gate.observe(true, 2000L))
        assertFalse(gate.observe(false, 2200L))
        assertFalse(gate.observe(true, 3000L))
        assertFalse(gate.observe(true, 5400L))
        assertTrue(gate.observe(true, 5500L))
    }

    @Test fun clockMinuteBoundaryDoesNotCountAsElapsedCharging() {
        val gate = ChargingConfirmation(2500L)
        assertFalse(gate.observe(true, 57_000L))
        assertFalse(gate.observe(true, 59_000L))
        assertTrue(gate.observe(true, 60_000L))
    }

    @Test fun negativeMonotonicShiftResetsChargingConfirmation() {
        val gate = ChargingConfirmation()
        assertFalse(gate.observe(true, 5000L))
        assertFalse(gate.observe(true, 3000L))
        assertFalse(gate.observe(true, 4500L))
        assertTrue(gate.observe(true, 5500L))
    }

    @Test fun chargingOnWirelessPadStopsActiveAlarm() {
        assertTrue(ChargingGate.shouldDismiss(true,
            BatteryManager.BATTERY_PLUGGED_WIRELESS,
            BatteryManager.BATTERY_STATUS_CHARGING))
    }

    @Test fun alreadyFullOnChargerStopsActiveAlarm() {
        assertTrue(ChargingGate.shouldDismiss(true,
            BatteryManager.BATTERY_PLUGGED_WIRELESS,
            BatteryManager.BATTERY_STATUS_FULL))
    }

    @Test fun withoutAnActiveAlarmChargingDoesNothing() {
        assertFalse(ChargingGate.shouldDismiss(false,
            BatteryManager.BATTERY_PLUGGED_WIRELESS,
            BatteryManager.BATTERY_STATUS_CHARGING))
    }

    @Test fun restingWatchNeverStopsAlarm() {
        assertFalse(ChargingGate.shouldDismiss(true,
            0, BatteryManager.BATTERY_STATUS_DISCHARGING))
    }

    @Test fun chargingStatusWithoutPowerSourceIsInsufficient() {
        assertFalse(ChargingGate.shouldDismiss(true,
            0, BatteryManager.BATTERY_STATUS_CHARGING))
    }

    @Test fun chargingPadPresentButChargingPausedDoesNotStop() {
        assertFalse(ChargingGate.shouldDismiss(true,
            BatteryManager.BATTERY_PLUGGED_WIRELESS,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING))
    }

    @Test fun unknownBatteryStatusDoesNotStop() {
        assertFalse(ChargingGate.shouldDismiss(true,
            BatteryManager.BATTERY_PLUGGED_WIRELESS,
            BatteryManager.BATTERY_STATUS_UNKNOWN))
    }
}
