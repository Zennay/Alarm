package com.zennay.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Process
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * Dedicated alarm-speaker playback; never coupled to the notification sound.
 * Plays either a locally synthesized looping sound or a selected Android URI.
 *
 * The 0-100% volume control is relative to the system's ALARM stream volume;
 * we deliberately do not bypass the user's DND or audio safety controls.
 */
class AlarmAudioPlayer(private val context: Context) {
    private var track: AudioTrack? = null
    private var player: MediaPlayer? = null
    private var streamer: Thread? = null
    @Volatile private var generation = 0L

    fun start() {
        stop()
        val choice = SoundSettings.choice(context)
        val volume = SoundSettings.volumePercent(context) / 100f
        if (volume <= 0f) return
        val uri = when (choice) {
            AlarmSound.SYSTEM_RINGTONE ->
                SoundSettings.selectedUri(context, AlarmSound.SYSTEM_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            AlarmSound.CUSTOM_FILE -> SoundSettings.selectedUri(context, AlarmSound.CUSTOM_FILE)
            else -> null
        }
        if (uri != null) {
            try {
                val p = MediaPlayer()
                p.setAudioAttributes(attributes())
                p.setDataSource(context, uri)
                p.isLooping = true
                p.setVolume(volume, volume)
                p.prepare()
                p.start()
                player = p
                return
            } catch (e: Exception) {
                Log.w(TAG, "Selected sound failed; using built-in backup tone", e)
                try { player?.release() } catch (_: Exception) {}
                player = null
            }
        }
        // A removed custom file or unavailable system ringtone must never
        // leave the watch with a vibration-only alarm unexpectedly.
        startTone(
            if (choice == AlarmSound.SYSTEM_RINGTONE || choice == AlarmSound.CUSTOM_FILE)
                AlarmSound.DOUBLE_BEEP else choice,
            volume
        )
    }

    /**
     * A MODE_STATIC loop is not reliable on every Wear OS audio HAL: some
     * devices play its buffer only once. Stream PCM continuously instead.
     */
    fun isPlaying(): Boolean {
        if (SoundSettings.volumePercent(context) <= 0) return true
        player?.let { return try { it.isPlaying } catch (_: Exception) { false } }
        track?.let {
            return try {
                it.playState == AudioTrack.PLAYSTATE_PLAYING && streamer?.isAlive == true
            } catch (_: Exception) { false }
        }
        return false
    }

    fun stop() {
        generation++
        streamer?.interrupt()
        streamer = null
        player?.let {
            try { it.stop() } catch (_: IllegalStateException) {}
            try { it.release() } catch (_: Exception) {}
        }
        player = null
        track?.let {
            try { it.pause(); it.flush() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        track = null
    }

    private fun startTone(choice: AlarmSound, volume: Float) {
        try {
            val pcm = makeTone(choice)
            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBuffer > 0) { "Unsupported audio format: minBuffer=$minBuffer" }
            val t = AudioTrack.Builder()
                .setAudioAttributes(attributes())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuffer, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            check(t.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack not initialized" }
            t.setVolume(volume)
            track = t
            val session = generation
            val thread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                try {
                    while (generation == session && !Thread.currentThread().isInterrupted) {
                        var offset = 0
                        while (offset < pcm.size && generation == session) {
                            val written = t.write(
                                pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING
                            )
                            if (written <= 0)
                                throw IllegalStateException("AudioTrack stream write failed: $written")
                            offset += written
                        }
                    }
                } catch (e: Exception) {
                    if (generation == session) Log.e(TAG, "Continuous alarm audio failed", e)
                }
            }, "AlarmContinuousAudio")
            thread.isDaemon = true
            streamer = thread
            t.play()
            thread.start()
            Log.i(TAG, "Started continuous MODE_STREAM alarm audio")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot start alarm speaker, watch vibration remains active", e)
            stop()
        }
    }

    companion object {
        private const val TAG = "AlarmAudio"
        private const val SAMPLE_RATE = 22050
        private const val LOOP_SECONDS = 4

        private fun attributes(): AudioAttributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

        /** Pure Kotlin PCM pattern for three distinguishable user-selectable sounds. */
        fun makeTone(choice: AlarmSound): ShortArray {
            val total = SAMPLE_RATE * LOOP_SECONDS
            val result = ShortArray(total)
            var phase = 0.0
            for (i in 0 until total) {
                val seconds = i.toDouble() / SAMPLE_RATE
                val segment = seconds % LOOP_SECONDS
                val (frequency, enabled) = when (choice) {
                    AlarmSound.SIREN -> Pair(620.0 + 630.0 * (segment % 1.2) / 1.2, segment < 3.4)
                    AlarmSound.URGENT -> Pair(
                        if ((segment % 0.8) < 0.4) 1300.0 else 950.0,
                        segment < 3.3 && (segment % 0.8) < 0.55
                    )
                    else -> Pair(
                        if (segment < 0.65 || segment in 1.0..1.65) 980.0 else 720.0,
                        segment < 0.65 || segment in 1.0..1.65 || segment in 2.4..3.05
                    )
                }
                phase += 2.0 * PI * frequency / SAMPLE_RATE
                // Ease each pulse edge to avoid hard digital clicks.
                val envelope = if (enabled) {
                    val fade = if (choice == AlarmSound.SIREN) {
                        minOf(segment / 0.012, (3.4 - segment) / 0.012, 1.0)
                    } else {
                        val local = when (choice) {
                            AlarmSound.URGENT -> segment % 0.8
                            else -> when {
                                segment < 0.65 -> segment
                                segment < 1.65 -> segment - 1.0
                                else -> segment - 2.4
                            }
                        }
                        minOf(local / 0.012, (0.55 - local) / 0.012, 1.0).coerceIn(0.0, 1.0)
                    }
                    fade.coerceIn(0.0, 1.0)
                } else 0.0
                // Fundamental + harmonic for audibility on a small watch speaker,
                // capped below full scale to prevent PCM clipping.
                val sound = 0.62 * sin(phase) + 0.20 * sin(2 * phase)
                result[i] = (sound * envelope * 27500).toInt().coerceIn(-32768, 32767).toShort()
            }
            return result
        }
    }
}
