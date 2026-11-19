package de.schaltstudio.core;

import java.util.ArrayList;
import java.util.List;

/** Vorgefertigte Beispiele: Name + Schaltung programmatisch aufbauen. */
public class Examples {

    public static class Ex {
        public final String title;
        public final Circuit circuit;
        Ex(String t, Circuit c) { title = t; circuit = c; }
    }

    public static List<Ex> all() {
        List<Ex> l = new ArrayList<>();
        l.add(new Ex("Pneumatik: Zylinder ausfahren (3/2-Tasterventil)", exCylSingle()));
        l.add(new Ex("Pneumatik: Doppelwirkender Zylinder mit 5/2-Ventil", exCylDouble()));
        l.add(new Ex("Pneumatik: Positionsschaltung (bistabiles 5/2-Ventil)", exPosition()));
        l.add(new Ex("Pneumatik: Folgeschaltung mit Rollenhebel", exFolge()));
        l.add(new Ex("Elektro: Selbsthaltung mit Melder", exSelbsthaltung()));
        l.add(new Ex("Elektro: Drehstrom-Motor mit Schütz (3~)", exMotor()));
        l.add(new Ex("Elektro: Stern-Dreieck-Anlauf mit Motorschutzschalter", exSternDreieck()));
        l.add(new Ex("Elektropneumatik: Zylinder mit Selbsthaltung", exElektroPneu()));
        return l;
    }

    public static Circuit newEmpty() {
        Circuit c = new Circuit();
        c.addComp(Circuit.RAIL_L, 2, 4);
        c.addComp(Circuit.RAIL_R, 44, 4);
        return c;
    }

    // ------------------------------------------------------------------

    /** 3/2-Tasterventil + einfachwirkender Zylinder. */
    private static Circuit exCylSingle() {
        Circuit c = new Circuit();
        c.name = "Zylinder einfachwirkend";
        c.addComp(Circuit.SUPPLY, 3, 15);
        Circuit.Comp cv = c.addComp(Circuit.VALVE32, 8, 13);
        Circuit.Comp zy = c.addComp(Circuit.CYL_S, 16, 11);

        // Versorgung (5,16) -> Port 1 (8,14)
        c.addWire(new int[]{5, 16, 6, 16, 6, 14, 8, 14}, false);
        // Port 2 (10,14) -> Zylinder A (17,12)
        c.addWire(new int[]{10, 14, 14, 14, 14, 12, 17, 12}, false);
        cv.name = "1V1"; zy.name = "1A1";
        return c;
    }

    /** 5/2 monostabil, Tasterventil steuert Pilot 12. */
    private static Circuit exCylDouble() {
        Circuit c = new Circuit();
        c.name = "Zylinder doppelwirkend";
        Circuit.Comp sp = c.addComp(Circuit.SUPPLY, 2, 15);
        Circuit.Comp cv = c.addComp(Circuit.VALVE32, 4, 3);
        Circuit.Comp mv = c.addComp(Circuit.VALVE52M, 10, 6);
        Circuit.Comp zy = c.addComp(Circuit.CYL, 20, 10);

        // Versorgung (4,16) -> 1 des Hauptventils (12,7)
        c.addWire(new int[]{4, 16, 9, 16, 9, 9, 12, 9, 12, 7}, false);
        // Versorgung -> 1 des Tasterventils (4,4)
        c.addWire(new int[]{4, 16, 2, 16, 2, 4, 4, 4}, false);
        // Taster 2 (6,4) -> Pilot 12 (10,6)
        c.addWire(new int[]{6, 4, 8, 4, 8, 6, 10, 6}, false);
        // Hauptventil 2 (11,6) -> Zylinder A (21,11)
        c.addWire(new int[]{11, 6, 11, 4, 18, 4, 18, 11, 21, 11}, false);
        // Hauptventil 4 (13,6) -> Zylinder B (28,11)
        c.addWire(new int[]{13, 6, 13, 5, 24, 5, 24, 11, 28, 11}, false);
        sp.name = "0Z"; cv.name = "1S1"; mv.name = "1V1"; zy.name = "1A1";
        return c;
    }

    /** Bistabiles 5/2-Ventil, zwei 3/2-Tasterventile. */
    private static Circuit exPosition() {
        Circuit c = new Circuit();
        c.name = "Positionsschaltung";
        c.addComp(Circuit.SUPPLY, 2, 15);
        Circuit.Comp ta = c.addComp(Circuit.VALVE32, 4, 2);
        Circuit.Comp tb = c.addComp(Circuit.VALVE32, 30, 2);
        Circuit.Comp mv = c.addComp(Circuit.VALVE52B, 14, 6);
        Circuit.Comp zy = c.addComp(Circuit.CYL, 20, 10);

        // Versorgung (4,16) -> 1 (16,7)
        c.addWire(new int[]{4, 16, 16, 16, 16, 7}, false);
        // Versorgung -> Taster A 1 (4,3)
        c.addWire(new int[]{4, 16, 2, 16, 2, 3, 4, 3}, false);
        // Versorgung -> Taster B 1 (30,3)
        c.addWire(new int[]{4, 16, 36, 16, 36, 5, 32, 5, 32, 3}, false);
        // Taster A 2 (6,3) -> Pilot 12 (14,6)
        c.addWire(new int[]{6, 3, 8, 3, 8, 6, 14, 6}, false);
        // Taster B 2 (32,3) -> Pilot 14 (18,6)
        c.addWire(new int[]{32, 3, 34, 3, 34, 2, 20, 2, 20, 6, 18, 6}, false);
        // 2 (15,6) -> Zylinder A (21,11)
        c.addWire(new int[]{15, 6, 15, 4, 19, 4, 19, 11, 21, 11}, false);
        // 4 (17,6) -> Zylinder B (28,11)
        c.addWire(new int[]{17, 6, 17, 5, 26, 5, 26, 11, 28, 11}, false);
        ta.name = "1S1"; tb.name = "1S2"; mv.name = "1V1"; zy.name = "1A1";
        return c;
    }

    /** Folgeschaltung: Tasterventil Zylinder A, Rollenhebel am A-Zylinder startet Zylinder B. */
    private static Circuit exFolge() {
        Circuit c = new Circuit();
        c.name = "Folgeschaltung";
        c.addComp(Circuit.SUPPLY, 3, 15);
        Circuit.Comp vA = c.addComp(Circuit.VALVE32, 8, 13);
        Circuit.Comp zyA = c.addComp(Circuit.CYL_S, 20, 11);
        Circuit.Comp vr = c.addComp(Circuit.VALVE32R, 14, 13);
        Circuit.Comp mv = c.addComp(Circuit.VALVE52M, 12, 5);
        Circuit.Comp zyB = c.addComp(Circuit.CYL_S, 22, 3);

        c.addWire(new int[]{5, 16, 6, 16, 6, 14, 8, 14}, false);        // Versorgung -> Taster A
        c.addWire(new int[]{5, 16, 9, 16, 9, 7, 14, 7, 14, 6}, false);  // Versorgung -> 5/2-Ventil
        c.addWire(new int[]{10, 14, 18, 14, 18, 12, 21, 12}, false);    // 2A -> Zylinder A
        c.addWire(new int[]{10, 14, 10, 16, 14, 16, 14, 14}, false);    // 2A -> Rollenhebel (Abzweig)
        c.addWire(new int[]{16, 14, 17, 14, 17, 3, 12, 3, 12, 5}, false); // Rollenhebel -> Pilot 12
        c.addWire(new int[]{13, 5, 13, 4, 23, 4}, false);               // 2MV -> Zylinder B

        vA.name = "1S1"; zyA.name = "1A1"; vr.name = "1S2"; mv.name = "1V1"; zyB.name = "1A2";
        vr.link = zyA.id;
        vr.thr = 0.25f;
        return c;
    }

    /** Klassische Selbsthaltung mit Taster, eigene Wiedergabe-Kontakt und Melder. */
    private static Circuit exSelbsthaltung() {
        Circuit c = new Circuit();
        c.name = "Selbsthaltung";
        c.addComp(Circuit.RAIL_L, 1, 4);
        c.addComp(Circuit.RAIL_R, 30, 4);
        Circuit.Comp k1 = c.addComp(Circuit.COIL, 20, 10);
        Circuit.Comp s1 = c.addComp(Circuit.PUSHBTN, 6, 10);
        Circuit.Comp kk = c.addComp(Circuit.CONTACT, 6, 13);
        Circuit.Comp h1 = c.addComp(Circuit.LAMP, 20, 13);

        c.addWire(new int[]{1, 10, 6, 10});           // L -> S1
        c.addWire(new int[]{1, 13, 6, 13});           // L -> K1-Kontakt
        c.addWire(new int[]{9, 10, 12, 10});          // S1 -> Knoten
        c.addWire(new int[]{12, 10, 12, 13});         // Brücke runter
        c.addWire(new int[]{9, 13, 12, 13});          // K1-Kontakt -> Knoten
        c.addWire(new int[]{12, 10, 20, 10});         // Knoten -> Spule
        c.addWire(new int[]{12, 13, 20, 13});         // Knoten -> Melder
        c.addWire(new int[]{23, 10, 30, 10});         // Spule -> 0
        c.addWire(new int[]{23, 13, 30, 13});         // Melder -> 0
        kk.link = k1.id;
        kk.name = k1.name;
        h1.crane = false; // grün
        return c;
    }

    /** Drehstrom-Motor: L1/L2/L3 über Schütz K1, Steuerstromkreis mit Taster + Selbsthaltung. */
    private static Circuit exMotor() {
        Circuit c = new Circuit();
        c.name = "Drehstrom-Motor";
        c.addComp(Circuit.RAIL3, 1, 2);
        c.addComp(Circuit.RAIL_R, 34, 2);
        Circuit.Comp km = c.addComp(Circuit.CONTACTOR, 14, 8);
        Circuit.Comp motor = c.addComp(Circuit.MOTOR, 24, 10);
        Circuit.Comp s1 = c.addComp(Circuit.PUSHBTN, 6, 4);
        Circuit.Comp kk = c.addComp(Circuit.CONTACT, 6, 7);

        // Steuerstromkreis: Taster S1 || Hilfskontakt K1 -> Schützspule K1 (A1/A2)
        c.addWire(new int[]{1, 4, 6, 4});          // Schiene -> Taster S1
        c.addWire(new int[]{9, 4, 12, 4});
        c.addWire(new int[]{12, 4, 12, 7});        // hinunter zur Selbsthaltung
        c.addWire(new int[]{1, 7, 6, 7});          // Schiene -> Hilfskontakt K1
        c.addWire(new int[]{9, 7, 12, 7});
        c.addWire(new int[]{12, 7, 12, 8});
        c.addWire(new int[]{12, 8, 14, 8});        // -> A1
        c.addWire(new int[]{17, 8, 20, 8, 20, 2, 34, 2}); // A2 -> 0-Schiene
        // Leistungsteil: drei Phasen über die Schütz-Hauptkontakte zum Motor
        c.addWire(new int[]{1, 10, 14, 10});
        c.addWire(new int[]{17, 10, 24, 10});
        c.addWire(new int[]{1, 11, 14, 11});
        c.addWire(new int[]{17, 11, 24, 11});
        c.addWire(new int[]{1, 12, 14, 12});
        c.addWire(new int[]{17, 12, 24, 12});

        kk.link = km.id;      // Hilfskontakt gehört zum Schütz K1
        kk.name = km.name;
        return c;
    }

    /** Stern-Dreieck-Anlauf: Motorschutzschalter F1, Netzschiene K1, Zeitrelais KT,
     *  Sternschütz K2 und Dreieckschütz K3 mit Verriegelung, Motor mit 6 Klemmen. */
    private static Circuit exSternDreieck() {
        Circuit c = new Circuit();
        c.name = "Stern-Dreieck-Anlauf";
        c.addComp(Circuit.RAIL3, 1, 2);                        // 0
        c.addComp(Circuit.RAIL_R, 52, 2);                      // 1
        Circuit.Comp f1 = c.addComp(Circuit.BIMET, 5, 6);      // 2
        Circuit.Comp k1 = c.addComp(Circuit.CONTACTOR, 13, 4); // 3
        c.addComp(Circuit.MOTOR6, 37, 6);                      // 4
        Circuit.Comp k2 = c.addComp(Circuit.CONTACTOR, 45, 10);// 5
        Circuit.Comp k3 = c.addComp(Circuit.CONTACTOR, 45, 16);// 6
        Circuit.Comp kt = c.addComp(Circuit.TIMER, 26, 10);    // 7
        Circuit.Comp s1 = c.addComp(Circuit.PUSHBTN, 20, 10);  // 8
        Circuit.Comp k1h = c.addComp(Circuit.CONTACT, 20, 13); // 9  Selbsthaltung K1
        Circuit.Comp kt1 = c.addComp(Circuit.CONTACT_NC, 25, 14); // 10 Stern abmelden
        Circuit.Comp k3i = c.addComp(Circuit.CONTACT_NC, 29, 14); // 11 Verriegelung
        Circuit.Comp kt2 = c.addComp(Circuit.CONTACT, 33, 17); // 12 Dreieck anmelden
        Circuit.Comp k2i = c.addComp(Circuit.CONTACT_NC, 37, 17); // 13 Verriegelung

        // Leistung: Schiene -> F1 -> K1 -> Motor (U1/V1/W1)
        c.addWire(new int[]{1, 6, 5, 6});                      // 0
        c.addWire(new int[]{1, 7, 5, 7});                      // 1
        c.addWire(new int[]{1, 8, 5, 8});                      // 2
        c.addWire(new int[]{8, 6, 10, 6, 13, 6});              // 3  Abzweig für Dreieck
        c.addWire(new int[]{8, 7, 11, 7, 13, 7});              // 4
        c.addWire(new int[]{8, 8, 12, 8, 13, 8});              // 5
        c.addWire(new int[]{16, 6, 37, 6});                    // 6
        c.addWire(new int[]{16, 7, 37, 7});                    // 7
        c.addWire(new int[]{16, 8, 37, 8});                    // 8
        // Motor-Endklemmen U2/V2/W2 über Klemmenleiste zu Stern- und Dreieckkontakt
        c.addWire(new int[]{40, 6, 41, 6});                    // 9
        c.addWire(new int[]{41, 6, 41, 12});                   // 10
        c.addWire(new int[]{41, 12, 45, 12});                  // 11
        c.addWire(new int[]{41, 12, 41, 18, 45, 18});          // 12
        c.addWire(new int[]{40, 7, 42, 7});                    // 13
        c.addWire(new int[]{42, 7, 42, 13, 45, 13});           // 14
        c.addWire(new int[]{42, 13, 42, 19, 45, 19});          // 15
        c.addWire(new int[]{40, 8, 43, 8});                    // 16
        c.addWire(new int[]{43, 8, 43, 14, 45, 14});           // 17
        c.addWire(new int[]{43, 14, 43, 20, 45, 20});          // 18
        c.addWire(new int[]{48, 12, 48, 13, 48, 14});          // 19 Sternpunkt
        // Dreieck: Phasen (klassisch gekreuzt) -> K3
        c.addWire(new int[]{10, 6, 10, 22, 49, 22, 49, 20, 48, 20}); // 20
        c.addWire(new int[]{11, 7, 11, 23, 50, 23, 50, 18, 48, 18}); // 21
        c.addWire(new int[]{12, 8, 12, 24, 51, 24, 51, 19, 48, 19}); // 22
        // Steuerstromkreis: Schiene -> 95/96 -> Taster -> M
        c.addWire(new int[]{1, 4, 3, 4, 3, 10, 5, 10});        // 23
        c.addWire(new int[]{8, 10, 20, 10});                   // 24
        c.addWire(new int[]{20, 10, 20, 13});                  // 25 Selbsthaltung abzweigen
        c.addWire(new int[]{23, 13, 23, 10});                  // 26 -> M
        c.addWire(new int[]{23, 10, 24, 10, 24, 3, 13, 3, 13, 4}); // 27 M -> K1.A1
        c.addWire(new int[]{23, 10, 26, 10});                  // 28 M -> KT
        c.addWire(new int[]{29, 10, 30, 10, 30, 3, 52, 3});    // 29 KT.A2 -> 0
        c.addWire(new int[]{24, 10, 24, 12, 34, 12});          // 30 Sammelschiene M
        // Stern-Rung: KT-Öffner (verzögert) -> K3-Verriegelung -> K2
        c.addWire(new int[]{24, 12, 24, 14});                  // 31
        c.addWire(new int[]{24, 14, 25, 14});                  // 32
        c.addWire(new int[]{28, 14, 29, 14});                  // 33
        c.addWire(new int[]{32, 14, 33, 14, 33, 10, 45, 10});  // 34 -> K2.A1
        c.addWire(new int[]{48, 10, 49, 10, 49, 4, 52, 4});    // 35 K2.A2 -> 0
        // Dreieck-Rung: KT-Schließer (verzögert) -> K2-Verriegelung -> K3
        c.addWire(new int[]{34, 12, 34, 17, 33, 17});          // 36
        c.addWire(new int[]{36, 17, 37, 17});                  // 37
        c.addWire(new int[]{40, 17, 44, 17, 44, 16, 45, 16});  // 38 -> K3.A1
        c.addWire(new int[]{48, 16, 52, 16, 52, 3});           // 39 K3.A2 -> 0
        c.addWire(new int[]{16, 4, 16, 2, 52, 2});             // 40 K1.A2 -> 0

        k1h.link = k1.id; k1h.name = k1.name;
        kt1.link = kt.id; kt1.name = kt.name;
        kt2.link = kt.id; kt2.name = kt.name;
        k3i.link = k3.id; k3i.name = k3.name;
        k2i.link = k2.id; k2i.name = k2.name;
        kt.delaySec = 2f;
        return c;
    }

    /** Selbsthaltung + Lampenstromkreis direkt auf das Magnetspulen-Ventil. */
    private static Circuit exElektroPneu() {
        Circuit c = new Circuit();
        c.name = "Elektropneumatik: Zylinder mit Selbsthaltung";
        c.addComp(Circuit.RAIL_L, 1, 2);
        c.addComp(Circuit.RAIL_R, 30, 2);
        Circuit.Comp k1 = c.addComp(Circuit.COIL, 14, 3);
        Circuit.Comp s1 = c.addComp(Circuit.PUSHBTN, 4, 3);
        Circuit.Comp ka = c.addComp(Circuit.CONTACT, 4, 5);
        Circuit.Comp kb = c.addComp(Circuit.CONTACT, 4, 9);
        Circuit.Comp h1 = c.addComp(Circuit.LAMP, 14, 9);
        Circuit.Comp mv = c.addComp(Circuit.VALVE52M, 10, 11);
        mv.sol = true;
        Circuit.Comp zy = c.addComp(Circuit.CYL, 20, 12);
        Circuit.Comp sp = c.addComp(Circuit.SUPPLY, 2, 16);

        // Stromkreis 1: S1 || K1 -> K1-Spule
        c.addWire(new int[]{1, 3, 4, 3});
        c.addWire(new int[]{1, 5, 4, 5});
        c.addWire(new int[]{7, 3, 10, 3});
        c.addWire(new int[]{7, 5, 10, 5});
        c.addWire(new int[]{10, 3, 10, 5});
        c.addWire(new int[]{10, 3, 14, 3});
        c.addWire(new int[]{17, 3, 30, 3});
        // Stromkreis 2: K1 -> Magnetspule (12e) & Melder parallel
        c.addWire(new int[]{1, 9, 4, 9});
        c.addWire(new int[]{7, 9, 10, 9});
        c.addWire(new int[]{10, 9, 10, 11});   // -> Ventilspule (10,11)
        c.addWire(new int[]{10, 9, 14, 9});    // -> Melder
        c.addWire(new int[]{17, 9, 30, 9});
        // Pneumatik: Versorgung (4,17) -> 1 (12,12)
        c.addWire(new int[]{4, 17, 12, 17, 12, 12}, false);
        // 2 (11,11) -> A (21,13)
        c.addWire(new int[]{11, 11, 11, 13, 21, 13}, false);
        // 4 (13,11) -> B (28,13)
        c.addWire(new int[]{13, 11, 13, 14, 24, 14, 24, 13, 28, 13}, false);
        ka.link = k1.id; ka.name = k1.name;
        kb.link = k1.id; kb.name = k1.name;
        h1.crane = false;
        sp.name = "0Z"; mv.name = "1V1"; zy.name = "1A1";
        return c;
    }
}
