package ai.cekdroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import ai.cekdroid.collect.Collector;
import ai.cekdroid.collect.Json;

/**
 * Headless entry point so the CLI can pull a report without touching the UI:
 *   adb shell am start -n ai.cekdroid/.DumpActivity --es out /sdcard/pc.json
 * Writes the Snapshot JSON to the given path, then finishes.
 *
 * Synchronous on purpose: there is no UI to block and `am start` needs the file
 * written before the process may be killed.
 */
public class DumpActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String outPath = intent != null ? intent.getStringExtra("out") : null;
        if (outPath == null) {
            // Internal files dir is always readable via `adb shell run-as <pkg>
            // cat ...`, unlike scoped /sdcard on API 29+.
            outPath = new java.io.File(getFilesDir(), "cekdroid-report.json")
                    .getAbsolutePath();
        }
        final String target = outPath;

        String json = null;
        String error = null;
        try {
            json = new Json().snapshot(new Collector(this).collect(true)).toString();
        } catch (Throwable t) {
            error = t.getClass().getSimpleName() + ": " + t.getMessage();
        }

        if (json != null) {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(target)) {
                fos.write(json.getBytes("UTF-8"));
            } catch (Throwable t) {
                error = "tulis gagal: " + t.getMessage();
            }
        }

        setResult(RESULT_OK, new Intent().putExtra("path", target).putExtra("error", error));
        finish();
    }
}
