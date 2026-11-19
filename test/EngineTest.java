import de.schaltstudio.core.Circuit;
import de.schaltstudio.core.Engine;

/** VM-Test der Simulation (ohne Android). */
public class EngineTest {

    static int fails = 0;

    static void check(String name, boolean cond) {
        if (!cond) { fails++; System.out.println("FAIL: " + name); }
        else System.out.println("ok:   " + name);
    }

    static void pump(Engine e, int n) {
        for (int i = 0; i < n; i++) e.tick();
    }

    // ---------------------------------------------------------------- Selbsthaltung

    static Circuit selbst() {
        Circuit c = new Circuit();
        c.addComp(Circuit.RAIL_L, 1, 4);
        c.addComp(Circuit.RAIL_R, 30, 4);
        Circuit.Comp k1 = c.addComp(Circuit.COIL, 20, 10);
        Circuit.Comp s1 = c.addComp(Circuit.PUSHBTN, 6, 10);
        Circuit.Comp kk = c.addComp(Circuit.CONTACT, 6, 13);
        Circuit.Comp h1 = c.addComp(Circuit.LAMP, 20, 13);
        c.addWire(new int[]{1, 10, 6, 10});
        c.addWire(new int[]{1, 13, 6, 13});
        c.addWire(new int[]{9, 10, 12, 10});
        c.addWire(new int[]{12, 10, 12, 13});
        c.addWire(new int[]{9, 13, 12, 13});
        c.addWire(new int[]{12, 10, 20, 10});
        c.addWire(new int[]{12, 13, 20, 13});
        c.addWire(new int[]{23, 10, 30, 10});
        c.addWire(new int[]{23, 13, 30, 13});
        kk.link = k1.id;
        return c;
    }

    // ---------------------------------------------------------------- 3/2 + einfachwirkender Zylinder

    static Circuit cylSingle() {
        Circuit c = new Circuit();
        c.addComp(Circuit.SUPPLY, 3, 15);
        Circuit.Comp cv = c.addComp(Circuit.VALVE32, 8, 13);
        c.addComp(Circuit.CYL_S, 16, 11);
        c.addWire(new int[]{5, 16, 6, 16, 6, 14, 8, 14}, false);
        c.addWire(new int[]{10, 14, 14, 14, 14, 12, 17, 12}, false);
        return c;
    }

    // ---------------------------------------------------------------- 5/2 monostabil + Taster

    static Circuit cylDouble() {
        Circuit c = new Circuit();
        Circuit.Comp sp = c.addComp(Circuit.SUPPLY, 2, 15);
        Circuit.Comp cv = c.addComp(Circuit.VALVE32, 4, 3);
        Circuit.Comp mv = c.addComp(Circuit.VALVE52M, 10, 6);
        Circuit.Comp zy = c.addComp(Circuit.CYL, 20, 10);
        c.addWire(new int[]{4, 16, 9, 16, 9, 9, 12, 9, 12, 7}, false);
        c.addWire(new int[]{4, 16, 2, 16, 2, 4, 4, 4}, false);
        c.addWire(new int[]{6, 4, 8, 4, 8, 6, 10, 6}, false);
        c.addWire(new int[]{11, 6, 11, 4, 18, 4, 18, 11, 21, 11}, false);
        c.addWire(new int[]{13, 6, 13, 5, 24, 5, 24, 11, 28, 11}, false);
        return c;
    }

    // ---------------------------------------------------------------- Elektropneumatik: Selbsthaltung -> Ventilspule

    static Circuit elektroPneu() {
        Circuit c = new Circuit();
        c.addComp(Circuit.RAIL_L, 1, 2);
        c.addComp(Circuit.RAIL_R, 30, 2);
        Circuit.Comp k1 = c.addComp(Circuit.COIL, 14, 3);
        Circuit.Comp s1 = c.addComp(Circuit.PUSHBTN, 4, 3);
        Circuit.Comp ka = c.addComp(Circuit.CONTACT, 4, 5);
        Circuit.Comp kb2 = c.addComp(Circuit.CONTACT, 4, 9);
        Circuit.Comp mv = c.addComp(Circuit.VALVE52M, 10, 11);
        mv.sol = true;
        c.addComp(Circuit.CYL, 20, 12);
        c.addComp(Circuit.SUPPLY, 2, 16);

        c.addWire(new int[]{1, 3, 4, 3});        // 0
        c.addWire(new int[]{1, 5, 4, 5});        // 1  (L -> Wakes-Kontakt ka)
        c.addWire(new int[]{7, 3, 10, 3});       // 2
        c.addWire(new int[]{7, 5, 10, 5});       // 3
        c.addWire(new int[]{10, 3, 10, 5});      // 4
        c.addWire(new int[]{10, 3, 14, 3});      // 5
        c.addWire(new int[]{17, 3, 30, 3});      // 6
        c.addWire(new int[]{1, 9, 4, 9});        // 7
        c.addWire(new int[]{7, 9, 10, 9});       // 8
        c.addWire(new int[]{10, 9, 10, 11});     // 9
        c.addWire(new int[]{4, 17, 12, 17, 12, 12}, false);   // 10
        c.addWire(new int[]{11, 11, 11, 13, 21, 13}, false);  // 11
        c.addWire(new int[]{13, 11, 13, 14, 24, 14, 24, 13, 28, 13}, false); // 12
        ka.link = k1.id;
        kb2.link = k1.id;
        return c;
    }

    // ---------------------------------------------------------------- Zeitrelais

    static Circuit timerCirc() {
        Circuit c = new Circuit();
        c.addComp(Circuit.RAIL_L, 1, 2);
        c.addComp(Circuit.RAIL_R, 30, 2);
        Circuit.Comp sw = c.addComp(Circuit.SWITCH, 6, 2);
        Circuit.Comp kt = c.addComp(Circuit.TIMER, 10, 2);
        Circuit.Comp kk = c.addComp(Circuit.CONTACT, 6, 5);
        Circuit.Comp h1 = c.addComp(Circuit.LAMP, 20, 5);
        c.addWire(new int[]{1, 2, 6, 2});
        c.addWire(new int[]{9, 2, 10, 2});
        c.addWire(new int[]{13, 2, 30, 2});
        c.addWire(new int[]{1, 5, 6, 5});
        c.addWire(new int[]{9, 5, 20, 5});
        c.addWire(new int[]{23, 5, 30, 5});
        kk.link = kt.id;
        sw.sw = true;
        return c;
    }

    // ---------------------------------------------------------------- Druckschalter

    static Circuit pswCirc() {
        Circuit c = new Circuit();
        c.addComp(Circuit.SUPPLY, 3, 15);
        Circuit.Comp cv = c.addComp(Circuit.VALVE32, 8, 13);
        c.addComp(Circuit.CYL_S, 16, 11);
        c.addComp(Circuit.RAIL_L, 1, 2);
        c.addComp(Circuit.RAIL_R, 30, 2);
        Circuit.Comp psw = c.addComp(Circuit.PSW, 6, 2);
        Circuit.Comp h1 = c.addComp(Circuit.LAMP, 20, 2);
        c.addWire(new int[]{5, 16, 6, 16, 6, 14, 8, 14}, false);
        c.addWire(new int[]{10, 14, 14, 14, 14, 12, 17, 12}, false);
        c.addWire(new int[]{1, 2, 6, 2});
        c.addWire(new int[]{9, 2, 20, 2});
        c.addWire(new int[]{23, 2, 30, 2});
        psw.link = cv.id + ":2";
        return c;
    }

    // ---------------------------------------------------------------- Folgeschaltung mit Rollenhebel

    static Circuit folgeCirc() {
        Circuit c = new Circuit();
        c.addComp(Circuit.SUPPLY, 3, 15);
        Circuit.Comp vA = c.addComp(Circuit.VALVE32, 8, 13);
        Circuit.Comp zyA = c.addComp(Circuit.CYL_S, 20, 11);
        Circuit.Comp vr = c.addComp(Circuit.VALVE32R, 14, 13);
        Circuit.Comp mv = c.addComp(Circuit.VALVE52M, 12, 5);
        Circuit.Comp zyB = c.addComp(Circuit.CYL_S, 22, 3);
        // Versorgung -> 1 des Tasterventils (8,14); Rollenhebel wird vom Abzweig versorgt
        c.addWire(new int[]{5, 16, 6, 16, 6, 14, 8, 14}, false);
        // 2A (10,14) -> Zylinder A (21,12)
        c.addWire(new int[]{10, 14, 18, 14, 18, 12, 21, 12}, false);
        // 2A -> 1 des Rollenhebels (Abzweig am Ventilanschluss)
        c.addWire(new int[]{10, 14, 10, 16, 14, 16, 14, 14}, false);
        // Versorgung -> 1 des Hauptventils (14,6)
        c.addWire(new int[]{5, 16, 9, 16, 9, 7, 14, 7, 14, 6}, false);
        // 2R (16,14) -> Pilot 12 des Hauptventils (12,5)
        c.addWire(new int[]{16, 14, 17, 14, 17, 3, 12, 3, 12, 5}, false);
        // 2MV (13,5) -> Zylinder B (23,4)
        c.addWire(new int[]{13, 5, 13, 4, 23, 4}, false);
        vr.link = zyA.id;
        vr.thr = 0.25f;
        return c;
    }

    // ---------------------------------------------------------------- Drehstrom-Motor

    static Circuit motorCirc() {
        Circuit c = new Circuit();
        c.addComp(Circuit.RAIL3, 1, 2);
        c.addComp(Circuit.RAIL_R, 30, 2);
        Circuit.Comp ka = c.addComp(Circuit.CONTACT, 10, 2);
        Circuit.Comp kb = c.addComp(Circuit.CONTACT, 10, 3);
        Circuit.Comp kc = c.addComp(Circuit.CONTACT, 10, 4);
        c.addComp(Circuit.MOTOR, 20, 2);
        Circuit.Comp s1 = c.addComp(Circuit.PUSHBTN, 6, 8);
        Circuit.Comp k1 = c.addComp(Circuit.COIL, 10, 8);
        Circuit.Comp kk = c.addComp(Circuit.CONTACT, 6, 10);
        c.addWire(new int[]{1, 2, 10, 2});          // 0
        c.addWire(new int[]{13, 2, 20, 2});         // 1
        c.addWire(new int[]{1, 3, 10, 3});          // 2
        c.addWire(new int[]{13, 3, 20, 3});         // 3  <- Phase V (wird später unterbrochen)
        c.addWire(new int[]{1, 4, 10, 4});          // 4
        c.addWire(new int[]{13, 4, 20, 4});         // 5
        c.addWire(new int[]{1, 8, 6, 8});           // 6
        c.addWire(new int[]{9, 8, 12, 8});          // 7
        c.addWire(new int[]{12, 8, 12, 10});        // 8
        c.addWire(new int[]{1, 10, 6, 10});         // 9
        c.addWire(new int[]{9, 10, 12, 10});        // 10
        c.addWire(new int[]{12, 8, 10, 8});         // 11
        c.addWire(new int[]{13, 8, 30, 8});         // 12
        kk.link = k1.id;
        ka.link = k1.id; kb.link = k1.id; kc.link = k1.id;
        return c;
    }

    // ---------------------------------------------------------------- Schütz (Spule + 3 Hauptkontakte)

    static Circuit kontaktorCirc() {
        Circuit c = new Circuit();
        c.addComp(Circuit.RAIL3, 1, 2);
        c.addComp(Circuit.RAIL_R, 34, 2);
        Circuit.Comp km = c.addComp(Circuit.CONTACTOR, 14, 8);
        c.addComp(Circuit.MOTOR, 24, 10);
        Circuit.Comp s1 = c.addComp(Circuit.PUSHBTN, 6, 4);
        Circuit.Comp kk = c.addComp(Circuit.CONTACT, 6, 7);
        c.addWire(new int[]{1, 4, 6, 4});                  // 0
        c.addWire(new int[]{9, 4, 12, 4});                 // 1
        c.addWire(new int[]{12, 4, 12, 7});                // 2
        c.addWire(new int[]{1, 7, 6, 7});                  // 3
        c.addWire(new int[]{9, 7, 12, 7});                 // 4
        c.addWire(new int[]{12, 7, 12, 8});                // 5  <- wird später entfernt
        c.addWire(new int[]{12, 8, 14, 8});                // 6  -> A1
        c.addWire(new int[]{17, 8, 20, 8, 20, 2, 34, 2});  // 7  A2 -> 0-Schiene
        c.addWire(new int[]{1, 10, 14, 10});               // 8
        c.addWire(new int[]{17, 10, 24, 10});              // 9
        c.addWire(new int[]{1, 11, 14, 11});               // 10
        c.addWire(new int[]{17, 11, 24, 11});              // 11
        c.addWire(new int[]{1, 12, 14, 12});               // 12
        c.addWire(new int[]{17, 12, 24, 12});              // 13
        kk.link = km.id;
        return c;
    }

    // ---------------------------------------------------------------- Motorschutzschalter (Bimetall)

    static Circuit bimetCirc() {
        Circuit c = new Circuit();
        c.addComp(Circuit.RAIL3, 1, 2);
        c.addComp(Circuit.RAIL_R, 34, 2);
        Circuit.Comp f1 = c.addComp(Circuit.BIMET, 5, 12);
        Circuit.Comp km = c.addComp(Circuit.CONTACTOR, 14, 10);
        c.addComp(Circuit.MOTOR, 24, 12);
        Circuit.Comp s1 = c.addComp(Circuit.PUSHBTN, 10, 16);
        Circuit.Comp kk = c.addComp(Circuit.CONTACT, 10, 19);
        // Steuerstromkreis: Schiene -> 95/96 des Bimetalls -> Taster -> Schützspule
        c.addWire(new int[]{1, 4, 3, 4, 3, 16, 5, 16});       // 0  Schiene -> 95
        c.addWire(new int[]{8, 16, 10, 16});                  // 1  96 -> Taster
        c.addWire(new int[]{10, 16, 10, 19});                 // 2  Haltekontakt abzweigen
        c.addWire(new int[]{13, 19, 13, 16});                 // 3  -> M-Knoten (Parallele zu Taster)
        c.addWire(new int[]{13, 16, 13, 9, 14, 9, 14, 10});   // 4  -> A1
        c.addWire(new int[]{17, 10, 17, 4, 34, 4});           // 5  A2 -> 0
        // Leistung: Schiene -> Bimetall-Pole -> Schütz -> Motor
        c.addWire(new int[]{1, 12, 5, 12});                   // 6
        c.addWire(new int[]{1, 13, 5, 13});                   // 7
        c.addWire(new int[]{1, 14, 5, 14});                   // 8
        c.addWire(new int[]{8, 12, 14, 12});                  // 9
        c.addWire(new int[]{8, 13, 14, 13});                  // 10
        c.addWire(new int[]{8, 14, 14, 14});                  // 11
        c.addWire(new int[]{17, 12, 24, 12});                 // 12
        c.addWire(new int[]{17, 13, 24, 13});                 // 13
        c.addWire(new int[]{17, 14, 24, 14});                 // 14
        kk.link = km.id;
        kk.name = km.name;
        return c;
    }

    // ---------------------------------------------------------------- Stern-Dreieck-Anlauf

    static Circuit ydCirc() {
        Circuit c = new Circuit();
        c.addComp(Circuit.RAIL3, 1, 2);                        // 0
        c.addComp(Circuit.RAIL_R, 52, 2);                      // 1
        Circuit.Comp f1 = c.addComp(Circuit.BIMET, 5, 6);      // 2  Pole 6/7/8, Hilfsöffner 10
        Circuit.Comp k1 = c.addComp(Circuit.CONTACTOR, 13, 4); // 3  Spule 4, Pole 6/7/8
        c.addComp(Circuit.MOTOR6, 37, 6);                      // 4  U1V1W1 links, U2V2W2 rechts
        Circuit.Comp k2 = c.addComp(Circuit.CONTACTOR, 45, 10);// 5  Stern
        Circuit.Comp k3 = c.addComp(Circuit.CONTACTOR, 45, 16);// 6  Dreieck
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
        c.addWire(new int[]{8, 6, 10, 6, 13, 6});              // 3  Abzweig für Dreieck (10,6)
        c.addWire(new int[]{8, 7, 11, 7, 13, 7});              // 4  (11,7)
        c.addWire(new int[]{8, 8, 12, 8, 13, 8});              // 5  (12,8)
        c.addWire(new int[]{16, 6, 37, 6});                    // 6
        c.addWire(new int[]{16, 7, 37, 7});                    // 7
        c.addWire(new int[]{16, 8, 37, 8});                    // 8
        // Motor-Endklemmen U2/V2/W2 auf Klemmenleiste (41/42/43) zu Stern- und Dreieckkontakt
        c.addWire(new int[]{40, 6, 41, 6});                    // 9
        c.addWire(new int[]{41, 6, 41, 12});                   // 10
        c.addWire(new int[]{41, 12, 45, 12});                  // 11 -> K2 Reihe 1
        c.addWire(new int[]{41, 12, 41, 18, 45, 18});          // 12 -> K3 Reihe 1
        c.addWire(new int[]{40, 7, 42, 7});                    // 13
        c.addWire(new int[]{42, 7, 42, 13, 45, 13});           // 14 -> K2 Reihe 2
        c.addWire(new int[]{42, 13, 42, 19, 45, 19});          // 15 -> K3 Reihe 2
        c.addWire(new int[]{40, 8, 43, 8});                    // 16
        c.addWire(new int[]{43, 8, 43, 14, 45, 14});           // 17 -> K2 Reihe 3
        c.addWire(new int[]{43, 14, 43, 20, 45, 20});          // 18 -> K3 Reihe 3
        c.addWire(new int[]{48, 12, 48, 13, 48, 14});          // 19 Sternpunkt
        // Dreieck: Phasen (klassisch gekreuzt) -> K3 rechts
        c.addWire(new int[]{10, 6, 10, 22, 49, 22, 49, 20, 48, 20}); // 20 L1 -> W2
        c.addWire(new int[]{11, 7, 11, 23, 50, 23, 50, 18, 48, 18}); // 21 L2 -> U2
        c.addWire(new int[]{12, 8, 12, 24, 51, 24, 51, 19, 48, 19}); // 22 L3 -> V2
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

    public static void main(String[] args) {
        // ================= Selbsthaltung =================
        {
            Circuit c = selbst();
            Engine e = new Engine(c);
            // comps: 0 railL, 1 railR, 2 Spule, 3 Taster, 4 Kontakt, 5 Melder
            Circuit.Comp k = c.comps.get(2), s = c.comps.get(3), kk = c.comps.get(4), h = c.comps.get(5);
            e.running = true;
            s.man = true;
            pump(e, 3);
            check("Selbsthaltung: Spule bei Tasterdruck", k.powered);
            check("Selbsthaltung: Melder an", h.powered);
            s.man = false;
            pump(e, 3);
            check("Selbsthaltung: hält nach Loslassen", k.powered && h.powered);
            c.removeWire(c.wires.get(1)); // Stromweg der Wiedergabe unterbrechen
            pump(e, 4);
            check("Selbsthaltung: fällt beim Wegfall des Haltestroms", !k.powered && !h.powered);
        }

        // ================= 3/2 + einfachwirkender Zylinder =================
        {
            Circuit c = cylSingle();
            Engine e = new Engine(c);
            Circuit.Comp cv = c.comps.get(1), zy = c.comps.get(2);
            e.running = true;
            pump(e, 20);
            check("SIMPLE Ruhe: Zylinder eingefahren", zy.cyl < 0.02f);
            cv.man = true;
            pump(e, 60);
            check("SIMPLE Druck: Zylinder ausgefahren", zy.cyl > 0.95f);
            cv.man = false;
            pump(e, 90);
            check("SIMPLE Feder: zurück", zy.cyl < 0.02f);
        }

        // ================= 5/2 monostabil + Taster =================
        {
            Circuit c = cylDouble();
            Engine e = new Engine(c);
            Circuit.Comp cv = c.comps.get(1), zy = c.comps.get(3);
            e.running = true;
            pump(e, 20);
            check("5/2 Ruhe: Zylinder zurück", zy.cyl < 0.02f);
            cv.man = true;
            pump(e, 80);
            check("5/2 Pilot: Zylinder vor", zy.cyl > 0.95f);
            cv.man = false;
            pump(e, 120);
            check("5/2 Feder: Zylinder zurück", zy.cyl < 0.02f);
        }

        // ================= Elektropneumatik =================
        {
            Circuit c = elektroPneu();
            Engine e = new Engine(c);
            // comps: 0 railL, 1 railR, 2 Spule k1, 3 Taster s1, 4 Kontakt ka, 5 Kontakt kb,
            //        6 Ventil mv, 7 Zylinder, 8 Versorgung
            Circuit.Comp k = c.comps.get(2), s = c.comps.get(3), zy = c.comps.get(7), mv = c.comps.get(6);
            e.running = true;
            s.man = true;
            pump(e, 60);
            check("E-Pneu: Spule erregt", k.powered);
            check("E-Pneu: Magnetventil zieht an (Pilot12)", mv.pilot12);
            check("E-Pneu: Zylinder vor", zy.cyl > 0.9f);
            s.man = false;
            pump(e, 60);
            check("E-Pneu: Selbsthaltung hält Zylinder", zy.cyl > 0.9f);
            c.removeWire(c.wires.get(1)); // Wiedergabestrom unterbrechen
            pump(e, 200);
            check("E-Pneu: nach Bruch zieht Zylinder zurück", zy.cyl < 0.25f);
        }

        // ================= Zeitrelais =================
        {
            Circuit c = timerCirc();
            Engine e = new Engine(c);
            // comps: 0 railL, 1 railR, 2 Schalter, 3 Zeitrelais, 4 Kontakt, 5 Melder
            Circuit.Comp sw = c.comps.get(2), kt = c.comps.get(3), h = c.comps.get(5);
            e.running = true;
            pump(e, 10);            // 0,4 s < 1 s Verzögerung
            check("TIMER: während Verzögerung noch offen", !kt.en && !h.powered);
            pump(e, 25);            // insgesamt 1,4 s > 1 s
            check("TIMER: nach Ablauf geschlossen, Melder an", kt.en && h.powered);
            sw.sw = false;
            pump(e, 5);
            check("TIMER: fällt ohne Anregung zurück", !kt.en && !h.powered && kt.tAccum == 0f);
        }

        // ================= Druckschalter =================
        {
            Circuit c = pswCirc();
            Engine e = new Engine(c);
            // comps: 0 Versorgung, 1 Ventil, 2 Zylinder, 3 railL, 4 railR, 5 Druckschalter, 6 Melder
            Circuit.Comp cv = c.comps.get(1), h = c.comps.get(6);
            e.running = true;
            pump(e, 3);
            check("PSW: ohne Leitungsdruck Melder aus", !h.powered);
            cv.man = true;
            pump(e, 6);
            check("PSW: bei Leitungsdruck geschlossen", h.powered);
            cv.man = false;
            pump(e, 6);
            check("PSW: nach Entlüftung wieder offen", !h.powered);
        }

        // ================= Folgeschaltung (Rollenhebel) =================
        {
            Circuit c = folgeCirc();
            Engine e = new Engine(c);
            // comps: 0 Versorgung, 1 Tasterventil A, 2 Zylinder A, 3 Rollenhebel, 4 5/2-Ventil, 5 Zylinder B
            Circuit.Comp vA = c.comps.get(1), zyA = c.comps.get(2), vr = c.comps.get(3), zyB = c.comps.get(5);
            e.running = true;
            pump(e, 10);
            check("FOLGE: Ruhe — B eingefahren", zyB.cyl < 0.02f);
            vA.man = true;
            pump(e, 60);
            check("FOLGE: A ausgefahren", zyA.cyl > 0.95f);
            check("FOLGE: Rollenhebel bei Schwelle ausgelöst", vr.sw);
            check("FOLGE: Zylinder B folgt", zyB.cyl > 0.3f);
        }

        // ================= Drehstrom-Motor =================
        {
            Circuit c = motorCirc();
            Engine e = new Engine(c);
            // comps: 0 rail3, 1 railR, 2-4 Kontakte, 5 Motor, 6 Taster, 7 Spule, 8 Haltekontakt
            Circuit.Comp s1 = c.comps.get(6), k1 = c.comps.get(7), m = c.comps.get(5);
            e.running = true;
            s1.man = true;
            pump(e, 6);
            check("MOTOR: Spule erregt", k1.powered);
            check("MOTOR: läuft bei drei Phasen", m.powered);
            s1.man = false;
            pump(e, 4);
            check("MOTOR: Selbsthaltung hält Motor", m.powered && k1.powered);
            c.removeWire(c.wires.get(3));   // Phase V unterbrechen
            pump(e, 4);
            check("MOTOR: ohne eine Phase stillsteht", !m.powered && k1.powered);
        }

        // ================= Schütz (Spule + Hauptkontakte) =================
        {
            Circuit c = kontaktorCirc();
            Engine e = new Engine(c);
            // comps: 0 rail3, 1 railR, 2 Schütz km, 3 Motor, 4 Taster s1, 5 Hilfskontakt kk
            Circuit.Comp km = c.comps.get(2), m = c.comps.get(3), s1 = c.comps.get(4);
            e.running = true;
            s1.man = true;
            pump(e, 6);
            check("SCHÜTZ: Spule über Taster erregt", km.powered);
            check("SCHÜTZ: Motor läuft über Hauptkontakte", m.powered);
            s1.man = false;
            pump(e, 4);
            check("SCHÜTZ: Selbsthaltung über Hilfskontakt", km.powered && m.powered);
            c.removeWire(c.wires.get(5));   // Steuerstromkreis zur Spule kappen
            pump(e, 4);
            check("SCHÜTZ: fällt ohne Steuerstrom ab", !km.powered && !m.powered);
            s1.man = true;
            pump(e, 6);
            check("SCHÜTZ: Taster allein erreicht Spule nicht mehr", !km.powered && !m.powered);
        }

        // ================= Motorschutzschalter (Bimetall) =================
        {
            Circuit c = bimetCirc();
            Engine e = new Engine(c);
            // comps: 0 rail3, 1 railR, 2 F1, 3 K1, 4 Motor, 5 Taster, 6 Haltekontakt
            Circuit.Comp f1 = c.comps.get(2), km = c.comps.get(3), m = c.comps.get(4), s1 = c.comps.get(5);
            e.running = true;
            s1.man = true;
            pump(e, 3);
            check("BIMET: Schütz erregt, Motor läuft", km.powered && m.powered);
            f1.tripped = true;
            pump(e, 4);
            check("BIMET: Auslösung trennt Leistung UND Steuerstrom", !km.powered && !m.powered);
            f1.tripped = false;
            s1.man = false;
            pump(e, 4);
            check("BIMET: zurückgestellt, ohne Taster kein Neustart", !km.powered && !m.powered);
            s1.man = true;
            pump(e, 3);
            check("BIMET: nach Zurücksetzen wieder an", km.powered && m.powered);
            s1.man = false;
            pump(e, 4);
            check("BIMET: hält über Haltekontakt", km.powered && m.powered);
        }

        // ================= Stern-Dreieck-Anlauf =================
        {
            Circuit c = ydCirc();
            Engine e = new Engine(c);
            // comps: 0 rail3, 1 railR, 2 F1, 3 K1, 4 Motor6, 5 K2, 6 K3, 7 KT, 8 S1,
            //        9 k1h, 10 kt1, 11 k3i, 12 kt2, 13 k2i
            Circuit.Comp f1 = c.comps.get(2), k1 = c.comps.get(3), m = c.comps.get(4),
                         k2 = c.comps.get(5), k3 = c.comps.get(6), kt = c.comps.get(7), s1 = c.comps.get(8);
            e.running = true;
            pump(e, 4);
            check("Y/D: ruht ohne Betätigung", !k1.powered && !k2.powered && !k3.powered && !m.powered);
            s1.man = true;
            pump(e, 10);
            check("Y/D: K1 + K2 ziehen an (Stern)", k1.powered && k2.powered && !k3.powered);
            check("Y/D: Motor läuft im Stern", m.powered);
            pump(e, 45);   // zusammen 55 Ticks, Verzögerung 2 s = 50 Ticks
            check("Y/D: Umschaltung — K2 ab, K3 an", k1.powered && !k2.powered && k3.powered);
            check("Y/D: Motor läuft im Dreieck", m.powered);
            s1.man = false;
            pump(e, 4);
            check("Y/D: hält über Selbsthaltung", k1.powered && k3.powered && m.powered);
            f1.tripped = true;
            pump(e, 4);
            check("Y/D: Motorschutz trennt alles", !k1.powered && !k2.powered && !k3.powered && !m.powered);
        }

        System.out.println(fails == 0 ? "\nALLES OK" : "\n" + fails + " FEHLER");
        if (fails > 0) System.exit(1);
    }
}
