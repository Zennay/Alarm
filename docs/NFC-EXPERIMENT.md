# Galaxy Watch 7 NFC test — no reader purchase, no root

This experiment probes the *existing* NFC hardware on the owner's SM-L315F. It does not assert reader support. The APK and watch must be used for an actual sticker test; compilation is not proof.

## Feature evidence from the owner's ADB output

The watch reports `android.hardware.nfc.any`, `android.hardware.nfc.ese`, `android.hardware.nfc.hce`, `android.hardware.nfc.hcef`, **but not** `android.hardware.nfc` (standard reader feature).

Official Android requirements: `NfcAdapter.enableReaderMode` and `enableForegroundDispatch` throw `UnsupportedOperationException` if `FEATURE_NFC` is missing:
- https://developer.android.com/reference/android/nfc/NfcAdapter#enableReaderMode(android.app.Activity,android.nfc.NfcAdapter.ReaderCallback,int,android.os.Bundle)
- https://developer.android.com/reference/android/nfc/NfcAdapter#enableForegroundDispatch(android.app.Activity,android.app.PendingIntent,android.content.IntentFilter[],java.lang.String[][])

API 35 `setDiscoveryTechnology()` supports polling masks, but AOSP checks NFC feature availability and may reject polling attempts on HCE-only devices. Treat it as an **experiment**, not a bypass:
- https://developer.android.com/reference/android/nfc/NfcAdapter#setDiscoveryTechnology(android.app.Activity,int,int)
- https://android.googlesource.com/platform/frameworks/base/+/4b41327160ef775ffdb47b0f6ed3e979eebbaf49/nfc/java/android/nfc/NfcAdapter.java

## Installation

Download latest preview APK (once build succeeds): https://github.com/Zennay/Alarm/releases/download/alarm-preview/Alarm-WearOS.apk
Upgrade on watch with current, authorized adb serial:

```bash
adb devices -l
adb -s 192.168.1.219:42429 install -r ~/Downloads/Alarm-WearOS.apk
adb -s 192.168.1.219:42429 shell am start -n com.zennay.alarm/.NfcProbeActivity
adb -s 192.168.1.219:42429 logcat -s AlarmNfcProbe:I AndroidRuntime:E
```

The IP and port may change after reconnecting; always use serial returned by `adb devices -l`.

## Test matrix

1. Enable NFC in Watch settings, ensure watch screen awake; open **Alarm → NFC-sticker testen**.
2. Take an existing ordinary NFC sticker in the bathroom, move it slowly around watch front/back NFC antenna region, hold a few seconds each position.
3. **1 Reader mode**: screenshot the response. `... GEWEIGERD / UnsupportedOperationException` strongly indicates unsupported public API.
4. **2 Foreground dispatch**: screenshot. Only a real `ECHTE NFC-TAG GELEZEN!` message proves tag detection.
5. **3 Discovery override**: only available when the watch runs Android API 35+. May be rejected if FEATURE_NFC absent. `INGESCHAKELD` means *API accepted configuration*, NOT that a tag was actually read.
6. If **any** mode reads the sticker, tap **Registreer mijn sticker bij volgende scan**, lift the sticker away and scan again to enroll the fingerprint. When the alarm is ringing, open NFC test and present the **same** sticker to stop the alarm.
7. Screenshot the screen and send the `AlarmNfcProbe` log lines. Do not share other private NFC data.

```bash
adb -s 192.168.1.219:42429 shell pm has-feature android.hardware.nfc
adb -s 192.168.1.219:42429 shell getprop ro.build.version.sdk
adb -s 192.168.1.219:42429 shell dumpsys nfc | head -n 90
```

## Safeguards

- Tag UID is hashed using SHA-256 before persistence and logging; fingerprint cannot prove that a sticker is cryptographically authentic, UIDs can be cloned.
- The app never tries hidden privileged NFC APIs, root, firmware changes, payment-service registration or wallet permission bypass.
- Optional NFC dismissal requires an actually scanned, enrolled physical tag. The existing 20-step challenge and 5-second emergency stop remain.
- If all three modes reject tag scanning or no tag callbacks arrive despite careful trials, ordinary sticker scanning on this firmware is not demonstrated. Do **not** advertise it as working.
- Non-privileged API-level tricks cannot grant unavailable kernel driver or vendor-firmware support.
