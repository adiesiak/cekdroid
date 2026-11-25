package ai.cekdroid.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Section icons drawn straight onto a Canvas.
 *
 * This project has no AndroidX and no design assets, and shipping a dozen
 * vector XMLs would need resource plumbing for no benefit: these are simple
 * outline glyphs, so drawing them costs no resources and scales exactly.
 * The geometry mirrors DevInfo's outline style (2dp stroke, round caps).
 */
public final class SectionIcon extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final String kind;
    private final int color;

    public SectionIcon(Context ctx, String kind, int color, int sizeDp) {
        super(ctx);
        this.kind = kind;
        this.color = color;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        int px = Math.round(sizeDp * ctx.getResources().getDisplayMetrics().density);
        setLayoutParams(new android.view.ViewGroup.LayoutParams(px, px));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float u = Math.min(w, h) / 24f;          // 24-unit design grid
        paint.setStrokeWidth(Math.max(1.6f, 1.7f * u));
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        path.reset();

        switch (kind) {
            case "device": {
                roundRect(7 * u, 2 * u, 17 * u, 22 * u, 2.2f * u);
                canvas.drawPath(path, paint);
                canvas.drawCircle(12 * u, 19 * u, 1.0f * u, fill());
                canvas.drawLine(10 * u, 5 * u, 14 * u, 5 * u, paint);
                break;
            }
            case "system": {
                canvas.drawCircle(12 * u, 12 * u, 3.2f * u, paint);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    float x1 = (float) (12 * u + Math.cos(a) * 6.0f * u);
                    float y1 = (float) (12 * u + Math.sin(a) * 6.0f * u);
                    float x2 = (float) (12 * u + Math.cos(a) * 8.2f * u);
                    float y2 = (float) (12 * u + Math.sin(a) * 8.2f * u);
                    canvas.drawLine(x1, y1, x2, y2, paint);
                }
                canvas.drawCircle(12 * u, 12 * u, 8.2f * u, paint);
                break;
            }
            case "cpu": {
                roundRect(5 * u, 5 * u, 19 * u, 19 * u, 2f * u);
                canvas.drawPath(path, paint);
                roundRect(9 * u, 9 * u, 15 * u, 15 * u, 1.2f * u);
                canvas.drawPath(path, paint);
                for (int i = 0; i < 3; i++) {
                    float p = (8 + i * 4) * u;
                    canvas.drawLine(p, 2.5f * u, p, 5 * u, paint);
                    canvas.drawLine(p, 19 * u, p, 21.5f * u, paint);
                    canvas.drawLine(2.5f * u, p, 5 * u, p, paint);
                    canvas.drawLine(19 * u, p, 21.5f * u, p, paint);
                }
                break;
            }
            case "gpu": {
                roundRect(3 * u, 7 * u, 21 * u, 19 * u, 2f * u);
                canvas.drawPath(path, paint);
                canvas.drawCircle(9 * u, 13 * u, 2.2f * u, paint);
                canvas.drawLine(13 * u, 11 * u, 19 * u, 11 * u, paint);
                canvas.drawLine(13 * u, 15 * u, 19 * u, 15 * u, paint);
                canvas.drawLine(8 * u, 7 * u, 8 * u, 4 * u, paint);
                break;
            }
            case "memory": {
                roundRect(3 * u, 8 * u, 21 * u, 17 * u, 1.8f * u);
                canvas.drawPath(path, paint);
                for (int i = 0; i < 4; i++) {
                    float p = (6 + i * 4) * u;
                    canvas.drawLine(p, 17 * u, p, 20 * u, paint);
                }
                canvas.drawLine(7 * u, 12 * u, 17 * u, 12 * u, paint);
                break;
            }
            case "storage": {
                roundRect(4 * u, 4 * u, 20 * u, 20 * u, 2.4f * u);
                canvas.drawPath(path, paint);
                roundRect(9 * u, 9 * u, 15 * u, 15 * u, 1.4f * u);
                canvas.drawPath(path, paint);
                canvas.drawLine(4 * u, 16 * u, 9 * u, 16 * u, paint);
                break;
            }
            case "battery": {
                roundRect(3 * u, 7 * u, 19 * u, 17 * u, 2.2f * u);
                canvas.drawPath(path, paint);
                roundRect(19 * u, 10 * u, 21.5f * u, 14 * u, 1f * u);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(12.6f * u, 9 * u);
                path.lineTo(8.4f * u, 13.6f * u);
                path.lineTo(11.2f * u, 13.6f * u);
                path.lineTo(10.4f * u, 15.4f * u);
                path.lineTo(14.8f * u, 10.6f * u);
                path.lineTo(11.9f * u, 10.6f * u);
                path.close();
                canvas.drawPath(path, fill());
                break;
            }
            case "display": {
                roundRect(2.5f * u, 4 * u, 21.5f * u, 18 * u, 2f * u);
                canvas.drawPath(path, paint);
                canvas.drawLine(8 * u, 21 * u, 16 * u, 21 * u, paint);
                canvas.drawLine(12 * u, 18 * u, 12 * u, 21 * u, paint);
                break;
            }
            case "camera": {
                roundRect(2.5f * u, 6.5f * u, 21.5f * u, 19 * u, 2.4f * u);
                canvas.drawPath(path, paint);
                canvas.drawCircle(12 * u, 12.8f * u, 3.6f * u, paint);
                canvas.drawLine(8 * u, 6.5f * u, 9.6f * u, 4 * u, paint);
                canvas.drawLine(9.6f * u, 4 * u, 14.4f * u, 4 * u, paint);
                canvas.drawLine(14.4f * u, 4 * u, 16 * u, 6.5f * u, paint);
                break;
            }
            case "sensor": {
                canvas.drawCircle(12 * u, 12 * u, 2 * u, fill());
                canvas.drawArc(arc(12, 12, 6), 200, 140, false, paint);
                canvas.drawArc(arc(12, 12, 9.5f), 200, 140, false, paint);
                canvas.drawArc(arc(12, 12, 6), 20, 140, false, paint);
                canvas.drawArc(arc(12, 12, 9.5f), 20, 140, false, paint);
                break;
            }
            case "network": {
                canvas.drawArc(arc(12, 18, 9.5f), 225, 90, false, paint);
                canvas.drawArc(arc(12, 18, 6), 225, 90, false, paint);
                canvas.drawArc(arc(12, 18, 2.8f), 225, 90, false, paint);
                canvas.drawCircle(12 * u, 18 * u, 1.1f * u, fill());
                break;
            }
            case "location": {
                // Map pin: the outline DevInfo uses for its Lokasi tab.
                path.moveTo(12 * u, 21 * u);
                path.cubicTo(12 * u, 21 * u, 4.6f * u, 14.2f * u, 4.6f * u, 9.6f * u);
                path.cubicTo(4.6f * u, 5.5f * u, 7.9f * u, 2.6f * u, 12 * u, 2.6f * u);
                path.cubicTo(16.1f * u, 2.6f * u, 19.4f * u, 5.5f * u, 19.4f * u, 9.6f * u);
                path.cubicTo(19.4f * u, 14.2f * u, 12 * u, 21 * u, 12 * u, 21 * u);
                path.close();
                canvas.drawPath(path, paint);
                canvas.drawCircle(12 * u, 9.5f * u, 2.7f * u, paint);
                break;
            }
            case "sim": {
                roundRect(6 * u, 2.5f * u, 18 * u, 21.5f * u, 2.6f * u);
                canvas.drawPath(path, paint);
                roundRect(9.5f * u, 10 * u, 14.5f * u, 17 * u, 1.2f * u);
                canvas.drawPath(path, paint);
                canvas.drawLine(9.5f * u, 13.4f * u, 14.5f * u, 13.4f * u, paint);
                break;
            }
            case "apps": {
                roundRect(3.5f * u, 3.5f * u, 10.5f * u, 10.5f * u, 2f * u);
                canvas.drawPath(path, paint);
                roundRect(13.5f * u, 3.5f * u, 20.5f * u, 10.5f * u, 2f * u);
                canvas.drawPath(path, paint);
                roundRect(3.5f * u, 13.5f * u, 10.5f * u, 20.5f * u, 2f * u);
                canvas.drawPath(path, paint);
                roundRect(13.5f * u, 13.5f * u, 20.5f * u, 20.5f * u, 2f * u);
                canvas.drawPath(path, paint);
                break;
            }
            case "codec": {
                path.moveTo(9 * u, 6 * u);
                path.lineTo(5 * u, 12 * u);
                path.lineTo(9 * u, 18 * u);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(15 * u, 6 * u);
                path.lineTo(19 * u, 12 * u);
                path.lineTo(15 * u, 18 * u);
                canvas.drawPath(path, paint);
                canvas.drawLine(13 * u, 4.5f * u, 11 * u, 19.5f * u, paint);
                break;
            }
            case "id": {
                roundRect(2.5f * u, 5 * u, 21.5f * u, 19 * u, 2.4f * u);
                canvas.drawPath(path, paint);
                canvas.drawCircle(8.5f * u, 11 * u, 2.2f * u, paint);
                canvas.drawLine(5 * u, 16 * u, 12 * u, 16 * u, paint);
                canvas.drawLine(15 * u, 10 * u, 19 * u, 10 * u, paint);
                canvas.drawLine(15 * u, 14 * u, 19 * u, 14 * u, paint);
                break;
            }
            case "tests": {
                // Checklist with a tick: the tab that runs checks.
                roundRect(3.6f * u, 2.6f * u, 20.4f * u, 21.4f * u, 3.2f * u);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(7.4f * u, 8.2f * u);
                path.lineTo(9.4f * u, 10.2f * u);
                path.lineTo(12.6f * u, 6.8f * u);
                canvas.drawPath(path, paint);
                canvas.drawLine(15.2f * u, 8.4f * u, 18.4f * u, 8.4f * u, paint);
                path.reset();
                path.moveTo(7.4f * u, 15.4f * u);
                path.lineTo(9.4f * u, 17.4f * u);
                path.lineTo(12.6f * u, 14f * u);
                canvas.drawPath(path, paint);
                canvas.drawLine(15.2f * u, 15.6f * u, 18.4f * u, 15.6f * u, paint);
                break;
            }
            case "tools": {
                // Wrench on the diagonal: an action, not a measurement.
                path.moveTo(6.2f * u, 17.8f * u);
                path.lineTo(13.6f * u, 10.4f * u);
                canvas.drawPath(path, paint);
                path.reset();
                path.addArc(arc(16.2f, 7.8f, 5.2f), 118, 244);
                canvas.drawPath(path, paint);
                canvas.drawCircle(16.2f * u, 7.8f * u, 1.7f * u, paint);
                canvas.drawCircle(5.6f * u, 18.4f * u, 1.5f * u, paint);
                break;
            }
            default: {
                canvas.drawCircle(12 * u, 12 * u, 8 * u, paint);
                canvas.drawLine(12 * u, 8 * u, 12 * u, 13 * u, paint);
                canvas.drawCircle(12 * u, 16.4f * u, 1 * u, fill());
            }
        }
    }

    private Paint fill() {
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setStyle(Paint.Style.FILL);
        f.setColor(color);
        return f;
    }

    private RectF arc(float cx, float cy, float r) {
        float u = Math.min(getWidth(), getHeight()) / 24f;
        return new RectF((cx - r) * u, (cy - r) * u, (cx + r) * u, (cy + r) * u);
    }

    private void roundRect(float l, float t, float r, float b, float radius) {
        path.addRoundRect(new RectF(l, t, r, b), radius, radius, Path.Direction.CW);
    }
}
