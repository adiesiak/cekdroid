package ai.cekdroid.collect.sections;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;
import ai.cekdroid.collect.SectionBase;

/** RAM, swap/zram, and reclaim pressure. */
public class MemorySection implements Section {

    @Override
    public String id() {
        return "memory";
    }

    @Override
    public String title() {
        return "Memori";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(mi);
            }
            out.put("total_kb", mi.totalMem / 1024);
            out.put("available_kb", mi.availMem / 1024);
            out.put("threshold_kb", mi.threshold / 1024);
            out.put("low_memory", mi.lowMemory);
            if (mi.totalMem > 0) {
                out.put("used_pct", SectionBase.round1(
                        100.0 * (mi.totalMem - mi.availMem) / mi.totalMem));
            }
            if (am != null) {
                out.put("heap_class_mb", am.getMemoryClass());
                out.put("large_heap_class_mb", am.getLargeMemoryClass());
            }
        } catch (Throwable t) {
            out.warn("ActivityManager: " + t.getMessage());
        }

        Map<String, Long> meminfo = readMeminfo();
        if (meminfo.isEmpty()) {
            out.warn("/proc/meminfo tidak terbaca");
            return;
        }
        out.put("meminfo_free_kb", meminfo.get("MemFree"));
        out.put("meminfo_cached_kb", meminfo.get("Cached"));
        out.put("meminfo_buffers_kb", meminfo.get("Buffers"));

        // Five-way split, matching how DevInfo explains RAM: Apps / Cache /
        // System / Other / Free. Derived from /proc/meminfo, which is
        // world-readable, so this works without any grant.
        long totalKb = orZero(meminfo.get("MemTotal"));
        if (totalKb > 0) {
            long freeKb = orZero(meminfo.get("MemFree"));
            long cachedKb = orZero(meminfo.get("Cached")) + orZero(meminfo.get("Buffers"));
            long slabKb = orZero(meminfo.get("Slab"));
            // /proc/meminfo names free, cache and kernel slab directly. What is
            // left is everything else the kernel does not itemise: app
            // anonymous memory, shared pages, page tables. That remainder is
            // reported as "Aplikasi" and "Lainnya" cannot be separated from it
            // by meminfo alone, so other is written as null with a reason
            // instead of a fabricated zero.
            long appsKb = Math.max(0, totalKb - freeKb - cachedKb - slabKb);

            out.put("seg_total_kb", totalKb);
            out.put("seg_apps_kb", appsKb);
            out.put("seg_apps_pct", SectionBase.round1(100.0 * appsKb / totalKb));
            out.put("seg_cache_kb", cachedKb);
            out.put("seg_cache_pct", SectionBase.round1(100.0 * cachedKb / totalKb));
            out.put("seg_system_kb", slabKb);
            out.put("seg_system_pct", SectionBase.round1(100.0 * slabKb / totalKb));
            out.put("seg_free_kb", freeKb);
            out.put("seg_free_pct", SectionBase.round1(100.0 * freeKb / totalKb));
            out.put("seg_other_kb", null);
        }

        // The kernel counters DevInfo lists under the memory chart.
        out.put("meminfo_active_kb", meminfo.get("Active"));
        out.put("meminfo_inactive_kb", meminfo.get("Inactive"));
        out.put("meminfo_shmem_kb", meminfo.get("Shmem"));
        out.put("meminfo_slab_kb", meminfo.get("Slab"));
        out.put("meminfo_sreclaimable_kb", meminfo.get("SReclaimable"));
        out.put("meminfo_sunreclaim_kb", meminfo.get("SUnreclaim"));
        out.put("meminfo_zram_kb", meminfo.get("SwapTotal"));
        out.put("meminfo_compressed_kb", meminfo.get("ComprMem"));

        Long swapTotal = meminfo.get("SwapTotal");
        Long swapFree = meminfo.get("SwapFree");
        if (swapTotal != null && swapTotal > 0) {
            long used = Math.max(0, swapTotal - (swapFree == null ? 0 : swapFree));
            out.put("swap_total_kb", swapTotal);
            out.put("swap_used_kb", used);
            out.put("swap_used_pct", SectionBase.round1(100.0 * used / swapTotal));
        }
    }

    private static long orZero(Long v) {
        return v == null ? 0 : v;
    }

    private static Map<String, Long> readMeminfo() {
        Map<String, Long> out = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String[] parts = line.substring(colon + 1).trim().split("\\s+");
                if (parts.length > 0) {
                    try {
                        out.put(key, Long.parseLong(parts[0]));
                    } catch (NumberFormatException ignored) {
                        // non-numeric rows are simply skipped
                    }
                }
            }
        } catch (Throwable ignored) {
            // unreadable /proc/meminfo is recorded by the caller's empty check
        }
        return out;
    }
}
