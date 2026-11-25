package ai.cekdroid.ui;

import android.content.Context;
import android.graphics.Color;

/**
 * Colour system.
 *
 * The default palette is taken from DevInfo's own assets so the two apps read
 * the same: the launcher icon is #29A7A8 and its section glyphs use sage greens
 * (#B0C7BB, #C4DBCF). Every section also gets one accent, because a single
 * accent for fifteen different screens is what made the old UI hard to scan.
 *
 * Light/dark and the accent hue are user-selectable. Rather than threading a
 * Context through every call site, each Activity calls {@link #configure} once
 * and the accessors below read that state. Values are safe before configure():
 * the defaults are the dark teal theme.
 */
public final class Palette {

    // Default brand values, still used for the launcher icon and as the base
    // that other accents are derived from.
    public static final int TEAL = 0xFF29A7A8;
    public static final int TEAL_DEEP = 0xFF1E7C7D;
    public static final int TEAL_BRIGHT = 0xFF4FC7C8;
    public static final int SAGE = 0xFFB0C7BB;
    public static final int SAGE_LIGHT = 0xFFC4DBCF;

    // Dark surfaces
    public static final int BG = 0xFF0E1416;
    public static final int SURFACE = 0xFF161F22;
    public static final int SURFACE_HIGH = 0xFF1D282B;
    public static final int LINE = 0xFF243034;

    // Text
    public static final int TEXT = 0xFFE8F0EE;
    public static final int TEXT_DIM = 0xFF8FA3A0;
    public static final int TEXT_FAINT = 0xFF5E6E6C;

    // Light surfaces
    public static final int BG_LIGHT = 0xFFF4F7F6;
    public static final int SURFACE_LIGHT = 0xFFFFFFFF;
    public static final int LINE_LIGHT = 0xFFDCE5E2;
    public static final int TEXT_LIGHT = 0xFF11201E;
    public static final int TEXT_DIM_LIGHT = 0xFF5C6F6C;
    public static final int TEXT_FAINT_LIGHT = 0xFF93A5A2;

    // Semantic
    public static final int OK = 0xFF3DDC84;
    public static final int WARN = 0xFFF5A524;
    public static final int CRITICAL = 0xFFF05C5C;
    public static final int INFO = 0xFF4CC2FF;

    // Configured state
    private static boolean isDark = true;
    private static int accentBase = TEAL;
    private static boolean configured;

    private Palette() {
    }

    /** Apply the saved theme. Call from onCreate of every Activity. */
    public static void configure(Context ctx) {
        isDark = ThemePrefs.effectiveDark(ctx);
        accentBase = ThemePrefs.accentColor(ctx);
        configured = true;
    }

    public static boolean isConfigured() {
        return configured;
    }
    /** The user-selected accent, used for headers and the tab indicator. */
    public static int brand() {
        return accentBase;
    }

    /** A brighter variant for use on a dark background. */
    public static int brandBright() {
        return isDark ? blend(accentBase, 0xFFFFFFFF, 0.32f)
                : blend(accentBase, 0xFF000000, 0.12f);
    }

    /**
     * One accent per section.
     *
     * Sections keep a hue of their own — a camera card should not be the same
     * colour as a storage card — but each is rotated toward the chosen brand
     * hue so the whole app still reads as one palette.
     */
    public static int accent(String sectionId) {
        int own = sectionHue(sectionId);
        if (accentBase == TEAL) {
            return own;
        }
        // Shift the section hue by the difference between the chosen accent and
        // the default teal, keeping the section's own identity.
        return rotateHue(own, hueOf(accentBase) - hueOf(TEAL));
    }

    private static int sectionHue(String sectionId) {
        switch (sectionId) {
            case "device": return TEAL;
            case "system": return 0xFF7DD3C0;
            case "cpu": return 0xFFF5A524;
            case "gpu": return 0xFFA78BFA;
            case "memory": return SAGE;
            case "storage": return SAGE_LIGHT;
            case "battery": return OK;
            case "display": return 0xFF60A5FA;
            case "camera": return 0xFFF472B6;
            case "sensor": return 0xFF34D399;
            case "network": return INFO;
            case "location": return 0xFF60A5FA;
            case "sim": return 0xFF818CF8;
            case "apps": return 0xFFFBBF24;
            case "codec": return 0xFF2DD4BF;
            case "id": return 0xFF94A3B8;
            case "tests": return 0xFFF472B6;
            case "tools": return 0xFFA3E635;
            default: return accentBase;
        }
    }

    /** 12% alpha of an accent, for pill and icon backgrounds. */
    public static int tint(int color) {
        return (color & 0x00FFFFFF) | 0x1F000000;
    }

    public static int tintStrong(int color) {
        return (color & 0x00FFFFFF) | 0x33000000;
    }

    /**
     * An accent pushed toward the page background, for the "free space" slice
     * of a bar.
     *
     * The old value was a 20%-alpha accent, which is indistinguishable from the
     * card behind it on the dark theme: the free slice of the storage bar
     * looked empty. This keeps the accent's hue but mixes in enough of the
     * surface that the segment stays visible in both themes while still reading
     * as the quiet end of the bar.
     */
    public static int faded(Context ctx, int color) {
        int base = dark(ctx) ? SURFACE : SURFACE_LIGHT;
        return blend(color, base, dark(ctx) ? 0.62f : 0.55f);
    }

    /** Text/icon colour that is readable on top of {@link #faded}. */
    public static int onFaded(Context ctx) {
        return dark(ctx) ? TEXT_DIM : TEXT_DIM_LIGHT;
    }

    public static int severity(String severity) {
        switch (severity) {
            case "critical": return CRITICAL;
            case "warn": return WARN;
            case "ok": return OK;
            default: return INFO;
        }
    }

    // ------------------------------------------------- theme-aware accessors

    public static boolean dark(Context ctx) {
        return configured ? isDark : systemDark(ctx);
    }

    public static boolean dark() {
        return isDark;
    }

    public static int bg(Context ctx) {
        return dark(ctx) ? BG : BG_LIGHT;
    }

    public static int surface(Context ctx) {
        return dark(ctx) ? SURFACE : SURFACE_LIGHT;
    }

    public static int line(Context ctx) {
        return dark(ctx) ? LINE : LINE_LIGHT;
    }

    public static int text(Context ctx) {
        return dark(ctx) ? TEXT : TEXT_LIGHT;
    }

    public static int dim(Context ctx) {
        return dark(ctx) ? TEXT_DIM : TEXT_DIM_LIGHT;
    }

    public static int faint(Context ctx) {
        return dark(ctx) ? TEXT_FAINT : TEXT_FAINT_LIGHT;
    }

    private static boolean systemDark(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** Blend two colours, used for the segmented memory bar. */
    public static int blend(int from, int to, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * clamped);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * clamped);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped);
        return Color.rgb(r, g, b);
    }

    /** HSL hue in degrees. */
    private static float hueOf(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return hsv[0];
    }

    /** Rotate a colour's hue while keeping its saturation and value. */
    private static int rotateHue(int color, float degrees) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + degrees + 360f) % 360f;
        return Color.HSVToColor(Color.alpha(color), hsv);
    }
}
