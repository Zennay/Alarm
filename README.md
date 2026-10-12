# Alarm — standalone Galaxy Watch wake-up app

A silent Wear OS alarm for the Samsung Galaxy Watch 7 (SM-L315F). **Work in progress / experimental: not a validated wake-up alarm yet.**

## Product scope

- **Exactly one active alarm**, with mutually exclusive modes:
  - **Fixed**: ring/vibrate at an exact local time.
  - **Smart window**: choose a local earliest and latest time; sense wrist movement inside that window and trigger early only after enough evidence of likely lighter sleep / awakening. Always trigger at the latest time.
- Watch-only operation for scheduling, wake-window sensing, vibration and dismissal. No mandatory phone.
- Strong vibration pattern, no sound, no snooze button. The user must complete a wake-up mission before dismissal. A normal user or Android OS can still force-stop/uninstall/reboot the app; there is **no guarantee an alarm cannot be stopped**.
- Prefer a bathroom-confirmation mission. A movement-based step challenge is a **fallback and cannot prove room location**.
- On successful dismissal, *optionally* trigger bright white bathroom lights through a **user-configured, authenticated HTTPS webhook**. Google Home alone does **not** expose a generic NFC trigger or turnkey watch-side light-control endpoint; native Google Home API compatibility and credentials need separate validation.
- Battery-conscious sensor use; permissions and graceful fallback; local-only sleep features where feasible.

## NFC feasibility: important hardware limitation

On the user's SM-L315F `adb shell pm list features | grep -i nfc` returned
`android.hardware.nfc.any`, `.ese`, `.hce`, `.hcef`, but **not** `android.hardware.nfc`.
The standard Android NFC reader API (`enableReaderMode`) cannot be assumed to work on this device. HCE makes the watch appear as a *card* to an active reader; a passive NFC sticker cannot read or deactivate the watch. **No new NFC reader is within scope.** NFC-tag dismissal is therefore deferred until real on-device reader-mode evidence exists; do not advertise it as implemented.

## Smart wake evidence and limitations

Sleep is scored clinically with polysomnography (EEG/EOG/EMG). Actigraphy (wrist movement) is a sleep/wake proxy, not a direct REM, N1/N2 or N3 measure. Heart rate and motion may improve stage estimates, but consumer devices are fallible. Sleep cycles are not reliably 90 minutes and a motion burst alone may be a turn in bed, not a lighter-stage awakening. There is currently insufficient strong evidence to **promise** that a motion-triggered smart alarm reliably reduces sleep inertia relative to a fixed alarm. Avoid unsupported claims.

References:
- Hilditch & McHill, 2019: https://pmc.ncbi.nlm.nih.gov/articles/PMC6710480/
- Imtiaz, 2021 sensing technology review: https://pmc.ncbi.nlm.nih.gov/articles/PMC7956647/
- Kim et al. Samsung smartwatch PSG validation (older Watch 3): https://www.e-jsm.org/journal/view.php?doi=10.13078%2Fjsm.230004
- Android NFC support: https://developer.android.com/develop/connectivity/nfc
- Google Home APIs SDK (device, OAuth/hub/compatibility dependent): https://developers.home.google.com/apis/android/sdk

## Target architecture

Kotlin Android/Wear OS, Android AlarmManager for exact boundary scheduling (subject to user-granted exact alarm permission), wake-window sensor service, vibration foreground service, persisted active-alarm config, explicit wake mission and fallback, reboot rescheduling. Device tests are necessary for notifications/full-screen alarm restrictions, Wear OS DND and max vibration strength, battery life, and OEM background process behavior. **Do not treat CI build success as proof of device reliability.**
