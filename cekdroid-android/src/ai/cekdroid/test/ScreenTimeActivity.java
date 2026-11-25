package ai.cekdroid.test;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Screen-time history, from UsageStatsManager.
 *
 * Rather than a per-app foreground total (which needs the usage-events stream
 * and a per-app aggregation pass), this replays the last seven days of events
 * and counts activations — screen unlocks and app launches — which is the
 * "pola penggunaan" half of what DevInfo shows and needs the same grant.
 *
 * The result is deliberately aggregate: the top apps by launch count, plus the
 * unlock count. A full timeline would need a chart surface the app does not
 * have, and the counts answer the same question.
 */
public class ScreenTimeActivity extends Activity {

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

        Chrome.addBar(this, root, "Waktu penggunaan layar");
        setContentView(root);

        if (!usageAccessGranted()) {
            showPermissionCard();
            return;
        }
        load();
    }

    private boolean usageAccessGranted() {
        try {
            AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) {
                return false;
            }
            int mode = ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    private void showPermissionCard() {
        status.setText("Butuh akses penggunaan untuk membaca riwayat pemakaian.");
        list.addView(Cards.body(this,
                "Android memerlukan izin terpisah di setelan sistem sebelum app "
                        + "bisa membaca histori pemakaian.",
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
        UsageStatsManager manager = (UsageStatsManager)
                getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            status.setText("Perangkat tidak menyediakan UsageStatsManager.");
            return;
        }

        long now = System.currentTimeMillis();
        long start = now - 7L * 24 * 60 * 60 * 1000;

        Map<String, Integer> launches = new LinkedHashMap<>();
        int unlocks = 0;
        try {
            UsageEvents events = manager.queryEvents(start, now);
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    String pkg = event.getPackageName();
                    if (pkg != null) {
                        launches.merge(pkg, 1, Integer::sum);
                    }
                } else if (event.getEventType() == UsageEvents.Event.KEYGUARD_HIDDEN) {
                    unlocks++;
                }
            }
        } catch (Throwable t) {
            status.setText("Riwayat pemakaian gagal dibaca: " + t.getMessage());
            return;
        }

        if (launches.isEmpty() && unlocks == 0) {
            status.setText("Belum ada riwayat dalam 7 hari terakhir.");
            list.addView(Cards.body(this,
                    "Android hanya menyimpan riwayat sejak perangkat dinyalakan, "
                            + "dan hanya bila akses penggunaan sudah aktif.",
                    Palette.dim(this)));
            return;
        }

        status.setText("7 hari terakhir · " + unlocks + " kali membuka kunci layar.");
        list.addView(Cards.row(this, "Pembukaan kunci layar", String.valueOf(unlocks)));

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(launches.entrySet());
        ranked.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue())
                .reversed());

        list.addView(Cards.heading(this, "App paling sering dibuka"));
        int shown = 0;
        for (Map.Entry<String, Integer> entry : ranked) {
            list.addView(Cards.row(this, entry.getKey(),
                    entry.getValue() + " kali"));
            if (++shown >= 20) {
                list.addView(Cards.hint(this, "+" + (ranked.size() - shown)
                        + " app lain tidak ditampilkan"));
                break;
            }
        }
    }
}
