package com.zennay.alarm

import android.os.BatteryManager
import org.junit.Assert.*
import org.junit.Test

class ChargingGateTest {
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
