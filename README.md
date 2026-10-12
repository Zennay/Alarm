# Alarm · Galaxy Watch 7

**Wear OS 0.3.3 preview** — volledig stille trilwekker voor Galaxy Watch 7 (SM-L315F).

**[APK downloaden](https://github.com/Zennay/Alarm/releases/download/alarm-preview/Alarm-WearOS.apk)** · [Release](https://github.com/Zennay/Alarm/releases/tag/alarm-preview)

## Gewenste werking

- Eén wekker tegelijk. Kies **vaste tijd** of **slim wekvenster** van 5–90 minuten.
- De slimme modus gebruikt een experimentele bewegingsheuristiek; de uiterste tijd is hard ingepland. Geen betrouwbare REM-detectie.
- Als de wekker afgaat, gebruikt het horloge **uitsluitend sterke, herhalende trillingen**. Er is **geen wekkergeluid, audioweergave, geluidskeuze of geluidstest**. **Geen snooze en geen uitschakelknop**.
- **Uitsluitend opladen stopt een actief trilalarm.** Een batterijmelding van Android of de periodieke statuscontrole herkent opladen of een volle batterij die op de lader aangesloten is.
- Opladen zonder een actief alarm doet **niets**. Er gaan geen lampen aan en er is geen netwerkverzoek.
- Als het horloge bij aanvang van de wekker al op de lader ligt, wordt het alarm meteen als voltooid beschouwd.
- Geen NFC-scanner, stappenopdracht, Google Home-koppeling of telefoon vereist voor de werking.

**Stille versie (0.3.3):** alle alarm-audio en geluidstestcode zijn uit de app verwijderd. De notificatie gebruikt een nieuw, expliciet stil kanaal. De trilmotor wordt tijdens een lopend alarm periodiek opnieuw geactiveerd. Een klokwijziging of onjuiste korte laadmelding mag de wekker niet uitschakelen. Een Watch 7-hardwaretest blijft nodig.

## Testen

Gebruik **Controleer oplader** om de laaddetectie zonder actief alarm te testen. **Trilmotor testen (1 sec)** is alleen een korte motortest; **Volledige wekker testen (continu)** is de echte trilwekker en mag pas stoppen bij opladen. Zie [de testhandleiding](docs/CHARGING-TEST.md).

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
