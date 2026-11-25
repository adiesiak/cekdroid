package ai.cekdroid.collect;

import android.content.Context;

import java.util.List;

/**
 * Runs every registered section once and hands back a Snapshot.
 *
 * Unlike the previous version this no longer owns a Json writer, so it can be
 * run repeatedly (pull to refresh, multi-tab, the headless dump activity) and
 * one failed section cannot corrupt the rest.
 */
public class Collector {

    private final Context ctx;
    private final Snapshot snapshot = new Snapshot();

    public Collector(Context ctx) {
        this.ctx = ctx;
    }

    /** Collect every section, recording failures as warnings. */
    public Snapshot collect(boolean deep) {
        snapshot.collectReset();
        for (Section section : Sections.all()) {
            snapshot.open(section.id(), section.title());
            SectionBase out = SectionBase.of(snapshot, section, deep);
            try {
                section.collect(ctx, out);
            } catch (Throwable t) {
                out.warn("bagian gagal: " + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            }
        }
        return snapshot;
    }

    public String json(boolean deep) {
        return new Json().snapshot(collect(deep)).toString();
    }

    /** Human-readable digest. Short: the UI shows cards, this is for sharing. */
    public String summaryText() {
        if (snapshot.isEmpty()) {
            return "Belum ada data.";
        }
        StringBuilder sb = new StringBuilder();
        for (String id : snapshot.ids()) {
            java.util.LinkedHashMap<String, Object> values = snapshot.section(id);
            if (values == null || values.isEmpty()) {
                continue;
            }
            sb.append('[').append(snapshot.title(id)).append("]\n");
            for (java.util.Map.Entry<String, Object> e : values.entrySet()) {
                Object v = e.getValue();
                if (v == null || (v instanceof List && ((List<?>) v).isEmpty())) {
                    continue;
                }
                sb.append("  ").append(e.getKey()).append(": ").append(v).append('\n');
            }
            sb.append('\n');
        }
        List<String> warnings = snapshot.warnings();
        if (!warnings.isEmpty()) {
            sb.append("Catatan:\n");
            for (String w : warnings) {
                sb.append(" · ").append(w).append('\n');
            }
        }
        return sb.toString();
    }
}
