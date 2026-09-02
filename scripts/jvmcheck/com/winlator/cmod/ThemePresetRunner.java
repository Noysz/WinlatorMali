package com.winlator.cmod;

/**
 * Menjalankan ThemePreset.java ASLI (bukan replika) di JVM biasa, pakai stub
 * android.graphics.Color, lalu MENCETAK nilai tiap role per preset.
 *
 * Kenapa penting: verifier sebelumnya (JS) cuma membuktikan ALGORITMA-nya bener. Runner ini
 * mengeksekusi kode yang beneran dikirim ke device, jadi kalau ada beda antara replika JS dan
 * Java (mis. pembulatan Math.round, urutan operasi float, int overflow), ketauan di sini.
 *
 * Output-nya dibandingkan dengan res/values/theme_overlays.xml oleh scripts/verify-theme-wiring.js.
 */
public final class ThemePresetRunner {

    private static final int[][] RAW = {
        // {onBg, onSurf, onSV, onPri, div} pakai -1 artinya "pakai default constructor simple"
        { 0xFF181A2F, 0xFF242E49, 0xFF37415C, 0xFFB4182D },
        { 0xFF30333A, 0xFF51403D, 0xFF72676F, 0xFFB79D9B },
        { 0xFF190019, 0xFF2B124C, 0xFF522B5B, 0xFF854F6C },
        { 0xFF2E365A, 0xFF3F5B8D, 0xFF6B597F, 0xFFBD6C73 },
        { 0xFF221820, 0xFF262F45, 0xFF45141B, 0xFFFE8492 },
        { 0xFF0E1F2F, 0xFF26425A, 0, 0xFFC38EB4 },          // surfaceVariant derived
        { 0xFF12232A, 0xFF2F3A32, 0xFF545748, 0xFFDB9F75 },
        { 0xFF1D1A39, 0xFF451952, 0xFF662549, 0xFFF39F5A },
        { 0xFF291C0E, 0xFF6E473B, 0xFFA78D78, 0xFFE1D4C2 }, // full ctor
        { 0xFF49225B, 0xFF6E3482, 0, 0xFFA56ABD },          // derived
        { 0xFF051F20, 0xFF0B2B26, 0xFF163832, 0xFF8EB69B },
        { 0xFF450714, 0xFF851636, 0, 0xFFCF325F },          // derived
        { 0xFF021024, 0xFF052659, 0, 0xFF5483B3 },          // derived
        { 0xFF4C1D3D, 0xFF852E4E, 0xFFA33757, 0xFFDC586D },
        // --- Batch 2 (index 14-17) ---
        { 0xFF192230, 0xFF2C2F38, 0xFF3F4952, 0xFFFFD001 },
        { 0xFF1E1E28, 0xFF272228, 0, 0xFFD6013B },          // derived
        { 0xFF3A3F43, 0xFF666C7B, 0, 0xFFDC5F00 },          // derived
        { 0xFF262236, 0xFF3D4F7E, 0, 0xFFE18546 }           // derived
    };

    private static final String[] NAMES = {
        "Crimson Dusk", "Mauve Ash", "Violet Cream", "Dusk Coral", "Navy Blush", "Twilight Rose",
        "Rust Pine", "Grape Sunset", "Cocoa Cream", "Orchid Lavender", "Emerald Depth",
        "Berry Pink", "Ocean Ice", "Peach Maroon",
        "Solar Slate", "Crimson Charcoal", "Urban Ember", "Midnight Amber"
    };

    public static void main(String[] args) {
        ThemePreset[] presets = new ThemePreset[RAW.length];
        for (int i = 0; i < RAW.length; i++) {
            int bg = RAW[i][0], su = RAW[i][1], sv = RAW[i][2], pri = RAW[i][3];
            // surfaceVariant == 0 artinya diturunkan pakai lerp, sama kaya di ThemeManager.PRESETS
            if (sv == 0) sv = ThemePreset.lerp(su, pri, 0.30f);
            if (i == 8) {
                // Cocoa Cream: satu-satunya yg pakai full constructor (onPrimary di-override)
                presets[i] = new ThemePreset(NAMES[i], bg, su, sv, pri,
                        0xFFFFFFFF, 0xFFE0E0E0, 0xFFAAAAAA, 0xFF2A1E12, 0xFF404040);
            } else {
                presets[i] = new ThemePreset(NAMES[i], bg, su, sv, pri);
            }
        }

        // Format: NAMA|role=AARRGGBB|... (dibaca verify-theme-wiring.js)
        for (ThemePreset t : presets) {
            StringBuilder sb = new StringBuilder(t.name);
            add(sb, "themeBackground", t.background);
            add(sb, "themeSurface", t.surface);
            add(sb, "themeSurfaceVariant", t.surfaceVariant);
            add(sb, "themeAccent", t.primary);
            add(sb, "themeOnBackground", t.onBackground);
            add(sb, "themeOnSurface", t.onSurface);
            add(sb, "themeOnSurfaceVariant", t.onSurfaceVariant);
            add(sb, "themeOnAccent", t.onPrimary);
            add(sb, "themeAccentDim", t.accentDim);
            add(sb, "themeAccentOnSurface", t.accentOnSurface);
            add(sb, "themeDivider", t.divider);
            add(sb, "themeSurfaceContainer", t.surfaceContainer);
            add(sb, "themeSurfaceContainerHigh", t.surfaceContainerHigh);
            add(sb, "themeSurfaceContainerHighest", t.surfaceContainerHighest);
            System.out.println(sb);
        }

        // Assert kontras langsung dari kode produksi, bukan dari replika.
        int bad = 0;
        for (ThemePreset t : presets) {
            bad += assertMin(t.name, "onBg/bg", ThemePreset.contrastRatio(t.onBackground, t.background), 4.5);
            bad += assertMin(t.name, "onSurf/surf", ThemePreset.contrastRatio(t.onSurface, t.surface), 4.5);
            bad += assertMin(t.name, "onSV/SV", ThemePreset.contrastRatio(t.onSurfaceVariant, t.surfaceVariant), 4.5);
            bad += assertMin(t.name, "onPrim/prim", ThemePreset.contrastRatio(t.onPrimary, t.primary), 4.5);
            bad += assertMin(t.name, "div/surf", ThemePreset.contrastRatio(t.divider, t.surface), 1.6);
            bad += assertMin(t.name, "accentOnSurf/surf", ThemePreset.contrastRatio(t.accentOnSurface, t.surface), 3.0);
        }
        System.out.println(bad == 0 ? "JAVA_CONTRAST_PASS" : "JAVA_CONTRAST_FAIL " + bad);
        if (bad != 0) System.exit(1);
    }

    private static void add(StringBuilder sb, String role, int color) {
        sb.append('|').append(role).append('=').append(String.format("#%08X", color));
    }

    private static int assertMin(String name, String label, double actual, double min) {
        if (actual >= min - 1e-9) return 0;
        System.out.println("CONTRAST_FAIL " + name + " " + label + " = " + actual + " < " + min);
        return 1;
    }
}
