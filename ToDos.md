# Tenet – offene ToDos

Stand: 02.10.2026, Version 0.21.0. Abgeleitet aus `App_Konzept.md`, dem aktuellen Code und den Tests im Emulator.

## Google-Anmeldung & Sync (0.15)

- [ ] Am Handy mit eigenem Google-Konto anmelden; Sync zwischen zwei Geräten testen (Neuanlage, Änderung, Löschen, erste Anmeldung auf neuem Handy)
- [ ] Fotos und Sprachmemos synchronisieren (Firebase Storage, braucht den Blaze-Tarif)

## Am echten Handy testen

Im Emulator nicht möglich.

- [x] Barcode-Scan mit echter Packung (Google Code Scanner → Open Food Facts)
- [ ] Journal-Sperre mit Fingerabdruck bzw. Displaysperre
- [ ] Sprachmemo aufnehmen und abspielen
- [ ] Neues Diktat-Sheet am Handy: längere Pausen, Signaltöne beim Neustart der Erkennung?
- [ ] Echter GPS-Lauf: Sprachansagen, geführte Intervalle, Auto-Pause, Live-Benachrichtigung
- [x] Health-Connect-Import mit Uhr oder Strava, inkl. Duplikat-Erkennung bei parallelem GPS-Lauf
- [ ] Live-Mitteilung im Training am echten Handy (Android 16: Chip in der Statusleiste, Eingabe vom Sperrbildschirm)
- [ ] Hold-Timer und AMRAP per Lautstärketaste
- [ ] Haptik (Vibration) an allen Stellen
- [ ] „Lauf starten“ auf der Heute-Seite an einem Tag mit geplantem Lauf
- [ ] Trainings-Erinnerung morgens an einem Tag mit offenem Training (Emulator: Job läuft, heute war schon alles erledigt)
- [ ] Health Connect: Trainings und Gewicht schreiben, Gewicht aus Waage/Fitbit lesen. Vorher in Einstellungen → Health Connect → „Berechtigungen“ die neuen Rechte erlauben
- [ ] Schlaf bei den Träumen mit echter Uhr/Schlaf-App prüfen (Emulator hat keine Schlafdaten)
- [ ] Pace-Ansagen bei lockeren/langen Läufen (zu schnell / passt / langsamer, Halbzeit, Ziel)
- [ ] KI per Sprache: Mahlzeit diktieren (Ernährung → Hinzufügen → Mikrofon), Lauf nachtragen (Diktieren), Gym-Sätze (Mikrofon in der Session), Traum/Tagebuch nach „Erzählen“

## Heute

- [ ] Widget für den Startbildschirm: Kalorien heute, „Traum notieren“

## Sport · Calisthenics

- [ ] Fotos für Skill-Stufen (Front Lever, Planche, L-Sit, Hollow Body, freier Handstand) – in free-exercise-db nicht enthalten
- [ ] Formvideos pro Versuch (Konzept: v2)

## Sport · Laufen

- [ ] GPX-Strecke beim Export auch mit Pausen-Segmenten

## Journal · Notizen


- [ ] Teilen aus anderen Apps (Share-Target): Text oder Link wird Notiz

## Journal · Tagebuch

- [ ] Serifenschrift als Leseschrift (einstellbar)

## Journal · Träume

- [ ] AMOLED-Schwarz als Option (nicht erzwungen)
- [ ] Widget „Traum notieren“ (Shortcut gibt es)

## Ernährung · Tracker

- [ ] Widget „Kalorien heute“

## Ernährung · Rezepte

- [ ] Makro-Optimierer (Konzept: Profi-Feature, OR-Tools CP-SAT)


## KI-Ausfüllhilfe (NVIDIA NIM)

- [ ] API-Schlüssel bei NVIDIA neu erzeugen (er stand im Chat) und in den Einstellungen eintragen

## Technik

- [ ] Eigener Signaturschlüssel statt Debug-Keystore. Wechsel = einmal neu installieren, dabei gehen die Daten verloren.
- [ ] Der NIM-Schlüssel aus `local.properties` wird in die APK gebaut. Für eine private App okay, vor einer Weitergabe entfernen.
- [ ] Datenbank-Verschlüsselung (SQLCipher). Erst sinnvoll zusammen mit einem Backup.

## Zuletzt erledigt (0.5.0 – 0.21.0)

- 0.21: Satzpausen je Übung nach Studienlage (schwere Grundübungen 3–5 min, Grundübungen 2–3 min, Isolation 60–90 s, Aufwärmsätze ~45 s; eigene Planwerte gehen vor, 0 = automatisch) und in jeder Übungskarte angezeigt; Trainingsmodus (Satz für Satz: Werte vorausgefüllt, „Satz fertig“, Pause mit Countdown, nächster Satz); Training als Live-Mitteilung (Android 16 Live Update mit Countdown-Chip in der Statusleiste und Fortschritt je Übung) mit „+30 s“, „Überspringen“, „Wie geplant“ und „Eintragen“ (Schnellwahl oder Text wie „80x8“) direkt in der Mitteilung

- 0.20.2: Option „Nur zwei Farben“ (Einstellungen → Farben): ganze App nur in Primär- und Sekundärfarbe, ohne eigene Bereichsfarben und ohne Tertiärfarbe

- 0.20.1: Bereichsfarben harmonisch: analoge Töne aus derselben Theme-Farbe (Sport = Theme-Ton, Journal +50°, Ernährung −50°, gleiche Buntheit/Helligkeit, gleicher Farbstil) statt Grün/Rosa-Kontrast; Bereichs-Theme übernimmt auch Sekundär-/Tertiärrollen; dezentere Flächentönung; Serien-Symbol ohne Fehler-Rot

- 0.20: Mehr Farbe mit MD3 Dynamic Color: Farbstil wählbar (Ruhig/Kräftig/Expressiv/Bunt, Standard Kräftig) aus Wallpaper- oder eigener Farbe; jeder Bereich hat seine Farbe – Sport = Primärfarbe, Journal = Tertiärfarbe, Ernährung = harmonisiertes Grün (M3-Custom-Color) – inkl. getönter Karten, Knöpfe und Chips im Tab, in den Unterseiten und auf den Heute-Karten; Serien mit Bereichsfarben

- 0.19.6: Rezept-Tags als Chips mit passendem Piktogramm statt „#Tag“ (Ernährung, Temperatur, Schärfe, Gang, Hauptzutat, Küche, Eigenschaften; sonst Etikett), doppelte Tags zusammengefasst

- 0.19.5: Rezept-Import getestet (Chefkoch, 2× Instagram, 2× TikTok, Freitext) und verbessert: Portionen aus dem Text („für 4 Personen“), vegetarisch bei vollständiger Zutatenliste ohne Fleisch, Tags ohne GROSSBUCHSTABEN und „Küche:“-Präfix, „1 Schritt/1 Zutat“, Hinweis statt „0 Zutaten“ bei Beiträgen mit Rezept nur auf der Website

- 0.19.4: Notiz-Kacheln: Checklisten-Fortschritt als „0/7“ oben neben dem Datum statt abgeschnittenem Balken unten; leere Punkte zählen nicht; kein „…“ mehr zusätzlich zum Ausblenden

- 0.19.3: Rezepte bearbeiten (auch Saffron-Rezepte): Titel, Zutaten-Zeilen, Schritte mit Timer, Reihenfolge; Saffron-Rezepte werden im Saffron-Format in Firebase zurückgeschrieben; Import-Übersetzung mit Ersatzmodell, falls die erste Antwort unbrauchbar ist

- 0.19.2: Rezept-Import aus Instagram/TikTok-Texten mit „Ingredients … Method“ liest Zutaten und alle Schritte direkt (keine erfundenen Zeiten), Titel ohne Serien-Präfix/Emojis; KI-Modell gpt-oss-20b (≈10 s statt Nemotron-Warteschlange), Übersetzung in Du-Form mit deutschen Küchenbegriffen, keine Platzhalter-Tags

- 0.19.1: Notiz-Editor ohne Umschalter/Vorschau: Checklisten öffnen nur als Checkliste, Texte nur als Text

- 0.19: Wetter (Open-Meteo, grober Standort) im Untertitel auf Heute und automatisch im Tagebuch-Eintrag; Schlaf aus Health Connect bei den Träumen (letzte Nacht + Dauer je Traum); Laufplan erkennt unrealistische Zielzeit bzw. veränderte Form und rechnet auf Knopfdruck Tempi (und Zielzeit) für den Rest des Plans neu; erster Start mit Modulen, Profil und Zielen (kcal/Makros, Wasser); Rezept-Import per KI wie in Saffron (Link von Webseite/TikTok/Instagram oder Freitext, schema.org direkt, keine erfundenen Mengen, Übersetzung ins Deutsche), angemeldet direkt in Firebase gespeichert; Teilen aus dem Browser → Rezept-Import; Notiz-Editor im M3-Stil (Titel, Textfläche, Ordner-Chip, Tags), Checklisten mit Überschriften

- 0.18: Rezepte optisch neu: Kacheln mit Titel auf dem Foto (Verlauf, 2 Zeilen) und kompakter Faktenzeile; Detail mit randlosem Foto (wischbar bei mehreren), Titel/Kategorie/Zeit/Portionen auf dem Foto, transparente Leiste mit runden Knöpfen, die beim Scrollen fest wird; Tags als eine Zeile; Notizen ohne Hashtags und eingeklappt

- 0.17.3: Rezeptfotos aus Saffron werden nach jedem Import als kleine Kopie (600 px WebP, ~65 KB) dauerhaft gespeichert – alle Kacheln offline, Detail fällt offline darauf zurück

- 0.17.2: Rezeptseite aufgeräumt: Suchleiste als Pille mit Sync und Sortieren darin, eine Filterzeile (Kategorie als Auswahlmenü), Trefferzahl + „Zurücksetzen“ nur bei aktiven Filtern

- 0.17.1: Checklisten-Notizen öffnen als Checkliste (auch Einkaufslisten aus Rezepten); abgeschnittene Kacheln blenden unten weich aus

- 0.17: Willkommens-Screen beim ersten Start (Mit Google anmelden oder als Gast), danach Berechtigungen einzeln oder „Alle erlauben“ (Mitteilungen, Standort, Mikrofon, Health Connect); Sync-Knopf auf der Rezeptseite

- 0.16: Tenet im Firebase-Projekt von Saffron (saffron-498311) registriert, Regeln um tenetRows/tenetTracks erweitert und deployt; nach der Google-Anmeldung kommen alle Saffron-Rezepte automatisch (Bilder mit frischen Storage-Links, Favorit/Bewertung zurück nach Saffron); Rezeptseite mit Suche, Filtern (Favoriten, Vegetarisch, ≤ 30 Min, Noch nicht gekocht, Kategorie) und Sortierung; Rezept-Kacheln mit Foto; Detail mit Bildkarussell, Sternen, „Gekocht“, skalierbaren Freitext-Zutaten, Schritten mit Timer und Zutaten, Notizen, Link zum Original; Kochmodus mit Zutaten pro Schritt

- 0.15: Anmeldung mit Google (Credential Manager → Firebase Auth) unter Einstellungen → Konto; Zwei-Wege-Sync der ganzen Datenbank mit Firestore (Änderungsprotokoll per SQLite-Trigger, stündlich + beim Öffnen/Verlassen der App, GPS-Strecken als ein Dokument pro Lauf); Abmelden, Cloud-Daten löschen; Wochenleiste auf Heute entzerrt

- 0.14.1: Letzte Übungsnotiz im Übungs-Detail; Verlauf-Knopf in der Skill-Session; Snackbars im Neu-Sheet sichtbar; Ziffern mit fester Breite in Titeln/Labels/Zahlen; App-Shortcuts (lange auf das Icon drücken): Neue Notiz, Mahlzeit, Traum; Punkt am Sport-Tab, solange ein Lauf aufgezeichnet wird

- 0.14: Gym-Einrichtung fragt zusätzlich Schulterdrücken (Kurzhantel, je Hand), Seitheben, Dips und Klimmzüge mit Zusatzgewicht ab; zu schwach für Körpergewicht → Lat-Zug bzw. Bankdips
- 0.14: Übungsfotos (Start/Ende, als Animation) für 113 Übungen: Bibliothek, Übungsauswahl, Training, Übungs-Detail, Einrichtung. Quelle: free-exercise-db (gemeinfrei)

- 0.13: Calisthenics auf Gym-Niveau: Plan-Seite (Kraft-Block mit Wdh-/Halte-Progression, Skill-Stufen), Zusammenfassung nach dem Training, Übungs-Detail mit Eigengewicht-Rekorden, Tauschen im Zirkel/EMOM, Skill-Tree als Carousel
- 0.13: Trainings-Erinnerung morgens („Heute: Intervalle 3 × 1 km“), stumm an Ruhetagen und nach erledigtem Training
- 0.13: Laufen: Pace-Hinweise bei lockeren/langen Läufen, GPX-Import und -Export, Pull-to-Refresh → Health Connect
- 0.13: Gym: Overload-Regel pro Übung (Wdh-Bereich, Schrittweite, Deload), Körpermaße + Körperfett, Körpergewicht und relative Kraft in den Fortschritts-Slides, Notizen pro Übung wie bei Hevy
- 0.13: Health Connect schreibt Trainings (Gym, Cali, Läufe) und Gewicht, liest Gewicht anderer Apps
- 0.13.1: Emulator-Test aller neuen Seiten; Fixes: Cali-Plan-Seite lud nicht, Cali-Dauer bei vergessener Session, Einrückung aktuelle Stufe, Halte-Übungen im Detail, Einzahl/Mehrzahl

- Laufplan mit Zielzeit, Tapering und Wettkampf-Einheit, Plan-Seite mit Prognose, Wochenvolumen und Einheiten
- Gym: Progressive Overload (Gewichtsvorschlag, 1RM, „Vorher“-Werte), Fortschrittsseite pro Plan
- Fortschritts-Slides für Gym, Calisthenics und Laufen (Sport → Chart-Symbol)
- Öffnen/Zurück-Animation wie Google Health (Shared Axis, auch bei der Wisch-Geste)
- FABs auf allen Tabs gleich groß und gleich platziert
- Eigenes Diktat-Sheet (MD3) statt Google-Dialog: hört über Pausen hinweg weiter, Live-Text, „Fertig“ beendet
- Ersteinrichtung für Gym (Ziel, Erfahrung, Tage, Split, Kraftwerte → Plan mit Startgewichten), Laufen (Ziel, Termin, Niveau, Bestzeit, Lauftage, Zielzeit, Tapering) und Calisthenics (Max-Test, Skill-Stufen, Fokus-Skills, Tage)
- Gym-Splits mit mehreren Routinen im Wechsel (Ganzkörper A/B, Oberkörper/Unterkörper, Push/Pull/Beine)
- Laufplan auf eigene Wochentage, harte Einheiten nicht direkt hintereinander
- Plus-Menü wie Google Keep (Heute, Journal: Bild, Audio, Liste, Text)
- Neue Einträge als 85-%-Sheet mit Blur dahinter, Schließen durch Runterziehen
- Übungskatalog (~90 Gym-Übungen + Calisthenics-Varianten) mit Bewegungsmustern; im Training Übungen tauschen, hinzufügen, entfernen – „Nur heute“ oder „Dauerhaft im Plan“; Routine-Editor mit Einheiten-Umschalter und Tauschen
- Form-Morphing: Wochentag auf Heute, Farben/Emotionen, Rekord-Abzeichen, leere Zustände, Journal-Sperre, Mikrofon
- Sprach-Mahlzeit: übliche Portion aus der eigenen Historie, Offline-Erkennung einfacher Sätze ohne KI
- Open-Food-Facts-Kennung mit echter App-Version
- M3-Durchgang über alle Screens: segmentierte Listen statt Trennlinien (Gym/Cali-Plan, Bibliothek, Einstellungen, Suche, Hinzufügen, Rezepte), einheitliche gefüllte Karten, M3-Schalter/Knöpfe überall, Abstand unter FABs, harmonisierte Datenfarben, echte Versionsnummer, leere neue Einträge werden nicht gespeichert
- Laufen wie Runna: „Heute/Als Nächstes“-Karte, Wochenansicht mit Status (erledigt/heute/verpasst/übersprungen) und km-Fortschritt, Trainings-Detail mit Ablauf (Einlaufen, Belastungen, Trabpausen, Auslaufen, Paces), „Warum dieses Training?“, Verschieben/Überspringen, automatische Zuordnung von Läufen, geführtes Einlaufen vor Intervallen und geführte Tempoläufe, Plan-Bezug in der Lauf-Detailseite
- Gym wie Hevy/Strong: Workout-Zusammenfassung (Dauer, Volumen, Sätze, Rekorde, Vergleich), Trainingsverlauf, Übungs-Detail (Rekorde, 1RM-Verlauf, Historie), Aufwärmsätze, Live-Zeile (Sätze/Volumen), Gewicht wird in Folgesätze übernommen, Sätze pro Muskel/Woche, vergessene Sessions beenden/verwerfen
- ✨-Symbol im Editor immer aktiv (leer → Diktat → KI füllt aus)
- Laufen: Uhrzeit per Time Picker, Karte „Wochenstruktur“ ohne Trennlinien
- Am Handy getestet: Barcode-Scan, Health-Connect-Import

## Bewusst ausgeschlossen

- Backup, Export und Import (nicht gewünscht)
- Tablet-Layouts
