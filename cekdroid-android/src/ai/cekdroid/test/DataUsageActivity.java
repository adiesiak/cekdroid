package ai.cekdroid.test;

import android.app.Activity;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Per-app data usage, from NetworkStatsManager.
 *
 * The bucket query needs PACKAGE_USAGE_STATS, which is granted from Settings,
 * not a runtime dialog. Without it the query returns empty and the screen says
 * so with a button to the right settings page — the same treatment the app uses
 * for its storage split.
 *
 * Usage is reported as the sum of cellular and Wi-Fi buckets for the current
 * cycle, which is what NetworkStatsManager exposes without a subscription id.
 */
public class DataUsageActivity extends Activity {

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

        Chrome.addBar(this, root, "Penggunaan data");
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
        status.setText("Butuh akses penggunaan untuk membaca statistik jaringan per app.");
        list.addView(Cards.body(this,
                "Android menyimpan statistik jaringan per app di balik izin akses "
                        + "penggunaan, yang hanya bisa diberikan dari Pengaturan.",
                Palette.dim(this)));
        TextView open = Cards.actionButton(this, "Buka pengaturan akses penggunaan");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Cards.dp(this, 12);
        open.setLayoutParams(lp);
        open.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
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
        NetworkStatsManager manager = (NetworkStatsManager)
                getSystemService(Context.NETWORK_STATS_SERVICE);
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            status.setText("Perangkat tidak menyediakan NetworkStatsManager.");
            return;
        }

        long now = System.currentTimeMillis();
        // One cycle is roughly a month; asking for the last 30 days keeps the
        // numbers comparable with what Settings shows.
        long start = now - 30L * 24 * 60 * 60 * 1000;

        List<Row> rows = new ArrayList<>();
        for (String type : new String[]{"mobile", "wifi"}) {
            int transport = "mobile".equals(type)
                    ? NetworkCapabilities.TRANSPORT_CELLULAR
                    : NetworkCapabilities.TRANSPORT_WIFI;
            try {
                NetworkStats stats = manager.querySummary(transport, null, start, now);
                NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket);
                    long bytes = bucket.getRxBytes() + bucket.getTxBytes();
                    if (bytes <= 0) {
                        continue;
                    }
                    rows.add(new Row(bucket.getUid(), type, bytes));
                }
                stats.close();
            } catch (Throwable t) {
                status.setText("Statistik " + type + " gagal: " + t.getMessage());
            }
        }

        if (rows.isEmpty()) {
            status.setText("Belum ada data tercatat dalam 30 hari terakhir.");
            list.addView(Cards.body(this,
                    "Statistik jaringan kosong. Ini bisa berarti akses penggunaan "
                            + "ditolak, atau Android belum mengumpulkan data sejak "
                            + "perangkat dinyalakan.",
                    Palette.dim(this)));
            return;
        }

        PackageManager pm = getPackageManager();
        List<Row> merged = new ArrayList<>();
        for (Row row : rows) {
            Row existing = null;
            for (Row candidate : merged) {
                if (candidate.uid == row.uid) {
                    existing = candidate;
                    break;
                }
            }
            if (existing == null) {
                merged.add(new Row(row.uid, row.kind, row.bytes));
            } else {
                existing.bytes += row.bytes;
            }
        }
        merged.sort(Comparator.comparingLong((Row r) -> r.bytes).reversed());

        status.setText(merged.size() + " app memakai data dalam 30 hari terakhir.");
        int shown = 0;
        for (Row row : merged) {
            String label;
            try {
                label = pm.getNameForUid(row.uid);
            } catch (Throwable t) {
                label = null;
            }
            if (label == null) {
                label = "uid " + row.uid;
            }
            list.addView(Cards.row(this, label, human(row.bytes)));
            if (++shown >= 30) {
                list.addView(Cards.hint(this, "+" + (merged.size() - shown)
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

    /** One uid's total, merged across transports. */
    private static final class Row {
        final int uid;
        final String kind;
        long bytes;

        Row(int uid, String kind, long bytes) {
            this.uid = uid;
            this.kind = kind;
            this.bytes = bytes;
        }
    }
}
