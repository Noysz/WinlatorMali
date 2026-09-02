// Cek konsistensi Fase 3 yg TIDAK bisa ditangkep compiler:
//   1. Nilai di theme_overlays.xml (generated) == hasil hitung ThemePreset (algoritma).
//   2. Urutan style di ThemeManager.PRESET_STYLES == urutan <style> di XML == urutan PRESETS.
//   3. Tiap ?attr/theme* yg dipake layout/drawable/menu/values PUNYA <attr> di attrs.xml (typo = crash inflate).
//   4. Tiap attr yg dideklarasi ADA nilainya di SEMUA overlay (attr tanpa nilai = crash resolve).
//   5. Ga ada lagi attr FOREGROUND dipake sebagai background (regresi peran).
//   8. Tiap tag <com.winlator...> di resource XML nunjuk ke kelas yg BENERAN ada (aapt2 ga cek ini).
//
// Kenapa manual: box ga ada Android SDK, jadi aapt2 (yg normalnya nangkep #3) ga bisa jalan.
// Ini bukan pengganti build — cuma nutup kelas error yg paling gampang kejadian.
'use strict';
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execSync } = require('child_process');

// Repo root is derived from this file's location, not hardcoded — CI checks out to
// /home/runner/work/... and a hardcoded path fails there with EACCES on a same-named path
// that happens to exist and belong to another user.
const REPO = path.resolve(__dirname, '..');
const RES = path.join(REPO, 'app/src/main/res');
const JAVA = path.join(REPO, 'app/src/main/java/com/winlator/cmod');

let fail = 0, pass = 0;
const check = (cond, label) => { if (cond) pass++; else { fail++; console.log(`  FAIL ${label}`); } };

// ---------- replika ThemePreset.java (harus sinkron; kalau Java diubah, ubah ini juga) ----------
// PRESISI: `t` di lerp() Java bertipe float (32-bit). Tanpa Math.fround, JS ngitung di double
// dan hasilnya bisa geser 1 per channel — kejadian nyata di accentDim Grape Sunset & Berry Pink.
const f32 = Math.fround;
const rgb = (v) => [(v >> 16) & 255, (v >> 8) & 255, v & 255];
const hex = (v) => '#' + (v >>> 0).toString(16).padStart(8, '0').toUpperCase();
const linearize = (c8) => { const c = c8 / 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); };
const relLum = (c) => { const [r, g, b] = rgb(c); return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b); };
const cr = (a, b) => { const [x, y] = [relLum(a), relLum(b)]; return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
const lerpChan = (a, b, t) => Math.round(f32(f32(a) + f32(f32(f32(b) - f32(a)) * f32(t))));
const lerp = (A, B, t) => {
    const [ar, ag, ab] = rgb(A), [br, bg, bb] = rgb(B);
    const al = lerpChan((A >>> 24) & 255, (B >>> 24) & 255, t);
    return ((al << 24) | (lerpChan(ar, br, t) << 16) | (lerpChan(ag, bg, t) << 8) | lerpChan(ab, bb, t)) >>> 0;
};
const bestOn = (bg) => cr(0xFF000000, bg) >= cr(0xFFFFFFFF, bg) ? 0xFF000000 : 0xFFFFFFFF;
const ensure = (p, on, t) => {
    if (cr(p, on) >= t) return p;
    const pole = bestOn(on);
    for (let s = 1; s <= 50; s++) { const c = lerp(p, pole, s / 50); if (cr(c, on) >= t) return c; }
    return pole;
};
const T_TEXT = 4.5, T_COMP = 3.0, T_DIV = 1.6;
const D = { onSurf: 0xFFE0E0E0, onSV: 0xFFAAAAAA, onBg: 0xFFFFFFFF, onPri: 0xFFFFFFFF, div: 0xFF404040 };

const RAW = [
    ['Crimson Dusk', 0xFF181A2F, 0xFF242E49, 0xFF37415C, 0xFFB4182D],
    ['Mauve Ash', 0xFF30333A, 0xFF51403D, 0xFF72676F, 0xFFB79D9B],
    ['Violet Cream', 0xFF190019, 0xFF2B124C, 0xFF522B5B, 0xFF854F6C],
    ['Dusk Coral', 0xFF2E365A, 0xFF3F5B8D, 0xFF6B597F, 0xFFBD6C73],
    ['Navy Blush', 0xFF221820, 0xFF262F45, 0xFF45141B, 0xFFFE8492],
    ['Twilight Rose', 0xFF0E1F2F, 0xFF26425A, lerp(0xFF26425A, 0xFFC38EB4, 0.30), 0xFFC38EB4],
    ['Rust Pine', 0xFF12232A, 0xFF2F3A32, 0xFF545748, 0xFFDB9F75],
    ['Grape Sunset', 0xFF1D1A39, 0xFF451952, 0xFF662549, 0xFFF39F5A],
    ['Cocoa Cream', 0xFF291C0E, 0xFF6E473B, 0xFFA78D78, 0xFFE1D4C2, D.onBg, D.onSurf, D.onSV, 0xFF2A1E12, D.div],
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
const build = (p) => {
    const [name, bg, su, sv, pri] = p;
    const onSurface = ensure(p[6] ?? D.onSurf, su, T_TEXT);
    return { name,
        themeBackground: bg, themeSurface: su, themeSurfaceVariant: sv, themeAccent: pri,
        themeOnBackground: ensure(p[5] ?? D.onBg, bg, T_TEXT),
        themeOnSurface: onSurface,
        themeOnSurfaceVariant: ensure(p[7] ?? D.onSV, sv, T_TEXT),
        themeOnAccent: ensure(p[8] ?? D.onPri, pri, T_TEXT),
        themeAccentDim: lerp(pri, 0xFF000000, 0.55),
        themeAccentOnSurface: ensure(pri, su, T_COMP),
        themeDivider: ensure(p[9] ?? D.div, su, T_DIV),
        themeSurfaceContainer: lerp(su, onSurface, 0.05),
        themeSurfaceContainerHigh: lerp(su, onSurface, 0.09),
        themeSurfaceContainerHighest: lerp(su, onSurface, 0.14)
    };
};
const slug = (n) => n.replace(/[^A-Za-z0-9]/g, '');
const expected = RAW.map(build);

// ---------- 0. jalanin ThemePreset.java ASLI di JVM, cocokkan dgn XML ----------
// Ini pengecekan terkuat di file ini: yg lain membandingkan XML dengan REPLIKA algoritma di JS,
// blok ini mengeksekusi kode Java yg beneran dikirim ke device. Bedanya pernah nyata — replika JS
// ngitung `t` sebagai double sementara Java pakai float, dan dua nilai accentDim melenceng 1.
// Butuh JDK saja (javac + java), TIDAK butuh Android SDK: android.graphics.Color di-stub.
console.log('=== 0. ThemePreset.java asli dijalanin di JVM vs XML ===');
let javaValues = null;

// Ketersediaan JDK diprobe TERPISAH dari compile+run. Sebelumnya satu try/catch nutup dua-duanya,
// dan waktu path repo salah di CI, javac gagal lalu ditelan jadi "JDK ga ada" — pengecekan
// terkuatnya mati diam-diam sementara workflow tetap lanjut. Kalau javac ADA, gagal compile atau
// gagal run itu FAIL, bukan SKIP.
let hasJdk = false;
try {
    execSync('javac -version', { stdio: 'ignore' });
    execSync('java -version', { stdio: 'ignore' });
    hasJdk = true;
} catch (e) {
    hasJdk = false;
}

if (!hasJdk) {
    console.log('     SKIP — JDK ga ada di PATH (javac/java). Cek di bawah cuma pakai replika JS.');
    console.log('     CI selalu punya JDK, jadi SKIP di CI = workflow-nya salah setup.');
} else {
    const outDir = fs.mkdtempSync(path.join(os.tmpdir(), 'themecheck-'));
    const stub = path.join(REPO, 'scripts/jvmcheck/android/graphics/Color.java');
    const runner = path.join(REPO, 'scripts/jvmcheck/com/winlator/cmod/ThemePresetRunner.java');
    const real = path.join(JAVA, 'ThemePreset.java');
    for (const f of [stub, runner, real]) {
        check(fs.existsSync(f), `file buat cek JVM ga ada: ${f}`);
    }
    let stdout = null;
    try {
        execSync(`javac -nowarn -d "${outDir}" "${stub}" "${real}" "${runner}"`, { stdio: 'pipe' });
    } catch (e) {
        fail++;
        console.log(`  FAIL javac GAGAL compile ThemePreset.java:\n${(e.stderr || e.stdout || e.message).toString().trim()}`);
    }
    if (fs.existsSync(path.join(outDir, 'com/winlator/cmod/ThemePreset.class'))) {
        try {
            stdout = execSync(`java -cp "${outDir}" com.winlator.cmod.ThemePresetRunner`, { encoding: 'utf8' });
        } catch (e) {
            fail++;
            console.log(`  FAIL runner Java exit non-zero:\n${(e.stdout || e.stderr || e.message).toString().trim()}`);
            stdout = (e.stdout || '').toString();
        }
    }
    if (stdout) {
        check(stdout.includes('JAVA_CONTRAST_PASS'), `runner Java lapor gagal kontras:\n${stdout}`);
        javaValues = {};
        for (const line of stdout.split('\n')) {
            if (!line.includes('|')) continue;
            const parts = line.trim().split('|');
            javaValues[parts[0]] = Object.fromEntries(parts.slice(1).map((kv) => kv.split('=')));
        }
        check(Object.keys(javaValues).length === RAW.length,
            `runner Java ngasih ${Object.keys(javaValues).length} preset, harusnya ${RAW.length}`);
        console.log(`     compile + run OK, ${Object.keys(javaValues).length} preset dari kode produksi`);
    }
}

// ---------- parse theme_overlays.xml ----------
const overlayXml = fs.readFileSync(path.join(RES, 'values/theme_overlays.xml'), 'utf8');
const styleBlocks = [...overlayXml.matchAll(/<style\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/style>/g)];

if (javaValues) {
    styleBlocks.forEach((blk, i) => {
        const items = Object.fromEntries([...blk[2].matchAll(/<item\s+name="([^"]+)">([^<]+)<\/item>/g)].map((m) => [m[1], m[2].trim()]));
        const name = expected[i].name;
        const jv = javaValues[name];
        check(!!jv, `preset "${name}" ga ada di output runner Java`);
        if (!jv) return;
        for (const [role, val] of Object.entries(jv)) {
            check(items[role] === val, `[JVM] ${name}.${role}: XML=${items[role]} JAVA=${val}`);
        }
    });
}

console.log(`\n=== 1. XML generated vs algoritma replika JS (${styleBlocks.length} style) ===`);
check(styleBlocks.length === expected.length, `jumlah style ${styleBlocks.length} != preset ${expected.length}`);

styleBlocks.forEach((blk, i) => {
    const [, styleName, body] = blk;
    const exp = expected[i];
    check(styleName === `ThemePreset.${slug(exp.name)}`,
        `style #${i} nama "${styleName}" != "ThemePreset.${slug(exp.name)}" (URUTAN GESER)`);
    const items = Object.fromEntries([...body.matchAll(/<item\s+name="([^"]+)">([^<]+)<\/item>/g)].map((m) => [m[1], m[2].trim()]));
    for (const [role, val] of Object.entries(exp)) {
        if (role === 'name') continue;
        check(items[role] === hex(val), `${exp.name}.${role}: XML=${items[role]} algoritma=${hex(val)}`);
    }
    // attr lama Fase 1 -> peran foreground
    check(items['themeColorAccent'] === hex(exp.themeAccentOnSurface), `${exp.name}.themeColorAccent != accentOnSurface`);
    check(items['themeColorPrimary'] === hex(exp.themeOnSurface), `${exp.name}.themeColorPrimary != onSurface`);
    check(items['themeColorPrimaryDark'] === hex(exp.themeOnSurfaceVariant), `${exp.name}.themeColorPrimaryDark != onSurfaceVariant`);
});

// ---------- 2. urutan PRESET_STYLES di ThemeManager.java ----------
console.log('\n=== 2. urutan ThemeManager.PRESET_STYLES ===');
const tmSrc = fs.readFileSync(path.join(JAVA, 'ThemeManager.java'), 'utf8');
const stylesArr = tmSrc.match(/PRESET_STYLES\s*=\s*\{([\s\S]*?)\};/);
check(!!stylesArr, 'PRESET_STYLES ga ketemu di ThemeManager.java');
if (stylesArr) {
    const ids = [...stylesArr[1].matchAll(/R\.style\.(\w+)/g)].map((m) => m[1]);
    check(ids.length === expected.length, `PRESET_STYLES ${ids.length} entri != ${expected.length} preset`);
    ids.forEach((id, i) => {
        const want = `ThemePreset_${slug(expected[i].name)}`;
        check(id === want, `PRESET_STYLES[${i}] = R.style.${id}, harusnya R.style.${want}`);
    });
}
// nama preset di PRESETS Java harus sama urutannya
const presetNames = [...tmSrc.matchAll(/new ThemePreset\("([^"]+)"/g)].map((m) => m[1]);
check(presetNames.length === expected.length, `PRESETS ${presetNames.length} entri != ${expected.length}`);
presetNames.forEach((n, i) => check(n === expected[i].name, `PRESETS[${i}] = "${n}", harusnya "${expected[i].name}"`));

// ---------- 3. tiap ?attr/theme* yg dipake HARUS dideklarasi ----------
console.log('\n=== 3. ?attr/ dipakai vs dideklarasi ===');
const attrsXml = fs.readFileSync(path.join(RES, 'values/attrs.xml'), 'utf8');
const declared = new Set([...attrsXml.matchAll(/<attr\s+name="([^"]+)"/g)].map((m) => m[1]));
// Komentar XML DIBUANG sebelum dicocokkan. Tanpa ini, teks dokumentasi ikut kebaca: header
// theme_overlays.xml nyebut "?attr/theme*" sebagai penjelasan, dan itu kebaca jadi attr
// bernama `theme` yang ga pernah ada -> 15 FAIL palsu. Komentar bukan kode.
const scanDirs = ['layout', 'drawable', 'menu', 'values', 'values-v27'];
const xmlFiles = [];
const walk = (d) => {
    if (!fs.existsSync(d)) return;
    for (const e of fs.readdirSync(d, { withFileTypes: true })) {
        const p = path.join(d, e.name);
        if (e.isDirectory()) walk(p);
        else if (e.name.endsWith('.xml')) xmlFiles.push(p);
    }
};
for (const d of scanDirs) walk(path.join(RES, d));
const usedRaw = xmlFiles
    .map((f) => fs.readFileSync(f, 'utf8').replace(/<!--[\s\S]*?-->/g, ''))
    .join('\n')
    .match(/\?attr\/[A-Za-z_][A-Za-z0-9_]*/g)
    ?.join('\n') || '';
const used = new Map();
for (const line of usedRaw.split('\n')) {
    const a = line.trim().replace('?attr/', '');
    if (!a) continue;
    used.set(a, (used.get(a) || 0) + 1);
}
// Attr bawaan platform/AppCompat — dideklarasi di luar app, wajar ga ada di attrs.xml.
const ANDROID_BUILTIN = new Set(['actionBarSize', 'selectableItemBackground', 'selectableItemBackgroundBorderless', 'colorAccent', 'colorPrimary', 'colorPrimaryDark', 'colorControlNormal', 'colorControlHighlight', 'textColorPrimary', 'textColorSecondary', 'listPopupWindowStyle', 'popupWindowStyle', 'popupMenuStyle', 'textAppearanceListItem', 'textAppearanceListItemSmall', 'textAppearanceSmall', 'textAppearanceMedium', 'textAppearanceLarge', 'dividerHorizontal', 'dividerVertical', 'homeAsUpIndicator']);
for (const [a, n] of [...used].sort()) {
    if (ANDROID_BUILTIN.has(a)) continue;
    check(declared.has(a), `?attr/${a} dipakai ${n}x tapi TIDAK ada <attr> di attrs.xml -> crash inflate`);
}
console.log(`     ${used.size} attr unik dipakai, ${declared.size} dideklarasi`);

// ---------- 4. tiap attr theme* yg dipake HARUS punya nilai di SEMUA overlay ----------
console.log('\n=== 4. attr punya nilai di semua overlay ===');
const themeAttrsUsed = [...used.keys()].filter((a) => a.startsWith('theme'));
styleBlocks.forEach((blk, i) => {
    const items = new Set([...blk[2].matchAll(/<item\s+name="([^"]+)">/g)].map((m) => m[1]));
    for (const a of themeAttrsUsed) {
        check(items.has(a), `overlay #${i} (${expected[i].name}) ga punya <item name="${a}"> -> resolve gagal`);
    }
});
console.log(`     ${themeAttrsUsed.length} attr theme* dipakai layout: ${themeAttrsUsed.join(', ')}`);

// ---------- 5. attr foreground TIDAK boleh jadi background ----------
console.log('\n=== 5. regresi peran (foreground dipakai sbg background) ===');
const FOREGROUND = ['themeColorPrimary', 'themeColorPrimaryDark', 'themeColorAccent', 'themeOnSurface', 'themeOnSurfaceVariant', 'themeOnBackground', 'themeOnAccent'];
// PENGECUALIAN SEMPIT (file, attr). Peran foreground dipakai sebagai <solid> itu normalnya
// regresi — tapi ada bentuk sah: shape KECIL yg digambar DI ATAS peran background
// pasangannya, mis. garis pemisah atau knob toggle. Di situ onX/X justru pasangan yg
// kontrasnya dijamin ThemePreset.ensureReadable, jadi ngelarangnya malah bikin salah.
// Yang tetep dilarang: <solid> foreground buat area BESAR (panel, dialog, header).
// Daftar ini di-cek anti-basi di bawah: entri yg ga kepake lagi = FAIL, biar ga numpuk
// jadi lubang permanen.
const FOREGROUND_AS_SOLID_OK = {
    // garis pemisah 1.3dp antara area teks dan kotak panah spinner (stroke, bukan isi)
    'combo_box_normal.xml': ['themeOnSurfaceVariant'],
    'combo_box_normal_dark.xml': ['themeOnSurfaceVariant'],
    'combo_box_pressed.xml': ['themeOnSurface'],
    'combo_box_pressed_dark.xml': ['themeOnSurface'],
    // knob 24dp toggle, duduk di atas track yg warnanya peran background pasangannya
    'toggle_button_off.xml': ['themeOnSurfaceVariant'], // track = themeSurfaceVariant
    'toggle_button_on.xml': ['themeOnAccent']           // track = themeAccent
};
const allowHit = new Set();
// Lima bentuk sintaks di grep bawah, karena attr yg sama dipake di tempat beda dgn format beda:
//   layout/drawable -> android:background="?attr/X"   (atribut XML)
//   drawable shape  -> <solid android:color="?attr/X" />
//   drawable custom -> app:cutFillColor="?attr/X"     (CutCornerDrawable, isi shape chamfer)
//   values/styles   -> <item name="android:background">?attr/X</item>  (isi elemen)
// Kalau cuma pattern pertama yg dicek, style di values/ lolos tanpa diperiksa.
// cutStrokeColor SENGAJA ga ikut di-grep, sejalan dgn <stroke> yg juga ga: outline itu
// justru tempat yg BENER buat peran foreground. Yg diawasi cuma isi/fill.
for (const a of FOREGROUND) {
    const raw = execSync(`grep -rn 'android:background="?attr/${a}"\\|<solid android:color="?attr/${a}"\\|app:cutFillColor="?attr/${a}"\\|name="android:background">?attr/${a}<\\|name="background">?attr/${a}<' ${RES}/layout ${RES}/drawable ${RES}/values ${RES}/values-v27 2>/dev/null || true`, { encoding: 'utf8' }).trim();
    const bad = [];
    for (const line of raw ? raw.split('\n') : []) {
        const file = path.basename(line.split(':')[0]);
        if ((FOREGROUND_AS_SOLID_OK[file] || []).includes(a)) { allowHit.add(`${file}|${a}`); continue; }
        bad.push(line);
    }
    check(bad.length === 0, `${a} dipakai sbg background:\n      ${bad.join('\n      ')}`);
}
for (const [file, attrs] of Object.entries(FOREGROUND_AS_SOLID_OK)) {
    for (const a of attrs) {
        check(allowHit.has(`${file}|${a}`),
            `allowlist assert-5 BASI: ${file} ga pakai ?attr/${a} sbg isi shape lagi — hapus entrinya`);
    }
}

// ---------- 6. applyTheme dipanggil di semua Activity ----------
console.log('\n=== 6. ThemeManager.applyTheme() di tiap Activity ===');
const activities = execSync(`grep -rl 'extends AppCompatActivity' ${JAVA} 2>/dev/null || true`, { encoding: 'utf8' }).trim().split('\n').filter(Boolean);
for (const f of activities) {
    const src = fs.readFileSync(f, 'utf8');
    check(src.includes('ThemeManager.applyTheme(this)'), `${path.basename(f)} ga manggil ThemeManager.applyTheme(this)`);
}
console.log(`     ${activities.length} Activity: ${activities.map((f) => path.basename(f, '.java')).join(', ')}`);

// ---------- 7. sisa jalur theme lama ----------
console.log('\n=== 7. sisa jalur theme lama ===');
const oldRefs = execSync(`grep -rn 'ThemeManager.isDarkMode\\|setTheme(R.style.AppTheme' ${JAVA} 2>/dev/null || true`, { encoding: 'utf8' }).trim();
check(oldRefs === '', `masih ada jalur theme lama:\n      ${oldRefs.replace(/\n/g, '\n      ')}`);

// Nothing may write dark_mode anymore — a write would resurrect the split-brain state that
// migrateLegacyPreference() exists to clear.
const darkWrites = execSync(`grep -rn 'putBoolean("dark_mode"' ${JAVA} 2>/dev/null || true`, { encoding: 'utf8' }).trim();
check(darkWrites === '', `dark_mode masih DITULIS (harus read-only sampai dihapus migrasi):\n      ${darkWrites.replace(/\n/g, '\n      ')}`);

// The 16 remaining readers all default to true; the migration is what makes that default correct.
// If it disappears, an old install that stored false silently splits: those files go light while
// the overlay paints dark.
const readers = execSync(`grep -rl 'getBoolean("dark_mode"' ${JAVA} 2>/dev/null || true`, { encoding: 'utf8' }).trim().split('\n').filter(Boolean);
check(tmSrc.includes('migrateLegacyPreference'), 'ThemeManager ga punya migrateLegacyPreference() — dark_mode lama ga dibersihin');
check(/applyTheme\(Context context\)\s*\{\s*migrateLegacyPreference\(context\);/.test(tmSrc),
    'migrateLegacyPreference() ga dipanggil PALING AWAL di applyTheme()');
const badDefaults = execSync(`grep -rn 'getBoolean("dark_mode", *false)' ${JAVA} 2>/dev/null || true`, { encoding: 'utf8' }).trim();
check(badDefaults === '', `ada pembacaan dark_mode dgn default false (harus true):\n      ${badDefaults.replace(/\n/g, '\n      ')}`);
console.log(`     ${readers.length} file masih BACA dark_mode (default true) — dibereskan migrasi, bukan diedit`);

// ---------- 8. tag kelas custom di resource XML ----------
console.log('\n=== 8. tag kelas custom di resource XML ===');
// aapt2 GA validasi nama tag drawable. `<com.winlator.cmod.widget.CutCornrDrawable>` lolos
// compile dan baru throw ClassNotFoundException waktu inflate — di SEMUA layar yg makai
// drawable itu sekaligus. Build hijau ga membuktikan apa-apa soal ini, jadi dicek di sini:
// satu-satunya cara nangkep typo/rename tanpa device.
const customTags = new Map(); // FQN -> Set(nama file)
const themedTags = new Set(); // FQN yg dikasih `?attr/` lewat attr app:-nya sendiri
for (const f of xmlFiles) {
    const src = fs.readFileSync(f, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
    // Namespace app: di file drawable cuma dipakai oleh tag custom-nya, jadi cek per-file cukup.
    const themedHere = /app:[A-Za-z0-9_]+="\?attr\//.test(src);
    const rel = path.relative(RES, f);
    for (const m of src.matchAll(/<(com\.winlator\.[A-Za-z0-9_.]+)[\s/>]/g)) {
        if (!customTags.has(m[1])) customTags.set(m[1], new Set());
        customTags.get(m[1]).add(rel);
        if (themedHere && rel.startsWith('drawable')) themedTags.add(m[1]);
    }
}
const pgSrc = fs.readFileSync(path.join(REPO, 'app/proguard-rules.pro'), 'utf8');
for (const [fqn, users] of [...customTags].sort()) {
    const rel = fqn.replace(/^com\.winlator\.cmod\./, '').replace(/\./g, '/') + '.java';
    const srcPath = path.join(JAVA, rel);
    const exists = fqn.startsWith('com.winlator.cmod.') && fs.existsSync(srcPath);
    check(exists, `tag <${fqn}> dipakai di ${[...users].join(', ')} tapi ${rel} ga ada -> ClassNotFoundException waktu inflate`);
    if (!exists) continue;

    // Cek tambahan HANYA buat kelas yg dipake sebagai drawable: jalur inflate-nya beda dari
    // View (DrawableInflater.inflateFromClass butuh constructor kosong, bukan (Context, AttributeSet)).
    const asDrawable = [...users].some((u) => u.startsWith('drawable'));
    if (!asDrawable) continue;
    const cls = fqn.split('.').pop();
    const src = fs.readFileSync(srcPath, 'utf8');
    check(new RegExp(`public\\s+${cls}\\s*\\(\\s*\\)`).test(src),
        `${cls} dipakai sbg tag drawable tapi ga punya constructor public tanpa argumen -> inflateFromClass gagal`);
    check(/@Override\s+public void inflate\(/.test(src),
        `${cls} dipakai sbg tag drawable tapi ga override inflate() -> attribut app: di XML diabaikan diam-diam`);
    check(pgSrc.includes(fqn),
        `${cls} cuma direferensi dari XML (refleksi) tapi ga ada -keep buat ${fqn} di proguard-rules.pro`);

    // Kontrak 2-fase theme. ResourcesImpl.loadXmlDrawable inflate drawable dgn theme=null, jadi
    // `?attr/` nyampe ke inflate() sbg TYPE_ATTRIBUTE mentah dan getColor() THROW di situ. Nilai
    // baru bisa dibaca di applyTheme(), yg cuma dipanggil kalau canApplyTheme() true — dan
    // DrawableWrapperState.canApplyTheme() nanyanya ke ConstantState anak, bukan ke drawable-nya,
    // jadi getConstantState() null = <inset> bilang "ga bisa di-theme" dan applyTheme dilewatin
    // diam-diam (isi jadi transparan, tanpa error). Ini akar crash 2026-09-02.
    if (themedTags.has(fqn)) {
        check(/@Override\s+public void applyTheme\(/.test(src),
            `${cls} dikasih ?attr/ di drawable XML tapi ga override applyTheme() -> UnsupportedOperationException waktu inflate`);
        check(/@Override\s+public boolean canApplyTheme\(/.test(src),
            `${cls} override applyTheme() tapi ga canApplyTheme() -> framework ga pernah manggil applyTheme()`);
        check(/@Override\s+public ConstantState getConstantState\(/.test(src),
            `${cls} pakai ?attr/ tapi getConstantState() ga di-override -> <inset> di atasnya lapor canApplyTheme=false, warna ga pernah ke-resolve`);
    }
}
console.log(`     ${customTags.size} tag kelas custom: ${[...customTags.keys()].map((f) => f.split('.').pop()).join(', ')}`);

console.log(`\n${fail === 0 ? 'PASS' : 'FAIL'} — ${pass} assert lolos, ${fail} gagal`);
process.exit(fail === 0 ? 0 : 1);
