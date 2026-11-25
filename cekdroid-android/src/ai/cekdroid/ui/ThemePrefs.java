package ai.cekdroid.ui;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Theme choice: light/dark mode and the accent hue.
 *
 * Stored as plain values and pushed into {@link Palette} once per Activity, so
 * every existing Palette call site keeps working without threading a Context
 * through the whole UI layer.
 */
public final class ThemePrefs {

    private static final String PREFS = "cekdroid_theme";
    private static final String KEY_MODE = "mode";
    private static final String KEY_ACCENT = "accent";

    /** Follow the system, or force one. */
    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    /** Accent choices, named so the settings screen can list them. */
    public static final String[] ACCENT_NAMES = {
            "Teal (bawaan)", "Hijau", "Biru", "Ungu", "Oranye", "Merah muda",
    };
    public static final int[] ACCENT_COLORS = {
            0xFF29A7A8, 0xFF22A06B, 0xFF2F6FED, 0xFF8B5CF6, 0xFFEE8A24, 0xFFE85D75,
    };

    private ThemePrefs() {
    }

    public static int mode(Context ctx) {
        return prefs(ctx).getInt(KEY_MODE, MODE_SYSTEM);
    }

    public static void setMode(Context ctx, int mode) {
        prefs(ctx).edit().putInt(KEY_MODE, mode).apply();
        Palette.configure(ctx);
    }

    /** Index into {@link #ACCENT_COLORS}, not the colour itself. */
    public static int accentIndex(Context ctx) {
        int saved = prefs(ctx).getInt(KEY_ACCENT, 0);
        return (saved >= 0 && saved < ACCENT_COLORS.length) ? saved : 0;
    }

    public static void setAccentIndex(Context ctx, int index) {
        prefs(ctx).edit().putInt(KEY_ACCENT, index).apply();
        Palette.configure(ctx);
    }

    public static int accentColor(Context ctx) {
        return ACCENT_COLORS[accentIndex(ctx)];
    }

    /** True when the effective appearance is dark, honouring the override. */
    public static boolean effectiveDark(Context ctx) {
        int mode = mode(ctx);
        if (mode == MODE_DARK) {
            return true;
        }
        if (mode == MODE_LIGHT) {
            return false;
        }
        int systemMode = ctx.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return systemMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
