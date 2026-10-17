package com.zennay.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Independent boundary/forward-gap controls beyond the original four
 * unchanged expected-RED overlap cases from Alarm QA PR #2.
 * Pure java.time and JVM only; no Watch, network, permissions or system clock.
 */
class TimePlannerDstBoundaryAndGapTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val overlap = LocalDateTime.of(2026, 10, 25, 2, 30)

    private fun repeated(offsetHours: Int): ZonedDateTime =
        ZonedDateTime.ofLocal(overlap, zone, ZoneOffset.ofHours(offsetHours))

    @Test fun exactlyAtFirstRepeated0230SchedulesSecondSameDay() {
        val now = repeated(2).toInstant().toEpochMilli()
        val expected = repeated(1).toInstant().toEpochMilli()
        val plan = TimePlanner.next(AlarmMode.FIXED, 2 * 60 + 30, 0, now, zone)
        assertEquals(expected, plan.earliestMs)
        assertEquals(expected, plan.latestMs)
    }

    @Test fun exactlyAtSecondRepeated0230SchedulesTomorrow() {
        val now = repeated(1).toInstant().toEpochMilli()
        val tomorrow = LocalDateTime.of(2026, 10, 26, 2, 30).atZone(zone)
        val plan = TimePlanner.next(AlarmMode.FIXED, 2 * 60 + 30, 0, now, zone)
        assertEquals(tomorrow.toInstant().toEpochMilli(), plan.earliestMs)
    }

    @Test fun springForwardGapStillShiftsToFirstValidTimeAndPreservesSmartDuration() {
        val now = LocalDateTime.of(2027, 3, 28, 1, 55).atZone(zone)
        // 02:30 does not exist when Amsterdam moves to UTC+02.
        // Preserve java.time's prior gap resolution: 03:30 CEST.
        val shifted = LocalDateTime.of(2027, 3, 28, 2, 30).atZone(zone)
        val plan = TimePlanner.next(
            AlarmMode.SMART, 2 * 60 + 30, 2 * 60 + 45,
            now.toInstant().toEpochMilli(), zone
        )
        assertEquals(shifted.toInstant().toEpochMilli(), plan.earliestMs)
        assertEquals(15L * 60_000L, plan.latestMs - plan.earliestMs)
    }
}
