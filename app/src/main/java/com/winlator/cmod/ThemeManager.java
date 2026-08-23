package com.winlator.cmod;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.annotation.StyleRes;
import androidx.preference.PreferenceManager;

import java.util.Arrays;
import java.util.List;

/**
 * Fase 3: replaces the old boolean `dark_mode` preference with an index into a list of full theme
 * presets (background/surface/surfaceVariant/accent). See ThemePreset for how each preset's derived
 * colors are computed and why contrast is enforced rather than assumed.
 *
 * <h3>Two ways colors reach the UI, and why both are needed</h3>
 * <ul>
 *   <li><b>Theme attributes</b> — {@link #applyTheme(Context)} layers a generated overlay style
 *       (res/values/theme_overlays.xml, one per preset) onto the activity's theme, so the 61
 *       {@code ?attr/theme*} references already present in layout/ and drawable/ follow the
 *       selected preset. {@code ?attr/} is resolved from the theme while a view inflates and
 *       cannot be driven from SharedPreferences directly, so an overlay is the only mechanism
 *       available here. Must be applied before {@code setContentView}.</li>
 *   <li><b>Programmatic getters</b> — for code that paints outside of inflation (Canvas work, tint
 *       filters set from Java). These read PRESETS directly, so they also work before any theme
 *       has been applied.</li>
 * </ul>
 * Both sides come from PRESETS: the XML is generated from this same list by
 * scripts/gen-theme-overlays.js, so the two cannot drift apart. Do not hand-edit the generated
 * file — regenerate it.
 *
 * <p>BACKWARD COMPAT: {@link #getAccentColor(Context)} and {@link #getPrimaryColor(Context)} keep
 * their exact signatures from Fase 1/2, so already-migrated call sites
 * (ExternalControllerBindingsActivity, InputControlsFragment, widget/SeekBar) keep working
 * unchanged. {@code isDarkMode(Context)} is gone; it had no callers.
 */
public final class ThemeManager {
    private static final String THEME_INDEX_KEY = "theme_preset_index";
    private static final String CUSTOM_ACCENT_KEY = "custom_accent_color";
    /**
     * Fase 1/2 preference. Nothing writes it anymore; kept so migrateLegacyPreference() can delete
     * a value left behind by an older install. See that method for why leaving it in place breaks
     * the 16 files that still read it.
     */
    private static final String DARK_MODE_KEY = "dark_mode";

    private ThemeManager() {}

    // ------------------------------------------------------------------------------------
    // 14 presets, derived from the moodboard palettes. Each is [background, surface,
    // surfaceVariant, primary]. Where a palette didn't have a natural 3rd dark stop for
    // surfaceVariant, it's derived via ThemePreset.lerp(surface, primary, 0.30f) instead of a
    // hand-picked hex — noted inline below.
    //
    // ORDER IS PERSISTED. getSelectedPresetIndex() stores a position, not a name, so reordering
    // or removing an entry silently moves existing users to a different theme. Append at the end.
    // PRESET_STYLES below must stay in the same order.
    // ------------------------------------------------------------------------------------
    public static final List<ThemePreset> PRESETS = Arrays.asList(
        new ThemePreset("Crimson Dusk",    0xFF181A2F, 0xFF242E49, 0xFF37415C, 0xFFB4182D),
        new ThemePreset("Mauve Ash",       0xFF30333A, 0xFF51403D, 0xFF72676F, 0xFFB79D9B),
        new ThemePreset("Violet Cream",    0xFF190019, 0xFF2B124C, 0xFF522B5B, 0xFF854F6C),
        new ThemePreset("Dusk Coral",      0xFF2E365A, 0xFF3F5B8D, 0xFF6B597F, 0xFFBD6C73),
        new ThemePreset("Navy Blush",      0xFF221820, 0xFF262F45, 0xFF45141B, 0xFFFE8492),
        new ThemePreset("Twilight Rose",   0xFF0E1F2F, 0xFF26425A,
                ThemePreset.lerp(0xFF26425A, 0xFFC38EB4, 0.30f) /* derived: no natural 3rd stop */, 0xFFC38EB4),
        new ThemePreset("Rust Pine",       0xFF12232A, 0xFF2F3A32, 0xFF545748, 0xFFDB9F75),
        new ThemePreset("Grape Sunset",    0xFF1D1A39, 0xFF451952, 0xFF662549, 0xFFF39F5A),
        // Cocoa Cream: light cream primary needs a dark onPrimary override (white would be
        // unreadable on it) — same idea as Bannerlator's Royal Gold/Monochrome.
        new ThemePreset("Cocoa Cream",     0xFF291C0E, 0xFF6E473B, 0xFFA78D78, 0xFFE1D4C2,
                0xFFFFFFFF, 0xFFE0E0E0, 0xFFAAAAAA, /* onPrimary */ 0xFF2A1E12, 0xFF404040),
        new ThemePreset("Orchid Lavender", 0xFF49225B, 0xFF6E3482,
                ThemePreset.lerp(0xFF6E3482, 0xFFA56ABD, 0.30f) /* derived */, 0xFFA56ABD),
        new ThemePreset("Emerald Depth",   0xFF051F20, 0xFF0B2B26, 0xFF163832, 0xFF8EB69B),
        new ThemePreset("Berry Pink",      0xFF450714, 0xFF851636,
                ThemePreset.lerp(0xFF851636, 0xFFCF325F, 0.30f) /* derived */, 0xFFCF325F),
        new ThemePreset("Ocean Ice",       0xFF021024, 0xFF052659,
                ThemePreset.lerp(0xFF052659, 0xFF5483B3, 0.30f) /* derived */, 0xFF5483B3),
        new ThemePreset("Peach Maroon",    0xFF4C1D3D, 0xFF852E4E, 0xFFA33757, 0xFFDC586D)
    );

    /**
     * Overlay style per preset, generated into res/values/theme_overlays.xml. Index N here styles
     * PRESETS.get(N). After touching PRESETS: regenerate the XML with
     * scripts/gen-theme-overlays.js, then update this array to match.
     */
    @StyleRes
    private static final int[] PRESET_STYLES = {
        R.style.ThemePreset_CrimsonDusk,
        R.style.ThemePreset_MauveAsh,
        R.style.ThemePreset_VioletCream,
        R.style.ThemePreset_DuskCoral,
        R.style.ThemePreset_NavyBlush,
        R.style.ThemePreset_TwilightRose,
        R.style.ThemePreset_RustPine,
        R.style.ThemePreset_GrapeSunset,
        R.style.ThemePreset_CocoaCream,
        R.style.ThemePreset_OrchidLavender,
        R.style.ThemePreset_EmeraldDepth,
        R.style.ThemePreset_BerryPink,
        R.style.ThemePreset_OceanIce,
        R.style.ThemePreset_PeachMaroon
    };

    public static final int DEFAULT_PRESET_INDEX = 0; // "Crimson Dusk"

    // --- Preset selection ---

    public static int getSelectedPresetIndex(Context context) {
        return clampIndex(prefs(context).getInt(THEME_INDEX_KEY, DEFAULT_PRESET_INDEX));
    }

    public static void setSelectedPresetIndex(Context context, int index) {
        prefs(context).edit().putInt(THEME_INDEX_KEY, clampIndex(index)).apply();
    }

    public static ThemePreset getCurrentPreset(Context context) {
        return PRESETS.get(getSelectedPresetIndex(context));
    }

    /**
     * Clamped on read as well as on write, so a preference written by a build that shipped more
     * presets cannot blow up with IndexOutOfBounds after a downgrade.
     */
    private static int clampIndex(int index) {
        return Math.max(0, Math.min(index, PRESETS.size() - 1));
    }

    // --- Theme application (must run before setContentView) ---

    /**
     * Layer the selected preset's overlay onto {@code context}'s theme. Call from every Activity's
     * {@code onCreate} after {@code super.onCreate} and BEFORE {@code setContentView}: views read
     * {@code ?attr/} values as they inflate, so an overlay applied later leaves everything already
     * on screen with the old colors.
     *
     * <p>{@code force = true} is required — the base themes in styles.xml already define
     * themeColorPrimary and friends, and without force those existing values win.
     *
     * <p>Switching preset on a live screen needs {@code Activity.recreate()}; re-applying the
     * overlay does not re-inflate views that already exist.
     */
    public static void applyTheme(Context context) {
        migrateLegacyPreference(context);
        Resources.Theme theme = context.getTheme();
        if (theme == null) return;
        theme.applyStyle(PRESET_STYLES[getSelectedPresetIndex(context)], true);
    }

    /**
     * One-time cleanup of the Fase 1/2 {@code dark_mode} boolean, run from {@link
     * #applyTheme(Context)} so it happens before anything reads it.
     *
     * <p>Nothing writes that key anymore, but 16 files still read it with a default of {@code true}
     * to pick between a dark and a light resource. An install where the user had unchecked dark mode
     * still has {@code false} stored, which would leave those 16 files on their light branches while
     * the preset overlay paints everything else dark — a mix that looks like random broken screens
     * rather than a theme. Removing the key makes every one of those reads fall back to {@code true}
     * and agree with the overlay.
     *
     * <p>Idempotent and cheap: after the first call the key is gone, so this is a single
     * {@code contains()} on later launches. It writes only when there is something to remove.
     */
    private static void migrateLegacyPreference(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.contains(DARK_MODE_KEY)) return;
        prefs.edit().remove(DARK_MODE_KEY).apply();
    }

    /** Overlay style id for a preset index — used by the picker preview. */
    @StyleRes
    public static int getPresetStyle(int index) {
        return PRESET_STYLES[clampIndex(index)];
    }

    /**
     * Resolve a color theme attribute (e.g. {@code R.attr.themeSurface}) from {@code context}'s
     * theme, returning {@code fallback} when it is not set. Prefer this over the getters below when
     * the value should follow whatever theme the current view actually has, rather than the
     * globally selected preset — they differ for a view inflated with a themed context.
     */
    @ColorInt
    public static int resolveThemeColor(Context context, int attrResId, @ColorInt int fallback) {
        Resources.Theme theme = context.getTheme();
        if (theme == null) return fallback;
        TypedValue value = new TypedValue();
        if (!theme.resolveAttribute(attrResId, value, true)) return fallback;
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        if (value.resourceId != 0) {
            return context.getResources().getColor(value.resourceId, theme);
        }
        return fallback;
    }

    // --- Custom accent override (HSV picker screen writes here) ---

    public static boolean hasCustomAccent(Context context) {
        return prefs(context).contains(CUSTOM_ACCENT_KEY);
    }

    public static void setCustomAccent(Context context, int color) {
        prefs(context).edit().putInt(CUSTOM_ACCENT_KEY, color).apply();
    }

    public static void clearCustomAccent(Context context) {
        prefs(context).edit().remove(CUSTOM_ACCENT_KEY).apply();
    }

    // ------------------------------------------------------------------------------------
    // Color getters used throughout the app.
    // getAccentColor() / getPrimaryColor() signatures are UNCHANGED from Fase 1/2 on purpose.
    // ------------------------------------------------------------------------------------

    @ColorInt
    public static int getAccentColor(Context context) {
        if (hasCustomAccent(context)) {
            return prefs(context).getInt(CUSTOM_ACCENT_KEY, getCurrentPreset(context).primary);
        }
        return getCurrentPreset(context).primary;
    }

    @ColorInt
    public static int getOnAccentColor(Context context) {
        if (hasCustomAccent(context)) {
            return ThemePreset.onAccentFor(getAccentColor(context));
        }
        return getCurrentPreset(context).onPrimary;
    }

    /**
     * Accent adjusted to keep 3:1 against the preset's surface. Use for accent-colored components
     * drawn on a card; {@link #getAccentColor(Context)} is the raw accent, which in six of the
     * presets sits too close to its own surface to stay distinguishable.
     *
     * <p>A custom accent is returned unchanged — the user picked that color on purpose, and quietly
     * shifting it would be surprising. Warning about a low-contrast pick belongs in the picker.
     */
    @ColorInt
    public static int getAccentOnSurfaceColor(Context context) {
        if (hasCustomAccent(context)) return getAccentColor(context);
        return getCurrentPreset(context).accentOnSurface;
    }

    @ColorInt
    public static int getPrimaryColor(Context context) {
        return getCurrentPreset(context).primary;
    }

    @ColorInt
    public static int getBackgroundColor(Context context) {
        return getCurrentPreset(context).background;
    }

    @ColorInt
    public static int getSurfaceColor(Context context) {
        return getCurrentPreset(context).surface;
    }

    @ColorInt
    public static int getSurfaceVariantColor(Context context) {
        return getCurrentPreset(context).surfaceVariant;
    }

    @ColorInt
    public static int getAccentDimColor(Context context) {
        return getCurrentPreset(context).accentDim;
    }

    @ColorInt
    public static int getSurfaceContainerColor(Context context) {
        return getCurrentPreset(context).surfaceContainer;
    }

    @ColorInt
    public static int getSurfaceContainerHighColor(Context context) {
        return getCurrentPreset(context).surfaceContainerHigh;
    }

    @ColorInt
    public static int getOnBackgroundColor(Context context) {
        return getCurrentPreset(context).onBackground;
    }

    @ColorInt
    public static int getOnSurfaceColor(Context context) {
        return getCurrentPreset(context).onSurface;
    }

    @ColorInt
    public static int getOnSurfaceVariantColor(Context context) {
        return getCurrentPreset(context).onSurfaceVariant;
    }

    @ColorInt
    public static int getDividerColor(Context context) {
        return getCurrentPreset(context).divider;
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
