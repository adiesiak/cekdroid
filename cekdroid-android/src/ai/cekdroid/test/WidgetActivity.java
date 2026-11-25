package ai.cekdroid.test;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Installed home-screen widgets.
 *
 * AppWidgetManager can list the providers a package declares without any
 * permission; whether a widget is currently placed on the launcher is a
 * separate query this screen does not make, so it lists what is available
 * rather than what is in use.
 */
public class WidgetActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Palette.configure(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.bg(this));
        int pad = Cards.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);

        TextView status = new TextView(this);
        status.setTextSize(12f);
        status.setTextColor(Palette.dim(this));
        status.setPadding(0, Cards.dp(this, 6), 0, Cards.dp(this, 12));
        root.addView(status);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Chrome.addBar(this, root, "Widget");
        setContentView(root);

        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (manager == null) {
            status.setText("AppWidgetManager tidak tersedia.");
            return;
        }

        PackageManager pm = getPackageManager();
        List<AppWidgetProviderInfo> providers;
        try {
            providers = manager.getInstalledProviders();
        } catch (Throwable t) {
            status.setText("Daftar widget gagal: " + t.getMessage());
            return;
        }
        if (providers == null || providers.isEmpty()) {
            status.setText("Tidak ada widget terpasang.");
            return;
        }
        status.setText(providers.size() + " widget tersedia dari app terpasang.");

        for (AppWidgetProviderInfo info : providers) {
            if (info == null || info.provider == null) {
                continue;
            }
            ComponentName component = info.provider;
            String label;
            try {
                label = info.loadLabel(pm);
            } catch (Throwable t) {
                label = component.getPackageName();
            }
            list.addView(Cards.row(this, label, component.getPackageName(),
                    info.minWidth + " x " + info.minHeight + " dp"));
        }
    }
}
