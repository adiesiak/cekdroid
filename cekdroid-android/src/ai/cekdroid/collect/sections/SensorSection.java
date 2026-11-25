package ai.cekdroid.collect.sections;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;

/**
 * Every sensor the platform advertises, with its reported capabilities.
 *
 * This lists sensors; it does not stream live values (that needs a registered
 * listener and belongs to the interactive test screen).
 */
public class SensorSection implements Section {

    @Override
    public String id() {
        return "sensor";
    }

    @Override
    public String title() {
        return "Sensor";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) {
            out.warn("SensorManager tidak tersedia");
            return;
        }
        List<Sensor> all = sm.getSensorList(Sensor.TYPE_ALL);
        out.put("count", all.size());

        List<LinkedHashMap<String, Object>> rows = new ArrayList<>();
        for (Sensor s : all) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("name", s.getName());
            row.put("vendor", s.getVendor());
            row.put("type", typeName(s.getType()));
            row.put("type_id", s.getType());
            row.put("version", s.getVersion());
            row.put("max_range", (double) s.getMaximumRange());
            row.put("resolution", (double) s.getResolution());
            row.put("power_ma", (double) s.getPower());
            row.put("min_delay_us", s.getMinDelay());
            row.put("wakeup", s.isWakeUpSensor());
            row.put("string_type", s.getStringType());
            rows.add(row);
        }
        out.rows("sensors", rows);
    }

    private static String typeName(int type) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER: return "accelerometer";
            case Sensor.TYPE_MAGNETIC_FIELD: return "magnetic_field";
            case Sensor.TYPE_GYROSCOPE: return "gyroscope";
            case Sensor.TYPE_LIGHT: return "light";
            case Sensor.TYPE_PRESSURE: return "pressure";
            case Sensor.TYPE_PROXIMITY: return "proximity";
            case Sensor.TYPE_GRAVITY: return "gravity";
            case Sensor.TYPE_LINEAR_ACCELERATION: return "linear_acceleration";
            case Sensor.TYPE_ROTATION_VECTOR: return "rotation_vector";
            case Sensor.TYPE_RELATIVE_HUMIDITY: return "relative_humidity";
            case Sensor.TYPE_AMBIENT_TEMPERATURE: return "ambient_temperature";
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED: return "magnetic_field_uncalibrated";
            case Sensor.TYPE_GAME_ROTATION_VECTOR: return "game_rotation_vector";
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED: return "gyroscope_uncalibrated";
            case Sensor.TYPE_STEP_DETECTOR: return "step_detector";
            case Sensor.TYPE_STEP_COUNTER: return "step_counter";
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR: return "geomagnetic_rotation_vector";
            case Sensor.TYPE_HEART_RATE: return "heart_rate";
            case Sensor.TYPE_POSE_6DOF: return "pose_6dof";
            case Sensor.TYPE_STATIONARY_DETECT: return "stationary_detect";
            case Sensor.TYPE_MOTION_DETECT: return "motion_detect";
            case Sensor.TYPE_HEART_BEAT: return "heart_beat";
            case Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT: return "low_latency_offbody_detect";
            default: return "type_" + type;
        }
    }
}
