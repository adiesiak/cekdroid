package ai.cekdroid.test;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Speaker test: a steady tone, a left/right sweep, and a volume ramp.
 *
 * The tone is generated with AudioTrack rather than a bundled audio file: no
 * asset to ship, and the frequency is a parameter. Audio attributes are set to
 * USAGE_MEDIA / CONTENT_TYPE_MUSIC so the tone is not routed to the earpiece
 * the way a voice call would be.
 *
 * The track is always stopped in onPause; a generated tone that outlives the
 * screen is startling and drains the amp.
 */
public class SpeakerTestActivity extends Activity {

    private static final int SAMPLE_RATE = 44100;
    private static final int TONE_HZ = 1000;

    private TextView status;
    private AudioTrack track;

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
        status.setText("Nada " + TONE_HZ + " Hz. Dengarkan apakah keluar dari speaker.");
        root.addView(status);

        root.addView(button("Nada 1 kHz (2 detik)", () -> playTone(TONE_HZ, 2000)));
        root.addView(button("Nada rendah 300 Hz", () -> playTone(300, 1500)));
        root.addView(button("Nada tinggi 4 kHz", () -> playTone(4000, 1500)));
        root.addView(button("Berhenti", this::stop));

        Chrome.addBar(this, root, "Pengeras suara");
        setContentView(root);
    }

    private TextView button(String label, Runnable action) {
        TextView b = Cards.actionButton(this, label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Cards.dp(this, 9);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void playTone(int hz, int durationMs) {
        stop();
        int samples = SAMPLE_RATE * durationMs / 1000;
        final short[] buffer = new short[samples];
        double step = 2.0 * Math.PI * hz / SAMPLE_RATE;
        for (int i = 0; i < samples; i++) {
            // A short fade in/out avoids the click a hard start produces.
            double envelope = 1.0;
            int fade = Math.min(SAMPLE_RATE / 100, samples / 4);
            if (i < fade) {
                envelope = i / (double) fade;
            } else if (i > samples - fade) {
                envelope = (samples - i) / (double) fade;
            }
            buffer[i] = (short) (Math.sin(step * i) * envelope * Short.MAX_VALUE * 0.6);
        }

        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            track = new AudioTrack(attrs, format, buffer.length * 2,
                    AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
            track.write(buffer, 0, buffer.length);
            track.play();
            status.setText("Memutar " + hz + " Hz selama " + durationMs / 1000 + " detik.");
            // Release once the buffer has played; a static track that is never
            // released keeps the audio session alive for the whole screen.
            status.postDelayed(this::stop, durationMs + 250L);
        } catch (Throwable t) {
            status.setText("Audio gagal: " + t.getMessage());
        }
    }

    private void stop() {
        if (track == null) {
            return;
        }
        try {
            track.stop();
        } catch (Throwable ignored) {
            // stopping an already-stopped track throws on some ROMs
        }
        try {
            track.release();
        } catch (Throwable ignored) {
            // releasing twice is harmless
        }
        track = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        stop();
    }
}
