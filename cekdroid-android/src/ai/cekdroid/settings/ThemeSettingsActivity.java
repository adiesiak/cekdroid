package ai.cekdroid.settings;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Motion;
import ai.cekdroid.ui.Palette;
import ai.cekdroid.ui.ThemePrefs;

/**
 * Appearance settings: dark/light mode and accent colour.
 *
 * Both write to ThemePrefs and take effect on the next screen open, which is
 * honest about how it works rather than pretending to live-update.
 */
public class ThemeSettingsActivity extends Activity {

    private LinearLayout root;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Palette.configure(this);
        render();
    }

    private void render() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.bg(this));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 16);
        header.setPadding(pad, pad, pad, Cards.dp(this, 8));

        // No title here: the platform action bar already shows "Tampilan", and
        // repeating it produced two identical headings stacked on top.
        TextView sub = new TextView(this);
        sub.setText("Perubahan berlaku saat layar dibuka kembali.");
        sub.setTextSize(12.5f);
        sub.setTextColor(Palette.dim(this));
        header.addView(sub);
        root.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, 0, pad, pad);

        content.addView(modeCard());
        content.addView(accentCard());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

                Chrome.addBar(this, root, "Tampilan");
        setContentView(root);
    }

    private LinearLayout modeCard() {
        LinearLayout card = Cards.card(this, Palette.accent("device"));
        card.addView(Cards.header(this, "device", "Mode tampilan", null));

        int current = ThemePrefs.mode(this);
        // FlowLayout rather than three weighted cells: "Selalu terang" is too
        // wide for a third of a 360dp screen and wrapped onto two lines.
        ai.cekdroid.ui.FlowLayout row = new ai.cekdroid.ui.FlowLayout(this, 7);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Cards.dp(this, 8);
        row.setLayoutParams(flp);

        row.addView(modeChip("Ikuti sistem", ThemePrefs.MODE_SYSTEM, current));
        row.addView(modeChip("Terang", ThemePrefs.MODE_LIGHT, current));
        row.addView(modeChip("Gelap", ThemePrefs.MODE_DARK, current));
        card.addView(row);
        return card;
    }

    private TextView modeChip(String label, int value, int current) {
        boolean on = value == current;
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Cards.dp(this, 14), Cards.dp(this, 9),
                Cards.dp(this, 14), Cards.dp(this, 9));
        chip.setTextColor(on ? Palette.brandBright() : Palette.dim(this));
        chip.setBackground(Cards.rounded(this,
                on ? Palette.tint(Palette.brand()) : 0x00000000,
                on ? Palette.tintStrong(Palette.brand()) : Palette.line(this), 9f));
        chip.setClickable(true);
        Motion.attachPress(chip);
        chip.setOnClickListener(v -> {
            ThemePrefs.setMode(this, value);
            finish();
            startActivity(getIntent());
        });
        return chip;
    }

    private LinearLayout accentCard() {
        LinearLayout card = Cards.card(this, Palette.accent("display"));
        card.addView(Cards.header(this, "display", "Warna aksen", null));
        card.addView(Cards.hint(this,
                "Warna tiap bagian tetap berbeda, tetapi disesuaikan dengan aksen pilihanmu."));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Cards.dp(this, 10), 0, Cards.dp(this, 2));

        int current = ThemePrefs.accentIndex(this);
        for (int i = 0; i < ThemePrefs.ACCENT_COLORS.length; i++) {
            row.addView(swatch(i, current));
        }
        card.addView(row);

        TextView name = new TextView(this);
        name.setText(ThemePrefs.ACCENT_NAMES[current]);
        name.setTextSize(12f);
        name.setTextColor(Palette.dim(this));
        name.setPadding(0, Cards.dp(this, 8), 0, 0);
        card.addView(name);
        return card;
    }

    private View swatch(final int index, int current) {
        int color = ThemePrefs.ACCENT_COLORS[index];
        View circle = new View(this);
        int size = Cards.dp(this, index == current ? 40 : 34);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(Cards.dp(this, 10));
        circle.setLayoutParams(lp);

        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        shape.setColor(color);
        if (index == current) {
            shape.setStroke(Cards.dp(this, 2), Palette.text(this));
        }
        circle.setBackground(shape);
        circle.setClickable(true);
        Motion.attachPress(circle);
        circle.setOnClickListener(v -> {
            ThemePrefs.setAccentIndex(this, index);
            finish();
            startActivity(getIntent());
        });
        return circle;
    }
}
