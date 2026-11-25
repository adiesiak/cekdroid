package ai.cekdroid.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import ai.cekdroid.collect.Snapshot;
import ai.cekdroid.overlay.FloatingMonitorService;
import ai.cekdroid.test.AppAnalysisActivity;
import ai.cekdroid.test.AppExportActivity;
import ai.cekdroid.test.BatteryChartActivity;
import ai.cekdroid.test.BluetoothTestActivity;
import ai.cekdroid.test.ButtonTestActivity;
import ai.cekdroid.test.ChargingTestActivity;
import ai.cekdroid.test.CompassActivity;
import ai.cekdroid.test.DataUsageActivity;
import ai.cekdroid.test.FingerprintTestActivity;
import ai.cekdroid.test.GnssTestActivity;
import ai.cekdroid.test.IpLookupActivity;
import ai.cekdroid.test.LevelToolActivity;
import ai.cekdroid.test.MicrophoneTestActivity;
import ai.cekdroid.test.ScreenTestActivity;
import ai.cekdroid.test.ScreenTimeActivity;
import ai.cekdroid.test.SensorTestActivity;
import ai.cekdroid.test.SingleSensorTestActivity;
import ai.cekdroid.test.SoundMeterActivity;
import ai.cekdroid.test.SpeakerTestActivity;
import ai.cekdroid.test.StorageAnalyzeActivity;
import ai.cekdroid.test.StorageCleanActivity;
import ai.cekdroid.test.TorchTestActivity;
import ai.cekdroid.test.VibrateTestActivity;
import ai.cekdroid.test.WidgetActivity;

/**
 * Content of the Tes and Alat tabs.
 *
 * TestLinks answers "which tool belongs to this data section" for the links that
 * sit inside a section page. This answers the different question DevInfo's Tests
 * and Tools tabs ask: "show me everything I can run", regardless of which section
 * the data lives in. Keeping both avoids making a reader know where a tool was
 * filed in order to find it.
 */
public final class Hubs {

    private Hubs() {
    }

    /** The Tes tab: hardware and sensor checks, in DevInfo's own order. */
    public static List<HubPage.Entry> tests(final Activity activity) {
        List<HubPage.Entry> out = new ArrayList<>();
        out.add(new HubPage.Entry("Analisis penyimpanan", "App mana yang menghabiskan ruang",
                "storage", Palette.TEAL,
                () -> launch(activity, StorageAnalyzeActivity.class)));
        out.add(new HubPage.Entry("Layar", "Pola warna penuh untuk cek piksel mati dan burn-in",
                "display", Palette.accent("display"),
                () -> launch(activity, ScreenTestActivity.class)));
        out.add(new HubPage.Entry("Senter", "Nyalakan dan matikan unit flash",
                "flash", Palette.WARN,
                () -> launch(activity, TorchTestActivity.class)));
        out.add(new HubPage.Entry("Pengeras suara", "Nada uji untuk speaker utama",
                "speaker", Palette.accent("sensor"),
                () -> launch(activity, SpeakerTestActivity.class)));
        out.add(new HubPage.Entry("Mikrofon", "Level input audio secara langsung",
                "mic", Palette.accent("network"),
                () -> launch(activity, MicrophoneTestActivity.class)));
        out.add(new HubPage.Entry("Speaker telinga", "Nada uji untuk earpiece",
                "speaker", Palette.accent("codec"),
                () -> launch(activity, SpeakerTestActivity.class)));
        out.add(new HubPage.Entry("Proksimitas telinga", "Nilai sensor jarak secara langsung",
                "sensor", Palette.accent("sensor"),
                () -> launchSensor(activity, android.hardware.Sensor.TYPE_PROXIMITY,
                        "Proksimitas telinga")));
        out.add(new HubPage.Entry("Sensor cahaya", "Nilai lux secara langsung",
                "sensor", Palette.accent("display"),
                () -> launchSensor(activity, android.hardware.Sensor.TYPE_LIGHT,
                        "Sensor cahaya")));
        out.add(new HubPage.Entry("Getaran", "Motor getar: pendek, panjang, dan pola",
                "vibrate", Palette.accent("sensor"),
                () -> launch(activity, VibrateTestActivity.class)));
        out.add(new HubPage.Entry("Bluetooth", "Status adapter dan perangkat berpasangan",
                "bluetooth", Palette.accent("network"),
                () -> launch(activity, BluetoothTestActivity.class)));
        out.add(new HubPage.Entry("Tes sidik jari", "Status sensor dan sidik jari terdaftar",
                "device", Palette.accent("device"),
                () -> launch(activity, FingerprintTestActivity.class)));
        out.add(new HubPage.Entry("Tombol & volume", "Tes semua tombol fisik termasuk Volume",
                "grid", Palette.accent("cpu"),
                () -> launch(activity, ButtonTestActivity.class)));
        out.add(new HubPage.Entry("Sensor realtime", "Nilai setiap sensor yang bergerak",
                "sensor", Palette.accent("sensor"),
                () -> launch(activity, SensorTestActivity.class)));
        out.add(new HubPage.Entry("GPS & satelit", "Jumlah satelit, akurasi, dan posisi",
                "network", Palette.accent("network"),
                () -> launch(activity, GnssTestActivity.class)));
        out.add(new HubPage.Entry("Baterai realtime", "Grafik arus dan daya tiap detik",
                "battery", Palette.accent("battery"),
                () -> launch(activity, BatteryChartActivity.class)));
        out.add(new HubPage.Entry("Uji pengisian daya", "Ukur arus, daya, dan kestabilan charger",
                "battery", Palette.OK,
                () -> launch(activity, ChargingTestActivity.class)));
        return out;
    }

    /** Launch the shared one-sensor screen with the type it should read. */
    private static void launchSensor(Activity activity, int sensorType, String title) {
        Intent intent = new Intent(activity, SingleSensorTestActivity.class);
        intent.putExtra(SingleSensorTestActivity.EXTRA_TYPE, sensorType);
        intent.putExtra(SingleSensorTestActivity.EXTRA_TITLE, title);
        activity.startActivity(intent);
    }

    /** The Alat tab: actions, not measurements, grouped as DevInfo groups them. */
    public static List<HubPage.Entry> tools(final Activity activity) {
        List<HubPage.Entry> out = new ArrayList<>();

        // Analisis dan pembersihan
        out.add(new HubPage.Entry("Analisis aplikasi", "Ukuran APK, data, dan cache tiap app",
                "apps", Palette.accent("apps"),
                () -> launch(activity, AppAnalysisActivity.class)));
        out.add(new HubPage.Entry("Analisis penyimpanan", "App mana yang menghabiskan ruang",
                "storage", Palette.TEAL,
                () -> launch(activity, StorageAnalyzeActivity.class)));
        out.add(new HubPage.Entry("Pembersihan penyimpanan", "Hapus cache yang aman dihapus",
                "storage", Palette.accent("storage"),
                () -> launch(activity, StorageCleanActivity.class)));

        // Penggunaan data
        out.add(new HubPage.Entry("Penggunaan data", "Trafik per app dalam 30 hari",
                "network", Palette.accent("network"),
                () -> launch(activity, DataUsageActivity.class)));
        out.add(new HubPage.Entry("Waktu penggunaan layar", "Aktivitas dan pembukaan kunci",
                "monitor", Palette.accent("display"),
                () -> launch(activity, ScreenTimeActivity.class)));

        // Tampilan status
        out.add(new HubPage.Entry("Monitor", "Overlay ringkas di atas app lain",
                "floating", Palette.accent("system"),
                () -> toggleOverlay(activity)));
        out.add(new HubPage.Entry("Widget", "Widget terpasang dan ukurannya",
                "grid", Palette.accent("device"),
                () -> launch(activity, WidgetActivity.class)));
        out.add(new HubPage.Entry("Pencarian IP", "Alamat dan cakupan jaringan aktif",
                "network", Palette.INFO,
                () -> launch(activity, IpLookupActivity.class)));

        // Alat pengukuran
        out.add(new HubPage.Entry("Waterpas gelembung", "Kemiringan perangkat dalam derajat",
                "sensor", Palette.accent("sensor"),
                () -> launch(activity, LevelToolActivity.class)));
        out.add(new HubPage.Entry("Kompas", "Arah mata angin dan kemiringan",
                "location", Palette.accent("location"),
                () -> launch(activity, CompassActivity.class)));
        out.add(new HubPage.Entry("Deteksi desibel", "Level suara dalam dBFS",
                "mic", Palette.accent("battery"),
                () -> launch(activity, SoundMeterActivity.class)));

        // Export dan berbagi tetap di sini: keduanya tindakan, bukan pengukuran.
        out.add(new HubPage.Entry("Laporan untuk pembeli", "Satu halaman: skor, verdict, data utama",
                "share", Palette.accent("device"),
                () -> shareReport(activity)));
        out.add(new HubPage.Entry("Export ikon & APK", "Simpan ke folder app di penyimpanan",
                "download", Palette.accent("apps"),
                () -> launch(activity, AppExportActivity.class)));
        out.add(new HubPage.Entry("Bagikan laporan", "Kirim JSON lengkap ke app lain",
                "share", Palette.accent("device"),
                () -> shareFromCache(activity)));
        out.add(new HubPage.Entry("Lihat APK terpasang", "Daftar app pihak ketiga dengan ukuran",
                "apps", Palette.accent("apps"),
                () -> activity.startActivity(
                        ListActivity.intent(activity, "apps", "installed",
                                "App terpasang"))));
        return out;
    }

    private static void launch(Activity activity, Class<?> target) {
        activity.startActivity(new Intent(activity, target));
    }

    /**
     * Start the monitor from the Alat hub.
     *
     * The hub has no toggle state, so it always starts the notification form —
     * the one that needs no permission and is always stoppable from its own
     * "Matikan" action. Turning on the overlay is the app bar's job, where the
     * current state is visible.
     */
    private static void toggleOverlay(Activity activity) {
        FloatingMonitorService.startNotification(activity);
        Toast.makeText(activity,
                "Monitor aktif di notifikasi · tekan 'Matikan' di sana untuk berhenti.",
                Toast.LENGTH_LONG).show();
    }

    /**
     * Rebuild the one-page report from the cached snapshot and share it.
     *
     * Same reason as {@link #shareFromCache}: the hub is a launcher, so a tap
     * must not trigger a fresh device read. The cached JSON is parsed back into
     * a Snapshot, which is the same shape the live collector produced.
     */
    private static void shareReport(Activity activity) {
        String json = activity.getSharedPreferences("cekdroid", Context.MODE_PRIVATE)
                .getString("last_report", null);
        if (json == null) {
            Toast.makeText(activity, "Belum ada laporan tersimpan.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            boolean ok = ReportCard.share(activity, Snapshot.fromJson(json));
            if (!ok) {
                Toast.makeText(activity, "Gagal membuat laporan.",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            Toast.makeText(activity, "Laporan tidak bisa dibaca: " + t.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Share the cached report.
     *
     * Shares the last collected JSON from preferences rather than starting a new
     * device read: the hub is a launcher, and a tap on "Bagikan" should not
     * silently cost a full collection.
     */
    private static void shareFromCache(Activity activity) {
        String json = activity.getSharedPreferences("cekdroid", Context.MODE_PRIVATE)
                .getString("last_report", null);
        if (json == null) {
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/json");
        send.putExtra(Intent.EXTRA_TEXT, json);
        activity.startActivity(Intent.createChooser(send, "Bagikan laporan CekDroid"));
    }
}
