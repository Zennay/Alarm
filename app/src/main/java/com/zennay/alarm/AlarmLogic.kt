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
        val now = Instant.ofEpochMilli(nowMs)
        val day = now.atZone(zone).toLocalDate()
        // Fall-back clocks can present the same local minute twice. Compare
        // each valid offset on the instant timeline before advancing the day.
        val early = nextFutureOn(day, startMinute, zone, now)
            ?: nextFutureOn(day.plusDays(1), startMinute, zone, now)
            ?: error("No future alarm instant could be resolved")
        val late = if (mode == AlarmMode.FIXED) early else {
            // Offset on the instant timeline avoids a negative interval at the DST transition.
            early.plusMinutes(interval.toLong())
        }
        return AlarmTimes(early.toInstant().toEpochMilli(), late.toInstant().toEpochMilli())
    }

    private fun nextFutureOn(day: LocalDate, minuteOfDay: Int, zone: ZoneId, now: Instant): ZonedDateTime? {
        val local = LocalDateTime.of(
            day, java.time.LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        )
        val offsets = zone.rules.getValidOffsets(local)
        // Spring-forward gaps retain java.time's established shift-to-valid
        // behaviour. Ordinary dates have one offset; autumn overlaps have two.
        val occurrences = if (offsets.isEmpty()) listOf(local.atZone(zone))
            else offsets.map { ZonedDateTime.ofLocal(local, zone, it) }
        return occurrences.filter { it.toInstant().isAfter(now) }
            .minByOrNull { it.toInstant() }
    }
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
    var triggered = false
        private set

    fun record(timeMs: Long, x: Float, y: Float, z: Float): Boolean {
        if (triggered || timeMs < windowStartMs) return triggered
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
            activeBuckets = if (averageDelta > 0.65) activeBuckets + 1 else 0
            if (timeMs - windowStartMs >= 120_000 && activeBuckets >= 2) triggered = true
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
