package ai.cekdroid.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ai.cekdroid.collect.Snapshot;

/**
 * The landing screen.
 *
 * DevInfo opens on a dashboard rather than a tab strip, and that is the single
 * biggest reason it feels easy: three numbers answer "how is my phone" before
 * any list appears. Everything here is derived from the same Snapshot the other
 * screens use, so nothing is collected twice.
 */
public final class Dashboard {

    private Dashboard() {
    }

    public static ScrollView build(Context ctx, Snapshot snap) {
        ScrollView scroll = new ScrollView(ctx);
        // Rows use setTextIsSelectable(true). A selectable TextView is
        // focusable, and the ScrollView scrolled itself down to it on attach,
        // so the screen opened halfway through instead of at the hero card.
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(ctx, 16);
        root.setPadding(pad, 0, pad, pad);
        scroll.addView(root);

        int index = 0;

        // ---------------------------------------------------------- hero
        LinearLayout hero = Cards.card(ctx);
        hero.setPadding(Cards.dp(ctx, 18), Cards.dp(ctx, 18),
                Cards.dp(ctx, 18), Cards.dp(ctx, 18));

        Map<String, Object> device = snap.section("device");
        String model = str(device, "model", "Perangkat");
        String brand = str(device, "manufacturer", "");
        // Marketing name ("OPPO A16") is what the owner recognises; the factory
        // code ("CPH2269") goes underneath as a secondary line.
        String marketing = str(device, "marketing_name", null);
        String android = str(device, "android", "?");
        String codename = str(device, "codename", "");
        String patch = str(device, "security_patch", "?");

        TextView title = new TextView(ctx);
        title.setText(marketing != null ? marketing : (brand + " " + model).trim());
        title.setTextSize(24f);
        title.setTextColor(Palette.text(ctx));
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        hero.addView(title);

        TextView sub = new TextView(ctx);
        StringBuilder subText = new StringBuilder();
        if (marketing != null) {
            subText.append("Model ").append(model).append("  ·  ");
        }
        subText.append("Android ").append(android);
        if (!codename.isEmpty() && !"REL".equals(codename)) {
            subText.append(" ").append(codename);
        }
        subText.append("  ·  patch ").append(patch);
        sub.setText(subText.toString());
        sub.setTextSize(12.5f);
        sub.setTextColor(Palette.dim(ctx));
        sub.setPadding(0, Cards.dp(ctx, 4), 0, Cards.dp(ctx, 10));
        hero.addView(sub);

        String uptime = uptimeLine(snap);
        if (uptime != null) {
            TextView up = new TextView(ctx);
            up.setText(uptime);
            up.setTextSize(11.5f);
            up.setTextColor(Palette.faint(ctx));
            up.setPadding(0, 0, 0, Cards.dp(ctx, 14));
            hero.addView(up);
        }

        LinearLayout rings = new LinearLayout(ctx);
        rings.setOrientation(LinearLayout.HORIZONTAL);
        rings.setGravity(Gravity.CENTER);

        Map<String, Object> battery = snap.section("battery");
        Map<String, Object> memory = snap.section("memory");
        Map<String, Object> storage = snap.section("storage");

        // Two rings, not three: three 92dp rings plus margins overflow a 360dp
        // screen and the last one gets clipped.
        int batteryLevel = integer(battery, "level", -1);
        if (batteryLevel >= 0) {
            RingView ring = new RingView(ctx, batteryColor(batteryLevel), 88);
            ring.setCaption("Baterai");
            rings.addView(ring);
            ring.animateTo(batteryLevel / 100f, batteryLevel + "%");
        }

        double ramUsed = number(memory, "used_pct", -1);
        if (ramUsed >= 0) {
            RingView ring = new RingView(ctx, Palette.SAGE, 88);
            // Keep the fixed size RingView set for itself; only add a margin.
            // Overriding with WRAP_CONTENT collapses a custom View to 0x0.
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(Cards.dp(ctx, 20));
            ring.setLayoutParams(lp);
            ring.setCaption("RAM");
            rings.addView(ring);
            ring.animateTo((float) (ramUsed / 100f), Math.round(ramUsed) + "%");
        }
        hero.addView(rings);

        // Storage gets a bar instead of a third ring: it reads better as a
        // proportion of a fixed whole. Three segments when the app-data split
        // is available, one solid bar otherwise.
        double storageFree = number(storage, "internal_free_pct", -1);
        long storageTotal = (long) number(storage, "internal_total_kb", 0);
        long storageFreeKb = (long) number(storage, "internal_free_kb", 0);
        if (storageFree >= 0) {
            long appsKb = (long) number(storage, "segment_apps_kb", -1);
            long systemKb = (long) number(storage, "segment_system_kb", -1);

            LinearLayout storageRow = new LinearLayout(ctx);
            storageRow.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Cards.dp(ctx, 16);
            storageRow.setLayoutParams(slp);

            LinearLayout caption = new LinearLayout(ctx);
            caption.setOrientation(LinearLayout.HORIZONTAL);

            TextView label = new TextView(ctx);
            label.setText("Penyimpanan");
            label.setTextSize(12f);
            label.setTextColor(Palette.dim(ctx));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            label.setLayoutParams(llp);
            caption.addView(label);

            TextView value = new TextView(ctx);
            value.setText(String.format(java.util.Locale.US, "%.1f%% terpakai",
                    100 - storageFree));
            value.setTextSize(12f);
            value.setTextColor(Palette.text(ctx));
            value.setTypeface(Typeface.MONOSPACE);
            caption.addView(value);
            storageRow.addView(caption);

            SegmentedBarView bar = new SegmentedBarView(ctx, 9f);
            List<SegmentedBarView.Slice> slices = new ArrayList<>();
            if (appsKb > 0 && systemKb > 0 && storageTotal > 0) {
                slices.add(new SegmentedBarView.Slice("Aplikasi & data",
                        appsKb / (float) storageTotal, Palette.brand()));
                slices.add(new SegmentedBarView.Slice("Sistem",
                        systemKb / (float) storageTotal, Palette.WARN));
                slices.add(new SegmentedBarView.Slice("Tersedia",
                        storageFreeKb / (float) storageTotal,
                        Palette.faded(ctx, Palette.brand())));
            } else {
                slices.add(new SegmentedBarView.Slice("Terpakai",
                        (storageTotal - storageFreeKb) / (float) storageTotal, Palette.brand()));
                slices.add(new SegmentedBarView.Slice("Tersedia",
                        storageFreeKb / (float) storageTotal,
                        Palette.faded(ctx, Palette.brand())));
            }
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(ctx, 9));
            blp.topMargin = Cards.dp(ctx, 7);
            blp.bottomMargin = Cards.dp(ctx, 10);
            bar.setLayoutParams(blp);
            storageRow.addView(bar);
            bar.setSlices(slices, true);

            if (appsKb > 0 && systemKb > 0) {
                storageRow.addView(legendLine(ctx, Palette.brand(), "Aplikasi & data",
                        gb(appsKb), pctOf(appsKb, storageTotal)));
                storageRow.addView(legendLine(ctx, Palette.WARN, "Sistem & dicadangkan",
                        gb(systemKb), pctOf(systemKb, storageTotal)));
                storageRow.addView(legendLine(ctx, Palette.faded(ctx, Palette.brand()),
                        "Tersedia", gb(storageFreeKb), pctOf(storageFreeKb, storageTotal)));
            } else {
                TextView total = new TextView(ctx);
                total.setText(gb(storageTotal - storageFreeKb) + " / " + gb(storageTotal)
                        + "  ·  " + gb(storageFreeKb) + " tersedia");
                total.setTextSize(11.5f);
                total.setTextColor(Palette.dim(ctx));
                storageRow.addView(total);
            }

            hero.addView(storageRow);
        }
        root.addView(hero);
        Motion.enter(hero, index++);

        // ------------------------------------------------------- health score
        // First card after the hero. A shop counter reads this before anything
        // else: one number, one verdict, and the reasons that produced them.
        View health = healthCard(ctx, snap);
        if (health != null) {
            root.addView(health);
            Motion.enter(health, index++);
        }

        // ------------------------------------------------- capabilities
        // DevInfo keeps the capability grid on its Perangkat tab, not the
        // dashboard: the dashboard answers "how is my phone right now", and a
        // check/cross table answers "what can this phone do at all". The grid
        // now lives in the Perangkat tab (see TabPage) so the two screens do
        // not repeat each other.

        // --------------------------------------------------------- cpu
        View cpuPanel = cpuCard(ctx, snap);
        if (cpuPanel != null) {
            root.addView(cpuPanel);
            Motion.enter(cpuPanel, index++);
        }

        // ------------------------------------------------------ memory
        View memoryPanel = memoryCard(ctx, snap);
        if (memoryPanel != null) {
            root.addView(memoryPanel);
            Motion.enter(memoryPanel, index++);
        }

        // --------------------------------------------------- warnings
        List<String> warnings = snap.warnings();
        if (!warnings.isEmpty()) {
            LinearLayout card = Cards.card(ctx, Palette.accent("battery"));
            card.addView(Cards.header(ctx, "battery", "Catatan keterbatasan", null));
            card.addView(Cards.hint(ctx,
                    "Data berikut tidak bisa dibaca app biasa. Ini batasan Android, bukan kerusakan."));
            for (int i = 0; i < Math.min(warnings.size(), 8); i++) {
                card.addView(Cards.warning(ctx, warnings.get(i)));
            }
            if (warnings.size() > 8) {
                card.addView(Cards.hint(ctx, "+" + (warnings.size() - 8) + " catatan lain"));
            }
            root.addView(card);
            Motion.enter(card, index++);
        }

        return scroll;
    }

    /**
     * CPU: SoC name, governor, and a per-core occupancy grid.
     *
     * DevInfo leads its dashboard with the chip and the clock of every core,
     * which answers "is my phone busy" instantly. The grid reuses the
     * frequencies block CpuSection already collects, so nothing is read twice.
     */
    private static LinearLayout cpuCard(Context ctx, Snapshot snap) {
        Map<String, Object> cpu = snap.section("cpu");
        if (cpu == null || cpu.isEmpty()) {
            return null;
        }
        Object raw = cpu.get("frequencies");
        if (!(raw instanceof List) || ((List<?>) raw).isEmpty()) {
            return null;
        }
        List<Map<String, Object>> cores = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) item;
                cores.add(row);
            }
        }
        if (cores.isEmpty()) {
            return null;
        }

        // The most common maximum is the "big" cluster clock; showing it beside
        // the core count tells the reader what class of chip this is.
        long peak = 0;
        for (Map<String, Object> core : cores) {
            peak = Math.max(peak, (long) number(core, "max_mhz", 0));
        }

        String soc = str(cpu, "hardware_cpuinfo", null);
        if (soc == null) {
            soc = str(cpu, "hardware", null);
        }

        LinearLayout card = Cards.card(ctx, Palette.accent("cpu"));
        card.addView(Cards.header(ctx, "cpu", "Prosesor",
                cores.size() + " core" + (peak > 0 ? " \u00b7 " + peak + " MHz" : "")));

        if (soc != null) {
            card.addView(Cards.body(ctx, soc, Palette.text(ctx)));
        }

        // The cluster line and the core range are what DevInfo prints directly
        // under the chip name: "Frekuensi: 400MHz - 2301MHz" plus one line per
        // cluster. They come from the same cpufreq sampling as the grid below.
        String range = str(cpu, "frequency_range_text", null);
        if (range != null) {
            TextView rangeLine = Cards.hint(ctx, "Frekuensi: " + range);
            rangeLine.setTextColor(Palette.dim(ctx));
            rangeLine.setPadding(0, Cards.dp(ctx, 4), 0, Cards.dp(ctx, 2));
            card.addView(rangeLine);
        }

        Object rawClusters = cpu.get("clusters");
        if (rawClusters instanceof List) {
            for (Object item : (List<?>) rawClusters) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<?, ?> cluster = (Map<?, ?>) item;
                Object min = cluster.get("min_mhz");
                Object max = cluster.get("max_mhz");
                Object count = cluster.get("cores");
                if (min == null || max == null || count == null) {
                    continue;
                }
                card.addView(Cards.hint(ctx, count + " x " + min + "MHz - " + max + "MHz"));
            }
        }

        String coreName = str(cpu, "core_name", null);
        if (coreName != null) {
            card.addView(Cards.hint(ctx, "Struktur: " + coreName));
        }

        String governor = str(cpu, "governor", null);
        String online = str(cpu, "core_online", null);
        StringBuilder meta = new StringBuilder();
        if (governor != null) {
            meta.append("Governor ").append(governor);
        }
        if (online != null) {
            if (meta.length() > 0) {
                meta.append("  \u00b7  ");
            }
            meta.append(online).append(" core aktif");
        }
        if (meta.length() > 0) {
            TextView line = Cards.hint(ctx, meta.toString());
            line.setPadding(0, Cards.dp(ctx, 2), 0, Cards.dp(ctx, 8));
            card.addView(line);
        }

        CoreGridView grid = new CoreGridView(ctx, cores, Palette.accent("cpu"));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = Cards.dp(ctx, 2);
        grid.setLayoutParams(glp);
        card.addView(grid);

        card.addView(Cards.hint(ctx,
                "Angka di bawah tiap core adalah frekuensi saat ini dalam MHz. "
                        + "Tinggi isian menunjukkan seberapa dekat ke clock maksimum."));
        return card;
    }

    /**
     * The health verdict: score ring, verdict word, and the reasons.
     *
     * Weighted categories that had no readable data are left out of the score
     * and listed as "tidak dinilai" under the bars — a shop must be able to see
     * which parts of the judgement were actually measured, because a phone whose
     * battery capacity the kernel hides should not silently score 100.
     */
    static LinearLayout healthCard(Context ctx, Snapshot snap) {
        Health.Result result = Health.evaluate(snap);
        LinearLayout card = Cards.card(ctx, Palette.accent("device"));

        card.addView(Cards.header(ctx, "device", "Skor kesehatan",
                result.score < 0 ? null : result.score + "/100"));

        if (result.score < 0) {
            card.addView(Cards.body(ctx, result.headline, Palette.dim(ctx)));
            return card;
        }

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        RingView ring = new RingView(ctx, result.color(), 96);
        top.addView(ring);
        ring.animateTo(result.score / 100f, String.valueOf(result.score));

        LinearLayout side = new LinearLayout(ctx);
        side.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.setMarginStart(Cards.dp(ctx, 16));
        side.setLayoutParams(slp);

        TextView verdict = new TextView(ctx);
        verdict.setText(result.verdict);
        verdict.setTextSize(21f);
        verdict.setTextColor(result.color());
        verdict.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        side.addView(verdict);

        TextView headline = new TextView(ctx);
        headline.setText(result.headline);
        headline.setTextSize(12f);
        headline.setTextColor(Palette.dim(ctx));
        headline.setLineSpacing(0, 1.25f);
        headline.setPadding(0, Cards.dp(ctx, 4), 0, 0);
        side.addView(headline);
        top.addView(side);
        card.addView(top);

        // Per-category bars: this is what makes the number reviewable rather
        // than a black box the buyer has to trust.
        card.addView(Cards.heading(ctx, "Rincian"));
        for (Health.Category category : result.categories) {
            card.addView(categoryRow(ctx, category));
        }

        if (!result.reasons.isEmpty()) {
            card.addView(Cards.heading(ctx, "Alasan"));
            int shown = Math.min(result.reasons.size(), 6);
            for (int i = 0; i < shown; i++) {
                card.addView(Cards.warning(ctx, result.reasons.get(i)));
            }
            if (result.reasons.size() > shown) {
                card.addView(Cards.hint(ctx, "+" + (result.reasons.size() - shown)
                        + " alasan lain"));
            }
        }

        card.addView(Cards.hint(ctx,
                "Skor dihitung dari bagian yang terbaca saja. Bagian yang tidak "
                        + "terbaca tidak dinilai, bukan dianggap baik."));
        return card;
    }

    /** One category: name, a thin meter, and its score or "tidak dinilai". */
    private static LinearLayout categoryRow(Context ctx, Health.Category category) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, Cards.dp(ctx, 6), 0, Cards.dp(ctx, 6));

        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(ctx);
        name.setText(category.label);
        name.setTextSize(12.5f);
        name.setTextColor(Palette.dim(ctx));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(nlp);
        line.addView(name);

        TextView value = new TextView(ctx);
        value.setText(category.measured() ? category.score + "" : "—");
        value.setTextSize(12.5f);
        value.setTextColor(category.measured() ? scoreColor(category.score)
                : Palette.faint(ctx));
        value.setTypeface(Typeface.MONOSPACE);
        line.addView(value);
        row.addView(line);

        if (category.measured()) {
            MeterView meter = new MeterView(ctx, scoreColor(category.score), 6f);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(ctx, 6));
            mlp.topMargin = Cards.dp(ctx, 5);
            meter.setLayoutParams(mlp);
            row.addView(meter);
            meter.animateTo(category.score / 100f);
        }

        if (category.note != null && !category.note.isEmpty()) {
            TextView note = Cards.hint(ctx, category.note);
            note.setPadding(0, Cards.dp(ctx, 3), 0, 0);
            row.addView(note);
        }
        return row;
    }

    private static int scoreColor(int score) {
        if (score >= 85) {
            return Palette.OK;
        }
        if (score >= 65) {
            return Palette.WARN;
        }
        return Palette.CRITICAL;
    }

    /**
     * The capability grid: what this phone can actually do.
     *
     * Public because it now lives on the Perangkat tab rather than the
     * dashboard, matching DevInfo — the dashboard is a status snapshot, while a
     * capability table is reference material that belongs with the device
     * identity. Every row is a ✓ or ✗ because an absent capability ("NFC ✕")
     * is information too, and a check/cross list scans faster than chips.
     */
    public static LinearLayout capabilitiesCard(Context ctx) {
        LinearLayout card = Cards.card(ctx, Palette.accent("device"));
        card.addView(Cards.header(ctx, "device", "Kemampuan perangkat", null));

        PackageManager pm = ctx.getPackageManager();

        String[][] items = {
                {"NFC", PackageManager.FEATURE_NFC},
                {"Emulasi kartu NFC", PackageManager.FEATURE_NFC_HOST_CARD_EMULATION},
                {"Sidik jari", PackageManager.FEATURE_FINGERPRINT},
                {"Pengenalan wajah", "android.hardware.biometrics.face"},
                {"Pengenalan iris", "android.hardware.biometrics.iris"},
                {"Bluetooth", PackageManager.FEATURE_BLUETOOTH},
                {"Bluetooth LE", PackageManager.FEATURE_BLUETOOTH_LE},
                {"Pemancar inframerah", PackageManager.FEATURE_CONSUMER_IR},
                {"UWB", "android.hardware.uwb"},
                {"Wi-Fi", PackageManager.FEATURE_WIFI},
                {"Ethernet", PackageManager.FEATURE_ETHERNET},
                {"GPS/GNSS", PackageManager.FEATURE_LOCATION_GPS},
                {"Host USB", PackageManager.FEATURE_USB_HOST},
                {"Telepon", PackageManager.FEATURE_TELEPHONY},
                {"eSIM", "android.hardware.telephony.euicc"},
                {"Senter", PackageManager.FEATURE_CAMERA_FLASH},
                {"Kamera", PackageManager.FEATURE_CAMERA_ANY},
                {"Sensor cahaya", PackageManager.FEATURE_SENSOR_LIGHT},
        };

        // Two columns, filled row by row, which is how the list stays short.
        for (int i = 0; i < items.length; i += 2) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, Cards.dp(ctx, 5), 0, Cards.dp(ctx, 5));
            row.addView(capabilityCell(ctx, pm, items[i][0], items[i][1]));
            if (i + 1 < items.length) {
                row.addView(capabilityCell(ctx, pm, items[i + 1][0], items[i + 1][1]));
            }
            card.addView(row);
        }
        return card;
    }

    private static LinearLayout capabilityCell(Context ctx, PackageManager pm,
                                               String label, String feature) {
        boolean on = pm != null && pm.hasSystemFeature(feature);
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView mark = new TextView(ctx);
        mark.setText(on ? "✓" : "✕");
        mark.setTextSize(14f);
        mark.setTextColor(on ? Palette.OK : Palette.faint(ctx));
        mark.setWidth(Cards.dp(ctx, 22));
        cell.addView(mark);

        TextView text = new TextView(ctx);
        text.setText(label);
        text.setTextSize(12.5f);
        text.setTextColor(on ? Palette.text(ctx) : Palette.dim(ctx));
        cell.addView(text);
        return cell;
    }

    /** RAM split into Apps / Cache / System / Free, the way DevInfo explains it. */
    private static LinearLayout memoryCard(Context ctx, Snapshot snap) {
        Map<String, Object> memory = snap.section("memory");
        if (memory == null || memory.isEmpty()) {
            return null;
        }
        long total = (long) number(memory, "total_kb", 0);
        long available = (long) number(memory, "available_kb", 0);
        if (total <= 0) {
            return null;
        }

        // Prefer the five-way split the collector derived from /proc/meminfo;
        // fall back to the three-way estimate when those keys are absent.
        long appsKb = (long) number(memory, "seg_apps_kb", -1);
        long cacheKb = (long) number(memory, "seg_cache_kb", -1);
        long systemKb = (long) number(memory, "seg_system_kb", -1);
        long freeKb = (long) number(memory, "seg_free_kb", -1);
        boolean segmented = appsKb > 0 && cacheKb > 0 && freeKb > 0;

        if (!segmented) {
            long cached = (long) number(memory, "meminfo_cached_kb", 0);
            cacheKb = Math.min(cached, available);
            appsKb = Math.max(0, total - available - cacheKb);
            freeKb = Math.max(0, available - cacheKb);
            systemKb = 0;
        }

        double usedPct = number(memory, "used_pct", -1);

        LinearLayout card = Cards.card(ctx, Palette.accent("memory"));
        card.addView(Cards.header(ctx, "memory", "Memori", gb(total)));

        List<SegmentedBarView.Slice> slices = new ArrayList<>();
        if (appsKb > 0) {
            slices.add(new SegmentedBarView.Slice("Aplikasi", appsKb / (float) total,
                    Palette.brand()));
        }
        if (cacheKb > 0) {
            slices.add(new SegmentedBarView.Slice("Cache", cacheKb / (float) total,
                    Palette.SAGE));
        }
        if (systemKb > 0) {
            slices.add(new SegmentedBarView.Slice("Sistem", systemKb / (float) total,
                    Palette.WARN));
        }
        long otherKb = Math.max(0, total - appsKb - cacheKb - systemKb - freeKb);
        if (otherKb > 0) {
            slices.add(new SegmentedBarView.Slice("Lainnya", otherKb / (float) total,
                    0xFFA78BFA));
        }
        if (freeKb > 0) {
            slices.add(new SegmentedBarView.Slice("Bebas", freeKb / (float) total,
                    Palette.faded(ctx, Palette.SAGE)));
        }

        SegmentedBarView bar = new SegmentedBarView(ctx, 11f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Cards.dp(ctx, 11));
        blp.topMargin = Cards.dp(ctx, 4);
        blp.bottomMargin = Cards.dp(ctx, 12);
        bar.setLayoutParams(blp);
        card.addView(bar);
        bar.setSlices(slices, true);

        if (usedPct >= 0) {
            // Integer, not int: a ternary mixing an int constant and null is
            // typed as int, so Java unboxes the null arm and throws NPE on
            // every normal reading.
            Integer tint = usedPct > 90 ? Integer.valueOf(Palette.CRITICAL)
                    : usedPct > 75 ? Integer.valueOf(Palette.WARN) : null;
            card.addView(Cards.row(ctx, "Terpakai",
                    String.format(java.util.Locale.US, "%.0f%%", usedPct),
                    "Tersedia " + gb(available) + " untuk app baru.",
                    tint));
        }

        if (appsKb > 0) {
            card.addView(legendLine(ctx, Palette.brand(), "Aplikasi", gb(appsKb),
                    pctOf(appsKb, total)));
        }
        if (cacheKb > 0) {
            card.addView(legendLine(ctx, Palette.SAGE, "Cache", gb(cacheKb),
                    pctOf(cacheKb, total)));
        }
        if (systemKb > 0) {
            card.addView(legendLine(ctx, Palette.WARN, "Sistem", gb(systemKb),
                    pctOf(systemKb, total)));
        }
        if (otherKb > 0) {
            card.addView(legendLine(ctx, 0xFFA78BFA, "Lainnya", gb(otherKb),
                    pctOf(otherKb, total)));
        }
        if (freeKb > 0) {
            card.addView(legendLine(ctx, Palette.faded(ctx, Palette.SAGE), "Bebas",
                    gb(freeKb), pctOf(freeKb, total)));
        }
        return card;
    }

    static int batteryColor(int level) {
        if (level <= 15) {
            return Palette.CRITICAL;
        }
        if (level <= 35) {
            return Palette.WARN;
        }
        return Palette.OK;
    }

    // ------------------------------------------------------------ helpers

    /** One compact legend row: dot, label, size, share. */
    private static LinearLayout legendLine(Context ctx, int color, String label,
                                           String size, String share) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Cards.dp(ctx, 3), 0, Cards.dp(ctx, 3));

        row.addView(Cards.dot(ctx, color, 8));

        TextView name = new TextView(ctx);
        name.setText(label);
        name.setTextSize(11.5f);
        name.setTextColor(Palette.dim(ctx));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.setMarginStart(Cards.dp(ctx, 8));
        name.setLayoutParams(nlp);
        row.addView(name);

        TextView value = new TextView(ctx);
        value.setText(size + "  ·  " + share);
        value.setTextSize(11.5f);
        value.setTextColor(Palette.text(ctx));
        value.setTypeface(Typeface.MONOSPACE);
        row.addView(value);
        return row;
    }

    static String pctOf(long part, long whole) {
        if (whole <= 0) {
            return "—";
        }
        return String.format(java.util.Locale.US, "%.1f%%", 100.0 * part / whole);
    }

    static String str(Map<String, Object> section, String key, String fallback) {
        if (section == null) {
            return fallback;
        }
        Object v = section.get(key);
        return v == null || String.valueOf(v).isEmpty() ? fallback : String.valueOf(v);
    }

    /**
     * Uptime and reboot count.
     *
     * Both are written by DeviceSection, so they live in the "device" section.
     * The comment here is deliberate: an earlier "fix" moved the read to
     * "system" and silently produced an empty line for two builds.
     */
    static String uptimeLine(Snapshot snap) {
        Map<String, Object> device = snap.section("device");
        String uptime = str(device, "uptime_text", null);
        int boots = integer(device, "boot_count", -1);
        if (uptime == null && boots < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (uptime != null) {
            sb.append("Aktif ").append(uptime);
        }
        if (boots >= 0) {
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            sb.append(boots).append(" kali boot");
        }
        return sb.toString();
    }

    static double number(Map<String, Object> section, String key, double fallback) {
        if (section == null) {
            return fallback;
        }
        Object v = section.get(key);
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return fallback;
    }

    static int integer(Map<String, Object> section, String key, int fallback) {
        double d = number(section, key, fallback);
        return d == fallback ? fallback : (int) Math.round(d);
    }

    static String gb(long kb) {
        if (kb < 0) {
            return "\u2014";
        }
        double g = kb / 1024.0 / 1024.0;
        if (g >= 1) {
            return String.format(java.util.Locale.US, "%.1f GB", g);
        }
        double mb = kb / 1024.0;
        if (mb >= 1) {
            return String.format(java.util.Locale.US, "%.0f MB", mb);
        }
        // Below a megabyte, "%.0f MB" rounded a real file down to "0 MB",
        // which read as a failed read rather than a small APK.
        if (kb > 0) {
            // %f takes a floating-point argument; passing the long directly
            // threw IllegalFormatConversionException and crashed the page for
            // any size between 1 KB and 1 MB.
            return String.format(java.util.Locale.US, "%.0f KB", (double) kb);
        }
        return "0";
    }
}
