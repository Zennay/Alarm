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

    fun save(context: Context, config: AlarmConfig): Boolean {
        return pref(context).edit()
            .putString("mode", config.mode.name)
            .putInt("first", config.firstMinute)
            .putInt("last", config.lastMinute)
            .putLong("earlyMs", config.earliestMs)
            .putLong("lateMs", config.latestMs)
            .putBoolean("enabled", config.enabled)
            .putBoolean("ringing", false).putBoolean("tracking", false)
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
    fun setDisabled(context: Context) {
        pref(context).edit().putBoolean("enabled", false)
            .putBoolean("ringing", false).putBoolean("tracking", false).apply()
    }
}
