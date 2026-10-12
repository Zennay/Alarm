# Product scope / delivery gates

## Must-have MVP (v0.1)

- [x] Kotlin Wear OS source, watch launcher, standalone local storage.
- [x] Exclusive **fixed** or **smart** mode, never multiple simultaneous alarms.
- [x] Choose exact clock time or earliest/latest local times (5–90 minute window).
- [x] Schedule start with Android `AlarmManager.setAlarmClock`, smart latest with `setExactAndAllowWhileIdle`.
- [x] Strong, silent cyclic vibration requested; no snooze button.
- [x] Movement proxy that can choose to wake earlier but never after hard latest by design.
- [x] Explicit step mission before dismissing; no immediate dismiss button.
- [x] Five-second emergency stop for permission/sensor failures.
- [x] Optional HTTPS webhook sends requested white/100% light state on successful completion; **not a direct Google Home SDK implementation**.
- [x] Unit tests for time scheduling + movement logic added (CI not yet confirmed).
- [ ] Build succeeds on GitHub self-hosted runner, then APK installed on SM-L315F.
- [ ] Real hardware tests: haptics, amplitude handling, locked watch UI, vibration in sleep/Doze, sensor events, background restrictions, missing permissions.
- [ ] User-configured bright-white bathroom light successfully activated.
- [ ] Verified precise physical bathroom presence without extra reader. **Not currently feasible with passive sticker**, see below.

## NFC decision

ADB feature list supplied by the owner:
```
feature:android.hardware.nfc.any
feature:android.hardware.nfc.ese
feature:android.hardware.nfc.hce
feature:android.hardware.nfc.hcef
```
No `android.hardware.nfc` feature was reported. This suggests that ordinary NFC tag reading isn't exposed to apps on SM-L315F. HCE can emulate a card **only to an active external reader**; the user's passive sticker cannot listen to the watch. User explicitly ruled out purchasing a reader, so no reader-dependent architecture is accepted. Any future direct NFC-reading claim requires an actual on-watch reader-mode proof, not just developer mode, `nfc.any` or HCE.

The watch-only step mission is a practical compromise; it cannot prove physical location.

## Lighting integration

Google Home APIs for Android can control eligible devices, but need SDK, OAuth/account access, supported device/hub and compatible runtime. **Wear OS availability is unverified.** An HTTPS webhook may connect an *existing* automation bridge but is not a Google Home trigger in itself. Direct pairing and actual white-color support depend on bulb/device capabilities. Never mark light integration done without an end-to-end real lamp test.

Docs: https://developers.home.google.com/apis/android/sdk ;
https://developers.home.google.com/apis/android/supported-device-types

## Remaining reliability issues

- Background wake-window sensing needs Watch 7 field validation and energy profiling.
- Verify that the watch accepts 255 haptic amplitude and that the strongest motor setting is physically perceptible.
- `SCHEDULE_EXACT_ALARM`, notification and activity-recognition permissions need friendly UX and regression tests.
- Reboot and time-zone changes, permission revoked, battery flat and OS killing services need explicit, tested behavior.
- No guarantee of an 'unkillable' alarm; system controls and safety overrides remain in user hands.
- One-shot scheduling only; no automatic daily repeat in initial scope.
