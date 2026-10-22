package com.zennay.alarm

import android.content.Context

data class AlarmConfig(
    val mode: AlarmMode,
    val firstMinute: Int,
    val lastMinute: Int,
    val earliestMs: Long,
    val latestMs: Long,
    val enabled: Boolean
)

/** Exact persisted state needed to roll back a partially armed alarm. */
data class AlarmStoreSnapshot(
    val config: AlarmConfig,
    val ringing: Boolean,
    val tracking: Boolean,
    val expiredUnconfirmed: Boolean
)

/** All state is local to the watch. One scheduled alarm, one shot. */
object AlarmStore {
    private const val PREF = "alarm"
    private fun pref(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun read(context: Context): AlarmConfig {
        val p = pref(context)
        return AlarmConfig(
            mode = if (p.getString("mode", "FIXED") == "SMART") AlarmMode.SMART else AlarmMode.FIXED,
            firstMinute = p.getInt("first", 7 * 60),
            lastMinute = p.getInt("last", 7 * 60 + 30),
            earliestMs = p.getLong("earlyMs", 0),
            latestMs = p.getLong("lateMs", 0),
            enabled = p.getBoolean("enabled", false)
        )
    }

    fun snapshot(context: Context): AlarmStoreSnapshot {
        val p = pref(context)
        return AlarmStoreSnapshot(
            config = read(context),
            ringing = p.getBoolean("ringing", false),
            tracking = p.getBoolean("tracking", false),
            expiredUnconfirmed = p.getBoolean("expiredUnconfirmed", false)
        )
    }

    /**
     * Restore the exact prior durable state after a scheduling or persistence
     * failure. Unlike save(), this must not erase a live ring/tracking state.
     */
    fun restore(context: Context, snapshot: AlarmStoreSnapshot): Boolean {
        val config = snapshot.config
        return pref(context).edit()
            .putString("mode", config.mode.name)
            .putInt("first", config.firstMinute)
            .putInt("last", config.lastMinute)
            .putLong("earlyMs", config.earliestMs)
            .putLong("lateMs", config.latestMs)
            .putBoolean("enabled", config.enabled)
            .putBoolean("ringing", snapshot.ringing)
            .putBoolean("tracking", snapshot.tracking)
            .putBoolean("expiredUnconfirmed", snapshot.expiredUnconfirmed)
            .commit()
    }

    /**
     * Persist the new schedule before its OS PendingIntents can fire. Keep
     * current ring/tracking flags intact until AlarmManager accepts the plan.
     */
    fun savePending(context: Context, config: AlarmConfig): Boolean =
        pref(context).edit()
            .putString("mode", config.mode.name)
            .putInt("first", config.firstMinute)
            .putInt("last", config.lastMinute)
            .putLong("earlyMs", config.earliestMs)
            .putLong("lateMs", config.latestMs)
            .putBoolean("enabled", config.enabled)
            .putBoolean("expiredUnconfirmed", false)
            .commit()

    /**
     * Commit completion only after the OS accepted the requested schedule.
     * savePending() already starts from an inactive state for ordinary setup.
     * Never write ringing/tracking here: a very-near START receiver may have
     * legitimately set either flag after the schedule was persisted.
     */
    fun markScheduled(context: Context): Boolean =
        pref(context).edit().putBoolean("expiredUnconfirmed", false).commit()

    fun save(context: Context, config: AlarmConfig): Boolean {
        return pref(context).edit()
            .putString("mode", config.mode.name)
            .putInt("first", config.firstMinute)
            .putInt("last", config.lastMinute)
            .putLong("earlyMs", config.earliestMs)
            .putLong("lateMs", config.latestMs)
            .putBoolean("enabled", config.enabled)
            .putBoolean("ringing", false).putBoolean("tracking", false)
            .putBoolean("expiredUnconfirmed", false)
            .commit() // Success means the one-shot metadata reached durable preferences.
    }

    fun setRinging(context: Context, value: Boolean) {
        pref(context).edit().putBoolean("ringing", value).putBoolean("tracking", !value).apply()
    }
    fun setTracking(context: Context) {
        pref(context).edit().putBoolean("tracking", true).putBoolean("ringing", false).apply()
    }
    fun isRinging(context: Context) = pref(context).getBoolean("ringing", false)
    fun isTracking(context: Context) = pref(context).getBoolean("tracking", false)
    /**
     * An elapsed one-shot found while no wake service is running cannot be
     * proven delivered after a cold reboot/clock correction. Preserve its
     * original scheduled instant for the user's next glance at the watch.
     * A new real alarm or explicit cancellation clears the warning.
     */
    fun expireUnconfirmed(context: Context): Boolean =
        pref(context).edit().putBoolean("enabled", false)
            .putBoolean("ringing", false).putBoolean("tracking", false)
            .putBoolean("expiredUnconfirmed", true).commit()

    fun hasUnconfirmedExpiry(context: Context): Boolean =
        pref(context).getBoolean("expiredUnconfirmed", false)

    /** Persist dismissal/cancellation state before claiming an alarm is off.
     * The Boolean is observed by user-triggered cancellation. A receiver
     * holding an obsolete PendingIntent also refuses disabled configs. */
    fun setDisabled(context: Context): Boolean =
        pref(context).edit().putBoolean("enabled", false)
            .putBoolean("ringing", false).putBoolean("tracking", false)
            .putBoolean("expiredUnconfirmed", false).commit()
}
