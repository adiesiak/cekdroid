package ai.cekdroid.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * A tab that launches tools rather than displaying collected data.
 *
 * DevInfo gives its test screens and utility actions their own tabs (Tests,
 * Tools) instead of burying them inside the section they relate to. Reaching
 * "Tes layar" used to mean knowing that it lived under Layar, which is not
 * something a reader should have to remember. These pages are grids of entry
 * points; nothing here collects or stores device data.
 */
public final class HubPage {

    /** One tappable tile. */
    public static final class Entry {
        public final String title;
        public final String hint;
        public final String glyph;
        public final int accent;
        public final Runnable action;

        public Entry(String title, String hint, String glyph, int accent, Runnable action) {
            this.title = title;
            this.hint = hint;
            this.glyph = glyph;
            this.accent = accent;
            this.action = action;
        }
    }

    private HubPage() {
    }

    public static ScrollView build(Context ctx, String title, String intro,
                                   java.util.List<Entry> entries) {
        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(false);
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(ctx, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        LinearLayout heading = new LinearLayout(ctx);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(0, 0, 0, Cards.dp(ctx, 10));

        TextView name = new TextView(ctx);
        name.setText(title);
        name.setTextSize(21f);
        name.setTextColor(Palette.text(ctx));
        name.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        heading.addView(name);

        TextView sub = new TextView(ctx);
        sub.setText(intro);
        sub.setTextSize(12f);
        sub.setTextColor(Palette.dim(ctx));
        sub.setLineSpacing(0, 1.25f);
        sub.setPadding(0, Cards.dp(ctx, 4), 0, 0);
        heading.addView(sub);
        root.addView(heading);

        // Two per row: a single column of wide rows pushed the list well past
        // the fold, and a three-wide grid clipped the longer labels.
        LinearLayout row = null;
        for (int i = 0; i < entries.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = Cards.dp(ctx, 9);
                row.setLayoutParams(rlp);
                root.addView(row);
            }
            Entry entry = entries.get(i);
            View tile = tile(ctx, entry);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 2 == 0) {
                tlp.setMarginEnd(Cards.dp(ctx, 5));
            } else {
                tlp.setMarginStart(Cards.dp(ctx, 5));
            }
            tile.setLayoutParams(tlp);
            row.addView(tile);
            Motion.enter(tile, i);
        }

        // Odd count leaves a hole; a plain spacer keeps the last tile at half
        // width instead of stretching it across the row.
        if (entries.size() % 2 == 1 && row != null) {
            View spacer = new View(ctx);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    0, 1, 1f);
            slp.setMarginStart(Cards.dp(ctx, 5));
            spacer.setLayoutParams(slp);
            row.addView(spacer);
        }
        return scroll;
    }

    private static View tile(Context ctx, final Entry entry) {
        LinearLayout tile = new LinearLayout(ctx);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setBackground(Cards.rounded(ctx, Palette.surface(ctx),
                Palette.line(ctx), 15f));
        int pad = Cards.dp(ctx, 13);
        tile.setPadding(pad, pad, pad, pad);
        tile.setClickable(true);
        tile.setFocusable(true);
        Motion.attachPress(tile);

        LinearLayout chip = new LinearLayout(ctx);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(Cards.rounded(ctx, Palette.tint(entry.accent), 0, 11f));
        int size = Cards.dp(ctx, 36);
        chip.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        chip.addView(icon(ctx, entry.glyph, entry.accent, 20));
        tile.addView(chip);

        TextView label = new TextView(ctx);
        label.setText(entry.title);
        label.setTextSize(13.5f);
        label.setTextColor(Palette.text(ctx));
        label.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        label.setPadding(0, Cards.dp(ctx, 9), 0, 0);
        tile.addView(label);

        if (entry.hint != null) {
            TextView hint = new TextView(ctx);
            hint.setText(entry.hint);
            hint.setTextSize(11f);
            hint.setTextColor(Palette.faint(ctx));
            hint.setLineSpacing(0, 1.2f);
            hint.setPadding(0, Cards.dp(ctx, 3), 0, 0);
            tile.addView(hint);
        }

        tile.setOnClickListener(v -> entry.action.run());
        return tile;
    }

    /**
     * Section glyphs when the name matches one, UI glyphs otherwise.
     *
     * The hub reuses the section vocabulary on purpose: a "Tes layar" tile
     * showing the same monitor outline as the Layar tab is what makes the two
     * read as the same feature. Glyph only knows the chrome icons, so asking it
     * for "sensor" or "battery" drew its fallback circle.
     */
    private static View icon(Context ctx, String kind, int accent, int sizeDp) {
        if (SECTION_KINDS.contains(kind)) {
            return new SectionIcon(ctx, kind, accent, sizeDp);
        }
        return new Glyph(ctx, kind, accent, sizeDp);
    }

    private static final java.util.Set<String> SECTION_KINDS =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "device", "system", "cpu", "gpu", "memory", "storage", "battery",
                    "display", "camera", "sensor", "network", "sim", "apps", "codec",
                    "id", "tests", "tools"));
}
