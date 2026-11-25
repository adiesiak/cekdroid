package ai.cekdroid.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Animated horizontal meter.
 *
 * Values animate from zero on first bind so a screen does not appear as a wall
 * of finished bars. The animation is the only motion in the app besides the
 * ring, which keeps it cheap on low-end hardware.
 */
public final class MeterView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float fraction;
    private float animated;
    private ValueAnimator animator;
    private int preferredHeightPx;

    public MeterView(Context ctx, int color, float heightDp) {
        super(ctx);
        track.setStyle(Paint.Style.FILL);
        track.setColor(Palette.tint(color) | 0x20000000);
        bar.setStyle(Paint.Style.FILL);
        bar.setColor(color);
        preferredHeightPx = Math.round(heightDp * ctx.getResources().getDisplayMetrics().density);
        setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, preferredHeightPx));
    }

    /** Without this a WRAP_CONTENT height measures to 0 and the bar vanishes. */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(widthMeasureSpec)
                : Math.max(getSuggestedMinimumWidth(), MeasureSpec.getSize(widthMeasureSpec));
        int h = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec)
                : preferredHeightPx;
        setMeasuredDimension(w, h);
    }

    public void setColor(int color) {
        bar.setColor(color);
        track.setColor((color & 0x00FFFFFF) | 0x25000000);
        invalidate();
    }

    /** Set the target 0..1 and animate to it. */
    public void animateTo(float value) {
        final float target = Math.max(0f, Math.min(1f, value));
        this.fraction = target;
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(animated, target);
        animator.setDuration(520);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> {
            animated = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    /**
     * Bind the final value with no animation.
     *
     * A bitmap capture runs right after construction, so an animated meter is
     * always photographed at zero. The exported report binds statically so the
     * bars in the image match the bars on screen.
     */
    public void setStatic(float value) {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        fraction = Math.max(0f, Math.min(1f, value));
        animated = fraction;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, track);

        float fillWidth = Math.max(h, w * animated);
        rect.set(0, 0, fillWidth, h);
        canvas.drawRoundRect(rect, r, r, bar);
    }
}
