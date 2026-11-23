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

1. Die aktuelle `tractive-watch.apk` aus den [Releases](https://github.com/jbecker-it/tractive-watch/releases) herunterladen.
2. Auf der Watch die **Entwicklertools** aktivieren (Einstellungen → System → Info → Softwareversion 7× tippen).
3. Über den PC installieren:
   ```bash
   adb install -r tractive-watch.apk
   ```
   …oder die APK auf die Watch kopieren und dort installieren.
4. App starten und mit **E-Mail & Passwort** des Tractive-Kontos anmelden (Passwort wird nur lokal verschlüsselt gespeichert). Google-Login wird nicht unterstützt (Google blockiert Drittanbieter-Apps).

> Hinweis: Ab v1.14 feste Signatur (`keystore/`). Beim Umstieg von v1.13 oder älter einmalig deinstallieren.

## Ablauf

1. Start → Anmelden (nur beim ersten Mal)
2. Home: Tier-Liste mit Akku und letzter Position
3. Tipp auf ein Tier → Schalter für **Licht**, **Ton**, **Live-Tracking**
4. **Karte** → Vollbild: ziehen, Krone = Zoom, Doppeltipp = näher, vom Rand wischen = zurück

## Selbst bauen

Erfordert **JDK 21** und das **Android SDK** (API 35, Build-Tools 35.0.0):

```bash
./gradlew assembleRelease
# APK unter app/build/outputs/apk/release/app-release.apk
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

- Auth (Google, wie in der offiziellen App): Google-Auth-Code aus dem Play-Services-Sign-In → `POST https://graph.tractive.com/4/auth/token/google`
- Auth (E-Mail/Passwort): `POST https://graph.tractive.com/4/auth/token`
- Befehle: `POST /4/tracker/{id}/command/{led_control|buzzer_control|live_tracking}`
- Echtzeit: NDJSON-Longpoll auf `https://channel.tractive.com/3/channel`
- Karte: `https://tile.openstreetmap.org/{z}/{x}/{y}.png` (OpenStreetMap-Kacheln)

Stack: **Kotlin, Jetpack Compose (Material3), OkHttp, Moshi, Play-Services-Auth** — ohne offizielles Tractive-SDK, ohne APK-Repackaging.

> **Google-Login-Hinweis:** Die App verwendet die bei Google registrierte Client-ID der offiziellen Tractive-Web-App. Das funktioniert, solange Tractive diese Client-ID beibehält; Google kann zudem Drittanbieter-Anwendungen mit dieser Client-ID blockieren (OAuth-2.0-Policy). In dem Fall bleibt die E-Mail/Passwort-Anmeldung als Ausweg.

## Hinweise & Haftung

- **Unoffiziell:** Die App ist nicht von Tractive autorisiert und nutzt interne Web-Endpunkte. Endpunkte können sich ändern; Nutzung auf eigenes Risiko (ggf. Abweichung von den Tractive-Nutzungsbedingungen).
- **Keine Gewähr** für Verlust oder Fehlfunktionen des Trackers.
- Tractive® ist eine Marke von Tractive Ltd.

## Lizenz

[MIT](LICENSE) — © Johannes Becker
