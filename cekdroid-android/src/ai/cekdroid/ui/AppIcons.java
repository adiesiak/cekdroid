package ai.cekdroid.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * App icons by package name, cached per process.
 *
 * The app list is rendered from the cached JSON, which carries only the package
 * name, so every row has to ask PackageManager for its icon. Re-resolving that
 * on each rebuild of a long list was the expensive part; the icons themselves
 * are small, so they are kept for the life of the process.
 */
public final class AppIcons {

    private static final Map<String, Drawable> CACHE = new HashMap<>();

    private AppIcons() {
    }

    /** The app's icon, or null when the package is gone or unreadable. */
    public static Drawable get(Context ctx, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        if (CACHE.containsKey(packageName)) {
            return CACHE.get(packageName);
        }
        Drawable icon = null;
        try {
            icon = ctx.getPackageManager().getApplicationIcon(packageName);
        } catch (Throwable ignored) {
            // Uninstalled between the report and this screen, or a row whose
            // name field is not a package: no icon is the honest answer.
        }
        CACHE.put(packageName, icon);
        return icon;
    }
}
