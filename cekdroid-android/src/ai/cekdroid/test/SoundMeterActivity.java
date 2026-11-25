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

import java.util.Locale;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Sound meter: the current, average and peak level in dBFS.
 *
 * dBFS is decibels relative to full scale — 0 dBFS is the loudest the ADC can
 * record, so every real reading is negative. It is not dB SPL: turning dBFS
 * into a sound-pressure reading needs a per-microphone calibration offset,
 * which no phone publishes, so this screen reports the honest unit rather than
 * a "decibel" number that would be wrong by an unknown amount.
 *
 * The recorder runs on its own thread and stops in onPause; audio is analysed
 * locally and never stored.
 */
public class SoundMeterActivity extends Activity {

    private static final int SAMPLE_RATE = 44100;

    private TextView current;
    private TextView stats;
    private TextView status;
    private ProgressBar bar;
    private volatile boolean running;
    private Thread worker;

    private double peakDbfs = -90;
    private double sumDbfs;
    private int samples;

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

        current = new TextView(this);
        current.setTextSize(48f);
        current.setTextColor(Palette.text(this));
        current.setText("—");
        root.addView(current);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(90);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(this, 14));
        blp.topMargin = Cards.dp(this, 8);
        bar.setLayoutParams(blp);
        root.addView(bar);

        stats = new TextView(this);
        stats.setTextSize(12f);
        stats.setTextColor(Palette.dim(this));
        stats.setPadding(0, Cards.dp(this, 14), 0, 0);
        stats.setText("Rata-rata —  ·  Puncak —");
        root.addView(stats);

        Chrome.addBar(this, root, "Deteksi desibel");
        setContentView(root);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            status.setText("Izin mikrofon diperlukan untuk mengukur level suara.");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7103);
        } else {
            start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != 7103) {
            return;
        }
        boolean granted = results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED;
        status.setText(granted ? "Mengukur… audio dianalisis lokal, tidak disimpan."
                : "Izin mikrofon ditolak; level tidak bisa diukur.");
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
            status.setText("Perangkat tidak bisa membuka input audio.");
            return;
        }
        running = true;
        peakDbfs = -90;
        sumDbfs = 0;
        samples = 0;

        worker = new Thread(() -> {
            AudioRecord record = null;
            try {
                record = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        minBuffer * 2);
                if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                    runOnUiThread(() -> status.setText("Input audio gagal dibuka."));
                    return;
                }
                record.startRecording();
                short[] buffer = new short[2048];
                while (running) {
                    int read = record.read(buffer, 0, buffer.length);
                    if (read <= 0) {
                        continue;
                    }
                    long sumSquares = 0;
                    int peak = 0;
                    for (int i = 0; i < read; i++) {
                        int sample = buffer[i];
                        sumSquares += (long) sample * sample;
                        peak = Math.max(peak, Math.abs(sample));
                    }
                    double rms = Math.sqrt(sumSquares / (double) read) / Short.MAX_VALUE;
                    double dbfs = rms <= 0 ? -90 : 20.0 * Math.log10(rms);
                    double peakNow = peak <= 0 ? -90
                            : 20.0 * Math.log10(peak / (double) Short.MAX_VALUE);

                    peakDbfs = Math.max(peakDbfs, peakNow);
                    sumDbfs += dbfs;
                    samples++;

                    final double shown = Math.max(-90, dbfs);
                    final double average = sumDbfs / Math.max(1, samples);
                    final double peakShown = peakDbfs;

                    runOnUiThread(() -> {
                        current.setText(String.format(Locale.US, "%.0f", shown));
                        bar.setProgress((int) Math.round(shown + 90));
                        stats.setText(String.format(Locale.US,
                                "Rata-rata %.0f  ·  Puncak %.0f", average, peakShown));
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
        }, "sound-meter");
        worker.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }
}
