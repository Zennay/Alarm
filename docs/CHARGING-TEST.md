# Galaxy Watch 7 — oplader als enige manier om alarm te stoppen

Alarm v0.3.3: een **actief** trillingsalarm stopt wanneer Android meldt dat het horloge oplaadt (of vol is en nog aan de lader ligt). NFC, stappen, lampen en snooze zijn verwijderd. Test op SM-L315F.

## Direct downloaden

[Alarm-WearOS.apk](https://github.com/Zennay/Alarm/releases/download/alarm-preview/Alarm-WearOS.apk)

Vanaf Mac (controleer eerst de actuele watch-serial):
```bash
adb devices -l
curl -L -o ~/Downloads/Alarm-WearOS.apk https://github.com/Zennay/Alarm/releases/download/alarm-preview/Alarm-WearOS.apk
adb -s 192.168.1.219:42429 install -r ~/Downloads/Alarm-WearOS.apk
adb -s 192.168.1.219:42429 shell am start -n com.zennay.alarm/.MainActivity
adb -s 192.168.1.219:42429 logcat -s AlarmWakeService:I AlarmRecovery:V AndroidRuntime:E
```

Indien `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: deze preview gebruikt een door de GitHub-runner gegenereerde debug-signing key; de oude app kan daardoor een andere handtekening hebben. Verwijder alleen als veilig de oude installatie via `adb -s <serial> uninstall com.zennay.alarm`, installeer dan opnieuw. Daardoor gaan alle ingestelde alarmen verloren.

## Hardwaretest

1. **Geen alarm actief:** leg horloge op lader, kies 'Controleer oplader'. Verwacht 'Opladen gedetecteerd ✓'; er mag geen alarm starten.
2. Verwijder horloge van lader; controleer 'Nog geen opladen gedetecteerd'.
3. Gebruik **niet** de knop **Trilmotor testen (1 sec)** om de volledige wekker te beoordelen: die knop trilt bewust maar 1 seconde. Kies **Volledige wekker testen (continu)** en bevestig, met lader binnen handbereik.
4. Horloge **trilt zonder enig piep- of wekkergeluid**; geen snooze- of stopknop. Scherm uit en app verlaten mogen niet bedoeld tot stop leiden.
5. **Minuutgrens-regressie:** laat het horloge zonder oplader minstens **3 volledige minuten** doorgaan, inclusief 3 overgangen `HH:mm:59` → `HH:mm+1:00`. Alleen de trillingen moeten doorgaan en de app moet **WEKKER ACTIEF** blijven tonen. Ook na scherm uit/terugkeren mag de wekker niet worden gewist. Controleer in `logcat` dat `CHARGING_DETECTED` niet verschijnt. Een tijdsynchronisatie/TIME_SET mag het actieve alarm ook niet wissen.
6. Zet horloge terug op de lader. Zodra het horloge daadwerkelijk opladen meldt, moeten vibratie en notificatie verdwijnen, zonder knoppen.
7. Pak horloge weer van lader: alarm blijft uit.
8. Probeer de test terwijl het horloge al oplaadt: alarm wordt meteen als voltooid beschouwd.
9. Test vast tijdstip en slim venster apart, inclusief scherm uit en na herstart.
10. Kijk in `adb shell dumpsys battery` of Samsung `Wireless powered: true` en batterijstatus 2 (charging) of 5 (full) meldt.

## Herstel na service-onderbreking

Versie 0.3.3 bewaart een actief alarm ook voorbij de oorspronkelijke minuut en registreert een herstellende `AlarmManager`-melding zolang de wekker actief is. Bij normale werking ververst hij periodiek de trilreeks. De app bevat geen alarm-audiospeler en gebruikt expliciet stille notificatiekanalen. Het herstel is **best-effort**: Wear OS mag processen nog steeds onderbreken en de exacte timing van wakeups uitstellen; hardwaretests blijven verplicht.

## Acceptatie

Een geslaagde CI-build bewijst alleen compilatie en unit-tests, niet de betrouwbaarheid van Galaxy Watch-7-laaddetectie. De vibratiemotor, Android background-beperkingen en de daadwerkelijke lader moeten apart worden gevalideerd. Gebruik niet als enige wekker totdat dit op het echte horloge is getest.

Android documentatie: https://developer.android.com/reference/android/os/BatteryManager


## Tijdswissel: eenmalig alarm op dubbele lokale tijd

De broncorrectie voor de Europese wintertijdwissel op **25 oktober 2026**
laat `TimePlanner.next` voor eenmalige **VAST** en **SLIM**-alarmen
alle werkelijk geldige UTC-offsets van de gekozen lokale tijd bekijken.
Bijvoorbeeld: 02:30 vindt eerst plaats met UTC+02 en later nog een keer
met UTC+01. Als 02:30 UTC+02 al voorbij is maar de tweede 02:30 UTC+01
nog in de toekomst ligt, wordt die tweede kans **op dezelfde dag**
gepland in plaats van op maandag 26 oktober. Pas nadat beide echte
instanten zijn verstreken volgt het volgende lokale etmaal.

Bij de zomertijdsprong, waar de lokale tijd niet bestaat, behoudt de app
het bestaande Java-`atZone`-gedrag. De eindgrens van het slimme venster
blijft een verstreken duur (5–90 minuten) vanaf de geselecteerde
werkelijke startinstant, niet een naïef verschil in dubbelzinnige
wandkloktijden.

**Releasegrens:** deze toelichting hoort bij een zelfstandige
broncorrectie op de nog onuitgebrachte Wear OS-eigenaarbranch.
Geen APK is hierdoor automatisch opnieuw uitgebracht of op een horloge
geaccepteerd. De al bestaande verplichtingen voor exacte CI, review,
fysieke lader-/trilduur en wintertijdgedrag blijven bestaan.
Gebruik een reservewekker totdat eigenaar en fysieke Android/Wear OS
acceptatie hebben plaatsgevonden.
