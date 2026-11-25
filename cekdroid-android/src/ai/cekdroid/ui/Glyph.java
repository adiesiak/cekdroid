package ai.cekdroid.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Interface glyphs drawn on a 24-unit grid.
 *
 * Same constraint as SectionIcon: no AndroidX, no bundled vector assets, so a
 * dozen small UI icons are cheaper to draw than to ship as XML drawables. Stroke
 * weight and cap style match the section glyphs so the app bar and the cards
 * look like one set.
 */
public final class Glyph extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final String kind;
    private int color;

    public Glyph(Context ctx, String kind, int color, int sizeDp) {
        super(ctx);
        this.kind = kind;
        this.color = color;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        int px = Math.round(sizeDp * ctx.getResources().getDisplayMetrics().density);
        setLayoutParams(new android.view.ViewGroup.LayoutParams(px, px));
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float u = Math.min(w, h) / 24f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(Math.max(1.5f, 1.9f * u));
        path.reset();

        switch (kind) {
            case "back":
                canvas.drawLine(15 * u, 5 * u, 8 * u, 12 * u, paint);
                canvas.drawLine(8 * u, 12 * u, 15 * u, 19 * u, paint);
                break;
            case "refresh":
                canvas.drawArc(arc(12, 12, 8f), -60, 280, false, paint);
                path.moveTo(12 * u, 1.6f * u);
                path.lineTo(17.2f * u, 3.6f * u);
                path.lineTo(13.2f * u, 6.6f * u);
                canvas.drawPath(path, paint);
                break;
            case "more":
                canvas.drawCircle(12 * u, 5.5f * u, 1.35f * u, fill());
                canvas.drawCircle(12 * u, 12 * u, 1.35f * u, fill());
                canvas.drawCircle(12 * u, 18.5f * u, 1.35f * u, fill());
                break;
            case "grid":
                roundRect(3.5f * u, 3.5f * u, 10.5f * u, 10.5f * u, 1.6f * u);
                canvas.drawPath(path, paint);
                roundRect(13.5f * u, 3.5f * u, 20.5f * u, 10.5f * u, 1.6f * u);
                canvas.drawPath(path, paint);
                roundRect(3.5f * u, 13.5f * u, 10.5f * u, 20.5f * u, 1.6f * u);
                canvas.drawPath(path, paint);
                roundRect(13.5f * u, 13.5f * u, 20.5f * u, 20.5f * u, 1.6f * u);
                canvas.drawPath(path, paint);
                break;
            case "palette":
                path.moveTo(12 * u, 3 * u);
                path.cubicTo(6 * u, 5.5f * u, 4.5f * u, 11 * u, 7 * u, 15.5f * u);
                path.cubicTo(8.5f * u, 18.2f * u, 12 * u, 20.6f * u, 17 * u, 18.6f * u);
                path.cubicTo(20 * u, 17.4f * u, 20.6f * u, 12.6f * u, 18 * u, 8.6f * u);
                path.cubicTo(16.6f * u, 6.4f * u, 14.4f * u, 4.2f * u, 12 * u, 3 * u);
                path.close();
                canvas.drawPath(path, paint);
                canvas.drawCircle(9.4f * u, 9.4f * u, 1.3f * u, fill());
                canvas.drawCircle(14.8f * u, 11.4f * u, 1.3f * u, fill());
                canvas.drawCircle(11.4f * u, 15.2f * u, 1.3f * u, fill());
                break;
            case "monitor":
                roundRect(2.6f * u, 4 * u, 21.4f * u, 16.4f * u, 2.4f * u);
                canvas.drawPath(path, paint);
                canvas.drawLine(9 * u, 20.4f * u, 15 * u, 20.4f * u, paint);
                canvas.drawLine(12 * u, 16.4f * u, 12 * u, 20.4f * u, paint);
                break;
            case "floating":
                // A small window hovering over a larger one: the overlay
                // monitor, not another screen.
                roundRect(2.4f * u, 3.4f * u, 15.4f * u, 13.4f * u, 2f * u);
                canvas.drawPath(path, paint);
                roundRect(9.6f * u, 10.6f * u, 21.6f * u, 20.6f * u, 2f * u);
                canvas.drawPath(path, paint);
                canvas.drawLine(13 * u, 15.6f * u, 18.2f * u, 15.6f * u, paint);
                break;
            case "download":
                canvas.drawLine(12 * u, 3.4f * u, 12 * u, 14.6f * u, paint);
                path.moveTo(8 * u, 10.6f * u);
                path.lineTo(12 * u, 14.8f * u);
                path.lineTo(16 * u, 10.6f * u);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(5.4f * u, 12.4f * u);
                path.lineTo(5.4f * u, 19.6f * u);
                path.lineTo(18.6f * u, 19.6f * u);
                path.lineTo(18.6f * u, 12.4f * u);
                canvas.drawPath(path, paint);
                break;
            case "vibrate":
                // Phone with motion arcs either side.
                roundRect(8.4f * u, 3.4f * u, 15.6f * u, 20.6f * u, 2f * u);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(5.4f * u, 8.6f * u);
                path.quadTo(3.6f * u, 12 * u, 5.4f * u, 15.4f * u);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(18.6f * u, 8.6f * u);
                path.quadTo(20.4f * u, 12 * u, 18.6f * u, 15.4f * u);
                canvas.drawPath(path, paint);
                break;
            case "share":
                canvas.drawLine(12 * u, 3.4f * u, 12 * u, 14.2f * u, paint);
                path.moveTo(8 * u, 7.2f * u);
                path.lineTo(12 * u, 3.2f * u);
                path.lineTo(16 * u, 7.2f * u);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(5.4f * u, 12.4f * u);
                path.lineTo(5.4f * u, 19.6f * u);
                path.lineTo(18.6f * u, 19.6f * u);
                path.lineTo(18.6f * u, 12.4f * u);
                canvas.drawPath(path, paint);
                break;
            case "phone":
                roundRect(6.4f * u, 2.2f * u, 17.6f * u, 21.8f * u, 2.9f * u);
                canvas.drawPath(path, paint);
                canvas.drawLine(10.2f * u, 5.4f * u, 13.8f * u, 5.4f * u, paint);
                canvas.drawCircle(12 * u, 18.6f * u, 1.05f * u, fill());
                break;
            case "flash": {
                // Torch: a lamp cone, not a lightning bolt (that reads as
                // "power" at 20dp and confused the test tile).
                roundRect(8 * u, 3.4f * u, 16 * u, 12 * u, 2.2f * u);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(8 * u, 7.6f * u);
                path.lineTo(4 * u, 9.4f * u);
                path.lineTo(4 * u, 14.8f * u);
                path.lineTo(8 * u, 16.2f * u);
                canvas.drawPath(path, paint);
                canvas.drawLine(9.6f * u, 15 * u, 14.4f * u, 15 * u, paint);
                canvas.drawLine(10.4f * u, 18 * u, 13.6f * u, 18 * u, paint);
                canvas.drawLine(11.4f * u, 20.8f * u, 12.6f * u, 20.8f * u, paint);
                break;
            }
            case "speaker": {
                path.moveTo(4.4f * u, 9.2f * u);
                path.lineTo(8.4f * u, 9.2f * u);
                path.lineTo(13 * u, 5 * u);
                path.lineTo(13 * u, 19 * u);
                path.lineTo(8.4f * u, 14.8f * u);
                path.lineTo(4.4f * u, 14.8f * u);
                path.close();
                canvas.drawPath(path, paint);
                canvas.drawArc(arc(13, 12, 3.6f), -55, 110, false, paint);
                canvas.drawArc(arc(13, 12, 6.6f), -55, 110, false, paint);
                break;
            }
            case "mic": {
                roundRect(9.2f * u, 2.6f * u, 14.8f * u, 13 * u, 2.8f * u);
                canvas.drawPath(path, paint);
                canvas.drawArc(arc(12, 11.4f, 5.4f), 0, 180, false, paint);
                canvas.drawLine(12 * u, 16.8f * u, 12 * u, 20.4f * u, paint);
                canvas.drawLine(8.6f * u, 20.4f * u, 15.4f * u, 20.4f * u, paint);
                break;
            }
            case "bluetooth": {
                path.moveTo(12 * u, 2.8f * u);
                path.lineTo(12 * u, 21.2f * u);
                path.lineTo(17.4f * u, 16 * u);
                path.lineTo(6.4f * u, 7.6f * u);
                path.moveTo(12 * u, 2.8f * u);
                path.lineTo(17.4f * u, 8 * u);
                path.lineTo(6.4f * u, 16.4f * u);
                canvas.drawPath(path, paint);
                break;
            }
            default:
                canvas.drawCircle(12 * u, 12 * u, 8 * u, paint);
                break;
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
