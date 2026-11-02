# Chip

Eigene Kalender-App für Android (Kotlin, Jetpack Compose), privater Gebrauch, Installation über
GitHub Releases + [Obtainium](https://obtainium.imranr.dev/). Lizenz: GPL-3.0 (`LICENSE`).

## Funktionen

- **Termine aus allen Kalendern des Handys** (Android-Kalenderdatenbank `CalendarContract`): das
  Google-Konto synchronisiert eigene und **geteilte Google-Kalender** hinein. Freigaben selbst richtest
  du im Google Kalender ein, Chip zeigt und bearbeitet nur, was das Konto synchronisiert.
- **Termine**: Liste der nächsten 90 Tage, nach Tagen gruppiert.
- **Woche** (Startansicht): Mo–So mit Stundenraster, ganztägige Termine oben, Überschneidungen
  nebeneinander, rote Linie für „jetzt“. Beim Öffnen füllt 8–18 Uhr die Höhe (Stundenhöhe passt
  sich dem Bildschirm an), davor/danach scrollen. Tippen auf eine freie Stelle legt dort einen Termin an.
  **Zoomen mit zwei Fingern:** Aufziehen wechselt eine Stufe feiner, Zusammenschieben eine gröber:
  **Jahr** (12 kleine Monate, Tage mit Terminen markiert) ↔ **Monat** (Raster mit Farbpunkten, darunter
  die Termine des gewählten Tages) ↔ **Woche** ↔ **Tag** (wie die Woche, nur ein Tag, mit Ort).
  **Doppeltipp auf den Tab** wechselt direkt zwischen Woche und Monat.
- **Bearbeiten**: Einzeltermine anlegen, ändern, löschen (Titel, Ort, Kalender, ganztägig,
  Beginn/Ende, Notiz). Zur Wahl stehen nur beschreibbare Kalender, die unter „Kalender & Abos“
  eingeschaltet sind. Serientermine und nur lesbare Kalender zeigt Chip an und öffnet sie zum
  Bearbeiten in der Kalender-App des Kontos.
- **ICS-Abos** (Tab „Kalender“): Web-Adresse (`https://` oder `webcal://`) eintragen, Name und Farbe
  wählen. Jedes Abo wird ein eigener, nur lesbarer Kalender auf dem Handy (nicht bei Google) und wird
  **täglich** aktualisiert, außerdem beim Öffnen der App, wenn das letzte Laden über einen Tag her
  ist, und von Hand. `webcal://`-Links aus dem Browser öffnen direkt den Abo-Dialog.
- **Kalender ein-/ausblenden**: nur für Chip (App und Widget), ändert nichts am Konto.
- **Widget „Heute“**: scrollbare Liste der heutigen Termine mit Uhrzeit, Titel und Ort.
  Aktualisiert sich bei jeder Änderung in der Kalenderdatenbank und kurz nach Mitternacht.

## Dateien

| Datei | Inhalt |
|---|---|
| `MainActivity.kt` | Einstieg, Berechtigung, Tabs, Editor/Details öffnen |
| `data/CalendarRepository.kt` | Lesen/Schreiben in `CalendarContract` (Kalender, Vorkommen, Termine) |
| `data/Prefs.kt` | Ausgeblendete Kalender, Abo-Liste (SharedPreferences, `commit()`) |
| `data/Formats.kt` | Deutsche Datums-/Zeitformate |
| `ics/IcsParser.kt` | ICS-Datei lesen (Zeilen falten, Eigenschaften, VEVENTs) |
| `ics/IcsMapper.kt` | VEVENT → Zeile für `CalendarContract.Events` (Zeitzonen, Serien, Ausnahmen) |
| `sync/SubscriptionSync.kt` | Abo-Kalender anlegen/löschen, herunterladen, Inhalt ersetzen |
| `sync/Background.kt` | WorkManager: tägliches Abo-Update, Widget bei Änderungen/Mitternacht |
| `widget/TodayWidget.kt` | Glance-Widget „Heute“ |
| `ui/…` | Compose-Bildschirme: Termine, Woche, Monat, Kalender & Abos, Editor |
| `ui/Zoom.kt` | Detailstufen Tag/Woche/Monat/Jahr und Pinch-Geste (Tests `CalendarZoomTest`) |
| `ui/DayLayout.kt` | Spalten für sich überschneidende Termine in der Wochenansicht (Tests `DayLayoutTest`) |

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

`READ_CALENDAR`/`WRITE_CALENDAR` (Kalender), `INTERNET`/`ACCESS_NETWORK_STATE` (nur zum Laden der Abos).
Keine Konten, keine Tracker, keine Google-Dienste in der App selbst.
