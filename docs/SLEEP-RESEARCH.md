# Smart-wake research and measurement design (2026-10-12)

## Decision: movement-assisted *wake opportunity*, not REM detection

**Do not claim** to detect REM, N3, or the end of a ~90-minute sleep cycle from accelerometer-only measurements. Real sleep stages are defined by EEG, eye movements and muscle tone (polysomnography, PSG). Wrist movement (actigraphy) can estimate rest/sleep and awakenings, but stillness cannot identify any given sleep stage.

The user can select only one of:
1. Fixed local-time alarm.
2. Smart window [earliest, latest], max 90 minutes. Movement *may* trigger early; latest is always a hard alarm. Choosing an early opportunity necessarily shortens total sleep relative to waiting until the deadline. Evaluate whether waking earlier actually helps this user.

The first heuristic uses acceleration vector differences with consecutive motion-active time buckets and a 2-minute warmup. This is **untuned**, not clinically validated. It does not make a definitive sleep/wake classification. It works during the selected window only, so it does not need to know when the first REM cycle began. Repeated overnight sleep stage monitoring is not part of v0.1.

### Why

- Sleep inertia is affected by more than sleep stage: circadian timing, homeostatic sleep pressure, preceding sleep duration, prior wakefulness and individual differences matter. Hilditch & McHill (2019), *Sleep inertia: current insights*, https://pmc.ncbi.nlm.nih.gov/articles/PMC6710480/
- Wrist actigraphy and multi-sensor sleep staging are distinct: actigraphy uses movement as a *proxy* for wake and sleep; PPG/HRV can improve model prediction with suitable training and PSG labels but are not equivalent to direct brain measurement. Imtiaz (2021), review, https://pmc.ncbi.nlm.nih.gov/articles/PMC7956647/
- Kim et al. (2023), *Validation of the Samsung Smartwatch for Sleep–Wake Determination and Sleep Stage Estimation*, comparing a Galaxy Watch 3 (NOT the Watch 7) against PSG, found substantially poorer staging agreement than sleep detection: https://www.e-jsm.org/journal/view.php?doi=10.13078%2Fjsm.230004
- Silva et al. (2024), sleep-staging research uses dedicated multisensor models; architecture, device hardware and annotated labels matter: https://pubmed.ncbi.nlm.nih.gov/38810479/

There is not strong enough evidence to promise that awakening on wrist movement is *better* than a fixed alarm for this individual. Avoid marketing it as proven science, particularly when a window may wake the user up to 90 minutes earlier. The MVP is an experiment with an inviolable latest-time fallback, not a medical device.

## Validation plan

**Device validation (SM-L315F)**:
- Keep the watch on overnight; measure whether `TYPE_ACCELEROMETER` samples arrive while the screen is off/Doze, and observe battery impact.
- In controlled bench tests, simulate stillness, a 5-second turn in bed, and sustained purposeful movement. Ensure isolated movement does not cause an early alarm.
- Log *only local aggregates*, not raw continuous sensor traces by default. Compare score distributions from 7–14 mornings.
- Record whether the alarm fires by the latest time even if the process is killed or the sensor is unavailable. Firmware limitations must be reported.

**Personal effectiveness**:
- Alternate smart-window vs fixed-time mornings for 2–4 weeks (same *latest* time); keep bedtime and planned sleep duration similar.
- Metrics: wake trigger, minutes between deadline and trigger, time actually out of bed, snooze attempts/forced stops, self-rated morning grogginess (1–5), watch battery drain and whether the partner was disturbed.
- Evaluate side-effects of earlier-than-necessary waking, sleep loss and false positives. Only tighten thresholds after data.
- No claims of reliable REM detection or reduced sleep inertia until independent PSG-quality data or credible controlled evidence exists.

## Implementation trade-offs

- Begin motion sampling only at the start of the smart window, not all night, to conserve battery; enables battery-conscious experimentation.
- No heart-rate permission or PPG-based REM classifier in v0.1. That avoids misleading physiologic interpretations and Wear OS background data complexity.
- Fixed alarms never depend on motion.
- Movement heuristic is instrument-independent and unit-testable. Later calibration requires real Watch 7 logs (de-identified locally) and annotation.

## User safety

A forced non-snoozable alarm is inherently constrained by Android security and hardware: the OS can shut down a service, remove permissions, disable vibrations or power down. A clearly marked safety stop is required when the step sensor fails. Prevent accidental re-arming during an active alarm.
