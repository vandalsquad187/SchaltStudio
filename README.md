# SchaltStudio

Schaltplan-Editor & Live-Simulation für Android – Elektro-Stromlaufpläne und Pneumatik, im Geist von Festo FluidSIM.

> Zeichne Stromlaufpläne und Pneumatik-Schaltpläne auf dem Handy und simuliere sie direkt
> mit – komplett offline, kein Konto, keine Cloud.

---

## Produktbeschreibung

**SchaltStudio** ist eine eigene Android-App im Geist von Festo FluidSIM-P 3.6: Schaltpläne
zeichnen (Elektro + Pneumatik) und live simulieren – auf dem Handy. Die Pläne liegen als
JSON-Dateien lokal auf dem Gerät.

Der Kern (Zeichnen + Simulation) ist komplett nutzbar. Zukunftsideen wie Zeitdiagramme
(Folgezeitplan) liegen als optionale Erweiterungen darüber.

## Kernfunktionen

- 📐 **Schaltpläne zeichnen** – Bauteile aus der Palette einfügen, orthogonal am Raster
  verdrahten, Verschieben per Drag, Zoom/Pinch
- ⚡ **Elektro-Simulation** – Stromlaufpläne mit Kontakten, Spulen, Schützen,
  Motorschutzschaltern, Zeitrelais, Drehstrommotoren (3~ und 6-Klemmen), Selbsthaltung,
  Gegensprech-Verriegelung, Stern-Dreieck-Anlauf
- 🎈 **Pneumatik-Simulation** – 3/2- und 5/2-Ventile, Zylinder (ein-/doppelwirkend),
  Rollenhebelventil, Magnetspule, Elektropneumatik-Kombinationen
- 🔗 **Verknüpfen per Antippen** – Kontakt einfach auf die passende Spule antippen
  (mögliche Ziele pulsieren blau)
- 🧪 **Simulationsbetrieb** – Taster gedrückt halten, Schalter antippen,
  Motorschutzschalter antippen = auslösen/zurückstellen
- 💾 **Beispiele** – fertige Schaltungen zum Studieren (Selbsthaltung, Motor mit Schütz,
  Stern-Dreieck-Anlauf, Folgeschaltung, Elektropneumatik)
- 💾 **Speichern/Laden** – Pläne als JSON auf dem Gerät

## Technologien

- **Java** + Android Views (Canvas-Rendering)
- eigene **Netz-Simulation** (Fixpunkt-Verfahren über Kontakte, 40-ms-Tick)
- **ohne Gradle** – manuelle Build-Pipeline: aapt2 + javac + d8 + apksigner
- JSON-Persistenz (org.json)
- Tests ohne Android: VM-Tests der Simulation (`EngineTest`, `ExSanity`)

## Architektur

```
SchaltStudio/
├── java/    Quellcode (de.schaltstudio.core = Modell/Engine,
│            de.schaltstudio.ui = Zeichnung/Editor)
├── res/     Layouts, Strings, Icons
├── test/    VM-Tests der Simulation
├── docs/    Dokumentation
└── build.sh APK-Build ohne Gradle (aapt2 + javac + d8 + apksigner)
```

Dokumentation: siehe [docs/](docs/).

## Roadmap

Siehe [docs/ROADMAP.md](docs/ROADMAP.md).

## Download

APK manuell: `SchaltStudio.apk` aufs Handy kopieren → installieren (Android 8.0+).
Neu bauen: `bash build.sh` → `dist/SchaltStudio.apk`

## Screenshots

*(werden ergänzt)*
