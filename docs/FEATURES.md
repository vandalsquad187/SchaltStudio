# Features

## Anders als andere

SchaltStudio ist **kein Bild-Editor** und **kein bloßer Plan-Betrachter**: jedes
Bauteil hat eine simulierte Funktion – Leitungen führen Strom, Ventile lassen Druck
durch, Zylinder fahren aus. Die Simulation läuft in Echtzeit (40-ms-Tick) im
Bearbeitungsmodus direkt weiter.

## Kernfeature-ÜBERSICHT

| Bereich | Funktion | Status |
|---|---|---|
| Editor | Bauteile einfügen, verschieben, löschen, umbenennen | ✅ |
| Editor | Orthogonal am Raster verdrahten, T-Stöße per Ecke | ✅ |
| Editor | Zoom, Schieben, Auswahl mit gestrichelter Verbindungslinie | ✅ |
| Verknüpfen | Kontakt → Spule per Antippen (Ziele pulsieren blau) | ✅ |
| Simulation | Elektro: Kontakte, Spulen, Schütze, Selbsthaltung, Verriegelung | ✅ |
| Simulation | Motorschutzschalter (Bimetall): auslösen/zurückstellen per Tap | ✅ |
| Simulation | Zeitrelais (einschaltverzögert 0,5/1/2/5/10 s) | ✅ |
| Simulation | Drehstrom-Motor 3~ und Motor 3~ mit 6 Klemmen (Stern/Dreieck) | ✅ |
| Simulation | Pneumatik: 3/2-, 5/2-Ventile, Zylinder, Rollenhebel | ✅ |
| Simulation | Elektropneumatik: Magnetspule, Druckschalter | ✅ |
| Beispiele | 8 fertige Schaltungen zum Studieren | ✅ |
| Persistenz | Pläne als JSON speichern/laden | ✅ |
| Bedienung | Deutsche Oberfläche, komplett offline | ✅ |

## Vereinfachungen gegenüber FluidSIM (bewusst)

- Zylinder bewegen sich mit fester Geschwindigkeit, keine Drücke in bar
- Phasen L1/L2/L3 sind intern ein Netz (Dreieck-Verdrahtung didaktisch vereinfacht)
- Keine Attribute/Farben am Bauteil wie im Original
- Alte `.ct`-Dateien aus Festo FluidSIM sind binär und nicht importierbar
