package com.winlator.cmod.widget;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Renderer untuk WinlatorHUD.STYLE_CYBER — panel gaya "MSI Afterburner" /
 * cyberpunk HUD (cut-corner panel, gauge lingkaran, accent ungu).
 *
 * Stateless terhadap data WinlatorHUD: semua value dilempar sebagai parameter,
 * jadi kelas ini nggak perlu akses field private WinlatorHUD sama sekali.
 * WinlatorHUD cukup punya satu instance `CyberHudRenderer` dan panggil
 * measureHorizontal()/drawHorizontal() (atau versi Vertical) dari method
 * onDraw()/onMeasure() yang sudah ada.
 *
 * Semua Path/RectF di-cache sebagai field: onDraw dipanggil terus-menerus
 * lewat redraw loop WinlatorHUD, jadi alokasi per-frame bikin GC churn pas
 * main game.
 */
public class CyberHudRenderer {

    public static final int BG          = Color.argb(215, 0x0D, 0x0D, 0x12);
    public static final int BORDER      = Color.argb(140, 0x8B, 0x5C, 0xF6);
    public static final int ACCENT      = Color.rgb(0x8B, 0x5C, 0xF6);
    public static final int ACCENT_SOFT = Color.rgb(0xC4, 0xB5, 0xFD);
    public static final int RING_BG     = Color.argb(90, 255, 255, 255);
    public static final int TILE_BG     = Color.argb(12, 255, 255, 255);
    public static final int INNER_LINE  = Color.argb(60, 0x8B, 0x5C, 0xF6);
    public static final int TICK        = Color.argb(170, 0xC4, 0xB5, 0xFD);

    private static final float PANEL_W_GAUGE = 78f;
    private static final float PANEL_W_FPS   = 112f;
    private static final float PANEL_W_BAR   = 86f;
    private static final float PANEL_W_TEXT  = 78f;
    private static final float TEXT_PAD      = 20f;
    private static final float GAP           = 6f;
    private static final float H_NORMAL      = 96f;
    private static final float H_COMPACT     = 60f;
    private static final float ROW_H_NORMAL  = 64f;
    private static final float ROW_H_COMPACT = 46f;
    private static final float CUT           = 8f;

    private final float density;
    private final Paint pBg       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRingBg   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRingFg   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel    = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint pValue    = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint pValueBig = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint pBarBg    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBarFg    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGraph    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTile      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pChamfer   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBracket   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pInner     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTick      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAccentBar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path reusablePath = new Path();
    private final Path graphPath    = new Path();
    private final RectF panelRect = new RectF();
    private final RectF oval      = new RectF();
    private final RectF barRect   = new RectF();

    public CyberHudRenderer(float density) {
        this.density = density;
        Typeface mono  = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        Typeface monoR = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL);

        pBg.setStyle(Paint.Style.FILL);
        pBg.setColor(BG);

        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(1.4f * density);
        pBorder.setColor(BORDER);

        pRingBg.setStyle(Paint.Style.STROKE);
        pRingBg.setStrokeWidth(4f * density);
        pRingBg.setColor(RING_BG);

        pRingFg.setStyle(Paint.Style.STROKE);
        pRingFg.setStrokeWidth(4f * density);
        pRingFg.setStrokeCap(Paint.Cap.ROUND);
        pRingFg.setColor(ACCENT);

        pLabel.setTextSize(9.5f * density);
        pLabel.setTypeface(monoR);
        pLabel.setColor(Color.argb(200, 255, 255, 255));
        pLabel.setTextAlign(Paint.Align.CENTER);

        pValue.setTextSize(13f * density);
        pValue.setTypeface(mono);
        pValue.setColor(Color.WHITE);
        pValue.setTextAlign(Paint.Align.CENTER);

        pValueBig.setTextSize(20f * density);
        pValueBig.setTypeface(mono);
        pValueBig.setColor(ACCENT_SOFT);
        pValueBig.setTextAlign(Paint.Align.CENTER);

        pBarBg.setStyle(Paint.Style.FILL);
        pBarBg.setColor(Color.argb(60, 255, 255, 255));

        pBarFg.setStyle(Paint.Style.FILL);
        pBarFg.setColor(ACCENT);

        pGraph.setStyle(Paint.Style.STROKE);
        pGraph.setStrokeWidth(1.3f * density);
        pGraph.setStrokeCap(Paint.Cap.ROUND);
        pGraph.setColor(ACCENT_SOFT);

        pTile.setStyle(Paint.Style.FILL);
        pTile.setColor(TILE_BG);

        pChamfer.setStyle(Paint.Style.STROKE);
        pChamfer.setStrokeWidth(2.2f * density);
        pChamfer.setColor(ACCENT);

        pBracket.setStyle(Paint.Style.STROKE);
        pBracket.setStrokeWidth(2f * density);
        pBracket.setColor(ACCENT);

        pInner.setStyle(Paint.Style.STROKE);
        pInner.setStrokeWidth(1f * density);
        pInner.setColor(INNER_LINE);

        pTick.setStyle(Paint.Style.STROKE);
        pTick.setStrokeWidth(1.2f * density);
        pTick.setColor(TICK);

        pAccentBar.setStyle(Paint.Style.FILL);
        pAccentBar.setColor(ACCENT);
    }

    // ---------- HORIZONTAL ----------

    public float rowHeightHorizontal(boolean compact) {
        return (compact ? H_COMPACT : H_NORMAL) * density;
    }

    public float measureHorizontal(boolean showGpu, boolean showCpu, boolean showRam,
                                    boolean showBatt, boolean showFps,
                                    boolean showRend, String rendVal,
                                    boolean showWrap, String wrapVal) {
        float w = 0; boolean first = true;
        if (showGpu)  { w += gap(first) + PANEL_W_GAUGE * density; first = false; }
        if (showCpu)  { w += gap(first) + PANEL_W_GAUGE * density; first = false; }
        if (showRam)  { w += gap(first) + PANEL_W_BAR   * density; first = false; }
        if (showBatt) { w += gap(first) + PANEL_W_BAR   * density; first = false; }
        if (showFps)  { w += gap(first) + PANEL_W_FPS   * density; first = false; }
        if (showRend) { w += gap(first) + textPanelWidth(rendVal); first = false; }
        if (showWrap) { w += gap(first) + textPanelWidth(wrapVal); first = false; }
        return Math.max(w, 40f * density);
    }

    /**
     * Panel Renderer/Wrapper isinya teks bebas ("+OpenGL", "WineD3D", "DXVK 2.3"),
     * jadi lebarnya ikut teks — panel lain lebarnya konstan. measureText() harus
     * pakai pValue, paint yang sama yang nanti nggambar, biar hasil ukur == hasil
     * gambar.
     */
    private float textPanelWidth(String s) {
        float textW = (s == null ? 0 : pValue.measureText(s)) + TEXT_PAD * density;
        return Math.max(PANEL_W_TEXT * density, textW);
    }

    private float gap(boolean first) { return first ? 0 : GAP * density; }

    /**
     * @param cpuTemp boleh null/kosong kalau SHOW_CPU_TEMP mati
     * @param graph   buffer FPS yang sudah ada di WinlatorHUD (field `graph`)
     * @param gHead   field `gHead` yang sudah ada
     * @param gBuf    konstanta GBUF yang sudah ada (40)
     * @param gMax    field `gMax` yang sudah ada
     */
    public void drawHorizontal(Canvas c, boolean compact,
            boolean showGpu, String gpuVal, int gpuPct,
            boolean showCpu, String cpuVal, int cpuPct, String cpuTemp,
            boolean showRam, String ramVal, int ramPct,
            boolean showBatt, String battVal, String battPct, boolean charging,
            boolean showFps, String fpsVal, float[] graph, int gHead, int gBuf, float gMax,
            boolean showRend, String rendVal, boolean showWrap, String wrapVal) {

        float h = rowHeightHorizontal(compact);
        float cut = compact ? CUT * 0.6f * density : CUT * density;
        float x = 0;
        RectF r = panelRect;

        if (showGpu) {
            float w = PANEL_W_GAUGE * density;
            r.set(x, 0, x + w, h);
            drawPanel(c, r, cut);
            drawGaugePanel(c, r, gpuVal, gpuPct, "GPU", compact);
            x += w + GAP * density;
        }
        if (showCpu) {
            float w = PANEL_W_GAUGE * density;
            r.set(x, 0, x + w, h);
            drawPanel(c, r, cut);
            String label = "CPU" + (cpuTemp != null && !cpuTemp.isEmpty() ? " " + cpuTemp : "");
            drawGaugePanel(c, r, cpuVal, cpuPct, label, compact);
            x += w + GAP * density;
        }
        if (showRam) {
            float w = PANEL_W_BAR * density;
            r.set(x, 0, x + w, h);
            drawPanel(c, r, cut);
            drawBarPanel(c, r, "RAM", ramVal, ramPct, compact);
            x += w + GAP * density;
        }
        if (showBatt) {
            float w = PANEL_W_BAR * density;
            r.set(x, 0, x + w, h);
            drawPanel(c, r, cut);
            drawBattPanel(c, r, battVal, battPct, charging, compact);
            x += w + GAP * density;
        }
        if (showFps) {
            float w = PANEL_W_FPS * density;
            r.set(x, 0, x + w, h);
            drawPanel(c, r, cut);
            drawFpsPanel(c, r, fpsVal, graph, gHead, gBuf, gMax, compact);
            x += w + GAP * density;
        }
        if (showRend) {
            float w = textPanelWidth(rendVal);
            r.set(x, 0, x + w, h);
            drawPanel(c, r, cut);
            drawTextPanel(c, r, "API", rendVal, compact);
            x += w + GAP * density;
        }
        if (showWrap) {
            float w = textPanelWidth(wrapVal);
            r.set(x, 0, x + w, h);
            drawPanel(c, r, cut);
            drawTextPanel(c, r, "WRAP", wrapVal, compact);
        }
    }

    // ---------- VERTICAL ----------

    public float rowHeightVertical(boolean compact) {
        return (compact ? ROW_H_COMPACT : ROW_H_NORMAL) * density;
    }

    public float measureVerticalWidth(boolean compact, boolean showRend, String rendVal,
                                      boolean showWrap, String wrapVal) {
        float w = (compact ? 130f : 168f) * density;
        // Panel metrik lebarnya konstan, tapi row API/WRAP isinya teks bebas
        // ("+OpenGL", "DXVK 2.3"). Tanpa dilebarin, teks panjang ke-potong.
        if (showRend) w = Math.max(w, rowTextWidth(rendVal));
        if (showWrap) w = Math.max(w, rowTextWidth(wrapVal));
        return w;
    }

    /** drawRowText() nulis value di left+10dp; sisakan 10dp lagi di kanan. */
    private float rowTextWidth(String s) {
        return TEXT_PAD * density + (s == null ? 0 : pValue.measureText(s));
    }

    /** Kembar dari drawVertical(): tiap row maju rowH + GAP, jadi n row = n*rowH + (n-1)*GAP. */
    public float measureVerticalHeight(int rows, boolean compact) {
        if (rows <= 0) return 0;
        return rows * rowHeightVertical(compact) + (rows - 1) * GAP * density;
    }

    public void drawVertical(Canvas c, float width, boolean compact,
            boolean showGpu, String gpuVal, int gpuPct,
            boolean showCpu, String cpuVal, int cpuPct, String cpuTemp,
            boolean showRam, String ramVal, int ramPct,
            boolean showBatt, String battVal, String battPct, boolean charging,
            boolean showFps, String fpsVal,
            boolean showRend, String rendVal, boolean showWrap, String wrapVal) {

        float rowH = rowHeightVertical(compact);
        float cut = compact ? CUT * 0.6f * density : CUT * density;
        float y = 0;
        RectF r = panelRect;

        // API/WRAP ditaruh paling atas di mode vertikal — dua-duanya row teks,
        // kebaca kayak header, gauge-nya nyusul di bawah. Beda urutan dari mode
        // horizontal (di sana nempel di ujung kanan) dan itu memang disengaja.
        if (showRend) {
            r.set(0, y, width, y + rowH);
            drawPanel(c, r, cut);
            drawRowText(c, r, "API", rendVal, null);
            y += rowH + GAP * density;
        }
        if (showWrap) {
            r.set(0, y, width, y + rowH);
            drawPanel(c, r, cut);
            drawRowText(c, r, "WRAP", wrapVal, null);
            y += rowH + GAP * density;
        }
        if (showGpu) {
            r.set(0, y, width, y + rowH);
            drawPanel(c, r, cut);
            drawRowGauge(c, r, "GPU", gpuVal, gpuPct);
            y += rowH + GAP * density;
        }
        if (showCpu) {
            r.set(0, y, width, y + rowH);
            drawPanel(c, r, cut);
            String label = "CPU" + (cpuTemp != null && !cpuTemp.isEmpty() ? " " + cpuTemp : "");
            drawRowGauge(c, r, label, cpuVal, cpuPct);
            y += rowH + GAP * density;
        }
        if (showRam) {
            r.set(0, y, width, y + rowH);
            drawPanel(c, r, cut);
            drawRowBar(c, r, "RAM", ramVal, ramPct);
            y += rowH + GAP * density;
        }
        if (showBatt) {
            r.set(0, y, width, y + rowH);
            drawPanel(c, r, cut);
            drawRowText(c, r, charging ? "CHG" : "PWR", battVal, battPct);
            y += rowH + GAP * density;
        }
        if (showFps) {
            r.set(0, y, width, y + rowH);
            drawPanel(c, r, cut);
            drawRowText(c, r, "FPS", fpsVal, null);
        }
    }

    // ---------- shared drawing helpers ----------

    /**
     * Chamfer cuma di dua sudut berlawanan (kanan-atas + kiri-bawah), sudut
     * kiri-atas & kanan-bawah tetap kotak — bentuk asimetris ini disengaja,
     * ngikut gambar referensi. Jangan "dirapikan" jadi empat sudut simetris.
     *
     * Detail border ini varian "V3" dari preview yang dipilih user: tile backing,
     * chamfer di-accent, corner bracket di dua sudut kotak, inner hairline, tick
     * mark + accent bar di tepi atas. Tick mark memang nembus accent bar 1.5dp —
     * itu bagian dari varian yang disetujui, bukan salah hitung.
     *
     * Semua bentuk digambar ulang dari satu Path yang di-reset — nol alokasi per
     * frame, karena onDraw dipanggil terus lewat redraw loop WinlatorHUD.
     */
    private void drawPanel(Canvas c, RectF r, float cut) {
        // Canvas onDraw di-clip pas segede view, dan view diukur persis segede
        // panel — nggak ada ruang lebih. Stroke digambar center di garisnya, jadi
        // kalau garisnya nempel di tepi, separuh tebalnya ke-clip (bracket 2dp
        // keliatan 1dp). Makanya semua garis digeser masuk setengah stroke
        // terlebar. Fill nggak kena, dia nggak lewat batas path.
        float half = 1f * density;
        float l = r.left + half, t = r.top + half;
        float rt = r.right - half, b = r.bottom - half;

        buildPanelPath(r.left, r.top, r.right, r.bottom, cut);
        c.drawPath(reusablePath, pBg);

        float tileInset = 2.5f * density;
        buildPanelPath(r.left + tileInset, r.top + tileInset,
                       r.right - tileInset, r.bottom - tileInset, cut);
        c.drawPath(reusablePath, pTile);

        buildPanelPath(l, t, rt, b, cut);
        c.drawPath(reusablePath, pBorder);

        float innerInset = 3f * density;
        buildPanelPath(r.left + innerInset, r.top + innerInset,
                       r.right - innerInset, r.bottom - innerInset, cut);
        c.drawPath(reusablePath, pInner);

        // chamfer dikasih accent terang biar potongan sudutnya kebaca sengaja
        c.drawLine(rt - cut, t, rt, t + cut, pChamfer);
        c.drawLine(l + cut, b, l, b - cut, pChamfer);

        // bracket cuma di dua sudut yang tetap kotak, jadi nggak nabrak chamfer
        float arm = 12f * density;
        c.drawLine(l, t + arm, l, t, pBracket);
        c.drawLine(l, t, l + arm, t, pBracket);
        c.drawLine(rt, b - arm, rt, b, pBracket);
        c.drawLine(rt, b, rt - arm, b, pBracket);

        float tickX = r.left + 18f * density;
        float tickH = 4f * density;
        for (int i = 0; i < 3; i++) {
            c.drawLine(tickX, t, tickX, t + tickH, pTick);
            tickX += 4.5f * density;
        }

        float barX = l + arm + 2f * density;
        float barEnd = Math.min(barX + r.width() * 0.34f, rt - cut);
        barRect.set(barX, t, barEnd, t + 2.5f * density);
        c.drawRect(barRect, pAccentBar);
    }

    private void buildPanelPath(float left, float top, float right, float bottom, float cut) {
        reusablePath.reset();
        reusablePath.moveTo(left, top);
        reusablePath.lineTo(right - cut, top);
        reusablePath.lineTo(right, top + cut);
        reusablePath.lineTo(right, bottom);
        reusablePath.lineTo(left + cut, bottom);
        reusablePath.lineTo(left, bottom - cut);
        reusablePath.close();
    }

    /**
     * Label pakai offset ABSOLUT dari tepi atas, value pakai offset PROPORSIONAL.
     * Pas compact (h 96dp -> 60dp) cuma value yang naik, labelnya diem, jadi
     * jaraknya nyusut sampe nabrak. Makanya tiap panel punya faktor value sendiri
     * buat compact — bukan angka acak, itu hasil ukur band glyph.
     */
    private void drawGaugePanel(Canvas c, RectF r, String value, int pct, String label, boolean compact) {
        float cx = r.centerX();
        float cy = r.top + r.height() * (compact ? 0.40f : 0.42f);
        float radius = Math.min(r.width(), r.height()) * (compact ? 0.28f : 0.34f);
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        c.drawArc(oval, -90, 360, false, pRingBg);
        float sweep = clampPct(pct) / 100f * 360f;
        c.drawArc(oval, -90, sweep, false, pRingFg);
        c.drawText(value, cx, cy + pValue.getTextSize() / 3f, pValue);
        c.drawText(label, cx, r.bottom - 8f * density, pLabel);
    }

    private void drawBarPanel(Canvas c, RectF r, String label, String value, int pct, boolean compact) {
        float cx = r.centerX();
        c.drawText(label, cx, r.top + 18f * density, pLabel);
        c.drawText(value, cx, r.top + r.height() * (compact ? 0.62f : 0.55f), pValue);

        float barW = r.width() - 16f * density;
        float barH = 6f * density;
        float barX = r.left + 8f * density;
        float barY = r.bottom - 16f * density;
        barRect.set(barX, barY, barX + barW, barY + barH);
        c.drawRoundRect(barRect, barH / 2f, barH / 2f, pBarBg);
        float fillW = barW * clampPct(pct) / 100f;
        if (fillW > 0) {
            barRect.set(barX, barY, barX + fillW, barY + barH);
            c.drawRoundRect(barRect, barH / 2f, barH / 2f, pBarFg);
        }
    }

    private void drawBattPanel(Canvas c, RectF r, String value, String pct, boolean charging, boolean compact) {
        float cx = r.centerX();
        c.drawText(charging ? "CHG" : "PWR", cx, r.top + 18f * density, pLabel);
        c.drawText(value, cx, r.top + r.height() * (compact ? 0.60f : 0.50f), pValue);
        if (pct != null && !pct.isEmpty()) {
            c.drawText(pct, cx, r.bottom - 10f * density, pLabel);
        }
    }

    /** Panel teks polos buat Renderer/Wrapper — chip label+value, nggak ada gauge. */
    private void drawTextPanel(Canvas c, RectF r, String label, String value, boolean compact) {
        float cx = r.centerX();
        c.drawText(label, cx, r.top + (compact ? 13f : 16f) * density, pLabel);
        c.drawText(value, cx, r.top + r.height() * (compact ? 0.70f : 0.62f), pValue);
    }

    private void drawFpsPanel(Canvas c, RectF r, String fpsVal, float[] graph, int gHead, int gBuf, float gMax, boolean compact) {
        float cx = r.centerX();
        c.drawText("FPS", cx, r.top + 16f * density, pLabel);
        c.drawText(fpsVal, cx, r.top + r.height() * (compact ? 0.74f : 0.50f), pValueBig);

        if (graph != null && !compact && gBuf > 1) {
            float gx = r.left + 10f * density;
            float gy = r.top + r.height() * 0.62f;
            float gw = r.width() - 20f * density;
            float gh = r.bottom - gy - 8f * density;
            int count = Math.min(gHead, gBuf);
            if (count >= 2 && gh > 4 && gMax > 0) {
                graphPath.reset();
                float bw = gw / (gBuf - 1);
                boolean first = true;
                for (int i = 0; i < count; i++) {
                    float v = graph[(gHead - count + i) % gBuf];
                    float px = gx + i * bw;
                    float py = gy + gh - (v / gMax) * gh;
                    if (first) { graphPath.moveTo(px, py); first = false; }
                    else graphPath.lineTo(px, py);
                }
                c.drawPath(graphPath, pGraph);
            }
        }
    }

    private void drawRowGauge(Canvas c, RectF r, String label, String value, int pct) {
        float cy = r.centerY();
        float radius = r.height() * 0.32f;
        float cx = r.left + radius + 10f * density;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        c.drawArc(oval, -90, 360, false, pRingBg);
        float sweep = clampPct(pct) / 100f * 360f;
        c.drawArc(oval, -90, sweep, false, pRingFg);

        Paint.Align la = pLabel.getTextAlign(), va = pValue.getTextAlign();
        pLabel.setTextAlign(Paint.Align.LEFT);
        pValue.setTextAlign(Paint.Align.LEFT);
        float textX = cx + radius + 10f * density;
        c.drawText(label, textX, cy - 2f * density, pLabel);
        c.drawText(value, textX, cy + pValue.getTextSize(), pValue);
        pLabel.setTextAlign(la);
        pValue.setTextAlign(va);
    }

    private void drawRowBar(Canvas c, RectF r, String label, String value, int pct) {
        Paint.Align la = pLabel.getTextAlign(), va = pValue.getTextAlign();
        pLabel.setTextAlign(Paint.Align.LEFT);
        pValue.setTextAlign(Paint.Align.LEFT);
        float textX = r.left + 10f * density;
        c.drawText(label + "  " + value, textX, r.top + r.height() * 0.42f, pValue);

        float barW = r.width() - 20f * density;
        float barH = 5f * density;
        float barX = r.left + 10f * density;
        float barY = r.bottom - 12f * density;
        barRect.set(barX, barY, barX + barW, barY + barH);
        c.drawRoundRect(barRect, barH / 2f, barH / 2f, pBarBg);
        float fillW = barW * clampPct(pct) / 100f;
        if (fillW > 0) {
            barRect.set(barX, barY, barX + fillW, barY + barH);
            c.drawRoundRect(barRect, barH / 2f, barH / 2f, pBarFg);
        }
        pLabel.setTextAlign(la);
        pValue.setTextAlign(va);
    }

    private void drawRowText(Canvas c, RectF r, String label, String value, String extra) {
        Paint.Align la = pLabel.getTextAlign(), va = pValue.getTextAlign();
        pLabel.setTextAlign(Paint.Align.LEFT);
        pValue.setTextAlign(Paint.Align.LEFT);
        float textX = r.left + 10f * density;
        c.drawText(label, textX, r.top + r.height() * 0.4f, pLabel);
        String full = value + (extra != null && !extra.isEmpty() ? " (" + extra + ")" : "");
        c.drawText(full, textX, r.bottom - 10f * density, pValue);
        pLabel.setTextAlign(la);
        pValue.setTextAlign(va);
    }

    private static float clampPct(int pct) {
        return Math.max(0, Math.min(100, pct));
    }
}
