package ai.cekdroid.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Card and row construction.
 *
 * No AndroidX means no CardView, so a card is a rounded GradientDrawable. The
 * structure mirrors DevInfo: a coloured icon, a readable label, the value, and
 * — where a number alone means nothing — one line of plain-language help.
 */
public final class Cards {

    private Cards() {
    }

    public static int dp(Context ctx, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                ctx.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable rounded(Context ctx, int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(ctx, radiusDp));
        if (stroke != 0) {
            d.setStroke(Math.max(1, dp(ctx, 1)), stroke);
        }
        return d;
    }

    /** A card with a coloured left edge, the app's visual signature. */
    public static LinearLayout card(Context ctx, int accent) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = rounded(ctx, Palette.surface(ctx), Palette.line(ctx), 16f);
        card.setBackground(bg);
        // 15dp inside and 10dp between cards is DevInfo's spacing: its cards
        // are separated by a visible gap, which is what keeps a long scroll from
        // reading as one continuous block.
        int pad = dp(ctx, 15);
        card.setPadding(pad, dp(ctx, 8), pad, dp(ctx, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 10);
        card.setLayoutParams(lp);
        return card;
    }

    public static LinearLayout card(Context ctx) {
        return card(ctx, Palette.brand());
    }

    /** Card header: icon chip, title, optional subtitle on the right. */
    public static LinearLayout header(Context ctx, String kind, String title, String trailing) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(ctx, 10));

        int accent = Palette.accent(kind);
        LinearLayout chip = new LinearLayout(ctx);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(rounded(ctx, Palette.tint(accent), 0, 11f));
        int chipSize = dp(ctx, 34);
        chip.setLayoutParams(new LinearLayout.LayoutParams(chipSize, chipSize));
        chip.addView(new SectionIcon(ctx, kind, accent, 20));
        row.addView(chip);

        TextView label = new TextView(ctx);
        label.setText(title);
        label.setTextSize(15f);
        label.setTextColor(Palette.text(ctx));
        label.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(dp(ctx, 11));
        label.setLayoutParams(lp);
        row.addView(label);

        if (trailing != null) {
            TextView t = new TextView(ctx);
            t.setText(trailing);
            t.setTextSize(12f);
            t.setTextColor(Palette.dim(ctx));
            row.addView(t);
        }
        return row;
    }

    /** Small uppercase caption above a group of rows. */
    public static TextView heading(Context ctx, String text) {
        TextView t = new TextView(ctx);
        t.setText(text.toUpperCase());
        t.setTextSize(10.5f);
        t.setTextColor(Palette.faint(ctx));
        t.setLetterSpacing(0.1f);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setPadding(0, dp(ctx, 12), 0, dp(ctx, 4));
        return t;
    }

    /**
     * label | value on one evenly-paced row.
     *
     * DevInfo's rows sit on an exact 62px rhythm because they carry only the
     * pair, and that rhythm is what makes a long card scannable. An inline hint
     * broke it: two rows of the same card came out 70px and 150px apart, so the
     * eye could not run down the value column. Hints now come from
     * {@link #rowWithHint} only where they earn the extra height, and this
     * method stays the same height for every row.
     */
    public static LinearLayout row(Context ctx, String label, String value) {
        return row(ctx, label, value, null, null);
    }

    public static LinearLayout row(Context ctx, String label, String value, String hint) {
        return row(ctx, label, value, hint, null);
    }

    /**
     * label | value with an optional explanation under it.
     *
     * Kept as a separate entry point so a caller can see, at the call site,
     * that it is opting out of the uniform rhythm. Passing a null hint is the
     * same as {@link #row(Context, String, String)}.
     */
    public static LinearLayout rowWithHint(Context ctx, String label, String value,
                                           String hint) {
        return row(ctx, label, value, hint, null);
    }

    public static LinearLayout row(Context ctx, String label, String value,
                                   String hint, Integer valueColor) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        // 10dp above and below a 13dp line puts consecutive rows on a 62px
        // pitch at density 2 — the same rhythm DevInfo uses, measured from its
        // own dump (each label row spans exactly 62px).
        wrap.setPadding(0, dp(ctx, 10), 0, dp(ctx, 10));

        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        TextView key = new TextView(ctx);
        key.setText(label);
        key.setTextSize(13f);
        key.setTextColor(Palette.dim(ctx));
        // 48/52 matches DevInfo's split: its label column ends at x=331 of a
        // 720px-wide row, i.e. 46%. Anything narrower clipped labels like
        // "Nomor seri perangkat keras" onto two lines and broke the rhythm.
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.48f);
        klp.setMarginEnd(dp(ctx, 10));
        key.setLayoutParams(klp);
        line.addView(key);

        TextView val = new TextView(ctx);
        val.setText(value == null ? "—" : value);
        val.setTextSize(13f);
        val.setTextColor(valueColor != null ? valueColor
                : value == null ? Palette.faint(ctx) : Palette.text(ctx));
        val.setTypeface(Typeface.MONOSPACE);
        val.setTextIsSelectable(true);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.52f);
        val.setLayoutParams(vlp);
        line.addView(val);

        wrap.addView(line);

        if (hint != null && !hint.isEmpty()) {
            TextView h = new TextView(ctx);
            h.setText(hint);
            h.setTextSize(11.5f);
            h.setTextColor(Palette.faint(ctx));
            h.setLineSpacing(0, 1.2f);
            h.setPadding(0, dp(ctx, 3), 0, 0);
            wrap.addView(h);
        }
        return wrap;
    }

    public static TextView subheading(Context ctx, String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(13f);
        t.setTextColor(Palette.text(ctx));
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setPadding(0, dp(ctx, 8), 0, dp(ctx, 4));
        return t;
    }

    public static TextView body(Context ctx, String text, int color) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(13f);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.3f);
        return t;
    }

    public static TextView hint(Context ctx, String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(11.5f);
        t.setTextColor(Palette.faint(ctx));
        t.setLineSpacing(0, 1.25f);
        t.setPadding(0, dp(ctx, 3), 0, dp(ctx, 3));
        return t;
    }

    public static TextView warning(Context ctx, String text) {
        TextView t = new TextView(ctx);
        t.setText("· " + text);
        t.setTextSize(12f);
        t.setTextColor(Palette.WARN);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(0, dp(ctx, 4), 0, dp(ctx, 4));
        return t;
    }


    /** Text button used for test entry points. */
    public static TextView actionButton(Context ctx, String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(13f);
        t.setTextColor(Palette.brandBright());
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(ctx, 12), dp(ctx, 11), dp(ctx, 12), dp(ctx, 11));
        t.setBackground(rounded(ctx, Palette.tint(Palette.brand()),
                Palette.tintStrong(Palette.brand()), 10f));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    public static View divider(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(Palette.line(ctx));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(ctx, 0.5f)));
        lp.topMargin = dp(ctx, 8);
        lp.bottomMargin = dp(ctx, 8);
        v.setLayoutParams(lp);
        return v;
    }

    /** Severity dot for findings. */
    public static View dot(Context ctx, int color, int sizeDp) {
        View v = new View(ctx);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackground(d);
        int s = dp(ctx, sizeDp);
        v.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return v;
    }

}
