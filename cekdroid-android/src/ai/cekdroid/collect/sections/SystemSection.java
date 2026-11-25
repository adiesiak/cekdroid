package ai.cekdroid.collect.sections;

import android.content.Context;
import android.os.Build;

import java.lang.reflect.Method;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;
import ai.cekdroid.collect.SectionBase;

/** Kernel, runtime, and platform build data that is not part of Build.*. */
public class SystemSection implements Section {

    @Override
    public String id() {
        return "system";
    }

    @Override
    public String title() {
        return "Sistem";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        // /proc/version is readable on most ROMs but not all; when it is
        // blocked, compose the same information from os.version so the row is
        // never an unexplained dash.
        String kernel = stripVersion(SectionBase.readFile("/proc/version"));
        if (kernel == null) {
            String osVersion = System.getProperty("os.version");
            String osArch = System.getProperty("os.arch");
            if (osVersion != null) {
                kernel = "Linux " + osVersion
                        + (osArch == null ? "" : " (" + osArch + ")");
                out.warn("/proc/version dibatasi; versi kernel dari System property");
            } else {
                out.warn("/proc/version dan os.version tidak terbaca");
            }
        }
        out.put("kernel", kernel);
        out.put("os_name", System.getProperty("os.name"));
        out.put("os_version", System.getProperty("os.version"));
        out.put("os_arch", System.getProperty("os.arch"));
        out.put("java_vm", System.getProperty("java.vm.version"));
        out.put("java_vendor", System.getProperty("java.vendor"));
        out.put("locale", java.util.Locale.getDefault().toString());
        out.put("timezone", java.util.TimeZone.getDefault().getID());
        out.put("processors", Runtime.getRuntime().availableProcessors());
        out.put("heap_max_mb", Runtime.getRuntime().maxMemory() / 1024 / 1024);

        // SystemProperties is hidden; getprop values reached via reflection are
        // the same data the CLI probe reads, so the two outputs line up.
        out.put("build_date", getProp("ro.build.date"));
        out.put("build_user", getProp("ro.build.user"));
        out.put("build_host", getProp("ro.build.host"));
        out.put("selinux", getProp("ro.boot.selinux"));
        out.put("verified_boot", getProp("ro.boot.verifiedbootstate"));
        out.put("verity", getProp("ro.boot.veritymode"));
        out.put("encryption", getProp("ro.crypto.state"));
        out.put("kernel_cmdline", SectionBase.readFile("/proc/cmdline"));

        // The rows DevInfo prints between the build identity and SELinux. Each
        // is a read-only prop; a ROM that does not define one leaves the row
        // null rather than inventing a value.
        putIfPresent(out, "system_as_root", getProp("ro.build.system_root_image"));
        putIfPresent(out, "dynamic_partitions", getProp("ro.boot.dynamic_partitions"));
        putIfPresent(out, "seamless_updates", getProp("ro.build.ab_update"));
        putIfPresent(out, "baseband", getProp("gsm.version.baseband"));
        putIfPresent(out, "sdk_ext", getProp("ro.build.version.extension"));
        putIfPresent(out, "board_platform", getProp("ro.board.platform"));
    }

    /** Write a value only when the platform supplied one. */
    private static void putIfPresent(Recorder out, String key, String value) {
        if (value != null && !value.isEmpty()) {
            out.put(key, value);
        }
    }

    /** /proc/version is one line: "Linux version 4.19. ...". */
    private static String stripVersion(String raw) {
        if (raw == null) {
            return null;
        }
        int nl = raw.indexOf('\n');
        return nl < 0 ? raw : raw.substring(0, nl);
    }

    /**
     * android.os.SystemProperties is a hidden class, so reflect into it. On a
     * ROM where the class or method is absent this returns null rather than
     * failing the whole section.
     */
    static String getProp(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            Object v = get.invoke(null, key);
            return SectionBase.s(v == null ? null : v.toString());
        } catch (Throwable t) {
            return null;
        }
    }
}
