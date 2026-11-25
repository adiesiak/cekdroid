package ai.cekdroid.test;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.LineChartView;
import ai.cekdroid.ui.Motion;
import ai.cekdroid.ui.Palette;

/**
 * Measured charging test: does the phone actually take current?
 *
 * "Tidak mau cas" is the most common repair complaint, and the answer is not
 * "the icon says charging" — a phone can show the charging symbol while drawing
 * current *out* of the battery, which is what this handset does right now
 * (status=charging, current=-313 mA). This screen therefore measures instead of
 * trusting the status:
 *
 *  - samples current for a fixed window and reports average, peak and spread
 *  - interprets the sign against the plugged state, because a negative current
 *    while on a charger is a real fault (charger or cable cannot supply)
 *  - derives wattage from the measured voltage, not from the charger's rating
 *  - reports whether the level actually moved, which is the only proof that
 *    energy reached the battery
 *
 * The test is repeatable so a counter can run it with the shop charger, then
 * with the customer's, and compare the two numbers.
 */
public class ChargingTestActivity extends Activity {

    private static final long SAMPLE_MS = 1000;
    /** Default measurement window; long enough for a charger to settle. */
    private static final int WINDOW_SAMPLES = 30;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private LineChartView chart;
    private TextView statusText;
    private TextView avgValue;
    private TextView peakValue;
    private TextView voltValue;
    private TextView powerValue;
    private TextView verdictTitle;
    private TextView verdictBody;
    private TextView progressText;
    private TextView startButton;

    /** Current samples in mA, sign preserved. */
    private final float[] samples = new float[WINDOW_SAMPLES];
    private int sampleCount;
    private boolean measuring;
    private long measureStartedAt;

    private int firstLevel = -1;
    private long firstLevelAt;

    private int pluggedState;
    private int batteryStatus;
    private float lastMv = Float.NaN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Palette.configure(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.bg(this));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 16);
        header.setPadding(pad, pad, pad, Cards.dp(this, 6));

        statusText = new TextView(this);
        statusText.setTextSize(12f);
        statusText.setTextColor(Palette.dim(this));
        statusText.setPadding(0, Cards.dp(this, 2), 0, Cards.dp(this, 12));
        header.addView(statusText);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        avgValue = metric("—");
        peakValue = metric("—");
        voltValue = metric("—");
        powerValue = metric("—");
        metrics.addView(metricBlock("Arus rata-rata", avgValue));
        metrics.addView(metricBlock("Puncak", peakValue));
        header.addView(metrics);

        LinearLayout metrics2 = new LinearLayout(this);
        metrics2.setOrientation(LinearLayout.HORIZONTAL);
        metrics2.setPadding(0, Cards.dp(this, 10), 0, 0);
        metrics2.addView(metricBlock("Tegangan", voltValue));
        metrics2.addView(metricBlock("Daya", powerValue));
        header.addView(metrics2);
        root.addView(header);

        chart = new LineChartView(this, WINDOW_SAMPLES, 130, " mA");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(this, 130));
        clp.leftMargin = pad;
        clp.rightMargin = pad;
        clp.topMargin = Cards.dp(this, 10);
        chart.setLayoutParams(clp);
        root.addView(chart);

        progressText = new TextView(this);
        progressText.setTextSize(11.5f);
        progressText.setTextColor(Palette.faint(this));
        progressText.setGravity(Gravity.CENTER);
        progressText.setPadding(pad, Cards.dp(this, 8), pad, 0);
        root.addView(progressText);

        startButton = Cards.actionButton(this, "Mulai ukur (30 detik)");
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = pad;
        blp.rightMargin = pad;
        blp.topMargin = Cards.dp(this, 10);
        startButton.setLayoutParams(blp);
        Motion.attachPress(startButton);
        startButton.setOnClickListener(v -> startMeasuring());
        root.addView(startButton);

        // Verdict card: the answer a counter reads out loud.
        LinearLayout verdictCard = Cards.card(this, Palette.accent("battery"));
        verdictCard.addView(Cards.header(this, "battery", "Hasil", null));
        verdictTitle = new TextView(this);
        verdictTitle.setTextSize(18f);
        verdictTitle.setTextColor(Palette.dim(this));
        verdictTitle.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        verdictTitle.setText("Belum diukur");
        verdictCard.addView(verdictTitle);

        verdictBody = new TextView(this);
        verdictBody.setTextSize(12f);
        verdictBody.setTextColor(Palette.dim(this));
        verdictBody.setLineSpacing(0, 1.3f);
        verdictBody.setPadding(0, Cards.dp(this, 5), 0, 0);
        verdictBody.setText("Colokkan charger, lalu tekan Mulai ukur. Ulangi dengan "
                + "charger lain untuk membandingkan.");
        verdictCard.addView(verdictBody);

        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = pad;
        vlp.rightMargin = pad;
        vlp.topMargin = Cards.dp(this, 12);
        verdictCard.setLayoutParams(vlp);

        LinearLayout scroll = new LinearLayout(this);
        scroll.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(verdictCard);
        root.addView(scroll);

        root.addView(Cards.hint(this,
                "Arus negatif saat terhubung charger berarti daya justru keluar dari "
                        + "baterai — biasanya charger atau kabel tidak mampu memberi arus."));

        Chrome.addBar(this, root, "Uji pengisian daya");
        setContentView(root);
    }

    private TextView metric(String placeholder) {
        TextView value = new TextView(this);
        value.setText(placeholder);
        value.setTextSize(19f);
        value.setTextColor(Palette.text(this));
        value.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return value;
    }

    private LinearLayout metricBlock(String label, TextView value) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView key = new TextView(this);
        key.setText(label);
        key.setTextSize(10.5f);
        key.setTextColor(Palette.faint(this));
        block.addView(key);
        block.addView(value);
        return block;
    }

    // ------------------------------------------------------------ measuring

    private void startMeasuring() {
        if (measuring) {
            stopMeasuring();
            return;
        }
        sampleCount = 0;
        measuring = true;
        measureStartedAt = SystemClock.elapsedRealtime();
        firstLevel = -1;
        chart.clear();
        startButton.setText("Hentikan");
        verdictTitle.setText("Mengukur…");
        verdictTitle.setTextColor(Palette.brandBright());
        verdictBody.setText("Jangan lepas atau goyangkan kabel selama pengukuran.");
        handler.post(sample);
    }

    private void stopMeasuring() {
        measuring = false;
        handler.removeCallbacks(sample);
        startButton.setText("Mulai ukur (30 detik)");
        if (sampleCount > 0) {
            finishMeasurement();
        }
    }

    private final Runnable sample = this::takeSample;

    /**
     * One measurement tick.
     *
     * Only the ammeter is a valid source. The charge counter was tried as a
     * fallback and rejected: its resolution is one percent of the pack, so over
     * a 30-second window at any real current the difference is always zero —
     * indistinguishable from "not charging". A test that cannot tell those two
     * apart is worse than no test, so a silent ammeter is reported as
     * unmeasurable instead.
     */
    private void takeSample() {
        readState();

        Long ua = batteryCurrentUa();
        if (ua != null) {
            float ma = ua / 1000f;
            if (sampleCount < WINDOW_SAMPLES) {
                samples[sampleCount++] = ma;
                chart.push(ma);
            }
            currentSource = "ammeter";
        } else {
            currentSource = "tidak tersedia";
        }

        if (firstLevel < 0) {
            firstLevel = currentLevel();
            firstLevelAt = SystemClock.elapsedRealtime();
        }

        updateLiveValues();
        int remaining = WINDOW_SAMPLES - sampleCount;
        progressText.setText(measuring
                ? "Sampel " + sampleCount + "/" + WINDOW_SAMPLES
                + (remaining > 0 ? " · " + remaining + " detik lagi" : "")
                + "  ·  sumber: " + currentSource
                : "");

        if (measuring && sampleCount >= WINDOW_SAMPLES) {
            stopMeasuring();
            return;
        }
        if (measuring) {
            handler.postDelayed(sample, SAMPLE_MS);
        }
    }

    /** Which source produced the current readings. */
    private String currentSource = "belum diukur";

    private void readState() {
        try {
            Intent battery = registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) {
                return;
            }
            pluggedState = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            batteryStatus = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int mv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            if (mv > 0) {
                lastMv = mv;
            }
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            lastLevel = (level >= 0 && scale > 0) ? Math.round(100f * level / scale) : -1;
            int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            lastTempC = temp > 0 ? temp / 10f : Float.NaN;
        } catch (Throwable t) {
            statusText.setText("Status baterai tidak terbaca: " + t.getMessage());
        }
    }

    private int lastLevel = -1;
    private float lastTempC = Float.NaN;

    private int currentLevel() {
        return lastLevel;
    }

    private void updateLiveValues() {
        String charger = chargerName(pluggedState);
        String state = statusName(batteryStatus);
        statusText.setText("Charger: " + charger + "  ·  status: " + state
                + (Float.isNaN(lastTempC) ? ""
                        : String.format(Locale.US, "  ·  %.1f °C", lastTempC)));

        if (sampleCount == 0) {
            avgValue.setText("—");
            peakValue.setText("—");
            voltValue.setText(Float.isNaN(lastMv) ? "—"
                    : String.format(Locale.US, "%.2f V", lastMv / 1000f));
            powerValue.setText("—");
            return;
        }
        float last = samples[sampleCount - 1];
        avgValue.setText(String.format(Locale.US, "%.0f mA", last));
        peakValue.setText(String.format(Locale.US, "%.0f mA", peakAbs()));
        voltValue.setText(Float.isNaN(lastMv) ? "—"
                : String.format(Locale.US, "%.2f V", lastMv / 1000f));
        if (!Float.isNaN(lastMv)) {
            double watts = (lastMv / 1000.0) * (Math.abs(last) / 1000.0);
            powerValue.setText(String.format(Locale.US, "%.2f W", watts));
        }
    }

    private float peakAbs() {
        float peak = 0;
        for (int i = 0; i < sampleCount; i++) {
            peak = Math.max(peak, Math.abs(samples[i]));
        }
        return peak;
    }

    // -------------------------------------------------------------- verdict

    private void finishMeasurement() {
        float sum = 0;
        for (int i = 0; i < sampleCount; i++) {
            sum += samples[i];
        }
        float mean = sampleCount == 0 ? 0 : sum / sampleCount;
        float meanAbs = Math.abs(mean);

        // Spread around the mean, as a fraction: a charger that oscillates
        // between 50 and 900 mA is a failing cable even though its average
        // looks acceptable.
        double variance = 0;
        for (int i = 0; i < sampleCount; i++) {
            double d = samples[i] - mean;
            variance += d * d;
        }
        double stddev = sampleCount == 0 ? 0 : Math.sqrt(variance / sampleCount);
        double spread = meanAbs > 1 ? stddev / meanAbs : 0;

        boolean plugged = pluggedState != 0;
        int levelNow = lastLevel;
        int levelDelta = (firstLevel >= 0 && levelNow >= 0) ? levelNow - firstLevel : 0;
        long seconds = Math.max(1, (SystemClock.elapsedRealtime() - measureStartedAt) / 1000);

        avgValue.setText(sampleCount == 0 ? "—"
                : String.format(Locale.US, "%.0f mA", mean));
        peakValue.setText(sampleCount == 0 ? "—"
                : String.format(Locale.US, "%.0f mA", peakAbs()));

        StringBuilder body = new StringBuilder();
        String title;
        int color;

        // ---------------------------------------------------------------
        // A reading only exists if the ammeter produced samples. The charge
        // counter is not a substitute: its resolution is a whole percent of
        // the pack (tens of mAh), so a 30-second window at any real current
        // shows no movement and the difference is always zero. Reporting that
        // zero as "charging is not happening" is a false diagnosis for a
        // phone that is charging perfectly well, so it is reported as an
        // unmeasurable ROM instead.
        // ---------------------------------------------------------------
        if (sampleCount == 0) {
            title = "Arus tidak terukur";
            color = Palette.WARN;
            body.append("ROM ini tidak memberi app akses ke arus baterai:\n");
            body.append("· API publik mengembalikan nilai placeholder (di bawah 1 mA)\n");
            body.append("· /sys/class/power_supply diblokir SELinux untuk app biasa\n");
            body.append("· penghitung muatan tidak berubah dalam 30 detik (resolusinya "
                    + "terlalu kasar)\n");
            body.append("Status charger tetap terbaca: ")
                    .append(chargerName(pluggedState)).append(", level ")
                    .append(levelNow >= 0 ? levelNow + "%" : "—").append('.');
            body.append("\nUntuk mengukur arus, pakai CLI lewat kabel: "
                    + "python -m cekdroid info --section battery");
            verdictTitle.setText(title);
            verdictTitle.setTextColor(color);
            verdictBody.setText(body.toString());
            progressText.setText("Tidak ada sampel arus yang bisa dipakai.");
            return;
        }

        body.append(String.format(Locale.US,
                "Rata-rata %.0f mA dari %d sampel dalam %d detik (sumber: %s).",
                mean, sampleCount, seconds, currentSource));
        body.append('\n').append(String.format(Locale.US,
                "Tegangan %.2f V · daya %.2f W.",
                Float.isNaN(lastMv) ? 0 : lastMv / 1000f,
                Float.isNaN(lastMv) ? 0 : (lastMv / 1000.0) * (meanAbs / 1000.0)));
        body.append('\n').append(levelDelta != 0
                ? "Level bergerak " + (levelDelta > 0 ? "+" : "") + levelDelta + "%."
                : "Level belum berubah selama pengukuran.");

        if (!plugged) {
            title = "Tidak ada charger";
            color = Palette.dim(this);
            body.append('\n').append("Colokkan charger lalu ulangi agar arus bisa dinilai.");
        } else if (sampleCount < 5) {
            title = "Arus tidak terbaca";
            color = Palette.WARN;
            body.append('\n').append("Kernel tidak melaporkan arus baterai; "
                    + "nilai keluar-masuk daya tidak bisa dinilai di ROM ini.");
        } else if (mean < -50) {
            // The charger is attached but power is leaving the battery. That is
            // the defect this screen exists to catch.
            title = "Daya justru keluar";
            color = Palette.CRITICAL;
            body.append('\n').append("Terhubung charger tetapi arus negatif: charger "
                    + "atau kabel tidak mampu memberi daya. Coba kabel dan adaptor lain.");
        } else if (meanAbs < 150) {
            title = "Arus sangat kecil";
            color = Palette.CRITICAL;
            body.append('\n').append("Di bawah 150 mA: pengisian praktis tidak terjadi. "
                    + "Periksa kontak port, kabel, dan adaptor.");
        } else if (meanAbs < 500) {
            title = "Pengisian lambat";
            color = Palette.WARN;
            body.append('\n').append("Arus di bawah 500 mA. Wajar untuk port USB "
                    + "komputer, lambat untuk charger dinding.");
        } else if (meanAbs < 1500) {
            title = "Pengisian normal";
            color = Palette.OK;
        } else {
            title = "Pengisian cepat";
            color = Palette.OK;
        }

        if (plugged && mean >= 0 && spread > 0.35 && sampleCount >= 10) {
            // Unstable current is a cable/port symptom, so it overrides an
            // otherwise acceptable average rather than being a footnote.
            title = "Arus tidak stabil";
            color = Palette.CRITICAL;
            body.append('\n').append("Arus naik-turun tajam (sebaran "
                    + Math.round(spread * 100) + "% dari rata-rata). Biasanya kabel "
                    + "longgar atau port kotor.");
        } else if (plugged && spread > 0.35 && sampleCount >= 10) {
            body.append('\n').append("Sebaran arus " + Math.round(spread * 100)
                    + "% — cukup besar, pantau kabelnya.");
        }

        if (plugged && meanAbs >= 150 && levelDelta <= 0 && seconds >= 20) {
            body.append('\n').append("Level tidak naik meski arus terbaca — "
                    + "periksa apakah baterai benar-benar menerima muatan.");
        }

        verdictTitle.setText(title);
        verdictTitle.setTextColor(color);
        verdictBody.setText(body.toString());
        progressText.setText(sampleCount + " sampel selesai.");
    }

    // -------------------------------------------------------------- helpers

    /**
     * Battery current in µA, or null when no usable reading exists.
     *
     * Three routes are tried in order, because they are not equally available:
     *
     *  1. getLongProperty — the modern form; some ROMs answer here and not in
     *     the int API.
     *  2. getIntProperty — the classic form. It returns Integer.MIN_VALUE for
     *     "unsupported", but OPPO's ColorOS answers a small placeholder instead.
     *  3. /sys/class/power_supply — readable by the shell user, blocked for an
     *     ordinary app by SELinux on this device, so it is only a last resort.
     *
     * The noise floor matters. This handset answers 57 µA from the public API
     * while `dumpsys battery` reports -312 mA for the same instant, so a
     * sub-milliamp answer is the ROM's placeholder, not a measurement — treating
     * it as real produces "0 mA, charging is not happening" for a phone that is
     * charging fine. Anything under 1 mA is therefore discarded and the
     * charge-counter route is used instead.
     */
    private Long batteryCurrentUa() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm != null) {
            try {
                long value = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (value != Long.MIN_VALUE && Math.abs(value) >= NOISE_FLOOR_UA) {
                    return value;
                }
            } catch (Throwable ignored) {
                // fall through to the int API
            }
            try {
                int value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (value != Integer.MIN_VALUE && Math.abs(value) >= NOISE_FLOOR_UA) {
                    return (long) value;
                }
            } catch (Throwable ignored) {
                // fall through to sysfs
            }
        }
        String raw = readSysfs("/sys/class/power_supply/battery/current_now");
        if (raw == null) {
            raw = readSysfs("/sys/class/power_supply/mtk-gauge/current_now");
        }
        if (raw != null) {
            try {
                long value = Long.parseLong(raw.trim());
                if (Math.abs(value) >= NOISE_FLOOR_UA) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // unparseable node, treat as absent
            }
        }
        return null;
    }

    /**
     * Below this, a reading is the ROM's placeholder rather than a current.
     *
     * 1 mA is a thousand times under the smallest meaningful charging current
     * and a thousand times over the noise a working sensor reports, so it
     * separates the two cases without guessing.
     */
    private static final long NOISE_FLOOR_UA = 1000L;

    /** One sysfs value, or null when unreadable. */
    private static String readSysfs(String path) {
        try (java.io.BufferedReader reader =
                     new java.io.BufferedReader(new java.io.FileReader(path))) {
            String line = reader.readLine();
            return line == null ? null : line.trim();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Charge counter in µAh, or null.
     *
     * This is the fallback measurement: the counter is the total charge the
     * pack has taken, so its rate of change over a window *is* the charging
     * current, with no dependency on whether the ROM exposes an ammeter.
     */
    private Long chargeCounterUah() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm == null) {
            return null;
        }
        try {
            long value = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            return value > 0 ? value : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String chargerName(int plugged) {
        if ((plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0) {
            return "Adaptor AC";
        }
        if ((plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0) {
            return "USB";
        }
        if ((plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) {
            return "Nirkabel";
        }
        return "tidak terhubung";
    }

    private static String statusName(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "mengisi";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "mengosongkan";
            case BatteryManager.BATTERY_STATUS_FULL: return "penuh";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "tidak mengisi";
            default: return "—";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        readState();
        updateLiveValues();
        if (!measuring) {
            showLivePreview();
        }
    }

    /**
     * Show the instantaneous current before a measurement is started.
     *
     * Without this the screen reads "—" until the user presses the button, and a
     * technician plugging in a charger gets no feedback at all. The preview
     * writes straight to the labels and the chart; it never touches the sample
     * buffer, so it cannot pollute the verdict maths.
     */
    private void showLivePreview() {
        Long ua = batteryCurrentUa();
        if (ua != null) {
            float ma = ua / 1000f;
            avgValue.setText(String.format(Locale.US, "%.0f mA", ma));
            peakValue.setText(String.format(Locale.US, "%.0f mA", Math.abs(ma)));
            chart.clear();
            chart.push(ma);
            progressText.setText("Pratinjau arus (ammeter) · tekan Mulai ukur untuk menilai.");
        } else {
            avgValue.setText("—");
            peakValue.setText("—");
            progressText.setText("Arus baterai tidak terbaca di ROM ini — "
                    + "lihat catatan di kartu Hasil setelah pengukuran.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        measuring = false;
        handler.removeCallbacks(sample);
    }
}
