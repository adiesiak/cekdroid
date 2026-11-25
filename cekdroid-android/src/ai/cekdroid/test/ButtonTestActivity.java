package ai.cekdroid.test;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Button and hardware-key test.
 *
 * Each row lights up when its key arrives, which catches a dead volume rocker,
 * a stuck power button, or a broken capacitive area faster than any diagnostic.
 */
public class ButtonTestActivity extends Activity {

    private static final int[][] KEYS = {
            {KeyEvent.KEYCODE_VOLUME_UP, 0},
            {KeyEvent.KEYCODE_VOLUME_DOWN, 0},
            {KeyEvent.KEYCODE_POWER, 0},
            {KeyEvent.KEYCODE_CAMERA, 0},
            {KeyEvent.KEYCODE_HEADSETHOOK, 0},
            {KeyEvent.KEYCODE_BACK, 0},
            {KeyEvent.KEYCODE_HOME, 0},
            {KeyEvent.KEYCODE_MENU, 0},
            {KeyEvent.KEYCODE_DPAD_UP, 0},
            {KeyEvent.KEYCODE_DPAD_DOWN, 0},
            {KeyEvent.KEYCODE_DPAD_LEFT, 0},
            {KeyEvent.KEYCODE_DPAD_RIGHT, 0},
            {KeyEvent.KEYCODE_DPAD_CENTER, 0},
    };

    private LinearLayout rows;
    private TextView status;
    private int seen;

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
        status.setText("Tekan tombol apa pun. 0/" + KEYS.length + " terdeteksi.");
        status.setTextSize(12f);
        status.setTextColor(Palette.dim(this));
        status.setPadding(0, Cards.dp(this, 6), 0, Cards.dp(this, 12));
        root.addView(status);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (int[] key : KEYS) {
            rows.addView(rowFor(key));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

                Chrome.addBar(this, root, "Tes tombol");
        setContentView(root);
    }

    private LinearLayout rowFor(int[] key) {
        LinearLayout row = Cards.row(this, KeyEvent.keyCodeToString(key[0]), "belum");
        row.setTag(key);
        row.setBackground(Cards.rounded(this, 0x00171A21, 0x252A34, 8f));
        return row;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        mark(keyCode);
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        mark(keyCode);
        return super.onKeyUp(keyCode, event);
    }

    private void mark(int keyCode) {
        for (int i = 0; i < rows.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) rows.getChildAt(i);
            int[] key = (int[]) row.getTag();
            if (key == null || key[0] != keyCode) {
                continue;
            }
            if (key[1] == 0) {
                key[1] = 1;
                seen++;
                row.setBackground(Cards.rounded(this, 0x1A3DDC84, 0x553DDC84, 8f));
                TextView value = (TextView) row.getChildAt(1);
                value.setText("terdeteksi");
                value.setTextColor(Palette.OK);
            }
            status.setText("Tekan tombol apa pun. " + seen + "/" + KEYS.length
                    + " terdeteksi.");
            return;
        }
    }
}
