package ai.cekdroid.collect.sections;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.UserHandle;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;
import ai.cekdroid.collect.SectionBase;

/**
 * Every mounted volume, plus the three-way split DevInfo shows.
 *
 * StatFs gives total/free exactly. "Apps & data" is the sum of StorageStats
 * over installed packages, which needs PACKAGE_USAGE_STATS; when that grant is
 * missing the remainder is reported as system-reserved instead of guessed.
 * Walking arbitrary directories is not possible under scoped storage, so
 * per-folder analysis is deliberately limited to MediaStore-style categories.
 */
public class StorageSection implements Section {

    @Override
    public String id() {
        return "storage";
    }

    @Override
    public String title() {
        return "Penyimpanan";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        File dataDir = Environment.getDataDirectory();
        long[] ft = freeTotalKb(dataDir);
        if (ft[0] <= 0) {
            out.warn("StatFs gagal pada /data");
            return;
        }
        long totalKb = ft[0];
        long freeKb = ft[1];
        long usedKb = totalKb - freeKb;

        out.put("internal_total_kb", totalKb);
        out.put("internal_free_kb", freeKb);
        out.put("internal_used_kb", usedKb);
        out.put("internal_free_pct", SectionBase.round1(100.0 * freeKb / totalKb));
        out.put("internal_used_pct", SectionBase.round1(100.0 * usedKb / totalKb));

        // DevInfo prints the storage medium beside the capacity. There is no
        // public API for it, so it is inferred from the boot device node name,
        // which every vendor sets in ro.boot.bootdevice.
        out.put("media_badge", mediaBadge());

        // Three-way split: apps & data, system & reserved, free.
        //
        // StorageStats returns real numbers for this app only and zeros for
        // everything else without the usage-access grant. A total under 10% of
        // used space is therefore "not permitted", not "no apps installed" —
        // on a phone with apps, app data is never a sliver. Reporting it as a
        // real split made the bar read "Sistem 77%", which is simply wrong.
        Long appsKb = appsAndDataKb(ctx);
        boolean plausible = appsKb != null && appsKb > usedKb / 10;
        if (plausible) {
            long systemKb = usedKb - appsKb;
            out.put("segment_apps_kb", appsKb);
            out.put("segment_apps_pct", SectionBase.round1(100.0 * appsKb / totalKb));
            out.put("segment_system_kb", systemKb);
            out.put("segment_system_pct", SectionBase.round1(100.0 * systemKb / totalKb));
            out.put("segment_free_kb", freeKb);
            out.put("segment_free_pct", SectionBase.round1(100.0 * freeKb / totalKb));
        } else {
            out.put("segment_apps_kb", null);
            // Say which of the two reasons applies. "Butuh izin" sent people
            // to a screen where the permission was already on, because the OS
            // toggle and the grant are different things.
            if (usageAccess(ctx)) {
                out.warn("rincian aplikasi vs sistem tidak terbaca meski akses "
                        + "penggunaan aktif; StorageStats mengembalikan nol");
            } else {
                out.warn("rincian aplikasi vs sistem butuh izin Akses penggunaan "
                        + "(Pengaturan > Aplikasi > Akses penggunaan)");
            }
        }

        List<LinkedHashMap<String, Object>> volumes = new ArrayList<>();
        try {
            StorageManager sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);
            List<StorageVolume> list = sm == null ? null : sm.getStorageVolumes();
            if (list != null) {
                for (StorageVolume v : list) {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("description", v.getDescription(ctx));
                    row.put("removable", v.isRemovable());
                    row.put("emulated", v.isEmulated());
                    row.put("state", v.getState());
                    row.put("primary", v.isPrimary());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        File dir = v.getDirectory();
                        if (dir != null) {
                            row.put("path", dir.getAbsolutePath());
                            long[] vol = freeTotalKb(dir);
                            row.put("total_kb", vol[0]);
                            row.put("free_kb", vol[1]);
                        }
                    }
                    volumes.add(row);
                }
            }
        } catch (Throwable t) {
            out.warn("StorageManager: " + t.getMessage());
        }
        if (volumes.isEmpty()) {
            out.warn("daftar volume tidak terbaca");
        }
        out.rows("volumes", volumes);

        // Media categories are readable through MediaStore without special
        // grants, so the interesting ones are worth the query.
        putMedia(ctx, out);

        File external = ctx.getExternalFilesDir(null);
        if (external != null) {
            long[] eft = freeTotalKb(external);
            out.put("external_app_total_kb", eft[0]);
            out.put("external_app_free_kb", eft[1]);
            out.put("external_app_path", external.getAbsolutePath());
        }

        File cacheDir = ctx.getCacheDir();
        if (cacheDir != null) {
            out.put("cache_dir", cacheDir.getAbsolutePath());
            out.put("cache_used_kb", directorySizeKb(cacheDir));
        }
    }

    /**
     * Sum of app APK + data across installed packages, in KB.
     *
     * Returns null when StorageStats is unavailable, which is the normal case
     * without the usage-access grant — the caller then says so rather than
     * presenting a made-up split.
     */
    private Long appsAndDataKb(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }
        try {
            StorageStatsManager ssm =
                    (StorageStatsManager) ctx.getSystemService(Context.STORAGE_STATS_SERVICE);
            StorageManager sm =
                    (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);
            if (ssm == null || sm == null) {
                return null;
            }
            UUID uuid = sm.getUuidForPath(Environment.getDataDirectory());
            long total = 0;
            PackageManager pm = ctx.getPackageManager();
            List<android.content.pm.ApplicationInfo> apps =
                    pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (android.content.pm.ApplicationInfo ai : apps) {
                try {
                    StorageStats stats = ssm.queryStatsForPackage(
                            uuid, ai.packageName, UserHandle.getUserHandleForUid(ai.uid));
                    total += stats.getAppBytes() + stats.getDataBytes();
                } catch (Throwable ignored) {
                    // a package can vanish between listing and querying
                }
            }
            return total / 1024;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Photos / video / audio / documents, straight from MediaStore. */
    private void putMedia(Context ctx, Recorder out) {
        String[][] columns = {
                {"photo", android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()},
                {"video", android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString()},
                {"audio", android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString()},
        };
        for (String[] pair : columns) {
            try {
                android.net.Uri uri = android.net.Uri.parse(pair[1]);
                String[] projection = {android.provider.MediaStore.MediaColumns.SIZE};
                long bytes = 0;
                long count = 0;
                try (android.database.Cursor c = ctx.getContentResolver()
                        .query(uri, projection, null, null, null)) {
                    if (c != null) {
                        int idx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.SIZE);
                        while (c.moveToNext()) {
                            bytes += c.getLong(idx);
                            count++;
                        }
                    }
                }
                out.put("media_" + pair[0] + "_kb", bytes / 1024);
                out.put("media_" + pair[0] + "_count", count);
            } catch (Throwable t) {
                out.warn("MediaStore " + pair[0] + ": " + t.getMessage());
            }
        }
    }

    /**
     * "eMMC" / "UFS" / "NVMe" from the boot device node name.
     *
     * Vendors name the node after the controller ("soc/1d84000.ufshc",
     * "sdhci/mmcblk0", "nvme0n1"). Matching those is a heuristic, so an
     * unrecognised node returns null rather than defaulting to the common
     * answer.
     */
    private static String mediaBadge() {
        String node = SystemSection.getProp("ro.boot.bootdevice");
        if (node == null) {
            node = SystemSection.getProp("ro.boot.boot_devices");
        }
        if (node == null) {
            return null;
        }
        String lower = node.toLowerCase(java.util.Locale.US);
        if (lower.contains("ufs") || lower.contains("ufshc")) {
            return "UFS";
        }
        if (lower.contains("nvme")) {
            return "NVMe";
        }
        if (lower.contains("mmc")) {
            return "eMMC";
        }
        return null;
    }

    /** True when usage-access has been granted; AppOps is the only readable check. */
    private static boolean usageAccess(Context ctx) {
        try {
            android.app.AppOpsManager ops = (android.app.AppOpsManager)
                    ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) {
                return false;
            }
            int mode = ops.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), ctx.getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** {totalKb, freeKb}, or {0,0} when the path is not usable. */
    private static long[] freeTotalKb(File dir) {
        try {
            StatFs stat = new StatFs(dir.getAbsolutePath());
            return new long[]{stat.getTotalBytes() / 1024, stat.getAvailableBytes() / 1024};
        } catch (Throwable t) {
            return new long[]{0, 0};
        }
    }

    /** Only used for the app's own cache dir, which is never blocked. */
    private static long directorySizeKb(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        long total = 0;
        for (File f : files) {
            total += f.isDirectory() ? directorySizeKb(f) : f.length();
        }
        return total / 1024;
    }
}
