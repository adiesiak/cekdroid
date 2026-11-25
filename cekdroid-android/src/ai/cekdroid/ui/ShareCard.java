package ai.cekdroid.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Turn the current screen into a shareable PNG with a CekDroid footer.
 *
 * DevInfo's share button exports an image rather than a JSON blob, and that is
 * the right default here too: a screenshot is what someone actually sends to a
 * friend or a repair shop. The strip at the bottom says where the picture came
 * from, which is also the only branding the app imposes on the image.
 *
 * The footer is drawn into the bitmap, not over the live view, so it cannot be
 * scrolled away or missed by a capture that starts mid-scroll.
 */
public final class ShareCard {

    private static final int FOOTER_DP = 46;

    private ShareCard() {
    }

    /**
     * Render {@code view}, append the footer, and hand the PNG to the chooser.
     * Returns false only when the bitmap or the file could not be produced.
     */
    public static boolean share(Activity activity, View view, String subtitle) {
        Bitmap shot = render(view);
        if (shot == null) {
            return false;
        }
        Bitmap composed = addFooter(activity, shot, subtitle);
        if (composed != shot) {
            shot.recycle();
        }

        Uri uri = write(activity, composed);
        composed.recycle();
        if (uri == null) {
            return false;
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("image/png");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_TEXT, "Info perangkat dari CekDroid");
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(send, "Bagikan tangkapan CekDroid"));
        return true;
    }

    /**
     * Draw the visible view into a bitmap.
     *
     * Only what is on screen is captured — that is what "share this screen"
     * means, and drawing an entire long scroll into one huge bitmap would
     * produce an image nobody can read on a phone.
     */
    private static Bitmap render(View view) {
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        try {
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            view.draw(canvas);
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Extend the image downward and draw the brand line into the new strip. */
    private static Bitmap addFooter(Context ctx, Bitmap src, String subtitle) {
        float density = ctx.getResources().getDisplayMetrics().density;
        int footer = Math.round(FOOTER_DP * density);
        int width = src.getWidth();

        Bitmap out;
        try {
            out = Bitmap.createBitmap(width, src.getHeight() + footer,
                    Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            return src;
        }

        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(src, 0, 0, null);

        int top = src.getHeight();
        // The strip needs to read as its own band. Painting it the page
        // background made the watermark look like part of the content and the
        // 1px rule vanished in the light theme, so the footer gets the raised
        // surface colour plus a visible rule.
        Paint strip = new Paint();
        strip.setColor(Palette.surface(ctx));
        canvas.drawRect(0, top, width, top + footer, strip);

        Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
        rule.setColor(Palette.dim(ctx));
        rule.setAlpha(90);
        canvas.drawRect(0, top, width, top + Math.max(2, Math.round(density * 1.5f)), rule);

        int pad = Math.round(16 * density);
        float mid = top + footer / 2f;

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(Palette.brand());
        float radius = footer * 0.12f;
        canvas.drawCircle(pad + radius, mid, radius, dot);

        Paint brand = new Paint(Paint.ANTI_ALIAS_FLAG);
        brand.setColor(Palette.text(ctx));
        brand.setTextSize(footer * 0.32f);
        brand.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        float brandX = pad + radius * 2 + pad * 0.45f;
        canvas.drawText("CekDroid", brandX, baseline(mid, brand), brand);

        if (subtitle != null && !subtitle.isEmpty()) {
            Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);
            note.setColor(Palette.dim(ctx));
            note.setTextSize(footer * 0.25f);
            note.setTextAlign(Paint.Align.RIGHT);
            float right = width - pad;
            float room = right - (brandX + brand.measureText("CekDroid") + pad);
            canvas.drawText(ellipsize(subtitle, note, room), right,
                    baseline(mid, note), note);
        }
        return out;
    }

    /** Centre a text line vertically on {@code mid}. */
    private static float baseline(float mid, Paint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        return mid - (fm.ascent + fm.descent) / 2f;
    }

    private static String ellipsize(String text, Paint paint, float room) {
        if (room <= 0 || paint.measureText(text) <= room) {
            return room <= 0 ? "" : text;
        }
        String cut = text;
        while (cut.length() > 1 && paint.measureText(cut + "…") > room) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.length() > 1 ? cut + "…" : "";
    }

    /** Write the PNG into cache/share and expose it through ShareProvider. */
    private static Uri write(Context ctx, Bitmap bitmap) {
        try {
            File dir = new File(ctx.getCacheDir(), "share");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            // A fixed name is fine: the chooser reads it immediately, and a
            // growing set of share-*.png files would slowly fill the cache.
            File file = new File(dir, "cekdroid-share.png");
            try (OutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return ShareProvider.uriFor(ctx, file);
        } catch (Throwable t) {
            return null;
        }
    }
}
