package com.zennay.alarm

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Native, readable small-screen Wear OS UI: only one active alarm. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var modes: RadioGroup
    private lateinit var start: Button
    private lateinit var end: Button
    private lateinit var cancel: Button
    private lateinit var finish: Button
    private lateinit var emergency: Button
    private var from = 420
    private var until = 450
    private val handler = Handler(Looper.getMainLooper())
    private var emergencyTimer: Runnable? = null
    private val refreshTask = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AlarmStore.read(this)
        from = config.firstMinute
        until = config.lastMinute
        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 10)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 25)
            setBackgroundColor(Color.BLACK)
        }
        setContentView(ScrollView(this).apply { addView(content) })
        fun label(value: String, size: Float = 15f): TextView {
            val view = TextView(this).apply {
                text = value
                textSize = size
                setTextColor(Color.WHITE)
                setPadding(4, 9, 4, 9)
            }
            content.addView(view)
            return view
        }
        fun button(value: String, action: () -> Unit): Button {
            val view = Button(this).apply { text = value; isAllCaps = false }
            content.addView(view, LinearLayout.LayoutParams(-1, -2))
            view.setOnClickListener { action() }
            return view
        }
        label("Alarm", 24f)
        status = label("")
        modes = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            addView(RadioButton(this@MainActivity).apply { id = 1; text = "Vaste tijd" })
            addView(RadioButton(this@MainActivity).apply { id = 2; text = "Slim wekvenster" })
            check(if (config.mode == AlarmMode.FIXED) 1 else 2)
        }
        content.addView(modes)
        start = button("") {
            pick(from) { from = it; refresh() }
        }
        end = button("") {
            pick(until) { until = it; refresh() }
        }
        modes.setOnCheckedChangeListener { _, _ -> refresh() }

        button("Wekker instellen") {
            if (AlarmStore.isRinging(this) || AlarmStore.isTracking(this)) {
                Toast.makeText(this, "De actieve wekker moet eerst worden afgerond", Toast.LENGTH_SHORT).show()
                return@button
            }
            if (!AlarmScheduler.canSchedule(this)) {
                Toast.makeText(this, "Geef toestemming voor exacte alarmen", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                return@button
            }
            try {
                val mode = if (modes.checkedRadioButtonId == 2) AlarmMode.SMART else AlarmMode.FIXED
                val times = TimePlanner.next(mode, from, until, System.currentTimeMillis(), ZoneId.systemDefault())
                val next = AlarmConfig(mode, from, until, times.earliestMs, times.latestMs, true)
                AlarmScheduler.schedule(this, next)
                AlarmStore.save(this, next)
                refresh()
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Wekker instellen mislukt", Toast.LENGTH_LONG).show()
            }
        }

        cancel = button("Geplande wekker annuleren") {
            if (!AlarmStore.isRinging(this) && !AlarmStore.isTracking(this)) {
                AlarmScheduler.cancel(this)
                AlarmStore.setDisabled(this)
                refresh()
            }
        }
        finish = button("Ik ben opgestaan — stop wekker") {
            if (AlarmStore.isRinging(this) && AlarmStore.steps(this) >= WakeService.MISSION_STEPS) {
                WakeService.send(this, WakeService.COMPLETE)
                handler.postDelayed({ refresh() }, 700L)
            }
        }
        emergency = Button(this).apply { text = "Noodstop: 5 seconden vasthouden"; isAllCaps = false }
        content.addView(emergency)
        emergency.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    emergencyTimer = Runnable {
                        WakeService.send(this, WakeService.EMERGENCY)
                        Toast.makeText(this, "Noodstop", Toast.LENGTH_SHORT).show()
                        refresh()
                    }.also { handler.postDelayed(it, 5000L) }
                    true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    emergencyTimer?.let { handler.removeCallbacks(it) }
                    emergencyTimer = null
                    true
                }
                else -> true
            }
        }
        button("Test trilling (1 seconde)") {
            val v = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                val older = getSystemService(VIBRATOR_SERVICE) as Vibrator
                older
            }
            v.vibrate(VibrationEffect.createOneShot(1000L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        label("Badkamerverlichting • optionele HTTPS-webhook", 13f)
        val input = EditText(this).apply {
            hint = "https://automatisering/..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(AlarmStore.webhook(this@MainActivity))
        }
        content.addView(input)
        button("Bewaar lichtkoppeling") {
            val address = input.text.toString().trim()
            if (address.isNotEmpty() && !address.startsWith("https://")) {
                Toast.makeText(this, "Alleen HTTPS", Toast.LENGTH_SHORT).show()
            } else {
                AlarmStore.setWebhook(this, address)
                Toast.makeText(this, "Opgeslagen", Toast.LENGTH_SHORT).show()
            }
        }
        label("NFC-sticker uitlezen werkt niet aantoonbaar op Watch 7. De stappenopdracht bewijst geen badkamerlocatie.", 12f)
        refresh()
    }

    private fun pick(minutes: Int, result: (Int) -> Unit) {
        TimePickerDialog(this, { _, hour, minute -> result(hour * 60 + minute) },
            minutes / 60, minutes % 60, true).show()
    }
    private fun formattedTime(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
    private fun refresh() {
        if (!::status.isInitialized) return
        val smart = modes.checkedRadioButtonId == 2
        start.text = (if (smart) "Vanaf " else "Om ") + formattedTime(from)
        end.visibility = if (smart) View.VISIBLE else View.GONE
        end.text = "Uiterlijk " + formattedTime(until)
        val config = AlarmStore.read(this)
        val ringing = AlarmStore.isRinging(this)
        val tracking = AlarmStore.isTracking(this)
        val format = DateTimeFormatter.ofPattern("EEE d MMM HH:mm")
        status.text = when {
            ringing -> "TRILLEN • " + AlarmStore.steps(this) + "/" + WakeService.MISSION_STEPS + " stappen; loop naar badkamer"
            tracking -> "Slim wekvenster; uiterlijk " +
                Instant.ofEpochMilli(config.latestMs).atZone(ZoneId.systemDefault()).format(format)
            config.enabled -> "Gepland " +
                Instant.ofEpochMilli(config.earliestMs).atZone(ZoneId.systemDefault()).format(format)
            else -> "Geen wekker actief"
        }
        finish.visibility = if (ringing) View.VISIBLE else View.GONE
        finish.isEnabled = ringing && AlarmStore.steps(this) >= WakeService.MISSION_STEPS
        cancel.isEnabled = config.enabled && !ringing && !tracking
        emergency.visibility = if (ringing || tracking) View.VISIBLE else View.GONE
    }
    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }
    override fun onPause() {
        handler.removeCallbacks(refreshTask)
        emergencyTimer?.let { handler.removeCallbacks(it) }
        super.onPause()
    }
}
