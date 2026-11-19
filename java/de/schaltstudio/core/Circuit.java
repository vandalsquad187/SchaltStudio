package de.schaltstudio.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Datenmodell für eine Schaltung (Stromlauf + Pneumatik).
 * Alle Koordinaten sind Rasterwerte (Grid-Einheiten), 1 Einheit = 1 Rasterfeld.
 */
public class Circuit {

    // ---- Bauteil-Typen (Elektro) ----
    public static final String RAIL_L = "rail_l";
    public static final String RAIL_R = "rail_r";
    public static final String RAIL3 = "rail3";            // Drehstrom-Schiene L1/L2/L3
    public static final String MOTOR = "motor";            // Drehstrom-Asynchronmotor
    public static final String CONTACT = "contact";        // Schließkontakt (NO)
    public static final String CONTACT_NC = "contactnc";   // Öffner (NC)
    public static final String SWITCH = "switch";          // Schalter (nicht selbsttätig)
    public static final String PUSHBTN = "pushbtn";        // Taster (Schließer)
    public static final String PUSHBTN_NC = "pushbtncc";   // Taster (Öffner)
    public static final String TIMER = "zeit";             // Zeitrelais (einschaltverzögert)
    public static final String PSW = "psw";                // Druckschalter
    public static final String LAMP = "lamp";              // Melder (Farbe in kind)
    public static final String COIL = "coil";              // Spule
    public static final String SOLENOID = "vsol";          // Magnetspule für Pneumatikventil
    public static final String CONTACTOR = "kontaktor";    // Schütz: Spule A1/A2 + 3 Hauptkontakte
    public static final String BIMET = "bimet";            // Motorschutzschalter (Bimetall): 3 Pole + Hilfskontakt 95/96
    public static final String MOTOR6 = "motor6";          // Drehstrom-Motor mit 6 Klemmen (Stern/Dreieck)

    // ---- Bauteil-Typen (Pneumatik) ----
    public static final String SUPPLY = "supply";          // Druckluftversorgung
    public static final String VALVE32 = "valve32";        // 3/2-Wegeventil, Taster
    public static final String VALVE32R = "valve32r";      // 3/2-Wegeventil, Rollenhebel
    public static final String VALVE52M = "valve52m";      // 5/2-Weg, monostabil (Feder)
    public static final String VALVE52B = "valve52b";      // 5/2-Weg, bistabil
    public static final String CYL = "cyl";                // Doppelwirkender Zylinder
    public static final String CYL_S = "cyls";             // Einfachwirkender Zylinder

    public static boolean isPneu(String tag) {
        switch (tag) {
            case SUPPLY: case VALVE32: case VALVE32R: case VALVE52M: case VALVE52B:
            case CYL: case CYL_S: return true;
            default: return false;
        }
    }

    /** Schaltes Bauteile, deren PORTS Strom durchlassen. */
    public static boolean isSwitching(String tag) {
        switch (tag) {
            case CONTACT: case CONTACT_NC: case SWITCH:
            case PUSHBTN: case PUSHBTN_NC: case PSW: return true;
            default: return false;
        }
    }

    public static boolean isElec(String tag) { return !isPneu(tag); }

    /** Zweiport-"Last" (kann stromlos/stromführend sein): Spule, Melder, VSol, Zeitrelais, Schützspule. */
    public static boolean isLoad(String tag) {
        return tag.equals(COIL) || tag.equals(LAMP) || tag.equals(SOLENOID)
                || tag.equals(TIMER) || tag.equals(CONTACTOR);
    }

    public String name = "Unbenannt";
    public final List<Comp> comps = new ArrayList<>();
    public final List<Wire> wires = new ArrayList<>();
    private final Map<String, Integer> nameCount = new HashMap<>();
    private int uid = 0;

    // ------------------------------------------------------------------

    public static class Comp {
        public String id;
        public String tag;
        public int x, y;        // Rasteranker
        public String name = "";
        public String link = ""; // Kontakt->Spulen-ID; VSol->Ventil-ID
        public boolean sol;      // Ventil mit integrierter Magnetspule
        public boolean crane;    // Melderfarbe: true=rot, false=grün
        public float thr;        // 3/2-Rollenhebel: Auslöseschwelle des Zylinders
        public float delaySec;   // Zeitrelais: Verzögerung in s
        public boolean tripped;  // Motorschutzschalter: ausgelöst (latcht bis Zurücksetzen)

        // Laufzeitzustand (nicht serialisiert)
        public transient boolean man = false;    // Taster/Ventil manuell betätigt
        public transient boolean sw = false;     // Schalter-Stellung
        public transient boolean powered = false; // Last erregt / Melder an
        public transient boolean state0 = false; // Ventil: true=Grundstellung(links), false=Pilot(links->verschiebt)
        public transient boolean pilot12 = false, pilot14 = false;
        public transient float cyl = 0f;         // Zylinder 0..1
        public transient boolean hot = false;    // Sicht-Flag für Ansicht
        public transient int moveDir = 0;        // -1 zurück, +1 vor
        public transient float anim = 0f;        // Animationsphase 0..1
        public transient float tAccum = 0f;      // Zeitrelais-Zeit
        public transient boolean en = false;     // Zeitrelais: roher Erregerzustand
    }

    public static class Wire {
        public String id;
        public int[] pts;        // x0,y0,x1,y1,... (Raster)
        public transient boolean electric = true;
        public transient boolean hot = false;    // stromführend / Druck
        public transient boolean ret = false;    // Rückleitung (amber)
        public transient boolean vent = false;   // entlüftet (gestrichelt)
    }

    // ------------------------------------------------------------------ Ports

    public static class Pt {
        public int dx, dy;      // Offset vom Anker
        public String kind;     // "e" elektrisch, "p" pneumatisch
        public String label;    // Portnummer
    }

    public static List<Pt> portsOf(Comp c) {
        List<Pt> l = new ArrayList<>();
        switch (c.tag) {
            case CONTACT: case CONTACT_NC: case SWITCH: case PUSHBTN: case PUSHBTN_NC:
            case COIL: case SOLENOID: case LAMP: case TIMER: case PSW:
                l.add(pt(0, 0, "e", ""));
                l.add(pt(3, 0, "e", ""));
                break;
            case MOTOR:
                l.add(pt(0, 0, "e", "U"));
                l.add(pt(0, 1, "e", "V"));
                l.add(pt(0, 2, "e", "W"));
                break;
            case CONTACTOR:
                l.add(pt(0, 0, "e", "A1"));
                l.add(pt(3, 0, "e", "A2"));
                l.add(pt(0, 2, "e", "1"));
                l.add(pt(3, 2, "e", "2"));
                l.add(pt(0, 3, "e", "3"));
                l.add(pt(3, 3, "e", "4"));
                l.add(pt(0, 4, "e", "5"));
                l.add(pt(3, 4, "e", "6"));
                break;
            case BIMET:
                l.add(pt(0, 0, "e", "1"));
                l.add(pt(3, 0, "e", "2"));
                l.add(pt(0, 1, "e", "3"));
                l.add(pt(3, 1, "e", "4"));
                l.add(pt(0, 2, "e", "5"));
                l.add(pt(3, 2, "e", "6"));
                l.add(pt(0, 4, "e", "95"));
                l.add(pt(3, 4, "e", "96"));
                break;
            case MOTOR6:
                l.add(pt(0, 0, "e", "U1"));
                l.add(pt(0, 1, "e", "V1"));
                l.add(pt(0, 2, "e", "W1"));
                l.add(pt(3, 0, "e", "U2"));
                l.add(pt(3, 1, "e", "V2"));
                l.add(pt(3, 2, "e", "W2"));
                break;
            case SUPPLY:
                l.add(pt(2, 1, "p", "1"));
                break;
            case VALVE32: case VALVE32R:
                l.add(pt(0, 1, "p", "1"));
                l.add(pt(2, 1, "p", "2"));
                l.add(pt(1, 0, "p", "3"));
                break;
            case VALVE52M: {
                l.add(pt(0, 0, c.sol ? "e" : "p", "12"));
                l.add(pt(1, 0, "p", "2"));
                l.add(pt(3, 0, "p", "4"));
                l.add(pt(2, 1, "p", "1"));
                l.add(pt(1, 1, "p", "3"));
                l.add(pt(3, 1, "p", "5"));
                break;
            }
            case VALVE52B: {
                l.add(pt(0, 0, "p", "12"));
                l.add(pt(4, 0, "p", "14"));
                l.add(pt(1, 0, "p", "2"));
                l.add(pt(3, 0, "p", "4"));
                l.add(pt(2, 1, "p", "1"));
                l.add(pt(1, 1, "p", "3"));
                l.add(pt(3, 1, "p", "5"));
                break;
            }
            case CYL:
                l.add(pt(1, 1, "p", "A"));
                l.add(pt(8, 1, "p", "B"));
                break;
            case CYL_S:
                l.add(pt(1, 1, "p", "A"));
                break;
        }
        return l;
    }

    public static Pt pt(int dx, int dy, String kind, String label) {
        Pt p = new Pt();
        p.dx = dx; p.dy = dy; p.kind = kind; p.label = label;
        return p;
    }

    // ------------------------------------------------------------------ Beispielnamen

    public String nextName(String tag) {
        String pfx;
        switch (tag) {
            case CONTACT: case CONTACT_NC: case COIL: case CONTACTOR: pfx = "K"; break;
            case SWITCH: case PUSHBTN: case PUSHBTN_NC: case PSW: pfx = "S"; break;
            case TIMER: pfx = "KT"; break;
            case MOTOR: case MOTOR6: pfx = "M"; break;
            case BIMET: pfx = "F"; break;
            case LAMP: pfx = "H"; break;
            case SOLENOID: pfx = "Y"; break;
            case SUPPLY: pfx = "0Z"; break;
            case VALVE32: case VALVE32R: case VALVE52M: case VALVE52B: pfx = "1V"; break;
            case CYL: case CYL_S: pfx = "1A"; break;
            case RAIL_L: return "L+";
            case RAIL_R: return "0";
            case RAIL3: return "L1/2/3";
            default: pfx = "X"; break;
        }
        int n = nameCount.containsKey(pfx) ? nameCount.get(pfx) : 0;
        n++;
        nameCount.put(pfx, n);
        return pfx + n;
    }

    public Comp addComp(String tag, int x, int y) {
        Comp c = new Comp();
        c.id = "c" + (++uid);
        c.tag = tag;
        c.x = Math.max(0, x);
        c.y = Math.max(0, y);
        c.crane = tag.equals(LAMP);
        c.thr = 0.5f;
        c.delaySec = 1f;
        c.name = nextName(tag);
        comps.add(c);
        return c;
    }

    public Wire addWire(int[] pts, boolean electric) {
        if (pts == null || pts.length < 4 || pts.length % 2 != 0) return null;
        Wire w = new Wire();
        w.id = "w" + (++uid);
        w.pts = pts;
        w.electric = electric;
        wires.add(w);
        return w;
    }

    /** Bequeme Variante: Medium (elektrisch/pneumatisch) wird automatisch bestimmt. */
    public Wire addWire(int[] pts) {
        if (pts == null || pts.length < 4 || pts.length % 2 != 0) return null;
        boolean pneu = false;
        for (Comp comp : comps) {
            for (Pt p : portsOf(comp)) {
                if (p.kind.charAt(0) == 'p' &&
                        ((comp.x + p.dx == pts[0] && comp.y + p.dy == pts[1]) ||
                         (comp.x + p.dx == pts[pts.length - 2] && comp.y + p.dy == pts[pts.length - 1]))) {
                    pneu = true;
                    break;
                }
            }
            if (pneu) break;
        }
        return addWire(pts, !pneu);
    }

    public void removeComp(Comp c) {
        comps.remove(c);
        for (Comp o : comps) {
            if (c.id.equals(o.link)) o.link = "";
        }
        // Leitungen am Port dieses Bauteils löschen
        List<Wire> dead = new ArrayList<>();
        for (Pt p : portsOf(c)) {
            int px = c.x + p.dx, py = c.y + p.dy;
            for (Wire w : wires) if (touches(w, px, py)) dead.add(w);
        }
        wires.removeAll(dead);
    }

    public void removeWire(Wire w) { wires.remove(w); }

    public static boolean touches(Wire w, int x, int y) {
        for (int i = 0; i + 1 < w.pts.length; i += 2)
            if (w.pts[i] == x && w.pts[i + 1] == y) return true;
        return false;
    }

    public static Comp findComp(Iterable<Comp> comps, String id) {
        for (Comp c : comps) if (c.id.equals(id)) return c;
        return null;
    }

    // ------------------------------------------------------------------ Serialisierung

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("v", 1);
            JSONArray ac = new JSONArray();
            for (Comp c : comps) {
                JSONObject co = new JSONObject();
                co.put("id", c.id);
                co.put("tag", c.tag);
                co.put("x", c.x); co.put("y", c.y);
                co.put("n", c.name);
                if (c.link != null && !c.link.isEmpty()) co.put("l", c.link);
                if (c.sol) co.put("sol", true);
                if (c.crane) co.put("rot", true);
                if (c.sw && c.tag.equals(SWITCH)) co.put("sw", true);
                if (c.thr != 0 && c.thr != 0.5f) co.put("thr", c.thr);
                if (c.delaySec != 0 && c.delaySec != 1f) co.put("dl", c.delaySec);
                if (c.tripped) co.put("tr", true);
                ac.put(co);
            }
            o.put("comps", ac);
            JSONArray aw = new JSONArray();
            for (Wire w : wires) {
                JSONObject wo = new JSONObject();
                wo.put("id", w.id);
                JSONArray pa = new JSONArray();
                for (int v : w.pts) pa.put(v);
                if (!w.electric) wo.put("m", "p");
                wo.put("pts", pa);
                aw.put(wo);
            }
            o.put("wires", aw);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static Circuit fromJson(JSONObject o) {
        Circuit c = new Circuit();
        try { c.name = o.optString("name", "Unbenannt"); } catch (Exception ig) {}
        try {
            JSONArray ac = o.getJSONArray("comps");
            for (int i = 0; i < ac.length(); i++) {
                JSONObject co = ac.getJSONObject(i);
                Comp k = new Comp();
                k.id = co.optString("id", "c" + (++c.uid));
                k.tag = co.optString("tag", CONTACT);
                k.x = co.optInt("x", 2); k.y = co.optInt("y", 2);
                k.name = co.optString("n", "");
                k.link = co.optString("l", "");
                k.sol = co.optBoolean("sol", false);
                k.crane = co.optBoolean("rot", k.tag.equals(Circuit.LAMP));
                k.sw = co.optBoolean("sw", false);
                k.thr = (float) co.optDouble("thr", 0.5);
                k.delaySec = (float) co.optDouble("dl", 1);
                k.tripped = co.optBoolean("tr", false);
                c.comps.add(k);
                c.uid++;
            }
            JSONArray aw = o.getJSONArray("wires");
            for (int i = 0; i < aw.length(); i++) {
                JSONObject wo = aw.getJSONObject(i);
                Wire w = new Wire();
                w.id = wo.optString("id", "w" + (++c.uid));
                JSONArray pa = wo.getJSONArray("pts");
                int[] pts = new int[pa.length()];
                for (int j = 0; j < pa.length(); j++) pts[j] = pa.optInt(j);
                w.pts = pts;
                w.electric = !wo.has("m") || !"p".equals(wo.optString("m"));
                c.wires.add(w);
            }
        } catch (Exception ig) {}
        return c;
    }
}
