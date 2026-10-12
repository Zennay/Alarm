package com.zennay.alarm

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Dark, watch-first screen. Native views avoid extra Compose dependencies and
 * fit the round Galaxy Watch screen; all core features work offline.
 */
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
    private lateinit var settingGroup: View
    private lateinit var ringingGroup: View
    private lateinit var action: TextView
    private lateinit var cancel: TextView
    private lateinit var emergency: TextView
    private lateinit var mission: TextView
    private var from = 7 * 60
    private var until = 7 * 60 + 30
    private var selectedMode = AlarmMode.FIXED
    private val handler = Handler(Looper.getMainLooper())
    private var emergencyTimer: Runnable? = null
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
        requestNeededPermissions()
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

    private fun text(
        label: String,
        size: Float = 14f,
        color: Int = white,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        this.text = label
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        includeFontPadding = true
    }

    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun gap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }
    private fun add(parent: LinearLayout, child: View, minHeight: Int = 0) {
        if (minHeight > 0) child.minimumHeight = dp(minHeight)
        parent.addView(child, LinearLayout.LayoutParams(-1, -2))
    }
    private fun pill(label: String, fill: Int, color: Int = white, outline: Int? = null): TextView =
        text(label, 15f, color, true).apply {
            background = shape(fill, 26, outline)
            minHeight = dp(48)
            setPadding(dp(10), dp(12), dp(10), dp(12))
        }
    private fun smallTitle(label: String): TextView =
        text(label, 11f, muted, true).apply {
            letterSpacing = 0.13f
            gravity = Gravity.CENTER
        }

    private fun drawLayout() {
        window.decorView.setBackgroundColor(ink)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        val page = column().apply {
            setPadding(dp(26), dp(18), dp(26), dp(32))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(page)
        setContentView(scroll)

        add(page, smallTitle("ALARM  /  WATCH 7"))
        gap(page, 9)
        heroTime = text("07:00", 50f, white, true).apply {
            letterSpacing = -0.05f
        }
        add(page, heroTime)
        heroCaption = text("Jouw rustige start", 13f, muted)
        add(page, heroCaption)
        gap(page, 10)
        status = text("", 12f, mint, true).apply {
            background = shape(card, 20)
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        add(page, status, 38)
        gap(page, 22)

        settingGroup = column()
        add(page, settingGroup)

        add(settingGroup as LinearLayout, smallTitle("KIES JE WEKKER"))
        gap(settingGroup as LinearLayout, 10)
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = shape(card, 28)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        fixedTab = pill("Vaste tijd", card, white)
        smartTab = pill("Slim venster", card, white)
        tabs.addView(fixedTab, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(smartTab, LinearLayout.LayoutParams(0, -2, 1f))
        add(settingGroup as LinearLayout, tabs)
        fixedTab.setOnClickListener { selectedMode = AlarmMode.FIXED; refresh() }
        smartTab.setOnClickListener { selectedMode = AlarmMode.SMART; refresh() }
        gap(settingGroup as LinearLayout, 19)

        add(settingGroup as LinearLayout, smallTitle("BEGIN"))
        gap(settingGroup as LinearLayout, 7)
        timePrimary = pill("07:00", card, mint, muted)
        timePrimary.textSize = 25f
        add(settingGroup as LinearLayout, timePrimary, 56)
        timePrimary.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                from = hour * 60 + minute
                refresh()
            }, from / 60, from % 60, true).show()
        }
        gap(settingGroup as LinearLayout, 13)

        val windowContainer = column()
        windowGroup = windowContainer
        add(settingGroup as LinearLayout, windowContainer)
        add(windowContainer, smallTitle("UITERLIJK"))
        gap(windowContainer, 7)
        timeSecondary = pill("07:30", card, mint, muted)
        timeSecondary.textSize = 25f
        add(windowContainer, timeSecondary, 56)
        timeSecondary.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                until = hour * 60 + minute
                refresh()
            }, until / 60, until % 60, true).show()
        }
        gap(windowContainer, 10)
        add(windowContainer, text("Beweging kan je eerder wekken. De eindtijd blijft vast.", 12f, muted))
        gap(settingGroup as LinearLayout, 16)

        action = pill("Wekker instellen", mint, ink)
        add(settingGroup as LinearLayout, action, 53)
        action.setOnClickListener { saveAlarm() }
        gap(settingGroup as LinearLayout, 10)
        cancel = pill("Annuleer geplande wekker", card, warn)
        add(settingGroup as LinearLayout, cancel)
        cancel.setOnClickListener {
            if (!AlarmStore.isRinging(this) && !AlarmStore.isTracking(this)) {
                AlarmScheduler.cancel(this)
                AlarmStore.setDisabled(this)
                refresh()
            }
        }
        gap(settingGroup as LinearLayout, 18)

        ringingGroup = column()
        add(page, ringingGroup)
        mission = text("", 20f, mint, true)
        add(ringingGroup as LinearLayout, mission)
        gap(ringingGroup as LinearLayout, 12)
        val finish = pill("Ik ben opgestaan", mint, ink)
        add(ringingGroup as LinearLayout, finish)
        finish.setOnClickListener {
            if (AlarmStore.isRinging(this) && AlarmStore.steps(this) >= WakeService.MISSION_STEPS) {
                WakeService.send(this, WakeService.COMPLETE)
                handler.postDelayed({ refresh() }, 450)
            }
        }
        // One long-press override is essential if Android denies step detection.
        emergency = pill("Noodstop · 5 sec vasthouden", card, warn)
        add(page, emergency)
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
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    emergencyTimer?.let { handler.removeCallbacks(it) }
                    emergencyTimer = null
                    true
                }
                else -> true
            }
        }
        gap(page, 14)
        val testVibration = pill("Test trillingen", card, muted)
        add(page, testVibration)
        testVibration.setOnClickListener {
            val motor = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                val old = getSystemService(VIBRATOR_SERVICE) as Vibrator
                old
            }
            motor.vibrate(VibrationEffect.createOneShot(1000L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        gap(page, 10)
        val nfc = pill("NFC-sticker testen  →", card, mint)
        add(page, nfc)
        nfc.setOnClickListener {
            startActivity(Intent(this, NfcProbeActivity::class.java))
        }
        gap(page, 20)

        val advanced = column()
        add(page, advanced)
        val advancedToggle = pill("Lampen & instellingen  ▾", card, muted)
        add(advanced, advancedToggle)
        val advancedBody = column()
        add(advanced, advancedBody)
        advancedBody.visibility = View.GONE
        advancedToggle.setOnClickListener {
            advancedBody.visibility = if (advancedBody.visibility == View.GONE) View.VISIBLE else View.GONE
            advancedToggle.text = if (advancedBody.visibility == View.VISIBLE)
                "Lampen & instellingen  ▴" else "Lampen & instellingen  ▾"
        }
        gap(advancedBody, 10)
        add(advancedBody, text("Licht aan na opstaan: optionele HTTPS-automatisering", 12f, muted))
        val urlInput = EditText(this).apply {
            hint = "https://..."
            textSize = 13f
            setTextColor(white)
            setHintTextColor(muted)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(AlarmStore.webhook(this@MainActivity))
            background = shape(card, 17, muted)
            setPadding(dp(12), dp(11), dp(12), dp(11))
        }
        add(advancedBody, urlInput, 48)
        gap(advancedBody, 8)
        val saveLights = pill("Bewaar koppeling", card, mint)
        add(advancedBody, saveLights)
        saveLights.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty() && !url.startsWith("https://")) {
                Toast.makeText(this, "Gebruik HTTPS", Toast.LENGTH_SHORT).show()
            } else {
                AlarmStore.setWebhook(this, url)
                Toast.makeText(this, "Opgeslagen", Toast.LENGTH_SHORT).show()
            }
        }
        gap(advancedBody, 11)
        add(advancedBody, text("NFC-readerfunctie ontbreekt in de Watch 7 featurelijst. Test 3 Android-methoden; alleen een echte tagmelding bevestigt werking.", 11f, muted))
        finish.isEnabled = false
        // Enable via live refresh without adding sensors to this Activity.
        finish.tag = "mission_complete"
    }

    private fun saveAlarm() {
        if (AlarmStore.isRinging(this) || AlarmStore.isTracking(this)) {
            Toast.makeText(this, "Rond eerst het actieve alarm af", Toast.LENGTH_SHORT).show()
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
            val config = AlarmConfig(selectedMode, from, until,
                times.earliestMs, times.latestMs, true)
            AlarmScheduler.schedule(this, config)
            AlarmStore.save(this, config)
            refresh()
            Toast.makeText(this, "Wekker ingesteld ✓", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Kon wekker niet instellen", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNeededPermissions() {
        val wanted = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
            wanted.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), 15)
    }

    private fun timeLabel(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

    private fun refresh() {
        if (!::status.isInitialized) return
        val c = AlarmStore.read(this)
        val ringing = AlarmStore.isRinging(this)
        val tracking = AlarmStore.isTracking(this)
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM HH:mm")
        val smart = selectedMode == AlarmMode.SMART
        fixedTab.background = shape(if (smart) card else mint, 24)
        smartTab.background = shape(if (smart) mint else card, 24)
        fixedTab.setTextColor(if (smart) muted else ink)
        smartTab.setTextColor(if (smart) ink else muted)
        timePrimary.text = timeLabel(from)
        timeSecondary.text = timeLabel(until)
        windowGroup.visibility = if (smart) View.VISIBLE else View.GONE
        heroTime.text = if (ringing) "OPSTAAN" else timeLabel(from)
        heroTime.textSize = if (ringing) 34f else 50f
        heroCaption.text = when {
            ringing -> "Je kunt dit. Kom uit bed."
            tracking -> "We zoeken een goed wekmoment"
            smart -> "Licht wakker, in jouw tijdvak"
            else -> "Stil, maar vastberaden"
        }
        status.text = when {
            ringing -> "●  ALARM ACTIEF"
            tracking -> "●  SLIM WEKVENSTER ACTIEF"
            c.enabled -> "✓  Gepland " + Instant.ofEpochMilli(c.earliestMs).atZone(ZoneId.systemDefault()).format(fmt)
            else -> "●  GEEN ALARM GEPLAND"
        }
        status.setTextColor(if (ringing) warn else mint)
        settingGroup.visibility = if (ringing || tracking) View.GONE else View.VISIBLE
        ringingGroup.visibility = if (ringing) View.VISIBLE else View.GONE
        emergency.visibility = if (ringing || tracking) View.VISIBLE else View.GONE
        cancel.visibility = if (c.enabled) View.VISIBLE else View.GONE
        if (ringing) {
            val steps = AlarmStore.steps(this)
            mission.text = steps.toString() + " / " + WakeService.MISSION_STEPS.toString() + " stappen"
            val finish = (ringingGroup as LinearLayout).getChildAt(2) as TextView
            finish.isEnabled = steps >= WakeService.MISSION_STEPS
            finish.alpha = if (finish.isEnabled) 1f else 0.42f
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresher)
        handler.post(refresher)
    }
    override fun onPause() {
        handler.removeCallbacks(refresher)
        emergencyTimer?.let { handler.removeCallbacks(it) }
        super.onPause()
    }
}
