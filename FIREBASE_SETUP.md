# Google-Anmeldung und Sync einrichten (einmalig)

**Stand 29.09.2026: erledigt.** Tenet ist im Firebase-Projekt `saffron-498311`
(geteilt mit Saffron) als Android-App „Tenet“ registriert
(App-ID `1:199105728544:android:8717f38e733376d2d0ac45`, SHA-1/SHA-256 des
Debug-Keystores eingetragen), `app/google-services.json` liegt im Projekt und
die Firestore-Regeln (Saffron + `tenetRows`/`tenetTracks`) sind deployt.
Die folgenden Schritte sind nur noch für ein neues Projekt nötig.

Nach der Anmeldung übernimmt Tenet außerdem alle Rezepte aus Saffron
(`users/{uid}/recipes`); Favorit und Bewertung werden zurückgeschrieben.

Tenet synchronisiert nach der Anmeldung mit Google die komplette Datenbank
über Firebase (Auth + Firestore). Die App funktioniert ohne diese Einrichtung
weiter, zeigt dann aber „Google-Anmeldung: noch nicht eingerichtet“.

## 1. Firebase-Projekt

1. https://console.firebase.google.com → **Projekt hinzufügen** (z. B. „Tenet“).
   Google Analytics wird nicht gebraucht.
2. **Android-App hinzufügen**
   - Paketname: `app.tenet.android`
   - SHA-1 (Debug-Keystore, mit dem auch die Release-APKs signiert sind):
     `CC:9B:B8:E2:1D:E1:24:92:FB:99:7B:C2:BF:C6:D3:A8:3C:B7:03:1D`
   - optional SHA-256:
     `50:E7:CC:BE:D3:B5:63:DE:07:10:EC:45:FB:1E:9D:A0:A0:C3:3D:6B:74:AC:80:96:C0:D4:13:D3:31:D1:44:24`
3. `google-services.json` herunterladen und nach `app/google-services.json` legen.

## 2. Anmeldung mit Google aktivieren

Firebase-Konsole → **Authentication** → Anmeldemethode → **Google** → aktivieren,
Support-E-Mail wählen → Speichern. **Danach `google-services.json` erneut
herunterladen** (erst jetzt steht die Web-Client-ID darin) und ersetzen.

## 3. Firestore anlegen

Firebase-Konsole → **Firestore Database** → Datenbank erstellen
(Standort z. B. `eur3` / Europa, Produktionsmodus). Unter **Regeln** den Inhalt
von `firebase/firestore.rules` einfügen und veröffentlichen: Jeder Nutzer
darf nur seinen eigenen Bereich `users/{uid}` lesen und schreiben.

## 4. Bauen

APK neu bauen – das Gradle-Plugin wird automatisch aktiv, sobald
`app/google-services.json` existiert. Danach: Einstellungen → Konto →
„Mit Google anmelden“.

## Was synchronisiert wird

- Alle Tabellen (Journal, Notizen, Träume, Ernährung, Rezepte, Gym,
  Calisthenics, Laufen, Körperwerte); GPS-Strecken als ein Dokument pro Lauf.
- Nicht: Fotos und Sprachmemos (Dateien) – die bleiben auf dem Gerät.
- Konflikte: eine noch nicht hochgeladene lokale Änderung gewinnt, sonst die
  neueste. Erste Anmeldung auf einem neuen Handy: erst die Cloud-Daten, dann
  nur das hochladen, was die Cloud noch nicht kennt.
- Kosten: Der kostenlose Spark-Tarif reicht für eine Person (50 000 Lese- und
  20 000 Schreibvorgänge pro Tag).
