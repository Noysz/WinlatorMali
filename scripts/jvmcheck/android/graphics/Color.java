package android.graphics;

/**
 * Stub android.graphics.Color — cukup buat ngejalanin ThemePreset.java ASLI di JVM biasa.
 * Semantik disamain sama dokumentasi Android: ARGB dikemas di int, alpha di bit 24-31.
 * argb() men-mask tiap komponen ke 8 bit, sama kaya implementasi asli.
 */
public class Color {
    public static int alpha(int color) { return color >>> 24; }
    public static int red(int color)   { return (color >> 16) & 0xFF; }
    public static int green(int color) { return (color >> 8) & 0xFF; }
    public static int blue(int color)  { return color & 0xFF; }
    public static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
