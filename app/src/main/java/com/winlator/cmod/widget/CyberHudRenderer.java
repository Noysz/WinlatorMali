package com.winlator.cmod.widget;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Renderer untuk WinlatorHUD.STYLE_CYBER — panel gaya "MSI Afterburner" /
 * cyberpunk HUD (cut-corner panel, gauge lingkaran, accent ungu).
 *
 * Stateless terhadap data WinlatorHUD: semua value dilempar sebagai parameter,
 * jadi kelas ini nggak perlu akses field private WinlatorHUD sama sekali.
 *
 * <h3>Kontrak pemakaian (WAJIB urut)</h3>
 * <ol>
 *   <li>{@link #layout} — hitung geometri sekali, hasilnya disimpan di field.</li>
 *   <li>{@link #layoutWidth()} / {@link #layoutHeight()} — ukuran view.</li>
 *   <li>{@link #draw} — gambar, TANPA ngitung ulang geometri.</li>
 * </ol>
 * Alasannya: WinlatorHUD punya dua jalur ukur (onMeasure + redrawRunnable) dan
 * kalau dua-duanya ngitung sendiri terus beda hasil, relayout-nya nge-loop.
 * draw() sengaja nggak nge-layout lagi supaya nggak mungkin beda dari ukuran
 * yang udah dipakai buat setMeasuredDimension.
 *
 * Semua Path/RectF/Matrix di-cache sebagai field: onDraw dipanggil terus-menerus
 * lewat redraw loop WinlatorHUD, jadi alokasi per-frame bikin GC churn pas
 * main game.
 */
public class CyberHudRenderer {

    // ---------- palet ----------
    public static final int BEVEL_TOP   = Color.argb(222, 0x20, 0x20, 0x30);
    public static final int BEVEL_BOT   = Color.argb(222, 0x05, 0x05, 0x09);
    public static final int BEVEL_HI    = Color.argb(50, 255, 255, 255);
    public static final int BEVEL_LO    = Color.argb(120, 0, 0, 0);
    public static final int HALO_COLOR  = Color.argb(55, 0, 0, 0);
    public static final int BORDER      = Color.argb(140, 0x8B, 0x5C, 0xF6);
    public static final int ACCENT      = Color.rgb(0x8B, 0x5C, 0xF6);
    public static final int ACCENT_SOFT = Color.rgb(0xC4, 0xB5, 0xFD);
    public static final int RING_BG     = Color.argb(90, 255, 255, 255);
    public static final int TILE_BG     = Color.argb(12, 255, 255, 255);
    public static final int INNER_LINE  = Color.argb(60, 0x8B, 0x5C, 0xF6);
    public static final int TICK        = Color.argb(170, 0xC4, 0xB5, 0xFD);

    /**
     * Halo di luar batas panel. Canvas onDraw di-clip persis segede view, jadi
     * view harus diukur `isi + 2*HALO` dan semua panel digeser masuk sebanyak
     * HALO — kalau nggak, halonya ke-makan clip habis dan efek timbulnya ilang.
     */
    public static final float HALO = 2.5f;

    // ---------- ukuran panel (preset "S3", -31% dari versi pertama) ----------
    private static final float H_NORMAL      = 66f;
    private static final float ROW_H_NORMAL  = 44f;
    private static final float PANEL_W_GAUGE = 54f;
    private static final float PANEL_W_BAR   = 60f;
    private static final float PANEL_W_FPS   = 78f;
    private static final float PANEL_W_TEXT  = 58f;
    private static final float PANEL_W_VERT  = 124f;
    private static final float TEXT_PAD      = 20f;
    private static final float GAP           = 4f;
    private static final float CUT           = 6f;

    /**
     * Faktor skala isi panel. Offset absolut di dalam panel (baseline label,
     * tebal ring, padding bar) dulu di-tune buat tinggi 96dp; dikali K biar
     * hasil tuning itu kebawa proporsional ke ukuran baru, bukan dihitung ulang
     * dari nol. Text size TIDAK pakai K — nilainya ditentukan sendiri.
     */
    private static final float K = H_NORMAL / 96f;

    // ---------- grid card (mode compact) ----------
    private static final int   CARD_COLS     = 2;
    private static final float CARD_PAD      = 7f;
    private static final float CARD_CELL_W   = 58f;
    private static final float CARD_CELL_H   = 32f;
    private static final float CARD_DIV      = 1f;
    private static final float CARD_CUT      = 6f;
    private static final float CARD_TEXT_PAD = 7f;

    // ---------- state layout ----------
    private static final int MODE_H = 0, MODE_V = 1, MODE_CARD = 2;
    private static final int MAX_ITEMS = 7;
    private static final int K_GPU = 0, K_CPU = 1, K_RAM = 2, K_BATT = 3,
                             K_FPS = 4, K_API = 5, K_WRAP = 6;

    private final int[]   itemKind = new int[MAX_ITEMS];
    private final float[] itemX    = new float[MAX_ITEMS];
    private final float[] itemY    = new float[MAX_ITEMS];
    private final float[] itemW    = new float[MAX_ITEMS];
    private final float[] itemH    = new float[MAX_ITEMS];
    private int itemCount;
    private int mode = MODE_H;
    private int cardCols = 1, cardRows = 1;
    private float outW, outH;

    private final float density;
    private final Paint pBg        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHalo      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBevelHi   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBevelLo   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRingBg    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRingFg    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel     = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint pValue     = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint pValueBig  = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint pCardLabel = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint pCardValue = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint pBarBg     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBarFg     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGraph     = new Paint(Paint.ANTI_ALIAS_FLAG);
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

    /**
     * Gradient bevel dibikin sekali di rentang unit y=0..1, terus tiap panel
     * cuma di-remap lewat local matrix. Bikin LinearGradient baru per panel per
     * frame = alokasi di jalur onDraw, itu yang mau dihindari.
     */
    private final LinearGradient bgShader;
    private final Matrix bgMatrix = new Matrix();

    public CyberHudRenderer(float density) {
        this.density = density;
        Typeface mono  = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        Typeface monoR = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL);

        bgShader = new LinearGradient(0, 0, 0, 1, BEVEL_TOP, BEVEL_BOT, Shader.TileMode.CLAMP);
        pBg.setStyle(Paint.Style.FILL);
        pBg.setShader(bgShader);

        pHalo.setStyle(Paint.Style.FILL);
        pHalo.setColor(HALO_COLOR);

        pBevelHi.setStyle(Paint.Style.STROKE);
        pBevelHi.setStrokeWidth(1.3f * density);
        pBevelHi.setColor(BEVEL_HI);

        pBevelLo.setStyle(Paint.Style.STROKE);
        pBevelLo.setStrokeWidth(1.3f * density);
        pBevelLo.setColor(BEVEL_LO);

        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(1.4f * density);
        pBorder.setColor(BORDER);

        pRingBg.setStyle(Paint.Style.STROKE);
        pRingBg.setStrokeWidth(4f * K * density);
        pRingBg.setColor(RING_BG);

        pRingFg.setStyle(Paint.Style.STROKE);
        pRingFg.setStrokeWidth(4f * K * density);
        pRingFg.setStrokeCap(Paint.Cap.ROUND);
        pRingFg.setColor(ACCENT);

        pLabel.setTextSize(8f * density);
        pLabel.setTypeface(monoR);
        pLabel.setColor(Color.argb(200, 255, 255, 255));
        pLabel.setTextAlign(Paint.Align.CENTER);

        pValue.setTextSize(10.5f * density);
        pValue.setTypeface(mono);
        pValue.setColor(Color.WHITE);
        pValue.setTextAlign(Paint.Align.CENTER);

        pValueBig.setTextSize(14f * density);
        pValueBig.setTypeface(mono);
        pValueBig.setColor(ACCENT_SOFT);
        pValueBig.setTextAlign(Paint.Align.CENTER);

        pCardLabel.setTextSize(8.5f * density);
        pCardLabel.setTypeface(monoR);
        pCardLabel.setColor(Color.argb(200, 255, 255, 255));
        pCardLabel.setTextAlign(Paint.Align.LEFT);

        pCardValue.setTextSize(12f * density);
        pCardValue.setTypeface(mono);
        pCardValue.setColor(Color.WHITE);
        pCardValue.setTextAlign(Paint.Align.LEFT);

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

    // =====================================================================
    // LAYOUT
    // =====================================================================

    /**
     * Hitung posisi semua panel. Deterministik dari parameter — dua jalur ukur
     * WinlatorHUD manggil ini dengan input yang sama, jadi hasilnya nggak
     * mungkin beda.
     *
     * @param compact  true = semua metrik digabung jadi SATU grid card
     * @param budgetW  lebar layar yang boleh dipakai (px, sebelum scale view);
     *                 0 atau negatif = tak dibatasi, nggak ada wrap
     * @param budgetH  tinggi layar yang boleh dipakai (px), idem
     */
    public void layout(boolean vertical, boolean compact,
                       boolean showGpu, boolean showCpu, boolean showRam,
                       boolean showBatt, boolean showFps,
                       boolean showRend, String rendVal,
                       boolean showWrap, String wrapVal,
                       float budgetW, float budgetH) {
        float d = density;
        float halo = HALO * d;
        float gap = GAP * d;
        itemCount = 0;

        if (compact) {
            mode = MODE_CARD;
            if (showGpu)  push(K_GPU);
            if (showCpu)  push(K_CPU);
            if (showRam)  push(K_RAM);
            if (showBatt) push(K_BATT);
            if (showFps)  push(K_FPS);
            if (showRend) push(K_API);
            if (showWrap) push(K_WRAP);

            float cw = CARD_CELL_W * d;
            if (showRend) cw = Math.max(cw, cardCellWidth(rendVal));
            if (showWrap) cw = Math.max(cw, cardCellWidth(wrapVal));
            float ch = CARD_CELL_H * d;
            float pad = CARD_PAD * d;
            float div = CARD_DIV * d;

            cardCols = Math.max(1, Math.min(CARD_COLS, itemCount));
            cardRows = Math.max(1, (itemCount + cardCols - 1) / cardCols);
            for (int i = 0; i < itemCount; i++) {
                itemX[i] = halo + pad + (i % cardCols) * (cw + div);
                itemY[i] = halo + pad + (i / cardCols) * (ch + div);
                itemW[i] = cw;
                itemH[i] = ch;
            }
            outW = 2 * halo + 2 * pad + cardCols * cw + (cardCols - 1) * div;
            outH = 2 * halo + 2 * pad + cardRows * ch + (cardRows - 1) * div;
            return;
        }

        if (vertical) {
            mode = MODE_V;
            // Urutan row: API/WRAP di atas (kebaca kayak header), gauge nyusul.
            // Beda dari mode horizontal (di sana nempel di ujung kanan) dan itu
            // memang disengaja.
            if (showRend) push(K_API);
            if (showWrap) push(K_WRAP);
            if (showGpu)  push(K_GPU);
            if (showCpu)  push(K_CPU);
            if (showRam)  push(K_RAM);
            if (showBatt) push(K_BATT);
            if (showFps)  push(K_FPS);

            float rowH = ROW_H_NORMAL * d;
            float vw = PANEL_W_VERT * d;
            // Panel metrik lebarnya konstan, tapi row API/WRAP isinya teks bebas
            // ("+OpenGL", "DXVK 2.3"). Tanpa dilebarin, teks panjang ke-potong.
            if (showRend) vw = Math.max(vw, rowTextWidth(rendVal));
            if (showWrap) vw = Math.max(vw, rowTextWidth(wrapVal));

            if (itemCount == 0) {
                outW = vw + 2 * halo;
                outH = rowH + 2 * halo;
                return;
            }

            // Bagi row ke kolom: ambil jumlah kolom MINIMUM yang muat, terus
            // di-balance rata. Greedy (isi kolom pertama sampai mentok) bikin
            // kolomnya setinggi budget alias mepet tepi layar; balance mangkas
            // tingginya tanpa nambah kolom.
            float budget = budgetH - 2 * halo;
            int fit = itemCount;
            if (budget > 0) fit = Math.max(1, (int) ((budget + gap) / (rowH + gap)));
            int ncol = Math.max(1, (itemCount + fit - 1) / fit);
            int perCol = Math.max(1, (itemCount + ncol - 1) / ncol);

            for (int i = 0; i < itemCount; i++) {
                itemX[i] = halo + (i / perCol) * (vw + gap);
                itemY[i] = halo + (i % perCol) * (rowH + gap);
                itemW[i] = vw;
                itemH[i] = rowH;
            }
            int rowsUsed = Math.min(perCol, itemCount);
            outW = 2 * halo + ncol * vw + (ncol - 1) * gap;
            outH = 2 * halo + rowsUsed * rowH + (rowsUsed - 1) * gap;
            return;
        }

        mode = MODE_H;
        float h = H_NORMAL * d;
        if (showGpu)  { push(K_GPU);  itemW[itemCount - 1] = PANEL_W_GAUGE * d; }
        if (showCpu)  { push(K_CPU);  itemW[itemCount - 1] = PANEL_W_GAUGE * d; }
        if (showRam)  { push(K_RAM);  itemW[itemCount - 1] = PANEL_W_BAR   * d; }
        if (showBatt) { push(K_BATT); itemW[itemCount - 1] = PANEL_W_BAR   * d; }
        if (showFps)  { push(K_FPS);  itemW[itemCount - 1] = PANEL_W_FPS   * d; }
        if (showRend) { push(K_API);  itemW[itemCount - 1] = textPanelWidth(rendVal); }
        if (showWrap) { push(K_WRAP); itemW[itemCount - 1] = textPanelWidth(wrapVal); }

        if (itemCount == 0) {
            outW = 40f * d + 2 * halo;
            outH = h + 2 * halo;
            return;
        }

        // Greedy: isi baris sampai mentok budget, baru pindah baris.
        float budget = budgetW - 2 * halo;
        float y = halo, rowW = 0, maxW = 0;
        int rows = 1;
        for (int i = 0; i < itemCount; i++) {
            float lead = rowW > 0 ? gap : 0;
            if (rowW > 0 && budget > 0 && rowW + lead + itemW[i] > budget) {
                maxW = Math.max(maxW, rowW);
                rows++;
                y += h + gap;
                rowW = 0;
                lead = 0;
            }
            itemX[i] = halo + rowW + lead;
            itemY[i] = y;
            itemH[i] = h;
            rowW += lead + itemW[i];
        }
        maxW = Math.max(maxW, rowW);
        outW = Math.max(maxW, 40f * d) + 2 * halo;
        outH = rows * h + (rows - 1) * gap + 2 * halo;
    }

    public float layoutWidth()  { return outW; }
    public float layoutHeight() { return outH; }

    private void push(int kind) {
        if (itemCount >= MAX_ITEMS) return;
        itemKind[itemCount++] = kind;
    }

    /**
     * Panel API/WRAP isinya teks bebas, jadi lebarnya ikut teks — panel lain
     * konstan. measureText() harus pakai paint yang sama yang nanti nggambar,
     * biar hasil ukur == hasil gambar.
     */
    private float textPanelWidth(String s) {
        return Math.max(PANEL_W_TEXT * density, measure(pValue, s) + TEXT_PAD * K * density);
    }

    /** drawRowText() nulis value di left+10K dp; sisakan 10K dp lagi di kanan. */
    private float rowTextWidth(String s) {
        return TEXT_PAD * K * density + measure(pValue, s);
    }

    private float cardCellWidth(String s) {
        return measure(pCardValue, s) + CARD_TEXT_PAD * density;
    }

    private static float measure(Paint p, String s) {
        return s == null || s.isEmpty() ? 0 : p.measureText(s);
    }

    // =====================================================================
    // DRAW
    // =====================================================================

    /**
     * Gambar hasil {@link #layout} terakhir. Sengaja NGGAK nge-layout ulang:
     * kalau ukuran parent kebetulan berubah antara measure dan draw, layout
     * baru bisa mutusin wrap yang beda dari ukuran view yang udah di-set, dan
     * hasilnya ke-clip. redrawRunnable WinlatorHUD manggil layout() tiap tick
     * sebelum invalidate(), jadi state-nya selalu segar.
     */
    public void draw(Canvas c,
                     String gpuVal, int gpuPct,
                     String cpuVal, int cpuPct, String cpuTemp,
                     String ramVal, int ramPct,
                     String battVal, String battPct, boolean charging,
                     String fpsVal, float[] graph, int gHead, int gBuf, float gMax,
                     String rendVal, String wrapVal) {

        if (itemCount == 0) {
            // Semua toggle mati: gambar shell kosong biar HUD masih kelihatan
            // dan masih bisa di-tap/di-drag.
            float halo = HALO * density;
            panelRect.set(halo, halo, outW - halo, outH - halo);
            drawPanel(c, panelRect, CUT * density, mode != MODE_CARD);
            return;
        }

        if (mode == MODE_CARD) {
            drawCard(c, gpuVal, gpuPct, cpuVal, cpuPct, ramVal, ramPct,
                     battVal, charging, fpsVal, rendVal, wrapVal);
            return;
        }

        float cut = CUT * density;
        RectF r = panelRect;
        for (int i = 0; i < itemCount; i++) {
            r.set(itemX[i], itemY[i], itemX[i] + itemW[i], itemY[i] + itemH[i]);
            drawPanel(c, r, cut, true);
            if (mode == MODE_V) {
                switch (itemKind[i]) {
                    case K_API:  drawRowText(c, r, "API", rendVal, null); break;
                    case K_WRAP: drawRowText(c, r, "WRAP", wrapVal, null); break;
                    case K_GPU:  drawRowGauge(c, r, "GPU", gpuVal, gpuPct); break;
                    case K_CPU:  drawRowGauge(c, r, cpuLabel(cpuTemp), cpuVal, cpuPct); break;
                    case K_RAM:  drawRowBar(c, r, "RAM", ramVal, ramPct); break;
                    case K_BATT: drawRowText(c, r, charging ? "CHG" : "PWR", battVal, battPct); break;
                    case K_FPS:  drawRowText(c, r, "FPS", fpsVal, null); break;
                }
            } else {
                switch (itemKind[i]) {
                    case K_GPU:  drawGaugePanel(c, r, gpuVal, gpuPct, "GPU"); break;
                    case K_CPU:  drawGaugePanel(c, r, cpuVal, cpuPct, cpuLabel(cpuTemp)); break;
                    case K_RAM:  drawBarPanel(c, r, "RAM", ramVal, ramPct); break;
                    case K_BATT: drawBattPanel(c, r, battVal, battPct, charging); break;
                    case K_FPS:  drawFpsPanel(c, r, fpsVal, graph, gHead, gBuf, gMax); break;
                    case K_API:  drawTextPanel(c, r, "API", rendVal); break;
                    case K_WRAP: drawTextPanel(c, r, "WRAP", wrapVal); break;
                }
            }
        }
    }

    private static String cpuLabel(String cpuTemp) {
        return cpuTemp != null && !cpuTemp.isEmpty() ? "CPU " + cpuTemp : "CPU";
    }

    // ---------- grid card ----------

    /**
     * Mode compact = SATU card, tiap metrik jadi cell grid 2 kolom. Ini ganti
     * 7 panel terpisah versi sebelumnya: horizontal-nya nggak kepanjangan dan
     * vertical-nya nggak kepotong, area ~50% lebih kecil.
     *
     * Card cuma dapet satu border + chamfer (detail=false) — tick, inner
     * hairline, dan accent bar dimatiin karena di ukuran cell segini malah rame.
     */
    private void drawCard(Canvas c,
                          String gpuVal, int gpuPct, String cpuVal, int cpuPct,
                          String ramVal, int ramPct, String battVal, boolean charging,
                          String fpsVal, String rendVal, String wrapVal) {
        float d = density;
        float halo = HALO * d;
        panelRect.set(halo, halo, outW - halo, outH - halo);
        drawPanel(c, panelRect, CARD_CUT * d, false);

        float div = CARD_DIV * d;
        for (int i = 0; i < itemCount; i++) {
            float cl = itemX[i], ct = itemY[i], cw = itemW[i], ch = itemH[i];
            float tx = cl + 2f * d;
            String label, value;
            int pct = -1;
            switch (itemKind[i]) {
                case K_GPU:  label = "GPU";  value = gpuVal;  pct = gpuPct; break;
                case K_CPU:  label = "CPU";  value = cpuVal;  pct = cpuPct; break;
                case K_RAM:  label = "RAM";  value = ramVal;  pct = ramPct; break;
                case K_BATT: label = charging ? "CHG" : "PWR"; value = battVal; break;
                case K_FPS:  label = "FPS";  value = fpsVal;  break;
                case K_API:  label = "API";  value = rendVal; break;
                default:     label = "WRAP"; value = wrapVal; break;
            }
            c.drawText(label, tx, ct + 9f * d, pCardLabel);
            if (value != null) c.drawText(value, tx, ct + 22f * d, pCardValue);

            if (pct >= 0) {
                float by = ct + 26.5f * d;
                float bh = 2.5f * d;
                float bw = cw - 5f * d;
                barRect.set(tx, by, tx + bw, by + bh);
                c.drawRect(barRect, pBarBg);
                float fillW = bw * clampPct(pct) / 100f;
                if (fillW > 0) {
                    barRect.set(tx, by, tx + fillW, by + bh);
                    c.drawRect(barRect, pBarFg);
                }
            }

            // hairline pemisah antar cell — bukan grid penuh, cuma di sisi yang
            // punya tetangga, biar tepi card tetap bersih
            int cc = i % cardCols;
            if (cc < cardCols - 1 && i + 1 < itemCount) {
                float x = cl + cw + div / 2f;
                c.drawLine(x, ct + 3f * d, x, ct + ch - 3f * d, pInner);
            }
            if (i + cardCols < itemCount) {
                float y = ct + ch + div / 2f;
                c.drawLine(cl + 2f * d, y, cl + cw - 2f * d, y, pInner);
            }
        }
    }

    // ---------- shell ----------

    /**
     * Chamfer cuma di dua sudut berlawanan (kanan-atas + kiri-bawah), sudut
     * kiri-atas & kanan-bawah tetap kotak — bentuk asimetris ini disengaja,
     * ngikut gambar referensi. Jangan "dirapikan" jadi empat sudut simetris.
     *
     * Bevel "B2" yang dipilih user: halo gelap di luar, bg gradient terang ke
     * gelap, highlight tipis di tepi dalam atas + shadow di tepi dalam bawah.
     * Efeknya panel kelihatan timbul tanpa nambah opasitas — layar game masih
     * kebaca di belakangnya (bg alpha 222/255).
     *
     * Semua garis digeser masuk setengah stroke terlebar: canvas onDraw di-clip
     * persis segede view, stroke digambar center di garisnya, jadi kalau
     * garisnya nempel tepi separuh tebalnya ke-clip (bracket 2dp keliatan 1dp).
     * Fill nggak kena, dia nggak lewat batas path.
     */
    private void drawPanel(Canvas c, RectF r, float cut, boolean detail) {
        float d = density;
        float half = 1f * d;
        float l = r.left + half, t = r.top + half;
        float rt = r.right - half, b = r.bottom - half;

        float s = HALO * d;
        buildPanelPath(r.left - s, r.top - s, r.right + s, r.bottom + s, cut);
        c.drawPath(reusablePath, pHalo);

        bgMatrix.setScale(1f, r.height());
        bgMatrix.postTranslate(0f, r.top);
        bgShader.setLocalMatrix(bgMatrix);
        pBg.setShader(bgShader);
        buildPanelPath(r.left, r.top, r.right, r.bottom, cut);
        c.drawPath(reusablePath, pBg);

        float tileInset = 2.5f * d;
        buildPanelPath(r.left + tileInset, r.top + tileInset,
                       r.right - tileInset, r.bottom - tileInset, cut);
        c.drawPath(reusablePath, pTile);

        buildPanelPath(l, t, rt, b, cut);
        c.drawPath(reusablePath, pBorder);

        float bw = 1.3f * d;
        c.drawLine(l + cut, t + bw, rt - cut, t + bw, pBevelHi);
        c.drawLine(l + cut, b - bw, rt - cut, b - bw, pBevelLo);

        // chamfer dikasih accent terang biar potongan sudutnya kebaca sengaja
        c.drawLine(rt - cut, t, rt, t + cut, pChamfer);
        c.drawLine(l + cut, b, l, b - cut, pChamfer);

        // bracket cuma di dua sudut yang tetap kotak, jadi nggak nabrak chamfer
        float arm = 12f * K * d;
        c.drawLine(l, t + arm, l, t, pBracket);
        c.drawLine(l, t, l + arm, t, pBracket);
        c.drawLine(rt, b - arm, rt, b, pBracket);
        c.drawLine(rt, b, rt - arm, b, pBracket);

        if (!detail) return;

        float innerInset = 3f * d;
        buildPanelPath(r.left + innerInset, r.top + innerInset,
                       r.right - innerInset, r.bottom - innerInset, cut);
        c.drawPath(reusablePath, pInner);

        float tickX = r.left + 18f * d;
        float tickH = 4f * d;
        for (int i = 0; i < 3; i++) {
            c.drawLine(tickX, t, tickX, t + tickH, pTick);
            tickX += 4.5f * d;
        }

        float barX = l + arm + 2f * d;
        float barEnd = Math.min(barX + r.width() * 0.34f, rt - cut);
        barRect.set(barX, t, barEnd, t + 2.5f * d);
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

    // ---------- isi panel mode horizontal ----------

    /**
     * Offset absolut di dalam panel dikali {@link #K}. Dulu label ditaruh 16-18dp
     * dari tepi atas (absolut) sementara value di 0.5×h (proporsional) — pas
     * tingginya nyusut cuma value yang naik, sampai nabrak label. Dengan K,
     * dua-duanya nyusut bareng.
     */
    private void drawGaugePanel(Canvas c, RectF r, String value, int pct, String label) {
        float cx = r.centerX();
        float cy = r.top + r.height() * 0.42f;
        float radius = Math.min(r.width(), r.height()) * 0.34f;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        c.drawArc(oval, -90, 360, false, pRingBg);
        float sweep = clampPct(pct) / 100f * 360f;
        c.drawArc(oval, -90, sweep, false, pRingFg);
        if (value != null) c.drawText(value, cx, cy + pValue.getTextSize() / 3f, pValue);
        c.drawText(label, cx, r.bottom - 8f * K * density, pLabel);
    }

    private void drawBarPanel(Canvas c, RectF r, String label, String value, int pct) {
        float d = density;
        float cx = r.centerX();
        c.drawText(label, cx, r.top + 18f * K * d, pLabel);
        if (value != null) c.drawText(value, cx, r.top + r.height() * 0.55f, pValue);

        float barW = r.width() - 16f * K * d;
        float barH = 6f * K * d;
        float barX = r.left + 8f * K * d;
        float barY = r.bottom - 16f * K * d;
        barRect.set(barX, barY, barX + barW, barY + barH);
        c.drawRoundRect(barRect, barH / 2f, barH / 2f, pBarBg);
        float fillW = barW * clampPct(pct) / 100f;
        if (fillW > 0) {
            barRect.set(barX, barY, barX + fillW, barY + barH);
            c.drawRoundRect(barRect, barH / 2f, barH / 2f, pBarFg);
        }
    }

    private void drawBattPanel(Canvas c, RectF r, String value, String pct, boolean charging) {
        float d = density;
        float cx = r.centerX();
        c.drawText(charging ? "CHG" : "PWR", cx, r.top + 18f * K * d, pLabel);
        if (value != null) c.drawText(value, cx, r.top + r.height() * 0.50f, pValue);
        if (pct != null && !pct.isEmpty()) {
            c.drawText(pct, cx, r.bottom - 10f * K * d, pLabel);
        }
    }

    /** Panel teks polos buat Renderer/Wrapper — chip label+value, nggak ada gauge. */
    private void drawTextPanel(Canvas c, RectF r, String label, String value) {
        float cx = r.centerX();
        c.drawText(label, cx, r.top + 16f * K * density, pLabel);
        if (value != null) c.drawText(value, cx, r.top + r.height() * 0.62f, pValue);
    }

    private void drawFpsPanel(Canvas c, RectF r, String fpsVal, float[] graph,
                              int gHead, int gBuf, float gMax) {
        float d = density;
        float cx = r.centerX();
        c.drawText("FPS", cx, r.top + 16f * K * d, pLabel);
        if (fpsVal != null) c.drawText(fpsVal, cx, r.top + r.height() * 0.50f, pValueBig);

        if (graph != null && gBuf > 1) {
            float gx = r.left + 10f * K * d;
            float gy = r.top + r.height() * 0.62f;
            float gw = r.width() - 20f * K * d;
            float gh = r.bottom - gy - 8f * K * d;
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

    // ---------- isi row mode vertikal ----------

    private void drawRowGauge(Canvas c, RectF r, String label, String value, int pct) {
        float d = density;
        float cy = r.centerY();
        float radius = r.height() * 0.32f;
        float cx = r.left + radius + 10f * K * d;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        c.drawArc(oval, -90, 360, false, pRingBg);
        float sweep = clampPct(pct) / 100f * 360f;
        c.drawArc(oval, -90, sweep, false, pRingFg);

        Paint.Align la = pLabel.getTextAlign(), va = pValue.getTextAlign();
        pLabel.setTextAlign(Paint.Align.LEFT);
        pValue.setTextAlign(Paint.Align.LEFT);
        float textX = cx + radius + 10f * K * d;
        c.drawText(label, textX, cy - 2f * K * d, pLabel);
        if (value != null) c.drawText(value, textX, cy + pValue.getTextSize(), pValue);
        pLabel.setTextAlign(la);
        pValue.setTextAlign(va);
    }

    private void drawRowBar(Canvas c, RectF r, String label, String value, int pct) {
        float d = density;
        Paint.Align la = pLabel.getTextAlign(), va = pValue.getTextAlign();
        pLabel.setTextAlign(Paint.Align.LEFT);
        pValue.setTextAlign(Paint.Align.LEFT);
        float textX = r.left + 10f * K * d;
        c.drawText(label + "  " + (value == null ? "" : value), textX,
                   r.top + r.height() * 0.42f, pValue);

        float barW = r.width() - 20f * K * d;
        float barH = 5f * K * d;
        float barY = r.bottom - 12f * K * d;
        barRect.set(textX, barY, textX + barW, barY + barH);
        c.drawRoundRect(barRect, barH / 2f, barH / 2f, pBarBg);
        float fillW = barW * clampPct(pct) / 100f;
        if (fillW > 0) {
            barRect.set(textX, barY, textX + fillW, barY + barH);
            c.drawRoundRect(barRect, barH / 2f, barH / 2f, pBarFg);
        }
        pLabel.setTextAlign(la);
        pValue.setTextAlign(va);
    }

    private void drawRowText(Canvas c, RectF r, String label, String value, String extra) {
        float d = density;
        Paint.Align la = pLabel.getTextAlign(), va = pValue.getTextAlign();
        pLabel.setTextAlign(Paint.Align.LEFT);
        pValue.setTextAlign(Paint.Align.LEFT);
        float textX = r.left + 10f * K * d;
        c.drawText(label, textX, r.top + r.height() * 0.4f, pLabel);
        String full = (value == null ? "" : value)
                    + (extra != null && !extra.isEmpty() ? " (" + extra + ")" : "");
        c.drawText(full, textX, r.bottom - 10f * K * d, pValue);
        pLabel.setTextAlign(la);
        pValue.setTextAlign(va);
    }

    private static float clampPct(int pct) {
        return Math.max(0, Math.min(100, pct));
    }
}
