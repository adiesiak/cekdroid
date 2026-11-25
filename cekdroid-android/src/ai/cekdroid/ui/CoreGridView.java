package ai.cekdroid.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;
import java.util.Map;

/**
 * Per-core occupancy grid, the way DevInfo shows an eight-core SoC.
 *
 * A list of "core 1 / 1085 MHz" rows costs a screenful and still does not show
 * at a glance whether the little cores are pinned or idle. Eight small cells
 * with a fill proportional to current/max frequency answer that in one look, and
 * fit the width of a card without scrolling.
 */
public final class CoreGridView extends View {

    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final List<Map<String, Object>> cores;
    private final int columns;
    private int accent;

    public CoreGridView(Context ctx, List<Map<String, Object>> cores, int accent) {
        super(ctx);
        this.cores = cores;
        this.accent = accent;
        this.columns = cores.size() > 4 ? 4 : Math.max(1, cores.size());

        cell.setStyle(Paint.Style.FILL);
        cell.setColor(Palette.dark(ctx) ? 0xFF101A1C : 0xFFEDF2F0);

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(accent);

        track.setStyle(Paint.Style.FILL);
        track.setColor(Palette.tintStrong(accent));

        text.setColor(Palette.text(ctx));
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);

        sub.setColor(Palette.dim(ctx));
        sub.setTextAlign(Paint.Align.CENTER);

        setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(dp(72) * rows())));
    }

    private int rows() {
        return Math.max(1, (int) Math.ceil(cores.size() / (float) columns));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(widthMeasureSpec)
                : Math.max(getSuggestedMinimumWidth(), MeasureSpec.getSize(widthMeasureSpec));
        int h = Math.round(dp(72) * rows());
        setMeasuredDimension(w, h);
    }

    public void setAccent(int accent) {
        this.accent = accent;
        fill.setColor(accent);
        track.setColor(Palette.tintStrong(accent));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (cores.isEmpty()) {
            return;
        }
        float w = getWidth();
        float cellH = (float) getHeight() / rows();
        float gap = dp(5);
        float cellW = (w - gap * (columns - 1)) / columns;

        text.setTextSize(dp(11));
        sub.setTextSize(dp(8.5f));

        for (int i = 0; i < cores.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            float left = col * (cellW + gap);
            float top = row * cellH + gap / 2f;
            float right = left + cellW;
            float bottom = top + cellH - gap;

            rect.set(left, top, right, bottom);
            canvas.drawRoundRect(rect, dp(8), dp(8), cell);

            Map<String, Object> core = cores.get(i);
            double current = num(core, "current_mhz", 0);
            double max = num(core, "max_mhz", 0);
            float ratio = max > 0 ? (float) Math.min(1.0, current / max) : 0f;

            String label = String.valueOf(core.get("core"));
            label = label.replace("cpu", "");
            // DevInfo labels each cell "Inti N" and prints the clock beneath;
            // a bare "0" reads as a number, not as core zero.
            int coreIndex;
            try {
                coreIndex = Integer.parseInt(label.trim());
            } catch (NumberFormatException e) {
                coreIndex = i;
            }
            String coreLabel = "Inti " + (coreIndex + 1);
            float cx = (left + right) / 2f;
            canvas.drawText(coreLabel, cx, top + (bottom - top) * 0.40f, text);
            String mhz = max > 0
                    ? String.format(java.util.Locale.US, "%.0f MHz", current) : "?";
            canvas.drawText(mhz, cx, top + (bottom - top) * 0.64f, sub);

            // Level bar across the bottom of the cell.
            //
            // Filling the whole cell was the first attempt; at max clock the
            // accent covered the digits printed inside it. A separate 3dp bar
            // keeps the label legible at every load while still showing how
            // hard the core is working.
            float barH = dp(3);
            float inset = dp(7);
            float trackL = left + inset;
            float trackR = right - inset;
            float trackY = bottom - barH - dp(5);
            rect.set(trackL, trackY, trackR, trackY + barH);
            canvas.drawRoundRect(rect, barH / 2f, barH / 2f, track);

            if (ratio > 0.02f) {
                float filledW = (trackR - trackL) * ratio;
                rect.set(trackL, trackY, trackL + filledW, trackY + barH);
                canvas.drawRoundRect(rect, barH / 2f, barH / 2f, fill);
            }
        }
    }

    private static double num(Map<String, Object> map, String key, double fallback) {
        if (map == null) {
            return fallback;
        }
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : fallback;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
