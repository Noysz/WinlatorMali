// Generator res/values/theme_overlays.xml dari daftar preset.
//
// KENAPA ADA FILE INI:
// Warna preset dihitung di Java (ThemePreset.ensureReadable — kontras dijamin).
// Tapi 61 referensi `?attr/` di layout/drawable cuma bisa baca nilai dari XML theme,
// dan `?attr/` TIDAK bisa di-set runtime dari SharedPreferences. Jalan satu-satunya di
// app View-based: theme overlay statis per preset, di-apply pakai Theme.applyStyle().
//
// Kalau hex-nya diketik tangan ke XML, nilainya bisa melenceng dari Java tanpa ada yg
// tau. Jadi: JAVA = SATU-SATUNYA SUMBER, XML = artifact hasil generate yg di-commit,
// dan /tmp/themepreset-verify.js ngecek dua-duanya identik.
//
// Jalanin ulang tiap kali PRESETS atau logic ThemePreset diubah:
//   node scripts/gen-theme-overlays.js
//
// CATATAN: logic warna di bawah adalah REPLIKA ThemePreset.java. Ubah salah satu tanpa
// yg lain = XML jadi bohong. Verifier akan gagal kalau itu kejadian.
'use strict';
const fs = require('fs');
const path = require('path');

// ---------- replika ThemePreset.java ----------
// PRESISI: `t` di lerp() Java bertipe `float` (32-bit), bukan double. Beda ini NYATA, bukan
// teoretis: pada Grape Sunset, (0-90) * 0.55f == -49.5 tepat di Java, tapi
// (0-90) * 0.55 == -49.50000000000001 di JS -> Math.round ngasih 40, Java ngasih 41.
// Dua nilai accentDim pernah melenceng 1 karena ini. Math.fround() memaksa pembulatan ke
// float32 di tiap langkah, jadi hasilnya identik dengan Java.
const f32 = Math.fround;
const rgb = (v) => [(v >> 16) & 255, (v >> 8) & 255, v & 255];
const hex = (v) => '#' + (v >>> 0).toString(16).padStart(8, '0').toUpperCase();
const linearize = (c8) => { const c = c8 / 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); };
const relLum = (c) => { const [r, g, b] = rgb(c); return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b); };
const contrastRatio = (a, b) => { const [x, y] = [relLum(a), relLum(b)]; return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
// Tiap operasi di-fround: meniru aritmetika float Java persis (channel + (channel-channel) * t).
const lerpChan = (a, b, t) => Math.round(f32(f32(a) + f32(f32(f32(b) - f32(a)) * f32(t))));
const lerp = (A, B, t) => {
    const [ar, ag, ab] = rgb(A), [br, bg, bb] = rgb(B);
    const al = lerpChan((A >>> 24) & 255, (B >>> 24) & 255, t);
    return ((al << 24) | (lerpChan(ar, br, t) << 16) | (lerpChan(ag, bg, t) << 8) | lerpChan(ab, bb, t)) >>> 0;
};
const bestOnColor = (bg) => contrastRatio(0xFF000000, bg) >= contrastRatio(0xFFFFFFFF, bg) ? 0xFF000000 : 0xFFFFFFFF;
const ensureReadable = (proposed, onTopOf, target) => {
    if (contrastRatio(proposed, onTopOf) >= target) return proposed;
    const pole = bestOnColor(onTopOf);
    for (let step = 1; step <= 50; step++) {
        const cand = lerp(proposed, pole, step / 50);
        if (contrastRatio(cand, onTopOf) >= target) return cand;
    }
    return pole;
};
const TARGET_TEXT = 4.5, TARGET_COMPONENT = 3.0, TARGET_DIVIDER = 1.6;
const D_ON_SURF = 0xFFE0E0E0, D_ON_SV = 0xFFAAAAAA, D_ON_BG = 0xFFFFFFFF, D_ON_PRI = 0xFFFFFFFF, D_DIV = 0xFF404040;

function makePreset(p) {
    const [name, background, surface, surfaceVariant, primary] = p;
    const pOnBg = p[5] ?? D_ON_BG, pOnSurf = p[6] ?? D_ON_SURF, pOnSv = p[7] ?? D_ON_SV,
          pOnPri = p[8] ?? D_ON_PRI, pDiv = p[9] ?? D_DIV;
    const onSurface = ensureReadable(pOnSurf, surface, TARGET_TEXT);
    return {
        name, background, surface, surfaceVariant, primary,
        onBackground: ensureReadable(pOnBg, background, TARGET_TEXT),
        onSurface,
        onSurfaceVariant: ensureReadable(pOnSv, surfaceVariant, TARGET_TEXT),
        onPrimary: ensureReadable(pOnPri, primary, TARGET_TEXT),
        divider: ensureReadable(pDiv, surface, TARGET_DIVIDER),
        accentDim: lerp(primary, 0xFF000000, 0.55),
        accentOnSurface: ensureReadable(primary, surface, TARGET_COMPONENT),
        surfaceContainer: lerp(surface, onSurface, 0.05),
        surfaceContainerHigh: lerp(surface, onSurface, 0.09),
        surfaceContainerHighest: lerp(surface, onSurface, 0.14)
    };
}

// ---------- 18 preset: HARUS identik dgn ThemeManager.PRESETS ----------
// Urutan penting: index-nya yg disimpen di SharedPreferences. Jangan disusun ulang —
// user yg udah milih preset #5 akan dapet preset lain kalau urutannya geser.
const RAW = [
    ['Crimson Dusk', 0xFF181A2F, 0xFF242E49, 0xFF37415C, 0xFFB4182D],
    ['Mauve Ash', 0xFF30333A, 0xFF51403D, 0xFF72676F, 0xFFB79D9B],
    ['Violet Cream', 0xFF190019, 0xFF2B124C, 0xFF522B5B, 0xFF854F6C],
    ['Dusk Coral', 0xFF2E365A, 0xFF3F5B8D, 0xFF6B597F, 0xFFBD6C73],
    ['Navy Blush', 0xFF221820, 0xFF262F45, 0xFF45141B, 0xFFFE8492],
    ['Twilight Rose', 0xFF0E1F2F, 0xFF26425A, lerp(0xFF26425A, 0xFFC38EB4, 0.30), 0xFFC38EB4],
    ['Rust Pine', 0xFF12232A, 0xFF2F3A32, 0xFF545748, 0xFFDB9F75],
    ['Grape Sunset', 0xFF1D1A39, 0xFF451952, 0xFF662549, 0xFFF39F5A],
    ['Cocoa Cream', 0xFF291C0E, 0xFF6E473B, 0xFFA78D78, 0xFFE1D4C2, D_ON_BG, D_ON_SURF, D_ON_SV, 0xFF2A1E12, D_DIV],
    ['Orchid Lavender', 0xFF49225B, 0xFF6E3482, lerp(0xFF6E3482, 0xFFA56ABD, 0.30), 0xFFA56ABD],
    ['Emerald Depth', 0xFF051F20, 0xFF0B2B26, 0xFF163832, 0xFF8EB69B],
    ['Berry Pink', 0xFF450714, 0xFF851636, lerp(0xFF851636, 0xFFCF325F, 0.30), 0xFFCF325F],
    ['Ocean Ice', 0xFF021024, 0xFF052659, lerp(0xFF052659, 0xFF5483B3, 0.30), 0xFF5483B3],
    ['Peach Maroon', 0xFF4C1D3D, 0xFF852E4E, 0xFFA33757, 0xFFDC586D],
    // --- Batch 2 (index 14-17) ---
    ['Solar Slate', 0xFF192230, 0xFF2C2F38, 0xFF3F4952, 0xFFFFD001],
    ['Crimson Charcoal', 0xFF1E1E28, 0xFF272228, lerp(0xFF272228, 0xFFD6013B, 0.30), 0xFFD6013B],
    ['Urban Ember', 0xFF3A3F43, 0xFF666C7B, lerp(0xFF666C7B, 0xFFDC5F00, 0.30), 0xFFDC5F00],
    ['Midnight Amber', 0xFF262236, 0xFF3D4F7E, lerp(0xFF3D4F7E, 0xFFE18546, 0.30), 0xFFE18546]
];

// slug buat nama style: "Crimson Dusk" -> "CrimsonDusk"
const slug = (n) => n.replace(/[^A-Za-z0-9]/g, '');

const presets = RAW.map(makePreset);

const lines = [];
lines.push('<?xml version="1.0" encoding="utf-8"?>');
lines.push('<!--');
lines.push('  GENERATED FILE - DO NOT EDIT BY HAND.');
lines.push('  Regenerate with: node scripts/gen-theme-overlays.js');
lines.push('');
lines.push('  One theme overlay per preset, applied at runtime with');
lines.push('  Theme.applyStyle(styleId, true) from ThemeManager.applyTheme(). This exists');
lines.push('  because `?attr/` values are resolved from the theme and cannot be set from');
lines.push('  SharedPreferences directly, so the 61 `?attr/theme*` references across');
lines.push('  layout/ and drawable/ can only follow the selected preset through a theme.');
lines.push('');
lines.push('  Colors here are computed by ThemePreset (contrast-enforced), NOT hand-picked.');
lines.push('  ThemeManager.PRESETS is the single source of truth; this file mirrors it.');
lines.push('');
lines.push('  Style order matches PRESETS order, and the index is persisted in');
lines.push('  SharedPreferences - do not reorder, or existing users get a different theme.');
lines.push('-->');
lines.push('<resources>');

presets.forEach((t, i) => {
    lines.push('');
    lines.push(`    <!-- ${i}: ${t.name} -->`);
    lines.push(`    <style name="ThemePreset.${slug(t.name)}" parent="">`);
    // Peran inti
    lines.push(`        <item name="themeBackground">${hex(t.background)}</item>`);
    lines.push(`        <item name="themeSurface">${hex(t.surface)}</item>`);
    lines.push(`        <item name="themeSurfaceVariant">${hex(t.surfaceVariant)}</item>`);
    lines.push(`        <item name="themeAccent">${hex(t.primary)}</item>`);
    // Warna "on*" (teks/ikon di atas warna di atasnya)
    lines.push(`        <item name="themeOnBackground">${hex(t.onBackground)}</item>`);
    lines.push(`        <item name="themeOnSurface">${hex(t.onSurface)}</item>`);
    lines.push(`        <item name="themeOnSurfaceVariant">${hex(t.onSurfaceVariant)}</item>`);
    lines.push(`        <item name="themeOnAccent">${hex(t.onPrimary)}</item>`);
    // Turunan
    lines.push(`        <item name="themeAccentDim">${hex(t.accentDim)}</item>`);
    lines.push(`        <item name="themeAccentOnSurface">${hex(t.accentOnSurface)}</item>`);
    lines.push(`        <item name="themeDivider">${hex(t.divider)}</item>`);
    lines.push(`        <item name="themeSurfaceContainer">${hex(t.surfaceContainer)}</item>`);
    lines.push(`        <item name="themeSurfaceContainerHigh">${hex(t.surfaceContainerHigh)}</item>`);
    lines.push(`        <item name="themeSurfaceContainerHighest">${hex(t.surfaceContainerHighest)}</item>`);
    // Attr lama dari Fase 1: dipetakan ke peran FOREGROUND, karena 47 dari 51
    // pemakaiannya adalah textColor/tint/stroke. 4 pemakaian sebagai background
    // dipindah ke themeSurface/themeSurfaceVariant/themeBackground di layout-nya.
    lines.push(`        <item name="themeColorAccent">${hex(t.accentOnSurface)}</item>`);
    lines.push(`        <item name="themeColorPrimary">${hex(t.onSurface)}</item>`);
    lines.push(`        <item name="themeColorPrimaryDark">${hex(t.onSurfaceVariant)}</item>`);
    // AppCompat: bikin widget bawaan (checkbox, ripple, seekbar) ikut accent preset.
    lines.push(`        <item name="colorAccent">${hex(t.accentOnSurface)}</item>`);
    // `colorAccent` sendiri CUMA nyentuh widget AppCompat. Switch/CheckBox yg ke-inflate
    // sebagai widget FRAMEWORK (mis. di dalam ContentDialog, yg parent-nya
    // @android:style/Theme.Dialog — bukan tema app) ambil tint dari attr `android:`,
    // yang ga ikut ke-override sama colorAccent. Tanpa 3 baris di bawah, toggle di dialog
    // nempel di warna accent bawaan tema platform, ga ikut preset.
    lines.push(`        <item name="android:colorAccent">${hex(t.accentOnSurface)}</item>`);
    lines.push(`        <item name="colorControlActivated">${hex(t.accentOnSurface)}</item>`);
    lines.push(`        <item name="android:colorControlActivated">${hex(t.accentOnSurface)}</item>`);
    // State OFF/normal (track switch mati, stroke checkbox kosong, tint ikon).
    lines.push(`        <item name="colorControlNormal">${hex(t.onSurfaceVariant)}</item>`);
    lines.push(`        <item name="android:colorControlNormal">${hex(t.onSurfaceVariant)}</item>`);
    lines.push(`        <item name="android:windowBackground">${hex(t.background)}</item>`);
    lines.push(`        <item name="android:colorBackground">${hex(t.background)}</item>`);
    lines.push('    </style>');
});

lines.push('</resources>');

const out = path.join(__dirname, '..', 'app', 'src', 'main', 'res', 'values', 'theme_overlays.xml');
fs.writeFileSync(out, lines.join('\n') + '\n');
console.log(`wrote ${out}`);
console.log(`${presets.length} overlay styles, ${lines.length} lines`);

// Emit juga daftar style buat dicocokin ThemeManager.PRESET_STYLES (biar ga salah urut).
console.log('\n// paste-check: urutan harus sama di ThemeManager.PRESET_STYLES');
presets.forEach((t, i) => console.log(`//   ${i}: R.style.ThemePreset_${slug(t.name)}  (${t.name})`));
