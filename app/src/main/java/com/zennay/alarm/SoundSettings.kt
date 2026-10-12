package com.zennay.alarm

import android.content.Context
import android.net.Uri

/**
 * Independent of scheduled alarm state. User can configure a sound in advance.
 * A file/system choice requires a URI that Android can actually open.
 */
enum class AlarmSound {
    DOUBLE_BEEP,
    SIREN,
    URGENT,
    SYSTEM_RINGTONE,
    CUSTOM_FILE
}

object SoundSettings {
    private const val PREFS = "alarm_sound"
    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun choice(context: Context): AlarmSound {
        val raw = preferences(context).getString("choice", AlarmSound.DOUBLE_BEEP.name)
        return AlarmSound.entries.firstOrNull { it.name == raw } ?: AlarmSound.DOUBLE_BEEP
    }

    fun setChoice(context: Context, choice: AlarmSound) {
        preferences(context).edit().putString("choice", choice.name).apply()
    }

    fun volumePercent(context: Context) =
        preferences(context).getInt("volume", 100).coerceIn(0, 100)

    fun setVolumePercent(context: Context, volume: Int) {
        require(volume in 0..100)
        preferences(context).edit().putInt("volume", volume).apply()
    }

    fun selectedUri(context: Context, type: AlarmSound): Uri? {
        if (type != AlarmSound.CUSTOM_FILE && type != AlarmSound.SYSTEM_RINGTONE) return null
        val stored = preferences(context).getString("uri_${type.name}", null)
        return stored?.let(Uri::parse)
    }

    fun setSelectedUri(context: Context, type: AlarmSound, uri: Uri) {
        require(type == AlarmSound.CUSTOM_FILE || type == AlarmSound.SYSTEM_RINGTONE)
        require(uri.scheme == "content" || uri.scheme == "android.resource" || uri.scheme == "file")
        preferences(context).edit().putString("uri_${type.name}", uri.toString()).apply()
        setChoice(context, type)
    }
}
