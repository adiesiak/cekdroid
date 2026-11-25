package ai.cekdroid.test;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Live sensor values, updated on the UI thread at the sensor's own rate.
 *
 * This is the only screen that registers listeners, and every listener is
 * unregistered in onPause so the sensors stop drawing power as soon as the
 * screen is not visible.
 */
public class SensorTestActivity extends Activity implements SensorEventListener {

    private SensorManager sm;
    private final Map<Integer, TextView> valueViews = new LinkedHashMap<>();
    private final Map<Integer, Integer> samples = new LinkedHashMap<>();
    private LinearLayout rows;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Palette.configure(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Palette.bg(this));

        status = new TextView(this);
        status.setTextSize(12f);
        status.setTextColor(Palette.dim(this));
        status.setPadding(0, Cards.dp(this, 6), 0, Cards.dp(this, 12));
        root.addView(status);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        int registered = 0;
        if (sm != null) {
            for (Sensor s : sm.getSensorList(Sensor.TYPE_ALL)) {
                if (s.getType() == Sensor.TYPE_PROXIMITY
                        || s.getType() == Sensor.TYPE_LIGHT
                        || s.getType() == Sensor.TYPE_ACCELEROMETER
                        || s.getType() == Sensor.TYPE_GYROSCOPE
                        || s.getType() == Sensor.TYPE_MAGNETIC_FIELD
                        || s.getType() == Sensor.TYPE_GRAVITY
                        || s.getType() == Sensor.TYPE_ROTATION_VECTOR
                        || s.getType() == Sensor.TYPE_PRESSURE
                        || s.getType() == Sensor.TYPE_RELATIVE_HUMIDITY
                        || s.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE
                        || s.getType() == Sensor.TYPE_STEP_COUNTER
                        || s.getType() == Sensor.TYPE_HEART_RATE) {
                    addSensor(s);
                    registered++;
                }
            }
        }
        status.setText(registered == 0
                ? "Tidak ada sensor yang bisa didaftarkan."
                : registered + " sensor aktif. Nilai diperbarui otomatis.");

        ScrollView scroll = new ScrollView(this);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

                Chrome.addBar(this, root, "Sensor realtime");
        setContentView(root);
    }

    private void addSensor(Sensor s) {
        LinearLayout row = Cards.row(this, s.getName(), "menunggu…");
        TextView value = (TextView) row.getChildAt(1);
        valueViews.put(s.getType(), value);
        samples.put(s.getType(), 0);
        rows.addView(row);
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final TextView view = valueViews.get(event.sensor.getType());
        if (view == null) {
            return;
        }
        final String text = format(event.values);
        postValue(view, text);
    }

    /**
     * Lambda rather than an anonymous Runnable: d8 8.2.2 in build-tools 34.0.0
     * crashes on anonymous inner classes with an internal NullPointerException
     * and aborts the build.
     */
    void postValue(final TextView view, final String text) {
        runOnUiThread(() -> {
            view.setText(text);
            view.setTextColor(Palette.OK);
        });
    }

    private static String format(float[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length && i < 3; i++) {
            if (i > 0) {
                sb.append("  ");
            }
            sb.append(String.format(Locale.US, "%.3f", values[i]));
        }
        return sb.toString();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // nothing to show; accuracy changes are not user-meaningful here
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sm != null) {
            sm.unregisterListener(this);
        }
    }
}
