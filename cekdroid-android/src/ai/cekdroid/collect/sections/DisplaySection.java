package ai.cekdroid.collect.sections;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;
import ai.cekdroid.collect.SectionBase;

/** Resolution, density, refresh rate, HDR, and the full supported mode list. */
public class DisplaySection implements Section {

    @Override
    public String id() {
        return "display";
    }

    @Override
    public String title() {
        return "Layar";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm == null ? null : wm.getDefaultDisplay();
        if (display == null) {
            out.warn("display tidak tersedia");
            return;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        out.put("width_px", metrics.widthPixels);
        out.put("height_px", metrics.heightPixels);
        out.put("density_dpi", metrics.densityDpi);
        out.put("density", (double) metrics.density);
        out.put("scaled_density", (double) metrics.scaledDensity);
        out.put("xdpi", SectionBase.round1(metrics.xdpi));
        out.put("ydpi", SectionBase.round1(metrics.ydpi));
        out.put("refresh_rate_hz", SectionBase.round1(display.getRefreshRate()));
        out.put("name", SectionBase.s(display.getName()));
        out.put("state", display.getState());
        out.put("rotation", display.getRotation());

        // Physical size in inches, computed from the pixel dimensions and the
        // reported DPI. Android has no API for the panel diagonal, and xdpi /
        // ydpi are per-axis, so the diagonal is derived the same way a spec
        // sheet states it.
        if (metrics.xdpi > 0 && metrics.ydpi > 0) {
            double wIn = metrics.widthPixels / metrics.xdpi;
            double hIn = metrics.heightPixels / metrics.ydpi;
            out.put("size_inches", SectionBase.round1(
                    Math.sqrt(wIn * wIn + hIn * hIn)));
        } else {
            out.put("size_inches", null);
        }

        // Density bucket, the same classification the platform uses to pick
        // resources (smallestWidth bucket is a different axis; this one is the
        // Android "size" qualifier).
        out.put("density_bucket", densityBucket(metrics.densityDpi));

        // Screen-off timeout is a plain System setting; reading it needs no
        // permission even though writing it would.
        try {
            out.put("screen_off_timeout_ms", android.provider.Settings.System.getInt(
                    ctx.getContentResolver(),
                    android.provider.Settings.System.SCREEN_OFF_TIMEOUT, -1));
        } catch (Throwable t) {
            out.put("screen_off_timeout_ms", null);
            out.warn("screen_off_timeout: " + t.getMessage());
        }

        try {
            Display.Mode mode = display.getMode();
            out.put("mode_width", mode.getPhysicalWidth());
            out.put("mode_height", mode.getPhysicalHeight());
            out.put("mode_refresh_hz", SectionBase.round1(mode.getRefreshRate()));
        } catch (Throwable t) {
            out.warn("mode aktif: " + t.getMessage());
        }

        try {
            int[] hdr = display.getHdrCapabilities().getSupportedHdrTypes();
            out.put("hdr_types", String.valueOf(hdr.length));
        } catch (Throwable t) {
            out.warn("HDR: " + t.getMessage());
        }

        List<LinkedHashMap<String, Object>> modes = new ArrayList<>();
        try {
            for (Display.Mode m : display.getSupportedModes()) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("id", m.getModeId());
                row.put("width", m.getPhysicalWidth());
                row.put("height", m.getPhysicalHeight());
                row.put("refresh_hz", SectionBase.round1(m.getRefreshRate()));
                modes.add(row);
            }
        } catch (Throwable t) {
            out.warn("daftar mode: " + t.getMessage());
        }
        out.rows("modes", modes);
    }

    /** Android's density bucket for this DPI, e.g. "xhdpi". */
    private static String densityBucket(int dpi) {
        if (dpi <= 0) {
            return null;
        }
        if (dpi <= 120) {
            return "ldpi";
        }
        if (dpi <= 160) {
            return "mdpi";
        }
        if (dpi <= 213) {
            return "tvdpi";
        }
        if (dpi <= 240) {
            return "hdpi";
        }
        if (dpi <= 320) {
            return "xhdpi";
        }
        if (dpi <= 480) {
            return "xxhdpi";
        }
        return "xxxhdpi";
    }
}
