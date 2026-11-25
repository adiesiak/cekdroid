package ai.cekdroid.test;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Torch test.
 *
 * The torch is driven through CameraManager.setTorchMode, which talks to the
 * flash unit directly and needs no CAMERA permission. The camera that owns the
 * flash is found by asking each camera for FLASH_INFO_AVAILABLE rather than
 * assuming camera "0" — on a front/back pair the front camera is often first.
 */
public class TorchTestActivity extends Activity {

    private CameraManager cameraManager;
    private String torchCameraId;
    private boolean on;
    private TextView status;
    private TextView button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Palette.configure(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Palette.bg(this));

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        torchCameraId = findTorchCamera();

        status = new TextView(this);
        status.setTextSize(12f);
        status.setTextColor(Palette.dim(this));
        status.setPadding(0, Cards.dp(this, 6), 0, Cards.dp(this, 12));
        status.setText(torchCameraId == null
                ? "Tidak ada unit flash yang bisa dikendalikan app ini."
                : "Ketuk tombol untuk menyalakan senter.");
        root.addView(status);

        button = Cards.actionButton(this, "Nyalakan senter");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Cards.dp(this, 9);
        button.setLayoutParams(lp);
        button.setOnClickListener(v -> toggle());
        root.addView(button);

        Chrome.addBar(this, root, "Senter");
        setContentView(root);
    }

    private String findTorchCamera() {
        if (cameraManager == null) {
            return null;
        }
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) {
                    return id;
                }
            }
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    private void toggle() {
        if (torchCameraId == null || cameraManager == null) {
            return;
        }
        on = !on;
        try {
            cameraManager.setTorchMode(torchCameraId, on);
            button.setText(on ? "Matikan senter" : "Nyalakan senter");
            status.setText(on ? "Senter menyala pada kamera " + torchCameraId + "."
                    : "Senter dimatikan.");
        } catch (CameraAccessException e) {
            on = false;
            status.setText("Senter gagal: " + e.getMessage());
        } catch (Throwable t) {
            on = false;
            status.setText("Senter gagal: " + t.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Leaving the screen with the LED on is a battery and heat problem, so
        // the torch is always switched off here rather than trusted to survive.
        if (on && torchCameraId != null && cameraManager != null) {
            try {
                cameraManager.setTorchMode(torchCameraId, false);
            } catch (Throwable ignored) {
                // the camera service may already be gone
            }
            on = false;
        }
    }
}
