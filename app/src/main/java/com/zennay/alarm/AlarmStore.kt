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

    fun save(context: Context, config: AlarmConfig) {
        pref(context).edit()
            .putString("mode", config.mode.name)
            .putInt("first", config.firstMinute)
            .putInt("last", config.lastMinute)
            .putLong("earlyMs", config.earliestMs)
            .putLong("lateMs", config.latestMs)
            .putBoolean("enabled", config.enabled)
            .putBoolean("ringing", false).putBoolean("tracking", false).putInt("steps", 0)
            .apply()
    }

    fun setRinging(context: Context, value: Boolean) {
        pref(context).edit().putBoolean("ringing", value).putBoolean("tracking", !value).apply()
    }
    fun setTracking(context: Context) {
        pref(context).edit().putBoolean("tracking", true).putBoolean("ringing", false).apply()
    }
    fun isRinging(context: Context) = pref(context).getBoolean("ringing", false)
    fun isTracking(context: Context) = pref(context).getBoolean("tracking", false)
    fun steps(context: Context) = pref(context).getInt("steps", 0)
    fun incrementSteps(context: Context): Int {
        val next = steps(context) + 1
        pref(context).edit().putInt("steps", next).apply()
        return next
    }
    fun setDisabled(context: Context) {
        pref(context).edit().putBoolean("enabled", false)
            .putBoolean("ringing", false).putBoolean("tracking", false).apply()
    }
    /** Persist hash, not raw NFC tag UID. Enabled only after actual tag discovery. */
    fun nfcTagFingerprint(context: Context): String? =
        pref(context).getString("nfcTagFingerprint", null)
    fun setNfcTagFingerprint(context: Context, fingerprint: String) {
        require(fingerprint.length == 64 && fingerprint.all { it in '0'..'9' || it in 'a'..'f' })
        pref(context).edit().putString("nfcTagFingerprint", fingerprint).apply()
    }
    fun webhook(context: Context) = pref(context).getString("webhook", "") ?: ""
    fun setWebhook(context: Context, url: String) =
        pref(context).edit().putString("webhook", url).apply()
}
