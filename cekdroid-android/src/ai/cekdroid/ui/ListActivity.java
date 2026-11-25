package ai.cekdroid.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full list view for one repeating block, with a filter box.
 *
 * The section page caps inline rows so a 300-item app list does not build 300
 * views on the main thread. This screen is the "Lihat semua" escape hatch: it
 * still renders lazily (chunks of 40 while scrolling) so it stays responsive on
 * a low-end device.
 */
public class ListActivity extends Activity {

    public static final String EXTRA_SECTION = "section";
    public static final String EXTRA_KEY = "key";
    public static final String EXTRA_TITLE = "title";

    private static final int CHUNK = 40;

    private final List<Map<?, ?>> all = new ArrayList<>();
    private final List<Map<?, ?>> filtered = new ArrayList<>();
    private LinearLayout rows;
    private TextView count;
    private ScrollView scroll;
    private int rendered;

    public static Intent intent(Context ctx, String sectionId, String key, String title) {
        Intent i = new Intent(ctx, ListActivity.class);
        i.putExtra(EXTRA_SECTION, sectionId);
        i.putExtra(EXTRA_KEY, key);
        i.putExtra(EXTRA_TITLE, title);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Palette.configure(this);

        String key = getIntent().getStringExtra(EXTRA_KEY);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null) {
            title = key;
        }
        loadRows(key);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.bg(this));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 16);
        header.setPadding(pad, pad, pad, Cards.dp(this, 8));

        count = new TextView(this);
        count.setTextSize(12f);
        count.setTextColor(Palette.dim(this));
        count.setPadding(0, Cards.dp(this, 3), 0, Cards.dp(this, 10));
        header.addView(count);

        if (all.size() > 12) {
            header.addView(filterBox());
        }
        root.addView(header);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(pad, 0, pad, pad);

        scroll = new ScrollView(this);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

                Chrome.addBar(this, root, "Daftar");
        setContentView(root);

        filtered.addAll(all);
        renderNextChunk();
        updateCount();
    }

    private EditText filterBox() {
        EditText box = new EditText(this);
        box.setHint("Cari…");
        box.setTextSize(13f);
        box.setSingleLine(true);
        box.setTextColor(Palette.text(this));
        box.setHintTextColor(Palette.faint(this));
        box.setBackground(Cards.rounded(this, Palette.surface(this),
                Palette.line(this), 10f));
        box.setPadding(Cards.dp(this, 12), Cards.dp(this, 9),
                Cards.dp(this, 12), Cards.dp(this, 9));
        box.addTextChangedListener(new FilterWatcher(this));
        return box;
    }

    /**
     * Static, holding the host explicitly.
     *
     * d8 8.2.2 in build-tools 34.0.0 crashes with an internal NPE while dexing
     * a NON-static inner class that reads an outer field — verified with a
     * minimal repro (Outer$Inner fails, Outer$Nest passes). Passing the host in
     * produces no synthetic outer-this accessor, so it dexes cleanly.
     */
    private static class FilterWatcher implements TextWatcher {
        private final ListActivity host;

        FilterWatcher(ListActivity host) {
            this.host = host;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {
        }

        @Override
        public void onTextChanged(CharSequence s, int a, int b, int c) {
            host.applyFilter(s.toString());
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }

    private void applyFilter(String query) {
        String needle = query.toLowerCase(java.util.Locale.US).trim();
        filtered.clear();
        if (needle.isEmpty()) {
            filtered.addAll(all);
        } else {
            for (Map<?, ?> row : all) {
                if (rowText(row).toLowerCase(java.util.Locale.US).contains(needle)) {
                    filtered.add(row);
                }
            }
        }
        rows.removeAllViews();
        rendered = 0;
        renderNextChunk();
        scroll.scrollTo(0, 0);
        updateCount();
    }

    private void updateCount() {
        if (count == null) {
            return;
        }
        count.setText(filtered.size() == all.size()
                ? all.size() + " baris"
                : filtered.size() + " dari " + all.size() + " baris");
    }

    /** Build the next chunk and re-arm itself when scrolled near the end. */
    private void renderNextChunk() {
        int till = Math.min(rendered + CHUNK, filtered.size());
        for (int i = rendered; i < till; i++) {
            rows.addView(rowView(filtered.get(i), i));
        }
        boolean grew = till > rendered;
        rendered = till;
        if (rendered < filtered.size()) {
            TextView more = Cards.body(this, "Memuat " + (filtered.size() - rendered)
                    + " baris lagi…", Palette.faint(this));
            more.setPadding(0, Cards.dp(this, 12), 0, 0);
            more.setOnClickListener(v -> {
                rows.removeView(more);
                renderNextChunk();
            });
            rows.addView(more);
            // Also load automatically as soon as it is laid out, so the user
            // does not have to tap for a long list.
            more.post(more::performClick);
        } else if (grew) {
            // nothing more to do
        }
    }

    private LinearLayout rowView(Map<?, ?> row, int index) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, Cards.dp(this, 8), 0, Cards.dp(this, 8));

        String title = firstNonNull(row, "name", "package", "description",
                "carrier", "core", "id");

        // Right column: title + details. Left column: the app icon, when the
        // row actually names a package. A list of app names with no icons reads
        // as a table of filenames; the icon is what makes it scannable.
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, 0, 0, 0);

        android.graphics.drawable.Drawable icon = AppIcons.get(this, packageOf(row));
        if (icon != null) {
            android.widget.ImageView image = new android.widget.ImageView(this);
            int size = Cards.dp(this, 38);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(size, size);
            ilp.setMarginEnd(Cards.dp(this, 12));
            image.setLayoutParams(ilp);
            image.setImageDrawable(icon);
            image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            line.addView(image);
        }

        if (title != null) {
            TextView head = new TextView(this);
            head.setText(String.valueOf(title));
            head.setTextSize(13.5f);
            head.setTextColor(Palette.text(this));
            head.setSingleLine(true);
            head.setEllipsize(android.text.TextUtils.TruncateAt.END);
            head.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            line.addView(head);
        }
        body.addView(line);

        TextView detail = new TextView(this);
        detail.setText(pairText(row, title));
        detail.setTextSize(12f);
        detail.setTextColor(Palette.dim(this));
        detail.setTypeface(Typeface.MONOSPACE);
        detail.setTextIsSelectable(true);
        detail.setPadding(icon != null ? Cards.dp(this, 50) : 0,
                Cards.dp(this, 2), 0, 0);
        body.addView(detail);

        wrap.addView(body);

        if (index < filtered.size() - 1) {
            wrap.addView(Cards.divider(this));
        }
        return wrap;
    }

    /** String form of a field, or null when absent. */
    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? null : text;
    }

    /**
     * The package name to load an icon for, if this row names an app.
     *
     * AppsSection writes the package into `name` and does not emit a separate
     * `package` key, so both are checked. Rows from other sections (sensors,
     * codecs) have neither and simply get no icon.
     */
    private static String packageOf(Map<?, ?> row) {
        String candidate = string(row.get("package"));
        if (candidate == null) {
            candidate = string(row.get("name"));
        }
        if (candidate == null || !candidate.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
            return null;
        }
        return candidate;
    }

    private String pairText(Map<?, ?> row, String skip) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> e : row.entrySet()) {
            String k = String.valueOf(e.getKey());
            if (k.equals("name") || k.equals("package")) {
                continue;
            }
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            sb.append(FieldMeta.of(k).label).append(' ')
                    .append(SectionPage.formatValue(k, v));
        }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    private String rowText(Map<?, ?> row) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> e : row.entrySet()) {
            sb.append(e.getKey()).append(' ').append(e.getValue()).append(' ');
        }
        return sb.toString();
    }

    private static String firstNonNull(Map<?, ?> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null && !String.valueOf(v).isEmpty()) {
                return String.valueOf(v);
            }
        }
        return null;
    }

    /**
     * Rows come from the cached report, not a fresh collect: the list is a view
     * of already-read data and must not trigger another device scan.
     */
    @SuppressWarnings("unchecked")
    private void loadRows(String key) {
        String json = getSharedPreferences("cekdroid", MODE_PRIVATE)
                .getString("last_report", null);
        if (json == null) {
            return;
        }
        String sectionId = getIntent().getStringExtra(EXTRA_SECTION);
        try {
            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONObject section = root.optJSONObject(sectionId);
            if (section == null) {
                return;
            }
            org.json.JSONArray array = section.optJSONArray(key);
            if (array == null) {
                return;
            }
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                java.util.Iterator<String> keys = item.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    Object v = item.get(k);
                    map.put(k, v == org.json.JSONObject.NULL ? null : v);
                }
                all.add(map);
            }
        } catch (Throwable ignored) {
            // a malformed cache simply yields an empty list
        }
    }
}
