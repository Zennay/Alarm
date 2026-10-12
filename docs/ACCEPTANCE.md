# Alarm v0.3.3 — acceptance criteria

### Functie
- [x] Wear OS, AMOLED UI, één alarm, vaste tijd of slim venster.
- [x] Alleen sterkst aangevraagde trillingen; geen audio, piep, geluidsinstellingen of snooze.
- [x] Geen stopknop zolang het alarm trilt; de enige app-level eindvoorwaarde is `charging`.
- [x] `ACTION_BATTERY_CHANGED` + `ACTION_POWER_CONNECTED` en elke 500 ms een actuele batterijcontrole met stabiele laadbevestiging.
- [x] Vereist echte laadstatus of vol aan de lader; 'connected' zonder laden voldoet niet.
- [x] Begint horloge al op de lader, dan stopt het alarm direct.
- [x] Zonder actief alarm heeft opladen geen effect.
- [x] Vaste wekker en slimme modus blijven bestaan.
- [x] NFC, stappen, lampen en ongebruikte permissies verwijderd.
- [x] Unit-tests laadstatuslogica en minuutwisseling; CI voorkomt herintroductie van audio in de app.
- [ ] Nieuwe APK slaagt CI en wordt op GitHub Release gepubliceerd.
- [ ] Testen op Galaxy Watch 7 met eigen oplader, in doze/lockscreen en overnacht.

### Grenzen
- Geen medische/REM-claim.
- Geen gegarandeerd onstopbare app: Android-systeemfuncties blijven in controle.
- De maximale trillingssterkte wordt door de firmware bepaald.
- Preview debug-APK kan per CI-run ander certificaat gebruiken; herinstallatie kan nodig zijn.
- Test altijd eerst met oplader binnen handbereik.

[Hardwaretest](CHARGING-TEST.md)
