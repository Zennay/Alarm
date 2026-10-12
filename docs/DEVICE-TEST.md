# Build and validate Alarm on Galaxy Watch 7 (SM-L315F)

## Build

The repository intentionally runs CI on the self-hosted Linux x64 VPS runner rather than inventing a local test result. Open the **Android Wear OS build** GitHub Actions run and download `Alarm-debug-apk` once the job is successful.

For a local developer checkout with Java 17, Android SDK 35 and Gradle 8.9:

```bash
gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. There is no Gradle wrapper yet; the workflow pins Gradle 8.9 through setup-gradle.

## Connect watch, not a duplicated ADB transport

The owner's Galaxy Watch has sometimes shown two connections (IP and mDNS):
```
adb devices -l
# 192.168.1.219:42429 device ... model:SM_L315F ...
# adb-RFAY32E68AB-... device ... model:SM_L315F ...
```
They represent the **same** watch; always select one serial explicitly.

Replace the IP/port with the latest value from Watch settings > Developer options > Wireless debugging:
```bash
adb -s 192.168.1.219:42429 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.1.219:42429 shell am start -n com.zennay.alarm/.MainActivity
adb -s 192.168.1.219:42429 logcat -s Alarm:V AlarmLights:V AndroidRuntime:E
```
If the original IP is stale, choose the other `adb devices -l` serial or re-pair. Do not assume 42429 remains valid.

## Real-hardware checklist (UNVERIFIED until tested)

- [ ] App launches in circular Wear OS UI; time pickers usable.
- [ ] Grant exact alarm, activity-recognition, and notification permissions.
- [ ] Fixed alarm triggers on an exact time with screen off, Doze, and DND.
- [ ] Smart window samples movement without premature notification/lighting and **always** triggers by latest.
- [ ] Alarm has no snooze and vibration strength is perceptible.
- [ ] 20 real steps are registered; cannot dismiss before steps; five-second emergency stop works.
- [ ] Restart watch before alarm: exact wake deadline is rescheduled.
- [ ] During smart window, reboot preserves latest deadline rather than firing instantly.
- [ ] Deny step permission: app remains usable and emergency stop works.
- [ ] Force-stop or shutdown: document actual OS limitations and what stops working.
- [ ] Measure battery drain on at least two nights (fixed vs smart).
- [ ] Confirm watch truly remains silent; ensure partner isn't disturbed.
- [ ] With a real pre-existing HTTPS bridge, verify bathroom light goes white at 100% when mission completes.

**Do not use as the only alarm for time-critical obligations before passing these tests.** Keep a conventional backup alarm until reliable.

## Why the owner's existing NFC sticker is not a dismissal method

The owner's feature output reports `nfc.any`, `nfc.hce`, `nfc.hcef`, `nfc.ese`, but **not** `android.hardware.nfc`. Android `PackageManager.FEATURE_NFC` specifically documents NFC *reader* support; `NfcAdapter.enableReaderMode` throws `UnsupportedOperationException` if it is unavailable. Developer mode cannot create this missing public API capability. This is not the same as HCE (emulates a card for an external reader), and the user disallows an additional reader.

Official API references:
- https://developer.android.com/reference/android/content/pm/PackageManager#FEATURE_NFC
- https://developer.android.com/reference/android/nfc/NfcAdapter#enableReaderMode(android.app.Activity,android.nfc.NfcAdapter.ReaderCallback,int,android.os.Bundle)
