package de.schaltstudio.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.schaltstudio.core.Circuit;
import de.schaltstudio.core.Engine;
import de.schaltstudio.core.Examples;

public class MainActivity extends Activity implements CircuitView.Listener {

    CircuitView view;
    Engine engine;
    Button btnSim;
    LinearLayout rootRef;
    LinearLayout portBar;   // Mini-Auswahl: Druckschalter-Anschluss
    final Handler timer = new Handler();
    final int TICK = 40;

    final Runnable tickLoop = new Runnable() {
        @Override public void run() {
            if (engine != null && engine.running) {
                engine.tick();
                view.invalidate();
                timer.postDelayed(this, TICK);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new CircuitView(this);
        view.setListener(this);
        Circuit initial = loadFromFile(new File(getFilesDir(), "circuits/last.json"));
        if (initial == null) initial = Examples.newEmpty();
        setLayout();
        install(initial);
        // Beispiel beim ersten Start vorschlagen
        if (savedInstanceState == null && firstRun()) {
            showExamples();
            markRan();
        }
    }

    boolean prefs() { return getSharedPreferences("app", 0) != null; }

    boolean firstRun() {
        return getSharedPreferences("app", 0).getBoolean("ran", false) == false;
    }

    void markRan() {
        getSharedPreferences("app", 0).edit().putBoolean("ran", true).apply();
    }

    // ------------------------------------------------------------------ Aufbau

    void setLayout() {
        LinearLayout root = new LinearLayout(this);
        rootRef = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFECEFF1);

        // Edge-to-edge (Android 12+): Systemleisten-Abstand für eigene Leisten sichern
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int top = ins.getSystemWindowInsetTop();
            int bot = ins.getSystemWindowInsetBottom();
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = ins.getInsets(android.view.WindowInsets.Type.systemBars());
                top = s.top; bot = s.bottom;
            }
            root.setPadding(0, top, 0, bot);
            return new android.view.WindowInsets.Builder(ins).build();
        });

        // Leiste oben
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(6), dp(4), dp(6), dp(4));
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF37474F);

        btnSim = barBtn(bar, "▶ Sim");
        btnSim.setOnClickListener(v -> toggleSim());
        barBtn(bar, "Neu").setOnClickListener(v -> confirmNew());
        barBtn(bar, "Beispiele").setOnClickListener(v -> showExamples());
        barBtn(bar, "Öffnen").setOnClickListener(v -> showOpen());
        barBtn(bar, "Speichern").setOnClickListener(v -> showSave());

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34), 1);
        for (int i = 0; i < bar.getChildCount(); i++) {
            View ch = bar.getChildAt(i);
            LinearLayout.LayoutParams t = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(34), 1);
            t.setMargins(dp(3), 0, dp(3), 0);
            ch.setLayoutParams(t);
            ((Button) ch).setAllCaps(false);
            ((Button) ch).setTextColor(0xFFFFFFFF);
            ((Button) ch).setBackground(rounded(0xFF546E7A));
        }

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // Mini-Leiste für Druckschalter-Anschluss (nur bei Bedarf sichtbar)
        portBar = new LinearLayout(this);
        portBar.setOrientation(LinearLayout.HORIZONTAL);
        portBar.setGravity(Gravity.CENTER_VERTICAL);
        portBar.setPadding(dp(6), dp(2), dp(6), dp(2));
        portBar.setBackgroundColor(0xFFE0F2F1);

        // Palettenleiste
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout pal = new LinearLayout(this);
        pal.setOrientation(LinearLayout.HORIZONTAL);
        pal.setPadding(dp(6), dp(5), dp(6), dp(5));
        pal.setGravity(Gravity.CENTER_VERTICAL);

        palBtn(pal, "＋ Elektro", () -> pickInsert(electricItems()));
        palBtn(pal, "＋ Pneumatik", () -> pickInsert(pneumaticItems()));
        palBtn(pal, "Werkzeuge", () -> showTools());

        hs.addView(pal);
        root.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    static final GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(10);
        return d;
    }

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    Button barBtn(LinearLayout bar, String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        bar.addView(b);
        return b;
    }

    void palBtn(LinearLayout pal, String label, Runnable act) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(0xFF102027);
        b.setBackground(rounded(0xFFCFD8DC));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(132), dp(38));
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> act.run());
        pal.addView(b);
    }

    // ------------------------------------------------------------------ Install

    void install(Circuit c) {
        view.cancelWire();
        view.circuit = c;
        engine = new Engine(c);
        view.engine = engine;
        view.post(() -> view.refit());
        setTitle("SchaltStudio — " + c.name);
    }

    // ------------------------------------------------------------------ Simulation

    void toggleSim() {
        if (engine == null) return;
        engine.running = !engine.running;
        view.cancelWire();
        view.selectNothing();
        hidePortBar();
        btnSim.setText(engine.running ? "■ Stop" : "▶ Sim");
        btnSim.setBackground(rounded(engine.running ? 0xFFB71C1C : 0xFF546E7A));
        if (engine.running) timer.postDelayed(tickLoop, TICK);
        else timer.removeCallbacks(tickLoop);
        view.invalidate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        timer.removeCallbacks(tickLoop);
        if (engine != null) engine.running = false;
        storeToFile(new File(getFilesDir(), "circuits/last.json"), view.circuit);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    // ------------------------------------------------------------------ Paletten

    String[][] electricItems() {
        return new String[][]{
                {Circuit.CONTACT, "Kontakt (Schließer)"},
                {Circuit.CONTACT_NC, "Kontakt (Öffner)"},
                {Circuit.SWITCH, "Schalter"},
                {Circuit.PUSHBTN, "Taster (Schließer)"},
                {Circuit.PUSHBTN_NC, "Taster (Öffner)"},
                {Circuit.COIL, "Spule"},
                {Circuit.LAMP, "Melder (grün)"},
                {"LAMP_RED", "Melder (rot)"},
                {Circuit.SOLENOID, "Magnetspule (Ventil extern)"},
                {Circuit.CONTACTOR, "Schütz (Spule + 3 Hauptkontakte)"},
                {Circuit.BIMET, "Motorschutzschalter (Bimetall)"},
                {Circuit.TIMER, "Zeitrelais (verzögert)"},
                {Circuit.PSW, "Druckschalter"},
                {Circuit.MOTOR, "Motor (Drehstrom 3~)"},
                {Circuit.MOTOR6, "Motor 3~ mit 6 Klemmen (Stern/Dreieck)"},
                {Circuit.RAIL3, "Drehstrom-Schiene L1/L2/L3"}
        };
    }

    String[][] pneumaticItems() {
        return new String[][]{
                {Circuit.SUPPLY, "Druckluftversorgung"},
                {Circuit.VALVE32, "3/2-Wegeventil (Taster)"},
                {Circuit.VALVE32R, "3/2-Wegeventil (Rollenhebel)"},
                {Circuit.VALVE52M, "5/2-Wegeventil monostabil"},
                {Circuit.VALVE52B, "5/2-Wegeventil bistabil"},
                {Circuit.CYL, "Zylinder doppelwirkend"},
                {Circuit.CYL_S, "Zylinder einfachwirkend"}
        };
    }

    void pickInsert(String[][] items) {
        String[] labels = new String[items.length];
        for (int i = 0; i < items.length; i++) labels[i] = items[i][1];
        new AlertDialog.Builder(this)
                .setTitle("Neues Bauteil")
                .setItems(labels, (d, which) -> {
                    String tag = items[which][0];
                    if ("LAMP_RED".equals(tag)) view.setInsert(Circuit.LAMP, "red");
                    else view.setInsert(tag, null);
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    void showTools() {
        new AlertDialog.Builder(this)
                .setTitle("Werkzeuge")
                .setItems(new String[]{
                        "Bauteil umbenennen…",
                        "Bauteil löschen…",
                        "Ansicht zurücksetzen",
                        "Alles zurücksetzen (Neu)"
                }, (d, which) -> {
                    switch (which) {
                        case 0: renamePick(); break;
                        case 1: deletePick(); break;
                        case 2: view.refit(); break;
                        case 3: confirmNew(); break;
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    // ------------------------------------------------------------------ Bauteil-Menü

    @Override
    public void onCompMenu(final Circuit.Comp c) {
        hidePortBar();
        List<String> opts = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        opts.add("Umbenennen"); ids.add(0);
        boolean linkable = c.tag.equals(Circuit.CONTACT) || c.tag.equals(Circuit.CONTACT_NC)
                || c.tag.equals(Circuit.SOLENOID) || c.tag.equals(Circuit.PSW)
                || c.tag.equals(Circuit.VALVE32R);
        if (linkable) { opts.add("Verknüpfen (Ziel antippen)…"); ids.add(1); }
        if (linkable) { opts.add("Aus Liste wählen…"); ids.add(7); }
        if (c.link != null && !c.link.isEmpty()) { opts.add("Verknüpfung lösen"); ids.add(6); }
        if (c.tag.equals(Circuit.TIMER)) {
            opts.add("Verzögerung: " + delayLabel(c.delaySec) + " ändern…"); ids.add(4);
        }
        if (c.tag.equals(Circuit.VALVE32R)) {
            opts.add("Auslöseschwelle: " + Math.round(c.thr * 100) + " % ändern…"); ids.add(5);
        }
        if (c.tag.equals(Circuit.LAMP)) { opts.add("Farbe wechseln (grün/rot)"); ids.add(2); }
        opts.add("Löschen"); ids.add(3);
        opts.add("Abbrechen"); ids.add(-1);

        String lnk = linkLabelOf(c);
        new AlertDialog.Builder(this)
                .setTitle(c.name + " (" + labelOf(c.tag) + (lnk.isEmpty() ? "" : " · → " + lnk) + ")")
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    switch (ids.get(which)) {
                        case 0: rename(c); break;
                        case 1: view.startLink(c); break;
                        case 2: c.crane = !c.crane; view.invalidate(); break;
                        case 4: showDelayDialog(c); break;
                        case 5: showThrDialog(c); break;
                        case 6:
                            c.link = "";
                            invalidate();
                            break;
                        case 7: linkPick(c); break;
                        case 3:
                            hidePortBar();
                            view.circuit.removeComp(c);
                            view.selectNothing();
                            view.invalidate();
                            break;
                    }
                }).show();
    }

    /** Klartext der Verknüpfung (inkl. Anschluss bei Druckschaltern). */
    String linkLabelOf(Circuit.Comp c) {
        if (c.link == null || c.link.isEmpty()) return "";
        String id = c.link, port = "";
        int ix = id.indexOf(':');
        if (ix >= 0) { port = id.substring(ix + 1); id = id.substring(0, ix); }
        Circuit.Comp t = Circuit.findComp(view.circuit.comps, id);
        if (t == null) return c.link;
        return t.name + (port.isEmpty() ? "" : " · Anschl. " + port);
    }

    static String delayLabel(float s) {
        if (s == 0.5f) return "0,5 s";
        return (s == (int) s ? String.valueOf((int) s) : String.valueOf(s)) + " s";
    }

    void showDelayDialog(final Circuit.Comp c) {
        final float[] vals = {0.5f, 1f, 2f, 5f, 10f};
        new AlertDialog.Builder(this)
                .setTitle("Einschaltverzögerung")
                .setItems(new String[]{"0,5 s", "1 s", "2 s", "5 s", "10 s"}, (d, w) -> {
                    c.delaySec = vals[w];
                    invalidate();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    void showThrDialog(final Circuit.Comp c) {
        final float[] vals = {0.25f, 0.5f, 0.75f};
        new AlertDialog.Builder(this)
                .setTitle("Auslöseschwelle des Rollenhebels (Zylinderposition)")
                .setItems(new String[]{"25 %", "50 %", "75 %"}, (d, w) -> {
                    c.thr = vals[w];
                    invalidate();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    @Override
    public void onWireMenu(final Circuit.Wire w) {
        new AlertDialog.Builder(this)
                .setTitle("Leitung")
                .setItems(new String[]{"Löschen", "Abbrechen"}, (d, which) -> {
                    if (which == 0) {
                        view.circuit.removeWire(w);
                        invalidate();
                    }
                }).show();
    }

    @Override
    public void onToast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPswPortPick(Circuit.Comp target, Circuit.Comp psw) {
        showPortBar(target, psw);
    }

    @Override
    public void onLinkEnded() {
        hidePortBar();
    }

    void invalidate() { view.invalidate(); }

    void renamePick() {
        final List<Circuit.Comp> cs = selectable();
        if (cs.isEmpty()) { toast("Keine Bauteile"); return; }
        String[] names = new String[cs.size()];
        for (int i = 0; i < cs.size(); i++) names[i] = cs.get(i).name + " (" + labelOf(cs.get(i).tag) + ")";
        new AlertDialog.Builder(this)
                .setTitle("Bauteil wählen")
                .setItems(names, (d, w) -> rename(cs.get(w))).show();
    }

    void deletePick() {
        final List<Circuit.Comp> cs = selectable();
        if (cs.isEmpty()) { toast("Keine Bauteile"); return; }
        String[] names = new String[cs.size()];
        for (int i = 0; i < cs.size(); i++) names[i] = cs.get(i).name + " (" + labelOf(cs.get(i).tag) + ")";
        new AlertDialog.Builder(this)
                .setTitle("Bauteil löschen")
                .setItems(names, (d, w) -> {
                    view.circuit.removeComp(cs.get(w));
                    view.selectNothing();
                    invalidate();
                }).show();
    }

    List<Circuit.Comp> selectable() {
        List<Circuit.Comp> cs = new ArrayList<>();
        for (Circuit.Comp c : view.circuit.comps) if (!isRail(c)) cs.add(c);
        Collections.reverse(cs); // zuletzt platzierte zuerst
        return cs;
    }

    static boolean isRail(Circuit.Comp c) {
        return c.tag.equals(Circuit.RAIL_L) || c.tag.equals(Circuit.RAIL_R) || c.tag.equals(Circuit.RAIL3);
    }

    void rename(final Circuit.Comp c) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(c.name);
        new AlertDialog.Builder(this)
                .setTitle("Bezeichnung")
                .setView(et)
                .setPositiveButton("OK", (d, w) -> {
                    c.name = et.getText().toString().trim();
                    view.invalidate();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    void linkPick(final Circuit.Comp c) {
        if (c.tag.equals(Circuit.PSW)) { pswLinkPick(c); return; }
        List<String> labels = new ArrayList<>();
        final List<Circuit.Comp> targets = new ArrayList<>();
        for (Circuit.Comp o : view.circuit.comps) {
            if (isRail(o) || o.id.equals(c.id)) continue;
            boolean ok = false;
            if (c.tag.equals(Circuit.SOLENOID))
                ok = o.tag.equals(Circuit.VALVE32) || o.tag.equals(Circuit.VALVE32R)
                        || o.tag.equals(Circuit.VALVE52M) || o.tag.equals(Circuit.VALVE52B);
            else if (c.tag.equals(Circuit.VALVE32R))
                ok = o.tag.equals(Circuit.CYL) || o.tag.equals(Circuit.CYL_S);
            else
                ok = o.tag.equals(Circuit.COIL) || o.tag.equals(Circuit.LAMP)
                        || o.tag.equals(Circuit.SOLENOID) || o.tag.equals(Circuit.TIMER)
                        || o.tag.equals(Circuit.CONTACTOR);
            if (ok) { targets.add(o); labels.add(o.name + " (" + labelOf(o.tag) + ")"); }
        }
        labels.add("(keine)");
        targets.add(null);
        new AlertDialog.Builder(this)
                .setTitle("Verknüpfen mit")
                .setItems(labels.toArray(new String[0]), (d, w) -> {
                    Circuit.Comp t = targets.get(w);
                    c.link = t == null ? "" : t.id;
                    if (t != null && c.tag.equals(Circuit.CONTACT)) c.name = t.name;
                    view.invalidate();
                }).show();
    }

    /** Druckschalter verknüpfen: pneumatisches Bauteil + Anschluss (z. B. 1V1 · Anschluss 2). */
    void pswLinkPick(final Circuit.Comp c) {
        List<String> labels = new ArrayList<>();
        final List<String> links = new ArrayList<>();
        for (Circuit.Comp o : view.circuit.comps) {
            if (!Circuit.isPneu(o.tag)) continue;
            for (Circuit.Pt p : Circuit.portsOf(o)) {
                if (!"p".equals(p.kind)) continue;
                links.add(o.id + ":" + p.label);
                labels.add(o.name + " · Anschluss " + p.label + " (" + labelOf(o.tag) + ")");
            }
        }
        labels.add("(keine)");
        links.add("");
        new AlertDialog.Builder(this)
                .setTitle("Druckschalter überwacht Anschluss…")
                .setItems(labels.toArray(new String[0]), (d, w) -> {
                    c.link = links.get(w);
                    invalidate();
                }).show();
    }

    // ------------------------------------------------------------------ Port-Leiste (Druckschalter)

    /** Ziel wurde angetippt: Mini-Leiste mit dessen p-Anschlüssen zeigen. */
    void showPortBar(final Circuit.Comp target, final Circuit.Comp psw) {
        portBar.removeAllViews();
        TextView hint = new TextView(this);
        hint.setText(psw.name + " → " + target.name + " · Anschluss:");
        hint.setTextColor(0xFF004D40);
        hint.setTextSize(12);
        hint.setPadding(dp(4), 0, dp(4), 0);
        portBar.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        boolean any = false;
        for (Circuit.Pt p : Circuit.portsOf(target)) {
            if (!"p".equals(p.kind)) continue;
            any = true;
            portBar.addView(portChip(p.label, () -> {
                psw.link = target.id + ":" + p.label;
                view.cancelLink();
                hidePortBar();
                view.invalidate();
                toast("Verknüpft: " + psw.name + " → " + target.name + " · Anschl. " + p.label);
            }));
        }
        portBar.addView(portChip("✕", () -> {
            view.cancelLink();
            hidePortBar();
            view.invalidate();
            toast("Verknüpfen abgebrochen");
        }));
        if (!any) toast(target.name + " hat keine pneumatischen Anschlüsse");
        if (portBar.getParent() == null) {
            int ix = rootRef.indexOfChild(view);
            rootRef.addView(portBar, ix < 0 ? rootRef.getChildCount() : ix,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    void hidePortBar() {
        if (portBar != null && portBar.getParent() instanceof ViewGroup)
            ((ViewGroup) portBar.getParent()).removeView(portBar);
    }

    Button portChip(String label, Runnable act) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(0xFF004D40);
        b.setBackground(rounded(0xFFB2DFDB));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(32));
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> act.run());
        return b;
    }

    String labelOf(String tag) {
        switch (tag) {
            case Circuit.CONTACT: return "Kontakt Schließer";
            case Circuit.CONTACT_NC: return "Kontakt Öffner";
            case Circuit.SWITCH: return "Schalter";
            case Circuit.PUSHBTN: return "Taster";
            case Circuit.PUSHBTN_NC: return "Taster Öffner";
            case Circuit.COIL: return "Spule";
            case Circuit.LAMP: return "Melder";
            case Circuit.SOLENOID: return "Magnetspule";
            case Circuit.CONTACTOR: return "Schütz";
            case Circuit.BIMET: return "Motorschutzschalter";
            case Circuit.TIMER: return "Zeitrelais";
            case Circuit.PSW: return "Druckschalter";
            case Circuit.MOTOR: return "Motor 3~";
            case Circuit.MOTOR6: return "Motor 3~ (6 Klemmen)";
            case Circuit.SUPPLY: return "Druckluft";
            case Circuit.VALVE32: return "3/2-Ventil";
            case Circuit.VALVE32R: return "3/2-Ventil Rollenhebel";
            case Circuit.VALVE52M: return "5/2 monostabil";
            case Circuit.VALVE52B: return "5/2 bistabil";
            case Circuit.CYL: return "Zylinder (doppel)";
            case Circuit.CYL_S: return "Zylinder (einfach)";
            case Circuit.RAIL_L: return "Schiene L+";
            case Circuit.RAIL_R: return "Schiene 0";
            case Circuit.RAIL3: return "Schiene L1/2/3";
        }
        return tag;
    }

    // ------------------------------------------------------------------ Beispiele/Öffnen/Speichern

    void confirmNew() {
        new AlertDialog.Builder(this)
                .setTitle("Neue Schaltung")
                .setMessage("Aktuelle Ansicht verwerfen?")
                .setPositiveButton("Neue Schaltung", (d, w) -> install(Examples.newEmpty()))
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    void showExamples() {
        final List<Examples.Ex> exs = Examples.all();
        String[] names = new String[exs.size()];
        for (int i = 0; i < exs.size(); i++) names[i] = exs.get(i).title;
        new AlertDialog.Builder(this)
                .setTitle("Beispiele")
                .setItems(names, (d, w) -> install(exs.get(w).circuit))
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    File circuitsDir() {
        File f = new File(getFilesDir(), "circuits");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    void showOpen() {
        File dir = circuitsDir();
        File[] files = dir.listFiles();
        List<String> names = new ArrayList<>();
        List<File> paths = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.getName().endsWith(".json") && !f.getName().equals("last.json")) {
                    names.add(f.getName().substring(0, f.getName().length() - 5));
                    paths.add(f);
                }
            }
        }
        Collections.sort(names);
        if (names.isEmpty()) { toast("Noch keine gespeicherten Schaltungen"); return; }
        new AlertDialog.Builder(this)
                .setTitle("Öffnen")
                .setItems(names.toArray(new String[0]), (d, w) -> {
                    Circuit c = loadFromFile(paths.get(w));
                    if (c != null) install(c);
                    else toast("Datei konnte nicht gelesen werden");
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    void showSave() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(view.circuit.name.equals("Unbenannt") ? "" : view.circuit.name);
        et.setHint("Name der Schaltung");
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(18), dp(8), dp(18), 0);
        wrap.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Speichern unter…")
                .setView(wrap)
                .setPositiveButton("Speichern", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) { toast("Name fehlt"); return; }
                    n = n.replaceAll("[\\\\/:*?\"<>|]", "_");
                    view.circuit.name = n;
                    File f = new File(circuitsDir(), n + ".json");
                    storeToFile(f, view.circuit);
                    setTitle("SchaltStudio — " + n);
                    toast("Gespeichert: " + n);
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ------------------------------------------------------------------ IO helpers

    boolean storeToFile(File f, Circuit c) {
        try {
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(c.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
            fo.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    Circuit loadFromFile(File f) {
        try {
            FileInputStream fi = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            fi.read(buf);
            fi.close();
            JSONObject o = new JSONObject(new String(buf, StandardCharsets.UTF_8));
            return Circuit.fromJson(o);
        } catch (Exception e) {
            return null;
        }
    }
}
