package ai.cekdroid.test;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Microphone test: a live input level meter.
 *
 * Audio is analysed locally and never written or sent anywhere; the peak level
 * is the only thing that leaves the read buffer. The recorder runs on its own
 * thread and stops in onPause, because an always-on microphone would keep the
 * privacy indicator lit and drain the battery.
 */
public class MicrophoneTestActivity extends Activity {

    private static final int SAMPLE_RATE = 44100;

    private TextView status;
    private ProgressBar level;
    private TextView levelText;
    private volatile boolean running;
    private Thread worker;

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

        level = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        level.setMax(100);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(this, 14));
        lp.bottomMargin = Cards.dp(this, 8);
        level.setLayoutParams(lp);
        root.addView(level);

        levelText = new TextView(this);
        levelText.setTextSize(12f);
        levelText.setTextColor(Palette.text(this));
        levelText.setPadding(0, 0, 0, Cards.dp(this, 12));
        root.addView(levelText);

        Chrome.addBar(this, root, "Mikrofon");
        setContentView(root);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            status.setText("Izin mikrofon diperlukan untuk menguji input audio.");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7101);
        } else {
            start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != 7101) {
            return;
        }
        boolean granted = results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED;
        status.setText(granted
                ? "Bicaralah di dekat mikrofon."
                : "Izin mikrofon ditolak; input tidak bisa diuji.");
        if (granted) {
            start();
        }
    }

    private void start() {
        if (running) {
            return;
        }
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) {
            status.setText("Tidak dapat memulai input mikrofon.");
            return;
        }
        running = true;
        status.setText("Mendengarkan… bicaralah di dekat mikrofon.");

        worker = new Thread(() -> {
            AudioRecord record = null;
            try {
                record = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        minBuffer * 2);
                if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                    runOnUiThread(() -> status.setText("AudioRecord gagal diinisialisasi."));
                    return;
                }
                record.startRecording();
                short[] buffer = new short[1024];
                while (running) {
                    int read = record.read(buffer, 0, buffer.length);
                    if (read <= 0) {
                        continue;
                    }
                    int peak = 0;
                    for (int i = 0; i < read; i++) {
                        peak = Math.max(peak, Math.abs(buffer[i]));
                    }
                    // Level as a percentage of full scale, with the dBFS value
                    // beside it: "42%" alone does not tell a reader whether the
                    // mic is clipping or merely picking up room noise.
                    double dbfs = peak == 0 ? -90.0
                            : 20.0 * Math.log10(peak / (double) Short.MAX_VALUE);
                    int percent = (int) Math.round(100.0 * peak / Short.MAX_VALUE);
                    final int shown = percent;
                    final String text = shown + "%  ·  "
                            + String.format(java.util.Locale.US, "%.0f dBFS", dbfs);
                    runOnUiThread(() -> {
                        level.setProgress(shown);
                        levelText.setText(text);
                    });
                }
            } catch (Throwable t) {
                final String message = t.getMessage();
                runOnUiThread(() -> status.setText("Input gagal: " + message));
            } finally {
                if (record != null) {
                    try {
                        record.stop();
                    } catch (Throwable ignored) {
                        // stopping an uninitialised record throws
                    }
                    try {
                        record.release();
                    } catch (Throwable ignored) {
                        // releasing twice is harmless
                    }
                }
            }
        }, "mic-level");
        worker.start();
    }

    private void stopMeter() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMeter();
    }
}
