package com.zennay.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * DST fall-back acceptance for the existing one-shot wake planner.
 *
 * The second 02:30 on the clock is a distinct future instant. A new alarm
 * scheduled after the first 02:30 but before the clock repeats must not be
 * silently deferred to tomorrow. This is deterministic; no wall clock,
 * Android device, permissions, network or system timezone are consulted.
 *
 * Kept in its own test file to avoid changing the active Wear OS app owner.
 */
class TimePlannerDstOverlapAcceptanceTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val repeatedLocalTime = LocalDateTime.of(2026, 10, 25, 2, 30)

    private fun offsetTime(hour: Int, minute: Int, offsetHours: Int): ZonedDateTime =
        ZonedDateTime.ofLocal(
            LocalDateTime.of(2026, 10, 25, hour, minute),
            zone,
            ZoneOffset.ofHours(offsetHours)
        )

    @Test fun firstOccurrenceStillChosenBeforeFirst0230() {
        val now = offsetTime(2, 20, 2)
        val first = ZonedDateTime.ofLocal(repeatedLocalTime, zone, ZoneOffset.ofHours(2))
        val plan = TimePlanner.next(AlarmMode.FIXED, 2 * 60 + 30, 0, now.toInstant().toEpochMilli(), zone)
        assertEquals(first.toInstant().toEpochMilli(), plan.earliestMs)
        assertEquals(plan.earliestMs, plan.latestMs)
    }

    @Test fun fixedAlarmChoosesSecond0230RatherThanTomorrow() {
        // 02:45 CEST is still BEFORE 02:30 CET on the instant timeline.
        val now = offsetTime(2, 45, 2)
        val second = ZonedDateTime.ofLocal(repeatedLocalTime, zone, ZoneOffset.ofHours(1))
        val plan = TimePlanner.next(AlarmMode.FIXED, 2 * 60 + 30, 0, now.toInstant().toEpochMilli(), zone)
        assertEquals("Do not miss the repeated same-night wake-up", second.toInstant().toEpochMilli(), plan.earliestMs)
        assertEquals(plan.earliestMs, plan.latestMs)
    }

    @Test fun smartWindowChoosesSecondOccurrenceAndKeepsElapsedWindow() {
        val now = offsetTime(2, 50, 2)
        val second = ZonedDateTime.ofLocal(repeatedLocalTime, zone, ZoneOffset.ofHours(1))
        val plan = TimePlanner.next(AlarmMode.SMART, 2 * 60 + 30, 2 * 60 + 45, now.toInstant().toEpochMilli(), zone)
        assertEquals("SMART must not skip the second 02:30", second.toInstant().toEpochMilli(), plan.earliestMs)
        assertEquals(15 * 60_000L, plan.latestMs - plan.earliestMs)
    }

    @Test fun tomorrowIsCorrectOnlyAfterBothOccurrencesPassed() {
        val now = offsetTime(2, 45, 1)
        val tomorrow = LocalDateTime.of(2026, 10, 26, 2, 30).atZone(zone)
        val plan = TimePlanner.next(AlarmMode.FIXED, 2 * 60 + 30, 0, now.toInstant().toEpochMilli(), zone)
        assertEquals(tomorrow.toInstant().toEpochMilli(), plan.earliestMs)
    }
}
