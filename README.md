# Chip

Eigene Kalender-App für Android (Kotlin, Jetpack Compose), privater Gebrauch, Installation über
GitHub Releases + [Obtainium](https://obtainium.imranr.dev/). Lizenz: GPL-3.0 (`LICENSE`).

## Funktionen

- **Termine aus allen Kalendern des Handys** (Android-Kalenderdatenbank `CalendarContract`): das
  Google-Konto synchronisiert eigene und **geteilte Google-Kalender** hinein. Freigaben selbst richtest
  du im Google Kalender ein, Chip zeigt und bearbeitet nur, was das Konto synchronisiert.
- **Bedienung**: keine Tabs. Oben links die aktuelle Ansicht (Antippen = Auswahl Tag/Woche/Monat/Jahr),
  daneben „Heute“, rechts das Zahnrad ⚙ für die Einstellungen (Kalender, Farben, Abos, Plus-Knöpfe, Widget).
- **Woche** (Startansicht): Mo–So mit Stundenraster, ganztägige Termine oben, Überschneidungen
  nebeneinander, rote Linie für „jetzt“. **Lang drücken** im Raster (Tag/Woche) zeigt einen Zeitwähler
  unter dem Daumen: ziehen wählt Viertelstunde (und in der Woche den Tag), Loslassen öffnet den
  Editor mit diesem Beginn. In der Monatsansicht öffnet langes Drücken auf einen Tag einen neuen Termin. Beim Öffnen füllt 8–18 Uhr die Höhe (Stundenhöhe passt
  sich dem Bildschirm an), davor/danach scrollen. Tippen auf eine freie Stelle legt dort einen Termin an.
  **Zoomen mit zwei Fingern:** Aufziehen wechselt eine Stufe feiner, Zusammenschieben eine gröber:
  **Jahr** (12 kleine Monate, Tage mit Terminen markiert) ↔ **Monat** (Raster mit Farbpunkten, darunter
  die Termine des gewählten Tages) ↔ **Woche** ↔ **Tag** (wie die Woche, nur ein Tag, mit Ort).
  **Wischen** nach links/rechts blättert zum nächsten/vorherigen Tag, Woche, Monat oder Jahr; die Ansicht
  folgt dabei dem Finger und schiebt sich weg. Beim Zoomen skalieren und überblenden die Ansichten live.
- **Plus-Knöpfe**: nebeneinander, je ein Plus mit den ersten drei Buchstaben für die Schnellauswahl-Kalender (vorbelegt „Langners“ und „Arbeit“, in
  deren Farbe), änderbar unter ⚙ → „Plus-Knöpfe“. Neue Termine über ein Plus beginnen
  heute zur nächsten vollen Stunde.
- **Heute**: Knopf neben dem Titel (Tag/Woche/Monat/Jahr) springt in der aktuellen Ansicht zu heute.
- **Bearbeiten**: Termine anlegen, ändern, löschen (Titel, Ort, Kalender, ganztägig,
  Beginn/Ende, Notiz). Zur Wahl stehen nur beschreibbare Kalender, die unter ⚙
  eingeschaltet sind. Nur lesbare Kalender zeigt Chip an und öffnet sie in der Kalender-App des Kontos.
- **Serien**: Wiederholen täglich, wöchentlich, alle 2 Wochen, monatlich am gleichen Datum, am n-ten
  oder letzten Wochentag des Monats, optional mit Enddatum. Beim Ändern oder Löschen eines
  Serientermins: nur dieser Termin, dieser und alle folgenden oder die ganze Serie. Regeln, die
  Chip nicht selbst abbildet (z. B. aus Google), bleiben unverändert erhalten.
- **Erinnerungen**: bis zu drei je Termin (Benachrichtigung). Neue Termine übernehmen die Vorgaben aus ⚙
  (ab Werk 1 Tag, 1 Stunde und 15 Minuten vorher). Gespeichert in Android (synchronisiert mit Google), angezeigt
  von Chip, sobald Android die Erinnerung meldet.
- **ICS-Abos** (⚙): Web-Adresse (`https://` oder `webcal://`) eintragen, Name und Farbe
  wählen. Jedes Abo wird ein eigener, nur lesbarer Kalender auf dem Handy (nicht bei Google) und wird
  **täglich** aktualisiert, außerdem beim Öffnen der App, wenn das letzte Laden über einen Tag her
  ist, und von Hand. `webcal://`-Links aus dem Browser öffnen direkt den Abo-Dialog.
- **Mehrtägige Termine** erscheinen verbunden: in Woche/Tag als durchgehender Balken oben, im Monat als
  Balken direkt unter der Tageszahl, im Jahr als durchgehend eingefärbte Tage.
- **Kalender ein-/ausblenden (auch nur für Monat/Jahr) und Farbe ändern** (⚙, Farbpunkt antippen): nur für Chip (App, Widget,
  Plus-Knöpfe), ändert nichts am Konto. Termine mit eigener Terminfarbe behalten diese.
- **Wetter im Widget** (⚙ → „Wetter im Widget“, Ort per Namenssuche, keine Standort-Berechtigung):
  aktuelle Temperatur, Höchst-/Tiefstwert von heute und Regenhinweis (ab 40 % für den Rest des Tages, z. B. „☂ ab 15 Uhr
  (70 %)“, als zweite, hellblaue Zeile), stündlich aktualisiert. Daten: [Open-Meteo](https://open-meteo.com) (CC BY 4.0).
- **Stempel-Knopf im Widget** (mit der eigenen App [FH Stempelkarte](https://github.com/Blaintino/FH-Stempelkarte),
  Release-Builds beider Apps): rechts neben der Wetter-Pille ein rundes Einloggen-Symbol (rot, wenn die Stempelkarte erinnert);
  eingeloggt grün mit der heutigen Arbeitszeit (über mehrmaliges Ein-/Ausloggen aufsummiert, netto: ab 6 h 30 min
  und ab 9 h weitere 15 min Pause abgezogen, soweit nicht schon ausgeloggt pausiert – die Zeit steht dann), Tippen loggt aus.
  Danach öffnet die Stempelkarte ZEUS. Sichtbar nur bei Bedarf: wenn die Stempelkarte dich an der FH erkannt hat
  oder du eingeloggt bist, oder von 1 h vor dem ersten bis 1 h nach dem
  letzten Termin in Kalendern mit „Stundenplan“ oder „Arbeit“ im Namen. Sonst (und ohne Stempelkarte) steht das Wetter mittig.
- **Widget „Heute“**: ohne Kopfzeile, schwarze Fläche nur hinter den Terminen (ohne Termine bleiben nur die Knöpfe, dann oben), scrollbare Liste der heutigen Termine mit Uhrzeit, Titel und
  Ort, nur laufende und kommende (vergangene verschwinden zum Terminende, ganztägige bleiben); unten die Plus-Knöpfe der App (gleiche Kalender und Farben, öffnen „Neuer Termin“) und
  „+ Quälgeist“ (öffnet die Schnelleingabe von Quälgeist, falls dort freigegeben, sonst Quälgeist).
  Aktualisiert sich bei jeder Änderung in der Kalenderdatenbank und kurz nach Mitternacht.

## Dateien

| Datei | Inhalt |
|---|---|
| `MainActivity.kt` | Einstieg, Berechtigungen, Kopfzeile, Ansichten mit Gesten, Editor/Details öffnen |
| `data/CalendarRepository.kt` | Lesen/Schreiben in `CalendarContract` (Kalender, Vorkommen, Termine) |
| `data/Repeat.kt` | Wiederholungen ↔ RRULE (`RepeatRules`, Tests `RepeatTest`) |
| `data/Reminders.kt` | Erinnerungs-Vorgaben (`ReminderRules`, `DefaultReminders`, Tests `ReminderRulesTest`) |
| `reminder/EventAlerts.kt` | Benachrichtigung bei `EVENT_REMINDER` (`EventReminderReceiver`) |
| `data/Prefs.kt` | Ausgeblendete Kalender, Abo-Liste (SharedPreferences, `commit()`) |
| `data/Formats.kt` | Deutsche Datums-/Zeitformate |
| `ics/IcsParser.kt` | ICS-Datei lesen (Zeilen falten, Eigenschaften, VEVENTs) |
| `ics/IcsMapper.kt` | VEVENT → Zeile für `CalendarContract.Events` (Zeitzonen, Serien, Ausnahmen) |
| `sync/SubscriptionSync.kt` | Abo-Kalender anlegen/löschen, herunterladen, Inhalt ersetzen |
| `sync/Background.kt` | WorkManager: tägliches Abo-Update, Widget bei Änderungen/Mitternacht |
| `weather/Weather.kt` | Ortssuche und Wetter von Open-Meteo, Zwischenspeicher, Widget-Texte (Tests `WeatherTextTest`) |
| `widget/TodayWidget.kt` | Glance-Widget „Heute“ |
| `stempel/Stempelkarte.kt` | Anbindung FH Stempelkarte: Status lesen, Sprungbrett, Empfänger (Tests `StempelStatusTest`) |
| `ui/…` | Compose-Bildschirme: Tag/Woche, Monat, Jahr, Einstellungen (`CalendarsScreen`), Editor |
| `ui/Zoom.kt` | Detailstufen Tag/Woche/Monat/Jahr und Pinch-Geste (Tests `CalendarZoomTest`) |
| `ui/DayLayout.kt` | Spalten für sich überschneidende Termine in der Wochenansicht (Tests `DayLayoutTest`) |

## Doku für Entwickler

`CLAUDE.md` (Aufbau, Ablauf, feste Entscheidungen, Übergabe-Notizen), `TODO.md` (offene Punkte),
`docs/mockups/` (Gesten-Mockup, auch als Artefakt https://claude.ai/artifact/5KxJ5e7qXsKwaBnQuxLv49).

## Bauen

JDK 21 + Android SDK (`local.properties` mit `sdk.dir=…` oder `ANDROID_HOME`), dann:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Die Debug-Version heißt „Chip (Debug)“ (`de.blaintino.chip.debug`) und läuft neben der Release-Version.

## GitHub Actions, Signatur und Obtainium

`.github/workflows/build-apk.yml` baut bei jedem Push auf `main` und `claude/**`: Unit-Tests, Lint,
signierte Release-APK (`Chip-<Lauf>.apk`) als Artefakt; nur auf `main` und nur signiert zusätzlich
ein GitHub Release `v<Lauf>` (eigenes Repo, daher kein Filter in Obtainium nötig). Ohne Secrets nur
eine Debug-APK mit Warnung (nicht als Update installierbar).

Secrets (Settings → Secrets and variables → Actions), gleicher Keystore wie Quälgeist, z. B. vom Mac:

```bash
base64 -i ~/Android-Schluessel/android-release.jks | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64 --repo Blaintino/Chip
```

```bash
gh secret set ANDROID_KEY_ALIAS --repo Blaintino/Chip --body release
```

```bash
gh secret set ANDROID_KEYSTORE_PASSWORD --repo Blaintino/Chip
```

```bash
gh secret set ANDROID_KEY_PASSWORD --repo Blaintino/Chip
```

## Rechte

`READ_CALENDAR`/`WRITE_CALENDAR` (Kalender), `INTERNET`/`ACCESS_NETWORK_STATE` (Abos und Wetter),
`POST_NOTIFICATIONS` (Erinnerungen), `RECEIVE_BOOT_COMPLETED`, `de.fhstempelkarte.permission.INTEROP` (Stempel-Knopf).
Keine Standort-Berechtigung.
Keine Konten, keine Tracker, keine Google-Dienste in der App selbst.
