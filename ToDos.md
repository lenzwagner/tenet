# Tenet – offene ToDos

Stand: 07.10.2026, Version 1.0.0.16 (auf `main` gepusht, APK `tenet-1.0.0.16-release.apk`). Abgeleitet aus `App_Konzept.md`, dem aktuellen Code und den Tests im Emulator.

## Übergabe (Session 06.–07.10.2026)

**Stand:** 1.0.0.6 – 1.0.0.16 gebaut, committet und gepusht, Unit-Tests grün (u. a. neu: MacroOptimizer, RunPaceProgression, GoalCheck, RunFueling, RunWorkoutVariants). Details je Version unten unter „Zuletzt erledigt“.

**Wichtig vor dem nächsten Update am Handy:**
- Seit 1.0.0.6 ist die Datenbank mit SQLCipher verschlüsselt (Rohschlüssel, im Android Keystore gewrappt, `no_backup/db.key`). Die alte Klartext-DB wird beim ersten Start einmal umgewandelt. Im Emulator geprüft, mit echten Daten noch nicht → vorher mit Google anmelden und synchronisieren.
- Neue Laufplan-Logik (Tempo-Progression, progressive Intervalle, Pyramiden, Schwellen-Blöcke, Renntempo-Finish) gilt nur für **neu erstellte** Pläne → bestehenden Halbmarathon-Plan über „Neu erstellen“ neu anlegen.

**Arbeitsweise / Werkzeuge:**
- Release bauen: `printf '1.0.0.X\n' | ./build_new.sh` (setzt versionCode/-Name, signiert mit Debug-Keystore).
- Emulator: AVD `Pixel_10` auf Port **5580** starten (`emulator -avd Pixel_10 -port 5580`), weil BlueStacks Port 5555 belegt; `emulator-5554` ist BlueStacks und nicht anfassen.
- Baseline Profile neu erzeugen nur auf dem Emulator: `ANDROID_SERIAL=emulator-5580 ./gradlew :app:generateReleaseBaselineProfile` (deinstalliert die App dort!).
- Die `preview-*.png` im Projektordner gehören nicht zu Tenet und werden bewusst nicht committet.

**Design-Stand:** Stil „Klar“ (Standard; „Expressiv“ in Optionen → Darstellung → Stil): neutrale Flächen, Inter, iOS-Schalter/Segmente/Gruppen. Seitenkopf wie Apple Health: Farbverlauf je Bereich (`HeaderImage.wash`, `pageWash`, `SubPageWash`), großer Titel klappt in kleine Leiste; auf allen Haupt- und Unterseiten. Karten wie Health-Zusammenfassung: `CardHeader` (kleines Piktogramm + Titel in Kategorie-Farbe `HealthTint`, graue Info rechts, Chevron), 20-dp-Ecken (`tenetCardShape`), getönte Karten in „Klar“ weiß (`tenetAccentCardColors`).

**Offene Ideen aus der Session (nicht begonnen):**
- Übergänge vom Element aus (Rezept-Kachel/Notiz wächst in die Detailseite, Shared Element)
- Feinere Haptik (Satz abhaken, Kalorienziel erreicht, Rekord)
- Rekord-/Workout-Zusammenfassung als teilbares Bild
- Bestehenden Laufplan auf die neuen Einheiten umrechnen, ohne ihn neu anzulegen
- Sheets („Hinzufügen“, „Neuer Eintrag“) eventuell auch mit leichtem Farbverlauf

## Google-Anmeldung & Sync (0.15)

- [ ] Am Handy mit eigenem Google-Konto anmelden; Sync zwischen zwei Geräten testen (Neuanlage, Änderung, Löschen, erste Anmeldung auf neuem Handy)
- [ ] Fotos und Sprachmemos synchronisieren (Firebase Storage, braucht den Blaze-Tarif)

## Am echten Handy testen

Im Emulator nicht möglich.

- [x] Barcode-Scan mit echter Packung (Google Code Scanner → Open Food Facts)
- [ ] Update auf 1.0.0.15 über 1.0.0.5: Datenbank wird einmalig verschlüsselt – Daten danach vollständig? (vorher synchronisieren)
- [ ] Kaltstart-Tempo am Handy (Emulator: ~2 s → ~0,6 s seit 1.0.0.6)
- [ ] Seitenkopf-Farbverlauf und Klar-Stil hell/dunkel am echten Display, auch beim Scrollen
- [ ] Laufplan neu erstellen: Wunschzeit-Check, Tempo-Zonen, progressive Intervalle/Pyramide/Renntempo-Finish, Verpflegung, Pace je km; Sprachführung bei progressiven Reps und Schwellen-Blöcken im echten Lauf
- [ ] Prognose „heute / am Wettkampftag“ nach ein paar echten Läufen
- [ ] Kalorienring: kurzer Impuls mit Haptik beim Schließen eines Rings (im Emulator nicht gesehen)
- [ ] „Übung tauschen“ öffnet halbhoch und lässt sich hochziehen
- [ ] Calisthenics-Live-Mitteilung: Hold-Countdown-Chip in der Statusleiste, „Satz fertig“ vom Sperrbildschirm
- [ ] Tastatur verdeckt nirgends mehr Eingaben (Setup, Rezepte, Einträge, Training)
- [ ] Journal-Sperre mit Fingerabdruck bzw. Displaysperre
- [ ] Sprachmemo aufnehmen und abspielen
- [ ] Neues Diktat-Sheet am Handy: längere Pausen, Signaltöne beim Neustart der Erkennung?
- [ ] Echter GPS-Lauf: Sprachansagen, geführte Intervalle, Auto-Pause, Live-Benachrichtigung
- [x] Health-Connect-Import mit Uhr oder Strava, inkl. Duplikat-Erkennung bei parallelem GPS-Lauf
- [ ] Live-Mitteilung im Gym-Training (Android 16: Chip in der Statusleiste, Eingabe vom Sperrbildschirm)
- [ ] Hold-Timer und AMRAP per Lautstärketaste
- [ ] Haptik (Vibration) an allen Stellen
- [ ] „Lauf starten“ auf der Heute-Seite an einem Tag mit geplantem Lauf
- [ ] Trainings-Erinnerung morgens an einem Tag mit offenem Training (Emulator: Job läuft, heute war schon alles erledigt)
- [ ] Health Connect: Trainings und Gewicht schreiben, Gewicht aus Waage/Fitbit lesen. Vorher in Einstellungen → Health Connect → „Berechtigungen“ die neuen Rechte erlauben
- [ ] Schlaf bei den Träumen mit echter Uhr/Schlaf-App prüfen (Emulator hat keine Schlafdaten)
- [ ] Pace-Ansagen bei lockeren/langen Läufen (zu schnell / passt / langsamer, Halbzeit, Ziel)
- [ ] Formvideo mit echter Kamera aufnehmen (Satz-Knopf in der Skill-Session), Vergleich zweier Videos, ½ Tempo
- [ ] KI per Sprache: Mahlzeit diktieren (Ernährung → Hinzufügen → Mikrofon), Lauf nachtragen (Diktieren), Gym-Sätze (Mikrofon in der Session), Traum/Tagebuch nach „Erzählen“

## Heute

- [ ] Widget für den Startbildschirm: Kalorien heute, „Traum notieren“

## Sport · Calisthenics

- [ ] Fotos für Skill-Stufen (Front Lever, Planche, L-Sit, Hollow Body, freier Handstand) – in free-exercise-db nicht enthalten

## Sport · Laufen

- [ ] GPX-Strecke beim Export auch mit Pausen-Segmenten
- [ ] Bestehende Pläne auf die neuen Einheiten-Typen umrechnen (aktuell nur bei „Neu erstellen“)

## Journal · Notizen

- [ ] Teilen aus anderen Apps (Share-Target): Text oder Link wird Notiz

## Journal · Träume

- [ ] Widget „Traum notieren“ (Shortcut gibt es)

## Ernährung · Tracker

- [ ] Widget „Kalorien heute“

## KI-Ausfüllhilfe (NVIDIA NIM)

- [ ] API-Schlüssel bei NVIDIA neu erzeugen (er stand im Chat) und in den Einstellungen eintragen

## Technik

- [ ] APK ist durch SQLCipher von 9,7 auf 18,5 MB gewachsen (native Bibliothek für 4 Architekturen). Mit `abiFilters += "arm64-v8a"` wäre sie wieder kleiner, läuft dann aber nur noch auf 64-Bit-ARM (alle aktuellen Handys, Emulator auf dem Mac auch)
- [ ] Eigener Signaturschlüssel statt Debug-Keystore. Wechsel = einmal neu installieren, dabei gehen die Daten verloren (und der DB-Schlüssel – vorher synchronisieren).
- [ ] Der NIM-Schlüssel aus `local.properties` wird in die APK gebaut. Für eine private App okay, vor einer Weitergabe entfernen.
- [ ] Alte APKs im Projektordner aufräumen (~40 Stück, je ~10–18 MB, enthalten den NIM-Schlüssel)

## Zuletzt erledigt (0.5.0 – 1.0.0.16)

- 1.0.0.16: Karten wie in Apple Health – Kopfzeile mit kleinem Piktogramm und Titel in der Kategorie-Farbe (Sport orange, Ernährung grün, Journal türkis, Schlaf/Traum lila, Serien orange, Körper pink, Wasser/Info blau), graue Info rechts und Pfeil bei antippbaren Karten; rundere Ecken; auf Heute, Sport (Gym, Calisthenics, Laufen, Plan, Fortschritt, Zusammenfassung), Ernährung (Ringe, Wasser, Mahlzeiten, Auswertung, Rezept, Optimierer) und Journal (Schlaf, An diesem Tag, Muster, Impuls)

- 1.0.0.15: Farbverlauf auch auf allen Unterseiten (Detailseiten, Editoren, Plan, Training, Optimierer, Suche …) in der Farbe ihres Bereichs; Kopfleisten durchsichtig, beim Scrollen in Seitenfarbe; AMOLED-Träume bleiben schwarz

- 1.0.0.14: Farbverlauf läuft auch gescrollt ganz weich aus (vorher harte Kante hinter den Karten)

- 1.0.0.13: Farbverlauf oben bleibt beim Scrollen sichtbar (hinter Titelleiste und Bereichs-Umschalter), keine Trennlinie mehr unter der Titelleiste

- 1.0.0.12: Seitenkopf wie Apple Health – Farbverlauf je Bereich reicht hinter die ersten Karten, scrollt beim Scrollen weg und blendet aus, großer Titel klappt in eine schmale Leiste mit kleinem Titel (Fotos entfernt); Laufplan: Einheiten in „Nächste Einheiten“ antippbar (Ablauf, Pace, Verpflegung); abwechslungsreiche Einheiten – Intervalle wechseln klassisch / progressiv (jede Wiederholung schneller) / Pyramide, Schwelle jede zweite Woche als Blöcke („3 × 9 min Schwelle“), Langläufe mit Renntempo-Finish ab Woche 3; Sprachführung kennt die neuen Formen; Pace je Kilometer bei durchgehenden Läufen; Tempo-Zonen E/M/T/I/R der aktuellen Woche im Plan; Zeitfelder markieren beim Antippen den Inhalt (überschreiben) und warnen bei unplausiblen Zeiten

- 1.0.0.11: Tastatur verdeckt keine Eingaben mehr – alle Seiten enden über der Tastatur (fokussiertes Feld scrollt ins Bild, Knöpfe unten bleiben erreichbar), Tab-Leiste weicht bei offener Tastatur aus; Bestzeit-Hinweis zeigt die Pace des Laufs und die 5-km-Zeit mit Pace („Das sind 4:10 /km. Entspricht etwa 19:11 auf 5 km (3:50 /km).“), damit die 5-km-Zeit nicht als Pace gelesen wird

- 1.0.0.10: Laufplan-Einrichtung: Bestzeit und Zielzeit in drei Feldern (Std / Min / Sek, springt nach zwei Ziffern weiter) statt einem Feld ohne Doppelpunkt (aus 1:21:22 wurde 12122 Minuten); Tempo-Progression: die Trainings-Tempi werden Woche für Woche schneller, von der aktuellen Form zur Zielform (aus der Zielzeit, sonst ~1,5 % pro 4 Wochen, höchstens 10 %), Entlastungswochen halten das Tempo, Tapering-Wochen laufen auf Zielform; gilt auch für „Tempi an Form anpassen“; Vorschau zeigt Start- und End-Tempo; Wunschzeit mit Check beim Einrichten (realistisch / ehrgeizig / kaum zu schaffen + Vorschlag); Prognose heute und hochgerechnet auf den Wettkampftag auf der Laufen-Seite und im Plan, folgt jedem eingetragenen Lauf (Ziel-Bewertung im Plan jetzt gegen den Wettkampftag); Verpflegung je Lauf im Trainings-Detail: Kohlenhydrate vorher/unterwegs, Wasser vorher/unterwegs, Natrium bei > 2 h, nach Dauer, Art, Wettkampf und Körpergewicht

- 1.0.0.9: Listen als iOS-Gruppen (Haarlinie statt Lücke, nur außen gerundet), Leerzustände mit schlichtem Symbol und Knopf („Erste Notiz schreiben“, „Tag festhalten“, „Traum erzählen“, „Rezept importieren“), Play-Knopf bei Gym/Calisthenics erst wenn die Heute-Karte weggescrollt ist, „Hinzufügen“ (Ernährung) als Sheet über der Seite, „Übung tauschen“ öffnet halbhoch, Kalorienringe: Impuls mit Glanz und Haptik beim Schließen eines Rings, Calisthenics-Session als Live-Mitteilung (Satz x/y, Fortschritt, Hold-Countdown als Statusleisten-Chip, „Satz fertig“)

- 1.0.0.8: Foto-Header auch im Stil „Klar“ wieder da (statt großer Titel)

- 1.0.0.7: Neuer Stil „Klar“ (Standard, Optionen → Darstellung → Stil; „Expressiv“ = bisheriger Look): iOS-artige neutrale Flächen (#F2F2F7 / Schwarz, weiße bzw. #1C1C1E Zellen), Inter-Schrift mit Apple-Gewichten, weißer Segment-Schieber, iOS-Schalter, graue Abschnittstitel, quadratische Icon-Kacheln in den Einstellungen, ruhigere Animationen. Seiten-Check: „Pause 0:00“ zeigt automatische Pause, Serien-Karte, Skill-Karussell ohne abgeschnittene Texte, eigenes Icon für Stufenaufstiege, kein doppeltes „Plan erstellen“ beim Laufen, Ernährungs-Aktionen in einer Zeile, ein KI-Verbindungstest, Wochenkalender nennt Pläne ohne feste Tage, erstes Workout „?“ statt 0 kg

- 1.0.0.6: Tempo: SQLCipher mit Rohschlüssel statt PBKDF2 (Kaltstart im Emulator ~2 s → ~0,6 s, vorher ~1 s Schlüsselableitung auf dem Main-Thread), Baseline Profile für Start und alle Tabs (`:baselineprofile`, neu erzeugen mit `ANDROID_SERIAL=… ./gradlew :app:generateReleaseBaselineProfile` – deinstalliert die App auf dem Testgerät!), Header-Fotos einmal im Hintergrund dekodiert statt bei jedem Tab-Wechsel, Glas-Leiste rechnet Blur in reduzierter Auflösung, Intro ~2,3 s → ~1,3 s, Seitenübergänge 300 → 250 ms, Sheets und Diagramme schneller; Datenbank mit SQLCipher verschlüsselt (Schlüssel im Android Keystore, nicht im Auto-Backup; alte Klartext-DB wird einmalig umgewandelt, nicht lesbare DB wird beiseitegelegt statt abzustürzen); Makro-Optimierer (Ernährung → „Was passt noch zu meinen Zielen?“: Rest von heute oder ganzer Tag aus Rezepten mit Nährwerten, Favoriten, zuletzt Gegessenem und Grundlebensmitteln, eigene lokale Suche statt OR-Tools, „Anderer Vorschlag“, Gerichte ausschließen, alles in eine Mahlzeit eintragen); Formvideos in der Skill-Session (System-Kamera, max. 30 s, nur lokal, pro Satz oder Session, Verlauf beim Skill, zwei Videos nebeneinander vergleichen, ½ Tempo); AMOLED-Schwarz (Aus / Nur Träume / Ganze App, nur im dunklen Modus); Serifenschrift Newsreader für Tagebuch und Träume (Editor und Karten)

- 1.0.0.5: Laufplan: nachgetragene oder importierte Läufe zählen für die Einheit am selben Tag oder für eine verpasste Einheit bis zu 2 Tage davor (übersprungene nie), Plan-Detail zählt wie die Wochenansicht; Satztabelle zeigt „22,5“ statt „22.5“; Live-Mitteilung liest Tabellenänderungen nach; Mitteilungs-Knöpfe (+30 s, Überspringen, Eintragen) funktionieren auch nach Neustart des Prozesses (Pause und Session werden gesichert)

- 1.0.0.1–1.0.0.4: Gym-Einrichtung: eigener Split (Trainingstage selbst benennen), optionale 30°-Schrägbank-Werte (LH/KH) ersetzen fehlende Bankdrück-Werte, gewählte 1RM-Formel überall; Brzycki korrigiert (36/(37 − Wdh)); Plan aus Trainingshistorie neu aufbauen; Startgewicht aus anderen Bank-Varianten (gekennzeichnet, keine PRs); beim Beenden nur abgehakte Sätze behalten, Sätze löschbar; Pause bleibt beim Wechsel in/aus dem Trainingsmodus; wöchentliche Gewichtsabfrage am Montag; „Kein Traum“ auf Heute; Schlaf der Nacht im Traum-Editor; KI: Modellliste vom Schlüssel laden, Modelle einzeln testen

- 0.25: Mehrere Rezepte auf einmal: Import-Modus „Mehrere“ (Links oder Text mit Links einfügen), nacheinander importiert und direkt gespeichert, Status je Link mit „Erneut“, Tippen öffnet das Rezept; Teilen eines Textes mit mehreren Links startet den Mehrfach-Import

- 0.24.1: Rezept-Import: Vorschau direkt bearbeitbar (Titel, Kategorie, Portionen, Minuten, Zutaten, Schritte inkl. Reihenfolge), Vegetarisch-Erkennung aus den bearbeiteten Zutaten; KI erkennt Anweisungen im Fließtext als Schritte

- 0.24: Laufform „Deine Form“ (5 km, 10 km, Halbmarathon, Marathon) aus allen Läufen der letzten 8 Wochen inkl. Health Connect, mit Puls (Daniels-VDOT + Pulsreserve nach Swain/Karvonen, Maxpuls nach Tanaka aus dem Profilalter); Plan-Prognose und „Tempi an Form anpassen“ nutzen sie; Trainingsmodus: kg/Wdh direkt eintippbar; Gym-Steigerung rundet auf 2,5-kg-Raster (62,5 → 67,5 statt 70); Health-Connect-Import mit Test-App (tools/hcseed) im Emulator geprüft

- 0.23.2: Alle Seiten im Zwei-Farben-Modus geprüft; Disziplin-Farben (Wochenkalender, Sport) folgen jetzt Primär-/Sekundärfarbe statt drei fester Töne. Datenfarben (Makro-Ringe, Stimmung, Pulszonen) bewusst mehrfarbig

- 0.23.1: Ganze App zweifarbig: „Nur zwei Farben“ ist Standard (Primär- + Sekundärfarbe, keine Bereichs-/Tertiärfarben; in den Einstellungen abschaltbar); App-Icon und Intro nur noch Lavendel + Blau

- 0.23: Supersätze auch für ein einzelnes Training (Übungsmenü „Supersatz mit nächster Übung“ / „lösen“, nur heute oder dauerhaft, Anzeige „Supersatz A“); Dropsätze: „Dropsatz anhängen“ (Übungsmenü) oder „Dropsatz danach“ (Satzmenü) mit ~75 % Gewicht, ohne Pause davor, Trainingsmodus/Mitteilung „sofort weiter … × max“

- 0.22.1: Supersätze im Trainingsmodus/Mitteilung abwechselnd (A1, B1, A2 …, 20 s Wechsel, Pause nach der Runde); erstes Training ohne Gewicht: „? kg × 8“, Eingabe statt „Wie geplant“; Farbstil-Vorschau zeigt bei „Nur zwei Farben“ nur zwei Farben; Notiz-Kacheln: Tags bleiben unten sichtbar; Rezept-Import übernimmt Zutaten, die nur in den Schritten stehen

- 0.22: Intro-Animation bei jedem Start vom Homescreen: Logo baut sich auf (Stamm federt hoch, Querbalken öffnet mit Lichtreflex, Punkt ploppt mit Puls, „Tenet“ blendet ein), dann sanfter Übergang in die App; antippen überspringt, aus bei deaktivierten Systemanimationen; System-Splash nahtlos in Indigo

- 0.21.1: Neues App-Icon: „T“ aus den drei Bereichsfarben (Querbalken Petrol→Violett, Stamm Blau) auf Indigo mit weichem Schimmer; Themed-Icon-Ebene für Android 13+

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
