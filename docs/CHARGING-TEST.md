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

## Slim wekvenster: betrouwbare bewegingsopvolging (bronreparatie)

De bewegingstrigger mag niet beginnen op basis van een gedeeltelijke
meting van vóór de vereiste **twee minuten opwarmtijd**. De bron telt
nu uitsluitend twee opeenvolgende complete activiteitsblokken van
15 seconden die **beide na** die twee minuten zijn begonnen.
De vroegst mogelijke bewegingstrigger na inschakelen ligt daardoor
ongeveer 30 seconden na de opwarmperiode, niet op het eerste moment
dat de twee minuten verlopen.

Bij een onderbreking van meer dan vijf seconden tussen de
bewegingsmetingen, een terugspringende klok of een ongeldige
accelerometerwaarde worden de oude bewegingsblokken gewist:
oude en nieuwe sensoractiviteit tellen dan nooit samen als
opeenvolgende waarnemingen. Een blok heeft daarnaast minstens vier
echte geldige accelerometerverschillen nodig. Eén losse, mogelijk
misleidende sensorspike kan daardoor niet zelfstandig een actief
15-secondenblok vertegenwoordigen. Wanneer er geen bruikbare
beweging is, blijft de al bestaande **uiterste wektijd** gelden;
dit is nadrukkelijk geen REM-faseherkenning.

Deze wijziging hoort bij [de bestaande bron-PR #5](https://github.com/Zennay/Alarm/pull/5).
Er is geen wijziging in het trillingspatroon, de lader-als-enige-stopactie,
persistente alarmstatus of de productie-/APK-releaseprocedure.
De correctie is niet op een Galaxy Watch geverifieerd; gebruik
een reservewekker totdat de echte Watch-acceptatie en bestaande
verplichte CI, review en eigenaaracceptatie zijn afgerond.


## Tijdswissel: geplande echte tijdinstanten zichtbaar op het horloge

Wanneer de app een wekker als **gepland** toont, staat naast dag en lokaal
tijdstip voortaan ook de werkelijke UTC-offset, bijvoorbeeld
`zo 25 okt 02:30 +02:00` of `zo 25 okt 02:30 +01:00`. Dat zijn op de
Nederlandse wintertijddag **twee verschillende tijdinstanten**. Bij een
**slim wekvenster** staan zowel het begin als de harde uiterste wektijd met
hun afzonderlijke UTC-offset op twee regels, zodat een venster over de
tijdswissel niet visueel de verkeerde eindtijd suggereert.

Dit is uitsluitend een wijziging in het statusoverzicht van
`MainActivity` op de bestaande [Alarm PR #5](https://github.com/Zennay/Alarm/pull/5).
De geplande epoch-milliseconden, `AlarmManager`, smart motion gating,
Wear OS trillingen, oplader-als-enige-stopactie en release/signing worden
**niet** gewijzigd. Zodra de wekker actief is, blijft het bestaande
'WEKKER ACTIEF' scherm bepalend.

**Niet afleiden uit een geslaagde build:** de fysieke Galaxy Watch 7,
de tweevoudige 02:30, haptische doorloop en werkelijke laadstatus moeten
apart met een geautoriseerde eigenaar worden gecontroleerd. Tot die tijd
een onafhankelijke reservewekker gebruiken. Geen APK-uitgave of toestelupdate
door deze bron-PR.

## Geplande wekker versus nog niet opgeslagen invoer

Op het hoofdscherm toont de **grote klok bij een ingeschakelde wekker de
daadwerkelijk opgeslagen starttijd**, en de status vermeldt nog steeds de
volledige geplande dag en UTC-offset. De tijdkeuze onder **WEKTIJD**
is een bewerkbaar formulier: aanpassen verandert de ingestelde wekker
**niet** voordat je op **Wekker instellen** tikt.

Als ingestelde modus of tijden van dat formulier afwijken, verschijnt
**NIET OPGESLAGEN** met de boodschap dat de bestaande wekker blijft
gelden. De knop **Wijzigingen terugdraaien** zet het formulier rechtstreeks
terug naar de daadwerkelijk opgeslagen modus en tijden. Die actie wijzigt
**geen** `AlarmManager`-planning, tracking, trillingen of opgeslagen wekker.
**Geplande wekker annuleren** blijft een afzonderlijke expliciete handeling. Deze scheiding voorkomt dat een gewijzigd tijdveld op het
AMOLED-scherm wordt verward met een al opnieuw geplande wekker.

Als de opgeslagen **uiterste** wekdeadline al is verstreken terwijl het
scherm geen actieve/trillende of slimme tracking-wekker ziet, verschijnt
een oranje **WEKTIJD VERSTREKEN** waarschuwing, samen met de oorspronkelijke
geplande tijd. Dat is een **waarschuwing, geen bewijs** dat de gebruiker
daadwerkelijk gewekt is of dat Android het alarm heeft afgeleverd. Bij
een actief trillingsalarm blijft altijd de bestaande status **WEKKER
ACTIEF** gelden en kan het horloge uitsluitend met de echte lader
worden gestopt.

Deze veranderingen zijn uitsluitend broncode op [PR #5](https://github.com/Zennay/Alarm/pull/5).
Er is geen APK geïnstalleerd, ondertekend of live gevalideerd.
Bestaande exacte-head CI, onafhankelijke review, eigenaaracceptatie en
fysieke Galaxy Watch 7-checks blijven verplicht; gebruik voorlopig
een onafhankelijke reservewekker.

## Voorbeeld bij opslaan en toestemming voor exacte alarmen

Zolang er **geen ingeschakelde wekker** is, of de geselecteerde modus/tijd
nog **niet is opgeslagen**, toont de app een afzonderlijk **VOORBEELD BIJ
OPSLAAN · NOG NIET ACTIEF**. Dit voorbeeld gebruikt dezelfde
`TimePlanner.next` als het daadwerkelijke instellen. De geplande echte
datum, wektijd en UTC-offset zijn daardoor alvast te beoordelen, ook
wanneer de Nederlandse wintertijd één lokaal tijdstip twee keer laat
voorkomen. Bij een slim wekvenster staat eveneens de uiterste wektijd.
Een ongeldig slim venster (niet tussen vijf en negentig minuten) geeft
een duidelijke melding in plaats van een misleidende voorbeeldtijd.

De voorbeeldweergave **roept geen Android AlarmManager aan**, bewaart geen
instelling en vervangt nooit de al ingeschakelde wekker. Een voorstel
heeft bewust geen actief vinkje. Alleen de expliciete knop
**Wekker instellen** plant werkelijk de eerstvolgende gebeurtenis.

Wanneer Wear OS geen toestemming meer geeft om exacte alarmen te plannen,
staat er een zichtbare waarschuwing en een aparte knop **Open
alarmtoestemming**. Zowel die knop als de opslaanroute delen een
afgeschermde instellingenactie met een gebruikersmelding als de
bestemming niet beschikbaar is. Zonder toestemming meldt de app geen
geslaagde nieuwe alarmplanning. De waarschuwing zelf herstelt bestaande
Android-alarmen niet; bekijk de toestemmingsstatus en stel waar nodig
opnieuw in.

Alle nieuwe schermen zijn uitsluitend sourcecode op
[PR #5](https://github.com/Zennay/Alarm/pull/5). Er is geen APK-release,
ondertekening, installatie of daadwerkelijke horlogeverificatie gebeurd.
Bestaande verplichte CI, onafhankelijke PR-review en echte stille
trillings-, wintertijd- en laadacceptatie blijven onverkort vereist.

## Bescherming tegen per ongeluk annuleren (21 oktober)

**Geplande wekker annuleren** wist niet meer direct bij één onbedoelde
aanraking op het kleine Watch-scherm. Eerst verschijnt een bevestiging
met de **daadwerkelijk opgeslagen datum, wektijd en UTC-offset**
(inclusief de juiste wintertijdinstantie) en twee expliciete keuzes:
**Wekker behouden** of **Ja, annuleren**.

De bevestiging is aan exact dezelfde opgeslagen `AlarmConfig` gebonden.
Wanneer een andere instelling intussen werkelijk is opgeslagen, de wekker
al trilt of de slimme bewegingsbewaking actief is, wordt de oude
bevestiging genegeerd en de status vernieuwd. Niet-opgeslagen wijzigingen
uit het tijdformulier kunnen geen andere al geplande wekker annuleren.
Dit verandert geen hervatgedrag, laderbeleid of wekplanningsalgoritme.
Het is broncode en geen bewijs dat fysieke Wear OS-dialogen of trilling
op de Watch 7 al zijn geaccepteerd.


## Geplande wekker blijft leidend bij vertraagde Android-meldingen

Bij het wijzigen van een ingestelde wekker gebruikt Android dezelfde
`PendingIntent`-acties voor het oude en het nieuwe tijdstip. Een melding kan
intussen al onderweg zijn. De `AlarmReceiver` vergelijkt daarom een
ontvangen START nu met de **huidige opgeslagen vroegste wektijd** en
een DEADLINE met de **huidige opgeslagen uiterste wektijd**.
Een achterhaalde melding vóór de nieuwe planning start **geen vroegtijdige
bewegingstracking of trilling**. Als de actuele deadline wel verstreken is,
blijft de echte alarmactie bereikbaar.

Ook een al lopend slim venster blijft beschermd: wanneer de systeemklok
door een handmatige tijd-/zonecorrectie voorbij de vastgelegde deadline
verspringt, start het systeem een onmiddellijke wekactie in plaats van de
nog actieve tracking stil uit te schakelen. Een pas na vele uren herstart
horloge houdt het bestaande *one-shot verlopen*-gedrag, zodat een oude
wekker niet willekeurig na een koude start afgaat.

Deze source-only aanpassingen veranderen niets aan de eis dat alleen
een echt laadcontact een trillende wekker mag stoppen. Onafhankelijke
review, exacte-commit CI en fysieke Watch 7-acceptatie zijn nog vereist.


## Veilig opnieuw instellen: een bestaande wekker niet eerst verwijderen

De knop **Wekker instellen** gebruikt nu `AlarmScheduler.armAndSave`.
Voorheen verwijderde `schedule` eerst de bestaande Android-timers.
Als het toevoegen van de nieuwe `START` of `DEADLINE` mislukte,
bleef de oude configuratie zichtbaar terwijl de feitelijke timer
mogelijk verdwenen was.

De nieuwe programmering vervangt de `START`-PendingIntent rechtstreeks
via `AlarmManager` en verandert de oude uiterste deadline pas nadat
de nieuwe start door Android is geaccepteerd. Als de nieuwe timer niet
volledig wordt geaccepteerd, probeert de app de vorige, nog geldige
wekker of het resterende slimme eindmoment opnieuw te programmeren.
Een al verlopen one-shot wordt nooit ongemerkt als toekomstalarm
aangemerkt. Als geen betrouwbare terugzetting mogelijk is, wordt de
overgebleven geplande timer opgeruimd en het bewaarde *actief*-vinkje
uitgeschakeld; de gebruiker krijgt een melding om opnieuw in te stellen.

Pas na het daadwerkelijk programmeren schrijft de app de nieuwe
configuratie **synchroon en controleerbaar** naar privépreferences.
Als opslaan mislukt, probeert de app weer de eerder opgeslagen wekker
te herstellen. Het scherm vernieuwt ook na een fout: een groene oude
status mag niet als bewijs van een succesvol nieuw alarm blijven staan.
De handmatige volledig-trillen-proef bewaart eerst duurzaam zijn
testsituatie voordat een bestaande planning wordt verwijderd; een
mislukte testopslag laat de echte oude timer intact.

Dit is de productlogica in een **open bron-PR**, niet een bewijs dat de
Wear OS-firmware alle alarm- en opslagfouten fysiek overleeft.
Bij afgewezen exacte-alarmrechten, een geforceerde appstop of defecte
opslag is een reservewekker noodzakelijk. Het alarm stopt nog steeds
alleen na werkelijk, stabiel waargenomen opladen; deze wijziging maakt
geen snoozeknop, geluid of alternatieve stopactie mogelijk.


## Annuleren van een echte geplande wekker bij opslagfouten

De bevestiging **Ja, annuleren** bewaart eerst synchroon en controleerbaar
dat de *exactzelfde* oude ingeplande wekker is uitgeschakeld. Als die
privéopslagbewerking mislukt, verwijdert de knop géén Android-timer en
verschijnt **Annuleren mislukt; vorige wekker behouden**. Een vertraging
tussen dialoog openen en accepteren kan niet een andere nieuwe wekker
annuleren. Na de succesvolle uitgeschakeld-opslag probeert de app de
overgebleven `PendingIntent`-timers op te ruimen; mocht Android die
opruimactie weigeren, dan weigert de eigen `AlarmReceiver` alsnog
binnengekomen berichten op basis van de duurzaam uitgeschakelde staat.
De gebruiker krijgt in dat geval een aparte waarschuwing dat Android
alarmen moeten worden gecontroleerd.

De annulering blijft onmogelijk terwijl de wekker trilt of een slim
wekvenster volgt. Een actief trilalarm stoppen blijft uitsluitend
mogelijk na echt, stabiel waargenomen opladen. Zonder onafhankelijke
Watch 7-acceptatie of exact-head automatische build is dit uitsluitend
een broncodewijziging en geen nieuwe hardwarebetrouwbaarheidsclaim.


## Volledige trilproef zonder geplande wekker te overschrijven

De **volledige proef** is een echte aanhoudende, stille trilling totdat de watch daadwerkelijk oplaadt. Een belangrijke bedieningsfout is hersteld: de eerdere proeffunctie verving de **reeds opgeslagen ochtendwekker** door een proefalarm op het huidige moment en annuleerde daarna Androids bestaande PendingIntents. Een gebruiker kon zo onbedoeld de volgende echte wekactie kwijtraken.

De app **weigert nu de volledige proef zolang een geplande wekker ingeschakeld is**, ook als het oorspronkelijke tijdstip al verstreken is. Het scherm zegt dit vooraf; tikken toont de exact bewaarde wektijd en de reden. De afzonderlijke **Trilmotor testen (1 sec)** blijft beschikbaar zonder wijzigingen aan planning of opslag. Wil de gebruiker bewust een volledige proef doen, dan moet de geplande wekker eerst via de bestaande bevestigde annuleeractie worden geannuleerd, en na de proef moet een nieuwe ochtendwekker expliciet worden ingesteld.

De volledige proef hercontroleert **na de bevestigingsdialoog** dat er nog altijd geen geplande, actieve of slimme trackingwekker is. Alleen als dit waar is, wordt een eenmalige tijdelijke proef als actieve ring opgeslagen. Bij opslagfout stopt de proef zonder de echte wekker te wijzigen; bij service-startfout probeert de app de tijdelijke toestand weer uit te schakelen en meldt expliciet dat geen echte wekker gepland is. Er is **geen** automatische restore, geen verborgen vervangen of bewaren van een eerdere real-world alarmplanning.

### Opladen bevestigt alleen een duurzaam opgeslagen stopactie

In `WakeService` werd na laadbevestiging de trilmotor en herstelmelding al gestopt vóór `AlarmStore.setDisabled()` was gecommit. Daardoor kon een opslagfout de gebruiker een gestopt alarm laten zien terwijl de opgeslagen status nog **actief** was. Nu is een echt bevestigd laad-/volsignaal **voorwaarde**, maar wordt het alarm pas als definitief gestopt beschouwd nadat de uitgeschakelde status duurzaam is gecommit. Mislukt dat, dan blijven de actieve ring en herstelplanning intact en probeert de periodieke laadcontrole opnieuw. Wanneer het committen gelukt is, kunnen mislukte Android PendingIntent-cleanups de persistente uitschakeling niet meer terugdraaien; de receiver verwerpt callbacks voor een uitgeschakelde wekker.

**Grenzen:** dit zijn broncorrecties op bestaande [Wear OS product-PR #5](https://github.com/Zennay/Alarm/pull/5), commits [46bda78](https://github.com/Zennay/Alarm/commit/46bda78895563e8866e59d14e61cd05004c262c3) en [17ef065](https://github.com/Zennay/Alarm/commit/17ef0652a66698a5106fb7b04a6ca2246e2b090f). Geen QA-suite of regressie uitgevoerd, APK getekend/gepubliceerd, Samsung Watch gewijzigd of release-gate omzeild. Verplichte exacte-head Android CI, onafhankelijke code-review en echte Galaxy Watch 7-acceptatie inclusief ladersignaal blijven nodig. Tot die tijd een onafhankelijke reservewekker gebruiken.


## Onbevestigde wekactie na herstart of klokcorrectie

Een echte wekker kan door een plotselinge herstart of klokcorrectie een al verstreken deadline hebben terwijl er **geen actieve trillingsservice** of smart tracking meer bevestigd is. De app mag dan niet beweren dat Android gewekt heeft, en mag de geplande gebeurtenis niet als stilzwijgend succesvol behandelen.

De bestaande `BootReceiver` schakelt een dergelijke verlopen eenmalige planning nog altijd uit, zodat een uren oud alarm niet plots alsnog afgaat. Maar hij bewaart nu tegelijk de expliciete, duurzame vlag `expiredUnconfirmed` in lokale Watch-preferences. De laatste opgeslagen `earliestMs/latestMs` en modus worden hierbij behouden. Het AMOLED-statusscherm toont vervolgens een **oranje WAKEACTIE NIET BEVESTIGD**-waarschuwing met de oorspronkelijk geplande echte datum en UTC-offset en de instructie een nieuwe wekker te plannen. Dit is geen claim dat de wekker definitief gemist is: de app kent de feitelijke menselijke ontwaking niet.

Wanneer de gebruiker **zelf een nieuwe planning opslaat**, of een alarm expliciet annuleert, wordt die eerdere waarschuwing gewist. Ook opladerbevestiging ruimt haar op na de succesvolle persistente stopactie. Een mislukte opslag van de vervallen toestand laat de oude `enabled` status intact; de bestaande waarschuwing voor verlopen maar nog ingeschakelde deadlines blijft dan zichtbaar. Het alarmschema wordt niet automatisch opnieuw aangemaakt of stilzwijgend gecorrigeerd.

Deze functionaliteit is volledig lokaal, verandert geen toestemmingen, sensordata, online service, release/keystore of echte hardwareacties. Code op [PR #5](https://github.com/Zennay/Alarm/pull/5): [91ba70d](https://github.com/Zennay/Alarm/commit/91ba70dfb38739bac0392094afe756309c3aec08), [93eac28](https://github.com/Zennay/Alarm/commit/93eac28f9ea3a848a68732ff6ad3c0962cf5c46d), [c6dd609](https://github.com/Zennay/Alarm/commit/c6dd609f57bd8819aed83cd1162c315da287156f). Het blijft **broncode, niet main of een uitgebrachte APK**. Alleen de bestaande automatische exacte-head checks, onafhankelijke eigenaarreview en echte Watch-acceptatie kunnen dit vrijgeven; handmatige QA-/regressiesuites zijn niet gestart.
