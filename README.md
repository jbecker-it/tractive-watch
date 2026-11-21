# Tractive Watch

Eine **unabhängige Wear OS-App** für [Tractive](https://tractive.com/) Hundetracker — gebaut für die Pixel Watch (5, 45 mm). Kein offizielles Produkt von Tractive.

Damit steuern und überwachen Sie Ihren Tractive-Tracker direkt vom Handgelenk: Taschenlampe an/aus, Piepton, Live-Tracking auslösen — plus Echtzeit-Position auf einer Karte.

## Funktionen

- 🔦 **LED / Taschenlampe** — ein und ausschalten
- 🔊 **Piepton (Buzzer)** — den Tracker hörbar machen
- 🚨 **Live-Tracking** — aus der App heraus auslösen
- 📍 **Echtzeit-Position** — über das Tractive-Kanal-API (NDJSON-Longpoll)
- 🗺️ **Kartenansicht** — OpenStreetMap-Tiles, ohne Google-Maps-Abhängigkeit, mit pulsierendem Marker und Auto-Zoom nach Positionsgenauigkeit
- 🔋 **Geräte-Status** — Akku, Signalstärke, letzter Standort
- 🔐 **Anmeldedaten werden lokal verschlüsselt** gespeichert (Android Keystore / EncryptedSharedPreferences)

## Voraussetzungen

- Wear OS 3 (Android 11) oder neuer, ideal: Pixel Watch 5
- Ein Tractive-Konto mit registriertem Tracker
- Internetzugang (LTE über die Watch oder WLAN)

## Installation

1. Die aktuelle `app-debug.apk` aus den [Releases](https://github.com/jbecker-it/tractive-watch/releases) herunterladen.
2. Auf der Watch die **Entwicklertools** aktivieren (Einstellungen → System → Info → Softwareversion 7× tippen).
3. Über den PC installieren:
   ```bash
   adb install app-debug.apk
   ```
   …oder die APK auf die Watch kopieren und dort installieren.
4. App starten und mit den **Tractive-Zugangsdaten** (E-Mail + Passwort, wie im Tractive-Web/Android) anmelden.

> Hinweis: Es ist eine Debug-Signatur. Bei Updates über ältere Builds ggf. zuerst die alte Version deinstallieren.

## Selbst bauen

Erfordert **JDK 21** und das **Android SDK** (API 35, Build-Tools 35.0.0):

```bash
./gradlew assembleDebug
# APK unter app/build/outputs/apk/debug/app-debug.apk
```

Projektstruktur:

```
app/src/main/java/de/jbecker/tractivewatch/
├── data/            Tractive-API-Client, Datenmodelle, Credential-Storage
├── ui/              Compose-Screens (Login, Home, Karte), OSM-Renderer
├── Repo.kt          Repository-/Orchestrierungsschicht
└── MainActivity.kt  Activity + simple Screen-Navigation
```

## Architektur / API

Die App nutzt die **Web-Backend-API** von Tractive (wie in Community-Projekten dokumentiert), da es keine offizielle öffentliche API gibt:

- Auth: `POST https://graph.tractive.com/4/auth/token`
- Befehle: `POST /4/tracker/{id}/command/{led_control|buzzer_control|live_tracking}`
- Echtzeit: NDJSON-Longpoll auf `https://channel.tractive.com/3/channel`
- Karte: `https://tile.openstreetmap.org/{z}/{x}/{y}.png` (OpenStreetMap-Kacheln)

Stack: **Kotlin, Jetpack Compose (Material3), OkHttp, Moshi** — ohne offizielles Tractive-SDK, ohne APK-Repackaging.

## Hinweise & Haftung

- **Unoffiziell:** Die App ist nicht von Tractive autorisiert und nutzt interne Web-Endpunkte. Endpunkte können sich ändern; Nutzung auf eigenes Risiko (ggf. Abweichung von den Tractive-Nutzungsbedingungen).
- **Keine Gewähr** für Verlust oder Fehlfunktionen des Trackers.
- Tractive® ist eine Marke von Tractive Ltd.

## Lizenz

[MIT](LICENSE) — © Johannes Becker
