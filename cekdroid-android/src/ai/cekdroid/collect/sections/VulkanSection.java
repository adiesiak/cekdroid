package ai.cekdroid.collect.sections;

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.LinkedHashMap;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;
import ai.cekdroid.collect.SectionBase;

/**
 * Vulkan capability, without a native Vulkan loader.
 *
 * The Vulkan API is native-only; there is no framework class that enumerates
 * devices. What the platform does expose is the version and hardware level as
 * declared system features, which is exactly what DevInfo shows for a device
 * whose driver never hands an app a device handle. Anything the features do
 * not carry is written as null with a warning instead of a guess.
 */
public class VulkanSection implements Section {

    @Override
    public String id() {
        return "vulkan";
    }

    @Override
    public String title() {
        return "Vulkan";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        PackageManager pm = ctx.getPackageManager();

        out.put("driver", SectionBase.s(SystemSection.getProp("ro.hardware.vulkan")));
        out.put("instance_version", declaredVersion(pm));
        out.put("api_version", declaredVersion(pm));

        // Hardware level: 0 = no support, 1 = full, 2 = limited.
        String level = systemFeature(pm, "android.hardware.vulkan.level");
        out.put("level", level);
        out.put("supported", level != null && !"0".equals(level.trim()));

        LinkedHashMap<String, Object> extras = new LinkedHashMap<>();
        extras.put("features", systemFeature(pm, "android.hardware.vulkan.features"));
        extras.put("compute", declared(pm, "android.hardware.vulkan.compute"));
        extras.put("compute_level", systemFeature(pm,
                "android.hardware.vulkan.compute.level"));
        out.put("extras", extras);

        if (level == null) {
            out.warn("Vulkan tidak dideklarasikan; device/instance detail hanya "
                    + "tersedia lewat native, yang tidak dimiliki app ini");
        }
    }

    /**
     * The declared Vulkan version, read back from the feature string.
     *
     * hasSystemFeature only returns a boolean, so getSystemAvailableFeatures is
     * used to read the trailing number the declaration carries
     * ("android.hardware.vulkan.version 0x00401000"). When more than one
     * version is declared, the highest wins.
     */
    private static String declaredVersion(PackageManager pm) {
        android.content.pm.FeatureInfo[] features;
        try {
            features = pm.getSystemAvailableFeatures();
        } catch (Throwable t) {
            return null;
        }
        if (features == null) {
            return null;
        }
        long best = -1;
        for (android.content.pm.FeatureInfo info : features) {
            if (info == null || info.name == null
                    || !info.name.startsWith("android.hardware.vulkan.version")) {
                continue;
            }
            if (info.version > best) {
                best = info.version;
            }
        }
        if (best < 0) {
            return null;
        }
        return String.format(java.util.Locale.US, "0x%08x", best);
    }

    private static String systemFeature(PackageManager pm, String name) {
        android.content.pm.FeatureInfo[] features;
        try {
            features = pm.getSystemAvailableFeatures();
        } catch (Throwable t) {
            return null;
        }
        if (features == null) {
            return null;
        }
        for (android.content.pm.FeatureInfo info : features) {
            if (info != null && name.equals(info.name)) {
                return String.valueOf(info.version);
            }
        }
        return null;
    }

    private static String declared(PackageManager pm, String name) {
        try {
            return pm.hasSystemFeature(name) ? "true" : "false";
        } catch (Throwable t) {
            return null;
        }
    }
}
