package ai.cekdroid.collect.sections;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;

/**
 * Every capability the platform declares to apps.
 *
 * DevInfo prints this as one long list with a note that a declaration is not a
 * guarantee of availability. That caveat is why the list is written verbatim
 * rather than filtered: a feature an OEM declared but did not ship is a real
 * fact about the device, and hiding it would make the two tools disagree.
 */
public class SystemFeaturesSection implements Section {

    /** Keeps the report bounded; a phone declares a few hundred features. */
    private static final int MAX_FEATURES = 400;

    @Override
    public String id() {
        return "features";
    }

    @Override
    public String title() {
        return "Fitur sistem";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        PackageManager pm = ctx.getPackageManager();
        FeatureInfo[] features;
        try {
            features = pm.getSystemAvailableFeatures();
        } catch (Throwable t) {
            out.warn("daftar fitur sistem gagal: " + t.getMessage());
            return;
        }
        if (features == null) {
            out.warn("sistem tidak melaporkan daftar fitur");
            return;
        }

        List<LinkedHashMap<String, Object>> rows = new ArrayList<>();
        int declared = 0;
        for (FeatureInfo info : features) {
            if (info == null) {
                continue;
            }
            if (info.name == null) {
                // A nameless entry marks an OpenGL ES version the device
                // supports; DevInfo shows those as GLES rows.
                if (info.reqGlEsVersion == 0) {
                    continue;
                }
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("name", "GLES " + glesName(info.reqGlEsVersion));
                row.put("value", String.format(java.util.Locale.US, "0x%x",
                        info.reqGlEsVersion));
                rows.add(row);
                declared++;
            } else {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("name", info.name);
                row.put("value", info.version > 0
                        ? String.valueOf(info.version) : null);
                rows.add(row);
                declared++;
            }
            if (rows.size() >= MAX_FEATURES) {
                out.warn("daftar fitur dipotong di " + MAX_FEATURES + " entri");
                break;
            }
        }
        out.put("count", declared);
        out.rows("features", rows);
    }

    /** "3.2" from 0x00030002, the way the platform spells GLES versions. */
    private static String glesName(int packed) {
        if (packed == 0) {
            return "?";
        }
        return (packed >> 16) + "." + (packed & 0xFFFF);
    }
}
