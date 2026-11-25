package ai.cekdroid.ui;

import java.util.ArrayList;
import java.util.List;

import ai.cekdroid.test.AppExportActivity;
import ai.cekdroid.test.BatteryChartActivity;
import ai.cekdroid.test.ButtonTestActivity;
import ai.cekdroid.test.GnssTestActivity;
import ai.cekdroid.test.ScreenTestActivity;
import ai.cekdroid.test.SensorTestActivity;
import ai.cekdroid.test.StorageAnalyzeActivity;
import ai.cekdroid.test.VibrateTestActivity;

/**
 * Which tool belongs to which tab.
 *
 * Kept out of the collect package on purpose: sections describe data, this
 * describes UI entry points, and mixing them would make a section untestable
 * without an Activity.
 */
public final class TestLinks {

    private TestLinks() {
    }

    public static List<SectionPage.Action> forSection(String id) {
        List<SectionPage.Action> out = new ArrayList<>();
        switch (id) {
            case "display":
                out.add(new SectionPage.Action("Tes layar (warna penuh)",
                        ScreenTestActivity.class));
                out.add(new SectionPage.Action("Tes tombol",
                        ButtonTestActivity.class));
                break;
            case "sensor":
                out.add(new SectionPage.Action("Nilai sensor realtime",
                        SensorTestActivity.class));
                break;
            case "network":
                out.add(new SectionPage.Action("GPS & satelit realtime",
                        GnssTestActivity.class));
                break;
            case "battery":
                out.add(new SectionPage.Action("Grafik arus & daya",
                        BatteryChartActivity.class));
                out.add(new SectionPage.Action("Tes getar",
                        VibrateTestActivity.class));
                break;
            case "apps":
                out.add(new SectionPage.Action("Export ikon & APK",
                        AppExportActivity.class));
                break;
            case "device":
                out.add(new SectionPage.Action("Tes layar (warna penuh)",
                        ScreenTestActivity.class));
                out.add(new SectionPage.Action("Tes tombol",
                        ButtonTestActivity.class));
                out.add(new SectionPage.Action("Tes getar",
                        VibrateTestActivity.class));
                out.add(new SectionPage.Action("GPS & satelit realtime",
                        GnssTestActivity.class));
                break;
            case "storage":
                out.add(new SectionPage.Action("Analisis penyimpanan",
                        StorageAnalyzeActivity.class));
                out.add(new SectionPage.Action("Export ikon & APK",
                        AppExportActivity.class));
                break;
            default:
                break;
        }
        return out;
    }
}
