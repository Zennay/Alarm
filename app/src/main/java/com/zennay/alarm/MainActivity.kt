package com.zennay.alarm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Watch-first AMOLED UI. No dismissal button: only charging stops a ringing alarm. */
class MainActivity : Activity() {
    private val ink = Color.rgb(7, 12, 19)
    private val card = Color.rgb(20, 31, 44)
    private val mint = Color.rgb(113, 239, 206)
    private val white = Color.WHITE
    private val muted = Color.rgb(155, 176, 188)
    private val warn = Color.rgb(255, 178, 120)

    private lateinit var heroTime: TextView
    private lateinit var heroCaption: TextView
    private lateinit var status: TextView
    private lateinit var fixedTab: TextView
    private lateinit var smartTab: TextView
    private lateinit var timePrimary: TextView
    private lateinit var timeSecondary: TextView
    private lateinit var windowGroup: View
    private lateinit var settings: View
    private lateinit var ringingPane: View
    private lateinit var chargingStatus: TextView
    private lateinit var cancel: TextView
    private var from = 7 * 60
    private var until = 7 * 60 + 30
    private var selectedMode = AlarmMode.FIXED
    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            if (!isFinishing) {
                refresh()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = AlarmStore.read(this)
        from = saved.firstMinute
        until = saved.lastMinute
        selectedMode = saved.mode
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
        }
        drawLayout()
        refresh()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value + 0.5f).toInt()
    private fun shape(fill: Int, radius: Int = 25, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun text(label: String, size: Float = 14f, color: Int = white, bold: Boolean = false) =
        TextView(this).apply {
            this.text = label
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            typeface = Typeface.create(
                if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL
            )
        }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun add(parent: LinearLayout, child: View) {
        parent.addView(child, LinearLayout.LayoutParams(-1, -2))
    }
    private fun gap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }
    private fun pill(label: String, fill: Int, color: Int, border: Int? = null) =
        text(label, 15f, color, true).apply {
            background = shape(fill, 25, border)
            minHeight = dp(48)
            setPadding(dp(10), dp(13), dp(10), dp(13))
        }
    private fun title(label: String) =
        text(label, 11f, muted, true).apply { letterSpacing = .13f }

    private fun drawLayout() {
        window.decorView.setBackgroundColor(ink)
        val root = column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(18), dp(26), dp(35))
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(root)
        })

        add(root, title("ALARM  /  WATCH 7"))
        gap(root, 8)
        heroTime = text("07:00", 49f, white, true).apply { letterSpacing = -0.05f }
        add(root, heroTime)
        heroCaption = text("Jouw rustige start", 13f, muted)
        add(root, heroCaption)
        gap(root, 14)
        status = text("", 12f, mint, true).apply {
            background = shape(card, 24)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        add(root, status)
        gap(root, 23)

        settings = column().also { add(root, it) }
        val settingsColumn = settings as LinearLayout
        add(settingsColumn, title("KIES JE WEKKER"))
        gap(settingsColumn, 9)
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = shape(card)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        fixedTab = pill("Vaste tijd", card, white)
        smartTab = pill("Slim venster", card, white)
        tabs.addView(fixedTab, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(smartTab, LinearLayout.LayoutParams(0, -2, 1f))
        add(settingsColumn, tabs)
        fixedTab.setOnClickListener { selectedMode = AlarmMode.FIXED; refresh() }
        smartTab.setOnClickListener { selectedMode = AlarmMode.SMART; refresh() }
        gap(settingsColumn, 18)

        add(settingsColumn, title("WEKTIJD"))
        gap(settingsColumn, 7)
        timePrimary = pill("07:00", card, mint, muted).apply { textSize = 26f }
        add(settingsColumn, timePrimary)
        timePrimary.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                from = hour * 60 + minute
                refresh()
            }, from / 60, from % 60, true).show()
        }
        gap(settingsColumn, 12)

        val window = column()
        windowGroup = window
        add(settingsColumn, window)
        add(window, title("UITERLIJK"))
        gap(window, 7)
        timeSecondary = pill("07:30", card, mint, muted).apply { textSize = 26f }
        add(window, timeSecondary)
        timeSecondary.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                until = hour * 60 + minute
                refresh()
            }, until / 60, until % 60, true).show()
        }
        gap(window, 8)
        add(window, text("Beweging kan eerder wekken; de eindtijd staat vast.", 12f, muted))
        gap(settingsColumn, 16)

        gap(settingsColumn, 13)
        add(settingsColumn, text(
            "STILLE WEKKER: alleen trillingen, nooit geluid.",
            12f, mint, true
        ))
        gap(settingsColumn, 14)

        val save = pill("Wekker instellen", mint, ink)
        add(settingsColumn, save)
        save.setOnClickListener { saveAlarm() }
        gap(settingsColumn, 9)
        cancel = pill("Geplande wekker annuleren", card, warn)
        add(settingsColumn, cancel)
        cancel.setOnClickListener {
            if (!AlarmStore.isRinging(this) && !AlarmStore.isTracking(this)) {
                AlarmScheduler.cancel(this)
                AlarmStore.setDisabled(this)
                refresh()
            }
        }
        gap(settingsColumn, 18)

        ringingPane = column().also { add(root, it) }
        val active = ringingPane as LinearLayout
        val icon = text("⌁", 45f, mint, true)
        add(active, icon)
        add(active, text("LEG JE WATCH\nOP DE OPLADER", 19f, white, true))
        gap(active, 13)
        chargingStatus = text("Wachten op oplader…", 13f, mint, true).apply {
            background = shape(card, 24)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        add(active, chargingStatus)
        gap(active, 10)
        add(active, text("Geen snooze. Trillingen stoppen vanzelf zodra opladen begint.", 12f, muted))
        gap(root, 15)

        val check = pill("Controleer oplader", card, mint)
        add(root, check)
        check.setOnClickListener {
            val connected = ChargingGate.isChargingNow(this)
            Toast.makeText(
                this,
                if (connected) "Opladen gedetecteerd ✓" else "Nog geen opladen gedetecteerd",
                Toast.LENGTH_LONG
            ).show()
        }
        gap(root, 8)
        val test = pill("Trilmotor testen (1 sec)", card, muted)
        add(root, test)
        test.setOnClickListener {
            val motor = if (Build.VERSION.SDK_INT >= 31)
                getSystemService(VibratorManager::class.java).defaultVibrator
            else {
                @Suppress("DEPRECATION")
                val older = getSystemService(VIBRATOR_SERVICE) as Vibrator
                older
            }
            motor.vibrate(VibrationEffect.createOneShot(1000L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        add(root, text("Dit is alleen een korte test van de trilmotor.", 10f, muted))
        gap(root, 8)
        val trial = pill("Volledige wekker testen (continu)", card, mint)
        add(root, trial)
        trial.setOnClickListener {
            if (AlarmStore.isRinging(this) || AlarmStore.isTracking(this)) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Wekker testen?")
                .setMessage("De wekker trilt zonder geluid totdat je horloge oplaadt. Houd de oplader bij de hand.")
                .setNegativeButton("Terug", null)
                .setPositiveButton("Start test") { _, _ ->
                    val now = System.currentTimeMillis()
                    AlarmScheduler.cancel(this)
                    AlarmStore.save(this, AlarmConfig(AlarmMode.FIXED, from, until,
                        now, now, true))
                    WakeService.send(this, WakeService.RING)
                    handler.postDelayed({ refresh() }, 500)
                }.show()
        }
    }

    private fun saveAlarm() {
        if (AlarmStore.isRinging(this) || AlarmStore.isTracking(this)) {
            Toast.makeText(this, "Wacht tot je horloge oplaadt.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!AlarmScheduler.canSchedule(this)) {
            Toast.makeText(this, "Sta exacte alarmen toe", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            return
        }
        try {
            val times = TimePlanner.next(selectedMode, from, until,
                System.currentTimeMillis(), ZoneId.systemDefault())
            val next = AlarmConfig(selectedMode, from, until,
                times.earliestMs, times.latestMs, true)
            AlarmScheduler.schedule(this, next)
            AlarmStore.save(this, next)
            refresh()
            Toast.makeText(this, "Wekker ingesteld ✓", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Kon wekker niet instellen", Toast.LENGTH_LONG).show()
        }
    }

    private fun formatMinute(min: Int) = "%02d:%02d".format(min / 60, min % 60)

    // Show the actual scheduled INSTANT, including its UTC offset. On the
    // autumn clock rollback both 02:30+02:00 and 02:30+01:00 exist, so
    // the wall-clock digits alone are not enough for a trustworthy preview.
    private fun plannedAlarmSummary(c: AlarmConfig): String {
        val zone = ZoneId.systemDefault()
        val display = DateTimeFormatter.ofPattern("EEE d MMM HH:mm XXX")
        val earliest = Instant.ofEpochMilli(c.earliestMs).atZone(zone).format(display)
        if (c.mode != AlarmMode.SMART) return "✓  Gepland $earliest"
        val latest = Instant.ofEpochMilli(c.latestMs).atZone(zone).format(display)
        return "✓  Slim vanaf $earliest\nUiterlijk $latest"
    }
    private fun refresh() {
        if (!::status.isInitialized) return
        val c = AlarmStore.read(this)
        val ringing = AlarmStore.isRinging(this)
        val tracking = AlarmStore.isTracking(this)
        val smart = selectedMode == AlarmMode.SMART
        fixedTab.background = shape(if (smart) card else mint, 24)
        smartTab.background = shape(if (smart) mint else card, 24)
        fixedTab.setTextColor(if (smart) muted else ink)
        smartTab.setTextColor(if (smart) ink else muted)
        timePrimary.text = formatMinute(from)
        timeSecondary.text = formatMinute(until)
        windowGroup.visibility = if (smart) View.VISIBLE else View.GONE
        heroTime.text = if (ringing) "OPSTAAN" else formatMinute(from)
        heroTime.textSize = if (ringing) 32f else 49f
        heroCaption.text = when {
            ringing -> "Stoppen kan alleen op de oplader"
            tracking -> "Zoeken naar het juiste wekmoment"
            smart -> "Wakker worden binnen je tijdvak"
            else -> "Alleen trillen, geen snooze"
        }
        status.text = when {
            ringing -> "●  WEKKER ACTIEF"
            tracking -> "●  SLIM WEKVENSTER ACTIEF"
            c.enabled -> plannedAlarmSummary(c)
            else -> "●  GEEN WEKKER GEPLAND"
        }
        status.setTextColor(if (ringing) warn else mint)
        settings.visibility = if (ringing || tracking) View.GONE else View.VISIBLE
        ringingPane.visibility = if (ringing) View.VISIBLE else View.GONE
        if (ringing) {
            chargingStatus.text = if (ChargingGate.isChargingNow(this))
                "Opladen gedetecteerd ✓" else "Wachten op oplader…"
        }
        cancel.visibility = if (c.enabled && !ringing && !tracking) View.VISIBLE else View.GONE
    }


    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresher)
        handler.post(refresher)
    }
    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }
}
