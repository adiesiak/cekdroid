package ai.cekdroid.test;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * One-sensor live readout, used by the proximity and light tests.
 *
 * DevInfo gives each of these a row on its Tes tab, and each needs its own
 * listener; a shared class with a sensor type keeps the two screens identical
 * in behaviour and one file instead of two copies.
 */
public class SingleSensorTestActivity extends Activity implements SensorEventListener {

    /** Sensor type to read; set by the launcher before the activity starts. */
    public static final String EXTRA_TYPE = "sensor_type";
    public static final String EXTRA_TITLE = "title";

    private SensorManager sm;
    private Sensor sensor;
    private TextView big;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Palette.configure(this);

        int type = getIntent().getIntExtra(EXTRA_TYPE, Sensor.TYPE_PROXIMITY);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null) {
            title = "Sensor";
        }

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

        big = new TextView(this);
        big.setTextSize(44f);
        big.setTextColor(Palette.text(this));
        big.setPadding(0, Cards.dp(this, 18), 0, Cards.dp(this, 18));
        big.setText("—");
        root.addView(big);

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sm == null ? null : sm.getDefaultSensor(type);
        if (sensor == null) {
            status.setText("Sensor tidak tersedia di perangkat ini.");
        } else {
            status.setText(sensor.getName() + " · " + sensor.getVendor());
        }

        Chrome.addBar(this, root, title);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sm != null && sensor != null) {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final float value = event.values.length > 0 ? event.values[0] : 0f;

        // Proximity is a boolean in practice: "dekat" (near) or "jauh" (far).
        // Printing "0.000" for a near reading is technically true and useless,
        // so the sensor's own max range is used to decide which word applies.
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            boolean near = value < event.sensor.getMaximumRange();
            runOnUiThread(() -> {
                big.setText(near ? "Dekat" : "Jauh");
                big.setTextColor(near ? Palette.WARN : Palette.OK);
            });
            return;
        }

        final String text = String.format(Locale.US, "%.1f", value);
        runOnUiThread(() -> {
            big.setText(text);
            big.setTextColor(Palette.OK);
        });
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
        // accuracy is not user-meaningful on these two sensors
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sm != null) {
            sm.unregisterListener(this);
        }
    }
}
