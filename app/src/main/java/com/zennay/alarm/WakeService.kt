package com.zennay.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Watch-only wake service.
 * When ringing, there is exactly one app-level dismissal condition: the
 * operating system reports that the watch is receiving charge.
 * The alarm is vibration-only: this service never plays audio.
 *
 * No NFC tag, step count, light webhook, snooze or interactive stop command.
 * The OS can still force-stop the application; do not claim it is unkillable.
 */
class WakeService : Service(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private var gate: MotionGate? = null
    private var ringing = false
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var batteryReceiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    private val chargingConfirmation = ChargingConfirmation()
    private var lastChargingState: ChargingGate.State? = null

    /**
     * Always recheck the *current* system state. Broadcasts can be stale and
     * are never sufficient on their own to clear a ringing alarm.
     */
    private fun confirmCharging(): Boolean {
        if (!ringing || !AlarmStore.isRinging(this)) return false
        val state = try { ChargingGate.stateNow(this) } catch (error: Exception) {
            Log.w(TAG, "Battery status unavailable; keep alarming", error)
            chargingConfirmation.reset()
            return false
        }
        if (state != lastChargingState) {
            Log.i(TAG, "CHARGE_STATUS plugged=${state.plugged} status=${state.status} charging=${state.charging}")
            lastChargingState = state
        }
        if (chargingConfirmation.observe(state.charging, SystemClock.elapsedRealtime())) {
            // A verified charging signal only authorizes dismissal. Never
            // report it completed until the disabled state is durable.
            return dismissForCharging()
        }
        return false
    }

    // Some Wear OS builds cap a single long-running vibrator waveform.
    // Re-arm the requested haptics while the persisted alarm is still active.
    private val keepVibrating = object : Runnable {
        override fun run() {
            if (!ringing || !AlarmStore.isRinging(this@WakeService)) return
            if (confirmCharging()) return
            startVibration()
            handler.postDelayed(this, VIBRATION_REARM_MS)
        }
    }

    private val chargeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!ringing || !AlarmStore.isRinging(this@WakeService)) return
            if (intent.action == Intent.ACTION_BATTERY_CHANGED ||
                intent.action == Intent.ACTION_POWER_CONNECTED) confirmCharging()
        }
    }

    private val pollCharging = object : Runnable {
        override fun run() {
            if (!ringing || !AlarmStore.isRinging(this@WakeService)) return
            if (!confirmCharging()) handler.postDelayed(this, CHARGING_POLL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensors = getSystemService(SensorManager::class.java)
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            val previous = getSystemService(VIBRATOR_SERVICE) as Vibrator
            previous
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Trilwekker", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRACKING, "Slim wekvenster", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun foreground(title: String, description: String) {
        val pending = PendingIntent.getActivity(
            this, 7,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, if (ringing) CHANNEL else CHANNEL_TRACKING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(description)
            .setContentIntent(pending)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setDefaults(0)
            .setSound(null)
            .setOnlyAlertOnce(true)
        if (ringing) builder.setFullScreenIntent(pending, true)
        startForeground(NOTIFICATION_ID, builder.build())
    }

    private fun hold(ms: Long) {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Alarm:awake")
            .apply { acquire(ms.coerceIn(60_000L, 95L * 60_000)) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MONITOR -> monitor()
            RING -> ring()
            else -> when {
                AlarmStore.isRinging(this) -> ring()
                AlarmStore.isTracking(this) -> monitor()
                else -> stopSelf()
            }
        }
        return START_STICKY
    }

    private fun monitor() {
        if (ringing || AlarmStore.isRinging(this)) return
        val config = AlarmStore.read(this)
        if (!config.enabled) { stopSelf(); return }
        if (System.currentTimeMillis() >= config.latestMs) { ring(); return }
        foreground("Slim wekvenster", "Beweging volgen tot je uiterste wektijd")
        AlarmStore.setTracking(this)
        gate = MotionGate(config.earliestMs)
        hold(config.latestMs - System.currentTimeMillis() + 60_000)
        sensors.unregisterListener(this)
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null || !sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            Log.w(TAG, "No accelerometer: exact latest-time alarm still scheduled")
        }
    }

    private fun ring() {
        if (ringing || !AlarmStore.read(this).enabled) return
        ringing = true
        Log.i(TAG, "RING_START at=${System.currentTimeMillis()} service=${hashCode()}")
        chargingConfirmation.reset()
        lastChargingState = null
        sensors.unregisterListener(this)
        gate = null
        AlarmStore.setRinging(this, true)
        AlarmScheduler.scheduleRecovery(this)
        foreground("Tijd om op te staan", "Plaats je horloge op de oplader om te stoppen")
        hold(90L * 60_000)

        // A watch already charging at ring start is dismissed after a
        // short stable confirmation period, not one stale battery broadcast.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(chargeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else {
                @Suppress("DEPRECATION")
                registerReceiver(chargeReceiver, filter)
            }
            batteryReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Charging broadcast registration failed; polling remains", e)
        }
        // Keep the output running until a *continuous* charging signal is
        // confirmed, even when power status changes momentarily.
        confirmCharging()
        handler.removeCallbacks(pollCharging)
        handler.postDelayed(pollCharging, CHARGING_POLL_MS)

        startVibration()
        handler.removeCallbacks(keepVibrating)
        handler.postDelayed(keepVibrating, VIBRATION_REARM_MS)
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Fullscreen activity launch restricted; notification remains", e)
        }
    }

    private fun startVibration() {
        // Maximum *requested* strength (wearable firmware decides actual haptics).
        val pattern = longArrayOf(0, 700, 220, 700, 220, 700, 600)
        val effect = if (vibrator?.hasAmplitudeControl() == true)
            VibrationEffect.createWaveform(pattern, intArrayOf(0, 255, 0, 255, 0, 255, 0), 1)
        else VibrationEffect.createWaveform(pattern, 1)
        try {
            vibrator?.vibrate(effect)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot re-arm haptic alarm; will retry", e)
        }
    }

    private fun dismissForCharging(): Boolean {
        if (!ringing || !AlarmStore.isRinging(this)) return false
        // Preference commit can fail (for example during low-storage or OS
        // disruption). Cancelling haptics/recovery first would acknowledge an
        // alarm that is still persistently marked as ringing. Keep sounding
        // and retry while the true charger signal remains stable.
        if (!AlarmStore.setDisabled(this)) {
            Log.e(TAG, "Charging confirmed but alarm dismissal was not stored; retrying")
            return false
        }
        Log.i(TAG, "CHARGING_DETECTED: persisted dismissal of active alarm")
        ringing = false
        handler.removeCallbacks(pollCharging)
        handler.removeCallbacks(keepVibrating)
        // Once persistent state is disabled, an in-flight receiver refuses
        // to restart the old alarm even if OS PendingIntent cancellation fails.
        try {
            AlarmScheduler.cancel(this)
        } catch (failure: Exception) {
            Log.w(TAG, "Persisted dismissal succeeded; Android timer cleanup deferred", failure)
        }
        vibrator?.cancel()
        disarmChargingReceiver()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return true
    }

    private fun disarmChargingReceiver() {
        if (!batteryReceiverRegistered) return
        batteryReceiverRegistered = false
        try { unregisterReceiver(chargeReceiver) }
        catch (e: IllegalArgumentException) { Log.w(TAG, "Battery receiver already released", e) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || ringing) return
        val now = System.currentTimeMillis()
        if (now >= AlarmStore.read(this).latestMs ||
            gate?.record(now, event.values[0], event.values[1], event.values[2]) == true
        ) ring()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        Log.w(TAG, "SERVICE_DESTROYED ringing=${AlarmStore.isRinging(this)}")
        // An Android lifecycle teardown is NOT user dismissal. Keep a recovery
        // wakeup pending so the charge-only alarm can resume after service loss.
        if (AlarmStore.isRinging(this)) {
            try { AlarmScheduler.scheduleRecovery(this, 10_000L) }
            catch (e: Exception) { Log.e(TAG, "Alarm recovery scheduling failed", e) }
        }
        handler.removeCallbacks(pollCharging)
        handler.removeCallbacks(keepVibrating)
        disarmChargingReceiver()
        sensors.unregisterListener(this)
        vibrator?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmWakeService"
        private const val CHANNEL = "alarm_haptics_silent_v1"
        private const val CHANNEL_TRACKING = "alarm_tracking_silent_v1"
        private const val NOTIFICATION_ID = 41
        private const val CHARGING_POLL_MS = 500L
        private const val VIBRATION_REARM_MS = 4_000L
        const val MONITOR = "com.zennay.alarm.MONITOR"
        const val RING = "com.zennay.alarm.RING"

        fun send(context: Context, action: String) {
            context.startForegroundService(
                Intent(context, WakeService::class.java).setAction(action)
            )
        }
    }
}
