package ai.cekdroid.collect.sections;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.LinkedHashMap;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;

/** Identity and build fingerprint: what the device calls itself. */
public class DeviceSection implements Section {

    @Override
    public String id() {
        return "device";
    }

    @Override
    public String title() {
        return "Perangkat";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        out.put("manufacturer", Build.MANUFACTURER);
        out.put("brand", Build.BRAND);
        out.put("model", Build.MODEL);

        // The marketing name is what people recognise ("OPPO A16"); Build.MODEL
        // is the factory code ("CPH2269"). Every vendor parks it in a different
        // prop, so try the known ones and fall back to Build.MODEL.
        String marketing = marketingName();
        out.put("marketing_name", marketing);
        out.put("device", Build.DEVICE);
        out.put("product", Build.PRODUCT);
        out.put("board", Build.BOARD);
        out.put("hardware", Build.HARDWARE);
        out.put("bootloader", Build.BOOTLOADER);
        out.put("android", Build.VERSION.RELEASE);
        out.put("sdk", Build.VERSION.SDK_INT);
        out.put("codename", Build.VERSION.CODENAME);
        out.put("incremental", Build.VERSION.INCREMENTAL);
        out.put("security_patch", Build.VERSION.SECURITY_PATCH);
        out.put("build_type", Build.TYPE);
        out.put("build_tags", Build.TAGS);
        out.put("fingerprint", Build.FINGERPRINT);
        out.put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : null);
        out.put("abis", String.join(", ", Build.SUPPORTED_ABIS));

        long uptimeMs = SystemClock.elapsedRealtime();
        out.put("uptime_ms", uptimeMs);
        out.put("uptime_text", formatUptime(uptimeMs / 1000));

        Long bootMs = System.currentTimeMillis() - uptimeMs;
        out.put("boot_time", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US).format(new java.util.Date(bootMs)));

        // Boot counter is a Global setting on most ROMs; absent on a few.
        try {
            int boots = Settings.Global.getInt(ctx.getContentResolver(),
                    Settings.Global.BOOT_COUNT, -1);
            out.put("boot_count", boots >= 0 ? boots : null);
            if (boots < 0) {
                out.warn("jumlah reboot tidak dilaporkan sistem");
            }
        } catch (Throwable t) {
            out.put("boot_count", null);
            out.warn("jumlah reboot: " + t.getMessage());
        }

        putCapabilities(ctx, out);
    }

    /**
     * Hardware capabilities, as a check/cross grid.
     *
     * DevInfo lists exactly these under its Perangkat tab, and every one is a
     * declared system feature rather than a guess: the phone either advertises
     * it to apps or it does not. A false row ("NFC ✕") is information too, so
     * all of them are written; the UI decides how to draw the cross.
     */
    private static void putCapabilities(Context ctx, Recorder out) {
        PackageManager pm = ctx.getPackageManager();
        LinkedHashMap<String, Object> caps = new LinkedHashMap<>();
        caps.put("nfc", has(pm, PackageManager.FEATURE_NFC));
        caps.put("nfc_hce", has(pm, PackageManager.FEATURE_NFC_HOST_CARD_EMULATION));
        caps.put("fingerprint", has(pm, PackageManager.FEATURE_FINGERPRINT));
        caps.put("face", has(pm, "android.hardware.biometrics.face"));
        caps.put("iris", has(pm, "android.hardware.biometrics.iris"));
        caps.put("bluetooth", has(pm, PackageManager.FEATURE_BLUETOOTH));
        caps.put("bluetooth_le", has(pm, PackageManager.FEATURE_BLUETOOTH_LE));
        caps.put("infrared", has(pm, PackageManager.FEATURE_CONSUMER_IR));
        caps.put("uwb", has(pm, "android.hardware.uwb"));
        caps.put("wifi", has(pm, PackageManager.FEATURE_WIFI));
        caps.put("ethernet", has(pm, PackageManager.FEATURE_ETHERNET));
        caps.put("gps", has(pm, PackageManager.FEATURE_LOCATION_GPS));
        caps.put("usb_host", has(pm, PackageManager.FEATURE_USB_HOST));
        caps.put("telephony", has(pm, PackageManager.FEATURE_TELEPHONY));
        caps.put("esim", has(pm, "android.hardware.telephony.euicc"));
        caps.put("flashlight", has(pm, PackageManager.FEATURE_CAMERA_FLASH));
        out.put("capabilities", caps);
    }

    private static boolean has(PackageManager pm, String feature) {
        try {
            return pm.hasSystemFeature(feature);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Vendor marketing name. Each OEM uses a different property, and lots of
     * devices leave them all empty, so this returns null rather than a guess.
     */
    static String marketingName() {
        String[] props = {
                "ro.vendor.oplus.market.name",   // OPPO / Realme / OnePlus
                "ro.product.marketname",         // AOSP-ish
                "ro.product.odm.marketname",
                "ro.product.vendor.marketname",
                "ro.config.marketing_name",
                "ro.product.market_name",
                "ro.cm.device.name",
        };
        for (String prop : props) {
            String value = SystemSection.getProp(prop);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /** "0d 22h 59m 8s", the way a person reads uptime. */
    static String formatUptime(long seconds) {
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append("d ");
        }
        sb.append(h).append('h')
                .append(' ').append(m).append('m')
                .append(' ').append(s).append('s');
        return sb.toString();
    }
}
