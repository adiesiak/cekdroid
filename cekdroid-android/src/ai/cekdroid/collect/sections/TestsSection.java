package ai.cekdroid.collect.sections;

import android.content.Context;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;

/**
 * The "Tes" tab.
 *
 * DevInfo gives its hardware tests a tab of their own rather than hiding each
 * one inside the data section it belongs to. There is nothing to collect here:
 * every entry launches an interactive screen, so this section only publishes how
 * many tools it offers and the report stays honest about being a launcher.
 */
public class TestsSection implements Section {

    /** How many test screens the hub links to; kept in sync with Hubs.tests. */
    static final int COUNT = 6;

    @Override
    public String id() {
        return "tests";
    }

    @Override
    public String title() {
        return "Tes";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        out.put("available", COUNT);
        out.put("layar", "Pola warna penuh untuk piksel mati dan burn-in");
        out.put("tombol", "Deteksi tombol fisik yang tidak merespons");
        out.put("getar", "Uji motor getar");
        out.put("sensor", "Nilai sensor realtime");
        out.put("gps", "Satelit dan posisi realtime");
        out.put("baterai", "Grafik arus dan daya realtime");
    }
}
