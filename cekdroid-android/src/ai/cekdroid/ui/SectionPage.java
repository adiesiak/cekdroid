package ai.cekdroid.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.cekdroid.collect.Snapshot;

/**
 * Renders one section of a Snapshot as cards.
 *
 * Every row goes through FieldMeta, so the screen shows "Memori bebas / 0.3 GB
 * / RAM yang benar-benar belum dipakai" instead of "meminfo_free_kb 281064".
 * Repeating data becomes a compact table; keys with a natural maximum (battery
 * level, storage) get a meter or a ring instead of a bare number.
 */
public final class SectionPage {

    private static final int MAX_INLINE_ROWS = 25;

    /**
     * How many rows in one card may carry an explanation.
     *
     * Zero would hide the meaning of unfamiliar fields; every row would double
     * the card's height and destroy the even row rhythm. Three covers the
     * genuinely obscure keys on any one card without turning the page into
     * prose.
     */
    private static final int MAX_HINTS_PER_CARD = 3;

    /** A tappable entry point into an interactive tool or test screen. */
    public static final class Action {
        public final String label;
        public final Class<?> activity;

        public Action(String label, Class<?> activity) {
            this.label = label;
            this.activity = activity;
        }
    }

    private SectionPage() {
    }

    public static ScrollView build(Context ctx, Snapshot snap, String id) {
        return build(ctx, snap, id, new ArrayList<Action>());
    }

    public static ScrollView build(Context ctx, Snapshot snap, String id,
                                   List<Action> actions) {
        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(false);
        // See Dashboard: focusable selectable rows otherwise pull the scroll
        // position down on attach.
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(ctx, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        appendSection(ctx, snap, id, actions, root);
        return scroll;
    }

    /**
     * Render one section's cards into an existing column.
     *
     * A tab can hold more than one collector (Perangkat = device + id, CPU =
     * cpu + gpu), so the cards have to go into a container the caller owns
     * rather than a fresh ScrollView. Returns false when the section had no
     * data at all, so the caller can skip a heading it would otherwise leave
     * dangling over nothing.
     */
    public static boolean appendSection(Context ctx, Snapshot snap, String id,
                                        List<Action> actions, LinearLayout root) {
        Map<String, Object> values = snap.section(id);
        if (values == null || values.isEmpty()) {
            return false;
        }

        // A headline figure first, when the section has one worth leading with.
        View head = highlightCard(ctx, snap, id, values);
        int index = 0;
        if (head != null) {
            root.addView(head);
            Motion.enter(head, index++);
        }

        // Before the data: say what is still blocked and offer the remedy in
        // the same place. Reading a screen full of dashes and then hunting for
        // why is the thing this card exists to prevent.
        View permits = permissionCard(ctx, id);
        if (permits != null) {
            root.addView(permits);
            Motion.enter(permits, index++);
        }

        // Everything else, grouped: scalars in one card, lists in their own.
        List<Map.Entry<String, Object>> scalars = new ArrayList<>();
        List<Map.Entry<String, Object>> lists = new ArrayList<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (isHighlightKey(id, e.getKey())) {
                continue;
            }
            // A nested map is not a scalar row: printing it would dump the raw
            // "{nfc=false, ...}" text. The device capability grid renders its
            // own map; any other map is flattened into ordinary rows so its
            // keys are still readable.
            if (e.getValue() instanceof Map) {
                if ("capabilities".equals(e.getKey())) {
                    continue;
                }
                Map<?, ?> nested = (Map<?, ?>) e.getValue();
                for (Map.Entry<?, ?> inner : nested.entrySet()) {
                    scalars.add(new java.util.AbstractMap.SimpleEntry<>(
                            String.valueOf(inner.getKey()), inner.getValue()));
                }
                continue;
            }
            if (e.getValue() instanceof List) {
                // An empty list produced a card reading "Kosong." under a
                // count of 0, which looks like a failure. Skip it: the warning
                // card already explains when a read was blocked.
                if (((List<?>) e.getValue()).isEmpty()) {
                    continue;
                }
                lists.add(e);
            } else {
                scalars.add(e);
            }
        }

        if (!scalars.isEmpty()) {
            LinearLayout card = Cards.card(ctx, Palette.accent(id));
            String title = snap.title(id);
            // No count badge: DevInfo's headers carry the icon and the title
            // only, and a lone number in the corner of a settings-style card
            // reads as a second, unexplained datum.
            card.addView(Cards.header(ctx, id, title, null));

            // Hints are shown for the first few rows only. Every row carrying
            // an explanation turned a 12-row card into a 24-line wall and broke
            // the even rhythm that makes the value column scannable; the rows
            // that need explaining most are the unfamiliar ones, which sit at
            // the top on a vendor ROM.
            int hintsShown = 0;
            for (Map.Entry<String, Object> e : scalars) {
                FieldMeta.Meta meta = FieldMeta.of(e.getKey());
                String hint = hintsShown < MAX_HINTS_PER_CARD ? meta.hint : null;
                if (hint != null && !hint.isEmpty()) {
                    hintsShown++;
                }
                card.addView(Cards.row(ctx, meta.label,
                        formatValue(e.getKey(), e.getValue()), hint,
                        valueTint(e.getKey(), e.getValue())));
            }
            root.addView(card);
            Motion.enter(card, index++);
        }

        for (Map.Entry<String, Object> e : lists) {
            View card = tableCard(ctx, id, e.getKey(), (List<?>) e.getValue());
            root.addView(card);
            Motion.enter(card, index++);
        }

        if (!actions.isEmpty()) {
            root.addView(toolsCard(ctx, actions));
            Motion.enter(root.getChildAt(root.getChildCount() - 1), index++);
        }

        List<String> mine = new ArrayList<>();
        String prefix = id + ": ";
        for (String w : snap.warnings()) {
            if (w.startsWith(prefix)) {
                mine.add(w.substring(prefix.length()));
            }
        }
        if (!mine.isEmpty()) {
            LinearLayout card = Cards.card(ctx, Palette.accent("battery"));
            card.addView(Cards.header(ctx, "battery", "Catatan", null));
            for (String w : mine) {
                card.addView(Cards.warning(ctx, w));
            }
            root.addView(card);
            Motion.enter(card, index);
        }

        return true;
    }

    // ------------------------------------------------------ permissions

    /**
     * The "you are missing one grant" card, or null when nothing is missing.
     *
     * Split by cause on purpose: a runtime dialog fixes READ_PHONE_STATE, but
     * no dialog can turn GPS on or grant usage access — those open a settings
     * screen. Telling them apart is the difference between a fixable screen and
     * a mystery.
     */
    private static View permissionCard(Context ctx, String id) {
        String[] perms = Perms.runtime(id);
        if (perms.length > 0 && !Perms.allGranted(ctx, perms)) {
            LinearLayout card = Cards.card(ctx, Palette.accent(id));
            card.addView(Cards.header(ctx, id, "Izin diperlukan", null));
            card.addView(Cards.hint(ctx, cardReason(id)));
            card.addView(Cards.body(ctx, "Izinkan " + Perms.describe(perms)
                    + " agar baris di bawah terisi.", Palette.dim(ctx)));
            TextView button = Cards.actionButton(ctx, "Beri izin "
                    + Perms.describe(perms));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Cards.dp(ctx, 10);
            button.setLayoutParams(lp);
            Motion.attachPress(button);
            button.setOnClickListener(v -> {
                if (ctx instanceof Activity) {
                    ((Activity) ctx).requestPermissions(perms, Perms.REQUEST_RUNTIME);
                }
            });
            card.addView(button);
            return card;
        }

        // Granted, yet still blank: the OS toggle is the remaining obstacle.
        if ("network".equals(id) && perms.length > 0 && !Perms.locationEnabled(ctx)) {
            return settingsCard(ctx, id, "Lokasi perangkat mati",
                    "Izin lokasi sudah diberikan, tetapi lokasi tingkat sistem "
                            + "dimatikan. SSID hanya terbaca saat lokasi aktif.",
                    "Buka pengaturan lokasi", Perms.locationSettings());
        }

        if ("storage".equals(id) && !Perms.usageAccess(ctx)) {
            return settingsCard(ctx, id, "Akses penggunaan belum aktif",
                    "Rincian aplikasi vs sistem dihitung dari StorageStats, yang "
                            + "hanya terbaca setelah akses penggunaan diberikan.",
                    "Buka pengaturan akses penggunaan", Perms.usageAccessSettings(ctx));
        }

        return null;
    }

    private static String cardReason(String id) {
        if ("sim".equals(id)) {
            return "Tanpa izin Telepon, operator, kode negara, dan daftar SIM "
                    + "tidak terbaca.";
        }
        if ("network".equals(id)) {
            return "Tanpa izin Lokasi, SSID Wi-Fi disamarkan oleh Android 10+.";
        }
        return "Sebagian data pada bagian ini memerlukan izin tambahan.";
    }

    private static LinearLayout settingsCard(Context ctx, String id, String title,
                                             String reason, String buttonLabel,
                                             Intent target) {
        LinearLayout card = Cards.card(ctx, Palette.accent(id));
        card.addView(Cards.header(ctx, id, title, null));
        card.addView(Cards.hint(ctx, reason));
        TextView button = Cards.actionButton(ctx, buttonLabel);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Cards.dp(ctx, 10);
        button.setLayoutParams(lp);
        Motion.attachPress(button);
        button.setOnClickListener(v -> {
            try {
                ctx.startActivity(target);
            } catch (Throwable ignored) {
                // a vendor ROM without that settings activity: nothing to open
            }
        });
        card.addView(button);
        return card;
    }

    // ------------------------------------------------------- highlight card

    private static boolean isHighlightKey(String id, String key) {
        if ("battery".equals(id)) {
            return key.equals("level") || key.equals("temperature_c")
                    || key.equals("health") || key.equals("status");
        }
        if ("storage".equals(id)) {
            return key.equals("internal_free_pct") || key.equals("internal_total_kb")
                    || key.equals("internal_free_kb");
        }
        if ("memory".equals(id)) {
            return key.equals("used_pct") || key.equals("total_kb")
                    || key.equals("available_kb");
        }
        return false;
    }

    /** Ring + two supporting facts, for the sections people open first. */
    private static View highlightCard(Context ctx, Snapshot snap, String id,
                                      Map<String, Object> values) {
        if ("battery".equals(id)) {
            int level = (int) Math.round(num(values, "level", -1));
            if (level < 0) {
                return null;
            }
            LinearLayout card = Cards.card(ctx, Palette.accent(id));
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            RingView ring = new RingView(ctx, Dashboard.batteryColor(level), 108);
            row.addView(ring);
            ring.animateTo(level / 100f, level + "%");

            LinearLayout side = new LinearLayout(ctx);
            side.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            slp.setMarginStart(Cards.dp(ctx, 18));
            side.setLayoutParams(slp);

            side.addView(factLine(ctx, "Status", text(values, "status",
                    "tidak diketahui")));
            double temp = num(values, "temperature_c", -1);
            if (temp > 0) {
                side.addView(factLine(ctx, "Suhu",
                        String.format(Locale.US, "%.1f °C", temp),
                        temp >= 45 ? Palette.CRITICAL
                                : temp >= 40 ? Palette.WARN : Palette.OK));
            }
            side.addView(factLine(ctx, "Kesehatan", text(values, "health", "?")));
            row.addView(side);
            card.addView(row);

            double wear = num(values, "wear_pct", -1);
            if (wear > 0) {
                card.addView(Cards.divider(ctx));
                card.addView(Cards.row(ctx, "Keausan baterai",
                        String.format(Locale.US, "%.0f%%", wear),
                        "Kapasitas sekarang dibanding desain pabrik."));
            }
            return card;
        }

        if ("storage".equals(id)) {
            double freePct = num(values, "internal_free_pct", -1);
            long total = (long) num(values, "internal_total_kb", 0);
            long free = (long) num(values, "internal_free_kb", 0);
            long apps = (long) num(values, "segment_apps_kb", -1);
            long system = (long) num(values, "segment_system_kb", -1);
            if (freePct < 0 || total <= 0) {
                return null;
            }
            LinearLayout card = Cards.card(ctx, Palette.accent(id));
            card.addView(Cards.header(ctx, id, "Penyimpanan internal", null));

            // A large percentage is the one number people look for; DevInfo
            // sets it at display size and everything else smaller.
            card.addView(bigNumber(ctx, String.format(Locale.US, "%.1f%%", 100 - freePct),
                    freePct < 10 ? Palette.CRITICAL
                            : freePct < 25 ? Palette.WARN : Palette.brand()));
            card.addView(Cards.hint(ctx, Dashboard.gb(total - free) + " / "
                    + Dashboard.gb(total) + "  ·  tersedia " + Dashboard.gb(free)));

            SegmentedBarView bar = new SegmentedBarView(ctx, 10f);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(ctx, 10));
            blp.topMargin = Cards.dp(ctx, 10);
            blp.bottomMargin = Cards.dp(ctx, 10);
            bar.setLayoutParams(blp);
            card.addView(bar);

            List<SegmentedBarView.Slice> slices = new ArrayList<>();
            if (apps > 0 && system > 0) {
                slices.add(new SegmentedBarView.Slice("apps", apps / (float) total,
                        Palette.brand()));
                slices.add(new SegmentedBarView.Slice("system", system / (float) total,
                        Palette.WARN));
                slices.add(new SegmentedBarView.Slice("free", free / (float) total,
                        Palette.faded(ctx, Palette.brand())));
            } else {
                slices.add(new SegmentedBarView.Slice("used", (total - free) / (float) total,
                        Palette.brand()));
                slices.add(new SegmentedBarView.Slice("free", free / (float) total,
                        Palette.faded(ctx, Palette.brand())));
            }
            bar.setSlices(slices, true);
            return card;
        }

        if ("memory".equals(id)) {
            double usedPct = num(values, "used_pct", -1);
            long total = (long) num(values, "total_kb", 0);
            long available = (long) num(values, "available_kb", 0);
            if (usedPct < 0 || total <= 0) {
                return null;
            }
            LinearLayout card = Cards.card(ctx, Palette.accent(id));
            card.addView(Cards.header(ctx, id, "Memori", Dashboard.gb(total)));

            card.addView(bigNumber(ctx, String.format(Locale.US, "%.0f%%", usedPct),
                    usedPct > 90 ? Palette.CRITICAL
                            : usedPct > 75 ? Palette.WARN : Palette.SAGE));
            card.addView(Cards.hint(ctx,
                    "Tersedia " + Dashboard.gb(available) + " untuk app baru."));

            MeterView meter = new MeterView(ctx,
                    usedPct > 90 ? Palette.CRITICAL
                            : usedPct > 75 ? Palette.WARN : Palette.SAGE, 12f);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(ctx, 12));
            mlp.topMargin = Cards.dp(ctx, 10);
            card.addView(meter, mlp);
            meter.animateTo((float) (usedPct / 100));
            return card;
        }
        return null;
    }

    /** A percentage at display size, with a small percent sign. */
    private static LinearLayout bigNumber(Context ctx, String text, int color) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(0, Cards.dp(ctx, 6), 0, Cards.dp(ctx, 2));

        String digits = text.endsWith("%") ? text.substring(0, text.length() - 1) : text;
        TextView big = new TextView(ctx);
        big.setText(digits);
        big.setTextSize(40f);
        big.setTextColor(color);
        big.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        wrap.addView(big);

        if (text.endsWith("%")) {
            TextView pct = new TextView(ctx);
            pct.setText("%");
            pct.setTextSize(19f);
            pct.setTextColor(color);
            pct.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(Cards.dp(ctx, 2));
            lp.bottomMargin = Cards.dp(ctx, 5);
            pct.setLayoutParams(lp);
            wrap.addView(pct);
        }
        return wrap;
    }

    private static LinearLayout factLine(Context ctx, String label, String value) {
        return factLine(ctx, label, value, null);
    }

    private static LinearLayout factLine(Context ctx, String label, String value,
                                         Integer color) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Cards.dp(ctx, 3), 0, Cards.dp(ctx, 3));

        TextView key = new TextView(ctx);
        key.setText(label);
        key.setTextSize(12f);
        key.setTextColor(Palette.dim(ctx));
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.45f);
        key.setLayoutParams(klp);
        row.addView(key);

        TextView val = new TextView(ctx);
        val.setText(value);
        val.setTextSize(13f);
        val.setTextColor(color != null ? color : Palette.text(ctx));
        val.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        row.addView(val);
        return row;
    }

    // ------------------------------------------------------------- tables

    private static View tableCard(Context ctx, String sectionId, String key, List<?> rows) {
        LinearLayout card = Cards.card(ctx, Palette.accent(sectionId));
        FieldMeta.Meta meta = FieldMeta.of(key);
        // Header without a count badge, matching the scalar cards above: the
        // row count is visible from the list itself and a bare number in the
        // corner reads as data rather than metadata.
        card.addView(Cards.header(ctx, sectionId, meta.label, null));
        if (meta.hint != null) {
            card.addView(Cards.hint(ctx, meta.hint));
        }
        if (rows.isEmpty()) {
            card.addView(Cards.body(ctx, "Kosong.", Palette.faint(ctx)));
            return card;
        }

        // The installed-app rows carry a dozen fields each. Joining them into
        // one monospace paragraph made every entry an unreadable run-on, so this
        // block renders name + version first and the rest as compact pairs.
        boolean pairs = "installed".equals(key);

        int limit = Math.min(rows.size(), MAX_INLINE_ROWS);
        for (int i = 0; i < limit; i++) {
            Object row = rows.get(i);
            if (row instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) row;
                String title = firstNonNull(map, "name", "package", "description",
                        "carrier", "core", "id");
                if (title != null) {
                    LinearLayout headRow = new LinearLayout(ctx);
                    headRow.setOrientation(LinearLayout.HORIZONTAL);
                    headRow.setGravity(Gravity.CENTER_VERTICAL);
                    headRow.setPadding(0, Cards.dp(ctx, 9), 0, 0);

                    // App rows carry the package in `name`; other sections do
                    // not name packages and get no icon, which keeps sensors and
                    // codecs from all showing the same generic placeholder.
                    android.graphics.drawable.Drawable icon =
                            AppIcons.get(ctx, packageOf(map));
                    if (icon != null) {
                        android.widget.ImageView image = new android.widget.ImageView(ctx);
                        int size = Cards.dp(ctx, 26);
                        LinearLayout.LayoutParams ilp =
                                new LinearLayout.LayoutParams(size, size);
                        ilp.setMarginEnd(Cards.dp(ctx, 9));
                        image.setLayoutParams(ilp);
                        image.setImageDrawable(icon);
                        image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                        headRow.addView(image);
                    }

                    TextView head = new TextView(ctx);
                    head.setText(String.valueOf(title));
                    head.setTextSize(13f);
                    head.setTextColor(Palette.text(ctx));
                    head.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    head.setTextIsSelectable(true);
                    headRow.addView(head);
                    card.addView(headRow);
                }
                if (pairs) {
                    addSummaryPairs(ctx, card, map);
                } else {
                    TextView line = new TextView(ctx);
                    line.setText(formatRow(map));
                    line.setTextSize(12f);
                    line.setTextColor(Palette.dim(ctx));
                    line.setTypeface(Typeface.MONOSPACE);
                    line.setPadding(0, Cards.dp(ctx, 5), 0, Cards.dp(ctx, 2));
                    line.setTextIsSelectable(true);
                    card.addView(line);
                }
                if (i < limit - 1) {
                    card.addView(Cards.divider(ctx));
                }
            } else {
                card.addView(Cards.row(ctx, "#" + (i + 1), String.valueOf(row)));
            }
        }
        if (rows.size() > limit) {
            TextView more = Cards.actionButton(ctx,
                    "Lihat semua " + rows.size() + " baris");
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            mlp.topMargin = Cards.dp(ctx, 10);
            more.setLayoutParams(mlp);
            Motion.attachPress(more);
            more.setOnClickListener(v -> ctx.startActivity(
                    ListActivity.intent(ctx, sectionId, key, meta.label)));
            card.addView(more);
            card.addView(Cards.hint(ctx, "+" + (rows.size() - limit)
                    + " baris tidak dimuat di sini, buka daftar lengkap"));
        }
        return card;
    }

    /**
     * The package name to load an icon for, if this row names an app.
     *
     * AppsSection stores the package in `name` and emits no separate `package`
     * key, so both are checked and the value is shape-validated: a sensor row
     * whose `name` happens to be a word must not be sent to PackageManager.
     */
    private static String packageOf(Map<?, ?> row) {
        Object raw = row.get("package");
        if (raw == null) {
            raw = row.get("name");
        }
        if (raw == null) {
            return null;
        }
        String candidate = String.valueOf(raw).trim();
        if (!candidate.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
            return null;
        }
        return candidate;
    }

    /**
     * Compact two-column summary for one list entry.
     *
     * Ordered by what a reader scanning an app list actually compares: version,
     * size, then where it came from. Fields that are the same for almost every
     * row (system flag, enabled, ABI, data dir) go into one faint trailing line
     * instead of four rows repeated under each app.
     */
    private static void addSummaryPairs(Context ctx, LinearLayout card, Map<?, ?> map) {
        String[][] ordered = {
                {"version", "Versi"},
                {"apk_size_kb", "Ukuran APK"},
                {"installer", "Sumber install"},
                {"target_sdk", "Target SDK"},
        };
        for (String[] spec : ordered) {
            Object v = map.get(spec[0]);
            if (v == null || String.valueOf(v).isEmpty()) {
                continue;
            }
            String value = formatValue(spec[0], v);
            // "0 MB" is what an unreadable system APK path produces; a dash is
            // honest about not knowing instead of claiming an empty file.
            if ("apk_size_kb".equals(spec[0]) && v instanceof Number
                    && ((Number) v).longValue() <= 0) {
                value = null;
            }
            if (value != null) {
                card.addView(compactPair(ctx, spec[1], value, Palette.text(ctx)));
            }
        }

        StringBuilder tail = new StringBuilder();
        appendIf(tail, map, "sideloaded", "Di luar Play Store");
        appendIf(tail, map, "min_sdk", "Min SDK");
        appendIf(tail, map, "native_libs", "ABI");

        if (map.get("debuggable") instanceof Boolean && (Boolean) map.get("debuggable")) {
            append(tail, "Debuggable");
        }
        if (tail.length() > 0) {
            TextView faint = Cards.hint(ctx, tail.toString());
            faint.setPadding(0, Cards.dp(ctx, 3), 0, Cards.dp(ctx, 1));
            card.addView(faint);
        }
    }

    private static void appendIf(StringBuilder sb, Map<?, ?> map, String key, String label) {
        Object v = map.get(key);
        if (v == null || String.valueOf(v).isEmpty()) {
            return;
        }
        String value = formatValue(key, v);
        if (value == null) {
            return;
        }
        append(sb, label + " " + value);
    }

    private static void append(StringBuilder sb, String text) {
        if (sb.length() > 0) {
            sb.append("  \u00b7  ");
        }
        sb.append(text);
    }

    private static LinearLayout compactPair(Context ctx, String label, String value,
                                            int color) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Cards.dp(ctx, 3), 0, Cards.dp(ctx, 3));

        TextView key = new TextView(ctx);
        key.setText(label);
        key.setTextSize(12f);
        key.setTextColor(Palette.faint(ctx));
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f);
        key.setLayoutParams(klp);
        row.addView(key);

        TextView val = new TextView(ctx);
        val.setText(value);
        val.setTextSize(12f);
        val.setTextColor(color);
        val.setTextIsSelectable(true);
        row.addView(val);
        return row;
    }

    /** "720 x 1600 · 60 Hz" style summary, skipping the fields already shown. */
    private static String formatRow(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> e : map.entrySet()) {
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
            sb.append(FieldMeta.of(k).label).append(' ').append(formatValue(k, v));
        }
        return sb.length() == 0 ? "—" : sb.toString();
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

    // -------------------------------------------------------------- tools

    private static LinearLayout toolsCard(Context ctx, List<Action> actions) {
        LinearLayout card = Cards.card(ctx, Palette.accent("device"));
        card.addView(Cards.header(ctx, "device", "Alat & tes", null));
        for (Action action : actions) {
            TextView button = Cards.actionButton(ctx, action.label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Cards.dp(ctx, 8);
            button.setLayoutParams(lp);
            Motion.attachPress(button);
            button.setOnClickListener(v -> ctx.startActivity(
                    new android.content.Intent(ctx, action.activity)));
            card.addView(button);
        }
        return card;
    }

    // ---------------------------------------------------------- formatting

    public static String formatValue(String key, Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? "Ya" : "Tidak";
        }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (key.endsWith("_kb")) {
                return Dashboard.gb((long) d);
            }
            if (key.endsWith("_pct")) {
                return String.format(Locale.US, "%.1f%%", d);
            }
            if (key.endsWith("_c")) {
                return String.format(Locale.US, "%.1f °C", d);
            }
            if (key.endsWith("_hz")) {
                return String.format(Locale.US, "%.0f Hz", d);
            }
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                return String.format(Locale.US, "%.0f", d);
            }
            return String.format(Locale.US, "%.2f", d);
        }
        if (v instanceof Number) {
            long n = ((Number) v).longValue();
            if (key.endsWith("_kb")) {
                return Dashboard.gb(n);
            }
            if (key.endsWith("_mb")) {
                return n + " MB";
            }
            if (key.endsWith("_uah")) {
                // Micro-amp-hours: 5000000 uAh means nothing to a reader, and
                // the raw number ran the column wide.
                return String.format(Locale.US, "%.0f mAh", n / 1000.0);
            }
            if (key.endsWith("_ua")) {
                return String.format(Locale.US, "%d mA", Math.round(n / 1000.0));
            }
            if (key.endsWith("_mv")) {
                return String.format(Locale.US, "%.2f V", n / 1000.0);
            }
            if (key.endsWith("_ms") && n >= 1000) {
                return (n / 1000) + " detik";
            }
            if (key.endsWith("_pct")) {
                return n + "%";
            }
            return String.valueOf(n);
        }
        String s = String.valueOf(v);
        return s.isEmpty() ? null : s;
    }

    /** Colour a value when it carries a judgement the user should see. */
    static Integer valueTint(String key, Object v) {
        if (!(v instanceof Number)) {
            return null;
        }
        double d = ((Number) v).doubleValue();
        if (key.equals("temperature_c")) {
            return d >= 45 ? Palette.CRITICAL : d >= 40 ? Palette.WARN : null;
        }
        if (key.equals("swap_used_pct")) {
            return d >= 60 ? Palette.WARN : null;
        }
        if (key.equals("internal_free_pct")) {
            return d < 10 ? Palette.CRITICAL : d < 25 ? Palette.WARN : null;
        }
        return null;
    }

    static double num(Map<String, Object> map, String key, double fallback) {
        if (map == null) {
            return fallback;
        }
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : fallback;
    }

    static String text(Map<String, Object> map, String key, String fallback) {
        if (map == null) {
            return fallback;
        }
        Object v = map.get(key);
        return v == null || String.valueOf(v).isEmpty() ? fallback : String.valueOf(v);
    }
}
