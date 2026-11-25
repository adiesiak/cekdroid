package ai.cekdroid.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable tab strip with an animated underline.
 *
 * Each tab is a label over a thin accent line that fades and grows when
 * selected, which reads as a single movement instead of a hard colour swap.
 * Home (the dashboard) is prepended by the caller so it is always reachable
 * even with every other tab hidden.
 */
public class TabStrip {

    public interface OnSelect {
        void onSelect(int index);
    }

    private final HorizontalScrollView scroller;
    private final LinearLayout strip;
    private final List<TabHolder> tabs = new ArrayList<>();
    private OnSelect listener;
    private int selected = -1;

    private static final class TabHolder {
        final TextView label;
        final View underline;

        TabHolder(TextView label, View underline) {
            this.label = label;
            this.underline = underline;
        }
    }

    public TabStrip(Context ctx) {
        scroller = new HorizontalScrollView(ctx);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        strip = new LinearLayout(ctx);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        int hpad = Cards.dp(ctx, 10);
        strip.setPadding(hpad, 0, hpad, 0);
        scroller.addView(strip);
    }

    public HorizontalScrollView view() {
        return scroller;
    }

    public void setOnSelect(OnSelect listener) {
        this.listener = listener;
    }

    public int selected() {
        return selected;
    }

    public void setTitles(List<String> titles) {
        strip.removeAllViews();
        tabs.clear();
        selected = -1;
        Context ctx = strip.getContext();

        for (int i = 0; i < titles.size(); i++) {
            final int index = i;

            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setPadding(Cards.dp(ctx, 4), Cards.dp(ctx, 10),
                    Cards.dp(ctx, 4), 0);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMarginEnd(Cards.dp(ctx, 16));
            cell.setLayoutParams(clp);

            TextView tab = new TextView(ctx);
            tab.setText(titles.get(i));
            tab.setTextSize(13.5f);
            tab.setGravity(Gravity.CENTER);
            cell.addView(tab);

            View underline = new View(ctx);
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(ctx, 2.5f));
            ulp.topMargin = Cards.dp(ctx, 7);
            underline.setLayoutParams(ulp);
            underline.setAlpha(0f);
            cell.addView(underline);

            cell.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSelect(index);
                }
            });

            strip.addView(cell);
            tabs.add(new TabHolder(tab, underline));
        }
        if (!titles.isEmpty()) {
            select(0);
        }
    }

    /** Highlight one tab, fade the underline in, and scroll it into view. */
    public void select(int index) {
        if (index < 0 || index >= tabs.size() || index == selected) {
            return;
        }
        selected = index;
        int active = Palette.brandBright();
        int idle = Palette.dim(strip.getContext());

        for (int i = 0; i < tabs.size(); i++) {
            TabHolder h = tabs.get(i);
            boolean on = i == index;
            h.label.setTextColor(on ? active : idle);
            h.label.setTypeface(Typeface.create(Typeface.DEFAULT,
                    on ? Typeface.BOLD : Typeface.NORMAL));
            if (on) {
                h.underline.setBackgroundColor(active);
                h.underline.animate().alpha(1f).setDuration(180).start();
            } else {
                h.underline.animate().alpha(0f).setDuration(120).start();
            }
        }

        final View cell = (View) tabs.get(index).label.getParent();
        scroller.post(() -> {
            int target = Math.max(0,
                    cell.getLeft() - Cards.dp(strip.getContext(), 20));
            scroller.smoothScrollTo(target, 0);
        });
    }
}
