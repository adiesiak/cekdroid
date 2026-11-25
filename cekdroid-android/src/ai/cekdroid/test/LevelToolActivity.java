package ai.cekdroid.test;

import android.app.Activity;
import android.content.Intent;
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
 * Bubble level: which way the phone is tilted, in degrees.
 *
 * Reads the gravity sensor when the device has one and falls back to the
 * accelerometer, because gravity is derived from it anyway and a phone without
 * a gravity sensor still has an accelerometer. Pitch and roll are computed the
 * standard way: the angle of the gravity vector against each axis.
 *
 * "Zero" stores the current reading as the reference, so a phone on a slightly
 * sloped table can still be used to compare two surfaces.
 */
public class LevelToolActivity extends Activity implements SensorEventListener {

    private SensorManager sm;
    private Sensor sensor;
    private TextView pitchView;
    private TextView rollView;
    private TextView status;
    private float zeroPitch;
    private float zeroRoll;
    private boolean hasZero;

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

        pitchView = bigValue("Sumbu X");
        rollView = bigValue("Sumbu Y");
        root.addView(label("Sumbu X"));
        root.addView(pitchView);
        root.addView(label("Sumbu Y"));
        root.addView(rollView);

        TextView reset = Cards.actionButton(this, "Nolkan");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Cards.dp(this, 14);
        reset.setLayoutParams(lp);
        reset.setOnClickListener(v -> {
            zeroPitch = lastPitch;
            zeroRoll = lastRoll;
            hasZero = true;
            status.setText("Referensi disimpan. Kemiringan diukur dari sini.");
        });
        root.addView(reset);

        Chrome.addBar(this, root, "Waterpas gelembung");
        setContentView(root);

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (sensor == null && sm != null) {
            sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        status.setText(sensor == null
                ? "Tidak ada sensor gravitasi atau akselerometer yang tersedia."
                : "Sensor: " + sensor.getName());
    }

    private float lastPitch;
    private float lastRoll;

    private TextView bigValue(String ignored) {
        TextView t = new TextView(this);
        t.setTextSize(40f);
        t.setTextColor(Palette.text(this));
        t.setPadding(0, 0, 0, Cards.dp(this, 6));
        t.setText("—");
        return t;
    }

    private TextView label(String text) {
        TextView t = Cards.hint(this, text);
        t.setTextColor(Palette.dim(this));
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sm != null && sensor != null) {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values.length < 3) {
            return;
        }
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        // Pitch and roll from the gravity vector: atan2 of the horizontal
        // components against the vertical one, converted to degrees.
        double pitch = Math.toDegrees(Math.atan2(-x, Math.sqrt(y * y + z * z)));
        double roll = Math.toDegrees(Math.atan2(y, Math.sqrt(x * x + z * z)));

        lastPitch = (float) pitch;
        lastRoll = (float) roll;

        final float shownPitch = hasZero ? (float) pitch - zeroPitch : (float) pitch;
        final float shownRoll = hasZero ? (float) roll - zeroRoll : (float) roll;
        final String textX = String.format(Locale.US, "%.1f°", shownPitch);
        final String textY = String.format(Locale.US, "%.1f°", shownRoll);
        final boolean level = Math.abs(shownPitch) < 1.5f && Math.abs(shownRoll) < 1.5f;

        runOnUiThread(() -> {
            pitchView.setText(textX);
            rollView.setText(textY);
            int color = level ? Palette.OK : Palette.text(this);
            pitchView.setTextColor(color);
            rollView.setTextColor(color);
        });
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
        // level accuracy is not shown; the reading itself is what matters
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sm != null) {
            sm.unregisterListener(this);
        }
    }
}
