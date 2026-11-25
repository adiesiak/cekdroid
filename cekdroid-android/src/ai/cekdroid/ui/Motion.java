package ai.cekdroid.ui;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Entry animations.
 *
 * Deliberately tiny: a card fades and lifts a few dp, then settles. Long or
 * bouncy motion gets tiring on a screen you open to read numbers, so nothing
 * here runs longer than 260 ms.
 *
 * Important: a view is usually not attached to a window yet when a screen is
 * being built. Animating it immediately left it stuck at alpha 0 (invisible but
 * still taking space), so the fade is posted to run after attachment and the
 * final state is forced rather than assumed.
 */
public final class Motion {

    private Motion() {
    }

    /** Fade + lift a view into place, staggered by {@code index}. */
    public static void enter(final View view, final int index) {
        if (view == null) {
            return;
        }
        final float offset = 14f * view.getResources().getDisplayMetrics().density;
        view.setAlpha(0f);
        view.setTranslationY(offset);

        final Runnable animation = () -> view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(Math.min(index, 12) * 26L)
                .setDuration(240L)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .withEndAction(() -> {
                    // Guarantee visibility even if the animator is cancelled.
                    view.setAlpha(1f);
                    view.setTranslationY(0f);
                })
                .start();

        if (view.isAttachedToWindow()) {
            animation.run();
        } else {
            view.post(animation);
        }
    }

    /** Press feedback for tappable cards and pills. */
    public static void attachPress(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.975f).scaleY(0.975f)
                            .setDuration(90).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f)
                            .setDuration(120).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** Animate a 0..1 progress into an arbitrary consumer. */
    public static ValueAnimator progress(float to, long duration,
                                         ValueAnimator.AnimatorUpdateListener listener) {
        ValueAnimator a = ValueAnimator.ofFloat(0f, Math.max(0f, Math.min(1f, to)));
        a.setDuration(duration);
        a.setInterpolator(new DecelerateInterpolator(1.4f));
        a.addUpdateListener(listener);
        a.start();
        return a;
    }
}
