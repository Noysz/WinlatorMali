package com.winlator.cmod;

import android.content.Context;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

public final class ThemeManager {
    private static final String DARK_MODE_KEY = "dark_mode";

    private ThemeManager() {}

    public static boolean isDarkMode(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DARK_MODE_KEY, true);
    }

    public static int getAccentColor(Context context) {
        int colorResource = isDarkMode(context) ? R.color.colorAccentDark : R.color.colorAccent;
        return ContextCompat.getColor(context, colorResource);
    }

    public static int getPrimaryColor(Context context) {
        return ContextCompat.getColor(context, R.color.colorPrimary);
    }
}
