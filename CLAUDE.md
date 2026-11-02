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
- Auf dem Handy installiert (v6, 2026-11-02): Kalender werden angezeigt. Abos, Widget und Editor
  dort noch nicht bestätigt. Cloud-Sessions haben weder Emulator noch Handy.

## Aufbau und Regeln

- **Datenquelle ist ausschließlich `CalendarContract`.** Google-Kalender (auch geteilte) kommen über
  das Google-Konto des Handys. Freigaben verwalten geht nur beim Anbieter, nicht in Chip.
- Ausblenden (`HiddenCalendars`) gilt nur in Chip (App + Widget), nie `Calendars.VISIBLE` ändern.
  Im Editor sind nur beschreibbare **und** eingeschaltete Kalender wählbar (Wunsch des Nutzers,
  `editorCalendars`); beim Bearbeiten bleibt der bisherige Kalender des Termins wählbar.
- Ansichten (Wunsch des Nutzers): Start-Tab ist die **Woche** (`WeekScreen`, Stundenraster,
  Überschneidungen über `DayLayout`, Tests `DayLayoutTest`). Beim Öffnen ist 8–18 Uhr
  sichtbar (Wunsch des Nutzers): Stundenhöhe = verfügbare Höhe / 10, nicht fest codiert. Ein Tab mit vier Detailstufen
  `CalendarZoom` (Tag/Woche/Monat/Jahr, `Zoom.kt`): **Pinch** (zwei Finger aufziehen = feiner,
  zusammenschieben = gröber, eine Stufe je Geste, `pinchToZoom` im Initial-Durchgang, verbraucht nur
  Mehrfinger-Ereignisse); **Doppeltipp** auf den Tab wechselt Woche ↔ Monat. Tag = `WeekScreen` mit
  `dayCount = 1`. Alle Stufen folgen `selectedDay` (auch die Monatsnavigation), damit Zoomen beim
  selben Datum bleibt. Tippen auf eine freie Stelle im
  Raster legt dort einen Termin an.
- Bearbeiten nur für Einzeltermine in beschreibbaren Kalendern (`CAL_ACCESS_CONTRIBUTOR`+).
  Serien (RRULE/RDATE) bewusst nur lesend mit Sprung in die Kalender-App (`EventDetails`) – das
  Ändern einzelner Serientermine braucht Ausnahme-Zeilen und ist ein eigener Schritt.
- Ganztägige Termine: in `Events` Mitternacht UTC und Zeitzone `UTC`, `DTEND` exklusiv. Der Editor
  zeigt den letzten Tag; Umrechnung in `CalendarRepository.event/save`. Tage von Vorkommen aus
  `Instances.START_DAY/END_DAY` (julianisch), nicht aus `BEGIN/END`.
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
- **Widget „Heute“** (Glance): scrollbare Liste mit Uhrzeit, Titel, Ort (Wunsch des Nutzers; weitere
  Widget-Gestaltung noch offen). Neu zeichnen über `TodayWidget.refresh`: nach eigenen Änderungen,
  nach Abo-Updates, bei jeder Änderung in der Kalenderdatenbank (WorkManager-Content-URI-Auslöser,
  der sich nach jedem Lauf neu anmeldet) und kurz nach Mitternacht.
- Neue Abhängigkeiten nur mit gutem Grund. Aktuell: Compose, Core-KTX, Activity-Compose,
  Lifecycle, WorkManager, Glance.
- Debug-Build: App-ID `de.blaintino.chip.debug`, Name über den Manifest-Platzhalter `appLabel`.

## Mögliche nächste Schritte (nur auf Nachfrage)

- Widget-Gestaltung (Farben, Schriftgröße, Zeitraum, Kalenderauswahl je Widget).
- Serien bearbeiten, Erinnerungen setzen, Teilnehmer einladen.
- Eigenes App-Icon.
