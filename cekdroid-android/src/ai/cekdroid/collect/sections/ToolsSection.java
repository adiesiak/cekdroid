package ai.cekdroid.collect.sections;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;

/**
 * The "Alat" tab.
 *
 * Utility actions that do not fit a data section: storage analysis, app/icon
 * export, the floating monitor, and sharing the report. DevInfo keeps a Tools
 * tab for the same reason -- an action is not a measurement, and burying it
 * under the section it happens to relate to makes it undiscoverable.
 */
public class ToolsSection implements Section {

    /** How many tool entries the hub links to; kept in sync with Hubs.tools. */
    static final int COUNT = 5;

    @Override
    public String id() {
        return "tools";
    }

    @Override
    public String title() {
        return "Alat";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        out.put("available", COUNT);
        out.put("analyze_storage", "Urutkan app berdasarkan ruang yang dipakai");
        out.put("export", "Simpan ikon dan APK ke folder app");
        out.put("monitor", "Overlay ringkas di atas app lain");
        out.put("share", "Bagikan laporan JSON");
        out.put("overlay_permission", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? "Izin 'Tampil di atas app lain' bisa dibuka dari sini"
                : null);
        out.put("uptime_ms", SystemClock.elapsedRealtime());
    }
}
