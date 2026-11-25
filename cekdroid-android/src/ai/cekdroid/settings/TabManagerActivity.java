package ai.cekdroid.settings;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedHashSet;
import java.util.Set;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Motion;
import ai.cekdroid.ui.Palette;
import ai.cekdroid.ui.SectionIcon;
import ai.cekdroid.ui.TabPrefs;
import ai.cekdroid.ui.Tabs;

/**
 * Pick which tabs appear on the main screen.
 *
 * Each row is a full-width toggle with the section's own icon and accent, so
 * the list looks like the tabs it controls rather than a generic settings list.
 */
public class TabManagerActivity extends Activity {

    private LinearLayout list;
    private Set<String> visible;
    private final java.util.Map<String, TextView> switches = new java.util.LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Palette.configure(this);
        visible = new java.util.LinkedHashSet<>(TabPrefs.visible(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.bg(this));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 16);
        header.setPadding(pad, pad, pad, Cards.dp(this, 8));

        TextView sub = new TextView(this);
        sub.setText("Sembunyikan yang tidak perlu. Dashboard selalu tampil.");
        sub.setTextSize(12.5f);
        sub.setTextColor(Palette.dim(this));
        sub.setPadding(0, Cards.dp(this, 4), 0, 0);
        header.addView(sub);
        root.addView(header);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad, 0, pad, pad);

        int index = 0;
        for (Tabs.Tab s : Tabs.all()) {
            list.addView(row(s));
            Motion.enter(list.getChildAt(list.getChildCount() - 1), index++);
        }

        TextView reset = Cards.actionButton(this, "Kembalikan ke bawaan");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Cards.dp(this, 14);
        reset.setLayoutParams(rlp);
        reset.setOnClickListener(v -> {
            TabPrefs.restoreDefaults(this);
            visible = new java.util.LinkedHashSet<>(TabPrefs.visible(this));
            refresh();
        });
        list.addView(reset);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

                Chrome.addBar(this, root, "Pilih tab");
        setContentView(root);
    }

    private LinearLayout row(Tabs.Tab section) {
        final String id = section.id;
        int accent = Palette.accent(id);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Cards.rounded(this, Palette.surface(this),
                Palette.line(this), 14f));
        int pad = Cards.dp(this, 12);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Cards.dp(this, 8);
        row.setLayoutParams(lp);
        row.setClickable(true);
        Motion.attachPress(row);

        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(Cards.rounded(this, Palette.tint(accent), 0, 10f));
        int size = Cards.dp(this, 34);
        chip.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        chip.addView(new SectionIcon(this, id, accent, 20));
        row.addView(chip);

        TextView label = new TextView(this);
        label.setText(section.title);
        label.setTextSize(14f);
        label.setTextColor(Palette.text(this));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llp.setMarginStart(Cards.dp(this, 12));
        label.setLayoutParams(llp);
        row.addView(label);

        TextView toggle = new TextView(this);
        toggle.setTextSize(11.5f);
        toggle.setGravity(Gravity.CENTER);
        toggle.setPadding(Cards.dp(this, 11), Cards.dp(this, 5),
                Cards.dp(this, 11), Cards.dp(this, 5));
        switches.put(id, toggle);
        row.addView(toggle);

        row.setOnClickListener(v -> {
            if (visible.contains(id)) {
                visible.remove(id);
            } else {
                visible.add(id);
            }
            TabPrefs.save(this, visible);
            paint(id);
        });

        paint(id);
        return row;
    }

    private void paint(String id) {
        TextView toggle = switches.get(id);
        if (toggle == null) {
            return;
        }
        boolean on = visible.contains(id);
        int accent = Palette.accent(id);
        toggle.setText(on ? "Tampil" : "Sembunyi");
        toggle.setTextColor(on ? accent : Palette.faint(this));
        toggle.setBackground(Cards.rounded(this,
                on ? Palette.tint(accent) : 0x00000000,
                on ? Palette.tintStrong(accent) : Palette.line(this), 20f));
    }

    private void refresh() {
        for (String id : switches.keySet()) {
            paint(id);
        }
    }
}
