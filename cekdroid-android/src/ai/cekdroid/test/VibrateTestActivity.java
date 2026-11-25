package ai.cekdroid.test;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/** Vibration motor test: short, long, and a ramped pattern. */
public class VibrateTestActivity extends Activity {

    private Vibrator vibrator;
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

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        status = new TextView(this);
        status.setText(vibrator == null || !vibrator.hasVibrator()
                ? "Perangkat tidak punya motor getar."
                : "Ketuk pola di bawah untuk merasakan motor getar.");
        status.setTextSize(12f);
        status.setTextColor(Palette.dim(this));
        status.setPadding(0, Cards.dp(this, 6), 0, Cards.dp(this, 12));
        root.addView(status);

        root.addView(button("Getar pendek (200 ms)", () -> buzz(200)));
        root.addView(button("Getar panjang (1 detik)", () -> buzz(1000)));
        root.addView(button("Pola naik-turun", this::pattern));
        root.addView(button("Hentikan", () -> {
            if (vibrator != null) {
                vibrator.cancel();
            }
        }));

                Chrome.addBar(this, root, "Tes getar");
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

    private void buzz(long ms) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms,
                    VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(ms);
        }
        status.setText("Getar " + ms + " ms.");
    }

    private void pattern() {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        long[] timings = {0, 100, 100, 200, 100, 300, 100, 400};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1));
        } else {
            vibrator.vibrate(timings, -1);
        }
        status.setText("Pola naik-turun dijalankan.");
    }
}
