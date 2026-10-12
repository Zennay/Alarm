package com.zennay.alarm

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.security.MessageDigest

/**
 * Non-privileged NFC diagnostic for Galaxy Watch 7.
 *
 * Reader mode and foreground dispatch *normally require* FEATURE_NFC.
 * API 35 discovery override is worth probing, but it cannot manufacture
 * NFC polling ability. This screen NEVER claims success without a Tag event.
 */
class NfcProbeActivity : Activity(), NfcAdapter.ReaderCallback {
    private var adapter: NfcAdapter? = null
    private lateinit var result: TextView
    private lateinit var details: TextView
    private var mode = Mode.READER
    private var active = false
    private var enrollNext = false
    private var lastSeen: String? = null

    private enum class Mode { READER, DISPATCH, DISCOVERY }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(25, 22, 25, 40)
            setBackgroundColor(Color.rgb(7, 12, 19))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(page)
        setContentView(scroll)

        fun label(s: String, size: Float = 14f, color: Int = Color.WHITE): TextView =
            TextView(this).also {
                it.text = s
                it.textSize = size
                it.setTextColor(color)
                it.gravity = Gravity.CENTER
                it.setPadding(4, 10, 4, 10)
                page.addView(it, LinearLayout.LayoutParams(-1, -2))
            }
        fun button(s: String, action: () -> Unit) {
            val b = Button(this).apply {
                text = s
                isAllCaps = false
                minimumHeight = 50
            }
            page.addView(b, LinearLayout.LayoutParams(-1, -2))
            b.setOnClickListener { action() }
        }

        label("NFC / TEST", 22f, Color.rgb(113, 239, 206))
        label("Houd de sticker tegen verschillende kanten van de watch. Dit scherm bewijst pas NFC-lezen als er echt een tagmelding komt.", 12f)
        details = label("", 12f, Color.LTGRAY)
        result = label("Kies een scanmethode.", 15f, Color.rgb(113, 239, 206))
        button("1 · Reader mode") { select(Mode.READER) }
        button("2 · Foreground dispatch") { select(Mode.DISPATCH) }
        button("3 · Discovery override (Android 15+)") { select(Mode.DISCOVERY) }
        button("Registreer mijn sticker bij volgende scan") {
            enrollNext = true
            result.text = "Registratie klaar: houd je NFC-sticker tegen de watch."
        }
        button("Stop scanner") { stopScanning(); result.text = "Scanner gestopt." }
        button("Terug naar alarm") { finish() }

        @Suppress("DEPRECATION")
        adapter = try { NfcAdapter.getDefaultAdapter(this) } catch (e: Exception) {
            Log.w(TAG, "Cannot obtain NFC adapter", e)
            null
        }
        val pm = packageManager
        val features = listOf(
            "android.hardware.nfc",
            "android.hardware.nfc.any",
            "android.hardware.nfc.hce",
            "android.hardware.nfc.hcef"
        ).joinToString("\n") { f -> "${f.substringAfterLast('.')} : ${pm.hasSystemFeature(f)}" }
        details.text = "Android API ${Build.VERSION.SDK_INT}\n${features}\n" +
            "NFC adapter: ${adapter != null} · actief: ${adapter?.isEnabled ?: false}\n" +
            "Geregistreerd: ${AlarmStore.nfcTagFingerprint(this) != null}"
    }

    override fun onResume() {
        super.onResume()
        select(mode)
    }

    override fun onPause() {
        stopScanning()
        super.onPause()
    }

    private fun select(next: Mode) {
        stopScanning()
        mode = next
        val nfc = adapter
        if (nfc == null) {
            update("GEEN NFC ADAPTER", "Android geeft geen NFC-adapter aan deze app.")
            return
        }
        if (!nfc.isEnabled) {
            update("NFC STAAT UIT", "Zet NFC in de horloge-instellingen aan en probeer opnieuw.")
            return
        }
        try {
            when (mode) {
                Mode.READER -> {
                    val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
                    nfc.enableReaderMode(this, this, flags, null)
                }
                Mode.DISPATCH -> {
                    // NFC framework injects discovered Tag extras into a mutable intent.
                    val intent = Intent(this, NfcProbeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    val pending = PendingIntent.getActivity(this, 48, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                    nfc.enableForegroundDispatch(this, pending, null, null)
                }
                Mode.DISCOVERY -> {
                    if (Build.VERSION.SDK_INT < 35) {
                        update("NIET BESCHIKBAAR", "Discovery override vereist Android 15 (API 35).")
                        return
                    }
                    nfc.setDiscoveryTechnology(
                        this, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                        NfcAdapter.FLAG_LISTEN_KEEP
                    )
                }
            }
            active = true
            update("${mode.name} INGESCHAKELD", "Geen bewijs van tag-lezen. Probeer nu de sticker.")
        } catch (e: Exception) {
            active = false
            update("${mode.name} GEWEIGERD", "${e.javaClass.simpleName}: ${e.message ?: "Geen details"}")
        } catch (e: LinkageError) {
            update("${mode.name} NIET ONDERSTEUND", e.javaClass.simpleName)
        }
    }

    private fun stopScanning() {
        if (!active) return
        active = false
        val nfc = adapter ?: return
        try {
            when (mode) {
                Mode.READER -> nfc.disableReaderMode(this)
                Mode.DISPATCH -> nfc.disableForegroundDispatch(this)
                Mode.DISCOVERY -> if (Build.VERSION.SDK_INT >= 35) nfc.resetDiscoveryTechnology(this)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup failed for $mode", e)
        }
    }

    private fun update(title: String, reason: String) {
        val msg = "$title\n$reason"
        result.text = msg
        Log.i(TAG, msg.replace("\n", " / "))
    }

    override fun onTagDiscovered(tag: Tag) {
        runOnUiThread { onScannedTag(tag) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action !in arrayOf(NfcAdapter.ACTION_TAG_DISCOVERED,
                NfcAdapter.ACTION_TECH_DISCOVERED, NfcAdapter.ACTION_NDEF_DISCOVERED)) return
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            val old = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            old
        }
        if (tag != null) onScannedTag(tag)
    }

    private fun onScannedTag(tag: Tag) {
        val bytes = tag.id
        if (bytes == null || bytes.isEmpty()) {
            update("TAG ZONDER UID", "Deze tag heeft geen bruikbaar ID. Registreren is niet mogelijk.")
            return
        }
        val fingerprint = fingerprint(bytes)
        if (fingerprint == lastSeen && !enrollNext) return
        lastSeen = fingerprint
        val type = tag.techList.joinToString(",") { it.substringAfterLast(".") }
        Log.i(TAG, "TAG_DETECTED UID SHA256=${fingerprint.take(12)} technologies=$type")
        if (enrollNext) {
            AlarmStore.setNfcTagFingerprint(this, fingerprint)
            enrollNext = false
            update("STICKER GEREGISTREERD", "UID-hash: ${fingerprint.take(12)}… • $type")
        } else if (AlarmStore.isRinging(this) && AlarmStore.nfcTagFingerprint(this) == fingerprint) {
            WakeService.sendNfcCompleted(this, fingerprint)
            update("STICKER HERKEND", "Uitschakelverzoek verzonden naar Alarm.")
        } else {
            update("ECHTE NFC-TAG GELEZEN!", "UID-hash: ${fingerprint.take(12)}… • $type")
        }
    }

    companion object {
        private const val TAG = "AlarmNfcProbe"
        /** Hash only; no raw NFC identifier is saved or printed. */
        fun fingerprint(id: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(id).joinToString("") { "%02x".format(it) }
    }
}
