# Alarm · Galaxy Watch 7

**Wear OS 0.3.2 preview** — hoorbare en trillende wekker voor Galaxy Watch 7 (SM-L315F).

**[APK downloaden](https://github.com/Zennay/Alarm/releases/download/alarm-preview/Alarm-WearOS.apk)** · [Release](https://github.com/Zennay/Alarm/releases/tag/alarm-preview)

## Gewenste werking

- Eén wekker tegelijk. Kies **vaste tijd** of **slim wekvenster** van 5–90 minuten.
- De slimme modus gebruikt een experimentele bewegingsheuristiek; de uiterste tijd is hard ingepland. Geen betrouwbare REM-detectie.
- Als de wekker afgaat speelt het horloge een **zelfgekozen alarmgeluid en trillingen tegelijk**. Je kunt in de app kiezen tussen drie ingebouwde geluiden, een systeemalarmgeluid (mits de Wear OS-kiezer beschikbaar is) of een eigen audiobestand (mits de bestandskiezer beschikbaar is), met een relatieve volumeregelaar en geluidsvoorbeeld. **Geen snooze en geen uitschakelknop**.
- **Uitsluitend opladen stopt een actief geluid- en trilalarm.** Een batterijmelding van Android of de periodieke statuscontrole herkent opladen of een volle batterij die op de lader aangesloten is.
- Opladen zonder een actief alarm doet **niets**. Er gaan geen lampen aan en er is geen netwerkverzoek.
- Als het horloge bij aanvang van de wekker al op de lader ligt, wordt het alarm meteen als voltooid beschouwd.
- Geen NFC-scanner, stappenopdracht, Google Home-koppeling of telefoon vereist voor de werking.

**Continuïteitsfix (0.3.2):** minuutwisselingen of een tijdsynchronisatie mogen een lopend alarm niet verwijderen; de alarmservice kan opnieuw gestart worden na een onderbreking en de trilreeks wordt periodiek vernieuwd. Het ingebouwde wekkergeluid speelt als doorlopende PCM-stream; kortstondige laadmeldingen kunnen de wekker niet uitschakelen. De trilmotor-test van één seconde is **niet** de volledige wekkertest: kies daarvoor **Volledige wekker testen (continu)**. Een Watch 7-hardwaretest blijft nodig.

## Testen

Stel onder **Wekkergeluid** je toon en volume in; de volumeregelaar is afhankelijk van het **systeemwekkervolume** van de Watch 7. Gebruik **Beluister geluid** om te testen. Gebruik **Controleer oplader** om zonder alarm te testen of het horloge het laden herkent. Gebruik **Test: wekker nu starten** met de oplader binnen handbereik om het echte trillingsalarm te testen. Zie [de testhandleiding](docs/CHARGING-TEST.md).

De app is **experimenteel en nog niet 's nachts op de fysieke Watch 7 gevalideerd**. Gebruik een reservewekker voor belangrijke afspraken. Android kan nog altijd apps geforceerd stoppen, het horloge uitzetten of notificaties beperken; geen app is volledig onstopbaar. Als je lader niet werkt, is er bewust geen knop in de app om het alarm te stoppen, maar de Android-systeeminstellingen blijven beschikbaar.

## Installeren

Download APK vanaf je telefoon en gebruik een Wear OS APK-installer, of installeer met ADB op de Mac:

```bash
adb devices -l
curl -L -o ~/Downloads/Alarm-WearOS.apk https://github.com/Zennay/Alarm/releases/download/alarm-preview/Alarm-WearOS.apk
adb -s 192.168.1.219:42429 install -r ~/Downloads/Alarm-WearOS.apk
```

IP en poort kunnen wisselen. Bij `INSTALL_FAILED_UPDATE_INCOMPATIBLE` heeft de oude preview mogelijk een andere debughandtekening. Oude app **veilig verwijderen en opnieuw installeren**, waarbij ingestelde alarmen verloren gaan.

## Onderzoek en broncode

[Smart wake-onderzoek](docs/SLEEP-RESEARCH.md) · [Scope](docs/ACCEPTANCE.md) · [Pull request](https://github.com/Zennay/Alarm/pull/1). Laadstatus via Android [BatteryManager](https://developer.android.com/reference/android/os/BatteryManager).
