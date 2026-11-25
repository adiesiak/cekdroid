package ai.cekdroid.test;

import android.app.Activity;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Motion;
import ai.cekdroid.ui.Palette;

/**
 * Storage analysis: which apps actually occupy the space.
 *
 * Read-only. "Bersihkan" here means clearing this app's own cache, because
 * deleting another app's data requires a system permission no third-party app
 * can hold; the screen says that instead of offering a button that would fail.
 */
public class StorageAnalyzeActivity extends Activity {

    private LinearLayout list;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Palette.configure(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.bg(this));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 16);
        header.setPadding(pad, pad, pad, Cards.dp(this, 8));

        status = new TextView(this);
        status.setTextSize(12f);
        status.setTextColor(Palette.dim(this));
        status.setPadding(0, Cards.dp(this, 4), 0, Cards.dp(this, 10));
        header.addView(status);
        root.addView(header);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad, 0, pad, pad);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

                Chrome.addBar(this, root, "Analisis penyimpanan");
        setContentView(root);

        clearOwnCacheCard();
        requestMediaPermissionIfNeeded();
        categoryCard();
        loadAppSizes();
        largeFilesCard();
        duplicateCard();
    }

    /**
     * Ask for media read access the first time this screen opens.
     *
     * Without it MediaStore returns an empty cursor, which reads as "you have no
     * photos" rather than "you have not granted this". The prompt is raised here
     * rather than at app start because this is the only screen that needs it.
     */
    private void requestMediaPermissionIfNeeded() {
        String permission = mediaPermission();
        if (permission == null) {
            return;
        }
        if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, 7201);
        }
    }

    /** The media-read permission that applies to this Android version. */
    private static String mediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return android.Manifest.permission.READ_MEDIA_IMAGES;
        }
        return android.Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != 7201) {
            return;
        }
        boolean granted = results.length > 0
                && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        // Rebuild so the cards reflect what is now readable: the first pass ran
        // before the answer arrived.
        finish();
        startActivity(getIntent());
        if (!granted) {
            android.widget.Toast.makeText(this,
                    "Tanpa izin media, kategori dan berkas terbesar tidak terbaca.",
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /**
     * MediaStore categories: photos, video, audio, and downloads.
     *
     * These are readable without a grant because MediaStore is the one storage
     * API scoped storage leaves open to third-party apps. Anything outside these
     * collections is not reachable, so the card states its own coverage instead
     * of implying it counted the whole disk.
     */
    private void categoryCard() {
        LinearLayout card = Cards.card(this, Palette.accent("storage"));
        card.addView(Cards.header(this, "storage", "Kategori media", null));

        String[][] collections = {
                {"Foto", android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()},
                {"Video", android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString()},
                {"Audio", android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString()},
                {"Unduhan", android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI.toString()},
        };

        long total = 0;
        for (String[] entry : collections) {
            long[] stat = collectionSize(entry[1]);
            if (stat == null) {
                card.addView(Cards.row(this, entry[0], "tidak terbaca", null));
                continue;
            }
            total += stat[0];
            card.addView(Cards.row(this, entry[0], formatSize(stat[0]),
                    stat[1] + " berkas"));
        }
        card.addView(Cards.hint(this, "Total kategori: " + formatSize(total)
                + ". Hanya koleksi MediaStore yang dihitung; berkas di luar itu "
                + "tidak bisa dibaca app biasa (scoped storage)."));
        list.addView(card);
        Motion.enter(card, 1);
    }

    /** {bytes, count} for one MediaStore collection, or null when blocked. */
    private long[] collectionSize(String uriText) {
        try {
            android.net.Uri uri = android.net.Uri.parse(uriText);
            String[] projection = {android.provider.MediaStore.MediaColumns.SIZE};
            long bytes = 0;
            long count = 0;
            try (android.database.Cursor c = getContentResolver()
                    .query(uri, projection, null, null, null)) {
                if (c == null) {
                    return null;
                }
                int idx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.SIZE);
                if (idx < 0) {
                    return null;
                }
                while (c.moveToNext()) {
                    bytes += c.getLong(idx);
                    count++;
                }
            }
            return new long[]{bytes, count};
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The biggest files this app can actually see.
     *
     * Scoped storage leaves the app its own external directory and MediaStore's
     * collections; everything else on shared storage is invisible. The card is
     * therefore built from MediaStore rows sorted by size, and says so.
     */
    private void largeFilesCard() {
        LinearLayout card = Cards.card(this, Palette.accent("storage"));
        card.addView(Cards.header(this, "storage", "Berkas terbesar", null));

        List<FileRow> rows = new ArrayList<>();
        collectLarge(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rows);
        collectLarge(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rows);
        collectLarge(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, rows);
        collectLarge(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, rows);

        if (rows.isEmpty()) {
            card.addView(Cards.body(this, "Tidak ada berkas media yang terbaca.",
                    Palette.faint(this)));
            list.addView(card);
            Motion.enter(card, 2);
            return;
        }
        rows.sort((a, b) -> Long.compare(b.bytes, a.bytes));

        int shown = Math.min(rows.size(), 15);
        for (int i = 0; i < shown; i++) {
            FileRow row = rows.get(i);
            card.addView(Cards.row(this, row.name, formatSize(row.bytes),
                    row.path));
        }
        card.addView(Cards.hint(this,
                "Diurutkan dari MediaStore; berkas di luar koleksi media tidak "
                        + "terlihat oleh app biasa."));
        list.addView(card);
        Motion.enter(card, 2);
    }

    private void collectLarge(android.net.Uri uri, List<FileRow> out) {
        try {
            String[] projection = {
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    android.provider.MediaStore.MediaColumns.SIZE,
                    android.provider.MediaStore.MediaColumns.DATA,
            };
            try (android.database.Cursor c = getContentResolver().query(
                    uri, projection, null, null,
                    android.provider.MediaStore.MediaColumns.SIZE + " DESC")) {
                if (c == null) {
                    return;
                }
                int nameIdx = c.getColumnIndex(
                        android.provider.MediaStore.MediaColumns.DISPLAY_NAME);
                int sizeIdx = c.getColumnIndex(
                        android.provider.MediaStore.MediaColumns.SIZE);
                int dataIdx = c.getColumnIndex(
                        android.provider.MediaStore.MediaColumns.DATA);
                int seen = 0;
                while (c.moveToNext() && seen < 25) {
                    long size = sizeIdx >= 0 ? c.getLong(sizeIdx) : 0;
                    if (size <= 0) {
                        continue;
                    }
                    String name = nameIdx >= 0 ? c.getString(nameIdx) : null;
                    String path = dataIdx >= 0 ? c.getString(dataIdx) : null;
                    out.add(new FileRow(name == null ? "(tanpa nama)" : name,
                            size, path));
                    seen++;
                }
            }
        } catch (Throwable ignored) {
            // a collection the ROM does not expose simply contributes nothing
        }
    }

    /**
     * Same-size media in the same collection, which is what a duplicate looks
     * like without reading every file's bytes.
     *
     * The name is compared too: two different 3 MB videos are not duplicates,
     * but the same name at the same size almost always is a re-download.
     */
    private void duplicateCard() {
        LinearLayout card = Cards.card(this, Palette.accent("battery"));
        card.addView(Cards.header(this, "storage", "Kemungkinan duplikat", null));

        List<FileRow> rows = new ArrayList<>();
        collectLarge(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rows);
        collectLarge(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rows);
        collectLarge(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, rows);

        java.util.Map<String, List<FileRow>> groups = new java.util.LinkedHashMap<>();
        for (FileRow row : rows) {
            String key = row.bytes + "|" + baseName(row.name);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        int reported = 0;
        long wasted = 0;
        for (List<FileRow> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            FileRow first = group.get(0);
            wasted += first.bytes * (group.size() - 1);
            if (reported < 10) {
                card.addView(Cards.row(this, first.name,
                        group.size() + " salinan", formatSize(first.bytes)
                                + "  ·  " + first.path));
                reported++;
            }
        }

        if (reported == 0) {
            card.addView(Cards.body(this,
                    "Tidak ada kandidat duplikat pada berkas media yang terbaca.",
                    Palette.faint(this)));
        } else {
            card.addView(Cards.hint(this,
                    "Perkiraan ruang terbuang: " + formatSize(wasted)
                            + ". Pencocokan memakai nama dan ukuran karena app "
                            + "biasa tidak bisa membaca isi semua berkas."));
        }
        list.addView(card);
        Motion.enter(card, 3);
    }

    /** File name without "-1", "(1)", or "_copy" suffixes. */
    private static String baseName(String name) {
        String base = name;
        int dot = base.lastIndexOf('.');
        String ext = dot >= 0 ? base.substring(dot) : "";
        String stem = dot >= 0 ? base.substring(0, dot) : base;
        stem = stem.replaceAll("(?i)[ _-]?(copy|salinan|\\d+)$", "");
        stem = stem.replaceAll("\\(\\d+\\)$", "");
        return stem + ext;
    }

    /** One media row for the large-file and duplicate lists. */
    private static final class FileRow {
        final String name;
        final long bytes;
        final String path;

        FileRow(String name, long bytes, String path) {
            this.name = name;
            this.bytes = bytes;
            this.path = path;
        }
    }

    /** The one cleanup that is actually permitted to an ordinary app. */
    private void clearOwnCacheCard() {
        long before = directorySize(getCacheDir());

        LinearLayout card = Cards.card(this, Palette.accent("storage"));
        card.addView(Cards.header(this, "storage", "Cache CekDroid", null));
        card.addView(Cards.row(this, "Ukuran sekarang", formatSize(before)));

        TextView button = Cards.actionButton(this, "Bersihkan cache CekDroid");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Cards.dp(this, 10);
        button.setLayoutParams(lp);
        Motion.attachPress(button);
        button.setOnClickListener(v -> {
            long freed = before - deleteContents(getCacheDir());
            status.setText(freed > 0
                    ? "Cache dibersihkan, " + formatSize(freed) + " dilepas."
                    : "Cache memang sudah kosong.");
            finish();
            startActivity(getIntent());
        });
        card.addView(button);
        card.addView(Cards.hint(this,
                "App lain tidak bisa dibersihkan dari sini: menghapus data app lain "
                        + "butuh izin sistem yang tidak dimiliki app biasa."));
        list.addView(card);
        Motion.enter(card, 0);
    }

    private void loadAppSizes() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            status.setText("Analisis per-app butuh Android 8 ke atas.");
            return;
        }
        StorageStatsManager ssm =
                (StorageStatsManager) getSystemService(STORAGE_STATS_SERVICE);
        StorageManager sm = (StorageManager) getSystemService(STORAGE_SERVICE);
        if (ssm == null || sm == null) {
            status.setText("Layanan statistik penyimpanan tidak tersedia.");
            return;
        }

        final List<long[]> sizes = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        int granted = 0;
        try {
            UUID uuid = sm.getUuidForPath(android.os.Environment.getDataDirectory());
            PackageManager pm = getPackageManager();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                try {
                    StorageStats stats = ssm.queryStatsForPackage(
                            uuid, ai.packageName, UserHandle.getUserHandleForUid(ai.uid));
                    long bytes = stats.getAppBytes() + stats.getDataBytes()
                            + stats.getCacheBytes();
                    if (bytes <= 0) {
                        continue;
                    }
                    granted++;
                    sizes.add(new long[]{bytes});
                    CharSequence label = pm.getApplicationLabel(ai);
                    labels.add(label + "  ·  " + ai.packageName);
                } catch (Throwable ignored) {
                    // packages without a readable size stay out of the ranking
                }
            }
        } catch (Throwable t) {
            status.setText("Tidak bisa membaca ukuran per-app: " + t.getMessage());
            return;
        }

        if (granted == 0) {
            status.setText("Butuh izin Akses penggunaan. Beri di Pengaturan > "
                    + "Aplikasi > CekDroid > Akses penggunaan.");
            return;
        }

        // Sort by size, biggest first: that is the only order that answers
        // "what should I delete".
        Integer[] order = new Integer[sizes.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) ->
                Long.compare(sizes.get(b)[0], sizes.get(a)[0]));

        status.setText(granted + " app dengan ukuran terbaca, terbesar di atas.");

        int shown = Math.min(order.length, 40);
        LinearLayout card = Cards.card(this, Palette.accent("storage"));
        card.addView(Cards.header(this, "storage", "App terbesar",
                String.valueOf(granted)));
        for (int i = 0; i < shown; i++) {
            int idx = order[i];
            TextView row = new TextView(this);
            row.setText(String.format(Locale.US, "%-2d  %s", i + 1, labels.get(idx)));
            row.setTextSize(12.5f);
            row.setTextColor(Palette.text(this));
            row.setPadding(0, Cards.dp(this, 6), 0, 0);
            card.addView(row);

            TextView size = new TextView(this);
            size.setText(formatSize(sizes.get(idx)[0]));
            size.setTextSize(12f);
            size.setTextColor(Palette.dim(this));
            size.setTypeface(Typeface.MONOSPACE);
            card.addView(size);
        }
        if (order.length > shown) {
            card.addView(Cards.hint(this, "+" + (order.length - shown)
                    + " app lain tidak ditampilkan"));
        }
        list.addView(card);
        Motion.enter(card, 1);
    }

    private static long directorySize(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        long total = 0;
        for (File f : files) {
            total += f.isDirectory() ? directorySize(f) : f.length();
        }
        return total;
    }

    /** Delete children, return bytes left so the caller can report what freed. */
    private static long deleteContents(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                deleteContents(f);
                f.delete();
            } else {
                f.delete();
            }
        }
        return directorySize(dir);
    }

    static String formatSize(long bytes) {
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.US, "%.1f MB", mb);
        }
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }
}
