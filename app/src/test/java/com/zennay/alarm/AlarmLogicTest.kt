package com.zennay.alarm

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmLogicTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private fun instant(iso: String) = LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli()

    @Test fun activeAlarmSurvivesNextMinuteAndLaterClockUpdates() {
        val latest = instant("2026-10-12T07:00:00")
        assertFalse(AlarmContinuationPolicy.shouldExpire(latest + 1_000, latest, true))
        assertFalse(AlarmContinuationPolicy.shouldExpire(latest + 60_000, latest, true))
        assertFalse(AlarmContinuationPolicy.shouldExpire(latest + 3_600_000, latest, true))
    }
    @Test fun onlyExpiredAndNotRingingAlarmsAreCleared() {
        val latest = instant("2026-10-12T07:00:00")
        assertFalse(AlarmContinuationPolicy.shouldExpire(latest - 1, latest, false))
        assertTrue(AlarmContinuationPolicy.shouldExpire(latest, latest, false))
    }
    @Test fun fixedNextDayWhenTimePassed() {
        val times = TimePlanner.next(AlarmMode.FIXED, 7*60, 0, instant("2026-10-12T08:00:00"), zone)
        assertEquals(instant("2026-10-13T07:00:00"), times.earliestMs)
        assertEquals(times.earliestMs, times.latestMs)
    }
    @Test fun smartWindowSameDay() {
        val times = TimePlanner.next(AlarmMode.SMART, 6*60+30, 7*60, instant("2026-10-12T01:00:00"), zone)
        assertEquals(instant("2026-10-12T06:30:00"), times.earliestMs)
        assertEquals(30*60*1000L, times.latestMs - times.earliestMs)
    }
    @Test fun windowCrossesMidnight() {
        val times = TimePlanner.next(AlarmMode.SMART, 23*60+45, 15, instant("2026-10-12T22:00:00"), zone)
        assertEquals(instant("2026-10-12T23:45:00"), times.earliestMs)
        assertEquals(instant("2026-10-13T00:15:00"), times.latestMs)
    }
    @Test(expected = IllegalArgumentException::class)
    fun excessivelyLongWindowRejected() {
        TimePlanner.next(AlarmMode.SMART, 6*60, 9*60, instant("2026-10-12T01:00:00"), zone)
    }
    @Test fun motionWithoutSignificantDeltasNeverTriggers() {
        val gate = MotionGate(0L)
        for (t in 0L..150_000L step 200) {
            assertFalse(gate.record(t, 0f, 0f, 9.81f))
        }
    }
    @Test fun sustainedStirringAfterWarmupTriggers() {
        val gate = MotionGate(0L)
        var seen = false
        for (t in 0L..160_000L step 200) {
            val x = if (t % 400L == 0L) 3f else -3f
            if (gate.record(t, x, 0f, 9.81f)) seen = true
        }
        assertTrue(seen)
    }
    @Test fun isolatedStirringDoesNotTrigger() {
        val gate = MotionGate(0L)
        for (t in 0L..150_000L step 200) {
            val x = if (t in 120_000L..130_000L && t % 400L == 0L) 3f else 0f
            gate.record(t, x, 0f, 9.81f)
        }
        assertFalse(gate.triggered)
    }
}
