# Chip – offene Punkte

Sammelliste (Wunsch des Nutzers): Kleinigkeiten ohne eigenen Build werden hier gesammelt und mit dem
nächsten Build erledigt; Release nur auf Ansage. Stand 2026-11-11, letzter Release v78.

## Offen

1. [ ] **Erinnerungen auf dem Gerät beobachten** (v75). Kommen sie pünktlich und nur einmal? Solange Google
   Kalender oder Samsung Kalender selbst benachrichtigen, kommen sie doppelt; der Nutzer will die Google-App
   später löschen. Empfehlung an den Nutzer: Chip in den Samsung-Akku-Einstellungen von der Optimierung
   ausnehmen, nicht unter „Schlafende Apps“.
   Falls Meldungen fehlen oder zu spät kommen: Chip plant selbst (`setExactAndAllowWhileIdle`, Prüfung beim
   Start und nach Neustart). Nicht `setAlarmClock`, weil dann jeder Termin als Wecker in der Statusleiste steht.
   Nur nach Rückmeldung des Nutzers umsetzen.
2. [ ] **Serien mit Google-Sync** (v73): Der Nutzer wollte keine Prüfliste und meldet sich bei Fehlern. Am
   unsichersten: einzeln verschobene oder gelöschte Termine (Ausnahme-Zeilen) in Google-Kalendern.
3. [ ] **Tägliche Abo-Aktualisierung im Hintergrund** ist noch nie beobachtet worden; Aktualisieren von Hand
   und beim Öffnen funktioniert.
4. [ ] **Geteilte ToDo-/Einkaufsliste**: Der Nutzer hält das eher für eine eigene App. Die Diskussion läuft in
   einer eigenen Session („Geteilte ToDo-/Einkaufsliste – Konzept“); für Chip ist nichts entschieden.

## Nur auf Nachfrage (Ideen, nicht beauftragt)

5. [ ] „Später erinnern“ als Knopf an der Erinnerungs-Benachrichtigung.
6. [ ] Teilnehmer einladen.
7. [ ] Widget-Gestaltung (Farben, Schriftgröße, Zeitraum, Kalenderauswahl je Widget).
8. [ ] Eigenes App-Icon.

## Erledigt (mit Entscheidungen des Nutzers)

- [x] Knopf „Heute“ mittig zwischen Titel und Zahnrad.
- [x] Serientermine (v73): täglich, wöchentlich, alle 2 Wochen, monatlich am Datum, am n-ten oder letzten
  Wochentag, mit Enddatum. Einzelne Termine verschieben, „dieser und folgende“, ganze Serie löschen.
- [x] Erinnerungen (v75): höchstens 3 je Termin; Vorgaben in den Einstellungen (ab Werk 1 Tag, 1 Std., 15 Min.
  vorher), werden von jedem neuen Termin übernommen und nur im Termin geändert oder gelöscht.
- [x] Stempel-Knopf wird nicht mehr von einer langen Wetterzeile aus dem Widget geschoben (v78).
- [x] Regenhinweis als zweite, auffällige (hellblau, fett) Zeile; die Pille wächst nur bei Regen (v78).
- [x] Stempel-Knopf der FH Stempelkarte im Widget (v61–v71, aus einer anderen Session; Details in `CLAUDE.md`).
- [x] Mehrtägige Termine, Monat/Jahr-Ausblenden je Kalender, Wetter-Pille, Gesten mit Rückmeldung (bis v59).
