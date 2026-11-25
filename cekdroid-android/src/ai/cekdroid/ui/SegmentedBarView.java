package ai.cekdroid.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Stacked bar for memory composition.
 *
 * DevInfo explains RAM by splitting it into Apps / Cache / Free instead of
 * showing one percentage, which is far easier to act on. Segments grow in
 * sequence so the parts are readable while the bar fills.
 */
public final class SegmentedBarView extends View {

    /** One slice: label, share (0..1), colour. */
    public static final class Slice {
        public final String label;
        public final float share;
        public final int color;

        public Slice(String label, float share, int color) {
            this.label = label;
            this.share = share;
            this.color = color;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final java.util.List<Slice> slices = new java.util.ArrayList<>();
    private float progress;
    private ValueAnimator animator;
    private int preferredHeightPx;

    public SegmentedBarView(Context ctx, float heightDp) {
        super(ctx);
        paint.setStyle(Paint.Style.FILL);
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

    public void setSlices(java.util.List<Slice> values, boolean animate) {
        slices.clear();
        slices.addAll(values);
        if (!animate) {
            progress = 1f;
            invalidate();
            return;
        }
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(680);
        animator.setInterpolator(new DecelerateInterpolator(1.4f));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
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
        float x = 0;
        float total = Math.min(1f, progress);
        for (Slice s : slices) {
            float segW = w * s.share * total;
            if (segW <= 0) {
                continue;
            }
            paint.setColor(s.color);
            rect.set(x, 0, x + segW, h);
            // Only round the outer ends so adjacent slices stay flush.
            boolean first = x == 0;
            boolean last = x + segW >= w * total - 0.5f;
            if (first || last) {
                canvas.drawRoundRect(rect, r, r, paint);
                if (first && !last) {
                    rect.set(x + segW - r, 0, x + segW, h);
                    canvas.drawRect(rect, paint);
                }
            } else {
                canvas.drawRect(rect, paint);
            }
            x += segW;
        }
    }
}
