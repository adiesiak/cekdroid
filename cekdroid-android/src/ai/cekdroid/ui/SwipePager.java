package ai.cekdroid.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Content host that turns a horizontal drag into a tab change.
 *
 * The tab strip is a scroller, so a horizontal drag on it already moves the
 * labels -- but the page body had no equivalent, which left tapping as the only
 * way to change tabs. This restores the gesture people expect from every other
 * tabbed app.
 *
 * Only a clearly horizontal movement is claimed: the section pages are vertical
 * ScrollViews with selectable rows and tappable buttons, and stealing a
 * near-vertical drag would break scrolling, while claiming a still finger would
 * break taps. ACTION_DOWN is never intercepted, so a tap still reaches the child
 * and a short drag falls back to normal scrolling.
 */
public class SwipePager extends FrameLayout {

    /** Called with +1 for "next tab" and -1 for "previous tab". */
    public interface OnSwipe {
        void onSwipe(int direction);
    }

    private final int slop;
    private float downX;
    private float downY;
    private boolean watching;

    private OnSwipe listener;

    public SwipePager(Context ctx) {
        super(ctx);
        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
    }

    public void setOnSwipe(OnSwipe listener) {
        this.listener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                watching = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!watching) {
                    break;
                }
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                // Twice the slop, and clearly wider than it is tall: a diagonal
                // flick during a scroll must not change tab.
                if (Math.abs(dx) > slop * 2 && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    watching = false;
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                watching = false;
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                // Must stay true: after onInterceptTouchEvent claims the
                // gesture, returning false here cancels it and no ACTION_UP
                // ever arrives, so the swipe would silently do nothing.
                return true;
            case MotionEvent.ACTION_UP:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (Math.abs(dx) > slop * 3 && Math.abs(dx) > Math.abs(dy)) {
                    // Dragging left reveals the next tab, matching the direction
                    // the content moved under the finger.
                    if (listener != null) {
                        listener.onSwipe(dx < 0 ? 1 : -1);
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }
}
