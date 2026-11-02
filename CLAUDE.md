# Chip – Hinweise für Claude Code

Eigene Kalender-App für Android (Kotlin, Jetpack Compose, minSdk 26, targetSdk 37), privater
Gebrauch. Funktionen und Dateiübersicht: `README.md`. Oberfläche, Texte, Commits und Doku auf Deutsch.

## Werkzeuge

- Toolchain wie `android-vorlage` (Monorepo `Claude_Code_Projekte`): Gradle 9.8, AGP 9.4 mit
  eingebautem Kotlin (kein `org.jetbrains.kotlin.android`-Plugin, nur
  `org.jetbrains.kotlin.plugin.compose`), Versionen in `gradle/libs.versions.toml`, JDK 21.
- Befehle: `./gradlew testDebugUnitTest lintDebug assembleDebug`. Unit-Tests: `IcsTest`, `DayLayoutTest` (ICS-Leser
  und -Umsetzung, Spalten der Wochenansicht, reines Kotlin, Zeitzone im Test fest `Europe/Berlin`).
- `.github/workflows/build-apk.yml`: Tests + Lint, signierte Release-APK aus den Secrets
  `ANDROID_KEYSTORE_BASE64` usw. (gleicher Keystore wie Quälgeist), nur auf `main` mit Secrets ein
  Release `v<run_number>` (eigenes Repo, daher ohne App-Präfix; Obtainium ohne Filter). Ohne Secrets Debug-APK mit Warnung. Nie auf
  Debug-Signatur „umstellen“. Keystore nie einchecken oder in einer Cloud-Session erzeugen.
- In Cloud-Sessions drosselt Maven Central oft (HTTP 429), lokal bauen geht dann nicht; der Build
  läuft verlässlich im CI.
- Auf dem Handy (Samsung S26 Ultra) vom Nutzer bestätigt (v27, 2026-11-02; Widget ohne Kopfzeile nur mit
  anstehenden Terminen, Plus-Knöpfe nebeneinander; davor v21): Kalender, Woche/Tag/
  Monat/Jahr, Pinch, Wischen, Heute-Knopf, Plus-Knöpfe, langes Drücken, Oberfläche ohne Tabs,
  Kalenderfarben, Widget mit Knöpfen (auch „+ Quälgeist“ mit Quälgeist v49), ICS-Abo hinzufügen und
  laden. Tägliche Abo-Aktualisierung im Hintergrund noch nicht beobachtet. Cloud-Sessions haben weder
  Emulator noch Handy.
- Arbeitsweise (Wunsch des Nutzers): Wünsche sammeln, auf dem Arbeitsbranch umsetzen (CI dort nur
  Test-Build), Release (= Merge nach `main`) erst auf Ansage.

## Aufbau und Regeln

- **Datenquelle ist ausschließlich `CalendarContract`.** Google-Kalender (auch geteilte) kommen über
  das Google-Konto des Handys. Freigaben verwalten geht nur beim Anbieter, nicht in Chip.
- Ausblenden (`HiddenCalendars`) gilt nur in Chip (App + Widget), nie `Calendars.VISIBLE` ändern.
- Eigene Kalenderfarben (`CalendarColors`, Wunsch des Nutzers) nur in Chip: `CALENDAR_COLOR` darf nur
  der Sync-Adapter ändern (Google würde es überschreiben). Angewendet in `CalendarRepository`
  (`calendars()`/`instances()`); Termine mit eigener `EVENT_COLOR` behalten ihre Farbe.
  Im Editor sind nur beschreibbare **und** eingeschaltete Kalender wählbar (Wunsch des Nutzers,
  `editorCalendars`); beim Bearbeiten bleibt der bisherige Kalender des Termins wählbar.
- Oberfläche (Wunsch des Nutzers): **keine Tabs** mehr (Termine-Liste entfernt). Oben: Titel = aktuelle
  Ansicht, Antippen öffnet ein Menü Tag/Woche/Monat/Jahr (Fallback zur Geste), daneben „Heute“, rechts
  ⚙ = Einstellungen (`CalendarsScreen`, Zurück schließt). Startansicht ist die **Woche** (`WeekScreen`, Stundenraster,
  Überschneidungen über `DayLayout`, Tests `DayLayoutTest`). Beim Öffnen ist 8–18 Uhr
  sichtbar (Wunsch des Nutzers): Stundenhöhe = verfügbare Höhe / 10, nicht fest codiert. Ein Tab mit vier Detailstufen
  `CalendarZoom` (Tag/Woche/Monat/Jahr, `Zoom.kt`): **Pinch** (zwei Finger aufziehen = feiner,
  zusammenschieben = gröber, eine Stufe je Geste) und **Wischen** mit einem Finger (links = nächster,
  rechts = vorheriger Zeitraum, `CalendarZoom.shift`; Wunsch des Nutzers) laufen zusammen in
  `calendarGestures` (Initial-Durchgang, verbraucht erst nach erkannter Geste, damit senkrechtes Scrollen
  und Tippen unberührt bleiben und ein Wischen kein Tippen auslöst). Tag = `WeekScreen` mit
  `dayCount = 1`. Alle Stufen folgen `selectedDay` (auch die Monatsnavigation), damit Zoomen beim
  selben Datum bleibt. Tippen auf eine freie Stelle im
  Raster legt dort einen Termin an.
- Bearbeiten nur für Einzeltermine in beschreibbaren Kalendern (`CAL_ACCESS_CONTRIBUTOR`+).
  Serien (RRULE/RDATE) bewusst nur lesend mit Sprung in die Kalender-App (`EventDetails`) – das
  Ändern einzelner Serientermine braucht Ausnahme-Zeilen und ist ein eigener Schritt.
- Ganztägige Termine: in `Events` Mitternacht UTC und Zeitzone `UTC`, `DTEND` exklusiv. Der Editor
  zeigt den letzten Tag; Umrechnung in `CalendarRepository.event/save`. Tage von Vorkommen aus
  `Instances.START_DAY/END_DAY` (julianisch), nicht aus `BEGIN/END`.
- Plus-Knöpfe (Wunsch des Nutzers): je Schnellauswahl-Kalender ein `ExtendedFloatingActionButton` in
  Kalenderfarbe (nebeneinander, Beschriftung „+ “ und die ersten drei Buchstaben des Kalendernamens) (`QuickAddCalendars`, 2 Plätze, IDs in SharedPreferences, beim ersten Start nach Namen
  „Langners“/„Arbeit“ vorbelegt, in den Einstellungen änderbar; leer = einfaches Plus). Neuer Termin per
  Plus: **heute** zur nächsten vollen Stunde (nicht der gewählte Tag); Tippen ins Raster: dort.
- Lang drücken (Wunsch des Nutzers): Tag/Woche → Zeitwähler unter dem Daumen (Viertelstunden, Ziehen
  auch über die Tage, Haptik, Loslassen öffnet den Editor); kurz tippen weiter = volle Stunde. Beides
  in einer Geste am Raster (`WeekScreen`), damit Tippen und langes Drücken sich nicht doppeln;
  `calendarGestures` wertet nach der Long-Press-Zeit kein Wischen mehr. Monat → langes Drücken auf
  einen Tag öffnet „Neuer Termin“ (heute: nächste volle Stunde, sonst 9:00).
- Knopf „Heute“ neben dem Titel (nur Tab Tag/Woche/Monat/Jahr) setzt `selectedDay` auf heute.
- **ICS-Abos sind Pflichtfunktion** (Wunsch des Nutzers, tägliche Aktualisierung):
  - Jedes Abo = eigener lokaler Kalender (Konto `Chip-Abos`, Typ `ACCOUNT_TYPE_LOCAL`, nur lesbar),
    angelegt und befüllt als Sync-Adapter (`CALLER_IS_SYNCADAPTER`). Abo-Liste in
    `SubscriptionStore` (SharedPreferences, Schlüssel = Kalender-ID, `commit()`).
  - Aktualisieren: Datei laden (ETag/Last-Modified, 304 = unverändert, Weiterleitungen inkl.
    http→https, max. 20 MB), komplett lesen, **erst dann** alle Termine des Kalenders ersetzen –
    ein Fehler lässt die alten Termine stehen und steht als `lastError` am Abo.
  - Serien: `DURATION` statt `DTEND` (CalendarContract-Pflicht). Geänderte Einzeltermine
    (`RECURRENCE-ID`) werden zu EXDATE am Serientermin + eigenständigem Termin, abgesagte nur zu
    EXDATE. Bewusst nicht über `ORIGINAL_SYNC_ID`.
  - Zeitzonen: IANA-Name, Mozilla-Präfix, Windows-Name (ICU), sonst `X-WR-TIMEZONE`, sonst Gerät.
  - WorkManager: täglich mit Netz (`Background.schedule`, auch bei jedem App-Start), beim Öffnen
    nachholen, wenn ein Abo über einen Tag alt ist.
- **Widget „Heute“** (Glance): ohne Kopfzeile/Datum (Wunsch des Nutzers), Widget durchsichtig mit reinem Schwarz
  nur hinter den Terminen; ohne anstehende Termine nur die Knopfreihe, dann **oben** (Wunsch des Nutzers; mit Terminen unten; kein Hinweistext; ein Widget kann
  seine Größe nicht selbst ändern), feste helle Schrift (Wunsch des Nutzers),
  scrollbare Liste mit Uhrzeit, Titel, Ort; **nur laufende und kommende Termine** (Wunsch des Nutzers,
  `WidgetFilter`, Tests `WidgetFilterTest`; ganztägige bleiben), Neuzeichnen zum nächsten Terminende
  (`Background.scheduleWidgetRefreshAt`, WorkManager, kann im Doze-Modus etwas später kommen). Unten Knöpfe: die Plus-Knöpfe aus `QuickAddCalendars`
  (öffnen `MainActivity` mit `NewEventIntents`, eigene Daten-URI je Kalender gegen zusammengelegte
  PendingIntents) und „+ Quälgeist“ (Farbe `GlanceTheme.colors.primary` = Material-You-Hauptfarbe wie die Überschriften
  und Heute-Markierung in der App, Wunsch des Nutzers) über das Sprungbrett `QuaelgeistActivity`: Aktion
  `de.quaelgeist.action.QUICK_ADD` (Quälgeist, dann Beta), sonst Quälgeist starten, sonst Hinweis;
  `<queries>` für beide Pakete im Manifest. Neu zeichnen über `TodayWidget.refresh`: nach eigenen Änderungen,
  nach Abo-Updates, bei jeder Änderung in der Kalenderdatenbank (WorkManager-Content-URI-Auslöser,
  der sich nach jedem Lauf neu anmeldet) und kurz nach Mitternacht.
  **Kein Flackern** (vom Nutzer gemeldet: schwarze Fläche, Knöpfe springen): Start mit dem letzten Inhalt
  aus `WidgetCache` (Speicher + SharedPreferences), „noch nichts geladen“ sieht aus wie „keine Termine“;
  `CalendarChangeWorker` zeichnet höchstens einmal pro Minute neu.
  Daten werden **innerhalb der Komposition** geladen (`LaunchedEffect` auf `refreshTick`), nicht vor
  `provideContent`: Läuft die Glance-Sitzung schon, ruft ein Update `provideGlance` nicht erneut auf.
  Fehler erscheinen als Text im Widget; `WidgetStatus` (Zeit + Meldung) steht in den Einstellungen mit
  Knopf „Widget neu zeichnen“ – zur Fehlersuche am Handy ohne adb. Nutzer meldete 2026-11-02 eine
  dauerhafte Ladeanzeige; Status war „noch nie gemeldet“, nach Entfernen und neu Platzieren lief es.
  Vermutung (nicht belegt): vor App-Updates platzierte Widgets wurden nicht neu angestoßen. Deshalb
  zeichnet `UpdateReceiver` (MY_PACKAGE_REPLACED, BOOT_COMPLETED) und jeder App-Start das Widget neu.
- **Wetter im Widget** (Wunsch des Nutzers): zentrierte Zeile (Antippen öffnet Chip), **kein Wettersymbol** (gab es kurz, v40–v42;
  vom Nutzer wieder gestrichen: stündlicher WMO-Code nicht eindeutig einem Zeitraum zuzuordnen, zeigte
  Regen, wo andere Apps keinen sahen), aktuelle Temperatur (stündliche Vorhersage der laufenden
  Stunde), Höchst-/Tiefstwert und Regenhinweis (`WeatherText`,
  Schwelle 40 % für die restlichen Stunden, Tests `WeatherTextTest`) als erste Zeile der Terminfläche,
  ohne Termine als eigene kleine Fläche über den Knöpfen. Quelle Open-Meteo (frei, ohne Schlüssel,
  Namensnennung in den Einstellungen), Ort **nur per Namenssuche** in den Einstellungen – ausdrücklich
  keine Standort-Berechtigung. Antwort in SharedPreferences `weather`, höchstens stündlich neu
  (`Weather.today`, `WeatherWorker` stündlich mit Netz); bei Netzfehler bleibt der letzte Stand von heute.
  Abrufstatus (`Weather.lastStatus`) und heutige Werte stehen in den Einstellungen, Knopf „Wetter jetzt
  laden“. Auf dem Gerät bestätigt (v37, 2026-11-02); in v35 erschien das Wetter zunächst nicht, nach
  „Wetter jetzt laden“ lief es – Ursache unklar (vermutlich Widget nicht neu gezeichnet oder erster Abruf
  fehlgeschlagen).
- Neue Abhängigkeiten nur mit gutem Grund. Aktuell: Compose, Core-KTX, Activity-Compose,
  Lifecycle, WorkManager, Glance.
- Debug-Build: App-ID `de.blaintino.chip.debug`, Name über den Manifest-Platzhalter `appLabel`.

## Mögliche nächste Schritte (nur auf Nachfrage)

- Widget-Gestaltung (Farben, Schriftgröße, Zeitraum, Kalenderauswahl je Widget).
- Serien bearbeiten, Erinnerungen setzen, Teilnehmer einladen.
- Eigenes App-Icon.
