package com.zennay.alarm

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/** A single watch-only foreground service. No REM claims, no NFC-reader assumption. */
class WakeService : Service(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private var gate: MotionGate? = null
    private var ringing = false
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        sensors = getSystemService(SensorManager::class.java)
        vibrator = if (Build.VERSION.SDK_INT >= 31)
            getSystemService(VibratorManager::class.java).defaultVibrator
        else {
            @Suppress("DEPRECATION")
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            v
        }
        val channel = NotificationChannel(CHANNEL, "Stille wekker", NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(null, null)
        channel.enableVibration(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun foreground(title: String, description: String) {
        val pending = PendingIntent.getActivity(
            this, 7, Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(description)
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .setOngoing(true)
            .build()
        startForeground(41, notification)
    }

    private fun hold(ms: Long) {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Alarm:awake").apply {
                acquire(ms.coerceIn(60_000, 95L * 60_000))
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MONITOR -> monitor()
            RING -> ring()
            COMPLETE -> if (ringing && AlarmStore.steps(this) >= MISSION_STEPS) finish(false)
            EMERGENCY -> finish(true)
            else -> when {
                AlarmStore.isRinging(this) -> ring()
                AlarmStore.isTracking(this) -> monitor()
                else -> stopSelf()
            }
        }
        return START_STICKY
    }

    private fun monitor() {
        val config = AlarmStore.read(this)
        if (!config.enabled) { stopSelf(); return }
        if (System.currentTimeMillis() >= config.latestMs) { ring(); return }
        foreground("Slim wekvenster", "Beweging volgen tot de uiterste wektijd")
        AlarmStore.setTracking(this)
        gate = MotionGate(config.earliestMs)
        hold(config.latestMs - System.currentTimeMillis() + 60_000)
        sensors.unregisterListener(this)
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null || !sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            Log.w("Alarm", "No accelerometer: latest alarm deadline is still scheduled")
        }
    }

    private fun ring() {
        if (ringing) return
        ringing = true
        sensors.unregisterListener(this)
        AlarmStore.setRinging(this, true)
        foreground("Tijd om op te staan", "Geen snooze; loop 20 stappen")
        hold(45L * 60_000)
        val waveform = longArrayOf(0, 700, 220, 700, 220, 700, 600)
        val effect = if (vibrator?.hasAmplitudeControl() == true)
            VibrationEffect.createWaveform(waveform, intArrayOf(0, 255, 0, 255, 0, 255, 0), 1)
        else VibrationEffect.createWaveform(waveform, 1)
        vibrator?.vibrate(effect)
        try {
            sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (denied: SecurityException) {
            Log.w("Alarm", "Activity recognition permission denied; use emergency stop", denied)
        }
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w("Alarm", "Fullscreen launch restricted; foreground notification still exists", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val now = System.currentTimeMillis()
                if (now >= AlarmStore.read(this).latestMs ||
                    gate?.record(now, event.values[0], event.values[1], event.values[2]) == true) ring()
            }
            Sensor.TYPE_STEP_DETECTOR -> if (ringing) AlarmStore.incrementSteps(this)
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun finish(emergency: Boolean) {
        AlarmScheduler.cancel(this)
        AlarmStore.setDisabled(this)
        vibrator?.cancel()
        if (!emergency) LightWebhook.trigger(AlarmStore.webhook(this))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        sensors.unregisterListener(this)
        vibrator?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "alarm_haptics"
        const val MISSION_STEPS = 20
        const val MONITOR = "com.zennay.alarm.MONITOR"
        const val RING = "com.zennay.alarm.RING"
        const val COMPLETE = "com.zennay.alarm.COMPLETE"
        const val EMERGENCY = "com.zennay.alarm.EMERGENCY"

        fun send(context: Context, action: String) {
            val intent = Intent(context, WakeService::class.java).setAction(action)
            if (action == COMPLETE || action == EMERGENCY) context.startService(intent)
            else context.startForegroundService(intent)
        }
    }
}

/** Optional bridge. Google Home has no automatic NFC-to-webhook integration. */
object LightWebhook {
    fun trigger(endpoint: String) {
        if (!endpoint.startsWith("https://", ignoreCase = true)) return
        Thread {
            try {
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 4_000
                conn.readTimeout = 4_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use {
                    it.write("""{"event":"alarm_dismissed","light":{"on":true,"brightness":100,"color":"white"}}""".toByteArray())
                }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                Log.w("AlarmLights", "Webhook failed", e)
            }
        }.start()
    }
}
