package ai.cekdroid.test;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Set;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Bluetooth test: adapter state and the paired-device list.
 *
 * Enumerating paired devices needs BLUETOOTH_CONNECT on Android 12+ and is
 * unguarded before that, so the permission is requested only on the versions
 * that gate it. A denied grant still leaves the adapter state readable, which
 * is why the adapter row renders before the list.
 */
public class BluetoothTestActivity extends Activity {

    private static final int REQUEST_CONNECT = 7102;

    private TextView status;
    private LinearLayout rows;
    private BluetoothAdapter adapter;

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

        TextView enable = Cards.actionButton(this, "Minta aktifkan Bluetooth");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Cards.dp(this, 10);
        enable.setLayoutParams(lp);
        enable.setOnClickListener(v -> requestEnable());
        root.addView(enable);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        root.addView(rows, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Chrome.addBar(this, root, "Bluetooth");
        setContentView(root);

        adapter = BluetoothAdapter.getDefaultAdapter();
        render();
    }

    private boolean needsConnectPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    private boolean hasConnectPermission() {
        return !needsConnectPermission()
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestEnable() {
        if (adapter == null) {
            return;
        }
        try {
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } catch (Throwable t) {
            status.setText("Tidak bisa meminta Bluetooth aktif: " + t.getMessage());
        }
    }

    private void render() {
        rows.removeAllViews();
        if (adapter == null) {
            status.setText("Perangkat tidak punya Bluetooth.");
            return;
        }

        boolean enabled;
        try {
            enabled = adapter.isEnabled();
        } catch (SecurityException e) {
            status.setText("Status adapter butuh izin Bluetooth.");
            return;
        }
        status.setText(enabled ? "Bluetooth aktif." : "Bluetooth mati.");
        rows.addView(Cards.row(this, "Adapter", enabled ? "Aktif" : "Mati"));
        rows.addView(Cards.row(this, "Alamat", safeAddress()));

        if (!enabled) {
            return;
        }
        if (needsConnectPermission() && !hasConnectPermission()) {
            rows.addView(Cards.body(this,
                    "Daftar perangkat berpasangan butuh izin Bluetooth di Android 12+.",
                    Palette.dim(this)));
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_CONNECT);
            return;
        }
        listPaired();
    }

    private String safeAddress() {
        try {
            String address = adapter.getAddress();
            // A ROM that hides the address returns the constant "02:00:00:00:00:00".
            return "02:00:00:00:00:00".equals(address) ? "disembunyikan sistem"
                    : address;
        } catch (Throwable t) {
            return null;
        }
    }

    private void listPaired() {
        Set<BluetoothDevice> bonded;
        try {
            bonded = adapter.getBondedDevices();
        } catch (SecurityException e) {
            rows.addView(Cards.body(this, "Daftar perangkat butuh izin Bluetooth.",
                    Palette.dim(this)));
            return;
        }
        if (bonded == null || bonded.isEmpty()) {
            rows.addView(Cards.body(this, "Belum ada perangkat berpasangan.",
                    Palette.dim(this)));
            return;
        }
        rows.addView(Cards.heading(this, "Berpasangan"));
        for (BluetoothDevice device : bonded) {
            String name;
            String address;
            try {
                name = device.getName();
                address = device.getAddress();
            } catch (SecurityException e) {
                name = "(butuh izin)";
                address = null;
            }
            rows.addView(Cards.row(this,
                    name == null ? "(tanpa nama)" : name,
                    address == null ? "—" : address));
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQUEST_CONNECT) {
            render();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }
}
