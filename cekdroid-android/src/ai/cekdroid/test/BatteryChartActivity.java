package ai.cekdroid.test;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
 * Live battery current and power.
 *
 * Android exposes no history, only the instantaneous value, so the chart is
 * built by sampling once a second into a ring buffer. Power is derived as
 * V x A because the platform reports current, not watts.
 */
public class BatteryChartActivity extends Activity {

    private static final long SAMPLE_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LineChartView chart;
    private TextView currentValue;
    private TextView powerValue;
    private TextView status;

    private boolean showingPower;
    private float lastMicroAmps = Float.NaN;
    private float lastMilliVolts = Float.NaN;

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
        header.setPadding(pad, pad, pad, Cards.dp(this, 10));

        // No title here: the action bar already shows "Baterai Realtime", and
        // repeating it stacked two identical headings.
        status = new TextView(this);
        status.setTextSize(12f);
        status.setTextColor(Palette.dim(this));
        status.setPadding(0, Cards.dp(this, 4), 0, Cards.dp(this, 12));
        header.addView(status);

        currentValue = metric("—");
        powerValue = metric("—");
        header.addView(buildMetricRow());
        root.addView(header);

        chart = new LineChartView(this, 120, 170, " W");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(this, 170));
        clp.leftMargin = pad;
        clp.rightMargin = pad;
        chart.setLayoutParams(clp);
        root.addView(chart);

        root.addView(toggleRow(pad));

        TextView hint = Cards.hint(this,
                "Garis diambil tiap detik. Daya dihitung dari tegangan x arus; "
                        + "arus negatif berarti baterai sedang mengalirkan daya.");
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = pad;
        hlp.rightMargin = pad;
        hlp.topMargin = Cards.dp(this, 10);
        hint.setLayoutParams(hlp);
        root.addView(hint);

                Chrome.addBar(this, root, "Baterai realtime");
        setContentView(root);
    }

    private LinearLayout buildMetricRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metricBlock("Arus", currentValue));
        row.addView(metricBlock("Daya", powerValue));
        return row;
    }

    /** A large value TextView for the metric row. */
    private TextView metric(String placeholder) {
        TextView value = new TextView(this);
        value.setText(placeholder);
        value.setTextSize(20f);
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
        key.setTextSize(11f);
        key.setTextColor(Palette.faint(this));
        block.addView(key);
        block.addView(value);
        return block;
    }

    private LinearLayout toggleRow(int pad) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(pad, Cards.dp(this, 12), pad, 0);

        TextView power = Cards.actionButton(this, "Daya");
        TextView current = Cards.actionButton(this, "Arus");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(Cards.dp(this, 8));
        power.setLayoutParams(lp);
        current.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        power.setOnClickListener(v -> {
            showingPower = true;
            chart.clear();
            status.setText("Menampilkan daya (W).");
        });
        current.setOnClickListener(v -> {
            showingPower = false;
            chart.clear();
            status.setText("Menampilkan arus (mA).");
        });

        Motion.attachPress(power);
        Motion.attachPress(current);
        row.addView(power);
        row.addView(current);
        return row;
    }

    private final Runnable sample = this::takeSample;

    private void takeSample() {
        Intent battery = registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int microAmps = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (bm != null) {
                long ua = batteryCurrent(bm);
                if (ua != Long.MIN_VALUE) {
                    lastMicroAmps = ua;
                }
            }
            if (microAmps > 0) {
                lastMilliVolts = microAmps;
            }
        }

        if (!Float.isNaN(lastMicroAmps)) {
            float microAmps = Math.abs(lastMicroAmps);
            // A reported 0 (or a value below the sensor's noise floor) is not a
            // reading: this ROM answers 0 from dumpsys battery and 1 uA from
            // sysfs. Saying "0 mA" would look like a bug in the app.
            boolean usable = microAmps > 1000f;

            if (!usable) {
                currentValue.setText("—");
                powerValue.setText("—");
                status.setText("Arus baterai tidak dilaporkan ROM ini "
                        + "(nilai dari sistem selalu 0).");
                handler.postDelayed(sample, SAMPLE_MS);
                return;
            }

            float milliamps = microAmps / 1000f;
            float sign = lastMicroAmps < 0 ? -1f : 1f;
            currentValue.setText(String.format(Locale.US, "%.0f mA", sign * milliamps));

            if (!Float.isNaN(lastMilliVolts)) {
                float watts = (lastMilliVolts / 1000f) * (lastMicroAmps / 1_000_000f);
                powerValue.setText(String.format(Locale.US, "%.2f W", Math.abs(watts)));
                chart.push(showingPower ? Math.abs(watts) : milliamps);
            }
        } else {
            status.setText("Arus baterai tidak dilaporkan kernel ini.");
        }

        handler.postDelayed(sample, SAMPLE_MS);
    }

    /** CURRENT_NOW (int constant 2) through the public int-based API. */
    private static long batteryCurrent(BatteryManager bm) {
        try {
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        } catch (Throwable t) {
            return Long.MIN_VALUE;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        chart.clear();
        handler.post(sample);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(sample);
    }
}
