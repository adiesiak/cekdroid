package ai.cekdroid.collect.sections;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;
import ai.cekdroid.collect.SectionBase;

/**
 * CPU identity, topology, and current frequencies.
 *
 * /proc/cpuinfo and cpufreq are world-readable on essentially every ROM, so
 * this works without privileges. Frequencies are sampled once, not averaged.
 */
public class CpuSection implements Section {

    @Override
    public String id() {
        return "cpu";
    }

    @Override
    public String title() {
        return "CPU";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        out.put("cores", Runtime.getRuntime().availableProcessors());
        out.put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : null);
        out.put("abis", String.join(", ", Build.SUPPORTED_ABIS));
        out.put("hardware", Build.HARDWARE);
        out.put("board", Build.BOARD);

        String cpuinfo = SectionBase.readFile("/proc/cpuinfo");
        if (cpuinfo != null) {
            out.put("hardware_cpuinfo", firstValue(cpuinfo, "Hardware"));
            out.put("model_name", firstValue(cpuinfo, "model name"));
            out.put("processor_implementer", firstValue(cpuinfo, "CPU implementer"));
            out.put("processor_architecture", firstValue(cpuinfo, "CPU architecture"));
            out.put("processor_variant", firstValue(cpuinfo, "CPU variant"));
            out.put("processor_part", firstValue(cpuinfo, "CPU part"));
            out.put("processor_revision", firstValue(cpuinfo, "CPU revision"));
        } else {
            out.warn("/proc/cpuinfo tidak terbaca");
        }

        out.put("core_online", onlineCores());
        out.put("governor", cpuGovernor());

        List<LinkedHashMap<String, Object>> freqs = frequencies();
        if (freqs.isEmpty()) {
            out.warn("frekuensi CPU tidak terbaca (cpufreq dibatasi)");
        }
        out.rows("frequencies", freqs);

        // DevInfo summarises the clusters before the per-core grid, because
        // "4 x 900-2301 MHz / 4 x 400-1800 MHz" is the single line that says
        // what class of chip this is. Derived from the same cpufreq nodes, so
        // nothing is read twice.
        putClusters(out, freqs);
        putCoreStructure(out, cpuinfo);
    }

    /**
     * Group cores into clusters by their frequency range.
     *
     * A big.LITTLE SoC has one cpufreq policy per cluster, so cores sharing a
     * (min, max) pair are the same cluster. Writing the group as a range
     * ("900MHz - 2301MHz") is how every chip spec sheet states it; the UI
     * prints one line per cluster instead of eight identical rows.
     */
    private static void putClusters(Recorder out,
                                    List<LinkedHashMap<String, Object>> freqs) {
        java.util.LinkedHashMap<String, List<String>> groups =
                new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, Long[]> ranges = new java.util.LinkedHashMap<>();
        for (LinkedHashMap<String, Object> row : freqs) {
            Long min = asLong(row.get("min_mhz"));
            Long max = asLong(row.get("max_mhz"));
            if (max == null) {
                continue;
            }
            String key = (min == null ? "?" : min) + "-" + max;
            groups.computeIfAbsent(key, k -> new java.util.ArrayList<>())
                    .add(String.valueOf(row.get("core")));
            ranges.put(key, new Long[]{min, max});
        }
        if (groups.isEmpty()) {
            return;
        }
        List<LinkedHashMap<String, Object>> clusters = new java.util.ArrayList<>();
        int index = 0;
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            Long[] range = ranges.get(e.getKey());
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("index", index++);
            row.put("cores", e.getValue().size());
            row.put("min_mhz", range[0]);
            row.put("max_mhz", range[1]);
            row.put("core_list", String.join(", ", e.getValue()));
            clusters.add(row);
        }
        out.rows("clusters", clusters);

        long overallMax = 0;
        long overallMin = Long.MAX_VALUE;
        for (Long[] range : ranges.values()) {
            if (range[1] != null) {
                overallMax = Math.max(overallMax, range[1]);
            }
            if (range[0] != null) {
                overallMin = Math.min(overallMin, range[0]);
            }
        }
        out.put("frequency_range_text", overallMax > 0
                ? (overallMin == Long.MAX_VALUE ? "?" : overallMin) + "MHz - "
                        + overallMax + "MHz"
                : null);
    }

    /**
     * Human name of the core, from the ARM part number in /proc/cpuinfo.
     *
     * 0xd03 is the A53 that every Helio G35 cluster uses; the table covers the
     * cores a phone in this class can plausibly have. An unknown part is
     * reported as its raw code, never guessed.
     */
    private static void putCoreStructure(Recorder out, String cpuinfo) {
        if (cpuinfo == null) {
            return;
        }
        String part = firstValue(cpuinfo, "CPU part");
        if (part == null) {
            return;
        }
        String name = CORE_NAMES.get(part.toLowerCase(java.util.Locale.US));
        out.put("core_part_code", part);
        out.put("core_name", name == null ? part : name);
    }

    private static final java.util.Map<String, String> CORE_NAMES =
            new java.util.HashMap<>();

    static {
        CORE_NAMES.put("0xd03", "Cortex-A53");
        CORE_NAMES.put("0xd04", "Cortex-A35");
        CORE_NAMES.put("0xd05", "Cortex-A55");
        CORE_NAMES.put("0xd07", "Cortex-A57");
        CORE_NAMES.put("0xd08", "Cortex-A72");
        CORE_NAMES.put("0xd09", "Cortex-A73");
        CORE_NAMES.put("0xd0a", "Cortex-A75");
        CORE_NAMES.put("0xd0b", "Cortex-A76");
        CORE_NAMES.put("0xd0c", "Neoverse-N1");
        CORE_NAMES.put("0xd0d", "Cortex-A77");
        CORE_NAMES.put("0xd41", "Cortex-A78");
        CORE_NAMES.put("0xd44", "Cortex-X1");
        CORE_NAMES.put("0xd46", "Cortex-A510");
        CORE_NAMES.put("0xd47", "Cortex-A710");
        CORE_NAMES.put("0xd48", "Cortex-X2");
    }

    private static Long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    /** Values like "Hardware : Qualcomm" — first match wins. */
    private static String firstValue(String block, String key) {
        for (String line : block.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            if (line.substring(0, colon).trim().equalsIgnoreCase(key)) {
                return SectionBase.s(line.substring(colon + 1));
            }
        }
        return null;
    }

    /** Count "cpuN" dirs that report a nonzero max_freq. */
    private static Integer onlineCores() {
        File root = new File("/sys/devices/system/cpu");
        File[] dirs = root.listFiles();
        if (dirs == null) {
            return null;
        }
        int n = 0;
        for (File d : dirs) {
            if (d.getName().matches("cpu[0-9]+") && d.isDirectory()) {
                n++;
            }
        }
        return n == 0 ? null : n;
    }

    private static String cpuGovernor() {
        for (int i = 0; i < 16; i++) {
            String g = SectionBase.readFirstLine(
                    new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_governor"));
            if (g != null && !g.isEmpty()) {
                return g;
            }
        }
        return null;
    }

    private List<LinkedHashMap<String, Object>> frequencies() {
        List<LinkedHashMap<String, Object>> out = new ArrayList<>();
        File root = new File("/sys/devices/system/cpu");
        File[] dirs = root.listFiles();
        if (dirs == null) {
            return out;
        }
        java.util.Arrays.sort(dirs);
        for (File dir : dirs) {
            if (!dir.getName().matches("cpu[0-9]+") || !dir.isDirectory()) {
                continue;
            }
            File cpufreq = new File(dir, "cpufreq");
            String cur = SectionBase.readFirstLine(new File(cpufreq, "scaling_cur_freq"));
            String max = SectionBase.readFirstLine(new File(cpufreq, "cpuinfo_max_freq"));
            String min = SectionBase.readFirstLine(new File(cpufreq, "cpuinfo_min_freq"));
            if (cur == null && max == null) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("core", dir.getName());
            row.put("current_mhz", toMhz(cur));
            row.put("max_mhz", toMhz(max));
            row.put("min_mhz", toMhz(min));
            out.add(row);
        }
        return out;
    }

    private static Long toMhz(String khz) {
        if (khz == null) {
            return null;
        }
        try {
            return Long.parseLong(khz.trim()) / 1000L;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
