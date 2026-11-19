# Architektur

SchaltStudio ist bewusst **ohne Gradle** und ohne fremde Laufzeitbibliotheken gebaut:
die Simulation ist reines Java und lässt sich ohne Android testen.

## Bausteine

```
java/de/schaltstudio/
├── core/
│   ├── Circuit.java     Bauteil-Modell, Ports, Leitungen, JSON-Persistenz
│   ├── Engine.java      Simulation: Netz-Solver + Pneumatik-Druckausbreitung
│   └── Examples.java    Vorgefertigte Beispiele
└── ui/
    ├── MainActivity.java  Editor-Oberfläche, Palette, Menüs, Speichern/Laden
    └── CircuitView.java   Zeichnung (Canvas), Auswahl, Verdrahten, Verknüpfen
```

## Netzmodell (Elektro)

- Jeder Netzknoten wird als `k('e', x, y)` bezeichnet – zusammengefasst über ein
  **Union-Find (DSU)** genau dann, wenn zwei Leitungen **identische Ecken (Vertices)**
  haben. Einfaches Überkreuzen verbindet nicht; ein T-Stoß braucht eine echte Ecke.
- Schienen: jeder Punkt mit `x = Schienenposition` gehört zum Schienennetz (L+, 0,
  L1/2/3).
- **Kontakte** bilden pro Tick Kanten im **Adjazenzgraphen** (Schließer leitet bei
  erregter Spule, Öffner umgekehrt; Zeitrelais-Kontakte folgen dem verzögerten
  Zustand). Die Simulation iteriert als **Fixpunkt** (max. 40 Iterationen pro Tick),
  bis sich nichts mehr ändert.
- **6-Klemmen-Motor:** läuft, wenn U1/V1/W1 unter Spannung sind UND (Stern: die
  Endklemmen sind im Kontakt-Graphen verbunden, oder Dreieck: alle drei Endklemmen
  unter Spannung).
- **Motorschutzschalter:** Pole 1‑2/3‑4/5‑6 + Hilfsöffner 95‑96 leiten, solange
  `tripped == false`; der Zustand latcht bis zum manuellen Zurücksetzen (Tap im
  Simulationsmodus) und wird in der JSON-Datei (`"tr"`) mitgeführt.

## Pneumatik

Druckausbreitung von der Versorgung über Ventile bis zu Zylindern; der
Rollenhebelventil-Port schaltet an einer verknüpften Zylinderposition automatisch
(Schwelle 25/50/75 %).

## Build-Pipeline (ohne Gradle)

`build.sh` führt aus:

1. `aapt2 compile/link` – Ressourcen + Manifest
2. `javac` – Java-Quellcode gegen `android.jar` (nur Compile-Classpath)
3. `d8` – DEX-Erzeugung
4. `zipalign` + `apksigner` – Ausrichten und Signieren (`keystore.jks`)

Tests laufen ohne Android: `test/EngineTest.java` (Simulation) und
`test/ExSanity.java` (Beispiel-Plausibilität) werden mit `javac` gegen
`build/classes` gebaut und auf der JVM ausgeführt.

## CI

`.github/workflows/ci.yml` baut das APK und führt beide Test-Suiten aus –
der Workflow installiert `platforms;android-36` und `build-tools;36.0.0` via
`sdkmanager` und nutzt dieselbe `build.sh` wie lokal.
