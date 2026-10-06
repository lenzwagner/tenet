# Kraftprognosen: Formeln, Incline-Übertragung und Tests

Stand: 2026-10-03. Die Tests prüfen Rechenwege und Datenfilter. Es liegen keine individuellen Messreihen des Nutzers vor; persönliche Vorhersagegenauigkeit wurde damit nicht validiert.

## Gefundene Fehler und Änderungen

- Brzycki hatte im Nenner `36 - Wiederholungen`. Korrekt in der üblichen Bruchdarstellung: `Gewicht * 36 / (37 - Wiederholungen)`. Auch der bisherige Test enthielt denselben Fehler. Bei 100 kg × 8 waren es 128,57 statt 124,14 kg.
- Epley bleibt `Gewicht * (1 + Wiederholungen / 30)`. Ein tatsächlich gehobener Einzelsatz wird bei beiden Formeln unverändert übernommen.
- NaN und unendliche Gewichte werden verworfen. Die inverse Gewichtsberechnung verwendet dieselbe Formel wie die 1RM-Schätzung.
- Einrichtung: optionale 30°-Incline-Werte (LH oder KH je Hand) können fehlende flache Bench-Werte ersetzen. Direkt eingegebene Bench-Werte haben Vorrang. Die Einrichtung berücksichtigt die ausgewählte Formel.
- Laufende Session: Ohne eigene Übungshistorie können abgeschlossene Sätze anderer freier Bench-Varianten einen gekennzeichneten Startwert liefern. Eigene Historie hat Vorrang, danach kommt die Übertragung, zuletzt der Startwert aus der Einrichtung. Automatische Übertragung nutzt Epley wie die bisherigen Session-Vorschläge.
- Nur abgehakte Sätze, keine Aufwärmsätze, keine Maschinenübertragung. Für automatische Übertragung: 1–10 Wiederholungen, letzte geeignete Session innerhalb von 90 Tagen. Die 90 Tage sind eine Produktregel, kein Literaturgrenzwert.
- Übertragene Schätzungen erzeugen keine PRs und werden nicht als absolvierte Bench-Sätze gespeichert.

## Literaturbasis

[Brzycki (1993), Strength Testing—Predicting a One-Rep Max from Reps-to-Fatigue](https://doi.org/10.1080/07303084.1993.10606684) beschreibt die Formel in der Dezimaldarstellung `Gewicht / (1.0278 - 0.0278 * Wiederholungen)`. Die verwendete Bruchdarstellung vermeidet gerundete Koeffizienten; beide sind wegen dieser Rundung nur näherungsweise identisch. Die Gleichungen werden auch in [Reynolds, Gordon & Robergs (2006), Tabelle 5](https://www.unm.edu/~rrobergs/478RMStrengthPrediction.pdf) verglichen.

[Reynolds et al. (2006)](https://pubmed.ncbi.nlm.nih.gov/16937972/) untersuchten 70 Personen mit 1-, 5-, 10- und 20RM-Tests. 5RM lieferte die höchste Prognosegenauigkeit. Die Bench-Regression lautet `1RM = 1.1307 * 5RM-Gewicht + 0.6999`, mit Standardfehler 2,98 kg. Bei angenommenen 80 kg × 5: 91,1559 kg. Epley ergibt 93,3333 kg, Brzycki 90 kg. Die Nähe in diesem Beispiel ist ein Gleichungsvergleich, kein Beweis individueller Genauigkeit.

[Rodríguez-Ridao et al. (2020), Tabelle 1](https://pmc.ncbi.nlm.nih.gov/articles/PMC7579505/) berichten bei 30 Trainierten folgende direkt gemessenen Gruppenmittel:

| LH-Bankwinkel | Gemessener 1RM, Mittel ± SD |
|---|---:|
| 0° | 81,4 ± 15,5 kg |
| 15° | 72,0 ± 14,0 kg |
| 30° | 63,3 ± 12,3 kg |
| 45° | 57,9 ± 9,7 kg |
| 60° | 52,2 ± 9,0 kg |

Daraus leitet die App für 30° den Näherungsfaktor `63.3 / 81.4 ≈ 0.77764` ab. Das Verhältnis von Gruppenmitteln ist keine validierte individuelle Regressionsgleichung. Andere Winkel können deutlich abweichen. Historische Katalogeinträge speichern keinen Winkel; die UI nennt deshalb ausdrücklich die 30°-Annahme.

[Saeterbakken et al. (2011)](https://pubmed.ncbi.nlm.nih.gov/21225489/) fanden bei zwölf trainierten Männern eine um 17 % niedrigere gesamte KH-Last gegenüber LH. Für KH-Eingaben je Hand verwendet die App deshalb `0.83 / 2`. Die Kombination dieses Faktors mit dem 30°-Faktor ist eine zusätzliche Heuristik aus zwei Studien, keine separat validierte Incline-KH→Bench-Gleichung. Maschinengewichte werden nicht damit umgerechnet.

## Reproduzierbare Rechenbeispiele

Diese Last-/Wiederholungspaare sind realistische Testeingaben, keine behaupteten Nutzerleistungen:

| Eingabe | Epley-1RM | Brzycki-1RM |
|---|---:|---:|
| 60 kg × 10 | 80,000 kg | 80,000 kg |
| 70 kg × 8 | 88,667 kg | 86,897 kg |
| 80 kg × 5 | 93,333 kg | 90,000 kg |
| 100 kg × 3 | 110,000 kg | 105,882 kg |
| 120 kg × 1 | 120,000 kg | 120,000 kg |

| Übertragung bei angenommener 30°-Bank | Grobes flaches LH-e1RM (Epley) | Startvorschlag für 8 Wdh + 2 Reserve |
|---|---:|---:|
| Incline LH 60 kg × 8 | 97,731 kg | 72,5 kg |
| Incline KH 30 kg je Hand × 8 | 117,749 kg | 87,5 kg |

Session-Startgewichte werden auf 2,5-kg-Schritte abgerundet. Das ist eine editierbare Rastervorgabe, keine Garantie der lokal verfügbaren Hanteln. Setup-Startgewichte verwenden das bestehende Rundungsverfahren. Bei Eingaben deutlich vor Muskelversagen ist die Wiederholungsreserve unbekannt; sie wird der Quellleistung nicht erfunden hinzuaddiert. Die zwei Reserve-Wiederholungen betreffen nur die Zielplanung.

## Regressionstests

`OneRepMaxTest` prüft Referenzwerte, Formelwahl, gleiche Ergebnisse bei zehn Wiederholungen, inverse Berechnung und ungültige Fließkommazahlen.

`BenchStrengthTest` prüft die publizierten Gruppenmittel als Kalibrierungsbeispiel, LH/KH-Einheiten, eigene Bench-Priorität, Setup-Übertragung, Quellkennzeichnung, Alter und Reihenfolge der Sessions, offene/Aufwärm-/hochrepetitive Sätze, Ausschluss von Maschinen und den separaten Reynolds-Gleichungsvergleich.

Ausführung:

```sh
./gradlew :core:common:testDebugUnitTest :feature:sport:compileDebugKotlin
```

Prüflauf am 2026-10-03: **259 Tests, 0 Fehler, 0 übersprungen**. Darunter 11 Formeltests, 15 Bench-Übertragungstests und 11 Setup-Tests. Sport-Modul erfolgreich kompiliert.

Ein erfolgreicher Testlauf bestätigt die implementierte Mathematik und Filter, nicht die physiologische Genauigkeit für eine bestimmte Person. UI-Darstellung und Gewichtseingabe benötigen weiterhin einen Gerätetest.
