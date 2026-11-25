package ai.cekdroid.test;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Export an app's icon and APK.
 *
 * Both are copied into the app's own external files directory, which is the
 * only place an unprivileged app may write and still have the user reach it.
 * System app APKs are readable but huge, so they are listed but not exported
 * automatically. Every failure names its own cause.
 */
public class AppExportActivity extends Activity {

    private TextView status;
    private LinearLayout list;
    private File outDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Palette.configure(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Palette.bg(this));

        status = new TextView(this);
        status.setTextSize(12f);
        status.setTextColor(Palette.dim(this));
        status.setPadding(0, Cards.dp(this, 6), 0, Cards.dp(this, 12));
        root.addView(status);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

                Chrome.addBar(this, root, "Export ikon & APK");
        setContentView(root);

        outDir = new File(getExternalFilesDir(null), "export");
        if (!outDir.exists() && !outDir.mkdirs()) {
            status.setText("Tidak bisa membuat folder export.");
            return;
        }
        status.setText("Hasil disimpan di " + outDir.getAbsolutePath());
        populate();
    }

    private void populate() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        int shown = 0;
        for (ApplicationInfo info : apps) {
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }
            CharSequence label;
            try {
                label = pm.getApplicationLabel(info);
            } catch (Throwable t) {
                label = info.packageName;
            }
            list.addView(rowFor(pm, info, String.valueOf(label)));
            if (++shown >= 40) {
                list.addView(Cards.body(this,
                        "+" + (apps.size() - shown) + " app lain tidak ditampilkan",
                        Palette.dim(this)));
                break;
            }
        }
    }

    private LinearLayout rowFor(PackageManager pm, ApplicationInfo info, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        // Palette, not a hardcoded pair: the old row kept a dark surface in the
        // light theme, so every export row was a dark block on a white page.
        row.setBackground(Cards.rounded(this, Palette.surface(this),
                Palette.line(this), 10f));
        int pad = Cards.dp(this, 12);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Cards.dp(this, 9);
        row.setLayoutParams(lp);

        row.addView(Cards.subheading(this, label + "  ·  " + info.packageName));

        // Per-row status: a single screen-wide label lost the result of the
        // previous tap as soon as a second one landed.
        TextView rowStatus = Cards.hint(this, "");
        rowStatus.setVisibility(android.view.View.GONE);
        row.addView(rowStatus);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView icon = Cards.actionButton(this, "Simpan ikon");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        alp.setMarginEnd(Cards.dp(this, 8));
        icon.setLayoutParams(alp);
        icon.setOnClickListener(v -> saveIcon(pm, info, label, rowStatus));
        actions.addView(icon);

        TextView apk = Cards.actionButton(this, "Salin APK");
        apk.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        apk.setOnClickListener(v -> saveApk(info, label, rowStatus));
        actions.addView(apk);

        row.addView(actions);
        return row;
    }

    /** Echo a result on the row it belongs to, and on the header for context. */
    private void report(TextView rowStatus, String message) {
        rowStatus.setText(message);
        rowStatus.setVisibility(android.view.View.VISIBLE);
        status.setText(message);
    }

    private void saveIcon(PackageManager pm, ApplicationInfo info, String label,
                          TextView rowStatus) {
        try {
            Drawable drawable = info.loadIcon(pm);
            int size = Math.max(drawable.getIntrinsicWidth(), 192);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);

            File target = new File(outDir, safeName(label) + ".png");
            try (OutputStream out = new FileOutputStream(target)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bmp.recycle();
            report(rowStatus, "Ikon disimpan: " + target.getName());
        } catch (Throwable t) {
            report(rowStatus, "Ikon gagal (" + info.packageName + "): " + t.getMessage());
        }
    }

    private void saveApk(ApplicationInfo info, String label, TextView rowStatus) {
        File source = new File(info.sourceDir);
        if (!source.isFile()) {
            report(rowStatus, "APK tidak terbaca: " + info.packageName);
            return;
        }
        File target = new File(outDir, safeName(label) + ".apk");
        try (InputStream in = new java.io.FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            long total = 0;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                total += read;
            }
            report(rowStatus, "APK disalin: " + target.getName()
                    + " (" + total / 1024 / 1024 + " MB)");
        } catch (Throwable t) {
            report(rowStatus, "APK gagal (" + info.packageName + "): " + t.getMessage());
        }
    }

    /** Keep only filesystem-safe characters so the file actually lands. */
    private static String safeName(String label) {
        String cleaned = label.replaceAll("[^A-Za-z0-9._-]", "_").trim();
        return cleaned.isEmpty() ? "app" : cleaned;
    }
}
