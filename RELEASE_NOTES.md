## v1.13 — Start-Fix + Bugfixes

**Behebt: App startet nicht (schwarzer Screen) in v1.12.**
Der Wear-NavHost lief ohne WearNavigator und zeichnete still nichts.

### Fixes
- Gespeicherte Anmeldung startet wieder direkt in der Tierliste
- Abmelden: kein Zurück-Swipe mehr in die Einstellungen
- Karte: LED/Ton wurden doppelt umgeschaltet (falscher Befehl)
- Befehle: Schalter ändert sich nur bei Erfolg, Fehler werden angezeigt
- „Aktualisieren“ zeigt die neue Position jetzt wirklich an
- „Live beenden“ wird nicht mehr abgebrochen
- Karte mit fester Größe, Marker in der Mitte, Fehleranzeige statt Endlos-Laden
- Crash-Log-Upload funktioniert (lief vorher auf dem Main-Thread)
- Keine Endlosschleife bei wiederholtem 401/403
- Zugangsdaten-Speicher robuster, Backup deaktiviert
- Display bleibt nur auf der Karte an (Akku)

### Layout
Wear Material 3 durchgehend: Listenköpfe, Buttons in voller Breite, Schalter für LED/Ton/Live, Tastatur-„Fertig“ beim Login, Bestätigung beim Abmelden.

### Installation
```
adb install -r app-debug.apk
```
Debug-Signatur: Falls die Installation wegen abweichender Signatur fehlschlägt, zuerst die alte Version deinstallieren (`adb uninstall de.jbecker.tractivewatch`) und neu anmelden.
