## v1.15 — LED / Ton / Live funktionieren

### Fix: Befehle an den Tracker
- LED, Ton und Live-Tracking wurden per **POST** geschickt, Tractive erwartet **GET**. Die Fehlerantwort war kein JSON, der Parser scheiterte daran, und die App zeigte fälschlich „Netzwerkfehler“. Mit LTE hatte das nichts zu tun.
- Befehle laufen zu Ende, auch wenn man den Screen während des Sendens verlässt (vorher `ForgottenCoroutineScopeException`).
- Fehler zeigen jetzt den echten Grund (z. B. „Tractive-Fehler 404: …“) statt „Netzwerkfehler“.

### Installation
```
adb install -r tractive-watch.apk
```
(Von v1.14 aus direkt als Update, gleiche Signatur.)
