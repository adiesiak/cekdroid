package ai.cekdroid.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab order and grouping, matching DevInfo's own tab strip.
 *
 * DevInfo shows fourteen tabs, and the reason it stays readable is that each
 * one answers a single question: "what is this device", "what is it running",
 * "what is wrong with it". CekDroid used to give every collector its own tab,
 * which split related facts (battery level and battery voltage) and buried
 * others (GPU under its own tab). This registry groups the collectors the way
 * a reader expects them, while the JSON underneath keeps one block per
 * collector — the report schema does not change.
 *
 * The dashboard is not in this list: it is index 0 of the strip, always
 * present, and built by {@link Dashboard} because it is a summary of every
 * section rather than a section of its own.
 */
public final class Tabs {

    /** One tab: an id, its label, and the collectors it renders. */
    public static final class Tab {
        public final String id;
        public final String title;
        public final String[] sections;

        Tab(String id, String title, String... sections) {
            this.id = id;
            this.title = title;
            this.sections = sections;
        }
    }

    private static final List<Tab> ALL = new ArrayList<>();

    static {
        // Order is the strip order a reader sees after the dashboard.
        ALL.add(new Tab("device", "Perangkat", "device", "id"));
        ALL.add(new Tab("system", "Sistem", "system"));
        ALL.add(new Tab("cpu", "CPU", "cpu", "gpu"));
        ALL.add(new Tab("location", "Lokasi"));
        ALL.add(new Tab("network", "Jaringan", "network", "sim"));
        ALL.add(new Tab("storage", "Penyimpanan", "storage", "memory"));
        ALL.add(new Tab("battery", "Baterai", "battery"));
        ALL.add(new Tab("display", "Layar", "display"));
        ALL.add(new Tab("camera", "Kamera", "camera"));
        ALL.add(new Tab("sensor", "Sensor", "sensor"));
        ALL.add(new Tab("apps", "Aplikasi", "apps", "codec"));
        ALL.add(new Tab("tests", "Tes"));
        ALL.add(new Tab("tools", "Alat"));
    }

    private Tabs() {
    }

    /** Every tab, in strip order. */
    public static List<Tab> all() {
        return new ArrayList<>(ALL);
    }

    /** The tab with this id, or null when it does not exist. */
    public static Tab find(String id) {
        for (Tab tab : ALL) {
            if (tab.id.equals(id)) {
                return tab;
            }
        }
        return null;
    }

    /** A tab's label, falling back to the id so an unknown tab never crashes. */
    public static String titleOf(String id) {
        Tab tab = find(id);
        return tab == null ? id : tab.title;
    }

    /** True for tabs whose body is a launcher grid rather than collected data. */
    public static boolean isHub(String id) {
        return "tests".equals(id) || "tools".equals(id);
    }
}
