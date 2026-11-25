package ai.cekdroid.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * Animated progress ring with the value in the middle.
 *
 * Sweeps from 12 o'clock clockwise and counts the number up at the same time,
 * so the two read as one movement. Used for battery and storage, the two values
 * a device-info screen is actually opened for.
 */
public final class RingView extends View {

    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private float fraction;
    private float animated;
    private int color;
    private String centerText = "";
    private String captionText = "";
    private ValueAnimator animator;
    /** Preferred size in px, used when the parent asks for WRAP_CONTENT. */
    private int preferredPx;

    public RingView(Context ctx, int color, int sizeDp) {
        super(ctx);
        this.color = color;
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(color);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Palette.tintStrong(color));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        // Read the theme rather than assuming dark: on a light background the
        // fixed light-grey numerals were almost invisible.
        label.setColor(Palette.text(ctx));
        caption.setTextAlign(Paint.Align.CENTER);
        caption.setColor(Palette.dim(ctx));

        float d = sizeDp * ctx.getResources().getDisplayMetrics().density;
        preferredPx = (int) d;
        setLayoutParams(new android.view.ViewGroup.LayoutParams(preferredPx, preferredPx));
        float stroke = d * 0.075f;
        arc.setStrokeWidth(stroke);
        ring.setStrokeWidth(stroke * 0.55f);
        label.setTextSize(d * 0.235f);
        caption.setTextSize(d * 0.105f);
    }

    /**
     * A custom View without onMeasure reports 0x0 for WRAP_CONTENT, which made
     * the second ring on the dashboard invisible. Honour the requested size when
     * it is exact, otherwise fall back to the preferred square.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolve(widthMeasureSpec);
        int h = resolve(heightMeasureSpec);
        int side = Math.min(w, h);
        setMeasuredDimension(side, side);
    }

    private int resolve(int spec) {
        int mode = MeasureSpec.getMode(spec);
        int size = MeasureSpec.getSize(spec);
        if (mode == MeasureSpec.EXACTLY) {
            return size;
        }
        if (mode == MeasureSpec.AT_MOST) {
            return Math.min(size, preferredPx);
        }
        return preferredPx;
    }

    public void setCaption(String text) {
        captionText = text == null ? "" : text;
        invalidate();
    }

    /** Animate to 0..1, showing {@code display} as the centre label. */
    public void animateTo(float value, final String display) {
        final float target = Math.max(0f, Math.min(1f, value));
        fraction = target;
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(animated, target);
        animator.setDuration(720);
        animator.setInterpolator(new OvershootInterpolator(0.9f));
        animator.addUpdateListener(a -> {
            animated = (float) a.getAnimatedValue();
            invalidate();
        });
        centerText = display;
        animator.start();
    }

    public void animateTo(float value) {
        animateTo(value, Math.round(value * 100) + "%");
    }

    /**
     * Set the ring to its final state with no animation.
     *
     * The share card draws the view into a bitmap immediately after building it,
     * so an animated ring would be captured at 0% — the report would show a
     * health score of zero. Static binding is what makes the exported image
     * match the screen.
     */
    public void setStatic(float value, String display) {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        fraction = Math.max(0f, Math.min(1f, value));
        animated = fraction;
        centerText = display == null ? "" : display;
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
        float inset = arc.getStrokeWidth() / 2f + 1f;
        oval.set(inset, inset, w - inset, h - inset);

        canvas.drawArc(oval, 0, 360, false, ring);
        canvas.drawArc(oval, -90, 360 * Math.min(1f, animated), false, arc);

        float cx = w / 2f;
        float cy = h / 2f;
        canvas.drawText(centerText, cx, cy + label.getTextSize() * 0.34f, label);
        if (!captionText.isEmpty()) {
            canvas.drawText(captionText, cx, cy + label.getTextSize() * 0.34f
                    + caption.getTextSize() * 1.5f, caption);
        }
    }
}
