package ai.cekdroid.ui;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * What each section still lacks, and how to ask for it.
 *
 * Two different things block the same field and they need different remedies:
 * a runtime permission is a dialog the app can raise, an OS toggle (location,
 * usage access) is a settings screen the user must flip. Reporting both as
 * "butuh izin" sent people to a permission dialog that was already granted.
 *
 * Nothing here guesses: every check asks the platform for its own answer.
 */
public final class Perms {

    /** Request code for every runtime prompt in this app. */
    public static final int REQUEST_RUNTIME = 7301;

    private Perms() {
    }

    /** The runtime permissions a section cannot fill its rows without. */
    public static String[] runtime(String sectionId) {
        if ("sim".equals(sectionId)) {
            return new String[]{Manifest.permission.READ_PHONE_STATE};
        }
        if ("network".equals(sectionId)) {
            // Only the SSID needs this; everything else in the section renders
            // without it, which is why the card says what it unlocks.
            return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        }
        return new String[0];
    }

    public static boolean allGranted(Context ctx, String[] permissions) {
        for (String p : permissions) {
            if (ctx.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when a section's runtime permissions are granted but the OS-level
     * toggle it also needs is off.
     */
    public static boolean locationEnabled(Context ctx) {
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return false;
        }
        try {
            return lm.isLocationEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Usage-access is not a runtime permission: it can only be granted from a
     * settings screen, and AppOps is the only way to read it back.
     */
    public static boolean usageAccess(Context ctx) {
        try {
            AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) {
                return false;
            }
            int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), ctx.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Intent usageAccessSettings(Context ctx) {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.setData(Uri.parse("package:" + ctx.getPackageName()));
        return intent;
    }

    public static Intent locationSettings() {
        return new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    }

    /**
     * A fingerprint of every grant this app cares about, so onResume can tell
     * whether anything changed while the user was away in Settings.
     */
    public static String stateKey(Context ctx) {
        StringBuilder sb = new StringBuilder();
        for (String id : new String[]{"sim", "network"}) {
            for (String p : runtime(id)) {
                sb.append(p).append('=')
                        .append(ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED)
                        .append(';');
            }
        }
        sb.append("usage=").append(usageAccess(ctx));
        sb.append(";loc=").append(locationEnabled(ctx));
        return sb.toString();
    }

    /** Human-readable app permission names, for the settings hint. */
    public static String describe(String[] permissions) {
        if (permissions == null || permissions.length == 0) {
            return "";
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String p : permissions) {
            if (Manifest.permission.READ_PHONE_STATE.equals(p)) {
                names.add("Telepon");
            } else if (Manifest.permission.ACCESS_FINE_LOCATION.equals(p)) {
                names.add("Lokasi");
            } else {
                names.add(p.substring(p.lastIndexOf('.') + 1));
            }
        }
        return TextUtils.join(", ", names);
    }
}
