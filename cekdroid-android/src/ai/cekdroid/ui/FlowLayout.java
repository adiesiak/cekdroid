package ai.cekdroid.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Wrapping row.
 *
 * LinearLayout does not wrap: with a horizontal orientation, children past the
 * right edge are simply clipped, which is why capability pills rendered as one
 * character per line or vanished. This lays children left to right and starts a
 * new line when the next one does not fit.
 */
public final class FlowLayout extends ViewGroup {

    private final int gapPx;

    public FlowLayout(Context ctx, int gapDp) {
        super(ctx);
        gapPx = Cards.dp(ctx, gapDp);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int available = width - getPaddingLeft() - getPaddingRight();

        int lineWidth = 0;
        int lineHeight = 0;
        int totalHeight = 0;
        int maxLineWidth = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();

            if (lineWidth > 0 && lineWidth + gapPx + cw > available) {
                totalHeight += lineHeight + gapPx;
                maxLineWidth = Math.max(maxLineWidth, lineWidth);
                lineWidth = cw;
                lineHeight = ch;
            } else {
                lineWidth = lineWidth == 0 ? cw : lineWidth + gapPx + cw;
                lineHeight = Math.max(lineHeight, ch);
            }
        }
        totalHeight += lineHeight;

        setMeasuredDimension(width,
                totalHeight + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int available = getWidth() - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();

            if (x > getPaddingLeft() && x + cw > getPaddingLeft() + available) {
                x = getPaddingLeft();
                y += lineHeight + gapPx;
                lineHeight = 0;
            }
            child.layout(x, y, x + cw, y + ch);
            x += cw + gapPx;
            lineHeight = Math.max(lineHeight, ch);
        }
    }
}
