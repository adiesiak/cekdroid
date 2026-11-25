package ai.cekdroid.test;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Compass: heading, plus pitch and roll.
 *
 * The rotation vector is preferred because the platform fuses accelerometer and
 * magnetometer into it and reports accuracy; a device without one falls back to
 * the two raw sensors with SensorManager.getRotationMatrix. Heading is
 * normalised to 0..360 and mapped to a compass point, which is the number a
 * person actually reads off a compass.
 */
public class CompassActivity extends Activity implements SensorEventListener {

    private static final String[] POINTS = {
            "U", "UTL", "TL", "TTL", "T", "TTG", "TG", "STG",
            "S", "SBD", "BD", "BBD", "B", "BBL", "BL", "UBL",
    };

    private SensorManager sm;
    private Sensor rotationSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];
    private final float[] accel = new float[3];
    private final float[] mag = new float[3];
    private boolean haveAccel;
    private boolean haveMag;

    private TextView heading;
    private TextView direction;
    private TextView detail;
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
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        status = new TextView(this);
        status.setTextSize(12f);
        status.setTextColor(Palette.dim(this));
        status.setPadding(0, Cards.dp(this, 6), 0, Cards.dp(this, 12));
        root.addView(status);

        heading = new TextView(this);
        heading.setTextSize(58f);
        heading.setTextColor(Palette.text(this));
        heading.setText("—");
        root.addView(heading);

        direction = new TextView(this);
        direction.setTextSize(22f);
        direction.setTextColor(Palette.brandBright());
        direction.setText("—");
        root.addView(direction);

        detail = new TextView(this);
        detail.setTextSize(12f);
        detail.setTextColor(Palette.faint(this));
        detail.setPadding(0, Cards.dp(this, 14), 0, 0);
        detail.setText("Kemiringan —  ·  Guling —");
        root.addView(detail);

        Chrome.addBar(this, root, "Kompas");
        setContentView(root);

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm != null) {
            rotationSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        status.setText(rotationSensor == null
                ? "Tidak ada sensor kompas yang tersedia."
                : "Putar perangkat untuk melihat arah.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sm == null) {
            return;
        }
        if (rotationSensor != null) {
            sm.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
            return;
        }
        // Fallback: the two raw sensors feed getRotationMatrix below.
        Sensor a = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor m = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (a != null && m != null) {
            sm.registerListener(this, a, SensorManager.SENSOR_DELAY_UI);
            sm.registerListener(this, m, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
            publish(orientation[0], orientation[1], orientation[2]);
            return;
        }
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accel, 0, Math.min(3, event.values.length));
            haveAccel = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, mag, 0, Math.min(3, event.values.length));
            haveMag = true;
        }
        if (haveAccel && haveMag) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, accel, mag)) {
                SensorManager.getOrientation(rotationMatrix, orientation);
                publish(orientation[0], orientation[1], orientation[2]);
            }
        }
    }

    private void publish(float azimuth, float pitch, float roll) {
        double degrees = (Math.toDegrees(azimuth) + 360.0) % 360.0;
        final String headingText = String.format(Locale.US, "%.0f°", degrees);
        final String point = pointFor(degrees);
        final String details = String.format(Locale.US,
                "Kemiringan %.0f°  ·  Guling %.0f°",
                Math.toDegrees(pitch), Math.toDegrees(roll));

        runOnUiThread(() -> {
            heading.setText(headingText);
            direction.setText(point);
            detail.setText(details);
        });
    }

    private static String pointFor(double degrees) {
        int index = (int) Math.round(degrees / 22.5) % 16;
        return POINTS[index];
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
        // accuracy is not shown: the compass point is the answer a reader wants
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sm != null) {
            sm.unregisterListener(this);
        }
        haveAccel = false;
        haveMag = false;
    }
}
