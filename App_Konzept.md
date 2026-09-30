# Tenet, Konzept für eine All-in-One Android-App

*Stand: September 2026. Design-Grundlage: Material Design 3 (inkl. M3 Expressive).*

> **Namenshinweis:** Vor dem ersten Gradle-Projekt kurz prüfen, ob „Tenet“ im Play Store frei ist und welche Package-ID du nimmst. Vorschlag: `app.tenet.android` oder `de.<deinname>.tenet`. Ein Umbenennen nach dem ersten Release ist deutlich unangenehmer als der Check jetzt.

---

## 1. Vision und Leitprinzipien

**Eine App für den persönlichen Alltag:** Training, Ernährung, Gedanken und Träume an einem Ort, mit einem gemeinsamen Datenmodell, einer gemeinsamen Suche und einem einzigen Capture-Button.

| Prinzip | Bedeutung für die Umsetzung |
|---|---|
| **Offline-first** | Alles läuft lokal (Room/SQLite). Netzwerk nur optional (Barcode-Lookup, Backup). |
| **Privacy by Design** | Tagebuch und Traumtagebuch sind sensibel. Datenbank-Verschlüsselung, App-Lock, kein Tracking. |
| **Capture in < 3 Sekunden** | Jede Eingabe (Satz, Notiz, Mahlzeit, Traum) ist aus jedem Tab über die Quick-Capture-Aktion erreichbar. |
| **Modular** | Module lassen sich in den Einstellungen deaktivieren. Die Tab-Bar passt sich an. |
| **Ein Datenmodell, viele Sichten** | Notiz, Tagebuch und Traum teilen sich eine Entity mit Typfeld. Suche, Tags und Export funktionieren dadurch einmal für alles. |

---

## 2. Informationsarchitektur

### 2.1 Top-Level-Ziele (Floating Tab Bar)

M3 empfiehlt für Bottom Navigation **3 bis 5 Ziele**. Daraus ergibt sich:

| # | Tab | Icon (Material Symbols) | Inhalt |
|---|---|---|---|
| 1 | **Heute** | `today` | Dashboard: Tageszusammenfassung aller Module |
| 2 | **Sport** | `fitness_center` | Drei Disziplinen per Slider: Gym, Calisthenics, Laufen. Je Disziplin Trainingsplan, Sessions, Fortschritt |
| 3 | **Journal** | `auto_stories` | Notizen, Tagebuch, Traumtagebuch |
| 4 | **Ernährung** | `restaurant` | Kalorientracker und Rezepte |
| 5 | **Einstellungen** | `settings` | Konfiguration, Backup, Module |

> **Design-Hinweis:** M3 würde Einstellungen eher in die Top App Bar (Avatar oder Overflow) legen, weil sie selten genutzt werden. Wenn der Tab dir wichtig ist, behalte ihn. Alternative: Heute-Tab weglassen und das Dashboard als Startansicht des Journal-Tabs nutzen. Beides ist sauber, entscheide nach Nutzungsfrequenz.

### 2.2 Unterstruktur je Tab

```
Heute
 └─ Dashboard (Karten je Modul, Streaks, Schnellaktionen)

Sport
 ├─ Disziplin-Slider oben: [Gym | Calisthenics | Laufen]
 ├─ Gym
 │   ├─ Trainingsplan (Routinen, Split, Periodisierung)
 │   ├─ Aktive Session (Vollbild, eigener Flow)
 │   ├─ Übungsbibliothek
 │   └─ Fortschritt (PRs, 1RM-Verlauf, Volumen)
 ├─ Calisthenics
 │   ├─ Trainingsplan (Skill- und Kraftblöcke)
 │   ├─ Skill-Tree (Progressionsstufen je Skill)
 │   ├─ Aktive Session (Wdh, Holds, Zirkel)
 │   └─ Fortschritt (Hold-Zeiten, Stufenaufstiege)
 ├─ Laufen
 │   ├─ Trainingsplan (Zielrennen, Wochenstruktur)
 │   ├─ Aktiver Lauf (GPS, Pace, Intervalle)
 │   ├─ Lauf-Historie (Karte, Splits)
 │   └─ Fortschritt (Wochenumfang, Bestzeiten)
 └─ Gemeinsam: Wochenkalender aller Einheiten, Körpermaße

Journal
 ├─ Segmented Button: [Notizen | Tagebuch | Träume]
 ├─ Notizen (Grid/Liste, Tags, Pins, Checklisten)
 ├─ Tagebuch (Kalender-/Timelineansicht, Stimmung)
 └─ Träume (Liste, Symbole, luzide Träume, Muster)

Ernährung
 ├─ Segmented Button: [Tracker | Rezepte]
 ├─ Tracker (Tagesansicht, Mahlzeiten, Makro-Ringe)
 ├─ Lebensmittel hinzufügen (Suche, Barcode, Favoriten, Rezept)
 └─ Rezepte (Sammlung, Detail, Kochmodus)

Einstellungen
 ├─ Darstellung, Module, Ziele & Einheiten
 ├─ Erinnerungen, Sicherheit
 └─ Daten (Backup, Export, Import)
```

---

## 3. Die Floating Tab Bar

### 3.1 Aufbau

Eine schwebende, pillenförmige Leiste über dem Content, statt der klassischen randbündigen `NavigationBar`. Sie entspricht dem **Floating Toolbar** aus M3 Expressive, das in der offiziellen Spezifikation unter *Toolbars* geführt wird. Der Bottom App Bar (Baseline) ist dort inzwischen als „not recommended“ markiert, Docked und Floating Toolbar sind die Nachfolger.

```
        ╭──────────────────────────────────────────╮
        │  ◉ Heute   🏋  📖   🍽   ⚙   │  ( + )  │
        ╰──────────────────────────────────────────╯
              ↑ aktiver Tab mit Label + Pill-Indikator   ↑ Quick-Capture-FAB
                 Container: translukent + Blur, Content scrollt sichtbar darunter durch
```

**Spezifikation:**

| Eigenschaft | Wert |
|---|---|
| Container | `surfaceContainer` mit reduzierter Deckkraft plus Blur (siehe 3.2), Shape `CircleShape` / `extraLarge` |
| Höhe | 64 dp (M3-Default für alle Toolbars) |
| Innenabstand | mind. 16 dp außen, gleichmäßiges Padding zwischen den Items |
| Abstand zum Rand | 16 dp seitlich, 16 dp + `navigationBars`-Inset unten |
| Aktiver Tab | Pill-Indikator in `secondaryContainer`, Icon gefüllt, Label sichtbar (`labelMedium`) |
| Inaktive Tabs | Nur Icon (outlined), `onSurfaceVariant`, Content Description gesetzt |
| Touch Target | mind. 48 × 48 dp |
| FAB rechts | `FloatingActionButton` in `primaryContainer`, als eigenes Element neben der Leiste. In Compose ist „Floating Toolbar mit FAB“ als Konfiguration direkt vorgesehen |
| Animation | Indikator wandert per Spring (`spring(dampingRatio = 0.8f, stiffness = MediumLow)`), Label blendet ein |
| Farbvariante | **Standard** (`surfaceContainer`). M3 Expressive bietet alternativ **Vibrant** (`primaryContainer`), falls die Leiste mehr Präsenz haben soll |

### 3.2 Transparenz, Blur und Lesbarkeit

Die Leiste soll transparent wirken, der Content also sichtbar darunter durchlaufen. Wichtig dabei: **vollständig transparent funktioniert nicht.** Sobald eine Liste mit Text darunter scrollt, verlieren Icons und Label ihren Kontrast, und die 3:1-Anforderung für UI-Elemente ist verletzt. Die Lösung ist eine **translukente Schicht mit Unschärfe**, umgangssprachlich Glasmorphismus.

**Drei Ebenen, von hinten nach vorn:**

1. **Blur des Hintergrunds.** Der Bereich hinter der Leiste wird weichgezeichnet (Radius ca. 24 dp). Dadurch entsteht ein flächiger, kontrastarmer Untergrund, auf dem Icons zuverlässig lesbar sind.
2. **Tonale Schicht.** `surfaceContainer` mit einer Deckkraft von etwa **0,7 im Light Mode** und **0,6 im Dark Mode**. Im Dark Mode darf sie transparenter sein, weil dunkle Flächen ohnehin weniger Kontrast schlucken.
3. **Feine Kontur.** 1 dp Border in `outlineVariant` mit ca. 30 % Deckkraft. Sie trennt die Leiste vom Untergrund, wenn der Hintergrund zufällig dieselbe Helligkeit hat. Ohne diese Kante „verschwindet“ eine Glasleiste auf hellen Flächen.

**Schattenwurf:** Bei transparenten Containern bitte **keine** klassische `shadowElevation`. Der Schatten wird unter der halbdurchsichtigen Fläche sichtbar und sieht schmutzig aus. Stattdessen `tonalElevation` und die Kontur aus Punkt 3.

**Adaptive Deckkraft (empfohlen):** Die Deckkraft wird an den Scroll-Zustand gekoppelt. Steht die Liste am Anfang, ist die Leiste fast transparent (0,4). Sobald Content darunter liegt, animiert sie auf 0,75 hoch. Das gibt Leichtigkeit ohne Lesbarkeitsverlust.

```kotlin
val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
val alpha by animateFloatAsState(
    targetValue = if (scrolled) 0.75f else 0.4f,
    animationSpec = tween(200),
    label = "barAlpha",
)
```

**Technische Umsetzung des Blurs:**

| Ansatz | API | Bewertung |
|---|---|---|
| `Modifier.blur()` auf dem Content unter der Bar | API 31+ | Kein echtes Backdrop-Blur, du müsstest den Content doppelt zeichnen. Unpraktisch. |
| `RenderEffect.createBlurEffect` + `RenderNode` | API 31+ | Echtes Backdrop-Blur, aber viel Handarbeit. |
| **Haze-Library** (`dev.chrisbanes.haze`) | API 21+, echtes Blur ab 31 | **Empfehlung.** Genau für diesen Fall gebaut: `hazeSource` auf den Content, `hazeEffect` auf die Leiste. |
| Fallback ohne Blur | alle | Deckkraft auf 0,92 anheben. Ohne Unschärfe braucht es mehr Deckung, sonst flimmert Text durch. |

```kotlin
val hazeState = rememberHazeState()

Box {
    LazyColumn(
        modifier = Modifier.hazeSource(hazeState),
        contentPadding = PaddingValues(bottom = 112.dp),  // 64 Bar + 32 Abstand + Inset
    ) { /* Content */ }

    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(16.dp)
            .height(64.dp)
            .clip(CircleShape)
            .hazeEffect(hazeState) {
                backgroundColor = MaterialTheme.colorScheme.surfaceContainer
                tints = listOf(
                    HazeTint(
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = alpha)
                    )
                )
                blurRadius = 24.dp
            },
    ) { /* Tabs */ }
}
```

**Edge-to-Edge ist Voraussetzung:** `enableEdgeToEdge()` in der `MainActivity` und `Window.setNavigationBarContrastEnforced(false)`, sonst zeichnet das System einen grauen Scrim hinter die Navigationsleiste und der Effekt ist dahin. Ab Android 15 ist Edge-to-Edge für `targetSdk 35` ohnehin erzwungen.

**Barrierefreiheit:** Wenn im System „Transparenz reduzieren“ bzw. eine entsprechende Accessibility-Einstellung aktiv ist, schaltet die App auf eine deckende Leiste um. Gleiches gilt als Option in den eigenen Einstellungen unter Darstellung.

### 3.3 Verhalten

- **Scroll-Verhalten:** Beim Runterscrollen fährt die Bar nach unten weg (oder schrumpft auf den FAB), beim Hochscrollen kommt sie zurück. Umsetzung über `NestedScrollConnection`.
- **Content-Padding:** Listen bekommen unten `contentPadding = 64 dp + 32 dp + Insets`. Bei einer transparenten Leiste ist das besonders wichtig, weil der letzte Eintrag sonst unter der Glasfläche hängen bleibt und dort unscharf, aber eben noch sichtbar ist. Das wirkt wie ein Fehler.
- **Ausblenden:** In jeder aktiven Sport-Session (Gym, Calisthenics, Lauf), im Kochmodus und im Editor ist die Bar ausgeblendet (Fokus-Modus).
- **Reselect:** Erneutes Tippen auf den aktiven Tab scrollt nach oben bzw. springt zur Wurzel des Tabs.
- **Zustand pro Tab:** Jeder Tab hat einen eigenen Back Stack, der beim Wechsel erhalten bleibt (`saveState`/`restoreState`).

### 3.4 Quick Capture (der FAB)

Der FAB ist **kontextsensitiv**:

| Aktiver Tab | Kurzer Tap | Long Press |
|---|---|---|
| Heute | **FAB Menu** (`FloatingActionButtonMenu`, offizielle M3-Komponente) mit allen Eintragstypen | - |
| Sport (Gym) | Workout starten | Leeres Workout |
| Sport (Calisthenics) | Session starten | Einzelnen Skill-Versuch loggen |
| Sport (Laufen) | Lauf starten (GPS) | Lauf manuell nachtragen |
| Journal | Eintrag im aktuellen Segment | Menü: Notiz / Tagebuch / Traum |
| Ernährung | Lebensmittel hinzufügen | Barcode direkt |
| Einstellungen | FAB ausgeblendet | - |

### 3.5 Adaptive Layouts

Auf Tablets und Foldables (Window Size Class *Medium/Expanded*) wird die Floating Bar zu einer **Navigation Rail** links, auf sehr breiten Layouts zu einem **Navigation Drawer**. Genau diese Staffelung sieht M3 vor: Navigation Bar für kleine, Navigation Rail für mittlere, Navigation Drawer für große Geräte. Empfehlung: `NavigationSuiteScaffold` als Basis und die Floating Bar als eigene Variante für *Compact* einsetzen.

---

## 4. Design-System (M3)

### 4.1 Farbe

- **Dynamic Color** (Material You) ab Android 12 als Standard, abschaltbar.
- **Fallback-Seed-Farbe** für ältere Geräte und bei deaktiviertem Dynamic Color, z. B. ein tiefes Petrol `#006A6A`. Schema mit dem Material Theme Builder erzeugen.
- **Modulfarben** als dezente Akzente, jeweils harmonisiert mit dem Hauptschema (`MaterialColors.harmonize` bzw. eigene Tonpaletten):

| Modul | Akzent-Rolle | Einsatz |
|---|---|---|
| Sport | `tertiary` als Basis, je Disziplin eine harmonisierte Custom Color (Gym, Calisthenics, Laufen) | PR-Badges, Charts, Kalenderpunkte, Slider-Indikator |
| Journal | `secondary` | Stimmungsskala |
| Träume | eigener Custom-Ton (Indigo/Violett) | Nachtstimmung, luzide Markierung |
| Ernährung | Makro-Farben: Protein, Kohlenhydrate, Fett als drei harmonisierte Custom Colors | Ringe, Balken |

- Light und Dark Mode vollständig, zusätzlich optional ein **AMOLED-Schwarz** für das Traumtagebuch (wird oft nachts genutzt).

### 4.2 Typografie

- M3-Typescale (Display, Headline, Title, Body, Label).
- Schrift: **Roboto Flex** oder System-Default. Für Tagebuch-Editor optional eine Serif (z. B. *Newsreader*) als Leseschrift, einstellbar.
- Zahlen in Gym und Ernährung mit `fontFeatureSettings = "tnum"` (tabellarische Ziffern), damit Spalten nicht springen.

### 4.3 Form, Elevation, Motion

- Shape-Skala: Karten `medium` (12 dp), Sheets `extraLarge` (28 dp oben), Chips `small`.
- M3 Expressive Shapes (z. B. Cookie/Clover) sparsam für Akzente: PR-Badge, Streak-Anzeige.
- Motion: Spring-basierte Übergänge, **Shared Element Transitions** von Listenkarte zu Detailansicht (Compose `SharedTransitionLayout`), Container Transform beim FAB zum Editor.
- **Edge-to-Edge** überall, Predictive Back unterstützen.

### 4.4 Komponenten-Mapping nach dem offiziellen M3-Katalog

Grundlage ist der Komponentenkatalog von m3.material.io. Er gliedert sich in **Action, Containment, Communication, Navigation, Selection und Text input**. Die folgende Tabelle ordnet jeder Komponente ihren Einsatzort in der App zu. Was hier nicht auftaucht, brauchst du vorerst nicht.

**Buttons (Action)**

| M3-Komponente | Einsatz in der App |
|---|---|
| **Buttons** | Primäraktionen in Dialogen und Formularen, „Session starten“, „Als Mahlzeit loggen“ |
| **Button groups** | Satztyp wählen (Aufwärmen / Arbeitssatz / Drop-Set), Portionsgröße |
| **Icon buttons** | Aktionen in App Bars, Satz löschen, Favorit markieren |
| **FABs** | Quick Capture in jedem Tab |
| **Extended FABs** | „Lauf starten“ auf der Laufen-Seite, wenn der Kontext eindeutig ist |
| **FAB menu** | Heute-Tab: alle Eintragstypen aus einem FAB heraus |
| **Segmented buttons** | Journal-Segmente, Ernährungs-Segmente, Zeitraum in Charts (Woche / Monat / Jahr) |
| **Split buttons** | „Workout starten“ mit Dropdown für die Auswahl einer anderen Routine |

**Navigation**

| M3-Komponente | Einsatz in der App |
|---|---|
| **Navigation bar** | Konzeptionelle Basis der Floating Tab Bar (Compact) |
| **Navigation rail** | Tablet- und Foldable-Layout (Medium) |
| **Navigation drawer** | Sehr breite Layouts (Expanded), optional Desktop-Modus |
| **Toolbars** (Floating Toolbar) | Die tatsächliche Umsetzung der Tab Bar, inkl. FAB-Konfiguration |
| **App bars** | `LargeTopAppBar` je Tab-Wurzel, `TopAppBar` in Detail- und Session-Screens |
| **Tabs** | Disziplin-Slider im Sport-Tab (`PrimaryTabRow` + `HorizontalPager`) |
| **Search** | Globale Suche, Lebensmittel- und Übungssuche |

**Containment**

| M3-Komponente | Einsatz in der App |
|---|---|
| **Cards** | Dashboard-Karten, Übungskarten in der Session, Rezeptkacheln, Skill-Karten |
| **Bottom sheets** | Satz bearbeiten, Portion wählen, Stimmung setzen, Plate Calculator |
| **Side sheets** | Filter auf Tablet-Layouts |
| **Dialogs** | Löschbestätigung, Ziel ändern, Stufenaufstieg bestätigen |
| **Carousel** | Rezeptfotos, „An diesem Tag“-Rückblicke, Formvideos je Skill |
| **Lists** | Übungsbibliothek, Lauf-Historie, Einstellungen, Suchergebnisse |
| **Divider** | Gliederung innerhalb von Listen und Karten |
| **Menus** | Overflow-Menüs, Sortierung, „Mehr“ pro Übung |

**Selection & Text input**

| M3-Komponente | Einsatz in der App |
|---|---|
| **Checkbox** | Checklisten-Notizen, Einkaufsliste, Zutaten im Kochmodus |
| **Chips** | Tags, Traumsymbole, Muskelgruppen-Filter, Emotionen, Rezept-Tags |
| **Radio button** | Einfachauswahl in Einstellungen (1RM-Formel, Pacemethode) |
| **Switch** | Module an/aus, Dynamic Color, luzid ja/nein, Erinnerungen |
| **Sliders** | Klarheit eines Traums (1 bis 5), RPE, Makroverteilung in Prozent |
| **Date pickers** | Eintrag rückdatieren, Zieldatum eines Laufplans |
| **Time pickers** | Erinnerungszeiten, Startzeit einer nachgetragenen Einheit |
| **Text fields** | Editor, Gewicht und Wiederholungen, Nährwerte, Rezeptschritte |

**Communication**

| M3-Komponente | Einsatz in der App |
|---|---|
| **Progress indicators** | Tagesziel-Ringe, Rest-Timer, Intervallfortschritt (Wavy-Variante nutzen) |
| **Loading indicator** | Kurze Wartezeiten, z. B. Barcode-Lookup |
| **Badges** | Ungelesene PRs, Anzahl offener Checklistenpunkte auf Tab-Icons |
| **Snackbar** | Undo nach jedem Löschvorgang, „Mahlzeit gespeichert“ |
| **Tooltips** | Erklärung seltener Icons, z. B. RPE oder VDOT |

> **Hinweis zur Bibliothek:** Nicht jede dieser Komponenten ist in jeder `androidx.compose.material3`-Version stabil. FAB Menu, Split Button, Loading Indicator, Wavy Progress und die Toolbars kamen mit M3 Expressive dazu und sind teils noch als experimentell markiert. Vor dem Projektstart die aktuelle Version prüfen und Experimentelles bewusst einsetzen.

---

## 5. Module im Detail

### 5.1 Heute (Dashboard)

**Ziel:** In einem Blick sehen, wie der Tag läuft, und mit einem Tap loslegen.

Aufbau von oben nach unten:
1. **Begrüßung + Datum** (`headlineMedium`), Wochentagsleiste zum Wechseln des Tages.
2. **Ernährungskarte:** Kalorienring (verbleibend), drei Makro-Balken, Button „Mahlzeit loggen“.
3. **Sport-Karte:** Nächste geplante Einheit (egal ob Gym, Calisthenics oder Laufen) oder Zusammenfassung der heutigen Session.
4. **Journal-Karte:** „Wie war dein Tag?“ bzw. Vorschau des heutigen Eintrags, Stimmung.
5. **Traumkarte (morgens bis 11 Uhr prominent):** „Hast du heute geträumt?“ mit Spracheingabe.
6. **Streaks:** Tagebuch-, Tracking-, Trainingsserie.

Karten sind per Drag & Drop sortierbar und ausblendbar.

### 5.2 Sport (Gym, Calisthenics, Laufen)

#### 5.2.0 Der Disziplin-Slider

Tippt man in der Floating Tab Bar auf **Sport**, erscheint oben unter der Top App Bar ein Slider mit drei Disziplinen. Die Auswahl tauscht den kompletten Inhalt darunter aus: jeweils den passenden Trainingsplan, die Sessions und den Fortschritt.

```
┌──────────────────────────────────────┐
│ Sport                        🔍  ⋮   │  TopAppBar
│ ┌──────────┬──────────────┬────────┐ │
│ │ ◉ Gym    │ Calisthenics │ Laufen │ │  Disziplin-Slider
│ └──────────┴──────────────┴────────┘ │
├──────────────────────────────────────┤
│  Trainingsplan der gewählten         │
│  Disziplin (wischbar)                │  HorizontalPager
│  ...                                 │
└──────────────────────────────────────┘
```

**Komponentenwahl (M3-korrekt):**

| Variante | Komponente | Wann sinnvoll |
|---|---|---|
| **Empfohlen** | `PrimaryTabRow` + `HorizontalPager` | Die drei Disziplinen sind gleichrangige Inhaltsbereiche. Genau dafür sind M3-Tabs gedacht, inkl. Wischgeste zwischen den Seiten. Der Indikator gleitet beim Wischen mit. |
| Alternative | `SingleChoiceSegmentedButtonRow` | Wenn der Slider optisch identisch zu Journal und Ernährung sein soll. Segmented Buttons sind laut M3 aber eher für Filter und Ansichtsoptionen gedacht, Wischen muss man selbst ergänzen. |

> **Konsistenz-Tipp:** Wenn du Tabs + Pager für Sport nimmst, stell Journal (Notizen/Tagebuch/Träume) und Ernährung (Tracker/Rezepte) gleich mit um. Ein einheitliches Muster für „Unterbereiche eines Tabs“ fühlt sich deutlich hochwertiger an.

**Verhalten:**
- Die zuletzt gewählte Disziplin wird gespeichert (DataStore) und beim nächsten Öffnen wiederhergestellt.
- Disziplinen lassen sich in den Einstellungen ausblenden. Bleibt nur eine übrig, verschwindet der Slider.
- Der Slider bleibt beim Scrollen sichtbar (pinned unter der collapsing Top App Bar).
- Jede Disziplin-Seite hat den gleichen Aufbau: **Heute geplant** → **Trainingsplan** → **Letzte Sessions** → **Fortschritt**. Wer eine Seite versteht, versteht alle drei.
- Ein disziplinübergreifender **Wochenkalender** (Icon in der Top App Bar) zeigt alle Einheiten farbcodiert, damit man z. B. Beintag und langen Lauf nicht auf denselben Tag legt.

#### 5.2.1 Gym

**Funktionen**
- **Übungsbibliothek:** Name, Muskelgruppen (primär/sekundär), Equipment, Typ (Kraft, Cardio, Zeit, Körpergewicht), Notizen, optional Bild. Vorgefüllte Basisbibliothek plus eigene Übungen.
- **Pläne/Routinen:** Geordnete Übungslisten mit Zielsätzen, Zielwiederholungen, Pausenzeit, Supersätze. Pläne gruppierbar in Splits (z. B. Push/Pull/Legs) und Mesozyklen.
- **Aktive Session (Kern-Screen):**
  - Vollbild, Tab-Bar ausgeblendet, Timer oben.
  - Pro Übung eine Karte mit Satztabelle: `Satz | Vorher | kg | Wdh | RPE | ✓`.
  - Spalte „Vorher“ zeigt die Werte der letzten Session (Progressive Overload auf einen Blick).
  - Abhaken startet den **Rest-Timer** automatisch. Timer läuft als Foreground Service mit Live-Notification, damit er auch bei gesperrtem Display funktioniert.
  - Satztypen: Aufwärmen, Arbeitssatz, Drop-Set, Failure.
  - Plate Calculator im Bottom Sheet.
- **Fortschritt:**
  - Geschätzter 1RM nach Epley: `1RM = Gewicht × (1 + Wdh / 30)`, alternativ Brzycki. Formel in den Einstellungen wählbar.
  - Volumen pro Muskelgruppe und Woche (Sätze bzw. `Σ Gewicht × Wdh`).
  - PR-Erkennung (Gewicht, Wiederholungen, 1RM, Volumen) mit Badge.
  - Körpergewicht und Maße als Zeitreihe.
- **Integration:** Health Connect (Workouts und Gewicht schreiben/lesen).

**Screen: Gym-Session (Wireframe)**
```
┌──────────────────────────────────────┐
│ ←  Push Day           00:42:13   ✓   │  TopAppBar
├──────────────────────────────────────┤
│ Bankdrücken                      ⋮   │  ElevatedCard
│ Satz  Vorher     kg    Wdh  RPE  ✓   │
│  W    40×10      40    10    -   ☑   │
│  1    80×8       82.5  8     8   ☑   │
│  2    80×8       82.5  [ ]  [ ]  ☐   │
│ + Satz hinzufügen                    │
├──────────────────────────────────────┤
│ ████████░░░░  Pause 1:12 / 2:00      │  Rest-Timer (Wavy Progress)
└──────────────────────────────────────┘
```

#### 5.2.2 Calisthenics

Calisthenics tickt anders als Gym: Fortschritt entsteht nicht über mehr Gewicht, sondern über **schwerere Varianten derselben Bewegung** und über **Haltezeiten**. Das Datenmodell muss das abbilden.

**Funktionen**
- **Skill-Tree:** Jeder Skill (z. B. Handstand, Front Lever, Planche, Muscle-up, Pistol Squat, One-Arm Push-up) ist eine geordnete Kette von Progressionsstufen, etwa `Tuck → Advanced Tuck → One Leg → Straddle → Full`. Darstellung als vertikale Stufenleiter mit Status je Stufe: gesperrt, in Arbeit, gemeistert.
- **Aufstiegskriterien pro Stufe:** z. B. „3 × 10 s sauber gehalten“ oder „3 × 8 Wdh“. Sind sie in zwei Sessions erfüllt, schlägt die App den Aufstieg vor (Bestätigung durch dich, nicht automatisch).
- **Trainingsplan:** Aufgeteilt in **Skill-Block** (frisch am Anfang, kurze Holds, viele Pausen) und **Kraft-Block** (Pull, Push, Beine, Core). Formate: klassische Sätze, Zirkel, EMOM, AMRAP.
- **Übungstypen:** Wiederholungen, Haltezeit (isometrisch), Negativ (exzentrische Dauer), jeweils optional mit **Zusatzgewicht** (Weste, Gürtel) oder **Unterstützung** (Bandstärke als Negativwert).
- **Aktive Session:**
  - Für Holds ein großer **Hold-Timer** mit Countdown, Start per Tap oder Lautstärketaste, akustisches Signal am Ende.
  - Zirkel- und EMOM-Modus mit Runden- und Intervalltimer.
  - Pro Satz schnell die Qualität markieren (sauber / mit Fehlern), weil Form bei Skills wichtiger ist als die reine Zahl.
- **Fortschritt:** längste Hold-Zeit je Stufe, maximale saubere Wiederholungen, Zeitstrahl der Stufenaufstiege („Tuck Front Lever gemeistert am …“), Wochenvolumen nach Bewegungsmuster (Pull, Push, Legs, Core).
- **Formvideos (optional, v2):** Kurzes Video pro Versuch aufnehmen und lokal an die Session hängen, um die Technik über Wochen zu vergleichen.

**Screen: Calisthenics-Seite (Wireframe)**
```
┌──────────────────────────────────────┐
│ Heute: Skill + Pull                  │  Karte „Heute geplant“
│ [ Session starten ]                  │
├──────────────────────────────────────┤
│ Front Lever                          │  Skill-Karte
│  ✓ Tuck           15 s               │
│  ◐ Advanced Tuck   8 s / Ziel 3×10 s │
│  🔒 One Leg                          │
│  🔒 Straddle                         │
│  🔒 Full                             │
├──────────────────────────────────────┤
│ Handstand   ◐ Wall 45 s   →          │
│ Muscle-up   ◐ Banded (rot) →         │
└──────────────────────────────────────┘
```

#### 5.2.3 Laufen

**Funktionen**
- **Trainingsplan:**
  - Plan-Vorlagen nach Ziel: 5 km, 10 km, Halbmarathon, Marathon, oder „einfach fitter werden“. Eingabe: Zieldatum, aktuelle Form (z. B. letzte 5-km-Zeit), Laufeinheiten pro Woche.
  - Wochenstruktur mit Einheitstypen: **Easy Run, Long Run, Tempo, Intervalle, Recovery, Ruhetag**.
  - Zielpaces werden aus einer aktuellen Wettkampf- oder Testzeit abgeleitet (z. B. nach dem VDOT-Ansatz von Jack Daniels oder einfacher über prozentuale Pace-Zonen). Die Methode in den Einstellungen wählbar machen.
  - Umfangssteigerung moderat halten und Entlastungswochen einplanen. Die App warnt, wenn der Wochenumfang sprunghaft steigt (Faustregel, keine harte Grenze).
- **Aktiver Lauf:**
  - GPS-Tracking über den Fused Location Provider in einem **Foreground Service** (Typ `location`), damit der Lauf bei gesperrtem Display weiterläuft. Live-Notification mit Zeit, Distanz, Pace.
  - Anzeige: Dauer, Distanz, aktuelle und durchschnittliche Pace, optional Herzfrequenz.
  - **Geführte Intervalle:** Die App kündigt Phasen per Sprachausgabe (TextToSpeech) und Vibration an („Noch 400 m in 4:30er Pace“).
  - Auto-Pause bei Stillstand, Kilometer-Ansagen.
  - Lauf manuell nachtragen (z. B. Laufband) ohne GPS.
- **Lauf-Detail:** Karte mit Route (Maps Compose oder osmdroid/MapLibre für eine Lösung ohne Google-Abhängigkeit), Splits pro Kilometer, Pace- und Höhenprofil, Herzfrequenzzonen.
- **Fortschritt:** Wochen- und Monatsumfang, Bestzeiten über Standarddistanzen (automatisch aus den schnellsten Teilstücken erkannt), Pace-Entwicklung bei Easy Runs als Formindikator.
- **Integration:** Health Connect (Läufe und Herzfrequenz lesen/schreiben), optional GPX-Import und -Export.

**Screen: Aktiver Lauf (Wireframe)**
```
┌──────────────────────────────────────┐
│ Intervalle 6 × 800 m        ⏸   ■   │
├──────────────────────────────────────┤
│              5,42 km                 │  displayLarge
│   Pace 4:28 /km     Ø 5:02 /km       │
│   Zeit 27:18        HF 168           │
├──────────────────────────────────────┤
│ Intervall 4/6   ██████░░░  520/800 m │
│ Nächste Phase: 400 m Trabpause       │
└──────────────────────────────────────┘
```

**Berechtigungen fürs Laufen:** `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`. Standortzugriff erst beim ersten Lauf anfragen, mit kurzer Begründung vorab (Rationale-Dialog), nicht beim App-Start.

### 5.3 Journal (Notizen, Tagebuch, Traumtagebuch)

Drei Segmente, **ein gemeinsames Datenmodell** (`Entry` mit `type`). Das macht Suche, Tags, Export und Verknüpfungen trivial.

#### Notizen
- Freitext mit **Markdown** (Überschriften, Listen, Checklisten, Code), Live-Rendering im Editor.
- Pins, Farben (aus der Tonpalette), Tags, Ordner optional.
- Ansichten: Staggered Grid oder Liste.
- Checklisten-Notizen mit abhakbaren Items.
- Anhänge: Bilder, Sprachmemos.
- **Verlinkung:** `[[Notiztitel]]`-Syntax für Querverweise (inkl. Backlinks im Detail-Screen).

#### Tagebuch
- **Ein Eintrag pro Tag** als Standard (mehrere erlaubt).
- Stimmung auf einer 5er-Skala (Emoji oder Icons), optional Energie und Schlafqualität.
- **Prompts** (rotierend oder eigene): „Wofür bist du heute dankbar?“, „Was hat dich heute gefordert?“
- Kalenderansicht mit Stimmungsfarbe pro Tag (Heatmap).
- „An diesem Tag“: Einträge von vor einem Jahr.
- Automatische Kontextinfos optional: Training des Tages, Kalorien, Wetter.

#### Traumtagebuch
- **Morgen-Capture:** Erinnerung nach dem Aufwachen, Eingabe per Sprache (`SpeechRecognizer`) oder Text, bewusst minimalistische, dunkle UI.
- Felder: Titel, Beschreibung, Klarheit (1 bis 5), Emotionen (Chips), **luzid ja/nein**, Albtraum ja/nein, wiederkehrend ja/nein.
- **Traumsymbole** als eigener Tag-Typ (Personen, Orte, Objekte), mit Häufigkeitsstatistik.
- Muster-Ansicht: häufigste Symbole, Anteil luzider Träume über Zeit, Korrelation mit Schlafdaten (falls aus Health Connect vorhanden).
- Optional: Reality-Check-Erinnerungen tagsüber für Luzidtraum-Training.

### 5.4 Ernährung (Kalorientracker und Rezepte)

#### Tracker
- **Tagesansicht:** großer Kalorienring (Ziel, gegessen, verbleibend), drei Makro-Ringe oder -Balken.
- Mahlzeiten-Sektionen: Frühstück, Mittag, Abend, Snacks (umbenennbar).
- **Lebensmittel hinzufügen:**
  - Suche (lokal zuerst, dann online)
  - **Barcode-Scan** mit CameraX + ML Kit Barcode Scanning, Lookup über die **Open Food Facts API**
  - Favoriten, Zuletzt verwendet, „Gestern kopieren“
  - Schnelleintrag (nur kcal und Makros)
  - Rezept als Portion loggen
- Portionseingabe im Bottom Sheet: Menge + Einheit (g, ml, Stück, Portion), Live-Vorschau der Nährwerte.
- **Ziele:** manuell oder berechnet (Grundumsatz nach Mifflin-St Jeor × Aktivitätsfaktor), Makroverteilung in % oder g/kg Körpergewicht.
- Wasser-Tracker als optionale Karte.
- Wochen- und Monatsauswertung, Durchschnitte, Zielerreichung.

#### Rezepte
- Titel, Foto, Portionen, Zeit, Tags (z. B. „High Protein“, „Meal Prep“), Zutaten, Schritte.
- **Zutaten sind Lebensmittel-Referenzen** → Nährwerte werden automatisch berechnet (pro Portion und gesamt).
- **Portionen skalieren** mit Stepper, alle Mengen passen sich an.
- **Kochmodus:** Bildschirm bleibt an, ein Schritt pro Seite, große Typo, Timer direkt aus dem Schritt startbar.
- „Als Mahlzeit loggen“ mit Portionsauswahl.
- Import: Text einfügen und parsen (v2), Einkaufsliste aus Rezepten generieren (landet als Checklisten-Notiz im Journal).

#### Profi-Feature für später: Makro-Optimierer
Klassisches **Diet Problem** (Stigler lässt grüßen): Aus deinen Rezepten und Favoriten einen Tagesplan bestimmen, der die Makroziele möglichst genau trifft.

- Entscheidungsvariablen: `x_i ∈ ℤ≥0` Portionen von Rezept/Lebensmittel *i*
- Nebenbedingungen: Kalorien und Makros innerhalb eines Korridors, max. Portionen pro Gericht, optional Mahlzeitenzuordnung
- Zielfunktion: Minimierung der gewichteten absoluten Abweichung von den Zielwerten (linearisiert über Hilfsvariablen), optional plus Vielfalt-Strafterm
- On-Device lösbar, z. B. mit **OR-Tools** (CP-SAT hat eine Java-API), bei der kleinen Instanzgröße im Millisekundenbereich.

### 5.5 Einstellungen

| Gruppe | Inhalte |
|---|---|
| **Darstellung** | Theme (System/Hell/Dunkel), Dynamic Color an/aus, Seed-Farbe, AMOLED-Modus, **Transparenz der Tab Bar (Glas / deckend)**, Schriftgröße im Editor, Serif für Journal |
| **Module** | Module an/aus, Reihenfolge der Tabs, Startbildschirm |
| **Profil & Ziele** | Größe, Gewicht, Alter, Aktivitätslevel, Kalorien- und Makroziele, Trainingsziele |
| **Einheiten** | kg/lb, kcal/kJ, metrisch/imperial, 1RM-Formel |
| **Erinnerungen** | Tagebuch abends, Traum morgens, Mahlzeiten, Training, Reality Checks |
| **Sicherheit** | App-Lock (BiometricPrompt, PIN-Fallback), separates Lock nur für Journal, Screenshots im Journal blockieren (`FLAG_SECURE`) |
| **Daten** | Lokales Backup (verschlüsselte ZIP), automatisches Backup in einen gewählten Ordner (Storage Access Framework), Export als JSON/CSV/Markdown, Import, Health Connect |
| **Über** | Version, Lizenzen, Datenschutzhinweis |

Umsetzung als eigene M3-Preference-Composables (Listen mit `ListItem`, `Switch`, Dialogen), da die klassische `androidx.preference` nicht Compose-nativ ist.

---

## 6. Globale Querschnittsfunktionen

- **Globale Suche** über Notizen, Tagebuch, Träume, Rezepte, Übungen: Room **FTS4**-Tabellen, Ergebnisse gruppiert nach Typ. Einstieg über Suchicon in jeder Top App Bar.
- **Tags** modulübergreifend (Tag „Urlaub“ findet Tagebucheinträge, Notizen und Rezepte).
- **Verknüpfungen:** Tagebucheintrag kann auf das Workout des Tages und die Kalorienbilanz verweisen.
- **Widgets** mit Jetpack Glance: Kalorien heute, Quick Capture, Rest-Timer, „Traum notieren“.
- **App Shortcuts** (Long Press aufs App-Icon): Neue Notiz, Workout starten, Mahlzeit loggen, Traum notieren.
- **Share-Target:** Text und Links aus anderen Apps als Notiz speichern, Rezept-URLs als Rezeptentwurf.
- **Barrierefreiheit:** Content Descriptions, Mindestkontrast (M3-Rollen erfüllen das weitgehend), TalkBack-Test, dynamische Schriftgrößen.

---

## 7. Technische Architektur

### 7.1 Stack

| Bereich | Wahl |
|---|---|
| Sprache | Kotlin |
| UI | Jetpack Compose + Material 3 (`androidx.compose.material3`, Expressive-Komponenten sind teils noch experimentell, Version beim Start prüfen) |
| Architektur | MVVM mit Unidirectional Data Flow (State als `StateFlow`, Events als sealed Interfaces) |
| DI | Hilt |
| Navigation | Navigation Compose mit Type-Safe Routes (oder Navigation 3, falls stabil) |
| Datenbank | Room + FTS4, optional SQLCipher für Verschlüsselung |
| Einstellungen | DataStore (Preferences oder Proto) |
| Hintergrund | WorkManager (Erinnerungen, Backups), Foreground Service (Rest-Timer) |
| Kamera/Barcode | CameraX + ML Kit Barcode Scanning |
| Netzwerk | Retrofit oder Ktor Client + kotlinx.serialization (Open Food Facts) |
| Charts | Vico (Compose-nativ) |
| Blur / Glaseffekt | Haze (`dev.chrisbanes.haze`) für die transparente Tab Bar |
| Bilder | Coil |
| Widgets | Jetpack Glance |
| Gesundheit | Health Connect |
| Tests | JUnit, Turbine (Flows), Compose UI Tests, Room In-Memory-Tests |

### 7.2 Modulstruktur (Gradle)

```
:app                      → MainActivity, Scaffold, Floating Tab Bar, NavHost
:core:designsystem        → Theme, Farben, Typo, Shapes, gemeinsame Composables
:core:database            → Room DB, DAOs, Entities, Migrationen
:core:data                → Repositories
:core:datastore           → Settings
:core:common              → Utils, Dispatchers, Result-Typen
:feature:today
:feature:sport            → SportScreen, Slider, Wochenkalender
:feature:sport:gym
:feature:sport:calisthenics
:feature:sport:running    → inkl. Location-Foreground-Service
:feature:journal
:feature:nutrition
:feature:settings
```

Vorteil: Features sind entkoppelt, Build-Zeiten sinken, und ein Modul kann deaktiviert werden, ohne dass andere es merken.

### 7.3 Datenmodell (Room, vereinfacht)

**Journal**
```
Entry(id, type[NOTE|DIARY|DREAM], title, body, createdAt, updatedAt,
      entryDate, pinned, color, archived)
DiaryMeta(entryId, mood, energy, sleepQuality)
DreamMeta(entryId, clarity, lucid, nightmare, recurring)
Tag(id, name, kind[GENERAL|DREAM_SYMBOL])
EntryTag(entryId, tagId)
Attachment(id, entryId, uri, mimeType)
EntryLink(fromId, toId)
EntryFts(title, body)  → FTS4, contentEntity = Entry
```

**Sport (gemeinsame Basis)**
```
Discipline = GYM | CALISTHENICS | RUNNING   (Enum)
TrainingPlan(id, discipline, name, goal?, startDate?, endDate?, active)
PlannedWorkout(id, planId, discipline, date?, weekIndex?, dayIndex?, title, order)
WorkoutSession(id, discipline, plannedWorkoutId?, startedAt, endedAt, notes, perceivedEffort?)
BodyMetric(id, date, weight, bodyFat?, measurementsJson?)
```
Der Wochenkalender und die Sport-Karte auf „Heute“ arbeiten nur mit `PlannedWorkout` und `WorkoutSession`, also disziplinunabhängig. Die Details hängen in disziplinspezifischen Tabellen.

**Gym**
```
Exercise(id, name, discipline, primaryMuscles, secondaryMuscles, equipment,
         measureType[REPS|HOLD|NEGATIVE|DURATION], notes, custom)
RoutineExercise(plannedWorkoutId, exerciseId, order, targetSets, targetReps,
                restSec, supersetGroup?)
SessionExercise(id, sessionId, exerciseId, order)
SetEntry(id, sessionExerciseId, index, type, weight, reps, rpe, durationSec, completed)
```

**Calisthenics** (nutzt `Exercise`, `SessionExercise` und `SetEntry` mit)
```
Skill(id, name, category[PULL|PUSH|LEGS|CORE|BALANCE])
SkillStep(id, skillId, order, exerciseId, criterionType[HOLD|REPS],
          criterionSets, criterionValue)
SkillProgress(skillId, currentStepId, updatedAt)
SkillStepAchievement(stepId, achievedAt)
SetEntry-Erweiterung: holdSec?, addedWeight?, assistance?, formQuality?
```

**Laufen**
```
RunPlanWorkout(plannedWorkoutId, runType[EASY|LONG|TEMPO|INTERVAL|RECOVERY],
               targetDistanceM?, targetDurationSec?, targetPaceSecPerKm?,
               intervalsJson?)
RunSession(sessionId, distanceM, durationSec, avgPaceSecPerKm, avgHr?,
           elevationGainM?, source[GPS|MANUAL|HEALTH_CONNECT])
RunTrackPoint(sessionId, timestamp, lat, lon, altitude?, hr?)
RunSplit(sessionId, index, distanceM, durationSec)
PersonalBest(distanceM, durationSec, sessionId, achievedAt)
```

> **Performance-Hinweis:** `RunTrackPoint` wächst schnell (ein Punkt pro Sekunde ergibt über 3.600 Zeilen pro Stunde). Während des Laufs in Batches schreiben, Index auf `sessionId`, und für die Listenansicht nur die aggregierten Werte aus `RunSession` laden.

**Ernährung**
```
Food(id, name, brand, barcode, kcalPer100, proteinPer100, carbsPer100, fatPer100,
     fiberPer100?, servingSizeG?, source[USER|OFF], favorite)
Recipe(id, title, servings, prepMin, cookMin, imageUri, instructionsJson)
RecipeIngredient(recipeId, foodId, amountG, displayUnit)
FoodLog(id, date, mealType, foodId?, recipeId?, amountG, portions?, kcal, protein, carbs, fat)
DailyGoal(dateFrom, kcal, protein, carbs, fat)
```

> **Wichtiger Punkt:** `FoodLog` speichert die Nährwerte **denormalisiert** zum Zeitpunkt des Loggens. Ändert sich später ein Lebensmittel oder Rezept, bleiben historische Tage korrekt.

### 7.4 Navigation

```kotlin
@Serializable sealed interface TopLevel {
    @Serializable data object Today : TopLevel
    @Serializable data class Sport(val discipline: Discipline? = null) : TopLevel
    // null = zuletzt gewählte Disziplin aus DataStore
    @Serializable data object Journal : TopLevel
    @Serializable data object Nutrition : TopLevel
    @Serializable data object Settings : TopLevel
}

// Unterziele, Beispiele
@Serializable data class EntryEditor(val entryId: Long?, val type: EntryType)
@Serializable data class ActiveGymSession(val sessionId: Long)
@Serializable data class ActiveCalisthenicsSession(val sessionId: Long)
@Serializable data class ActiveRun(val sessionId: Long)
@Serializable data class SkillDetail(val skillId: Long)
@Serializable data class RunDetail(val sessionId: Long)
@Serializable data class RecipeDetail(val recipeId: Long)
@Serializable data class CookingMode(val recipeId: Long, val servings: Int)
```

Die Tab Bar wird eingeblendet, wenn die aktuelle Destination eine `TopLevel`-Route ist, sonst ausgeblendet (animiert).

Der Disziplin-Slider ist **keine** eigene Navigationsebene, sondern UI-State innerhalb von `Sport` (Pager-Index im `SportViewModel`, gespiegelt in DataStore). So bleibt der Back Stack flach: Zurück aus dem Sport-Tab führt nicht erst durch alle Disziplinen. Der optionale `discipline`-Parameter erlaubt trotzdem Deep Links, z. B. vom Widget direkt in „Laufen“.

```kotlin
@Composable
fun SportScreen(vm: SportViewModel = hiltViewModel()) {
    val disciplines by vm.enabledDisciplines.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(
        initialPage = vm.initialPage,
        pageCount = { disciplines.size },
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(vm::onDisciplineSelected)
    }

    Column {
        if (disciplines.size > 1) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                disciplines.forEachIndexed { index, d ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(d.label) },
                        icon = { Icon(d.icon, contentDescription = null) },
                    )
                }
            }
        }
        HorizontalPager(state = pagerState, beyondViewportPageCount = 1) { page ->
            when (disciplines[page]) {
                Discipline.GYM -> GymPage()
                Discipline.CALISTHENICS -> CalisthenicsPage()
                Discipline.RUNNING -> RunningPage()
            }
        }
    }
}
```

### 7.5 Skelett der Floating Tab Bar (Compose)

Vollständigere Fassung mit Transparenz siehe Abschnitt 3.2. Hier die Struktur ohne Blur-Details:

```kotlin
@Composable
fun FloatingTabBar(
    items: List<TabItem>,
    selected: TopLevel,
    onSelect: (TopLevel) -> Unit,
    onFabClick: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = barAlpha),
                tonalElevation = 3.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                ),
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { item ->
                        TabPill(
                            item = item,
                            selected = item.route == selected,
                            onClick = { onSelect(item.route) },
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            FloatingActionButton(onClick = onFabClick) {
                Icon(Icons.Rounded.Add, contentDescription = "Neuer Eintrag")
            }
        }
    }
}
```

`TabPill` animiert Breite und Hintergrund (`animateColorAsState`, `animateContentSize` mit Spring) und zeigt das Label nur im aktiven Zustand.

---

## 8. Roadmap

| Phase | Umfang | Ziel |
|---|---|---|
| **MVP (v0.1)** | Scaffold + transparente Floating Tab Bar (inkl. Blur und Fallback), Theme, Notizen, Tagebuch, Traumtagebuch (ohne Statistik), einfacher Kalorientracker mit manueller Eingabe, Einstellungen (Theme, Module) | Täglich selbst nutzbar |
| **v0.2** ✅ | Sport-Tab mit Disziplin-Slider, Gym komplett (Bibliothek, Routinen, Session, Rest-Timer), Fortschritts-Charts | Training ersetzt bisherige App |
| **v0.2.5** 🟡 | Calisthenics vollständig ✅ (Skill-Tree mit DB-Tabellen, Aufstiegskriterien inkl. Hold-Timer-Session, Zirkel-/EMOM-Modi, AMRAP folgt später) — Wochenkalender ✅ — Laufen: DB-Tabellen (RunPlanWorkout, RunPlanDetail, RunSession, RunTrackPoint, RunSplit, PersonalBest) + Trainingsplan-Struktur mit Zielpaces (VDOT/Prozent, in den Einstellungen wählbar) ✅ — offen: GPS-Tracking (Foreground-Service), Splits/Karte, geführte Intervalle, AMRAP | Alle drei Sportarten in einer App |
| **v0.3** | Barcode + Open Food Facts, Rezepte mit Nährwertberechnung, Kochmodus | Ernährung vollständig |
| **v0.4** | Globale Suche (FTS), Tags modulübergreifend, Backup/Export, App-Lock | Datensicherheit |
| **v1.0** | Dashboard „Heute“, Widgets, Shortcuts, Health Connect, Traum-Statistiken, Tablet-Layout | Rundes Produkt |
| **v2** | Makro-Optimierer (MILP), Rezept-Import per Text, Einkaufsliste, verschlüsselter Cloud-Sync | Nice to have |

---

## 9. Offene Entscheidungen

1. **Heute-Tab oder nicht?** Fünf Tabs sind das M3-Maximum. Mit Heute und Einstellungen ist die Bar voll, neue Module haben dann keinen Platz mehr.
2. **Verschlüsselung:** SQLCipher für die ganze DB (einfach, kleiner Performance-Overhead) oder nur Journal-Felder verschlüsseln (komplexer, aber Suche über verschlüsselte Felder wird schwierig).
3. **Sync:** Rein lokal mit Backup-Datei oder später eigener Sync (z. B. über Supabase oder einen eigenen Server)? Beeinflusst die IDs: Für späteren Sync von Anfang an **UUIDs** statt Auto-Increment-Longs verwenden.
4. **Markdown-Editor:** Eigene Implementierung mit `BasicTextField` + VisualTransformation oder eine Bibliothek? Eigenbau ist aufwendiger, aber kontrollierbarer.
5. **Karten fürs Laufen:** Google Maps (Maps Compose, API-Key, Google-Abhängigkeit) oder MapLibre/osmdroid mit OpenStreetMap (frei, passt besser zum Privacy-Ansatz).
6. **Lauf-Pacemethode:** VDOT-Tabellen (präzise, aber Lizenz- und Umsetzungsaufwand prüfen) oder eigene prozentuale Zonen relativ zur 5-km-Pace (einfach, transparent).
7. **Blur-Strategie:** Haze als Abhängigkeit aufnehmen (komfortabel, aber eine Library mehr) oder eigenes `RenderEffect`-Backdrop ab API 31 mit Fallback auf hohe Deckkraft darunter?
8. **minSdk:** Vorschlag API 26 (Android 8), Dynamic Color greift ab API 31 automatisch.
