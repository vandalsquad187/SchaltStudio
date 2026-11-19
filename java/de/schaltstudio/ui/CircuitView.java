package de.schaltstudio.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import de.schaltstudio.core.Circuit;
import de.schaltstudio.core.Engine;

/** Zeichenfläche: Schaltplan-Editor (Raster, Bauteile, Leitungen) + Live-Simulation. */
public class CircuitView extends View {

    public interface Listener {
        void onCompMenu(Circuit.Comp c);
        void onWireMenu(Circuit.Wire w);
        void onToast(String msg);
        void onPswPortPick(Circuit.Comp target, Circuit.Comp psw);
        void onLinkEnded();
    }

    static final float STEP = 30f;
    static final float MIN_SC = 0.3f, MAX_SC = 4f;

    static final int INK = 0xFF263238, LABEL = 0xFF455A64, PAPER = 0xFFFCFCFA, BLUE = 0xFF1976D2;
    static final int WIRE_IDLE_E = 0xFFB0BEC5, WIRE_LIVE = 0xFFD32F2F, WIRE_RET = 0xFFFB8C00;
    static final int WIRE_IDLE_P = 0xFF607D8B, WIRE_PRESS = 0xFFC62828, WIRE_VENT = 0xFF90A4AE;

    public Circuit circuit;
    public Engine engine;
    Listener listener;
    final Handler handler = new Handler();

    float sc = 1f, ox = -40f, oy = 0f;

    // ---- Eingabe-Zustand ----
    String insertTag = null;
    String insertColor = null;
    String selCompId = null;

    boolean wireActive = false;
    int wireKind = 0;                              // 0 elektrisch, 1 pneumatisch
    final List<Integer> wireAnchors = new ArrayList<>();
    float wireCursorX, wireCursorY;

    Circuit.Comp moveComp;
    final List<int[]> moveSnap = new ArrayList<>();
    float moveGrabGX, moveGrabGY;
    Circuit.Comp pressComp;
    boolean movedFar = false, lpFired = false;

    int secondPointer = -1;
    float pinchDistBase = 1, pinchCx, pinchCy, pinchSc, pinchOx, pinchOy;
    float downX, downY, lastX, lastY;
    long downAt;

    // ---- Verknüpf-Modus (Ziel antippen statt Liste) ----
    public static final int LINK_CONTACT = 1, LINK_SOLENOID = 2, LINK_ROLLER = 3, LINK_PSW = 4;
    int linkKind = 0;
    Circuit.Comp linkSrc = null;

    final Paint pInk = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint pGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint pSel = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint pTmp = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint pPulse = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint pLink = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CircuitView(Context ctx) {
        super(ctx);
        pGrid.setColor(0xFFE4E7E4);
        pGrid.setStrokeWidth(1);
        pSel.setStyle(Paint.Style.STROKE);
        pSel.setStrokeWidth(px(2.5f));
        pSel.setColor(0xFF1E88E5);
        pSel.setPathEffect(new DashPathEffect(new float[]{px(7), px(5)}, 0));
        pTmp.setStyle(Paint.Style.STROKE);
        pTmp.setStrokeWidth(px(3));
        pTmp.setPathEffect(new DashPathEffect(new float[]{px(9), px(6)}, 0));
        pText.setColor(LABEL);
        pPulse.setStyle(Paint.Style.STROKE);
        pPulse.setStrokeWidth(px(3f));
        pLink.setStyle(Paint.Style.STROKE);
        pLink.setStrokeWidth(px(2f));
        pLink.setColor(0xFF7E57C2);
        pLink.setAlpha(190);
        pLink.setPathEffect(new DashPathEffect(new float[]{px(6), px(5)}, 0));
    }

    public void setListener(Listener l) { listener = l; }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.postDelayed(pulseAnim, 300);
    }

    /** Blitzt pulsierende Markierungen: Link-Modus-Ziele, unverknüpfte Kontakte. */
    final Runnable pulseAnim = new Runnable() {
        @Override public void run() {
            if (circuit == null) return;
            boolean need = linkSrc != null;
            if (!need && (engine == null || !engine.running)) {
                for (Circuit.Comp cp : circuit.comps) {
                    if ((cp.tag.equals(Circuit.CONTACT) || cp.tag.equals(Circuit.CONTACT_NC))
                            && (cp.link == null || cp.link.isEmpty())) { need = true; break; }
                }
            }
            if (!need) return;
            invalidate();
            handler.postDelayed(this, 300);
        }
    };

    float px(float d) { return d * getResources().getDisplayMetrics().density; }
    float stepPx() { return STEP * sc; }
    float sx(float g) { return ox + g * stepPx(); }
    float sy(float g) { return oy + g * stepPx(); }
    int gxs(float screenX) { return Math.round((screenX - ox) / stepPx()); }
    int gys(float screenY) { return Math.round((screenY - oy) / stepPx()); }

    static boolean isRail(String t) {
        return t.equals(Circuit.RAIL_L) || t.equals(Circuit.RAIL_R) || t.equals(Circuit.RAIL3);
    }

    public void refit() {
        float minX = 0, minY = 0, maxX = 40, maxY = 20;
        for (Circuit.Comp c : circuit.comps) {
            float[] b = compBBox(c);
            minX = Math.min(minX, b[0]); maxX = Math.max(maxX, b[2]);
            minY = Math.min(minY, b[1]); maxY = Math.max(maxY, b[3]);
        }
        float wpx = (maxX - minX) * STEP, hpx = (maxY - minY) * STEP;
        if (wpx < 1 || hpx < 1) return;
        sc = Math.max(MIN_SC, Math.min(2.0f,
                Math.min((getWidth() - px(40)) / wpx, (getHeight() - px(150)) / hpx)));
        ox = (getWidth() - wpx * sc) / 2f - minX * STEP * sc;
        oy = (getHeight() - hpx * sc) / 2f - minY * STEP * sc;
        invalidate();
    }

    // ================================================================ Zeichnen

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(PAPER);
        cv.drawRect(0, 0, w, h, pFill);

        float gp = stepPx();
        if (gp > 9) {
            float ax = ((ox % gp) + gp) % gp, ay = ((oy % gp) + gp) % gp;
            for (float x = ax; x < w; x += gp)
                for (float y = ay; y < h; y += gp)
                    cv.drawCircle(x, y, 1.3f, pGrid);
        }

        for (Circuit.Comp c : circuit.comps) {
            if (c.tag.equals(Circuit.RAIL3)) drawRail3(cv, c);
            else if (isRail(c.tag)) drawRail(cv, c);
        }
        for (Circuit.Wire wr : circuit.wires) drawWire(cv, wr);
        if (wireActive) drawTempWire(cv);
        drawLinkLines(cv);

        for (Circuit.Comp c : circuit.comps) {
            if (isRail(c.tag)) continue;
            drawComp(cv, c);
            if (c.id.equals(selCompId)) {
                float[] b = compBBox(c);
                cv.drawRect(sx(b[0]), sy(b[1]), sx(b[2]), sy(b[3]), pSel);
            }
        }
        drawLinkHighlights(cv);

        pText.setTextSize(px(14));
        if (insertTag != null) {
            pText.setColor(BLUE);
            cv.drawText("Bauteil platzieren: auf ein Rasterfeld tippen", px(12), h - px(88), pText);
        } else if (wireActive) {
            pText.setColor(wireKind == 0 ? WIRE_LIVE : BLUE);
            cv.drawText("Leitung: für Ecken im Raster tippen · auf Port/Schiene tippen = fertig", px(10), h - px(66), pText);
        } else if (linkSrc != null) {
            pText.setColor(BLUE);
            cv.drawText(linkHint(), px(12), h - px(66), pText);
        }
        if (engine != null && engine.running) {
            pText.setColor(0xFFB71C1C);
            cv.drawText("● Simulation — Taster halten, Schalter antippen", px(12), px(24), pText);
        }
    }

    void drawWire(Canvas cv, Circuit.Wire w) {
        Paint p = new Paint(pInk);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        boolean running = engine != null && engine.running;
        if (w.electric) {
            p.setStrokeWidth(px(3.2f));
            if (running && w.hot) p.setColor(WIRE_LIVE);
            else if (running && w.ret) p.setColor(WIRE_RET);
            else p.setColor(WIRE_IDLE_E);
        } else {
            p.setStrokeWidth(px(3.6f));
            if (running && w.hot) p.setColor(WIRE_PRESS);
            else if (running && w.vent) { p.setColor(WIRE_VENT); p.setAlpha(230); }
            else p.setColor(WIRE_IDLE_P);
        }
        cv.drawPath(wirePath(w.pts), p);
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(p.getColor());
        for (int i = 0; i + 3 < w.pts.length; i += 2)
            cv.drawCircle(sx(w.pts[i]), sy(w.pts[i + 1]), px(2.6f), pFill);
    }

    Path wirePath(int[] pts) {
        Path path = new Path();
        path.moveTo(sx(pts[0]), sy(pts[1]));
        for (int i = 2; i + 1 < pts.length; i += 2) path.lineTo(sx(pts[i]), sy(pts[i + 1]));
        return path;
    }

    void drawTempWire(Canvas cv) {
        int n = wireAnchors.size();
        if (n < 2) return;
        int lx = wireAnchors.get(n - 2), ly = wireAnchors.get(n - 1);
        int cx = gxs(wireCursorX), cy = gys(wireCursorY);
        List<Integer> list = new ArrayList<>(wireAnchors);
        if (cx != lx && cy != ly) { list.add(cx); list.add(ly); }
        list.add(cx); list.add(cy);
        int[] pts = new int[list.size()];
        for (int i = 0; i < list.size(); i++) pts[i] = list.get(i);
        Paint p = new Paint(pTmp);
        p.setColor(wireKind == 0 ? WIRE_LIVE : BLUE);
        p.setStyle(Paint.Style.STROKE);
        cv.drawPath(wirePath(pts), p);
    }

    void drawRail(Canvas cv, Circuit.Comp c) {
        float x = sx(c.x);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setColor(0xFF37474F);
        pInk.setStrokeWidth(px(4));
        cv.drawLine(x, 0, x, getHeight(), pInk);
        pText.setColor(0xFFB71C1C);
        pText.setTextSize(px(14));
        String n = c.name != null ? c.name : "";
        if (n.isEmpty()) n = Circuit.RAIL_L.equals(c.tag) ? "L+" : "0";
        cv.drawText(n, x < getWidth() / 2 ? x + px(6) : x - px(30), px(20), pText);
    }

    void drawRail3(Canvas cv, Circuit.Comp c) {
        float x = sx(c.x);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setColor(0xFF37474F);
        pInk.setStrokeWidth(px(2.2f));
        cv.drawLine(x - px(5), 0, x - px(5), getHeight(), pInk);
        cv.drawLine(x + px(5), 0, x + px(5), getHeight(), pInk);
        pInk.setStrokeWidth(px(3.4f));
        cv.drawLine(x, 0, x, getHeight(), pInk);
        pText.setColor(0xFFB71C1C);
        pText.setTextSize(px(13));
        String n = c.name != null && !c.name.isEmpty() ? c.name : "L1 L2 L3";
        cv.drawText(n, x < getWidth() / 2 ? x + px(9) : x - px(58), px(20), pText);
    }

    float[] compBBox(Circuit.Comp c) {
        switch (c.tag) {
            case Circuit.CONTACT: case Circuit.CONTACT_NC: case Circuit.SWITCH:
            case Circuit.PUSHBTN: case Circuit.PUSHBTN_NC:
            case Circuit.COIL: case Circuit.LAMP: case Circuit.SOLENOID:
            case Circuit.TIMER: case Circuit.PSW:
                return new float[]{c.x - 0.4f, c.y - 1.55f, c.x + 3.4f, c.y + 0.95f};
            case Circuit.VALVE32R:
                return new float[]{c.x - 0.9f, c.y - 1.15f, c.x + 2.2f, c.y + 1.45f};
            case Circuit.MOTOR:
                return new float[]{c.x - 0.7f, c.y - 0.55f, c.x + 3.4f, c.y + 2.95f};
            case Circuit.MOTOR6:
                return new float[]{c.x - 0.8f, c.y - 0.55f, c.x + 3.9f, c.y + 2.95f};
            case Circuit.BIMET:
                return new float[]{c.x - 0.8f, c.y - 0.55f, c.x + 3.9f, c.y + 4.95f};
            case Circuit.CONTACTOR:
                return new float[]{c.x - 0.8f, c.y - 0.55f, c.x + 3.4f, c.y + 4.95f};
            case Circuit.SUPPLY:
                return new float[]{c.x - 0.2f, c.y - 0.2f, c.x + 2.2f, c.y + 2.2f};
            case Circuit.VALVE32:
                return new float[]{c.x - 0.2f, c.y - 0.9f, c.x + 2.2f, c.y + 1.45f};
            case Circuit.VALVE52M: case Circuit.VALVE52B:
                return new float[]{c.x - 0.2f, c.y - 0.9f, c.x + 4.2f, c.y + 1.45f};
            case Circuit.CYL:
                return new float[]{c.x - 0.2f, c.y - 0.4f, c.x + 11.8f, c.y + 1.45f};
            case Circuit.CYL_S:
                return new float[]{c.x - 0.2f, c.y - 0.4f, c.x + 7.8f, c.y + 1.45f};
        }
        return new float[]{c.x - 1, c.y - 1, c.x + 1, c.y + 1};
    }

    void drawComp(Canvas cv, Circuit.Comp c) {
        switch (c.tag) {
            case Circuit.CONTACT: drawContact(cv, c, false, false, c.powered); break;
            case Circuit.CONTACT_NC: drawContact(cv, c, true, false, c.powered); break;
            case Circuit.SWITCH: drawContact(cv, c, false, true, c.sw); break;
            case Circuit.PUSHBTN: drawButtonContact(cv, c, false, c.man); break;
            case Circuit.PUSHBTN_NC: drawButtonContact(cv, c, true, c.man); break;
            case Circuit.COIL: drawCoil(cv, c, c.powered, false); break;
            case Circuit.SOLENOID: drawCoil(cv, c, c.powered, true); break;
            case Circuit.LAMP: drawLamp(cv, c); break;
            case Circuit.SUPPLY: drawSupply(cv, c); break;
            case Circuit.VALVE32: drawValve32(cv, c); break;
            case Circuit.VALVE32R: drawValve32R(cv, c); break;
            case Circuit.VALVE52M: case Circuit.VALVE52B: drawValve52(cv, c); break;
            case Circuit.CYL: drawCylinder(cv, c, false); break;
            case Circuit.CYL_S: drawCylinder(cv, c, true); break;
            case Circuit.TIMER: drawTimer(cv, c); break;
            case Circuit.PSW: drawPSW(cv, c); break;
            case Circuit.MOTOR: drawMotor(cv, c); break;
            case Circuit.MOTOR6: drawMotor6(cv, c); break;
            case Circuit.BIMET: drawBimet(cv, c); break;
            case Circuit.CONTACTOR: drawContactor(cv, c); break;
        }
        drawLabel(cv, c);
    }

    void drawLabel(Canvas cv, Circuit.Comp c) {
        String shown = c.name;
        if ((shown == null || shown.isEmpty()) && c.link != null && !c.link.isEmpty()) {
            Circuit.Comp lnk = Circuit.findComp(circuit.comps, c.link);
            if (lnk != null) shown = lnk.name;
        }
        if (shown == null || shown.isEmpty()) return;
        pText.setColor(LABEL);
        pText.setTextSize(px(14));
        float[] b = compBBox(c);
        float tx = sx(b[0]);
        float ty = sy(b[1]) - px(4);
        pText.setTextAlign(Paint.Align.LEFT);
        if (b[1] < 1.6f) { // knapp am oberen Rand: Bezeichnung unter das Bauteil
            pText.setTextAlign(Paint.Align.CENTER);
            tx = sx(b[0] + (b[2] - b[0]) / 2);
            ty = sy(b[3]) + px(14);
        }
        cv.drawText(shown, tx, ty, pText);
        pText.setTextAlign(Paint.Align.LEFT);
        if ((c.tag.equals(Circuit.CONTACT) || c.tag.equals(Circuit.CONTACT_NC))
                && (c.link == null || c.link.isEmpty()) && engine != null && !engine.running) {
            pText.setColor(0xFFEF6C00);
            pText.setTextSize(px(10));
            cv.drawText("→ Spule verknüpfen!", tx, ty + px(13), pText);
        }
    }

    void stubDown(Canvas cv, float gx, float gy) {
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setColor(INK);
        pInk.setStrokeWidth(px(2.6f));
        cv.drawLine(sx(gx), sy(gy), sx(gx), sy(gy + 0.35f), pInk);
    }

    void stubUp(Canvas cv, float gx, float gy) {
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setColor(INK);
        pInk.setStrokeWidth(px(2.6f));
        cv.drawLine(sx(gx), sy(gy), sx(gx), sy(gy - 0.35f), pInk);
    }

    void drawContact(Canvas cv, Circuit.Comp c, boolean nc, boolean switchKind, boolean closed) {
        drawContactAt(cv, c, c.y, nc, switchKind, closed);
    }

    void drawContactAt(Canvas cv, Circuit.Comp c, float y, boolean nc, boolean switchKind, boolean closed) {
        stubDown(cv, c.x, y); stubDown(cv, c.x + 3, y);
        pInk.setColor(INK);
        pInk.setStrokeWidth(px(2.6f));
        pInk.setStyle(Paint.Style.STROKE);
        cv.drawLine(sx(c.x), sy(y), sx(c.x), sy(y - 0.35f), pInk);
        cv.drawLine(sx(c.x + 3), sy(y), sx(c.x + 3), sy(y - 0.35f), pInk);
        pInk.setStrokeWidth(px(3.4f));
        pInk.setColor(closed ? 0xFFB71C1C : INK);
        if (closed) cv.drawLine(sx(c.x), sy(y), sx(c.x + 3), sy(y), pInk);
        else cv.drawLine(sx(c.x), sy(y), sx(c.x + 3), sy(y - 0.95f), pInk);
        pInk.setColor(INK);
        if (nc) {
            pInk.setStrokeWidth(px(3.6f));
            cv.drawLine(sx(c.x + 2.1f), sy(y - 0.6f), sx(c.x + 2.1f), sy(y + 0.2f), pInk);
        }
        if (switchKind) dot(cv, c.x, y, 2.6f, closed ? 0xFFB71C1C : INK);
    }

    void drawButtonContact(Canvas cv, Circuit.Comp c, boolean nc, boolean pressed) {
        drawContact(cv, c, nc, false, nc ? !pressed : pressed);
        float mx = c.x + 1.5f;
        pInk.setColor(INK);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setStrokeWidth(px(2.8f));
        cv.drawLine(sx(mx), sy(c.y - 0.85f), sx(mx), sy(c.y - 0.3f), pInk);
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(pressed ? 0xFFE53935 : 0xFFECEFF1);
        cv.drawCircle(sx(mx), sy(c.y - 1.12f), px(5.5f), pFill);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setStrokeWidth(px(2f));
        cv.drawCircle(sx(mx), sy(c.y - 1.12f), px(5.5f), pInk);
    }

    void drawCoil(Canvas cv, Circuit.Comp c, boolean powered, boolean magnet) {
        stubDown(cv, c.x, c.y); stubDown(cv, c.x + 3, c.y);
        pInk.setColor(INK);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setStrokeWidth(px(2.6f));
        cv.drawLine(sx(c.x), sy(c.y), sx(c.x), sy(c.y - 0.35f), pInk);
        cv.drawLine(sx(c.x + 3), sy(c.y), sx(c.x + 3), sy(c.y - 0.35f), pInk);
        float l = sx(c.x + 0.7f), r = sx(c.x + 2.3f), t = sy(c.y - 0.45f), b = sy(c.y + 0.45f);
        if (powered) {
            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(0xFFFFCDD2);
            cv.drawRect(l, t, r, b, pFill);
        }
        pInk.setColor(INK);
        pInk.setStrokeWidth(px(3f));
        cv.drawRect(l, t, r, b, pInk);
        if (magnet) {
            pInk.setStrokeWidth(px(2f));
            cv.drawLine(l, b, r, t, pInk);
        }
    }

    void drawLamp(Canvas cv, Circuit.Comp c) {
        stubDown(cv, c.x, c.y); stubDown(cv, c.x + 3, c.y);
        pInk.setColor(INK);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setStrokeWidth(px(2.6f));
        cv.drawLine(sx(c.x), sy(c.y), sx(c.x), sy(c.y - 0.35f), pInk);
        cv.drawLine(sx(c.x + 3), sy(c.y), sx(c.x + 3), sy(c.y - 0.35f), pInk);
        float cx = sx(c.x + 1.5f), cy = sy(c.y), r = px(11);
        pFill.setStyle(Paint.Style.FILL);
        if (c.powered) {
            pFill.setColor(c.crane ? 0xFFFF8A80 : 0xFF66BB6A);
            pFill.setAlpha(70);
            cv.drawCircle(cx, cy, r * 1.8f, pFill);
            pFill.setAlpha(255);
            pFill.setColor(0xFFFDD835);
        } else {
            pFill.setColor(0xFFFFFFFF);
        }
        cv.drawCircle(cx, cy, r, pFill);
        pInk.setColor(INK);
        pInk.setStrokeWidth(px(2.8f));
        cv.drawCircle(cx, cy, r, pInk);
        cv.drawLine(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f, pInk);
        cv.drawLine(cx - r * 0.7f, cy + r * 0.7f, cx + r * 0.7f, cy - r * 0.7f, pInk);
    }

    void drawSupply(Canvas cv, Circuit.Comp c) {
        float x0 = sx(c.x), y0 = sy(c.y), w = stepPx() * 2, h = stepPx() * 2;
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFFE3F2FD);
        cv.drawRect(x0, y0, x0 + w, y0 + h, pFill);
        pInk.setColor(0xFF0D47A1);
        pInk.setStrokeWidth(px(3f));
        pInk.setStyle(Paint.Style.STROKE);
        cv.drawRect(x0, y0, x0 + w, y0 + h, pInk);
        float cx = x0 + w / 2, cy = y0 + h / 2;
        Path t = new Path();
        t.moveTo(cx - stepPx() * 0.55f, cy - stepPx() * 0.35f);
        t.lineTo(cx - stepPx() * 0.55f, cy + stepPx() * 0.35f);
        t.lineTo(cx + stepPx() * 0.55f, cy);
        t.close();
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFF1565C0);
        cv.drawPath(t, pFill);
    }

    void drawValve32(Canvas cv, Circuit.Comp c) {
        float x0 = sx(c.x), y0 = sy(c.y), w = stepPx() * 2, h = stepPx();
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(c.man ? 0xFFB2DFDB : 0xFFE8F5E9);
        cv.drawRect(x0, y0, x0 + w, y0 + h, pFill);
        pInk.setColor(0xFF1B5E20);
        pInk.setStrokeWidth(px(3f));
        pInk.setStyle(Paint.Style.STROKE);
        cv.drawRect(x0, y0, x0 + w, y0 + h, pInk);
        stubDown(cv, c.x, c.y + 1); stubDown(cv, c.x + 2, c.y + 1); stubUp(cv, c.x + 1, c.y);
        pInk.setColor(0xFF1B5E20);
        pInk.setStrokeWidth(px(2.6f));
        if (c.man) {
            arrow(cv, x0 + stepPx() * 0.2f, y0 + h * 0.55f, x0 + w - stepPx() * 0.2f, y0 + h * 0.55f);
        } else {
            arrow(cv, x0 + w - stepPx() * 0.25f, y0 + h * 0.82f, x0 + w - stepPx() * 0.25f, y0 - stepPx() * 0.15f);
        }
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(c.man ? 0xFFE53935 : 0xFFECEFF1);
        cv.drawCircle(x0 + stepPx() * 0.25f, y0 - stepPx() * 0.45f, px(6), pFill);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setStrokeWidth(px(2f));
        cv.drawCircle(x0 + stepPx() * 0.25f, y0 - stepPx() * 0.45f, px(6), pInk);
        portLabel(cv, "3", c.x + 1.35f, c.y - 0.02f);
        portLabel(cv, "1", c.x - 0.12f, c.y + 1.5f);
        portLabel(cv, "2", c.x + 2.12f, c.y + 1.5f);
    }

    void drawValve52(Canvas cv, Circuit.Comp c) {
        boolean bistable = c.tag.equals(Circuit.VALVE52B);
        float x0 = sx(c.x), y0 = sy(c.y), w = stepPx() * 4, h = stepPx(), boxW = w / 2;
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFFE8F5E9);
        cv.drawRect(x0, y0, x0 + w, y0 + h, pFill);
        if (engine != null && engine.running) {
            pFill.setColor(0xFF66BB6A);
            pFill.setAlpha(60);
            if (!c.state0) cv.drawRect(x0 + boxW, y0, x0 + w, y0 + h, pFill);
            else cv.drawRect(x0, y0, x0 + boxW, y0 + h, pFill);
            pFill.setAlpha(255);
        }
        pInk.setColor(0xFF1B5E20);
        pInk.setStrokeWidth(px(3f));
        pInk.setStyle(Paint.Style.STROKE);
        cv.drawRect(x0, y0, x0 + w, y0 + h, pInk);
        cv.drawLine(x0 + boxW, y0, x0 + boxW, y0 + h, pInk);
        pInk.setColor(0xFF33691E);
        pInk.setStrokeWidth(px(2.2f));
        arrow(cv, x0 + stepPx() * 0.4f, y0 + h * 0.76f, x0 + stepPx() * 1.6f, y0 + h * 0.26f);
        arrow(cv, x0 + boxW + stepPx() * 1.6f, y0 + h * 0.76f, x0 + boxW + stepPx() * 0.4f, y0 + h * 0.26f);
        stubDown(cv, c.x + 2, c.y + 1); stubDown(cv, c.x + 1, c.y + 1); stubDown(cv, c.x + 3, c.y + 1);
        stubUp(cv, c.x + 1, c.y); stubUp(cv, c.x + 3, c.y);
        pInk.setColor(INK);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setStrokeWidth(px(2.6f));
        cv.drawLine(sx(c.x), sy(c.y), sx(c.x), sy(c.y - 0.4f), pInk);
        if (bistable) {
            cv.drawLine(sx(c.x + 4), sy(c.y), sx(c.x + 4), sy(c.y - 0.4f), pInk);
        } else {
            pInk.setStrokeWidth(px(2f));
            for (int i = 0; i < 5; i++) {
                float fy = sy(c.y - 0.05f - i * 0.11f);
                cv.drawLine(sx(c.x + 4f) - px(3), fy, sx(c.x + 4f) + px(3), fy + px(2.5f), pInk);
            }
        }
        if (engine != null && engine.running) {
            if (c.pilot12) dot(cv, c.x, c.y - 0.4f, 3.4f, 0xFFD32F2F);
            if (bistable && c.pilot14) dot(cv, c.x + 4, c.y - 0.4f, 3.4f, 0xFFD32F2F);
        }
        portLabel(cv, "12", c.x - 0.62f, c.y - 0.12f);
        portLabel(cv, "1", c.x + 2.08f, c.y + 1.5f);
        portLabel(cv, "3", c.x + 1.08f, c.y + 1.5f);
        portLabel(cv, "5", c.x + 3.08f, c.y + 1.5f);
        portLabel(cv, "2", c.x + 0.75f, c.y + 0.02f);
        portLabel(cv, "4", c.x + 3.75f, c.y + 0.02f);
    }

    void drawCylinder(Canvas cv, Circuit.Comp c, boolean single) {
        float x0 = sx(c.x), y0 = sy(c.y);
        int lenG = single ? 6 : 9;
        float bxl = x0, bxt = y0, bxr = x0 + stepPx() * lenG, byb = y0 + stepPx();
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFFF5F5F2);
        cv.drawRect(bxl, bxt, bxr, byb, pFill);
        pInk.setColor(INK);
        pInk.setStrokeWidth(px(3f));
        pInk.setStyle(Paint.Style.STROKE);
        cv.drawRect(bxl, bxt, bxr, byb, pInk);
        float faceG = (single ? 1.2f : 1.0f) + c.cyl * (single ? 3.2f : 5.4f);
        float fx = sx(c.x + faceG);
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFF455A64);
        cv.drawRect(fx - px(6), bxt + px(4), fx + px(6), byb - px(4), pFill);
        float rodTip = bxr + stepPx() * 1.8f + stepPx() * 1.0f * c.cyl;
        pInk.setColor(INK);
        pInk.setStrokeWidth(px(4f));
        pInk.setStyle(Paint.Style.STROKE);
        cv.drawLine(fx + px(6), y0 + stepPx() * 0.5f, rodTip, y0 + stepPx() * 0.5f, pInk);
        if (single) {
            pInk.setStrokeWidth(px(2.2f));
            float fx0 = sx(c.x + 0.15f);
            for (int i = 0; i < 5; i++) {
                float fy = sy(c.y + 0.08f + i * 0.17f);
                cv.drawLine(fx0, fy, fx0 + px(7), fy + px(2.5f), pInk);
            }
        }
        pInk.setColor(0xFF0D47A1);
        pInk.setStrokeWidth(px(2.6f));
        cv.drawLine(sx(c.x + 1), byb, sx(c.x + 1), byb + stepPx() * 0.35f, pInk);
        if (!single) cv.drawLine(sx(c.x + 8), byb, sx(c.x + 8), byb + stepPx() * 0.35f, pInk);
        portLabel(cv, "A", c.x + 1.06f, c.y + 1.52f);
        if (!single) portLabel(cv, "B", c.x + 8.06f, c.y + 1.52f);
        if (engine != null && engine.running && c.moveDir != 0) {
            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(c.moveDir > 0 ? 0xFF2E7D32 : 0xFF1565C0);
            float ax = c.moveDir > 0 ? rodTip + px(4) : bxl - px(16);
            Path tr = new Path();
            tr.moveTo(ax, y0 + stepPx() * 0.1f);
            tr.lineTo(ax + px(10), y0 + stepPx() * 0.5f);
            tr.lineTo(ax, y0 + stepPx() * 0.9f);
            cv.drawPath(tr, pFill);
        }
    }

    void drawTimer(Canvas cv, Circuit.Comp c) {
        drawCoil(cv, c, c.en, false);
        // DIN-Kennzeichen für Einschaltverzögerung: Halbkreis-Flag links im Kasten
        float l = sx(c.x + 0.7f), t = sy(c.y - 0.45f), b = sy(c.y + 0.45f);
        pInk.setColor(INK);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setStrokeWidth(px(2f));
        Path arc = new Path();
        float cy0 = (t + b) / 2, rr = (b - t) / 2 - px(2);
        arc.moveTo(l + px(3), cy0 - rr);
        arc.quadTo(l - px(5), cy0, l + px(3), cy0 + rr);
        cv.drawPath(arc, pInk);
        // Fortschrittsanzeige während der Verzögerung
        if (engine != null && engine.running && (c.tAccum > 0 || c.en)) {
            float total = c.delaySec * 1000f;
            float f = Math.max(0f, Math.min(1f, c.tAccum / total));
            if (c.en) f = 1f;
            pInk.setColor(0xFFD32F2F);
            pInk.setStrokeWidth(px(3f));
            cv.drawLine(sx(c.x + 0.2f), sy(c.y + 0.85f), sx(c.x + 0.2f + 2.6f * f), sy(c.y + 0.85f), pInk);
        }
    }

    void drawPSW(Canvas cv, Circuit.Comp c) {
        boolean closed = engine != null && engine.running && engine.pswClosed(c);
        drawContact(cv, c, false, false, closed);
        // Druck-Betätiger: schräge Linie + "P"
        pInk.setColor(INK);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setStrokeWidth(px(2.4f));
        cv.drawLine(sx(c.x + 1.45f), sy(c.y - 0.45f), sx(c.x + 2.0f), sy(c.y - 1.15f), pInk);
        pText.setColor(0xFF0D47A1);
        pText.setTextSize(px(11));
        cv.drawText("P", sx(c.x + 2.15f), sy(c.y - 0.9f), pText);
    }

    void drawValve32R(Canvas cv, Circuit.Comp c) {
        boolean pressed = c.sw;
        float x0 = sx(c.x), y0 = sy(c.y), w = stepPx() * 2, h = stepPx();
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(pressed ? 0xFFB2DFDB : 0xFFE8F5E9);
        cv.drawRect(x0, y0, x0 + w, y0 + h, pFill);
        pInk.setColor(0xFF1B5E20);
        pInk.setStrokeWidth(px(3f));
        pInk.setStyle(Paint.Style.STROKE);
        cv.drawRect(x0, y0, x0 + w, y0 + h, pInk);
        stubDown(cv, c.x, c.y + 1); stubDown(cv, c.x + 2, c.y + 1); stubUp(cv, c.x + 1, c.y);
        pInk.setColor(0xFF1B5E20);
        pInk.setStrokeWidth(px(2.6f));
        if (pressed) {
            arrow(cv, x0 + stepPx() * 0.2f, y0 + h * 0.55f, x0 + w - stepPx() * 0.2f, y0 + h * 0.55f);
        } else {
            arrow(cv, x0 + w - stepPx() * 0.25f, y0 + h * 0.82f, x0 + w - stepPx() * 0.25f, y0 - stepPx() * 0.15f);
        }
        // Rollenhebel: schräger Hebel mit Rolle
        pInk.setColor(INK);
        pInk.setStrokeWidth(px(2.4f));
        pInk.setStyle(Paint.Style.STROKE);
        cv.drawLine(sx(c.x + 0.35f), sy(c.y - 0.05f), sx(c.x - 0.45f), sy(c.y - 0.85f), pInk);
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(pressed ? 0xFFE53935 : 0xFFFFFFFF);
        cv.drawCircle(sx(c.x - 0.45f), sy(c.y - 0.95f), px(4.5f), pFill);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setStrokeWidth(px(2f));
        cv.drawCircle(sx(c.x - 0.45f), sy(c.y - 0.95f), px(4.5f), pInk);
        portLabel(cv, "3", c.x + 1.35f, c.y - 0.02f);
        portLabel(cv, "1", c.x - 0.12f, c.y + 1.5f);
        portLabel(cv, "2", c.x + 2.12f, c.y + 1.5f);
    }

    void drawMotor(Canvas cv, Circuit.Comp c) {
        float cx = sx(c.x + 1.9f), cy = sy(c.y + 1f), r = stepPx() * 1.15f;
        // Zuleitungen U/V/W (horizontal in das Gehäuse)
        pInk.setColor(INK);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setStrokeWidth(px(2.6f));
        float dxx = (float) Math.sqrt(1.15f * 1.15f - 1f);
        cv.drawLine(sx(c.x), sy(c.y), sx(c.x + 1.9f - dxx), sy(c.y), pInk);
        cv.drawLine(sx(c.x), sy(c.y + 1), sx(c.x + 1.9f - 1.15f), sy(c.y + 1), pInk);
        cv.drawLine(sx(c.x), sy(c.y + 2), sx(c.x + 1.9f - dxx), sy(c.y + 2), pInk);
        pInk.setStrokeWidth(px(3f));
        cv.drawCircle(cx, cy, r, pInk);
        // Rotor, dreht im Simulationsbetrieb
        if (engine != null && engine.running && c.powered) {
            pInk.setStrokeWidth(px(2.2f));
            pInk.setColor(0xFFB71C1C);
            float a = c.anim * 6.28f;
            for (int i = 0; i < 3; i++) {
                float aa = a + i * (float) (Math.PI * 2 / 3);
                cv.drawLine(cx, cy, cx + r * 0.62f * (float) Math.cos(aa),
                        cy + r * 0.62f * (float) Math.sin(aa), pInk);
            }
        }
        pInk.setColor(INK);
        pText.setColor(INK);
        pText.setTextSize(px(11));
        pText.setTextAlign(Paint.Align.CENTER);
        cv.drawText("M", cx, cy - px(2), pText);
        cv.drawText("3~", cx, cy + px(12), pText);
        pText.setTextAlign(Paint.Align.LEFT);
        portLabel(cv, "U", c.x - 0.62f, c.y - 0.06f);
        portLabel(cv, "V", c.x - 0.62f, c.y + 0.94f);
        portLabel(cv, "W", c.x - 0.62f, c.y + 1.94f);
    }

    void drawMotor6(Canvas cv, Circuit.Comp c) {
        float cx = sx(c.x + 1.5f), cy = sy(c.y + 1f), r = stepPx() * 1.15f;
        float dxx = (float) Math.sqrt(1.15f * 1.15f - 1f);
        pInk.setColor(INK);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setStrokeWidth(px(2.6f));
        // Zuleitungen U1/V1/W1 links und U2/V2/W2 rechts in das Gehäuse
        for (int i = 0; i < 3; i++) {
            cv.drawLine(sx(c.x), sy(c.y + i), sx(c.x + 1.5f - dxx), sy(c.y + i), pInk);
            cv.drawLine(sx(c.x + 3), sy(c.y + i), sx(c.x + 1.5f + dxx), sy(c.y + i), pInk);
        }
        pInk.setStrokeWidth(px(3f));
        cv.drawCircle(cx, cy, r, pInk);
        // Rotor, dreht im Simulationsbetrieb
        if (engine != null && engine.running && c.powered) {
            pInk.setStrokeWidth(px(2.2f));
            pInk.setColor(0xFFB71C1C);
            float a = c.anim * 6.28f;
            for (int i = 0; i < 3; i++) {
                float aa = a + i * (float) (Math.PI * 2 / 3);
                cv.drawLine(cx, cy, cx + r * 0.62f * (float) Math.cos(aa),
                        cy + r * 0.62f * (float) Math.sin(aa), pInk);
            }
        }
        pInk.setColor(INK);
        pText.setColor(INK);
        pText.setTextSize(px(11));
        pText.setTextAlign(Paint.Align.CENTER);
        cv.drawText("M", cx, cy - px(2), pText);
        cv.drawText("3~", cx, cy + px(12), pText);
        pText.setTextAlign(Paint.Align.LEFT);
        portLabel(cv, "U1", c.x - 0.68f, c.y - 0.06f);
        portLabel(cv, "V1", c.x - 0.68f, c.y + 0.94f);
        portLabel(cv, "W1", c.x - 0.68f, c.y + 1.94f);
        portLabel(cv, "U2", c.x + 3.06f, c.y - 0.06f);
        portLabel(cv, "V2", c.x + 3.06f, c.y + 0.94f);
        portLabel(cv, "W2", c.x + 3.06f, c.y + 1.94f);
    }

    void drawBimet(Canvas cv, Circuit.Comp c) {
        pInk.setColor(INK);
        pInk.setStyle(Paint.Style.STROKE);
        // drei Pole mit Bimetall-Heizwicklung (Kästchen um die Leitung)
        for (int i = 0; i < 3; i++) {
            float y = c.y + i;
            stubDown(cv, c.x, y); stubDown(cv, c.x + 3, y);
            pInk.setStrokeWidth(px(2.6f));
            cv.drawLine(sx(c.x), sy(y), sx(c.x + 1.1f), sy(y), pInk);
            cv.drawLine(sx(c.x + 1.9f), sy(y), sx(c.x + 3), sy(y), pInk);
            if (c.tripped) {
                pFill.setStyle(Paint.Style.FILL);
                pFill.setColor(0xFFFFCDD2);
                cv.drawRect(sx(c.x + 1.1f), sy(y - 0.22f), sx(c.x + 1.9f), sy(y + 0.22f), pFill);
            }
            pInk.setStrokeWidth(px(2f));
            cv.drawRect(sx(c.x + 1.1f), sy(y - 0.22f), sx(c.x + 1.9f), sy(y + 0.22f), pInk);
        }
        // Hilfsöffner 95/96 (Reihe y+4), mechanisch gekoppelt
        drawContactAt(cv, c, c.y + 4, true, false, !c.tripped);
        pInk.setStrokeWidth(px(1.6f));
        for (float yy = c.y + 2.5f; yy < c.y + 3.8f; yy += 0.28f)
            cv.drawLine(sx(c.x + 1.5f), sy(yy), sx(c.x + 1.5f), sy(yy + 0.13f), pInk);
        if (c.tripped) {
            pText.setColor(0xFFB71C1C);
            pText.setTextSize(px(12));
            cv.drawText("Störung", sx(c.x - 0.4f), sy(c.y + 3.15f), pText);
        }
        String[] num = {"1", "2", "3", "4", "5", "6", "95", "96"};
        float[] ly = {0f, 0f, 1f, 1f, 2f, 2f, 4f, 4f};
        float[] lx = {-0.62f, 3.08f, -0.62f, 3.08f, -0.62f, 3.08f, -0.62f, 3.08f};
        for (int i = 0; i < 8; i++)
            portLabel(cv, num[i], c.x + lx[i], c.y + ly[i] - 0.06f);
    }

    void arrow(Canvas cv, float x1, float y1, float x2, float y2) {
        pInk.setStyle(Paint.Style.STROKE);
        cv.drawLine(x1, y1, x2, y2, pInk);
        float ang = (float) Math.atan2(y2 - y1, x2 - x1);
        float al = px(7);
        Path h = new Path();
        h.moveTo(x2, y2);
        h.lineTo(x2 - al * (float) Math.cos(ang - 0.45), y2 - al * (float) Math.sin(ang - 0.45));
        h.lineTo(x2 - al * (float) Math.cos(ang + 0.45), y2 - al * (float) Math.sin(ang + 0.45));
        h.close();
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(pInk.getColor());
        cv.drawPath(h, pFill);
    }

    void portLabel(Canvas cv, String s, float gx, float gy) {
        pText.setColor(0xFF546E7A);
        pText.setTextSize(px(10));
        cv.drawText(s, sx(gx), sy(gy), pText);
    }

    void dot(Canvas cv, float gx, float gy, float r, int color) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(color);
        cv.drawCircle(sx(gx), sy(gy), px(r), pFill);
    }

    void drawContactor(Canvas cv, Circuit.Comp c) {
        // Spule A1/A2
        stubDown(cv, c.x, c.y); stubDown(cv, c.x + 3, c.y);
        pInk.setColor(INK);
        pInk.setStyle(Paint.Style.STROKE);
        pInk.setStrokeWidth(px(2.6f));
        cv.drawLine(sx(c.x), sy(c.y), sx(c.x), sy(c.y - 0.35f), pInk);
        cv.drawLine(sx(c.x + 3), sy(c.y), sx(c.x + 3), sy(c.y - 0.35f), pInk);
        float l = sx(c.x + 0.7f), r = sx(c.x + 2.3f), t = sy(c.y - 0.45f), b = sy(c.y + 0.45f);
        if (c.powered) {
            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(0xFFFFCDD2);
            cv.drawRect(l, t, r, b, pFill);
        }
        pInk.setColor(INK);
        pInk.setStrokeWidth(px(3f));
        cv.drawRect(l, t, r, b, pInk);
        portLabel(cv, "A1", c.x - 0.78f, c.y - 0.06f);
        portLabel(cv, "A2", c.x + 3.1f, c.y - 0.06f);
        // mechanische Kupplung Spule -> Hauptkontakte (gestrichelt angedeutet)
        pInk.setStrokeWidth(px(1.6f));
        for (float yy = c.y + 0.6f; yy < c.y + 1.9f; yy += 0.28f)
            cv.drawLine(sx(c.x + 1.5f), sy(yy), sx(c.x + 1.5f), sy(yy + 0.13f), pInk);
        // drei Hauptkontakte (Schließer): 1-2 / 3-4 / 5-6
        for (int i = 0; i < 3; i++)
            drawContactAt(cv, c, c.y + 2 + i, false, false, c.powered);
        String[] num = {"1", "2", "3", "4", "5", "6"};
        for (int i = 0; i < 3; i++) {
            float ry = c.y + 2 + i;
            portLabel(cv, num[2 * i], c.x - 0.62f, ry - 0.06f);
            portLabel(cv, num[2 * i + 1], c.x + 3.1f, ry - 0.06f);
        }
    }

    // ================================================================ Verknüpfen (Ziel antippen)

    /** Link-Modus starten: mögliche Ziele pulsieren, Antippen verknüpft. */
    public void startLink(Circuit.Comp src) {
        if (engine != null && engine.running) {
            if (listener != null) listener.onToast("Zum Verknüpfen erst ■ Stop drücken");
            return;
        }
        String t = src.tag;
        if (t.equals(Circuit.CONTACT) || t.equals(Circuit.CONTACT_NC)) linkKind = LINK_CONTACT;
        else if (t.equals(Circuit.SOLENOID)) linkKind = LINK_SOLENOID;
        else if (t.equals(Circuit.VALVE32R)) linkKind = LINK_ROLLER;
        else if (t.equals(Circuit.PSW)) linkKind = LINK_PSW;
        else return;
        linkSrc = src;
        insertTag = null; insertColor = null;
        cancelWireSilent();
        selCompId = src.id;
        handler.removeCallbacks(pulseAnim);
        handler.postDelayed(pulseAnim, 300);
        invalidate();
    }

    public boolean linkModeActive() { return linkSrc != null; }

    public void cancelLink() {
        linkSrc = null;
        invalidate();
    }

    boolean isLinkTarget(Circuit.Comp o) {
        if (linkSrc == null || o.id.equals(linkSrc.id)) return false;
        switch (linkKind) {
            case LINK_CONTACT:
                return o.tag.equals(Circuit.COIL) || o.tag.equals(Circuit.LAMP)
                        || o.tag.equals(Circuit.SOLENOID) || o.tag.equals(Circuit.TIMER)
                        || o.tag.equals(Circuit.CONTACTOR);
            case LINK_SOLENOID:
                return o.tag.equals(Circuit.VALVE32) || o.tag.equals(Circuit.VALVE32R)
                        || o.tag.equals(Circuit.VALVE52M) || o.tag.equals(Circuit.VALVE52B);
            case LINK_ROLLER:
                return o.tag.equals(Circuit.CYL) || o.tag.equals(Circuit.CYL_S);
            case LINK_PSW:
                if (!Circuit.isPneu(o.tag)) return false;
                for (Circuit.Pt p : Circuit.portsOf(o)) if ("p".equals(p.kind)) return true;
                return false;
        }
        return false;
    }

    String linkHint() {
        switch (linkKind) {
            case LINK_CONTACT: return "Ziel antippen: Spule · Zeitrelais · Schütz · leere Fläche = Abbrechen";
            case LINK_SOLENOID: return "Ventil antippen, das die Magnetspule steuert · leere Fläche = Abbrechen";
            case LINK_ROLLER: return "Zylinder antippen, der den Rollenhebel auslöst · leere Fläche = Abbrechen";
            case LINK_PSW: return "Pneumatik-Bauteil antippen, dessen Druck überwacht wird · leere Fläche = Abbrechen";
        }
        return "Ziel antippen · leere Fläche = Abbrechen";
    }

    static String linkId(String link) {
        if (link == null) return "";
        int ix = link.indexOf(':');
        return ix >= 0 ? link.substring(0, ix) : link;
    }

    void drawLinkLines(Canvas cv) {
        if (circuit == null || (engine != null && engine.running)) return;
        Circuit.Comp sel = null;
        for (Circuit.Comp cp : circuit.comps) if (cp.id.equals(selCompId)) sel = cp;
        if (sel == null) return;
        if (sel.link != null && !sel.link.isEmpty()) {
            Circuit.Comp t = Circuit.findComp(circuit.comps, linkId(sel.link));
            if (t != null) drawLinkLine(cv, sel, t);
        }
        for (Circuit.Comp cp : circuit.comps) {
            if (cp == sel || cp.link == null || cp.link.isEmpty()) continue;
            if (linkId(cp.link).equals(selCompId)) drawLinkLine(cv, cp, sel);
        }
    }

    void drawLinkLine(Canvas cv, Circuit.Comp a, Circuit.Comp b) {
        if (b == null) return;
        float[] ba = compBBox(a), bb = compBBox(b);
        float ax = sx((ba[0] + ba[2]) / 2f), ay = sy((ba[1] + ba[3]) / 2f);
        float bx = sx((bb[0] + bb[2]) / 2f), by = sy((bb[1] + bb[3]) / 2f);
        cv.drawLine(ax, ay, bx, by, pLink);
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(pLink.getColor());
        cv.drawCircle(bx, by, px(3), pFill);
    }

    void drawLinkHighlights(Canvas cv) {
        if (circuit == null || (engine != null && engine.running)) return;
        long t = System.currentTimeMillis();
        float pulse = (float) Math.abs(Math.sin(t / 320.0));
        if (linkSrc != null) {
            pPulse.setColor(0xFF1E88E5);
            pPulse.setAlpha((int) (70 + 150 * pulse));
            for (Circuit.Comp cp : circuit.comps) {
                if (!isLinkTarget(cp)) continue;
                float[] b = compBBox(cp);
                cv.drawRect(sx(b[0]) - px(3), sy(b[1]) - px(3), sx(b[2]) + px(3), sy(b[3]) + px(3), pPulse);
            }
            pPulse.setColor(0xFF7E57C2);
            pPulse.setAlpha(255);
            float[] b = compBBox(linkSrc);
            cv.drawRect(sx(b[0]) - px(3), sy(b[1]) - px(3), sx(b[2]) + px(3), sy(b[3]) + px(3), pPulse);
        } else {
            // unverknüpfte Kontakte mahnen (gelb pulsierend)
            pPulse.setColor(0xFFF9A825);
            pPulse.setAlpha((int) (60 + 120 * pulse));
            for (Circuit.Comp cp : circuit.comps) {
                if (!cp.tag.equals(Circuit.CONTACT) && !cp.tag.equals(Circuit.CONTACT_NC)) continue;
                if (cp.link != null && !cp.link.isEmpty()) continue;
                float[] b = compBBox(cp);
                cv.drawRect(sx(b[0]) - px(3), sy(b[1]) - px(3), sx(b[2]) + px(3), sy(b[3]) + px(3), pPulse);
            }
        }
    }

    // ================================================================ Berührung

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = lastX = ev.getX();
                downY = lastY = ev.getY();
                wireCursorX = lastX; wireCursorY = lastY;
                downAt = System.currentTimeMillis();
                movedFar = false; lpFired = false;
                handler.postDelayed(lpChecker, 560);
                onDown();
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (ev.getPointerCount() >= 2 && secondPointer < 0) {
                    secondPointer = 1;
                    pinchDistBase = Math.max(1f, pinchDistance(ev));
                    pinchCx = (ev.getX(0) + ev.getX(1)) / 2;
                    pinchCy = (ev.getY(0) + ev.getY(1)) / 2;
                    pinchSc = sc; pinchOx = ox; pinchOy = oy;
                    movedFar = true;
                    handler.removeCallbacks(lpChecker);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (secondPointer >= 0 && ev.getPointerCount() >= 2) {
                    float d = Math.max(1f, pinchDistance(ev));
                    float cx = (ev.getX(0) + ev.getX(1)) / 2, cy = (ev.getY(0) + ev.getY(1)) / 2;
                    float ns = Math.max(MIN_SC, Math.min(MAX_SC, pinchSc * (d / pinchDistBase)));
                    float wxp = (pinchCx - pinchOx) / pinchSc, wyp = (pinchCy - pinchOy) / pinchSc;
                    sc = ns; ox = cx - wxp * ns; oy = cy - wyp * ns;
                    invalidate();
                    return true;
                }
                onMove(ev);
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                secondPointer = -1;
                return true;
            }
            case MotionEvent.ACTION_UP: {
                handler.removeCallbacks(lpChecker);
                onUp();
                secondPointer = -1;
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                handler.removeCallbacks(lpChecker);
                abortAll();
                return true;
            }
        }
        return true;
    }

    final Runnable lpChecker = new Runnable() {
        @Override public void run() {
            if (movedFar || lpFired) return;
            lpFired = true;
            int gx = gxs(downX), gy = gys(downY);
            if (linkSrc != null) {
                linkSrc = null;
                if (listener != null) { listener.onLinkEnded(); listener.onToast("Verknüpfen abgebrochen"); }
                invalidate();
                return;
            }
            if (wireActive) {
                cancelWire();
                if (listener != null) listener.onToast("Leitung abgebrochen");
                return;
            }
            Circuit.Wire w = hitWire(gx, gy);
            if (w != null && listener != null) { listener.onWireMenu(w); return; }
            Circuit.Comp c = hitComp(gx, gy);
            if (c != null && !isRail(c.tag) && listener != null) listener.onCompMenu(c);
        }
    };

    float pinchDistance(MotionEvent ev) {
        float dx = ev.getX(0) - ev.getX(1), dy = ev.getY(0) - ev.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    void abortAll() {
        if (pressComp != null) { pressComp.man = false; pressComp = null; }
        moveComp = null;
        invalidate();
    }

    // -------------------------------------------------------------- Down

    void onDown() {
        if (linkSrc != null) return; // Link-Modus: nur Tippen zählt, Pinch weiter möglich
        int gx = gxs(downX), gy = gys(downY);
        if (engine != null && engine.running) {
            Circuit.Comp h = hitComp(gx, gy);
            if (h != null && isManual(h)) {
                pressComp = h;
                h.man = true;
                invalidate();
            }
            return;
        }
        int[] port = hitPort(gx, gy);
        if (port != null && !wireActive) {
            // Leitung am Port beginnen
            wireActive = true;
            wireKind = port[2] == 'p' ? 1 : 0;
            wireAnchors.clear();
            wireAnchors.add(port[0]); wireAnchors.add(port[1]);
            return;
        }
        if (wireActive) return; // Ecken kommen per Tap
        int rl = railAt(gx);
        if (rl > 0) {
            wireActive = true; wireKind = 0;
            wireAnchors.clear();
            wireAnchors.add(rl); wireAnchors.add(gy);
            return;
        }
        Circuit.Comp hc = hitComp(gx, gy);
        if (hc != null) {
            moveComp = hc;
            moveGrabGX = gx - hc.x; moveGrabGY = gy - hc.y;
            snapshotPorts(hc);
            selCompId = hc.id;
        }
    }

    boolean isManual(Circuit.Comp c) {
        return c.tag.equals(Circuit.PUSHBTN) || c.tag.equals(Circuit.PUSHBTN_NC) || c.tag.equals(Circuit.VALVE32);
    }

    int[] hitPort(int gx, int gy) {
        float best = 0.66f; int[] out = null;
        for (Circuit.Comp c : circuit.comps) {
            for (Circuit.Pt p : Circuit.portsOf(c)) {
                int ax = c.x + p.dx, ay = c.y + p.dy;
                float dx = gx - ax, dy = gy - ay;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < best) {
                    best = d;
                    out = new int[]{ax, ay, p.kind.charAt(0)};
                }
            }
        }
        return out;
    }

    int railAt(int gx) {
        for (Circuit.Comp r : circuit.comps)
            if (isRail(r.tag) && gx == r.x) return r.x;
        return -1;
    }

    Circuit.Comp hitComp(int gx, int gy) {
        for (Circuit.Comp c : circuit.comps) {
            if (isRail(c.tag)) continue;
            float[] b = compBBox(c);
            if (gx >= b[0] && gx <= b[2] && gy >= b[1] && gy <= b[3]) return c;
        }
        for (Circuit.Comp c : circuit.comps) {
            if (!isRail(c.tag)) continue;
            if (Math.abs(gx - c.x) < 0.6f) return c;
        }
        return null;
    }

    // -------------------------------------------------------------- Move

    void snapshotPorts(Circuit.Comp c) {
        moveSnap.clear();
        if (isRail(c.tag)) moveSnap.add(new int[]{c.x, 0, RAILMARK});
        else
            for (Circuit.Pt p : Circuit.portsOf(c))
                moveSnap.add(new int[]{c.x + p.dx, c.y + p.dy, 0});
    }

    static final int RAILMARK = 99;

    void onMove(MotionEvent ev) {
        float x = ev.getX(), y = ev.getY();
        float dXX = x - downX, dYY = y - downY;
        if (dXX * dXX + dYY * dYY > stepPx() * stepPx() * 0.012f) {
            movedFar = true;
            if (pressComp != null) { pressComp.man = false; pressComp = null; }
        }
        wireCursorX = x; wireCursorY = y;

        if (moveComp != null) {
            int gx = gxs(x), gy = gys(y);
            if (isRail(moveComp.tag)) {
                int nx = gx - Math.round(moveGrabGX);
                if (nx != moveComp.x) {
                    int oldx = moveComp.x;
                    moveComp.x = nx;
                    for (Circuit.Wire w : circuit.wires) {
                        if (!w.electric) continue;
                        for (int i = 0; i < w.pts.length; i += 2)
                            if (w.pts[i] == oldx) w.pts[i] = nx;
                    }
                }
            } else {
                int nx = gx - Math.round(moveGrabGX), ny = gy - Math.round(moveGrabGY);
                int dx = nx - moveComp.x, dy = ny - moveComp.y;
                if (dx != 0 || dy != 0) {
                    for (int[] s : moveSnap) {
                        for (Circuit.Wire w : circuit.wires)
                            for (int i = 0; i < w.pts.length; i += 2)
                                if (RAILMARK != s[2] && w.pts[i] == s[0] && w.pts[i + 1] == s[1]) {
                                    w.pts[i] += dx; w.pts[i + 1] += dy;
                                }
                    }
                    for (int[] s : moveSnap) { s[0] += dx; s[1] += dy; }
                    moveComp.x += dx; moveComp.y += dy;
                }
            }
            invalidate();
            lastX = x; lastY = y;
            return;
        }

        if (movedFar && !lpFired) {
            ox += x - lastX; oy += y - lastY;
        }
        lastX = x; lastY = y;
        invalidate();
    }

    // -------------------------------------------------------------- Up

    void onUp() {
        int gx = gxs(lastX), gy = gys(lastY);
        boolean tap = !movedFar && (System.currentTimeMillis() - downAt) < 700;
        if (pressComp != null) { pressComp.man = false; pressComp = null; }
        if (tap) {
            if (engine != null && engine.running) {
                Circuit.Comp h = hitComp(gx, gy);
                if (h != null && h.tag.equals(Circuit.SWITCH)) h.sw = !h.sw;
                else if (h != null && h.tag.equals(Circuit.BIMET)) {
                    h.tripped = !h.tripped;
                    if (listener != null)
                        listener.onToast(h.tripped ? "Störung: Bimetall ausgelöst!" : "Bimetall zurückgestellt");
                }
            } else {
                doTap(gx, gy);
            }
        }
        moveComp = null;
        invalidate();
    }

    /** Tap im Bearbeitungsmodus. */
    void doTap(int gx, int gy) {
        if (linkSrc != null) {
            Circuit.Comp h = hitComp(gx, gy);
            if (h != null && h != linkSrc && isLinkTarget(h)) {
                if (linkKind == LINK_PSW) {
                    if (listener != null) listener.onPswPortPick(h, linkSrc);
                } else {
                    linkSrc.link = h.id;
                    if (linkKind == LINK_CONTACT) linkSrc.name = h.name;
                    if (listener != null) listener.onToast("Verknüpft mit " + h.name);
                    linkSrc = null;
                }
                invalidate();
                return;
            }
            if (h == linkSrc) return;
            linkSrc = null;
            if (listener != null) { listener.onLinkEnded(); listener.onToast("Verknüpfen abgebrochen"); }
            invalidate();
            return;
        }
        if (wireActive) {
            int[] port = hitPort(gx, gy);
            boolean validTarget = false;
            int tx = gx, ty = gy;
            if (port != null && ((port[2] == 'p') == (wireKind == 1))) {
                validTarget = true; tx = port[0]; ty = port[1];
            } else if (wireKind == 0) {
                int rl = railAt(gx);
                if (rl > 0) { validTarget = true; tx = rl; ty = gy; }
            }
            if (validTarget) {
                if (wireAnchors.size() == 2 && tx == wireAnchors.get(0) && ty == wireAnchors.get(1)) {
                    if (listener != null) listener.onToast("Leitung zu kurz");
                    cancelWire();
                    return;
                }
                routeAppend(tx, ty);
                int[] pts = new int[wireAnchors.size()];
                for (int i = 0; i < pts.length; i++) pts[i] = wireAnchors.get(i);
                circuit.addWire(pts, wireKind == 0);
                cancelWire();
                invalidate();
                return;
            }
            routeAppend(gx, gy);
            invalidate();
            return;
        }
        if (insertTag != null) {
            Circuit.Comp c = circuit.addComp(insertTag, gx, gy);
            if (c.tag.equals(Circuit.LAMP)) c.crane = "red".equals(insertColor);
            if (Circuit.isElec(insertTag) && !isRail(insertTag)) ensureRails();
            selCompId = c.id;
            insertTag = null; insertColor = null;
            if (listener != null) listener.onToast("Bauteil eingefügt");
            invalidate();
            return;
        }
        Circuit.Comp h = hitComp(gx, gy);
        if (h != null && h.tag.equals(Circuit.SWITCH)) {
            h.sw = !h.sw;
            invalidate();
            return;
        }
        selCompId = null;
        invalidate();
    }

    /** Route von letztem Anker zum Zielpunkt orthogonal verlängern (horizontal zuerst). */
    void routeAppend(int tx, int ty) {
        int lx = wireAnchors.get(wireAnchors.size() - 2);
        int ly = wireAnchors.get(wireAnchors.size() - 1);
        if (tx == lx && ty == ly) return;
        if (tx == lx || ty == ly) {
            wireAnchors.add(tx); wireAnchors.add(ty);
        } else {
            wireAnchors.add(tx); wireAnchors.add(ly);
            wireAnchors.add(tx); wireAnchors.add(ty);
        }
    }

    public void cancelWire() {
        wireActive = false;
        wireAnchors.clear();
        invalidate();
    }

    // -------------------------------------------------------------- Werkzeuge

    public void setInsert(String tag, String colorKind) {
        insertTag = tag;
        insertColor = colorKind;
        selCompId = null;
        linkSrc = null;
        cancelWireSilent();
        invalidate();
    }

    void cancelWireSilent() {
        wireActive = false;
        wireAnchors.clear();
    }

    public void ensureRails() {
        boolean l = false, r = false;
        for (Circuit.Comp c : circuit.comps) {
            if (c.tag.equals(Circuit.RAIL_L)) l = true;
            if (c.tag.equals(Circuit.RAIL_R)) r = true;
        }
        if (!l) circuit.addComp(Circuit.RAIL_L, 2, 4);
        if (!r) circuit.addComp(Circuit.RAIL_R, 44, 4);
    }

    public void zoom(float f) {
        float ns = Math.max(MIN_SC, Math.min(MAX_SC, sc * f));
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float wx = (cx - ox) / sc, wy = (cy - oy) / sc;
        sc = ns;
        ox = cx - wx * ns;
        oy = cy - wy * ns;
        invalidate();
    }

    public void selectNothing() {
        selCompId = null;
        linkSrc = null;
        cancelWireSilent();
        invalidate();
    }

    public String sel() { return selCompId; }

    Circuit.Wire hitWire(int gx, int gy) {
        Circuit.Wire best = null;
        float bestD = 0.45f;
        for (Circuit.Wire w : circuit.wires) {
            for (int i = 0; i + 3 < w.pts.length; i += 2) {
                float d = segDist(gx, gy, w.pts[i], w.pts[i + 1], w.pts[i + 2], w.pts[i + 3]);
                if (d < bestD) { bestD = d; best = w; }
            }
        }
        return best;
    }

    static float segDist(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        float l2 = dx * dx + dy * dy;
        if (l2 <= 0) return (float) Math.hypot(px - x1, py - y1);
        float t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / l2));
        float cX = x1 + t * dx, cY = y1 + t * dy;
        return (float) Math.hypot(px - cX, py - cY);
    }
}
