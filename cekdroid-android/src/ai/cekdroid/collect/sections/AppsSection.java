package ai.cekdroid.collect.sections;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;
import ai.cekdroid.collect.SectionBase;

/**
 * Installed third-party apps with the fields a device-info app is expected to
 * show: target/min SDK, installer, ABI, and whether it was sideloaded.
 */
public class AppsSection implements Section {

    @Override
    public String id() {
        return "apps";
    }

    @Override
    public String title() {
        return "Aplikasi";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        PackageManager pm = ctx.getPackageManager();
        List<PackageInfo> all;
        try {
            all = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                    : pm.getInstalledPackages(0);
        } catch (Throwable t) {
            out.warn("daftar paket gagal: " + t.getMessage());
            return;
        }

        int thirdParty = 0;
        List<LinkedHashMap<String, Object>> rows = new ArrayList<>();
        for (PackageInfo pi : all) {
            ApplicationInfo ai = pi.applicationInfo;
            if (ai == null) {
                continue;
            }
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (system) {
                continue;
            }
            thirdParty++;

            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("name", pi.packageName);
            row.put("version", pi.versionName);
            row.put("version_code", (long) pi.getLongVersionCode());
            row.put("target_sdk", pi.applicationInfo.targetSdkVersion);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                row.put("min_sdk", pi.applicationInfo.minSdkVersion);
            }
            String installer = installerOf(pm, pi.packageName);
            row.put("installer", installer);
            // "Sideloaded" means "not from an app store". The old test only
            // accepted Google Play, which flagged every OEM store, F-Droid
            // install and package-installer install as sideloaded — on an OPPO
            // that turned most preinstalled apps into false positives.
            row.put("sideloaded", isSideloaded(installer, ai));
            row.put("system", false);
            row.put("enabled", ai.enabled);
            row.put("debuggable", (ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
            row.put("is_64bit", is64Bit(ai));
            row.put("native_libs", ai.nativeLibraryDir == null ? null
                    : new File(ai.nativeLibraryDir).getParentFile() == null ? null
                    : new File(ai.nativeLibraryDir).getName());
            row.put("data_dir", ai.dataDir);
            row.put("apk_path", ai.sourceDir);
            row.put("apk_size_kb", apkSizeKb(ai.sourceDir));
            rows.add(row);
        }

        out.put("third_party_count", thirdParty);
        out.put("system_count", all.size() - thirdParty);
        out.put("total_count", all.size());
        // Kept whole in the report: the full list view pages through it, and a
        // truncated JSON would make "Lihat semua" a lie. The inline card caps
        // what it draws instead.
        out.rows("installed", rows);
    }

    private static String installerOf(PackageManager pm, String pkg) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return pm.getInstallSourceInfo(pkg).getInstallingPackageName();
            }
            return pm.getInstallerPackageName(pkg);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * True when the app did not come from a recognised app store.
     *
     * The allowlist is the set of installers that are actually stores: Google
     * Play, the platform package installer (used by Play and by the system on
     * older builds), Samsung Galaxy Store, Huawei AppGallery, Amazon Appstore,
     * and F-Droid. An unknown non-null installer is still sideloaded, and a
     * null installer on an app that is not preinstalled is the classic ADB
     * `pm install` case, which is sideloading by definition.
     */
    private static boolean isSideloaded(String installer, ApplicationInfo ai) {
        boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        if (system) {
            return false;
        }
        if (installer == null) {
            return true;
        }
        for (String store : STORE_INSTALLERS) {
            if (store.equals(installer)) {
                return false;
            }
        }
        return true;
    }

    private static final String[] STORE_INSTALLERS = {
            "com.android.vending",
            "com.google.android.packageinstaller",
            "com.google.android.apps.packageinstaller",
            "com.sec.android.app.samsungapps",
            "com.huawei.appmarket",
            "com.amazon.venezia",
            "org.fdroid.fdroid",
            "com.oppo.market",
            "com.heytap.market",
            "com.xiaomi.mipicks",
            "com.vivo.appstore",
    };

    /**
     * True when the app ships a 64-bit native library.
     *
     * Android has no direct query for this, but nativeLibraryDir names the ABI
     * directory the app's native code was extracted to (".../lib/arm64"), so a
     * "64" in the last segment is the platform's own answer.
     */
    private static boolean is64Bit(ApplicationInfo ai) {
        if (ai.nativeLibraryDir == null) {
            return false;
        }
        String dir = new File(ai.nativeLibraryDir).getName();
        return dir.contains("64");
    }

    private static Long apkSizeKb(String sourceDir) {
        if (sourceDir == null) {
            return null;
        }
        File f = new File(sourceDir);
        return f.isFile() ? f.length() / 1024 : null;
    }
}
