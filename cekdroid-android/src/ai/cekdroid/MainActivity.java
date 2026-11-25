package ai.cekdroid;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ai.cekdroid.collect.Collector;
import ai.cekdroid.collect.Json;
import ai.cekdroid.collect.Snapshot;
import ai.cekdroid.overlay.FloatingMonitorService;
import ai.cekdroid.settings.TabManagerActivity;
import ai.cekdroid.settings.ThemeSettingsActivity;
import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Dashboard;
import ai.cekdroid.ui.HubPage;
import ai.cekdroid.ui.Hubs;
import ai.cekdroid.ui.Motion;
import ai.cekdroid.ui.Palette;
import ai.cekdroid.ui.Perms;
import ai.cekdroid.ui.ReportCard;
import ai.cekdroid.ui.SectionPage;
import ai.cekdroid.ui.ShareCard;
import ai.cekdroid.ui.Splash;
import ai.cekdroid.ui.SwipePager;
import ai.cekdroid.ui.TabPage;
import ai.cekdroid.ui.TabPrefs;
import ai.cekdroid.ui.TabStrip;
import ai.cekdroid.ui.Tabs;
import ai.cekdroid.ui.TestLinks;

/**
 * Dashboard-first shell.
 *
 * Index 0 is always the dashboard, so there is one obvious answer to "how is my
 * phone" before any list appears. The remaining tabs come from TabPrefs and can
 * be trimmed in the tab manager, which is how DevInfo keeps 15 screens from
 * feeling like a wall.
 *
 * The header is Chrome.brandBar: brand mark, "CekDroid", one small status line,
 * and the actions as icon buttons on that same row. An earlier build stacked a
 * status row, a row of four word-chips, and a full-width "Perbarui" button above
 * the tabs, which spent about 150dp of a 640dp screen before any content.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "cekdroid";
    private static final String KEY_JSON = "last_report";
    private static final String KEY_TS = "last_ts";

    private TabStrip tabStrip;
    private SwipePager content;
    private Chrome.Bar bar;
    private Chrome.Banner banner;

    private List<Tabs.Tab> sections = new ArrayList<>();
    private Snapshot snapshot;
    private final List<android.view.View> pages = new ArrayList<>();
    private boolean floatingRunning;
    /** True only while the floating overlay (not just the notification) is on. */
    private boolean floatingOverlay;

    /**
     * Theme this Activity last rendered with.
     *
     * Tracked on the instance because Palette's state is static and shared: by
     * the time onResume runs, the theme screen has already written the new
     * values into it, so comparing against Palette would always show no change
     * and the rebuild would never happen.
     */
    private boolean appliedDark;
    private int appliedAccent;
    private String appliedPerms;
    private boolean splashShown;
    private android.view.View splashView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Palette.configure(this);
        Chrome.paintStatusBar(this);
        appliedDark = Palette.dark(this);
        appliedAccent = Palette.brand();
        appliedPerms = Perms.stateKey(this);
        rebuildSections();

        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(Palette.bg(this));
        shell.addView(buildLayout(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The splash sits on top of an already-built dashboard, so the reveal
        // shows settled content instead of a tree still assembling itself.
        android.view.View splash = Splash.build(this);
        shell.addView(splash, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splashShown = true;

        setContentView(shell);

        String cached = prefs().getString(KEY_JSON, null);
        setStatus(cached == null
                ? "Belum ada laporan \u00b7 ketuk perbarui"
                : "Laporan tersimpan " + lastSeen());
        collectAsync(true);

        // Hold the splash until the first read lands, but never longer than
        // this: a device that is slow to answer should still reach a usable
        // screen rather than sitting behind the logo.
        splashView = splash;
        splash.postDelayed(this::hideSplash, 2400);
    }

    /** Remove the splash overlay exactly once. */
    private void hideSplash() {
        if (!splashShown) {
            return;
        }
        splashShown = false;
        Splash.dismiss(splashView, null);
        splashView = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // The theme screen writes its choice and finishes, so returning here is
        // the only moment the change can be picked up.
        Palette.configure(this);
        if (Palette.dark(this) != appliedDark || Palette.brand() != appliedAccent) {
            appliedDark = Palette.dark(this);
            appliedAccent = Palette.brand();
            rebuildAfterThemeChange();
            return;
        }

        // Picks up changes made in the tab manager without a relaunch.
        List<String> current = new ArrayList<>();
        for (Tabs.Tab s : sections) {
            current.add(s.title);
        }
        List<Tabs.Tab> fresh = TabPrefs.activeSections(this);
        if (!titles(fresh).equals(current)) {
            rebuildSections();
            applyTabs();
        }

        // A grant made in the permission dialog or in Settings leaves the
        // visible rows stale until the next read. Re-collecting is the only
        // honest fix: the snapshot was built when the answer was still "no".
        String perms = Perms.stateKey(this);
        if (!perms.equals(appliedPerms)) {
            appliedPerms = perms;
            if (snapshot != null) {
                collectAsync(false);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == Perms.REQUEST_RUNTIME && snapshot != null) {
            appliedPerms = Perms.stateKey(this);
            collectAsync(false);
        }
    }

    /**
     * Rebuild the whole view tree against the new palette.
     *
     * Colours are baked into TextViews at construction, so the only reliable
     * way to restyle is to build the screen again. The snapshot is kept, so no
     * device read is repeated.
     */
    private void rebuildAfterThemeChange() {
        rebuildSections();
        Snapshot keep = snapshot;
        boolean keepSplash = splashShown;
        setContentView(buildLayout());
        Chrome.paintStatusBar(this);
        snapshot = keep;
        if (snapshot != null) {
            applyTabs();
        }
        if (keepSplash) {
            // Nothing to restore: the splash was only relevant on cold start.
            splashShown = false;
        }
    }

    private static List<String> titles(List<Tabs.Tab> list) {
        List<String> out = new ArrayList<>();
        for (Tabs.Tab s : list) {
            out.add(s.title);
        }
        return out;
    }

    private void rebuildSections() {
        sections = TabPrefs.activeSections(this);
        pages.clear();
    }

    // ------------------------------------------------------------ layout

    private LinearLayout buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.bg(this));

        bar = Chrome.brandBar(this, null,
                Chrome.iconButton(this, "refresh", Palette.brandBright(),
                        v -> collectAsync(false)),
                Chrome.iconButton(this, "floating", Palette.text(this),
                        v -> toggleFloating()),
                Chrome.iconButton(this, "grid", Palette.text(this),
                        v -> startActivity(new Intent(this, TabManagerActivity.class))),
                Chrome.iconButton(this, "palette", Palette.text(this),
                        v -> startActivity(new Intent(this, ThemeSettingsActivity.class))),
                Chrome.iconButton(this, "share", Palette.text(this),
                        v -> share()));
        root.addView(bar.root);
        root.addView(Chrome.hairline(this));

        tabStrip = new TabStrip(this);
        tabStrip.setOnSelect(this::showTab);
        root.addView(tabStrip.view());
        root.addView(Chrome.hairline(this));

        banner = new Chrome.Banner(this);
        root.addView(banner);

        content = new SwipePager(this);
        content.setOnSwipe(this::stepTab);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Build the tabs now, not on the first successful read.
        //
        // They only need sections, which comes from preferences, so waiting for
        // the collector left the screen as a bare bar for as long as the device
        // took to answer. On this hardware that was long enough to see.
        buildTabs();
        content.addView(loadingState());
        return root;
    }

    private void buildTabs() {
        List<String> titles = new ArrayList<>();
        titles.add("Dasbor");
        for (Tabs.Tab s : sections) {
            titles.add(s.title);
        }
        tabStrip.setTitles(titles);
        pages.clear();
    }

    private void applyTabs() {
        buildTabs();
        if (snapshot != null) {
            showTab(0);
        }
    }

    /**
     * Move one tab in {@code direction} (+1 next, -1 previous).
     *
     * Clamped rather than wrapping: a swipe past the last tab doing nothing is
     * what people expect from a strip that visibly ends, and wrapping from Alat
     * back to Dasbor lost their place. Hidden tabs are not in the strip at all,
     * so the step is over the visible list only.
     */
    private void stepTab(int direction) {
        if (tabStrip == null) {
            return;
        }
        int count = sections.size() + 1;
        int next = tabStrip.selected() + direction;
        if (next < 0 || next >= count) {
            return;
        }
        showTab(next);
    }

    /** Index 0 is the dashboard; index N maps to sections[N - 1]. */
    private void showTab(int index) {
        tabStrip.select(index);
        content.removeAllViews();
        if (snapshot == null) {
            content.addView(loadingState());
            return;
        }
        while (pages.size() <= index) {
            pages.add(null);
        }
        android.view.View page = pages.get(index);
        if (page == null) {
            if (index == 0) {
                page = Dashboard.build(this, snapshot);
            } else {
                page = TabPage.build(this, snapshot, sections.get(index - 1));
            }
            pages.set(index, page);
        }
        content.addView(page);
    }

    /**
     * Skeleton shown while the first report is being read.
     *
     * Three dimmed blocks read as "content is coming"; the old centred
     * "Membaca perangkat..." line looked like an error message.
     */
    private LinearLayout loadingState() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 16);
        column.setPadding(pad, Cards.dp(this, 18), pad, pad);

        for (int i = 0; i < 3; i++) {
            android.view.View block = new android.view.View(this);
            block.setBackground(Cards.rounded(this, Palette.surface(this), 0, 16f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(this, i == 0 ? 150 : 84));
            lp.bottomMargin = Cards.dp(this, 10);
            block.setLayoutParams(lp);
            block.setAlpha(0.55f);
            column.addView(block);
            block.animate().alpha(0.26f).setDuration(700)
                    .setStartDelay(i * 90L).start();
        }
        return column;
    }

    // ------------------------------------------------------------- data

    private void setStatus(String text) {
        if (bar != null) {
            bar.setStatus(text);
        }
    }

    private void collectAsync(final boolean first) {
        if (bar != null) {
            bar.setActionsEnabled(false);
        }
        if (!first) {
            setStatus("Membaca perangkat\u2026");
        }
        new Thread(() -> {
            String error = null;
            Snapshot result = null;
            try {
                result = new Collector(getApplicationContext()).collect(true);
            } catch (Throwable t) {
                error = t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            final Snapshot snap = result;
            final String err = error;
            runOnUiThread(() -> {
                if (bar != null) {
                    bar.setActionsEnabled(true);
                }
                if (snap == null) {
                    setStatus("Gagal membaca");
                    banner.show("Gagal membaca perangkat: " + err, true);
                    return;
                }
                snapshot = snap;
                pages.clear();
                prefs().edit()
                        .putString(KEY_JSON, new Json().snapshot(snap).toString())
                        .putLong(KEY_TS, System.currentTimeMillis())
                        .apply();
                int warnings = snap.warnings().size();
                setStatus("Diperbarui " + timeNow());
                applyTabs();
                hideSplash();
                if (warnings > 0 && !splashShown) {
                    banner.show(warnings + " catatan: sebagian data dibatasi Android", false);
                }
            });
        }).start();
    }

    /**
     * Share: a one-page report by default, the current screen as an option.
     *
     * A transaction needs the report — identity, score, verdict — not a
     * screenshot of whichever tab happened to be open. The screenshot is still
     * there for a technician who wants to show one particular card, but it is
     * the second choice now instead of the only one.
     */
    private void share() {
        if (snapshot == null) {
            banner.show("Belum ada data. Tunggu pembacaan pertama.", true);
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Bagikan")
                .setItems(new String[]{
                        "Laporan 1 halaman (untuk pembeli)",
                        "Tangkapan layar tab ini"
                }, (dialog, which) -> {
                    if (which == 0) {
                        shareReport();
                    } else {
                        shareScreenshot();
                    }
                })
                .show();
    }

    /** The one-page sale report: verdict, breakdown, key facts. */
    private void shareReport() {
        boolean ok = ReportCard.share(this, snapshot);
        if (!ok) {
            banner.show("Gagal membuat laporan.", true);
        }
    }

    /**
     * Share what is on screen as an image.
     *
     * The whole window, not just the page body: the bar and the tab strip are
     * what tell the recipient which screen and which app this is, and a card
     * cropped out of its frame reads like a random screenshot.
     */
    private void shareScreenshot() {
        View target = findViewById(android.R.id.content);
        if (target == null || target.getWidth() <= 0) {
            banner.show("Belum ada tampilan untuk dibagikan.", true);
            return;
        }
        String subtitle = currentTabTitle();
        boolean ok = ShareCard.share(this, target, subtitle);
        if (!ok) {
            banner.show("Gagal membuat gambar untuk dibagikan.", true);
        }
    }

    /** Tab name for the footer, so the shared image says which screen it is. */
    private String currentTabTitle() {
        if (tabStrip == null) {
            return null;
        }
        int index = tabStrip.selected();
        if (index <= 0) {
            return "Dasbor";
        }
        int section = index - 1;
        if (section < 0 || section >= sections.size()) {
            return null;
        }
        return sections.get(section).title;
    }

    /**
     * Cycle the monitor: off -> notification -> notification + overlay -> off.
     *
     * The notification form needs no permission and is what DevInfo uses, so it
     * is the first step. The floating overlay is the second step and asks for
     * the overlay grant at that point, not before, so the common case never
     * sends anyone into Settings. The banner always names the current state and
     * how to stop it, because a foreground notification cannot be swiped away.
     */
    private void toggleFloating() {
        if (!floatingRunning) {
            // Android 13+ suppresses a foreground notification without this
            // grant, which would leave the monitor running invisibly. Ask once,
            // at the moment the user turns it on.
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                String permission = "android.permission.POST_NOTIFICATIONS";
                if (checkSelfPermission(permission)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{permission}, 7401);
                }
            }
            FloatingMonitorService.startNotification(this);
            floatingRunning = true;
            floatingOverlay = false;
            banner.show("Monitor aktif di notifikasi \u00b7 tekan lagi untuk overlay, "
                    + "atau 'Matikan' di notifikasinya.", false);
            return;
        }
        if (!floatingOverlay) {
            if (!FloatingMonitorService.canDrawOverlays(this)) {
                banner.show("Overlay butuh izin 'Tampil di atas app lain'. "
                        + "Monitor tetap jalan di notifikasi.", true);
                try {
                    startActivity(FloatingMonitorService.overlayPermissionIntent(this));
                } catch (Throwable t) {
                    banner.show("Tidak bisa membuka pengaturan izin: " + t.getMessage(), true);
                }
                return;
            }
            FloatingMonitorService.startWithOverlay(this);
            floatingOverlay = true;
            banner.show("Overlay mengambang aktif \u00b7 tekan lagi untuk mematikan.", false);
            return;
        }
        FloatingMonitorService.stop(this);
        floatingRunning = false;
        floatingOverlay = false;
        banner.show("Monitor dimatikan.", false);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private String lastSeen() {
        long ts = prefs().getLong(KEY_TS, 0L);
        if (ts == 0L) {
            return "tanpa waktu";
        }
        return new java.text.SimpleDateFormat("dd MMM HH:mm",
                java.util.Locale.getDefault()).format(new java.util.Date(ts));
    }

    private static String timeNow() {
        return new java.text.SimpleDateFormat("HH:mm",
                java.util.Locale.getDefault()).format(new java.util.Date());
    }
}
