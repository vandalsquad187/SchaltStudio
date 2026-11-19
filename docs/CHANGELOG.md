# Changelog

Alle nennenswerten Änderungen. Format nach
[Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

(noch nichts)

---

## [1.3] – 2026-11-12 (versionCode 4)

### Added
- **Motorschutzschalter F (Bimetall):** drei Pole 1‑2/3‑4/5‑6 + Hilfsöffner 95‑96 in
  einem Bauteil – schaltet Leistung UND Steuerstromkreis; im Simulationsbetrieb
  antippen = auslösen (latcht, „Störung"-Anzeige), nochmal antippen = zurückstellen
- **Motor 3~ mit 6 Klemmen:** U1/V1/W1 + U2/V2/W2 – läuft im Stern (Endklemmen
  gebrückt) oder im Dreieck (Endklemmen an den Phasen)
- **Beispiel „Stern-Dreieck-Anlauf":** F1 + Netzschiene K1 + Zeitrelais KT (2 s) +
  Stern- und Dreieckschütz mit Gegensprech-Verriegelung

### Fixed
- Stern-Erkennung in der Engine prüft jetzt die Kontakt-Netzwerk-Graphen statt
  reine Netz-IDs (die Sternschiene verbundet die Endklemmen erst über die
  K2-Hauptkontakte)
- Bimetall-Test-Layout: Haltekontakt wurde von einer Leitung umgangen (Kupfer-Short
  um das Bauteil)

---

## [1.2] – (versionCode 3)

### Added
- **Verknüpfen per Antippen:** Ziel einfach auf dem Plan antippen statt Liste
  durchscrollen – mögliche Ziele pulsieren blau, gestrichelte Verbindungslinie
- **Neues Betriebsmittel Schütz:** Spule A1/A2 + drei Hauptkontakte 1‑2/3‑4/5‑6 in
  EINEM Bauteil; Hilfskontakte verknüpfbar wie gewohnt
- Unverknüpfte Kontakte mahnen gelb pulsierend, bis sie verknüpft sind

### Changed
- Motor-Beispiel auf das Schütz-Bauteil umgebaut (S1, Selbsthaltung K1, Motor M)

---

## [1.1] – (versionCode 2)

### Added
- Betriebsmittel-Bezeichnungen an Bauteilen (Kontakte zeigen die Spulen-Bezeichnung)
- Neue Betriebsmittel: Zeitrelais, Druckschalter, 3/2-Rollenhebelventil,
  Drehstrom-Motor 3~
- Neue Beispiele: Folgeschaltung mit Rollenhebel, Drehstrom-Motor

---

## [1.0] – (versionCode 1)

### Added
- Erste Version: Schaltplan-Editor (Elektro + Pneumatik) mit Live-Simulation,
  Beispielen, Speichern/Laden
