package ai.cekdroid.collect.sections;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;
import ai.cekdroid.collect.SectionBase;

/**
 * Battery status, health, and wear.
 *
 * Wear needs charge_full / charge_full_design, which many MediaTek kernels do
 * not expose at all; when absent the value is null with a warning rather than
 * an invented percentage.
 */
public class BatterySection implements Section {

    @Override
    public String id() {
        return "battery";
    }

    @Override
    public String title() {
        return "Baterai";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        int levelNow = -1;
        try {
            Intent intent = ctx.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent == null) {
                out.warn("broadcast baterai tidak tersedia");
                return;
            }
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            int volt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            String tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);

            levelNow = (level >= 0 && scale > 0)
                    ? Math.round(100f * level / scale) : -1;
            out.put("level", levelNow >= 0 ? levelNow : null);
            out.put("status", statusName(status));
            out.put("health", healthName(health));
            out.put("temperature_c", temp > 0 ? SectionBase.round1(temp / 10.0) : null);
            out.put("voltage_mv", volt > 0 ? volt : null);
            out.put("technology", SectionBase.s(tech));
            out.put("ac_powered", (plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0);
            out.put("usb_powered", (plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0);
            out.put("wireless_powered",
                    (plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0);

            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                out.put("charging", bm.isCharging());
                long cycle = readLongProperty(bm, "cycle_count");
                long full = readLongProperty(bm, "charge_full");
                long design = readLongProperty(bm, "charge_full_design");
                long counter = readLongProperty(bm, "charge_counter");
                long current = readLongProperty(bm, "current_now");

                out.put("cycle_count", cycle > 0 ? cycle : null);
                out.put("charge_full_uah", full > 0 ? full : null);
                out.put("charge_full_design_uah", design > 0 ? design : null);
                out.put("charge_counter_uah", counter > 0 ? counter : null);
                out.put("current_ua", current != 0 ? current : null);

                if (full > 0 && design > 0) {
                    out.put("wear_pct", SectionBase.round1(100.0 * full / design));
                } else {
                    out.put("wear_pct", null);
                    out.warn("kapasitas desain tidak diekspos kernel; wear tidak tersedia");
                }
            }
        } catch (Throwable t) {
            out.warn("baca baterai gagal: " + t.getMessage());
        }

        List<LinkedHashMap<String, Object>> supplies = powerSupplies();
        if (supplies.isEmpty()) {
            out.warn("/sys/class/power_supply tidak terbaca (SELinux membatasi app "
                    + "biasa di banyak ROM); kapasitas hanya dari BatteryManager");
        }
        out.rows("power_supplies", supplies);

        putCapacityDetail(supplies, out, levelNow, ctx);
    }

    /**
     * Design capacity, estimated full charge, and the charging ceiling.
     *
     * DevInfo states all three in mAh and mV. On most ROMs /sys/class/power_supply
     * is closed to an ordinary app, so this falls back to what BatteryManager
     * exposes: BATTERY_PROPERTY_CHARGE_COUNTER is the charge in µAh, and while
     * the pack is full that value *is* the estimated full-charge capacity. That
     * is also why DevInfo labels it "learned from charging sessions" — it is
     * only meaningful near 100%.
     *
     * A value that cannot be derived stays null with a reason rather than zero.
     */
    private void putCapacityDetail(List<LinkedHashMap<String, Object>> supplies,
                                   Recorder out, int levelNow, Context ctx) {
        Long designUah = null;
        Long fullUah = null;
        Long chargeNowUah = null;

        for (LinkedHashMap<String, Object> row : supplies) {
            Object type = row.get("type");
            if (type == null || !"Battery".equalsIgnoreCase(String.valueOf(type))) {
                continue;
            }
            Long design = toLong(row.get("charge_full_design"));
            Long full = toLong(row.get("charge_full"));
            Long now = toLong(row.get("charge_counter"));
            if (design != null && design > 0) {
                designUah = design;
            }
            if (full != null && full > 0) {
                fullUah = full;
            }
            if (now != null && now > 0) {
                chargeNowUah = now;
            }
        }

        if (chargeNowUah == null) {
            chargeNowUah = readLongPropertyOrNull(ctx, "charge_counter");
        }

        // BatteryManager's charge counter is the only capacity signal an app
        // gets without sysfs, and it equals the pack's full capacity only when
        // the battery is (nearly) full.
        if (fullUah == null && chargeNowUah != null && levelNow >= 99) {
            fullUah = chargeNowUah;
        }

        out.put("design_capacity_mah", designUah == null ? null
                : Math.round(designUah / 1000.0));
        out.put("estimated_full_charge_mah", fullUah == null ? null
                : Math.round(fullUah / 1000.0));
        if (designUah == null && fullUah == null) {
            out.warn("kapasitas desain tidak diekspos kernel; app biasa hanya "
                    + "mendapat charge counter via BatteryManager");
        }

        if (designUah != null && fullUah != null && designUah > 0) {
            out.put("health_pct", SectionBase.round1(100.0 * fullUah / designUah));
        } else {
            out.put("health_pct", null);
        }

        // Charging ceiling, read straight from the charger node.
        Long currentMaxUa = firstNumber(supplies, "current_max");
        Long voltageMaxUv = firstNumber(supplies, "voltage_max");
        out.put("max_charging_current_ma", currentMaxUa == null ? null
                : Math.round(currentMaxUa / 1000.0));
        out.put("max_charging_voltage_mv", voltageMaxUv == null ? null
                : Math.round(voltageMaxUv / 1000.0));
        out.put("charge_now_mah", chargeNowUah == null ? null
                : Math.round(chargeNowUah / 1000.0));

        if (currentMaxUa == null) {
            out.warn("input pengisian maksimum hanya ada di /sys/class/power_supply, "
                    + "yang tidak terbaca untuk app biasa di ROM ini");
        }
    }

    /**
     * One BatteryManager long property by name, or null when unavailable.
     *
     * The public constants cover charge counter; reading by name keeps the
     * helper shared with the private ones and survives a vendor that omits a
     * property (getLongProperty returns Long.MIN_VALUE then).
     */
    private Long readLongPropertyOrNull(Context ctx, String name) {
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) {
            return null;
        }
        long value = readLongProperty(bm, name);
        return value <= 0 ? null : value;
    }

    /** First non-zero value of {@code key} across the power-supply rows. */
    private static Long firstNumber(List<LinkedHashMap<String, Object>> supplies,
                                    String key) {
        for (LinkedHashMap<String, Object> row : supplies) {
            Long value = toLong(row.get(key));
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * BatteryManager.getLongProperty needs an int constant that is not public
     * for cycle_count/charge_full, so resolve the field by name.
     */
    private long readLongProperty(BatteryManager bm, String name) {
        String[] candidates = {
                "BATTERY_PROPERTY_" + name.toUpperCase(java.util.Locale.US),
                name.toUpperCase(java.util.Locale.US),
        };
        for (String candidate : candidates) {
            try {
                java.lang.reflect.Field field = BatteryManager.class.getField(candidate);
                int key = field.getInt(null);
                long value = bm.getLongProperty(key);
                if (value != Long.MIN_VALUE) {
                    return value;
                }
            } catch (Throwable ignored) {
                // try the next name
            }
        }
        return -1;
    }

    private List<LinkedHashMap<String, Object>> powerSupplies() {
        List<LinkedHashMap<String, Object>> out = new ArrayList<>();
        File root = new File("/sys/class/power_supply");
        File[] dirs = root.listFiles();
        if (dirs == null) {
            return out;
        }
        java.util.Arrays.sort(dirs);
        for (File dir : dirs) {
            if (!dir.isDirectory()) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("name", dir.getName());
            row.put("type", SectionBase.s(SectionBase.readFirstLine(new File(dir, "type"))));
            row.put("capacity", num(SectionBase.readFirstLine(new File(dir, "capacity"))));
            row.put("status", SectionBase.s(SectionBase.readFirstLine(new File(dir, "status"))));
            row.put("health", SectionBase.s(SectionBase.readFirstLine(new File(dir, "health"))));
            row.put("cycle_count", num(SectionBase.readFirstLine(new File(dir, "cycle_count"))));
            row.put("charge_full", num(SectionBase.readFirstLine(new File(dir, "charge_full"))));
            row.put("charge_full_design",
                    num(SectionBase.readFirstLine(new File(dir, "charge_full_design"))));
            row.put("charge_counter",
                    num(SectionBase.readFirstLine(new File(dir, "charge_counter"))));
            // The charger node carries the negotiated ceiling; reading it here
            // keeps the per-row table and the summary in one pass.
            row.put("current_max", num(SectionBase.readFirstLine(new File(dir, "current_max"))));
            row.put("voltage_max", num(SectionBase.readFirstLine(new File(dir, "voltage_max"))));
            row.put("temp_c", num(SectionBase.readFirstLine(new File(dir, "temp"))));
            out.add(row);
        }
        return out;
    }

    private static Long num(String v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String statusName(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "not_charging";
            default: return null;
        }
    }

    private static String healthName(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "over_voltage";
            case BatteryManager.BATTERY_HEALTH_COLD: return "cold";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "failure";
            default: return null;
        }
    }
}
