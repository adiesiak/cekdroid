package ai.cekdroid.test;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ai.cekdroid.ui.Palette;

/**
 * Screen tests: the full set DevInfo's Tes tab offers.
 *
 * Two kinds of check live here and they always have: flat colours (dead pixels,
 * burn-in, tint) and interaction (does every part of the panel register a
 * touch, how many fingers at once). Both are drawn on one View so the whole
 * surface is uniform and a tap cannot land on a stray text line.
 *
 * The mode list matches DevInfo's own set:
 *   solid colours, grey ramp, black/white balance, saturation ramp,
 *   touch coverage, and multi-touch count.
 */
public class ScreenTestActivity extends Activity {

    /** One step in the sequence. */
    private static final class Step {
        final String name;
        final int kind;
        final int color;

        Step(String name, int kind, int color) {
            this.name = name;
            this.kind = kind;
            this.color = color;
        }
    }

    private static final int KIND_SOLID = 0;
    private static final int KIND_GRAY = 1;
    private static final int KIND_SATURATION = 2;
    private static final int KIND_TOUCH = 3;
    private static final int KIND_MULTITOUCH = 4;

    private static final List<Step> STEPS = new ArrayList<>();

    static {
        STEPS.add(new Step("Merah", KIND_SOLID, 0xFFFF0000));
        STEPS.add(new Step("Hijau", KIND_SOLID, 0xFF00FF00));
        STEPS.add(new Step("Biru", KIND_SOLID, 0xFF0000FF));
        STEPS.add(new Step("Putih", KIND_SOLID, 0xFFFFFFFF));
        STEPS.add(new Step("Hitam", KIND_SOLID, 0xFF000000));
        STEPS.add(new Step("Keseimbangan putih", KIND_SOLID, 0xFF808080));
        STEPS.add(new Step("Gradasi abu-abu", KIND_GRAY, 0));
        STEPS.add(new Step("Gradasi saturasi", KIND_SATURATION, 0));
        STEPS.add(new Step("Cakupan sentuh", KIND_TOUCH, 0));
        STEPS.add(new Step("Multi-touch", KIND_MULTITOUCH, 0));
    }

    private int index;
    private TextView hint;
    private FieldView field;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Palette.configure(this);
        // Keep the status and navigation bars out of the way so the whole panel
        // is one uniform field.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        show(0);
    }

    private void show(int i) {
        index = Math.max(0, Math.min(i, STEPS.size() - 1));
        Step step = STEPS.get(index);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(step.kind == KIND_SOLID ? step.color : Color.BLACK);

        field = new FieldView(this, step);
        field.setOnClickListener(v -> advance());
        root.addView(field, new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        hint = new TextView(this);
        boolean interactive = step.kind == KIND_TOUCH || step.kind == KIND_MULTITOUCH;
        hint.setText(step.name + "  ·  " + (index + 1) + "/" + STEPS.size()
                + (interactive
                        ? "\nSentuh area di atas, lalu ketuk bar ini untuk lanjut"
                        : "\nKetuk untuk lanjut, kembali untuk mundur"));
        hint.setTextSize(13f);
        hint.setGravity(Gravity.CENTER);
        hint.setTextColor(step.kind == KIND_SOLID && isLight(step.color)
                ? 0xFF000000 : 0xFFFFFFFF);
        hint.setPadding(0, dp(12), 0, dp(18));
        hint.setBackgroundColor(step.kind == KIND_SOLID && isLight(step.color)
                ? 0x22000000 : 0x22FFFFFF);
        // The touch steps consume every gesture on the field, so the bar under
        // it is the only place left to tap "next".
        if (interactive) {
            hint.setClickable(true);
            hint.setOnClickListener(v -> advance());
        }
        root.addView(hint);

        setContentView(root);
    }

    private static boolean isLight(int color) {
        double luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color);
        return luminance > 150;
    }

    private void advance() {
        if (index >= STEPS.size() - 1) {
            finish();
            return;
        }
        show(index + 1);
    }

    @Override
    public void onBackPressed() {
        if (index > 0) {
            show(index - 1);
        } else {
            super.onBackPressed();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * Draws the current step's field and reports touch state.
     *
     * Solid colours are painted by the parent layout, so this view draws only
     * the ramps and the touch traces.
     */
    private static final class FieldView extends View {

        private final Step step;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint touchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** Covered cells, one per 24dp block, for the touch-coverage test. */
        private boolean[] cells;
        private int columns;
        private int rows;
        private final List<float[]> points = new ArrayList<>();
        private int maxPointers;

        FieldView(Context ctx, Step step) {
            super(ctx);
            this.step = step;
            touchPaint.setStyle(Paint.Style.FILL);
            touchPaint.setColor(0xFF29A7A8);
            text.setColor(0xFFFFFFFF);
            text.setTextAlign(Paint.Align.CENTER);
            setFocusable(true);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            int cell = Math.max(1, Math.round(24 * getResources().getDisplayMetrics().density));
            columns = Math.max(1, w / cell);
            rows = Math.max(1, h / cell);
            cells = new boolean[columns * rows];
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();

            switch (step.kind) {
                case KIND_GRAY:
                    // Full black-to-white ramp. Banding and gamma errors show up
                    // as steps in what should be a smooth line.
                    paint.setShader(new LinearGradient(0, 0, 0, h,
                            new int[]{0xFF000000, 0xFFFFFFFF}, null,
                            Shader.TileMode.CLAMP));
                    canvas.drawRect(0, 0, w, h, paint);
                    paint.setShader(null);
                    break;
                case KIND_SATURATION:
                    // Saturation ramp: red at hue 0 with increasing saturation,
                    // which is the check for a panel that cannot show saturated
                    // colour without clipping.
                    int steps = 64;
                    float bandH = h / steps;
                    for (int i = 0; i < steps; i++) {
                        float sat = i / (float) (steps - 1);
                        paint.setColor(Color.HSVToColor(new float[]{0f, sat, 1f}));
                        canvas.drawRect(0, i * bandH, w, (i + 1) * bandH + 1, paint);
                    }
                    break;
                case KIND_TOUCH:
                    paint.setColor(0xFF101A1C);
                    canvas.drawRect(0, 0, w, h, paint);
                    if (cells != null) {
                        float cellW = w / columns;
                        float cellH = h / rows;
                        for (int r = 0; r < rows; r++) {
                            for (int c = 0; c < columns; c++) {
                                if (cells[r * columns + c]) {
                                    canvas.drawRect(c * cellW, r * cellH,
                                            (c + 1) * cellW, (r + 1) * cellH, touchPaint);
                                }
                            }
                        }
                    }
                    drawTrace(canvas);
                    drawOverlay(canvas, "Sentuh seluruh layar");
                    break;
                case KIND_MULTITOUCH:
                    paint.setColor(0xFF101A1C);
                    canvas.drawRect(0, 0, w, h, paint);
                    drawTrace(canvas);
                    text.setTextSize(52 * getResources().getDisplayMetrics().density);
                    canvas.drawText(String.valueOf(maxPointers), w / 2, h / 2, text);
                    drawOverlay(canvas, "Sentuh dengan beberapa jari sekaligus");
                    break;
                default:
                    break;
            }
        }

        private void drawTrace(Canvas canvas) {
            float radius = 46 * getResources().getDisplayMetrics().density;
            for (float[] point : points) {
                canvas.drawCircle(point[0], point[1], radius, touchPaint);
            }
        }

        private void drawOverlay(Canvas canvas, String message) {
            text.setTextSize(13 * getResources().getDisplayMetrics().density);
            canvas.drawText(message, getWidth() / 2,
                    getHeight() - 16 * getResources().getDisplayMetrics().density, text);
            if (step.kind == KIND_TOUCH && cells != null) {
                int covered = 0;
                for (boolean cell : cells) {
                    if (cell) {
                        covered++;
                    }
                }
                int pct = Math.round(100f * covered / cells.length);
                canvas.drawText(String.format(Locale.US, "%d%% tertutup", pct),
                        getWidth() / 2,
                        28 * getResources().getDisplayMetrics().density, text);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            maxPointers = Math.max(maxPointers, event.getPointerCount());
            points.clear();
            for (int i = 0; i < event.getPointerCount(); i++) {
                float x = event.getX(i);
                float y = event.getY(i);
                points.add(new float[]{x, y});
                if (step.kind == KIND_TOUCH && cells != null) {
                    int c = (int) (x / (getWidth() / (float) columns));
                    int r = (int) (y / (getHeight() / (float) rows));
                    if (c >= 0 && c < columns && r >= 0 && r < rows) {
                        cells[r * columns + c] = true;
                    }
                }
            }
            invalidate();

            // The plain colour steps are advanced by a tap; the touch tests
            // need the gesture to stay on screen, so they never advance here.
            if (step.kind == KIND_SOLID || step.kind == KIND_GRAY
                    || step.kind == KIND_SATURATION) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    performClick();
                }
            }
            return true;
        }

        @Override
        public boolean performClick() {
            // Accessibility: a click is what the parent turns into "advance".
            super.performClick();
            return true;
        }
    }
}
