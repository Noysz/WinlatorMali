package com.winlator.cmod;

import android.graphics.Color;

/**
 * A single theme preset. Only 4 "core" colors are hand-picked per preset — background, surface,
 * surfaceVariant, primary (= accent) — everything else below is DERIVED automatically, so new
 * presets stay visually consistent without hand-tuning 10+ hex values each time. This mirrors the
 * pattern used by Bannerlator's ThemePreset.kt (see github.com/The412Banner/Bannerlator, GPL-3.0),
 * adapted from Compose/Kotlin to plain Java + int ARGB colors for this codebase's View-based UI.
 *
 * <p>CONTRAST IS ENFORCED, NOT ASSUMED. Every "on*" color and the divider is checked against the
 * surface it will be drawn on using the WCAG 2.x contrast ratio, and nudged until it is readable.
 * A hand-picked value that already passes is kept untouched; only failing ones are corrected. This
 * exists because the previous revision hardcoded five colors as single constants shared by all
 * presets, which measured 48 WCAG failures across 14 presets — worst cases were a divider at 1.01
 * (invisible) and secondary text at 1.34. A fixed gray cannot work on surfaces ranging from
 * #0B2B26 to #851636, so these are now relative to each palette instead of absolute.
 */
public final class ThemePreset {
    public final String name;

    // --- Core colors (hand-picked per preset) ---
    public final int background;
    public final int surface;
    public final int surfaceVariant;
    public final int primary;

    // --- "On" colors: what to draw ON TOP of the color above it (text/icons) ---
    public final int onBackground;
    public final int onSurface;
    public final int onSurfaceVariant;
    public final int onPrimary;

    public final int divider;

    // --- Derived roles (computed automatically, never hand-picked) ---

    /** Darkened primary — for low-emphasis fills/borders/tracks (unselected chip outline, switch-on track). */
    public final int accentDim;

    /**
     * Primary, adjusted only if needed so an accent-filled component stays distinguishable from
     * the card it sits on (WCAG 1.4.11 non-text contrast, 3:1). 6 of the 14 presets fail this with
     * their raw primary — including the default "Crimson Dusk" at 1.99, where an accent switch on a
     * card is nearly invisible. Use this for accent fills ON `surface`; use `primary` for accent on
     * `background` or for text/icon tinting.
     */
    public final int accentOnSurface;

    /** Elevated "container" ramp for raised cards/dialogs, each a bit lighter than `surface`. */
    public final int surfaceContainer;
    public final int surfaceContainerHigh;
    public final int surfaceContainerHighest;

    // Starting points for the simple constructor. These are HINTS, not guarantees: each is kept
    // only if it already meets its contrast target on this preset's palette, otherwise it is
    // derived. Do not treat them as the final values.
    private static final int DEFAULT_ON_SURFACE = 0xFFE0E0E0;
    private static final int DEFAULT_ON_SURFACE_VARIANT = 0xFFAAAAAA;
    private static final int DEFAULT_ON_BACKGROUND = 0xFFFFFFFF;
    private static final int DEFAULT_ON_PRIMARY = 0xFFFFFFFF;
    private static final int DEFAULT_DIVIDER = 0xFF404040;

    /** WCAG AA, normal-size text. Applied to every text/icon "on*" color. */
    private static final double TARGET_TEXT = 4.5d;
    /** WCAG AA, non-text UI component boundaries (1.4.11). Applied to accentOnSurface. */
    private static final double TARGET_COMPONENT = 3.0d;
    /**
     * Dividers are decoration, not content — WCAG sets no ratio for them. 1.6 is an internal floor
     * chosen so the line is actually perceptible without reading as text; it is not a standard.
     */
    private static final double TARGET_DIVIDER = 1.6d;

    /** Simple constructor — just the 4 core colors. Everything else is derived. */
    public ThemePreset(String name, int background, int surface, int surfaceVariant, int primary) {
        this(name, background, surface, surfaceVariant, primary,
                DEFAULT_ON_BACKGROUND, DEFAULT_ON_SURFACE, DEFAULT_ON_SURFACE_VARIANT,
                DEFAULT_ON_PRIMARY, DEFAULT_DIVIDER);
    }

    /**
     * Full constructor — lets a preset propose any "on*" color. A proposed value is honored as-is
     * when it already meets its contrast target, and corrected toward readability when it does not,
     * so passing a value can improve the result but cannot make it unreadable.
     */
    public ThemePreset(String name, int background, int surface, int surfaceVariant, int primary,
                        int onBackground, int onSurface, int onSurfaceVariant, int onPrimary, int divider) {
        this.name = name;
        this.background = background;
        this.surface = surface;
        this.surfaceVariant = surfaceVariant;
        this.primary = primary;

        this.onBackground = ensureReadable(onBackground, background, TARGET_TEXT);
        this.onSurface = ensureReadable(onSurface, surface, TARGET_TEXT);
        this.onSurfaceVariant = ensureReadable(onSurfaceVariant, surfaceVariant, TARGET_TEXT);
        this.onPrimary = ensureReadable(onPrimary, primary, TARGET_TEXT);
        this.divider = ensureReadable(divider, surface, TARGET_DIVIDER);

        this.accentDim = lerp(primary, 0xFF000000, 0.55f);
        // Nudged toward the surface's own best-contrast pole, so a too-dark accent brightens and a
        // too-light one darkens, rather than always going one direction.
        this.accentOnSurface = ensureReadable(primary, surface, TARGET_COMPONENT);
        this.surfaceContainer = lerp(surface, this.onSurface, 0.05f);
        this.surfaceContainerHigh = lerp(surface, this.onSurface, 0.09f);
        this.surfaceContainerHighest = lerp(surface, this.onSurface, 0.14f);
    }

    /**
     * Linear-interpolate two ARGB colors channel-by-channel. t=0 -> colorA, t=1 -> colorB.
     * Also handy for building a `surfaceVariant` on palettes that don't naturally have a 3rd
     * dark stop: e.g. lerp(surface, primary, 0.30f) — see "Twilight Rose" / "Berry Pink" in
     * ThemeManager.PRESETS, which use this instead of a hand-picked hex.
     *
     * <p>Note: this interpolates in non-linear sRGB, so a midpoint reads slightly duller than a
     * perceptually-uniform blend (OKLab/HCT) would. Kept for zero dependencies; the derived roles
     * here are subtle enough that it is not worth a color-science library.
     */
    public static int lerp(int colorA, int colorB, float t) {
        int a = Math.round(Color.alpha(colorA) + (Color.alpha(colorB) - Color.alpha(colorA)) * t);
        int r = Math.round(Color.red(colorA)   + (Color.red(colorB)   - Color.red(colorA))   * t);
        int g = Math.round(Color.green(colorA) + (Color.green(colorB) - Color.green(colorA)) * t);
        int b = Math.round(Color.blue(colorA)  + (Color.blue(colorB)  - Color.blue(colorA))  * t);
        return Color.argb(a, r, g, b);
    }

    /**
     * WCAG 2.x relative luminance: sRGB channels linearized, then weighted. This is NOT the same as
     * a simple brightness average — a mid blue and a mid yellow of equal "brightness" differ a lot
     * here, which is exactly why the naive check this replaced picked white on #5483B3 (ratio 3.98,
     * below AA) while the correct answer was black (5.28).
     */
    private static double relativeLuminance(int color) {
        return 0.2126d * linearize(Color.red(color))
             + 0.7152d * linearize(Color.green(color))
             + 0.0722d * linearize(Color.blue(color));
    }

    private static double linearize(int channel8bit) {
        double c = channel8bit / 255.0d;
        return c <= 0.03928d ? c / 12.92d : Math.pow((c + 0.055d) / 1.055d, 2.4d);
    }

    /** WCAG contrast ratio between two colors: 1.0 (identical) .. 21.0 (black on white). */
    public static double contrastRatio(int colorA, int colorB) {
        double lumA = relativeLuminance(colorA);
        double lumB = relativeLuminance(colorB);
        double lighter = Math.max(lumA, lumB);
        double darker = Math.min(lumA, lumB);
        return (lighter + 0.05d) / (darker + 0.05d);
    }

    /**
     * Black or white — whichever gives the HIGHER WCAG contrast on {@code backgroundColor}. Used as
     * the direction to push a failing color toward, and as the fallback "on" color.
     */
    public static int bestOnColor(int backgroundColor) {
        return contrastRatio(0xFF000000, backgroundColor) >= contrastRatio(0xFFFFFFFF, backgroundColor)
                ? 0xFF000000
                : 0xFFFFFFFF;
    }

    /**
     * Keep {@code proposed} if it already reaches {@code target} contrast on {@code onTopOf};
     * otherwise blend it toward that background's best-contrast pole in fixed steps and return the
     * first blend that reaches the target. If even the pole itself cannot reach it (possible for the
     * 3:1 component target on a mid-tone surface), the pole is returned as the closest achievable —
     * a bounded loop, never an infinite search.
     */
    private static int ensureReadable(int proposed, int onTopOf, double target) {
        if (contrastRatio(proposed, onTopOf) >= target) return proposed;

        int pole = bestOnColor(onTopOf);
        for (int step = 1; step <= 50; step++) {
            int candidate = lerp(proposed, pole, step / 50.0f);
            if (contrastRatio(candidate, onTopOf) >= target) return candidate;
        }
        return pole;
    }

    /**
     * Contrast-safe "on-accent" color for a user-picked CUSTOM accent (not a baked-in preset).
     * Light accent -> black text/icon, dark accent -> white — so a near-white or near-black custom
     * pick never leaves icons/text invisible on top of it. Mirrors Bannerlator's luminance guard
     * (their issue #46).
     *
     * <p>CHANGED: this used to threshold ITU-R BT.601 brightness at 0.5. It now maximizes the WCAG
     * ratio instead, because the two disagree on mid-tone accents and BT.601 loses — on #5483B3 it
     * chose white at 3.98 (fails AA) where black scores 5.28. Same signature, same contract
     * ("readable color to draw on this accent"), better answer.
     */
    public static int onAccentFor(int accentColor) {
        return bestOnColor(accentColor);
    }
}
