package ai.cekdroid.test;

import android.app.Activity;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
 * Per-app storage analysis, sorted by size.
 *
 * StorageStatsManager reports real APK and data sizes for every package only
 * when usage-access is granted; without it, every package but this one reports
 * zero. That is detected rather than printed: a list of apps at "0 B" reads as
 * a broken screen, so the grant is required up front and its absence is
 * explained with a button to the right settings page.
 */
public class AppAnalysisActivity extends Activity {

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

        Chrome.addBar(this, root, "Analisis aplikasi");
        setContentView(root);

        if (!usageAccessGranted()) {
            showPermissionCard();
            return;
        }
        load();
    }

    private boolean usageAccessGranted() {
        try {
            android.app.AppOpsManager ops = (android.app.AppOpsManager)
                    getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) {
                return false;
            }
            int mode = ops.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    private void showPermissionCard() {
        status.setText("Butuh akses penggunaan untuk mengukur ukuran tiap app.");
        list.addView(Cards.body(this,
                "StorageStats hanya mengembalikan angka nyata untuk semua app "
                        + "setelah akses penggunaan diberikan; tanpa itu setiap app "
                        + "melaporkan nol.",
                Palette.dim(this)));
        TextView open = Cards.actionButton(this, "Buka pengaturan akses penggunaan");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Cards.dp(this, 12);
        open.setLayoutParams(lp);
        open.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(
                    android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Throwable ignored) {
                // a ROM without that settings activity: nothing to open
            }
        });
        list.addView(open);
    }

    private void load() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            status.setText("Analisis ukuran app butuh Android 8 atau lebih baru.");
            return;
        }
        StorageStatsManager ssm = (StorageStatsManager)
                getSystemService(Context.STORAGE_STATS_SERVICE);
        android.os.storage.StorageManager sm = (android.os.storage.StorageManager)
                getSystemService(Context.STORAGE_SERVICE);
        if (ssm == null || sm == null) {
            status.setText("StorageStatsManager tidak tersedia.");
            return;
        }

        UUID uuid;
        try {
            uuid = sm.getUuidForPath(Environment.getDataDirectory());
        } catch (Throwable t) {
            status.setText("UUID penyimpanan tidak terbaca: " + t.getMessage());
            return;
        }

        PackageManager pm = getPackageManager();
        List<Entry> entries = new ArrayList<>();
        long total = 0;
        try {
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                try {
                    StorageStats stats = ssm.queryStatsForPackage(uuid, ai.packageName,
                            UserHandle.getUserHandleForUid(ai.uid));
                    long bytes = stats.getAppBytes() + stats.getDataBytes()
                            + stats.getCacheBytes();
                    if (bytes <= 0) {
                        continue;
                    }
                    String label;
                    try {
                        label = String.valueOf(pm.getApplicationLabel(ai));
                    } catch (Throwable t) {
                        label = ai.packageName;
                    }
                    entries.add(new Entry(label, ai.packageName, bytes,
                            stats.getDataBytes(), stats.getCacheBytes()));
                    total += bytes;
                } catch (Throwable ignored) {
                    // a package can vanish between listing and querying
                }
            }
        } catch (Throwable t) {
            status.setText("Daftar app gagal: " + t.getMessage());
            return;
        }

        if (entries.isEmpty()) {
            status.setText("Tidak ada ukuran app yang terbaca.");
            return;
        }
        entries.sort(Comparator.comparingLong((Entry e) -> e.bytes).reversed());

        status.setText(entries.size() + " app · total " + human(total));
        int shown = 0;
        for (Entry entry : entries) {
            LinearLayout row = Cards.row(this, entry.label, human(entry.bytes),
                    entry.pkg + "  ·  data " + human(entry.dataBytes)
                            + "  ·  cache " + human(entry.cacheBytes));
            list.addView(row);
            if (++shown >= 60) {
                list.addView(Cards.hint(this, "+" + (entries.size() - shown)
                        + " app lain tidak ditampilkan"));
                break;
            }
        }
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

    private static final class Entry {
        final String label;
        final String pkg;
        final long bytes;
        final long dataBytes;
        final long cacheBytes;

        Entry(String label, String pkg, long bytes, long dataBytes, long cacheBytes) {
            this.label = label;
            this.pkg = pkg;
            this.bytes = bytes;
            this.dataBytes = dataBytes;
            this.cacheBytes = cacheBytes;
        }
    }
}
