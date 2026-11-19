import de.schaltstudio.core.Circuit;
import de.schaltstudio.core.Engine;
import de.schaltstudio.core.Examples;

/** Prüft die gebauten Beispiele auf Plausibilität. */
public class ExSanity {

    static int fails = 0;

    static void check(String name, boolean cond) {
        if (!cond) { fails++; System.out.println("FAIL: " + name); }
        else System.out.println("ok:   " + name);
    }

    public static void main(String[] args) {
        Examples.Ex motor = null, folge = null;
        for (Examples.Ex ex : Examples.all()) {
            if (ex.circuit.name.contains("Drehstrom")) motor = ex;
            if (ex.circuit.name.contains("Folge")) folge = ex;
        }
        check("Beispiel Motor vorhanden", motor != null);
        check("Beispiel Folgeschaltung vorhanden", folge != null);

        if (motor != null) {
            Circuit c = Examples.all().get(indexOfName(motor.circuit.name)).circuit;
            Engine e = new Engine(c);
            e.running = true;
            Circuit.Comp km = null, s1 = null, m = null;
            for (Circuit.Comp comp : c.comps) {
                if (Circuit.CONTACTOR.equals(comp.tag)) km = comp;
                if (Circuit.PUSHBTN.equals(comp.tag)) s1 = comp;
                if (Circuit.MOTOR.equals(comp.tag)) m = comp;
            }
            check("Motor-Beispiel: Schütz + Taster + Motor drin", km != null && s1 != null && m != null);
            s1.man = true;
            for (int i = 0; i < 6; i++) e.tick();
            check("Motor-Beispiel: Schütz zieht an", km.powered);
            check("Motor-Beispiel: Motor läuft", m.powered);
            s1.man = false;
            for (int i = 0; i < 4; i++) e.tick();
            check("Motor-Beispiel: Selbsthaltung hält", km.powered && m.powered);
        }

        Examples.Ex yd = null;
        for (Examples.Ex ex : Examples.all())
            if (ex.circuit.name.contains("Stern-Dreieck")) yd = ex;
        check("Beispiel Stern-Dreieck vorhanden", yd != null);
        if (yd != null) {
            Circuit c = Examples.all().get(indexOfName(yd.circuit.name)).circuit;
            Engine e = new Engine(c);
            e.running = true;
            Circuit.Comp k1 = null, k2 = null, k3 = null, s1 = null, m = null, f1 = null;
            int kx = 0;
            for (Circuit.Comp comp : c.comps) {
                if (Circuit.CONTACTOR.equals(comp.tag)) {
                    if (kx == 0) k1 = comp; else if (kx == 1) k2 = comp; else k3 = comp;
                    kx++;
                }
                if (Circuit.PUSHBTN.equals(comp.tag)) s1 = comp;
                if (Circuit.MOTOR6.equals(comp.tag)) m = comp;
                if (Circuit.BIMET.equals(comp.tag)) f1 = comp;
            }
            check("Stern-Dreieck-Beispiel: alle Bauteile drin",
                    k1 != null && k2 != null && k3 != null && s1 != null && m != null && f1 != null);
            for (int i = 0; i < 4; i++) e.tick();
            check("Stern-Dreieck-Beispiel: ruht", !k1.powered && !k2.powered && !k3.powered && !m.powered);
            s1.man = true;
            for (int i = 0; i < 10; i++) e.tick();
            check("Stern-Dreieck-Beispiel: K1+K2 an (Stern)", k1.powered && k2.powered && !k3.powered && m.powered);
            for (int i = 0; i < 45; i++) e.tick();
            check("Stern-Dreieck-Beispiel: umgeschaltet (Dreieck)", k1.powered && !k2.powered && k3.powered && m.powered);
            f1.tripped = true;
            for (int i = 0; i < 4; i++) e.tick();
            check("Stern-Dreieck-Beispiel: Motorschutz trennt alles", !k1.powered && !k2.powered && !k3.powered && !m.powered);
        }

        if (folge != null) {
            Circuit c = Examples.all().get(indexOfName(folge.circuit.name)).circuit;
            Engine e = new Engine(c);
            e.running = true;
            Circuit.Comp zyA = null, zyB = null, vA = null;
            for (Circuit.Comp comp : c.comps) {
                if (Circuit.CYL_S.equals(comp.tag) && zyA == null) zyA = comp;
                else if (Circuit.CYL_S.equals(comp.tag)) zyB = comp;
                if (Circuit.VALVE32.equals(comp.tag)) vA = comp;
            }
            for (int i = 0; i < 10; i++) e.tick();
            check("Folge-Beispiel: Ruhe — B hinten", zyB.cyl < 0.02f);
            vA.man = true;
            for (int i = 0; i < 60; i++) e.tick();
            check("Folge-Beispiel: A voraus", zyA.cyl > 0.95f);
            check("Folge-Beispiel: B folgt", zyB.cyl > 0.3f);
        }

        System.out.println(fails == 0 ? "\nALLES OK" : "\n" + fails + " FEHLER");
        if (fails > 0) System.exit(1);
    }

    static int indexOfName(String name) {
        java.util.List<Examples.Ex> all = Examples.all();
        for (int i = 0; i < all.size(); i++)
            if (all.get(i).circuit.name.equals(name)) return i;
        return 0;
    }
}
