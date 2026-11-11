# Chip – Hinweise für Claude Code

Eigene Kalender-App für Android (Kotlin, Jetpack Compose, minSdk 26, targetSdk 37), privater
Gebrauch. Funktionen für Nutzer: `README.md`. Oberfläche, Texte, Commits und Doku auf Deutsch.
Offene Punkte: `TODO.md`. Aktueller Stand und offene Fragen: Abschnitt **Übergabe-Notizen** am Ende.

## Überblick

- **App-ID** `de.blaintino.chip` (Debug-Build `de.blaintino.chip.debug`, Name „Chip (Debug)“, läuft daneben).
- **Repo** `Blaintino/Chip` (GitHub meldet beim Push „repository moved“ auf die Schreibweise
  `Blaintino/Chip.git` – harmlos), Lizenz GPL-3.0.
- **Release**: GitHub Release `v<run_number>` mit `Chip-<run_number>.apk`, nur aus `main` und nur signiert.
  Kein App-Präfix im Tag, weil eigenes Repo.
- **Installation** beim Nutzer (Samsung Galaxy S26 Ultra): Obtainium mit der Repo-URL, **ohne Filter**.
  Cloud-Sessions haben weder Emulator noch Handy – geprüft wird nur im CI, auf dem Gerät prüft der Nutzer.
- **Signatur**: gleicher Keystore wie Quälgeist und FH Stempelkarte (wichtig für die `signature`-Berechtigung der
  Stempelkarte). Secrets im Repo: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`
  (`release`), `ANDROID_KEY_PASSWORD`. Werte stehen nirgends im Repo, der Nutzer setzt sie vom Mac per `gh secret set`
  (Befehle im README).
- **Verwandte eigene Apps**:
  - Quälgeist (`Blaintino/Quaelgeist`, `de.quaelgeist`, Beta `de.quaelgeist.beta`): exportiert seit v49
    (`Blaintino/Quaelgeist` PR #18) die Aktion `de.quaelgeist.action.QUICK_ADD`, die der Widget-Knopf „+ Quälgeist“ nutzt.
  - FH Stempelkarte (`Blaintino/FH-Stempelkarte`, `de.fhstempelkarte`): Schnittstelle in deren `INTEROP.md`.
  - `Blaintino/Calendar-fork`: früherer Versuch als Fork von Fossify Calendar (App-ID `de.calendarfork`, PR #1 nie
    gemergt). Aufgegeben zugunsten von Chip (Wunsch des Nutzers: mehr Kontrolle über Widget und Layouts; Fossify
    hängt an seiner Commons-Bibliothek mit „Fake-Version“-Prüfung für fremde App-IDs). Nicht weiterverfolgen.

## Code-Aufbau (`app/src/main/java/de/blaintino/chip/`)

| Paket/Datei | Inhalt |
|---|---|
| `MainActivity.kt` | Einstieg, Berechtigungen, Kopfzeile, Seiten-Schleife mit Gesten, Editor/Details, Intents (webcal, Widget-Plus, Erinnerung) |
| `QuaelgeistActivity.kt` | Sprungbrett für „+ Quälgeist“ aus dem Widget |
| `data/` | `CalendarRepository` (alles CalendarContract), `Repeat` (RRULE), `Reminders` (Vorgaben), `Prefs` (SharedPreferences), `Formats` |
| `ics/` | ICS lesen (`IcsParser`) und in Termin-Zeilen umsetzen (`IcsMapper`) |
| `sync/` | `SubscriptionSync` (Abos), `Background` (alle WorkManager-Jobs), `UpdateReceiver` |
| `reminder/` | `EventAlerts` + `EventReminderReceiver` (Benachrichtigungen) |
| `weather/` | Open-Meteo, Zwischenspeicher, `WeatherText` |
| `widget/` | Glance-Widget „Heute“, `WidgetCache`, `WidgetFilter` |
| `stempel/` | FH Stempelkarte: Status, Sichtbarkeit, Sprungbrett, Empfänger |
| `ui/` | Woche/Tag (`WeekScreen`), `MonthScreen`, `YearScreen`, Einstellungen (`CalendarsScreen`), `EventEditor`, Gesten (`Zoom`), Layout-Helfer (`DayLayout`, `SpanLayout`) |

Reine Logik liegt bewusst in eigenen Objekten ohne Android-Abhängigkeit und hat JVM-Tests in `app/src/test/`
(`RepeatTest`, `ReminderRulesTest`, `IcsTest`, `DayLayoutTest`, `SpanLayoutTest`, `CalendarZoomTest`,
`WidgetFilterTest`, `WeatherTextTest`, `StempelStatusTest`, `StempelVisibilityTest`; Zeitzone fest `Europe/Berlin`).
Neue Logik genauso aufteilen.

## Ablauf Branch → PR → Merge → Release

1. Auf dem Arbeitsbranch arbeiten (bisher `claude/vigilant-lamport-2afb5d`, andere Sessions nutzten
   `claude/<thema>`). Vor jedem neuen Zyklus auf `main` zurücksetzen:
   `git fetch origin main && git checkout -B <branch> origin/main`, dann `git push -f -u origin <branch>`.
2. Push startet im CI einen Test-Build (Tests + Lint + APK als Artefakt, kein Release). Lokal bauen scheitert in
   Cloud-Sessions meist an Maven Central (HTTP 429) – dann ist das CI die einzige Prüfung, also Importe und
   Opt-ins sorgfältig prüfen. Stand abfragen:
   `curl -s "https://api.github.com/repos/Blaintino/Chip/actions/runs?head_sha=<sha>"`.
3. Erst wenn der Nutzer „Release“ sagt: PR nach `main` anlegen und mergen (Merge-Commit). Der Lauf auf `main`
   erstellt das Release `v<run_number>`; dem Nutzer die Nummer nennen.
4. Reine Doku-Commits mit `[skip ci]`, damit kein Build läuft.
5. Commit-Nachrichten deutsch. Keine Modellnamen in Commits/PRs.

## Feste Entscheidungen des Nutzers (nicht ohne Rückfrage ändern)

- Release nur auf Ansage; Kleinigkeiten ohne eigenen Build sammeln (`TODO.md`).
- Datenquelle nur `CalendarContract`, Teilen über Google. ICS-Abos mit täglicher Aktualisierung sind Pflicht.
- Wetter: Ort nur per Namenssuche, **keine Standort-Berechtigung**; **kein Wettersymbol**; Schrift 16 sp; Regen als
  zweite, hellblaue Zeile; Antippen öffnet Chip.
- Oberfläche ohne Tabs, Startansicht Woche mit 8–18 Uhr sichtbar, „Heute“ mittig, ⚙ rechts.
- Plus-Knöpfe: zwei Kalender („Langners“, „Arbeit“), nebeneinander, „+ “ und drei Buchstaben, neuer Termin heute
  zur nächsten vollen Stunde.
- Editor: nur beschreibbare und eingeschaltete Kalender.
- Kalenderfarben und Ausblenden nur in Chip, nie am Konto.
- Widget: keine Kopfzeile, Schwarz nur hinter Terminen und nur so hoch wie nötig, nur laufende/kommende Termine, ohne Termine Knöpfe oben,
  „+ Quälgeist“ in der Material-You-Hauptfarbe, Stempel-Knopf nur im Kontext.
- Mehrtägige Termine: Monat dünne Balken direkt unter der Zahl, Jahr Tage voll eingefärbt.
- Erinnerungen: höchstens 3 je Termin; Vorgaben in den Einstellungen (ab Werk 1 Tag / 1 Std. / 15 Min.) gelten für
  jeden **neuen** Termin und werden nur im Termin selbst geändert oder gelöscht.
- Ziel des Nutzers: Chip soll den Google Kalender auf dem Handy ersetzen (dann wird die Google-App gelöscht).

## Gelernte Fallen

- `BoxWithConstraints`-Inhalt läuft erst beim Layout: keine Schleifenvariablen darin lesen (Fehler in v53).
- Glance: Daten in der Komposition laden; vor App-Updates platzierte Widgets brauchen einen Anstoß
  (`UpdateReceiver`, App-Start). Flackern durch „noch nicht geladen“ vermeiden (`WidgetCache`).
- Glance-`Row`: Elemente ohne Gewicht werden zuerst gemessen – ein breiter Text verdrängte den Stempel-Knopf, bis
  die Pille `defaultWeight` bekam (v78).
- CI-Fehler, die lokal niemand sah: fehlendes `@OptIn(ExperimentalMaterial3Api::class)`, fehlende Importe,
  Lint `ByteOrderMark` (BOM als `'\uFEFF'` schreiben, nie als Zeichen).
- CalendarContract: ganztägig = Mitternacht UTC, `DTEND` exklusiv; Tage aus `START_DAY/END_DAY`; Serien mit
  `DURATION` statt `DTEND`; Ausnahmen über `CONTENT_EXCEPTION_URI`; `CALENDAR_COLOR` darf nur der Sync-Adapter ändern.
- Implizite Broadcasts: `EVENT_REMINDER` ist ausgenommen und erreicht den Manifest-Empfänger.
- Cloud-Session: Klon ist flach und ohne Tracking-Ref – nach dem Checkout `git branch -u origin/<branch>`,
  sonst meldet der Stop-Hook „unpushed commits“. `docs/cloud/repos.init.gradle` biegt Maven Central auf
  `repo1.maven.org` um (`./gradlew --init-script docs/cloud/repos.init.gradle …`), hat aber nicht verlässlich geholfen.
- Mockups als HTML: in JS bei Bit-Schiebereien für Indizes `>>>` statt `>>` (negative Werte).

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
- Zuletzt auf dem Handy bestätigt: v59 (2026-11-02; Monat-Balken unter der Zahl, Jahr voll eingefärbt, Wetter 16 sp;
  v55: mehrtägige Termine, Monat/Jahr-Schalter, Monatsfix; v51: Wischen/Pinch mit Rückmeldung, Zoom zum Tag unter den
  Fingern, Wetter-Pille, Heute-Knopf mittig) und v61/v63 (Stempel-Knopf). Neuere Releases siehe Übergabe-Notizen.
- Auf dem Handy (Samsung S26 Ultra) vom Nutzer bestätigt (v27, 2026-11-02; Widget ohne Kopfzeile nur mit
  anstehenden Terminen, Plus-Knöpfe nebeneinander; davor v21): Kalender, Woche/Tag/
  Monat/Jahr, Pinch, Wischen, Heute-Knopf, Plus-Knöpfe, langes Drücken, Oberfläche ohne Tabs,
  Kalenderfarben, Widget mit Knöpfen (auch „+ Quälgeist“ mit Quälgeist v49), ICS-Abo hinzufügen und
  laden. Tägliche Abo-Aktualisierung im Hintergrund noch nicht beobachtet. Cloud-Sessions haben weder
  Emulator noch Handy.
- Arbeitsweise (Wunsch des Nutzers): Wünsche sammeln, auf dem Arbeitsbranch umsetzen (CI dort nur
  Test-Build), Release (= Merge nach `main`) erst auf Ansage.
  Kleinigkeiten ohne eigenen Build stehen in `TODO.md` und werden mit dem nächsten Build abgearbeitet.

## Aufbau und Regeln

- **Datenquelle ist ausschließlich `CalendarContract`.** Google-Kalender (auch geteilte) kommen über
  das Google-Konto des Handys. Freigaben verwalten geht nur beim Anbieter, nicht in Chip.
- Ausblenden (`HiddenCalendars`) gilt nur in Chip (App + Widget), nie `Calendars.VISIBLE` ändern.
  Zusätzlich je Kalender „Monat/Jahr“ (`OverviewHiddenCalendars`, Wunsch des Nutzers: Stundenplan-Abo
  überdeckte dort alles): aus = nur in Tag, Woche und Widget.
- **Mehrtägige Termine** (Wunsch des Nutzers): `isSpanning()` = ganztägig über mehrere Tage oder ≥ 24 h
  (Nachttermine bleiben im Raster). Woche/Tag: Leiste oben mit durchgehenden Balken über alle Tage
  (`SpanLayout.lanes`, Tests `SpanLayoutTest`; runde Enden nur bei echtem Beginn/Ende, Tag zeigt „(k/n)“),
  Monat: verbundene dünne Balken direkt unter der Tageszahl (Punkte rutschen darunter), Jahr: Tage voll in der
  Terminfarbe als durchgehendes Band (Wunsch des Nutzers, Test statt Unterstrich).
  Achtung (Fehler in v53, vom Nutzer gemeldet): Inhalte von `BoxWithConstraints` laufen erst beim Layout –
  dort nie Schleifenvariablen (`var weekStart`) lesen, sondern je Durchlauf feste Werte (`ws`).
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
  `calendarGestures` mit **laufender Rückmeldung** (Wunsch des Nutzers, Mockup-Artefakt „Chip Gesten-Mockup“
  https://claude.ai/artifact/5KxJ5e7qXsKwaBnQuxLv49, Kopie in `docs/mockups/chip-gesten.html` mit Bildern):
  Wischen verschiebt die Ansicht mit dem Finger (`dragX`, Nachbarn links/rechts nur während des Wischens
  gezeichnet), Loslassen nach ¼ Breite oder mit Schwung blättert (`settleSwipe`, 280 ms), sonst zurück;
  Pfeile im Kopf animieren genauso (`select` erkennt Sprünge um genau einen Zeitraum). Pinch skaliert
  aktuelle und Ziel-Ansicht live (aufziehen: Ziel wächst aus dem Fingermittelpunkt; ab 45 % Fortschritt
  wird beim Loslassen gewechselt, `settlePinch`). Gezoomt wird zu dem Tag/Monat, über dem die Geste beginnt (Wunsch des
  Nutzers): die aktuelle Ansicht meldet ihre Tages-/Monatsbereiche in `DayBounds` (`LocalDayBounds`,
  Woche nur waagerecht), `pinchDay` wird beim Wechsel zu `selectedDay`. Alle Seiten in einer Schleife mit `key(Stufe, Datum)`,
  damit Nachbar/Ziel beim Übernehmen ihren Zustand behalten (Initial-Durchgang, verbraucht erst nach erkannter Geste, damit senkrechtes Scrollen
  und Tippen unberührt bleiben und ein Wischen kein Tippen auslöst). Tag = `WeekScreen` mit
  `dayCount = 1`. Alle Stufen folgen `selectedDay` (auch die Monatsnavigation), damit Zoomen beim
  selben Datum bleibt. Tippen auf eine freie Stelle im
  Raster legt dort einen Termin an.
- Bearbeiten nur in beschreibbaren Kalendern (`CAL_ACCESS_CONTRIBUTOR`+), sonst `EventDetails` mit Sprung
  in die Kalender-App.
- **Serien** (Wunsch des Nutzers, 2026-11-09): Editor „Wiederholen“ mit `RepeatKind` (täglich, wöchentlich,
  alle 2 Wochen, monatlich am Datum / am n-ten (1–4) / am letzten Wochentag) und „Endet“ (UNTIL; ganztägig als
  Datum, sonst Tagesende in UTC). Umsetzung in `RepeatRules` (`data/Repeat.kt`, Tests `RepeatTest`); alles, was
  nicht genau passt (COUNT, mehrere Wochentage, YEARLY, RDATE …), ist `CUSTOM` und bleibt roh erhalten.
  Serien in `Events` mit `RRULE` + `DURATION`, `DTEND` null. Der Editor zeigt das angetippte Vorkommen
  (`eventForInstance`); Speichern/Löschen fragt den Umfang (`SeriesScope`):
  - **Nur dieser**: Ausnahme-Zeile über `Events.CONTENT_EXCEPTION_URI` mit `ORIGINAL_INSTANCE_TIME`
    (= `Instances.BEGIN`), Löschen = Ausnahme mit `STATUS_CANCELED`. Einzeln geänderte Termine
    (`ORIGINAL_ID`) werden direkt geändert bzw. abgesagt, haben keine eigene Wiederholung.
  - **Dieser und folgende**: alte Serie bekommt UNTIL vor dem Vorkommen (`RepeatRules.endBefore`, COUNT
    fällt weg), spätere Ausnahme-Zeilen werden gelöscht, ab hier eine neue Serie. Beim ersten Termin = ganze Serie.
  - **Alle**: Verschiebung des Vorkommens (Wanduhrzeit) wird auf den ersten Termin übertragen; Regel nur neu
    gebaut, wenn sich Art/Ende oder der Tag geändert hat. Eine andere Wiederholungsart geht nur „ab hier“
    (sonst passte z. B. „monatlich am 9.“ nicht zum ersten Termin).
  Noch nicht auf dem Gerät geprüft, vor allem wie Google-Sync die Ausnahmen übernimmt.
- **Erinnerungen** (Wunsch des Nutzers, 2026-11-09: Chip soll den Google Kalender ersetzen): bis zu 3 je Termin
  (`ReminderRules.MAX`), `EventDraft.reminders` (Minuten vorher). Gespeichert in `CalendarContract.Reminders`
  (`METHOD_ALERT`; nur ALERT/DEFAULT werden ersetzt, E-Mail-Erinnerungen aus Google bleiben), bei allen
  Speicherwegen inkl. Ausnahme-Zeilen und neuer Serie „ab hier“. Neue Termine übernehmen `DefaultReminders`
  (Einstellungen, ab Werk 1 Tag / 1 Std. / 15 Min.); bestehende Termine behalten ihre eigenen, Abo-Termine haben keine.
  Anzeigen: Der Kalender-Provider plant die Zeitpunkte selbst und sendet `EVENT_REMINDER` (Ausnahme von der
  Sperre für implizite Broadcasts); `EventReminderReceiver` → `EventAlerts.showDue` liest `CalendarAlerts`
  (Alarmzeit bis 30 min zurück), zeigt je eine Benachrichtigung (Kanal `termine`, hoch), merkt sich gezeigte
  (Schlüssel Termin:Beginn:Alarmzeit, 2 Tage) und setzt `STATE_FIRED`. Tippen öffnet Chip am Tag des Termins
  (`EXTRA_DAY`). `POST_NOTIFICATIONS` wird einmal beim Start erfragt; Status und letzte Erinnerung in den
  Einstellungen. Doppelte Meldungen, solange Google Kalender / Samsung Kalender selbst benachrichtigen.
  Noch nicht auf dem Gerät geprüft.
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
- Knopf „Heute“ mittig zwischen Titel und Zahnrad (Wunsch des Nutzers) setzt `selectedDay` auf heute.
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
  nur hinter den Terminen; ohne anstehende Termine nur die Knopfreihe, dann **oben** (Wunsch des Nutzers; kein Hinweistext; ein Widget kann
  seine Größe nicht selbst ändern). **Nur so hoch wie der Inhalt** (Wunsch des Nutzers, 2026-11-11, v80): alles
  steht oben, die schwarze Fläche endet nach dem letzten Termin, die Knöpfe folgen direkt darunter; reicht der Platz
  nicht, scrollt die Liste. Technik: Spalte ohne feste Höhe, Terminfläche mit `defaultWeight` (Höhe 0) – LinearLayout
  misst sie zuerst in Inhaltsgröße und kürzt nur bei Platzmangel (bei `targetSdk` > 23). feste helle Schrift (Wunsch des Nutzers),
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
- **Wetter im Widget** (Wunsch des Nutzers): eigene schwarze Pille (`WeatherPill`, so breit wie der Text,
  mittig, 40 dp hoch bzw. höher mit Regenzeile, Schrift 16 sp) über der Terminfläche bzw. ohne Termine über den Knöpfen (Antippen öffnet Chip), **kein Wettersymbol** (gab es kurz, v40–v42;
  vom Nutzer wieder gestrichen: stündlicher WMO-Code nicht eindeutig einem Zeitraum zuzuordnen, zeigte
  Regen, wo andere Apps keinen sahen), aktuelle Temperatur (stündliche Vorhersage der laufenden
  Stunde), Höchst-/Tiefstwert und Regenhinweis (`WeatherText`; Regen als **zweite Zeile** in Hellblau und fett,
  Wunsch des Nutzers 2026-11-11, die Pille wird dann höher; `line()` trennt mit `\n`,
  Schwelle 40 % für die restlichen Stunden, Tests `WeatherTextTest`) (siehe Pille oben). Quelle Open-Meteo (frei, ohne Schlüssel,
  Namensnennung in den Einstellungen), Ort **nur per Namenssuche** in den Einstellungen – ausdrücklich
  keine Standort-Berechtigung. Antwort in SharedPreferences `weather`, höchstens stündlich neu
  (`Weather.today`, `WeatherWorker` stündlich mit Netz); bei Netzfehler bleibt der letzte Stand von heute.
  Abrufstatus (`Weather.lastStatus`) und heutige Werte stehen in den Einstellungen, Knopf „Wetter jetzt
  laden“. Auf dem Gerät bestätigt (v37, 2026-11-02); in v35 erschien das Wetter zunächst nicht, nach
  „Wetter jetzt laden“ lief es – Ursache unklar (vermutlich Widget nicht neu gezeichnet oder erster Abruf
  fehlgeschlagen).
- **Stempel-Knopf im Widget** (Wunsch des Nutzers, 2026-11-03): Anbindung an die eigene App FH Stempelkarte
  (`Blaintino/FH-Stempelkarte`, Vertrag dort in `INTEROP.md`), `stempel/Stempelkarte.kt`. Rechts in der Zeile der
  Wetter-Pille (`TopRow`; mit Knopf steht das Wetter links und füllt mit `defaultWeight` nur den Rest der Zeile –
  vorher schob ein langer Regenhinweis den Knopf aus dem Widget, vom Nutzer gemeldet 2026-11-11; ohne Stempelkarte
  wie früher mittig und so breit wie der Text): nicht
  eingeloggt ein **rundes Symbol** (`ic_stempel_login`, 40 dp; vorher Text „Einloggen“ – dem Nutzer dauerhaft zu
  aufdringlich, eine sinnvolle Regel zum Ausblenden fiel ihm nicht ein) in der Farbe der Stempelkarte (rot bei
  `NICHT_EINGELOGGT`, sonst schwarz wie die Pille). **Nur im Kontext sichtbar** (Wunsch des Nutzers, Varianten
  „Stempelkarte“ + „Kalender“, `StempelVisibility`, Tests `StempelVisibilityTest`): Stempelkarte nicht `NEUTRAL`
  (erkannt/erinnert oder eingeloggt), oder heute zwischen 1 h vor dem ersten und 1 h nach dem letzten nicht ganztägigen Termin der
  Arbeitskalender (Name enthält „Stundenplan“ oder „Arbeit“, auch in Chip ausgeblendete). Neuzeichnen zu den
  Fenstergrenzen über `scheduleWidgetRefreshAt` (früheste von Terminende und Fenstergrenze).
  „Heute schon gearbeitet → bis Mitternacht sichtbar“ gab es kurz, vom Nutzer gestrichen (das Terminfenster reicht). Eingeloggt grün mit
  der **heutigen Arbeitszeit** (Summe aller Sitzungen, rechnet die Stempelkarte), Tippen = ausloggen. Die Zeit
  ist ein `Chronometer` per `AndroidRemoteViews` (`res/layout/widget_chronometer.xml`), Basis aus
  `arbeit_heute_ms` + `arbeit_laeuft_seit` – zählt im Launcher selbst, **kein minütliches Neuzeichnen** (Akku;
  Nachteil: zeigt Sekunden). **Netto nach § 4 ArbZG** (Wunsch des Nutzers, 2026-11-04; gleiche Regel wie
  `WorkTime.net` der Stempelkarte, `StempelStatus.workedMs`, Tests `StempelStatusTest`): > 6 h → 30 min, > 9 h →
  45 min Pause insgesamt, genommene Pausen (`arbeit_pausen_ms`, ausgeloggt ≥ 15 min) zählen; die fehlende Pause
  wird gleitend abgezogen – der Chronometer **steht** dann (`paused`, `setChronometer(…, started = false)`), das
  Widget zeichnet zu `StempelStatus.nextChange` neu (kann im Doze etwas später kommen). Ohne bekannten Beginn „Eingeloggt“. Sprungbrett `StempelkarteActivity` startet
  `de.fhstempelkarte.action.EINLOGGEN`/`AUSLOGGEN` (dann ZEUS), Fehler als Toast. `StempelReceiver`
  (`ZUSTAND_GEAENDERT`, nur mit Berechtigung) zeichnet neu; Status auch in `WidgetCache`. Alles hinter der
  `signature`-Berechtigung `de.fhstempelkarte.permission.INTEROP` (gleicher Keystore): **Debug-Chip sieht den
  Knopf nie**; Stempelkarte muss vor Chip installiert sein (v17+), Arbeitszeit braucht die Stempelkarte mit
  `WorkTime`. `WidgetStatus` nennt den gelesenen Stempelkarten-Zustand.
  v61/v63 auf dem Gerät (2026-11-03): Timer und Zeit laufen, das Nachziehen nach dem Stempeln dauert eine Weile
  (für den Nutzer in Ordnung). Anfangs (v61) war kein Timer zu sehen. Deshalb zweiter
  Weg fürs Neuzeichnen: WorkManager-Content-URI-Auslöser auf den Status-Provider (`Background.watchStempelkarte`,
  `StempelChangeWorker`, meldet sich nach jedem Lauf neu an) zusätzlich zum Broadcast. Fehlersuche ohne adb:
  Abschnitt „FH Stempelkarte“ in den Einstellungen (`Stempelkarte.describe`: lesbar?, Zustand, Arbeitszeit,
  Beginn bekannt?, letzte Wechselmeldung und woher – Broadcast oder Provider).
- Neue Abhängigkeiten nur mit gutem Grund. Aktuell: Compose, Core-KTX, Activity-Compose,
  Lifecycle, WorkManager, Glance.
- Debug-Build: App-ID `de.blaintino.chip.debug`, Name über den Manifest-Platzhalter `appLabel`.

## Mögliche nächste Schritte (nur auf Nachfrage)

- Widget-Gestaltung (Farben, Schriftgröße, Zeitraum, Kalenderauswahl je Widget).
- Teilnehmer einladen; „Später erinnern“ an der Benachrichtigung.
- Eigenes App-Icon.

## Übergabe-Notizen (Stand 2026-11-11)

- **Letzter Release: v80** (2026-11-11, PR #28: Widget nur so hoch wie der Inhalt + diese Doku). Davor v78
  (PR #27: Regenhinweis als zweite Zeile, Stempel-Knopf wird nicht mehr verdrängt) und v75 (PR #26, Erinnerungen) und v73 (PR #25, Serientermine), beide 2026-11-09.
- **Auf dem Gerät bestätigt** bis v59 sowie der Stempel-Knopf (v61/v63). Der Nutzer hat am 2026-11-11 ein Widget mit
  v75 oder älter gezeigt (Regenzeile verdrängte den Knopf, behoben in v78). **Nicht ausdrücklich bestätigt:** Serien
  (v73), Erinnerungen (v75), Regenzeile (v78), Widgethöhe nach Inhalt (v80). Der Nutzer wollte keine Prüfliste („ich melde mich, wenn was nicht
  funktioniert“).
- **Halbfertig im Code:** nichts. Alle Arbeit ist in `main`.
- **Branches:** `main` = v80. `claude/vigilant-lamport-2afb5d` = `main` plus Doku-Nachträge (`[skip ci]`, ohne PR;
  gehen mit dem nächsten Release mit). Keine weiteren Branches.
- **Arbeitsmodus seit 2026-11-11 (Wunsch des Nutzers): Sammeln ohne Programmieren.** Neue Wünsche nur in
  `TODO.md` aufnehmen (mit Entscheidungen), nichts umsetzen, bis der Nutzer ausdrücklich sagt, dass gebaut wird.
- **Größte Unsicherheiten** (siehe `TODO.md`):
  - Ob Android/Samsung `EVENT_REMINDER` pünktlich an Chip liefert. Falls nicht, ist der Vorschlag: Chip plant eigene
    Alarme (`setExactAndAllowWhileIdle`, nicht `setAlarmClock` – sonst steht jeder Termin als Wecker in der Statusleiste).
  - Wie Google-Sync von Chip angelegte Serien-Ausnahmen übernimmt.
- **Offene Fragen an den Nutzer:**
  - Kommen Erinnerungen pünktlich und einmal (Google-/Samsung-Kalender-Benachrichtigungen schon aus)?
    Ist Chip in den Samsung-Akku-Einstellungen von der Optimierung ausgenommen?
  - Wurde die tägliche Abo-Aktualisierung im Hintergrund inzwischen beobachtet?
- **Ausgelagert:** Geteilte ToDo-/Einkaufsliste – der Nutzer hält das eher für eine eigene App; Diskussion läuft in
  einer eigenen Session („Geteilte ToDo-/Einkaufsliste – Konzept“, https://claude.ai/code/session_012fSuJWzu9Bsebas3TtG27W).
  Für Chip nichts entschieden.

