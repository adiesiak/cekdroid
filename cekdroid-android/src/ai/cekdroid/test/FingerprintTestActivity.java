package ai.cekdroid.test;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Fingerprint test.
 *
 * `FingerprintManager` is deprecated but is the only fingerprint API available
 * without AndroidX (BiometricPrompt lives in androidx.biometric) or a
 * Play Services dependency. It still works from API 26 through 28 and remains
 * available, though inert, on later versions, so the screen reports what the
 * platform says rather than assuming Android 11 dropped it.
 *
 * This screen reports sensor state only. Running an actual authentication
 * needs a CryptoObject and a key generated for it, which is a security feature
 * rather than a hardware test, so it is deliberately not started here.
 */
public class FingerprintTestActivity extends Activity {

    private TextView status;
    private FingerprintManager manager;

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
        status.setTextSize(14f);
        status.setTextColor(Palette.text(this));
        status.setPadding(0, Cards.dp(this, 6), 0, Cards.dp(this, 12));
        root.addView(status);

        LinearLayout facts = Cards.card(this, Palette.accent("device"));
        facts.addView(Cards.header(this, "device", "Sensor sidik jari", null));
        facts.addView(sensorFacts(this));
        root.addView(facts);

        Chrome.addBar(this, root, "Sidik jari");
        setContentView(root);

        manager = (FingerprintManager) getSystemService(Context.FINGERPRINT_SERVICE);
        status.setText(describeHardware());
    }

    private String describeHardware() {
        if (manager == null) {
            return "FingerprintManager tidak tersedia di ROM ini.";
        }
        try {
            if (!manager.isHardwareDetected()) {
                return "Perangkat tidak punya sensor sidik jari.";
            }
            if (!manager.hasEnrolledFingerprints()) {
                return "Sensor ada, tetapi belum ada sidik jari terdaftar. "
                        + "Daftarkan dulu di Pengaturan untuk menguji.";
            }
            return "Sensor siap. Sentuh pemindai sidik jari.";
        } catch (SecurityException e) {
            return "Butuh izin USE_FINGERPRINT untuk menguji sensor.";
        } catch (Throwable t) {
            return "Status sensor tidak terbaca: " + t.getMessage();
        }
    }

    private LinearLayout sensorFacts(Context ctx) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        FingerprintManager fm = (FingerprintManager) ctx.getSystemService(
                Context.FINGERPRINT_SERVICE);
        if (fm == null) {
            wrap.addView(Cards.body(ctx, "Tidak tersedia.", Palette.dim(ctx)));
            return wrap;
        }
        try {
            wrap.addView(Cards.row(ctx, "Sensor terdeteksi",
                    fm.isHardwareDetected() ? "Ya" : "Tidak"));
            wrap.addView(Cards.row(ctx, "Sidik jari terdaftar",
                    fm.hasEnrolledFingerprints() ? "Ya" : "Tidak"));
        } catch (SecurityException e) {
            wrap.addView(Cards.body(ctx, "Butuh izin untuk membaca status sensor.",
                    Palette.dim(ctx)));
        } catch (Throwable t) {
            wrap.addView(Cards.body(ctx, t.getMessage(), Palette.dim(ctx)));
        }
        KeyguardManager keyguard = (KeyguardManager) ctx.getSystemService(
                Context.KEYGUARD_SERVICE);
        wrap.addView(Cards.row(ctx, "Kunci layar aktif",
                keyguard != null && keyguard.isKeyguardSecure() ? "Ya" : "Tidak"));
        wrap.addView(Cards.row(ctx, "Tombol sidik jari di layar",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? "API mendukung" : "—"));
        return wrap;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) {
            status.setText(describeHardware());
        }
    }
}
