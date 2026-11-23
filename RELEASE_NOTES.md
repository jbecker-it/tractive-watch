## v1.14 — Flüssig + Vollbild-Karte

### Performance
- **Optimierter Release-Build (R8)** statt Debug-APK: Compose läuft im Debug-Build um ein Vielfaches langsamer. Das war der Hauptgrund für das Ruckeln.
- App-Log wird nicht mehr bei jedem Ereignis auf dem Main-Thread gelesen und geschrieben
- Karte zeichnet nur noch bei Bewegung (keine Dauer-Animation)

### Neue Karte
Die Karte füllt jetzt die ganze Uhr, ohne Optionen-Buttons:
- **Ziehen** = verschieben (mit Schwung)
- **Krone drehen** = zoomen
- **Doppeltipp** = an der Stelle hineinzoomen, **Pinch** geht auch
- **Vom linken Rand wischen** = zurück
- Die Karte folgt dem Tier live. Nach dem Verschieben wird die Info-Pille unten blau, ein Tipp darauf springt zurück zum Tier.
- Genauigkeitskreis um die Position, Kachel-Cache (auch offline für bereits gesehene Bereiche)

### Installation
```
adb install -r tractive-watch.apk
```
**Einmalig:** Ab v1.14 hat die APK eine feste Signatur. Für das Update von v1.13 oder älter zuerst deinstallieren: `adb uninstall de.jbecker.tractivewatch`. Danach neu anmelden. Spätere Updates klappen direkt mit `-r`.
