package ai.cekdroid.collect.sections;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;
import ai.cekdroid.collect.SectionBase;

/**
 * Identifiers, with an honest account of what is blocked.
 *
 * ANDROID_ID is per-app-signing-key, not a hardware ID, and resets on factory
 * reset. Hardware serial is "unknown" for ordinary apps since Android 10.
 * Nothing here can be turned into a tracking identifier, and the UI says so.
 */
public class IdSection implements Section {

    @Override
    public String id() {
        return "id";
    }

    @Override
    public String title() {
        return "ID";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        String androidId = null;
        try {
            androidId = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
        } catch (Throwable t) {
            out.warn("ANDROID_ID: " + t.getMessage());
        }
        out.put("android_id", SectionBase.s(androidId));

        out.put("fingerprint", Build.FINGERPRINT);
        out.put("build_id", Build.ID);
        out.put("display_id", Build.DISPLAY);
        out.put("device", Build.DEVICE);
        out.put("board", Build.BOARD);
        out.put("hardware", Build.HARDWARE);
        out.put("bootloader", Build.BOOTLOADER);
        out.put("radio", SectionBase.s(Build.getRadioVersion()));

        String serial = null;
        try {
            serial = Build.getSerial();
        } catch (SecurityException e) {
            out.warn("serial perangkat butuh izin privileged; nilainya 'unknown'");
        } catch (Throwable t) {
            out.warn("serial: " + t.getMessage());
        }
        out.put("serial", SectionBase.s(serial));
        if (serial != null && "unknown".equalsIgnoreCase(serial.trim())) {
            out.warn("serial perangkat = 'unknown' (dibatasi Android 10+)");
        }

        out.put("advertising_id", null);
        out.warn("Advertising ID tidak tersedia tanpa Play Services");

        out.put("imei", null);
        out.warn("IMEI tidak tersedia untuk app biasa");
    }
}
