package ai.cekdroid.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolling line chart for a live series.
 *
 * Android has no chart primitive in the framework, and pulling in a chart
 * library is impossible here (no AndroidX, classpath is android.jar only), so
 * this draws a couple of hundred samples directly. The area under the line is
 * filled with a gradient so a single series still reads as a quantity.
 */
public final class LineChartView extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final List<Float> samples = new ArrayList<>();
    private final int capacity;
    private float min = -1f;
    private float max = 1f;
    private String unit = "";
    private int color;

    public LineChartView(Context ctx, int capacity, int heightDp, String unit) {
        super(ctx);
        this.capacity = capacity;
        this.unit = unit;
        color = Palette.brandBright();

        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2f));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(color);

        fill.setStyle(Paint.Style.FILL);

        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(Math.max(1f, dp(0.7f)));
        grid.setColor(Palette.line(ctx));

        label.setColor(Palette.faint(ctx));
        label.setTextSize(dp(9f));

        setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(widthMeasureSpec)
                : getSuggestedMinimumWidth();
        int h = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec)
                : dp(120f);
        setMeasuredDimension(w, h);
    }

    public void setRange(float lower, float upper) {
        min = lower;
        max = upper;
        invalidate();
    }

    /** Append one sample; the oldest falls off once capacity is reached. */
    public void push(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return;
        }
        samples.add(value);
        while (samples.size() > capacity) {
            samples.remove(0);
        }
        if (value < min || value > max) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        invalidate();
    }

    public void clear() {
        samples.clear();
        min = -1f;
        max = 1f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        // Three horizontal guides with their value on the left.
        float top = h * 0.10f;
        float bottom = h * 0.86f;
        for (int i = 0; i <= 2; i++) {
            float y = top + (bottom - top) * i / 2f;
            canvas.drawLine(0, y, w, y, grid);
            float value = max - (max - min) * i / 2f;
            canvas.drawText(String.format(java.util.Locale.US, "%.1f%s", value, unit),
                    dp(2f), y - dp(3f), label);
        }

        if (samples.size() < 2) {
            canvas.drawText("Menunggu data…", dp(8f), h * 0.55f, label);
            return;
        }

        float span = Math.max(0.0001f, max - min);
        float stepX = w / Math.max(1, capacity - 1);
        int offset = capacity - samples.size();

        path.reset();
        float firstX = 0;
        float firstY = 0;
        for (int i = 0; i < samples.size(); i++) {
            float x = (offset + i) * stepX;
            float y = bottom - (bottom - top) * ((samples.get(i) - min) / span);
            if (i == 0) {
                path.moveTo(x, y);
                firstX = x;
                firstY = y;
            } else {
                path.lineTo(x, y);
            }
        }

        // Area fill: close the line down to the baseline.
        Path filled = new Path(path);
        filled.lineTo(w, bottom);
        filled.lineTo(firstX, bottom);
        filled.close();
        android.graphics.LinearGradient gradient =
                new android.graphics.LinearGradient(0, top, 0, bottom,
                        Palette.tintStrong(color), 0x00000000,
                        android.graphics.Shader.TileMode.CLAMP);
        fill.setShader(gradient);
        canvas.drawPath(filled, fill);

        canvas.drawPath(path, line);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
