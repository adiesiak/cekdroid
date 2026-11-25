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
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import ai.cekdroid.collect.Snapshot;

/**
 * One-page report for a phone sale: the health verdict, the device identity,
 * and the few facts a buyer asks about, as a single shareable image.
 *
 * A screenshot of the dashboard is the wrong artefact for a transaction: it
 * includes whatever tab happened to be open and none of the summary. This builds
 * a fixed-width column sized for a chat app, draws it into a bitmap, and hands it
 * to the share sheet with the same provider the screenshot share already uses.
 *
 * Everything here binds statically (no entry animation, no ring animation),
 * because a bitmap is captured immediately after the view is measured — an
 * animated ring would be photographed at zero.
 */
public final class ReportCard {

    /** Width of the exported image in dp; 360 reads well in WhatsApp. */
    private static final int WIDTH_DP = 360;

    private ReportCard() {
    }

    /** Build, render, and share. Returns false only if the bitmap/ file failed. */
    public static boolean share(Activity activity, Snapshot snap) {
        View page = build(activity, snap);
        Bitmap bitmap = render(activity, page);
        if (bitmap == null) {
            return false;
        }
        Uri uri = write(activity, bitmap);
        bitmap.recycle();
        if (uri == null) {
            return false;
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("image/png");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_TEXT, summaryText(snap));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(send, "Bagikan laporan CekDroid"));
        return true;
    }

    // ------------------------------------------------------------- build

    private static View build(Context ctx, Snapshot snap) {
        Health.Result health = Health.evaluate(snap);
        Map<String, Object> device = snap.section("device");
        Map<String, Object> battery = snap.section("battery");
        Map<String, Object> storage = snap.section("storage");
        Map<String, Object> memory = snap.section("memory");
        Map<String, Object> display = snap.section("display");
        Map<String, Object> sensor = snap.section("sensor");

        LinearLayout page = new LinearLayout(ctx);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Palette.bg(ctx));
        int pad = Cards.dp(ctx, 18);
        page.setPadding(pad, pad, pad, pad);

        // ------------------------------------------------------- header
        TextView brand = new TextView(ctx);
        brand.setText("CekDroid · Laporan perangkat");
        brand.setTextSize(11f);
        brand.setTextColor(Palette.faint(ctx));
        brand.setLetterSpacing(0.08f);
        page.addView(brand);

        String marketing = str(device, "marketing_name");
        String model = str(device, "model");
        String title = marketing != null ? marketing
                : (str(device, "brand") + " " + model).trim();
        TextView name = new TextView(ctx);
        name.setText(title);
        name.setTextSize(21f);
        name.setTextColor(Palette.text(ctx));
        name.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        name.setPadding(0, Cards.dp(ctx, 2), 0, 0);
        page.addView(name);

        TextView sub = new TextView(ctx);
        String androidVersion = str(device, "android");
        String patch = str(device, "security_patch");
        sub.setText("Model " + nz(model) + "  ·  Android " + nz(androidVersion)
                + "  ·  patch " + nz(patch));
        sub.setTextSize(11f);
        sub.setTextColor(Palette.dim(ctx));
        sub.setPadding(0, Cards.dp(ctx, 2), 0, 0);
        page.addView(sub);

        page.addView(rule(ctx));

        // ------------------------------------------------------- verdict
        LinearLayout verdictRow = new LinearLayout(ctx);
        verdictRow.setOrientation(LinearLayout.HORIZONTAL);
        verdictRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        verdictRow.setPadding(0, Cards.dp(ctx, 6), 0, 0);

        RingView ring = new RingView(ctx, health.color(), 86);
        verdictRow.addView(ring);
        ring.setStatic(health.score < 0 ? 0f : health.score / 100f,
                health.score < 0 ? "—" : String.valueOf(health.score));

        LinearLayout side = new LinearLayout(ctx);
        side.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.setMarginStart(Cards.dp(ctx, 14));
        side.setLayoutParams(slp);

        TextView verdict = new TextView(ctx);
        verdict.setText(health.verdict);
        verdict.setTextSize(20f);
        verdict.setTextColor(health.color());
        verdict.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        side.addView(verdict);

        TextView headline = new TextView(ctx);
        headline.setText(health.headline);
        headline.setTextSize(11f);
        headline.setTextColor(Palette.dim(ctx));
        headline.setLineSpacing(0, 1.25f);
        side.addView(headline);
        verdictRow.addView(side);
        page.addView(verdictRow);

        page.addView(rule(ctx));

        // ------------------------------------------------------ categories
        page.addView(heading(ctx, "Rincian penilaian"));
        for (Health.Category category : health.categories) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, Cards.dp(ctx, 4), 0, Cards.dp(ctx, 4));

            TextView label = new TextView(ctx);
            label.setText(category.label);
            label.setTextSize(11.5f);
            label.setTextColor(Palette.dim(ctx));
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f));
            row.addView(label);

            // The meter is a static bar here, not the animated one used on
            // screen, for the bitmap-capture reason in setStatic().
            MeterView meter = new MeterView(ctx, category.measured()
                    ? scoreColor(category.score) : Palette.faint(ctx), 5f);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    0, Cards.dp(ctx, 5), 0.38f);
            mlp.setMarginEnd(Cards.dp(ctx, 8));
            meter.setLayoutParams(mlp);
            row.addView(meter);
            meter.setStatic(category.measured() ? category.score / 100f : 0f);

            TextView value = new TextView(ctx);
            value.setText(category.measured() ? String.valueOf(category.score) : "—");
            value.setTextSize(11.5f);
            value.setTextColor(category.measured()
                    ? scoreColor(category.score) : Palette.faint(ctx));
            value.setTypeface(Typeface.MONOSPACE);
            value.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.20f));
            row.addView(value);

            page.addView(row);
        }

        // --------------------------------------------------------- facts
        page.addView(rule(ctx));
        page.addView(heading(ctx, "Data utama"));
        page.addView(fact(ctx, "Baterai",
                nz(str(battery, "health")) + " · "
                        + fmt(battery, "temperature_c", "%.1f °C") + " · "
                        + capacityLine(battery)));
        page.addView(fact(ctx, "Penyimpanan",
                gb(storage, "internal_total_kb") + " total · "
                        + fmt(storage, "internal_free_pct", "%.1f%%") + " tersedia"));
        page.addView(fact(ctx, "RAM",
                gb(memory, "total_kb") + " · swap "
                        + fmt(memory, "swap_used_pct", "%.0f%%")));
        page.addView(fact(ctx, "Layar",
                dims(display) + " · " + fmt(display, "density_dpi", "%.0f dpi")));
        page.addView(fact(ctx, "Sensor",
                intOf(sensor, "count") + " terdeteksi"));
        page.addView(fact(ctx, "Bootloader",
                nz(str(device, "bootloader")) + " · verified "
                        + nz(str(snap.section("system"), "verified_boot"))));

        // -------------------------------------------------------- reasons
        if (!health.reasons.isEmpty()) {
            page.addView(rule(ctx));
            page.addView(heading(ctx, "Perlu diperhatikan"));
            int shown = Math.min(health.reasons.size(), 4);
            for (int i = 0; i < shown; i++) {
                TextView reason = new TextView(ctx);
                reason.setText("· " + health.reasons.get(i));
                reason.setTextSize(11f);
                reason.setTextColor(Palette.WARN);
                reason.setLineSpacing(0, 1.2f);
                reason.setPadding(0, Cards.dp(ctx, 2), 0, Cards.dp(ctx, 2));
                page.addView(reason);
            }
        }

        // --------------------------------------------------------- footer
        page.addView(rule(ctx));
        TextView footer = new TextView(ctx);
        footer.setText("Dibuat " + new SimpleDateFormat("dd MMM yyyy HH:mm",
                Locale.getDefault()).format(new Date())
                + " · CekDroid\nSkor hanya menilai data yang terbaca. Perangkat "
                + "yang dimodifikasi atau pernah kena air tidak bisa dideteksi app.");
        footer.setTextSize(9.5f);
        footer.setTextColor(Palette.faint(ctx));
        footer.setLineSpacing(0, 1.3f);
        footer.setPadding(0, Cards.dp(ctx, 4), 0, 0);
        page.addView(footer);

        return page;
    }

    // ------------------------------------------------------------ render

    /**
     * Measure at a fixed width and draw into a bitmap.
     *
     * The width is fixed in dp so the image is the same on every device; the
     * height follows the content. A software canvas is required because the
     * view was never attached to a window.
     */
    private static Bitmap render(Context ctx, View page) {
        int width = Cards.dp(ctx, WIDTH_DP);
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        page.measure(widthSpec, heightSpec);
        int height = page.getMeasuredHeight();
        if (height <= 0) {
            return null;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            page.layout(0, 0, width, height);
            page.draw(canvas);
            return bitmap;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Write the PNG into cache/share, the folder ShareProvider serves. */
    private static Uri write(Context ctx, Bitmap bitmap) {
        try {
            File dir = new File(ctx.getCacheDir(), "share");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            // A distinct name from the screenshot share: both can be on screen
            // in the chooser, and an overwritten file would show the wrong image.
            File file = new File(dir, "cekdroid-report.png");
            try (OutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return ShareProvider.uriFor(ctx, file);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Short caption so the message body says something useful. */
    private static String summaryText(Snapshot snap) {
        Health.Result health = Health.evaluate(snap);
        Map<String, Object> device = snap.section("device");
        String marketing = str(device, "marketing_name");
        String name = marketing != null ? marketing : str(device, "model");
        return "Cek kesehatan " + nz(name) + ": " + health.verdict
                + (health.score >= 0 ? " (" + health.score + "/100)" : "")
                + " — dari CekDroid.";
    }

    // ----------------------------------------------------------- helpers

    private static int scoreColor(int score) {
        if (score >= 85) {
            return Palette.OK;
        }
        if (score >= 65) {
            return Palette.WARN;
        }
        return Palette.CRITICAL;
    }

    private static View rule(Context ctx) {
        View line = new View(ctx);
        line.setBackgroundColor(Palette.line(ctx));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Cards.dp(ctx, 0.7f)));
        lp.topMargin = Cards.dp(ctx, 10);
        lp.bottomMargin = Cards.dp(ctx, 10);
        line.setLayoutParams(lp);
        return line;
    }

    private static TextView heading(Context ctx, String text) {
        TextView t = new TextView(ctx);
        t.setText(text.toUpperCase(Locale.getDefault()));
        t.setTextSize(9.5f);
        t.setTextColor(Palette.faint(ctx));
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setLetterSpacing(0.1f);
        t.setPadding(0, 0, 0, Cards.dp(ctx, 5));
        return t;
    }

    private static LinearLayout fact(Context ctx, String label, String value) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Cards.dp(ctx, 3), 0, Cards.dp(ctx, 3));

        TextView key = new TextView(ctx);
        key.setText(label);
        key.setTextSize(11.5f);
        key.setTextColor(Palette.dim(ctx));
        key.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.34f));
        row.addView(key);

        TextView val = new TextView(ctx);
        val.setText(value);
        val.setTextSize(11.5f);
        val.setTextColor(Palette.text(ctx));
        val.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.66f));
        row.addView(val);
        return row;
    }

    private static String capacityLine(Map<String, Object> battery) {
        String design = intOf(battery, "design_capacity_mah");
        String full = intOf(battery, "estimated_full_charge_mah");
        if (design == null && full == null) {
            return "kapasitas tidak terbaca";
        }
        return nz(full) + " / " + nz(design) + " mAh";
    }

    private static String dims(Map<String, Object> display) {
        String w = intOf(display, "width_px");
        String h = intOf(display, "height_px");
        if (w == null || h == null) {
            return "—";
        }
        return w + "×" + h;
    }

    private static String gb(Map<String, Object> section, String key) {
        if (section == null) {
            return "—";
        }
        Object raw = section.get(key);
        if (!(raw instanceof Number)) {
            return "—";
        }
        double g = ((Number) raw).doubleValue() / 1024.0 / 1024.0;
        return String.format(Locale.US, "%.1f GB", g);
    }

    private static String fmt(Map<String, Object> section, String key, String pattern) {
        if (section == null) {
            return "—";
        }
        Object raw = section.get(key);
        if (!(raw instanceof Number)) {
            return "—";
        }
        return String.format(Locale.US, pattern, ((Number) raw).doubleValue());
    }

    private static String intOf(Map<String, Object> section, String key) {
        if (section == null) {
            return null;
        }
        Object raw = section.get(key);
        if (!(raw instanceof Number)) {
            return null;
        }
        return String.valueOf(((Number) raw).longValue());
    }

    private static String str(Map<String, Object> section, String key) {
        if (section == null) {
            return null;
        }
        Object raw = section.get(key);
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    private static String nz(String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }
}
