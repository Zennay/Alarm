package com.zennay.alarm

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class AlarmMode { FIXED, SMART }
data class AlarmTimes(val earliestMs: Long, val latestMs: Long)

/**
 * No inferred 90-minute REM cycles. Smart mode is a bounded wrist-movement heuristic.
 * Next occurrence is one-shot, with a hard final alarm time.
 */
object TimePlanner {
    fun next(mode: AlarmMode, startMinute: Int, endMinute: Int, nowMs: Long, zone: ZoneId): AlarmTimes {
        require(startMinute in 0..1439 && endMinute in 0..1439)
        val interval = (endMinute - startMinute + 1440) % 1440
        if (mode == AlarmMode.SMART) require(interval in 5..90) { "Kies een wekvenster van 5 tot 90 minuten." }
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        // A civil time can happen twice on autumn DST rollback. Select the
        // earliest *future instant*, not always the earlier UTC offset.
        val early = nextLocalOccurrence(now.toLocalDate(), startMinute, now, zone)
        val late = if (mode == AlarmMode.FIXED) early else {
            // Offset on the instant timeline avoids a negative interval at the DST transition.
            early.plusMinutes(interval.toLong())
        }
        return AlarmTimes(early.toInstant().toEpochMilli(), late.toInstant().toEpochMilli())
    }

    private fun nextLocalOccurrence(
        day: LocalDate, minuteOfDay: Int, now: ZonedDateTime, zone: ZoneId
    ): ZonedDateTime {
        val local = LocalDateTime.of(
            day, java.time.LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        )
        // ZoneRules gives BOTH legal offsets when clocks fall back. For a
        // spring-forward gap there is no legal offset: retain Java's existing
        // atZone gap resolution rather than silently skipping the alarm.
        val offsets = zone.rules.getValidOffsets(local)
        val today = if (offsets.isEmpty()) listOf(local.atZone(zone)) else
            offsets.map { offset -> ZonedDateTime.ofLocal(local, zone, offset) }
        return today.filter { it.isAfter(now) }
            .minByOrNull { it.toInstant() }
            ?: localAt(day.plusDays(1), minuteOfDay, zone)
    }

    private fun localAt(day: LocalDate, minuteOfDay: Int, zone: ZoneId): ZonedDateTime =
        LocalDateTime.of(day, java.time.LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)).atZone(zone)
}

/**
 * Not a REM classifier. Only a conservative movement-based proxy for probable stirring.
 * Uses acceleration-vector deltas rather than raw gravity; 2 consecutive 15s active
 * buckets inside a window, after a 2 minute warm-up. Sensor noise / turns can fool it.
 */
class MotionGate(private val windowStartMs: Long) {
    private var px = 0f
    private var py = 0f
    private var pz = 0f
    private var prev = false
    private var bucketStart = -1L
    private var total = 0.0
    private var count = 0
    private var activeBuckets = 0
    private var lastSampleMs = -1L
    var triggered = false
        private set

    fun record(timeMs: Long, x: Float, y: Float, z: Float): Boolean {
        if (triggered || timeMs < windowStartMs) return triggered
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
            // Corrupt sensor samples must never be mistaken for movement.
            // Require two fresh, complete buckets before an early wake.
            prev = false
            bucketStart = -1L
            lastSampleMs = -1L
            activeBuckets = 0
            total = 0.0
            count = 0
            return false
        }
        // Sensor callbacks can pause when Wear OS throttles background work.
        // Two 'consecutive' buckets separated by a long gap are not evidence
        // of continuous stirring. A clock jump backwards is equally stale.
        if (lastSampleMs >= 0L &&
            (timeMs < lastSampleMs || timeMs - lastSampleMs > 5_000L)) {
            prev = false
            bucketStart = -1L
            activeBuckets = 0
            total = 0.0
            count = 0
        }
        lastSampleMs = timeMs
        if (bucketStart == -1L) bucketStart = timeMs
        if (prev && timeMs - bucketStart < 15_000) {
            val dx = (x - px).toDouble()
            val dy = (y - py).toDouble()
            val dz = (z - pz).toDouble()
            total += kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtMost(10.0)
            count++
        }
        if (timeMs - bucketStart >= 15_000) {
            val averageDelta = if (count > 0) total / count else 0.0
            // Two complete 15-second buckets must START after the two-minute
            // warm-up. Before this fix an active bucket from minute 1:45
            // could count toward the early wake exactly at minute 2:00.
            val afterWarmup = bucketStart - windowStartMs >= 120_000L
            // One isolated accelerometer spike in a sparsely sampled 15s
            // interval is not sustained stirring. Require enough real vector
            // deltas before accepting each of the two motion buckets.
            activeBuckets = if (afterWarmup && count >= 4 && averageDelta > 0.65)
                activeBuckets + 1 else 0
            if (activeBuckets >= 2) triggered = true
            bucketStart = timeMs
            total = 0.0
            count = 0
        }
        px = x; py = y; pz = z; prev = true
        return triggered
    }
}

/**
 * Clock changes are never a dismissal event. The scheduled deadline may have
 * passed, but an alarm that is already ringing still requires real charging.
 */
object AlarmContinuationPolicy {
    fun shouldExpire(nowMs: Long, latestMs: Long, alreadyRinging: Boolean): Boolean =
        nowMs >= latestMs && !alreadyRinging
}
