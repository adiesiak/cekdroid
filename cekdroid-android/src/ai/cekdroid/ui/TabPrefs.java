package ai.cekdroid.ui;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which tabs the user chose to see.
 *
 * DevInfo ships a "Tab Manager" for exactly this reason: fifteen tabs is a wall,
 * but somebody who only cares about battery and storage should not have to
 * scroll past camera and codec every time. Selection is stored in
 * SharedPreferences and always falls back to a sensible default set.
 */
public final class TabPrefs {

    private static final String PREFS = "cekdroid_tabs";
    private static final String KEY = "visible";
    private static final String KEY_VERSION = "default_version";

    /**
     * Bumped whenever {@link #DEFAULT} gains an entry.
     *
     * A saved selection wins over the defaults, which is correct -- but it also
     * means a new tab never reaches anyone who has already opened the app. On
     * upgrade the missing defaults are appended once, leaving the user's own
     * choices in place; a tab they deliberately hid stays hidden because it is
     * already in their saved set.
     */
    private static final int DEFAULT_VERSION = 3;

    /**
     * Shown on first run.
     *
     * Mirrors DevInfo's default bar: the dashboard, the hardware sections people
     * open first, then the sections that are screens of their own -- location,
     * the test hub, and the tools hub. The ids are tab ids from {@link Tabs},
     * not collector ids, because one tab can hold several collectors.
     */
    private static final String[] DEFAULT = {
            "device", "system", "cpu", "location", "network", "storage",
            "battery", "display", "camera", "sensor", "apps", "tests", "tools",
    };

    private TabPrefs() {
    }

    public static Set<String> visible(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY, null);
        if (raw == null || raw.isEmpty()) {
            LinkedHashSet<String> d = new LinkedHashSet<>();
            java.util.Collections.addAll(d, DEFAULT);
            p.edit().putInt(KEY_VERSION, DEFAULT_VERSION).apply();
            return d;
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        if (out.isEmpty()) {
            java.util.Collections.addAll(out, DEFAULT);
            p.edit().putInt(KEY_VERSION, DEFAULT_VERSION).apply();
            return out;
        }
        if (p.getInt(KEY_VERSION, 1) < DEFAULT_VERSION) {
            // One-time upgrade: adopt any default the user has never seen.
            boolean added = false;
            for (String id : DEFAULT) {
                if (out.add(id)) {
                    added = true;
                }
            }
            p.edit().putInt(KEY_VERSION, DEFAULT_VERSION).apply();
            if (added) {
                save(ctx, out);
            }
        }
        return out;
    }

    public static void save(Context ctx, Set<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, sb.toString()).apply();
    }

    /** The tabs to show, always in strip order. */
    public static List<Tabs.Tab> activeSections(Context ctx) {
        Set<String> visible = visible(ctx);
        List<Tabs.Tab> out = new ArrayList<>();
        for (Tabs.Tab tab : Tabs.all()) {
            if (visible.contains(tab.id)) {
                out.add(tab);
            }
        }
        return out;
    }

    public static void restoreDefaults(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply();
    }
}
