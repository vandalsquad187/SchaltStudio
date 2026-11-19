package de.schaltstudio.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vereinfachte Schaltungssimulation:
 * - Elektro als Stromlaufplan-Auswertung (Strompotential-Ausbreitung, Fixpunkt über Kontakte)
 * - Pneumatik als gerichtete Druckausbreitung von der Versorgung bis zu den Ventilen/Zylindern
 */
public class Engine {

    public final Circuit c;
    public boolean running = false;

    // Sichtbare Netzauswertung (für die Ansicht)
    public Set<String> linesL = new HashSet<>(); // stromführend (rot)
    public Set<String> linesR = new HashSet<>(); // Rückleitung (amber)
    public Set<String> linesP = new HashSet<>(); // unter Druck (rot)
    public Set<String> linesV = new HashSet<>(); // entlüftet (hell)

    static final float CYL_SPEED = 0.03f; // pro Tick

    public Engine(Circuit c) { this.c = c; }

    // ------------------------------------------------------------------ Netze

    private static String k(char kind, int x, int y) { return kind + ":" + x + "," + y; }

    /** Union-Find über Knoten-Schlüssel. */
    private static class DSU {
        final Map<String, String> p = new HashMap<>();
        String find(String a) {
            String r = a;
            String n = p.get(r);
            while (n != null && !n.equals(r)) { r = n; n = p.get(r); }
            String s = a;
            while (!s.equals(r)) {
                String nxt = p.get(s);
                p.put(s, r);
                s = nxt;
            }
            return r;
        }
        void union(String a, String b) {
            a = find(a); b = find(b);
            if (!a.equals(b)) p.put(a, b);
        }
    }

    // ------------------------------------------------------------------ Simulation

    public void tick() {
        // ---- Ports erfassen ----
        Map<String, Character> portKind = new HashMap<>();
        DSU dsu = new DSU();

        for (Circuit.Comp comp : c.comps) {
            for (Circuit.Pt pt : Circuit.portsOf(comp)) {
                String key = k(pt.kind.charAt(0), comp.x + pt.dx, comp.y + pt.dy);
                portKind.put(key, pt.kind.charAt(0));
            }
        }

        Circuit.Comp railL = null, railR = null, rail3 = null;
        for (Circuit.Comp comp : c.comps) {
            if (Circuit.RAIL_L.equals(comp.tag)) railL = comp;
            if (Circuit.RAIL_R.equals(comp.tag)) railR = comp;
            if (Circuit.RAIL3.equals(comp.tag)) rail3 = comp;
        }

        // ---- Leitungen in Netze einfügen ----
        for (Circuit.Wire w : wList()) {
            if (w.pts.length < 4) continue;
            // Medium: entscheidend ist, welcher Porttyp an den Enden trifft
            boolean hadE = matchesKind(w, 'e', portKind);
            boolean hadP = matchesKind(w, 'p', portKind);
            if (hadE) w.electric = true;
            else if (hadP) w.electric = false;
            char kk = w.electric ? 'e' : 'p';
            String prev = k(kk, w.pts[0], w.pts[1]);
            for (int i = 2; i < w.pts.length; i += 2) {
                String cur = k(kk, w.pts[i], w.pts[i + 1]);
                dsu.union(prev, cur);
                prev = cur;
            }
        }
        // Ports mit Koordinaten treffen => verbinden (gleicher Schlüssel = gleiche Stelle)

        for (Circuit.Wire w : wList()) {
            if (!w.electric) continue;
            for (int i = 0; i < w.pts.length; i += 2) {
                if (railL != null && Math.abs(w.pts[i] - railL.x) <= 0) dsu.union("RL", k('e', w.pts[i], w.pts[i + 1]));
                if (railR != null && Math.abs(w.pts[i] - railR.x) <= 0) dsu.union("RR", k('e', w.pts[i], w.pts[i + 1]));
                if (rail3 != null && Math.abs(w.pts[i] - rail3.x) <= 0) dsu.union("R3", k('e', w.pts[i], w.pts[i + 1]));
            }
        }

        netzData = new HashMap<>();
        for (Circuit.Comp comp : c.comps) {
            for (Circuit.Pt pt : Circuit.portsOf(comp)) {
                String key = k(pt.kind.charAt(0), comp.x + pt.dx, comp.y + pt.dy);
                netzData.put(key, dsu.find(key));
            }
        }

        // ================= Elektro =================
        // Anfangszustände anwenden
        for (Circuit.Comp comp : c.comps) comp.pilot12 = comp.pilot14 = false;

        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 40) {
            changed = false;
            Map<String, List<String>> adj = new HashMap<>();
            for (Circuit.Comp comp : c.comps) {
                if (!Circuit.isSwitching(comp.tag)) continue;
                if (!conducts(comp, compLinkState(comp))) continue;
                List<Circuit.Pt> ports = Circuit.portsOf(comp);
                String a = netzOf(ports.get(0), comp, 'e');
                String bb = netzOf(ports.get(1), comp, 'e');
                if (a == null || bb == null) continue;
                adj.computeIfAbsent(a, x -> new ArrayList<>()).add(bb);
                adj.computeIfAbsent(bb, x -> new ArrayList<>()).add(a);
            }
            // Schütz: drei Hauptkontakte leiten, wenn die Spule (A1/A2) erregt ist
            for (Circuit.Comp comp : c.comps) {
                if (!Circuit.CONTACTOR.equals(comp.tag) || !comp.powered) continue;
                List<Circuit.Pt> kports = Circuit.portsOf(comp);
                for (int i = 2; i + 1 < kports.size(); i += 2) {
                    String ka = netzOf(kports.get(i), comp, 'e');
                    String kb = netzOf(kports.get(i + 1), comp, 'e');
                    if (ka == null || kb == null) continue;
                    adj.computeIfAbsent(ka, x -> new ArrayList<>()).add(kb);
                    adj.computeIfAbsent(kb, x -> new ArrayList<>()).add(ka);
                }
            }
            // Motorschutzschalter: Pole 1-2/3-4/5-6 + Hilfsöffner 95-96 leiten, solange nicht ausgelöst
            for (Circuit.Comp comp : c.comps) {
                if (!Circuit.BIMET.equals(comp.tag) || comp.tripped) continue;
                List<Circuit.Pt> kports = Circuit.portsOf(comp);
                for (int i = 0; i + 1 < kports.size(); i += 2) {
                    String ka = netzOf(kports.get(i), comp, 'e');
                    String kb = netzOf(kports.get(i + 1), comp, 'e');
                    if (ka == null || kb == null) continue;
                    adj.computeIfAbsent(ka, x -> new ArrayList<>()).add(kb);
                    adj.computeIfAbsent(kb, x -> new ArrayList<>()).add(ka);
                }
            }
            linesL.clear(); linesR.clear();
            if (railL != null) {
                String seedL = dsu.find("RL");
                linesL.add(seedL);
                bfs(adj, seedL, linesL);
            }
            if (rail3 != null) {
                String seed3 = dsu.find("R3");
                linesL.add(seed3);
                bfs(adj, seed3, linesL);
            }
            if (railR != null) {
                String seedR = dsu.find("RR");
                linesR.add(seedR);
                bfs(adj, seedR, linesR);
            }

            for (Circuit.Comp comp : c.comps) {
                if (!Circuit.isLoad(comp.tag)) continue;
                List<Circuit.Pt> ports = Circuit.portsOf(comp);
                if (ports.size() < 2) continue;
                String a = netzOf(ports.get(0), comp, 'e');
                String bb = netzOf(ports.get(1), comp, 'e');
                boolean pw = (a != null && linesL.contains(a) && bb != null && linesR.contains(bb))
                        || (bb != null && linesL.contains(bb) && a != null && linesR.contains(a));
                if (pw != comp.powered) { comp.powered = pw; changed = true; }
            }
            // Drehstrommotor: läuft, wenn alle drei Zuleitungen unter Spannung
            for (Circuit.Comp comp : c.comps) {
                if (!Circuit.MOTOR.equals(comp.tag)) continue;
                List<Circuit.Pt> ports = Circuit.portsOf(comp);
                boolean pw = true;
                for (Circuit.Pt p : ports) {
                    String n = netzOf(p, comp, 'e');
                    if (n == null || !linesL.contains(n)) { pw = false; break; }
                }
                if (pw != comp.powered) { comp.powered = pw; changed = true; }
            }
            // 6-Klemmen-Motor: Stern (U2=V2=W2 verbunden) oder Dreieck (U2/V2/W2 unter Spannung)
            for (Circuit.Comp comp : c.comps) {
                if (!Circuit.MOTOR6.equals(comp.tag)) continue;
                List<Circuit.Pt> ports = Circuit.portsOf(comp);
                boolean pw = true;
                for (int i = 0; i < 3; i++) {
                    String n = netzOf(ports.get(i), comp, 'e');
                    if (n == null || !linesL.contains(n)) { pw = false; break; }
                }
                if (pw) {
                    String n2 = netzOf(ports.get(3), comp, 'e'), n3 = netzOf(ports.get(4), comp, 'e'), n4 = netzOf(ports.get(5), comp, 'e');
                    boolean delta = n2 != null && n3 != null && n4 != null
                            && linesL.contains(n2) && linesL.contains(n3) && linesL.contains(n4);
                    // Stern: die drei Endklemmen sind im Kontakt-Graphen verbunden (Sternschiene)
                    boolean star = false;
                    if (n2 != null && n3 != null && n4 != null) {
                        Set<String> seen = new HashSet<>();
                        ArrayDeque<String> q = new ArrayDeque<>();
                        q.add(n2); seen.add(n2);
                        while (!q.isEmpty() && !star) {
                            String cur = q.poll();
                            for (String nx : adj.getOrDefault(cur, java.util.Collections.emptyList())) {
                                if (nx.equals(n3) || nx.equals(n4)) { star = true; break; }
                                if (seen.add(nx)) q.add(nx);
                            }
                        }
                    }
                    pw = delta || star;
                }
                if (pw != comp.powered) { comp.powered = pw; changed = true; }
            }
        }
        // Motor-Rotor drehen (einmal pro Tick, nicht pro Fixpunkt-Iteration)
        for (Circuit.Comp comp : c.comps)
            if ((Circuit.MOTOR.equals(comp.tag) || Circuit.MOTOR6.equals(comp.tag)) && comp.powered)
                comp.anim += 0.06f;

        // Ventil mit integrierter Magnetspule (1-port elektrisch)
        for (Circuit.Comp comp : c.comps) {
            if (!Circuit.isPneu(comp.tag) || !comp.sol) continue;
            List<Circuit.Pt> ports = Circuit.portsOf(comp);
            for (Circuit.Pt pt : ports) {
                if (!"e".equals(pt.kind)) continue;
                String netz = netzOf(pt, comp, 'e');
                if (netz != null && linesL.contains(netz)) {
                    if (pt.label.startsWith("12")) comp.pilot12 = true;
                    if (pt.label.startsWith("14")) comp.pilot14 = true;
                }
            }
        }
        // Externe Magnetspule -> verknüpftes Ventil
        for (Circuit.Comp comp : c.comps) {
            if (!Circuit.SOLENOID.equals(comp.tag) || !comp.powered) continue;
            Circuit.Comp v = Circuit.findComp(c.comps, comp.link);
            if (v != null && Circuit.isPneu(v.tag)) {
                if (Circuit.portsOf(v).stream().anyMatch(pp -> "12".equals(pp.label))) v.pilot12 = true;
                if (Circuit.portsOf(v).stream().anyMatch(pp -> "14".equals(pp.label))) v.pilot14 = true;
            }
        }

        // Zeitrelais: Einschaltverzögerung
        final int tickMs = 40;
        for (Circuit.Comp comp : c.comps) {
            if (!Circuit.TIMER.equals(comp.tag)) continue;
            if (comp.powered) {
                comp.tAccum += tickMs;
                if (comp.tAccum >= comp.delaySec * 1000f) comp.en = true;
            } else {
                comp.tAccum = 0f;
                comp.en = false;
            }
            comp.powered = comp.en; // schlatende Kontakte folgen dem verzögerten Zustand
        }

        // ================= Pneumatik =================
        // (1) Vorlauf: Ventilzustände wie beim letzten Tick -> Piloten ermitteln
        Map<String, List<String>> adj1 = new HashMap<>();
        buildPneuAdj(adj1);
        Set<String> pn1 = new HashSet<>();
        for (Circuit.Comp comp : c.comps) {
            if (Circuit.SUPPLY.equals(comp.tag)) {
                List<Circuit.Pt> ports = Circuit.portsOf(comp);
                String netz = netzOf(ports.get(0), comp, 'p');
                if (netz != null) bfsDir(adj1, netz, pn1);
            }
        }
        for (Circuit.Comp comp : c.comps) {
            if (Circuit.isPneu(comp.tag)) {
                List<Circuit.Pt> ports = Circuit.portsOf(comp);
                for (Circuit.Pt pt : ports) {
                    if (!"p".equals(pt.kind)) continue;
                    String netz = netzOf(pt, comp, 'p');
                    if (netz == null) continue;
                    if (pn1.contains(netz) && "12".equals(pt.label)) comp.pilot12 = true;
                    if (pn1.contains(netz) && "14".equals(pt.label)) comp.pilot14 = true;
                }
            }
        }

        // (2) Ventilzustände anwenden
        for (Circuit.Comp comp : c.comps) {
            if (!Circuit.isPneu(comp.tag)) continue;
            switch (comp.tag) {
                case Circuit.VALVE52M:
                    comp.state0 = !comp.pilot12;
                    break;
                case Circuit.VALVE52B:
                    if (comp.pilot12) comp.state0 = false;
                    else if (comp.pilot14) comp.state0 = true;
                    break;
            }
        }

        // (3) Erneut ausbreiten (endgültiger Zustand)
        linesP.clear(); linesV.clear();
        Map<String, List<String>> adj2 = new HashMap<>();
        buildPneuAdj(adj2);
        for (Circuit.Comp comp : c.comps) {
            if (Circuit.SUPPLY.equals(comp.tag)) {
                List<Circuit.Pt> ports = Circuit.portsOf(comp);
                String netz = netzOf(ports.get(0), comp, 'p');
                if (netz != null) bfsDir(adj2, netz, linesP);
            }
        }
        // Entlüftete Netze: Rückwärts von Auslassschlüssen
        Map<String, List<String>> rev = new HashMap<>();
        for (Map.Entry<String, List<String>> e : adj2.entrySet())
            for (String to : e.getValue())
                rev.computeIfAbsent(to, x -> new ArrayList<>()).add(e.getKey());
        for (Circuit.Comp comp : c.comps) {
            if (!Circuit.isPneu(comp.tag)) continue;
            List<Circuit.Pt> ports = Circuit.portsOf(comp);
            for (Circuit.Pt pt : ports) {
                if (!"p".equals(pt.kind)) continue;
                if (!pt.label.equals("3") && !pt.label.equals("5")) continue;
                String netz = netzOf(pt, comp, 'p');
                if (netz != null) bfsDir(rev, netz, linesV);
            }
        }

        // (4) Zylinder bewegen
        for (Circuit.Comp comp : c.comps) {
            if (!Circuit.CYL.equals(comp.tag) && !Circuit.CYL_S.equals(comp.tag)) continue;
            List<Circuit.Pt> ports = Circuit.portsOf(comp);
            String na = netzOf(ports.get(0), comp, 'p');
            String nb = ports.size() > 1 ? netzOf(ports.get(1), comp, 'p') : null;
            boolean pa = na != null && linesP.contains(na);
            boolean pb = nb != null && linesP.contains(nb);
            boolean va = na != null && linesV.contains(na);
            boolean vb = nb != null && linesV.contains(nb);
            int dir = 0;
            if (Circuit.CYL_S.equals(comp.tag)) {
                if (pa) dir = 1;
                else dir = -1; // Feder zieht zurück, sobald kein Druck mehr anliegt
            } else {
                if (pa && vb && !pb) dir = 1;
                else if (pb && va && !pa) dir = -1;
            }
            comp.moveDir = dir;
            if (dir > 0) comp.cyl = Math.min(1f, comp.cyl + CYL_SPEED);
            else if (dir < 0) comp.cyl = Math.max(0f, comp.cyl - CYL_SPEED);
        }

        // Leitungen einfärben (Sichtzustand)
        for (Circuit.Wire w : wList()) {
            w.hot = false; w.ret = false; w.vent = false;
            if (w.pts.length < 4) continue;
            String root = dsu.find(k(w.electric ? 'e' : 'p', w.pts[0], w.pts[1]));
            if (w.electric) {
                w.hot = linesL.contains(root);
                w.ret = linesR.contains(root) && !w.hot;
            } else {
                w.hot = linesP.contains(root);
                w.vent = linesV.contains(root) && !w.hot;
            }
        }

        // Druckzustand für das nächste Tick sichern (Druckschalter reagiert verzögert)
        prevP.clear();
        prevP.addAll(linesP);
    }

    private List<Circuit.Wire> wList() { return c.wires; }

    private String netzOf(Circuit.Pt pt, Circuit.Comp comp, char kind) {
        if (netzData == null) return null;
        String key = k(kind, comp.x + pt.dx, comp.y + pt.dy);
        String n = netzData.get(key);
        return n != null ? n : key; // isolierter Port = eigenes Netz
    }

    private Map<String, String> netzData;
    final Set<String> prevP = new HashSet<>(); // pneumatische Netze des Vorticks (Druckschalter)

    private boolean conducts(Circuit.Comp comp, boolean linkPowered) {
        switch (comp.tag) {
            case Circuit.SWITCH: return comp.sw;
            case Circuit.PUSHBTN: return comp.man;
            case Circuit.PUSHBTN_NC: return !comp.man;
            case Circuit.CONTACT: return comp.link != null && !comp.link.isEmpty() && linkPowered;
            case Circuit.CONTACT_NC:
                if (comp.link == null || comp.link.isEmpty()) return true;
                return !linkPowered;
            case Circuit.PSW: {
                // Druckschalter: geschlossen, wenn verknüfpter pneumatischer Anschluss unter Druck
                String l = comp.link == null ? "" : comp.link;
                int ix = l.indexOf(':');
                String cid = ix >= 0 ? l.substring(0, ix) : l;
                String pl = ix >= 0 ? l.substring(ix + 1) : "1";
                Circuit.Comp pc = Circuit.findComp(c.comps, cid);
                if (pc == null || netzData == null) return false;
                for (Circuit.Pt p : Circuit.portsOf(pc)) {
                    if (!p.label.equals(pl)) continue;
                    if (!"p".equals(p.kind)) return false;
                    String key = k('p', pc.x + p.dx, pc.y + p.dy);
                    String n = netzData.containsKey(key) ? netzData.get(key) : key;
                    return prevP.contains(n);
                }
                return false;
            }
            default: return false;
        }
    }

    /** Sichtzustand eines Druckschalters (für die Zeichnung). */
    public boolean pswClosed(Circuit.Comp comp) { return conducts(comp, false); }

    private boolean compLinkState(Circuit.Comp comp) {
        if (comp.link != null && !comp.link.isEmpty()) {
            for (Circuit.Comp o : c.comps) if (o.id.equals(comp.link))
                return Circuit.TIMER.equals(o.tag) ? o.en : o.powered;
        }
        return false;
    }

    private void bfs(Map<String, List<String>> adj, String seed, Set<String> out) {
        ArrayDeque<String> q = new ArrayDeque<>();
        q.add(seed);
        out.add(seed);
        while (!q.isEmpty()) {
            String cur = q.poll();
            List<String> n = adj.get(cur);
            if (n == null) continue;
            for (String nx : n) if (out.add(nx)) q.add(nx);
        }
    }

    private void bfsDir(Map<String, List<String>> adj, String seed, Set<String> out) {
        if (!out.add(seed)) return;
        ArrayDeque<String> q = new ArrayDeque<>();
        q.add(seed);
        while (!q.isEmpty()) {
            String cur = q.poll();
            List<String> n2 = adj.get(cur);
            if (n2 == null) continue;
            for (String nx : n2) if (out.add(nx)) q.add(nx);
        }
    }

    private void buildPneuAdj(Map<String, List<String>> adj) {
        for (Circuit.Comp comp : c.comps) {
            if (!Circuit.isPneu(comp.tag) || Circuit.SUPPLY.equals(comp.tag) ||
                Circuit.CYL.equals(comp.tag) || Circuit.CYL_S.equals(comp.tag)) continue;
            List<Circuit.Pt> ports = Circuit.portsOf(comp);
            Map<String, String> byLabel = new HashMap<>();
            for (Circuit.Pt p : ports) {
                String netz = netzOf(p, comp, 'p');
                if (netz != null) byLabel.put(p.label, netz);
            }
            String n1 = byLabel.get("1"), n2 = byLabel.get("2"), n3 = byLabel.get("3"),
                   n4 = byLabel.get("4"), n5 = byLabel.get("5");
            if (comp.tag.equals(Circuit.VALVE32) || comp.tag.equals(Circuit.VALVE32R)) {
                boolean pressed = comp.man;
                if (comp.tag.equals(Circuit.VALVE32R)) {
                    // Rollenhebel: ausgelöst, wenn verknüpfter Zylinder die Schwelle erreicht
                    Circuit.Comp zy = Circuit.findComp(c.comps, comp.link);
                    pressed = zy != null && zy.cyl >= comp.thr - 0.02f;
                    comp.sw = pressed; // Sichtzustand
                }
                if (pressed && n1 != null && n2 != null) dir(adj, n1, n2);
                if (!pressed && n2 != null && n3 != null) dir(adj, n2, n3);
            } else if (comp.state0) {
                if (n1 != null && n4 != null) dir(adj, n1, n4);
                if (n2 != null && n5 != null) dir(adj, n2, n5);
            } else {
                if (n1 != null && n2 != null) dir(adj, n1, n2);
                if (n4 != null && n3 != null) dir(adj, n4, n3);
            }
        }
    }

    private void dir(Map<String, List<String>> adj, String a, String b) {
        adj.computeIfAbsent(a, x -> new ArrayList<>()).add(b);
    }

    private boolean matchesKind(Circuit.Wire w, char kind, Map<String, Character> portKind) {
        for (int i = 0; i < w.pts.length; i += 2) {
            Character pk = portKind.get(k(kind, w.pts[i], w.pts[i + 1]));
            if (pk != null && pk == kind) return true;
        }
        return false;
    }
}
