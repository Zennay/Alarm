package com.zennay.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

object AlarmScheduler {
    private fun alarmManager(c: Context) = c.getSystemService(AlarmManager::class.java)
    private fun intent(c: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, request, Intent(c, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    fun canSchedule(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager(c).canScheduleExactAlarms()

    private fun showIntent(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Replace the old START without cancelling it first; same PendingIntent
     * identity makes Android replace that one-shot atomically if accepted.
     * Keep the former deadline until the new start has been accepted. */
    private fun programFuture(c: Context, config: AlarmConfig) {
        val am = alarmManager(c)
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(config.earliestMs, showIntent(c)),
            intent(c, AlarmReceiver.START, 101)
        )
        if (config.mode == AlarmMode.SMART) {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, config.latestMs,
                intent(c, AlarmReceiver.DEADLINE, 102)
            )
        } else {
            am.cancel(intent(c, AlarmReceiver.DEADLINE, 102))
        }
    }

    /** Reconstitute a persisted, still-upcoming alarm after a partial failure.
     * An already-started smart window keeps its remaining hard deadline;
     * an expired one-shot must not spontaneously ring after recovery. */
    private fun restorePrevious(c: Context, previous: AlarmConfig): Boolean {
        if (!previous.enabled || !canSchedule(c)) return false
        val now = System.currentTimeMillis()
        return try {
            when {
                previous.earliestMs > now -> programFuture(c, previous)
                previous.mode == AlarmMode.SMART && previous.latestMs > now -> {
                    val am = alarmManager(c)
                    am.cancel(intent(c, AlarmReceiver.START, 101))
                    restoreDeadline(c, previous.latestMs)
                }
                else -> return false
            }
            true
        } catch (failure: Exception) {
            Log.e("AlarmScheduler", "Previous alarm could not be restored", failure)
            false
        }
    }

    /** Arm an exact alarm without first destroying the previous pending alarm.
     *
     * The user-visible persisted config is replaced only by armAndSave().
     * If Android accepts START but rejects DEADLINE, explicitly try to
     * restore the previous pending alarm rather than silently losing it.
     */
    fun schedule(c: Context, config: AlarmConfig) {
        require(config.enabled && config.earliestMs > System.currentTimeMillis())
        check(canSchedule(c)) { "Geef de app toestemming voor exacte alarmen." }
        val previous = AlarmStore.read(c)
        try {
            programFuture(c, config)
        } catch (failure: Exception) {
            if (!restorePrevious(c, previous)) {
                // Do not leave the old 'enabled' label displayed when neither
                // prior nor new scheduling is known to be safely armed.
                if (!AlarmStore.isRinging(c) && !AlarmStore.isTracking(c)) {
                    val am = alarmManager(c)
                    am.cancel(intent(c, AlarmReceiver.START, 101))
                    am.cancel(intent(c, AlarmReceiver.DEADLINE, 102))
                    AlarmStore.setDisabled(c)
                }
                throw IllegalStateException(
                    "Wekker instellen mislukt; controleer en stel je wekker opnieuw in."
                )
            }
            throw IllegalStateException(
                "Nieuwe planning mislukt; vorige wekker is opnieuw ingesteld."
            )
        }
    }

    /** Persist the new alarm before claiming success in the watch UI.
     * On a storage failure, re-arm the previously saved schedule. */
    fun armAndSave(c: Context, config: AlarmConfig) {
        val previous = AlarmStore.read(c)
        schedule(c, config)
        if (!AlarmStore.save(c, config)) {
            if (!restorePrevious(c, previous)) {
                if (!AlarmStore.isRinging(c) && !AlarmStore.isTracking(c)) {
                    val am = alarmManager(c)
                    am.cancel(intent(c, AlarmReceiver.START, 101))
                    am.cancel(intent(c, AlarmReceiver.DEADLINE, 102))
                    AlarmStore.setDisabled(c)
                }
                throw IllegalStateException(
                    "Wekker niet veilig opgeslagen; stel hem opnieuw in."
                )
            }
            throw IllegalStateException(
                "Nieuwe wekker niet opgeslagen; vorige planning hersteld."
            )
        }
    }

    /** After reboot during a smart window, restore *deadline*, never alarm immediately. */
    fun restoreDeadline(c: Context, latestMs: Long) {
        if (latestMs <= System.currentTimeMillis() || !canSchedule(c)) return
        alarmManager(c).setAlarmClock(
            AlarmManager.AlarmClockInfo(latestMs, PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )),
            intent(c, AlarmReceiver.DEADLINE, 102)
        )
    }

    /**
     * While ringing, an independent RTC/elapsed-time wakeup restarts playback
     * if Wear OS kills the foreground service. Rescheduled by the receiver,
     * and cancelled ONLY after genuine charger dismissal.
     *
     * setAndAllowWhileIdle is the fallback when exact alarms are unavailable.
     */
    fun scheduleRecovery(c: Context, delayMs: Long = 30_000L) {
        if (!AlarmStore.isRinging(c)) return
        val am = alarmManager(c)
        val at = SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(10_000L)
        val pending = intent(c, AlarmReceiver.RECOVER, 103)
        try {
            try {
                if (canSchedule(c)) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
                }
            } catch (e: SecurityException) {
                Log.w("AlarmRecovery", "Exact wakeup denied, trying non-exact recovery", e)
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            }
        } catch (e: Exception) {
            // Recovery is best-effort. A watchdog failure must NEVER abort
            // the real alarm service or its continuous playback.
            Log.e("AlarmRecovery", "Cannot schedule rescue alarm", e)
        }
    }

    fun cancelRecovery(c: Context) {
        alarmManager(c).cancel(intent(c, AlarmReceiver.RECOVER, 103))
    }

    fun cancel(c: Context) {
        cancelRecovery(c)
        val am = alarmManager(c)
        am.cancel(intent(c, AlarmReceiver.START, 101))
        am.cancel(intent(c, AlarmReceiver.DEADLINE, 102))
    }
}
