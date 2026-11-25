package ai.cekdroid.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Screen scaffold: one app bar, one content area.
 *
 * The platform action bar was doing three things badly here — it repeated the
 * title the screen already printed, it could not carry the brand mark, and it
 * forced a second status line underneath before the tabs could start. DevInfo
 * draws its own bar (mark + name + three icon buttons, then straight into the
 * tabs), which is why its top edge reads as one object instead of five stacked
 * rows. Every screen now goes through this class so the header never drifts.
 */
public final class Chrome {

    /** Bar height in dp. Two text lines (title + status) fit without clipping. */
    private static final int BAR_HEIGHT = 58;

    private Chrome() {
    }

    /**
     * Insert a titled bar with a back arrow at the top of an existing root.
     *
     * Secondary screens were built against the platform action bar, which
     * supplied both the title and the way back. With the action bar gone they
     * had no heading and no visible exit, so every one of them calls this with
     * the title it used to get for free.
     */
    public static void addBar(Activity activity, LinearLayout root, String title) {
        addBar(activity, root, title, null);
    }

    public static void addBar(Activity activity, LinearLayout root, String title,
                              String subtitle) {
        if (root == null) {
            return;
        }
        root.addView(bar(activity, title, subtitle, true), 0);
        Chrome.paintStatusBar(activity);
    }

    /** Root + the empty area below the bar, where a screen puts its content. */
    public static final class Screen {
        public final LinearLayout root;
        public final FrameLayout content;

        Screen(LinearLayout root, FrameLayout content) {
            this.root = root;
            this.content = content;
        }
    }

    /**
     * A titled screen with a back arrow.
     *
     * Also paints the status bar to match the page: without this the window
     * keeps the platform's dark bar and a light theme shows a hard seam.
     */
    public static Screen screen(Activity activity, String title) {
        Palette.configure(activity);
        paintStatusBar(activity);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.bg(activity));

        root.addView(bar(activity, title, null, true));

        FrameLayout content = new FrameLayout(activity);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return new Screen(root, content);
    }

    /** Bar with a title and an optional second line, no back arrow. */
    public static LinearLayout bar(Activity activity, String title, String subtitle) {
        return bar(activity, title, subtitle, false);
    }

    public static LinearLayout bar(Activity activity, String title, String subtitle,
                                   boolean back) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Palette.bg(activity));
        int pad = Cards.dp(activity, 16);
        bar.setPadding(back ? Cards.dp(activity, 8) : pad, Cards.dp(activity, 8),
                Cards.dp(activity, 10), Cards.dp(activity, 8));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(activity, BAR_HEIGHT)));

        if (back) {
            bar.addView(iconButton(activity, "back", Palette.text(activity),
                    v -> activity.finish()));
        }
        bar.addView(titleBlock(activity, title, subtitle, 0,
                back ? Cards.dp(activity, 4) : 0));
        return bar;
    }

    /**
     * The home bar: brand mark, app name, live status line, and the actions.
     *
     * Actions are glyph buttons rather than labelled chips. Four word-chips
     * (Tab / Tampilan / Monitor / Bagikan) needed their own row and pushed the
     * tabs 100dp down the screen; the same four fit on the title line as icons.
     */
    public static final class Bar {
        public final LinearLayout root;
        public final TextView subtitle;
        /** Enabled/disabled together while a read is in flight. */
        public final java.util.List<View> actions = new java.util.ArrayList<>();

        Bar(LinearLayout root, TextView subtitle) {
            this.root = root;
            this.subtitle = subtitle;
        }

        public void setStatus(String text) {
            if (subtitle == null) {
                return;
            }
            subtitle.setText(text == null ? "" : text);
            subtitle.setVisibility(text == null || text.isEmpty()
                    ? View.GONE : View.VISIBLE);
        }

        public void setActionsEnabled(boolean enabled) {
            for (View action : actions) {
                action.setEnabled(enabled);
                action.setAlpha(enabled ? 1f : 0.4f);
            }
        }
    }

    public static Bar brandBar(Activity activity, String subtitle, View... actions) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Palette.bg(activity));
        int pad = Cards.dp(activity, 16);
        bar.setPadding(pad, Cards.dp(activity, 7), Cards.dp(activity, 8),
                Cards.dp(activity, 7));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(activity, 60)));

        bar.addView(logoMark(activity, 30));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(Cards.dp(activity, 10));
        titles.setLayoutParams(tlp);

        TextView name = new TextView(activity);
        name.setText("CekDroid");
        name.setTextSize(17f);
        name.setTextColor(Palette.text(activity));
        name.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        name.setSingleLine(true);
        name.setLetterSpacing(0.01f);
        titles.addView(name);

        TextView sub = new TextView(activity);
        sub.setText(subtitle == null ? "" : subtitle);
        sub.setTextSize(10.5f);
        sub.setTextColor(Palette.dim(activity));
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.setPadding(0, Cards.dp(activity, 2), 0, 0);
        sub.setVisibility(subtitle == null || subtitle.isEmpty()
                ? View.GONE : View.VISIBLE);
        titles.addView(sub);
        bar.addView(titles);

        Bar holder = new Bar(bar, sub);
        for (View action : actions) {
            if (action == null) {
                continue;
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Cards.dp(activity, 34), Cards.dp(activity, 34));
            lp.setMarginStart(Cards.dp(activity, 3));
            action.setLayoutParams(lp);
            bar.addView(action);
            holder.actions.add(action);
        }
        return holder;
    }

    private static LinearLayout titleBlock(Context ctx, String title, String subtitle,
                                           int startMargin, int extraEnd) {
        LinearLayout block = new LinearLayout(ctx);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(startMargin);
        lp.setMarginEnd(extraEnd);
        block.setLayoutParams(lp);

        TextView name = new TextView(ctx);
        name.setText(title);
        name.setTextSize(17f);
        name.setTextColor(Palette.text(ctx));
        name.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setLetterSpacing(0.01f);
        block.addView(name);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(ctx);
            sub.setText(subtitle);
            sub.setTextSize(10.5f);
            sub.setTextColor(Palette.dim(ctx));
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setPadding(0, Cards.dp(ctx, 2), 0, 0);
            sub.setId(android.R.id.text2);
            block.addView(sub);
        }
        return block;
    }

    /** Square icon button: tinted circle, glyph, press feedback. */
    public static LinearLayout iconButton(Context ctx, String glyph, int color,
                                          View.OnClickListener onClick) {
        LinearLayout button = new LinearLayout(ctx);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Cards.rounded(ctx, Palette.surface(ctx), 0, 11f));
        button.setClickable(true);
        button.setFocusable(true);
        button.addView(new Glyph(ctx, glyph, color, 19));
        Motion.attachPress(button);
        if (onClick != null) {
            button.setOnClickListener(onClick);
        }
        return button;
    }

    /** Round-button variant with no fill, for a bar that is already tinted. */
    public static LinearLayout flatButton(Context ctx, String glyph, int color,
                                          View.OnClickListener onClick) {
        LinearLayout button = new LinearLayout(ctx);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        int size = Cards.dp(ctx, 34);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, size));
        button.addView(new Glyph(ctx, glyph, color, 19));
        Motion.attachPress(button);
        if (onClick != null) {
            button.setOnClickListener(onClick);
        }
        return button;
    }

    /** The brand mark: rounded teal plate with a phone outline. */
    public static View logoMark(Context ctx, int sizeDp) {
        View mark = new View(ctx) {
            private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();
            private final Path path = new Path();

            @Override
            protected void onDraw(Canvas canvas) {
                float w = getWidth();
                float h = getHeight();
                float r = w * 0.30f;
                rect.set(0, 0, w, h);
                fill.setShader(new android.graphics.LinearGradient(
                        0, 0, 0, h, Palette.brandBright(), Palette.TEAL_DEEP,
                        android.graphics.Shader.TileMode.CLAMP));
                canvas.drawRoundRect(rect, r, r, fill);
                fill.setShader(null);

                line.setStyle(Paint.Style.STROKE);
                line.setStrokeCap(Paint.Cap.ROUND);
                line.setStrokeJoin(Paint.Join.ROUND);
                // White, not the background colour: a dark glyph on the teal
                // plate collapsed into a solid blob at 30dp.
                line.setColor(0xFFFFFFFF);
                line.setStrokeWidth(w * 0.075f);
                float u = w / 24f;
                path.reset();
                path.addRoundRect(new RectF(7.4f * u, 3.2f * u, 16.6f * u, 20.8f * u),
                        2.6f * u, 2.6f * u, Path.Direction.CW);
                canvas.drawPath(path, line);
                canvas.drawCircle(12 * u, 17.4f * u, w * 0.045f, line);
            }
        };
        int size = Cards.dp(ctx, sizeDp);
        mark.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return mark;
    }

    /** Thin rule between the bar and the tab strip. */
    public static View hairline(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(Palette.line(ctx));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Cards.dp(ctx, 0.6f))));
        return v;
    }

    /**
     * Transient message strip under the tab bar.
     *
     * Success text used to sit in the header forever ("Laporan tersimpan
     * 06:07"), which is clutter. Messages now appear when they are news and
     * fade out; a failure stays until the next action so it cannot be missed.
     */
    public static final class Banner extends FrameLayout {

        private final TextView text;
        private final FadeOut fadeOut = new FadeOut(this);

        public Banner(Context ctx) {
            super(ctx);
            setVisibility(GONE);
            setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            int accent = Palette.WARN;
            text = new TextView(ctx);
            text.setTextSize(11.5f);
            text.setTextColor(Palette.text(ctx));
            text.setPadding(Cards.dp(ctx, 12), Cards.dp(ctx, 9),
                    Cards.dp(ctx, 12), Cards.dp(ctx, 9));
            text.setBackground(Cards.rounded(ctx, Palette.tint(accent),
                    Palette.tintStrong(accent), 10f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Cards.dp(ctx, 16);
            lp.rightMargin = Cards.dp(ctx, 16);
            lp.bottomMargin = Cards.dp(ctx, 8);
            text.setLayoutParams(lp);
            addView(text);
        }

        /** Show a message; {@code sticky} keeps it until replaced. */
        public void show(String message, boolean sticky) {
            if (message == null || message.isEmpty()) {
                hide();
                return;
            }
            text.setText(message);
            int accent = sticky ? Palette.CRITICAL : Palette.INFO;
            text.setBackground(Cards.rounded(getContext(), Palette.tint(accent),
                    Palette.tintStrong(accent), 10f));
            setVisibility(VISIBLE);
            setAlpha(0f);
            animate().alpha(1f).setDuration(150).start();
            removeCallbacks(fadeOut);
            if (!sticky) {
                postDelayed(fadeOut, 3200);
            }
        }

        public void hide() {
            removeCallbacks(fadeOut);
            setVisibility(GONE);
        }

        /**
         * Static, holding the host explicitly.
         *
         * d8 8.2.2 in build-tools 34.0.0 crashes with an internal NPE while
         * dexing an inner class that reads a field of its enclosing instance
         * (the same reason ListActivity.FilterWatcher is static). One instance
         * per Banner also keeps the posted and cancelled Runnable identical.
         */
        private static final class FadeOut implements Runnable {

            private final Banner host;

            FadeOut(Banner host) {
                this.host = host;
            }

            @Override
            public void run() {
                host.animate().alpha(0f).setDuration(220)
                        .withEndAction(new HideAction(host)).start();
            }
        }

        private static final class HideAction implements Runnable {

            private final Banner host;

            HideAction(Banner host) {
                this.host = host;
            }

            @Override
            public void run() {
                host.setVisibility(GONE);
            }
        }
    }

    /** Status bar colour follows the page instead of the platform default. */
    public static void paintStatusBar(Activity activity) {
        try {
            activity.getWindow().setStatusBarColor(Palette.bg(activity));
            int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
            // Light navigation bar only exists from API 27; asking for it on 26
            // is harmless but the constant does not resolve, so guard it.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                activity.getWindow().setNavigationBarColor(Palette.bg(activity));
            }
            if (!Palette.dark(activity)) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        } catch (Throwable ignored) {
            // some ROMs reject navigation-bar tinting below API 27
        }
    }

    /** Fade-and-rise in for a freshly built screen. */
    public static void enter(View view, long delayMs) {
        if (view == null) {
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(Cards.dp(view.getContext(), 10));
        view.post(() -> view.animate().alpha(1f).translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .withEndAction(() -> {
                    view.setAlpha(1f);
                    view.setTranslationY(0f);
                }).start());
    }
}
