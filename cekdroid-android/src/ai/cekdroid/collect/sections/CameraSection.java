package ai.cekdroid.collect.sections;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.util.SizeF;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;
import ai.cekdroid.collect.SectionBase;

/**
 * Camera capabilities via Camera2 characteristics.
 *
 * Enumerating cameras needs no CAMERA permission; only opening a device does,
 * and this never opens one. Anything a ROM hides stays null.
 */
public class CameraSection implements Section {

    @Override
    public String id() {
        return "camera";
    }

    @Override
    public String title() {
        return "Kamera";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) {
            out.warn("CameraManager tidak tersedia");
            return;
        }
        String[] ids;
        try {
            ids = cm.getCameraIdList();
        } catch (Throwable t) {
            out.warn("daftar kamera gagal: " + t.getMessage());
            return;
        }
        out.put("count", ids.length);

        List<LinkedHashMap<String, Object>> cams = new ArrayList<>();
        for (String id : ids) {
            try {
                cams.add(describe(cm, id));
            } catch (Throwable t) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("error", t.getClass().getSimpleName());
                cams.add(row);
            }
        }
        out.rows("cameras", cams);
    }

    private LinkedHashMap<String, Object> describe(CameraManager cm, String id) throws Exception {
        CameraCharacteristics c = cm.getCameraCharacteristics(id);
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);

        Integer facing = c.get(CameraCharacteristics.LENS_FACING);
        row.put("facing", facing == null ? null
                : facing == CameraCharacteristics.LENS_FACING_FRONT ? "front"
                : facing == CameraCharacteristics.LENS_FACING_BACK ? "back" : "external");

        Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        row.put("flash", flash != null && flash);

        Size size = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        if (size != null) {
            row.put("sensor_width", size.getWidth());
            row.put("sensor_height", size.getHeight());
            // Megapixels is the number every spec sheet leads with, so it is
            // computed here rather than left to the reader.
            row.put("megapixels", SectionBase.round1(
                    size.getWidth() * (double) size.getHeight() / 1_000_000.0));
        }

        float[] apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
        row.put("apertures", apertures == null ? null : joinFloats(apertures));

        float[] focals = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        row.put("focal_lengths_mm", focals == null ? null : joinFloats(focals));

        // The physical sensor diagonal, from the pixel array and the physical
        // pixel size. DevInfo prints it next to the megapixel count.
        SizeF physical = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        if (physical != null && physical.getWidth() > 0 && physical.getHeight() > 0) {
            double diag = Math.sqrt(physical.getWidth() * physical.getWidth()
                    + physical.getHeight() * physical.getHeight());
            row.put("sensor_size_inch", SectionBase.round1(diag / 16.0));
        }

        Integer hw = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        row.put("hardware_level", hwName(hw));

        // Mode tables: DevInfo lists these as comma-separated names because
        // they answer "can this camera do X" without a spec-sheet lookup.
        row.put("antibanding_modes", antibandingNames(
                c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)));
        row.put("aberration_modes", aberrationNames(
                c.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)));
        row.put("autofocus_modes", autofocusNames(
                c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)));
        row.put("exposure_modes", exposureNames(
                c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)));

        Range<Integer> compensation = c.get(
                CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        if (compensation != null) {
            row.put("compensation_range", "[" + compensation.getLower() + ","
                    + compensation.getUpper() + "]");
        }
        Rational step = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        row.put("compensation_step", step == null ? null
                : step.getNumerator() + "/" + step.getDenominator());

        Range<Integer>[] fps = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        row.put("fps_ranges", fps == null ? null : joinRanges(fps));

        int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        row.put("raw_capable", hasCap(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW));
        row.put("manual_sensor",
                hasCap(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR));
        row.put("logical_multi",
                hasCap(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA));

        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map != null) {
            Size[] jpeg = map.getOutputSizes(android.graphics.ImageFormat.JPEG);
            row.put("jpeg_sizes", jpeg == null ? null : jpeg.length);
            if (jpeg != null && jpeg.length > 0) {
                row.put("max_jpeg", jpeg[0].getWidth() + "x" + jpeg[0].getHeight());
            }
        }
        return row;
    }

    private static boolean hasCap(int[] caps, int wanted) {
        if (caps == null) {
            return false;
        }
        for (int c : caps) {
            if (c == wanted) {
                return true;
            }
        }
        return false;
    }

    private static String joinFloats(float[] values) {
        StringBuilder sb = new StringBuilder();
        for (float v : values) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(SectionBase.round1(v));
        }
        return sb.toString();
    }

    private static String joinRanges(Range<Integer>[] ranges) {
        StringBuilder sb = new StringBuilder();
        for (Range<Integer> r : ranges) {
            if (r == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('[').append(r.getLower()).append(',').append(r.getUpper()).append(']');
        }
        return sb.toString();
    }

    /** CONTROL_AE_AVAILABLE_ANTIBANDING_MODES -> "Off, 50Hz, 60Hz, Auto". */
    private static String antibandingNames(int[] modes) {
        if (modes == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int mode : modes) {
            String name;
            switch (mode) {
                case CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_OFF: name = "Mati"; break;
                case CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_50HZ: name = "50Hz"; break;
                case CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_60HZ: name = "60Hz"; break;
                case CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_AUTO: name = "Otomatis"; break;
                default: name = "mode_" + mode;
            }
            appendName(sb, name);
        }
        return sb.toString();
    }

    /** COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES -> "Off, Fast, High Quality". */
    private static String aberrationNames(int[] modes) {
        if (modes == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int mode : modes) {
            String name;
            switch (mode) {
                case CameraCharacteristics.COLOR_CORRECTION_ABERRATION_MODE_OFF: name = "Mati"; break;
                case CameraCharacteristics.COLOR_CORRECTION_ABERRATION_MODE_FAST: name = "Cepat"; break;
                case CameraCharacteristics.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY:
                    name = "Kualitas tinggi"; break;
                default: name = "mode_" + mode;
            }
            appendName(sb, name);
        }
        return sb.toString();
    }

    /** CONTROL_AF_AVAILABLE_MODES -> "Off, Auto, Macro, Continuous video, ...". */
    private static String autofocusNames(int[] modes) {
        if (modes == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int mode : modes) {
            String name;
            switch (mode) {
                case CameraCharacteristics.CONTROL_AF_MODE_OFF: name = "Mati"; break;
                case CameraCharacteristics.CONTROL_AF_MODE_AUTO: name = "Otomatis"; break;
                case CameraCharacteristics.CONTROL_AF_MODE_MACRO: name = "Makro"; break;
                case CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO:
                    name = "Video kontinu"; break;
                case CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE:
                    name = "Foto beruntun"; break;
                case CameraCharacteristics.CONTROL_AF_MODE_EDOF: name = "EDOF"; break;
                default: name = "mode_" + mode;
            }
            appendName(sb, name);
        }
        return sb.toString();
    }

    /** CONTROL_AE_AVAILABLE_MODES -> "Off, On, Auto flash, Flash always on". */
    private static String exposureNames(int[] modes) {
        if (modes == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int mode : modes) {
            String name;
            switch (mode) {
                case CameraCharacteristics.CONTROL_AE_MODE_OFF: name = "Mati"; break;
                case CameraCharacteristics.CONTROL_AE_MODE_ON: name = "Aktif"; break;
                case CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH:
                    name = "Flash otomatis"; break;
                case CameraCharacteristics.CONTROL_AE_MODE_ON_ALWAYS_FLASH:
                    name = "Flash selalu aktif"; break;
                case CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE:
                    name = "Flash red-eye otomatis"; break;
                default: name = "mode_" + mode;
            }
            appendName(sb, name);
        }
        return sb.toString();
    }

    private static void appendName(StringBuilder sb, String name) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(name);
    }

    private static String hwName(Integer level) {
        if (level == null) {
            return null;
        }
        switch (level) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY: return "legacy";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED: return "limited";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL: return "full";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3: return "level3";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL: return "external";
            default: return String.valueOf(level);
        }
    }
}
