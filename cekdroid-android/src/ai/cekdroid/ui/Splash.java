package ai.cekdroid.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
 * Cold-start splash.
 *
 * Drawn as an overlay on top of the dashboard rather than as its own Activity:
 * a separate Activity means a window transition the user sees as a flash, and
 * it would have to relaunch the real screen anyway. Here the dashboard is
 * already built underneath, so when the overlay fades out the content is
 * settled instead of popping in.
 */
public final class Splash {

    /** Long enough to register as a deliberate open, short enough to not annoy. */
    private static final long HOLD_MS = 620;

    private Splash() {
    }

    /**
     * Build the overlay. Call {@link #dismiss(View, Runnable)} once the caller
     * is ready to reveal the content.
     */
    public static View build(Context ctx) {
        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(Palette.bg(ctx));
        root.setClickable(true);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        column.setLayoutParams(lp);

        View mark = Chrome.logoMark(ctx, 84);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                Cards.dp(ctx, 84), Cards.dp(ctx, 84));
        mark.setLayoutParams(mlp);
        column.addView(mark);

        TextView name = new TextView(ctx);
        name.setText("CekDroid");
        name.setTextSize(27f);
        name.setTextColor(Palette.text(ctx));
        name.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        name.setLetterSpacing(0.02f);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Cards.dp(ctx, 18);
        name.setLayoutParams(nlp);
        column.addView(name);

        TextView tag = new TextView(ctx);
        tag.setText("Info perangkat & diagnostik");
        tag.setTextSize(12f);
        tag.setTextColor(Palette.dim(ctx));
        tag.setGravity(Gravity.CENTER);
        tag.setLetterSpacing(0.06f);
        tag.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(tag);

        root.addView(column);

        // A single swept accent line near the bottom, so the wait has a pulse
        // without a spinner appearing over the centred mark.
        root.addView(new Sweep(ctx), bottomLineParams(ctx));

        playIn(mark, name, tag);
        return root;
    }

    private static FrameLayout.LayoutParams bottomLineParams(Context ctx) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(ctx, 2));
        lp.gravity = Gravity.BOTTOM;
        lp.bottomMargin = Cards.dp(ctx, 72);
        lp.leftMargin = Cards.dp(ctx, 96);
        lp.rightMargin = Cards.dp(ctx, 96);
        return lp;
    }

    private static void playIn(View mark, View name, View tag) {
        float lift = Cards.dp(mark.getContext(), 12);

        mark.setAlpha(0f);
        mark.setScaleX(0.80f);
        mark.setScaleY(0.80f);
        mark.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(420)
                .setInterpolator(new Overshoot())
                .start();

        fadeUp(name, lift, 110);
        fadeUp(tag, lift, 210);
    }

    private static void fadeUp(View view, float lift, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(lift);
        view.animate().alpha(1f).translationY(0f)
                .setStartDelay(delay)
                .setDuration(340)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
    }

    /** Fade the overlay away, then drop it so it cannot eat touches. */
    public static void dismiss(final View splash, final Runnable onDone) {
        if (splash == null) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        splash.animate().alpha(0f)
                .setStartDelay(HOLD_MS)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator(1.2f))
                .withEndAction(() -> {
                    ViewGroup parent = (ViewGroup) splash.getParent();
                    if (parent != null) {
                        parent.removeView(splash);
                    }
                    if (onDone != null) {
                        onDone.run();
                    }
                }).start();
    }

    /** Small overshoot without pulling in the framework class by name twice. */
    private static final class Overshoot extends android.view.animation.OvershootInterpolator {
        Overshoot() {
            super(1.1f);
        }
    }

    /**
     * Indeterminate sweep: a short accent segment that travels left to right
     * and repeats. Cheaper and calmer than ProgressBar's spinning disc.
     */
    private static final class Sweep extends View {

        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float phase;

        Sweep(Context ctx) {
            super(ctx);
            track.setColor(Palette.line(ctx));
            bar.setColor(Palette.brandBright());
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            float r = h / 2f;
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, r, r, track);

            float segW = w * 0.30f;
            float travel = w - segW;
            // Triangle wave so the segment bounces instead of jumping back.
            float t = phase < 0.5f ? phase * 2f : (1f - phase) * 2f;
            float x = Math.max(0f, Math.min(travel, travel * t));
            rect.set(x, 0, x + segW, h);
            canvas.drawRoundRect(rect, r, r, bar);

            phase += 0.018f;
            if (phase > 1f) {
                phase = 0f;
            }
            postInvalidateDelayed(16);
        }
    }
}
