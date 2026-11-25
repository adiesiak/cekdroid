package ai.cekdroid.test;

import android.app.Activity;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.UserHandle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Cache cleanup: what can be removed, and what this app may remove.
 *
 * An unprivileged app can only delete its own cache. `StorageStats` reports
 * every app's cache size once usage-access is granted, so the screen shows the
 * full picture but only offers deletion for this app's own cache — claiming
 * otherwise would be a button that always fails.
 */
public class StorageCleanActivity extends Activity {

    private LinearLayout list;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Palette.configure(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.bg(this));
        int pad = Cards.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        status.setTextSize(12f);
        status.setTextColor(Palette.dim(this));
        status.setPadding(0, Cards.dp(this, 6), 0, Cards.dp(this, 12));
        root.addView(status);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Chrome.addBar(this, root, "Pembersihan penyimpanan");
        setContentView(root);

        render();
    }

    private void render() {
        list.removeAllViews();

        long ownCache = directorySize(getCacheDir());
        status.setText("Cache app ini: " + human(ownCache));
        list.addView(Cards.row(this, "Cache CekDroid", human(ownCache)));

        TextView clean = Cards.actionButton(this, "Bersihkan cache CekDroid");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Cards.dp(this, 12);
        clean.setLayoutParams(lp);
        clean.setOnClickListener(v -> {
            int removed = deleteTree(getCacheDir());
            status.setText(removed + " item cache dihapus. "
                    + "Cache app ini sekarang " + human(directorySize(getCacheDir())) + ".");
            render();
        });
        list.addView(clean);

        list.addView(Cards.hint(this,
                "App biasa hanya boleh menghapus cache miliknya sendiri. Cache app "
                        + "lain bisa dihapus lewat Pengaturan > Penyimpanan."));

        Long externalCache = externalCacheSize();
        if (externalCache != null) {
            list.addView(Cards.row(this, "Cache eksternal", human(externalCache)));
        }

        list.addView(Cards.heading(this, "Cache app lain (baca saja)"));
        list.addView(Cards.hint(this,
                "Ukuran hanya muncul bila akses penggunaan sudah diberikan."));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            list.addView(Cards.body(this, "Butuh Android 8+ untuk membaca cache app lain.",
                    Palette.faint(this)));
            return;
        }
        StorageStatsManager ssm = (StorageStatsManager)
                getSystemService(Context.STORAGE_STATS_SERVICE);
        android.os.storage.StorageManager sm = (android.os.storage.StorageManager)
                getSystemService(Context.STORAGE_SERVICE);
        if (ssm == null || sm == null) {
            return;
        }

        UUID uuid;
        try {
            uuid = sm.getUuidForPath(Environment.getDataDirectory());
        } catch (Throwable t) {
            return;
        }

        List<CacheRow> rows = new ArrayList<>();
        try {
            for (ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
                try {
                    StorageStats stats = ssm.queryStatsForPackage(uuid, ai.packageName,
                            UserHandle.getUserHandleForUid(ai.uid));
                    long cache = stats.getCacheBytes();
                    if (cache <= 0) {
                        continue;
                    }
                    String label;
                    try {
                        label = String.valueOf(getPackageManager()
                                .getApplicationLabel(ai));
                    } catch (Throwable t) {
                        label = ai.packageName;
                    }
                    rows.add(new CacheRow(label, cache));
                } catch (Throwable ignored) {
                    // a package can vanish between listing and querying
                }
            }
        } catch (Throwable t) {
            return;
        }

        if (rows.isEmpty()) {
            list.addView(Cards.body(this,
                    "Tidak ada cache app lain yang terbaca. Berikan akses penggunaan "
                            + "untuk melihat ukurannya.",
                    Palette.faint(this)));
            return;
        }
        rows.sort(Comparator.comparingLong((CacheRow r) -> r.bytes).reversed());
        for (CacheRow row : rows) {
            list.addView(Cards.row(this, row.label, human(row.bytes)));
        }
    }

    private Long externalCacheSize() {
        File external = getExternalCacheDir();
        return external == null ? null : directorySize(external);
    }

    private static long directorySize(File dir) {
        if (dir == null) {
            return 0;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return dir.length();
        }
        long total = 0;
        for (File f : files) {
            total += f.isDirectory() ? directorySize(f) : f.length();
        }
        return total;
    }

    /** Delete everything under {@code dir}, returning how many entries went. */
    private static int deleteTree(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (File f : files) {
            if (f.isDirectory()) {
                count += deleteTree(f);
            }
            if (f.delete()) {
                count++;
            }
        }
        return count;
    }

    private static String human(long bytes) {
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(java.util.Locale.US, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", mb);
        }
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
    }

    private static final class CacheRow {
        final String label;
        final long bytes;

        CacheRow(String label, long bytes) {
            this.label = label;
            this.bytes = bytes;
        }
    }
}
