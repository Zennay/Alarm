# Galaxy Watch 7 — oplader als enige manier om alarm te stoppen

Alarm v0.2.0: een **actief** trillingsalarm stopt wanneer Android meldt dat het horloge oplaadt (of vol is en nog aan de lader ligt). NFC, stappen, lampen en snooze zijn verwijderd. Test op SM-L315F.

## Direct downloaden

[Alarm-WearOS.apk](https://github.com/Zennay/Alarm/releases/download/alarm-preview/Alarm-WearOS.apk)

Vanaf Mac (controleer eerst de actuele watch-serial):
```bash
adb devices -l
curl -L -o ~/Downloads/Alarm-WearOS.apk https://github.com/Zennay/Alarm/releases/download/alarm-preview/Alarm-WearOS.apk
adb -s 192.168.1.219:42429 install -r ~/Downloads/Alarm-WearOS.apk
adb -s 192.168.1.219:42429 shell am start -n com.zennay.alarm/.MainActivity
adb -s 192.168.1.219:42429 logcat -s AlarmWakeService:I AndroidRuntime:E
```

Indien `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: deze preview gebruikt een door de GitHub-runner gegenereerde debug-signing key; de oude app kan daardoor een andere handtekening hebben. Verwijder alleen als veilig de oude installatie via `adb -s <serial> uninstall com.zennay.alarm`, installeer dan opnieuw. Daardoor gaan alle ingestelde alarmen verloren.

## Hardwaretest

1. **Geen alarm actief:** leg horloge op lader, kies 'Controleer oplader'. Verwacht 'Opladen gedetecteerd ✓'; er mag geen alarm starten.
2. Verwijder horloge van lader; controleer 'Nog geen opladen gedetecteerd'.
3. Kies **Test: wekker nu starten** en bevestig, met lader binnen handbereik.
4. Horloge trilt continu; geen snooze- of stopknop. Scherm uit en app verlaten mogen niet bedoeld tot stop leiden.
5. Zet horloge terug op de lader. Zodra het horloge daadwerkelijk opladen meldt, moeten vibratie en notificatie verdwijnen, zonder knoppen.
6. Pak horloge weer van lader: alarm blijft uit.
7. Probeer de test terwijl het horloge al oplaadt: alarm wordt meteen als voltooid beschouwd.
8. Test vast tijdstip en slim venster apart, inclusief scherm uit en na herstart.
9. Kijk in `adb shell dumpsys battery` of Samsung `Wireless powered: true` en batterijstatus 2 (charging) of 5 (full) meldt.

## Acceptatie

Een geslaagde CI-build bewijst alleen compilatie en unit-tests, niet de betrouwbaarheid van Galaxy Watch-7-laaddetectie. De vibratiemotor, Android background-beperkingen en de daadwerkelijke lader moeten apart worden gevalideerd. Gebruik niet als enige wekker totdat dit op het echte horloge is getest.

Android documentatie: https://developer.android.com/reference/android/os/BatteryManager
