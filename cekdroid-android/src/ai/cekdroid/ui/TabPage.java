package ai.cekdroid.ui;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ai.cekdroid.collect.Snapshot;

/**
 * Renders one tab, which may hold more than one collector.
 *
 * DevInfo's tabs are grouped by question, not by data source: "Perangkat"
 * carries both the build identity and the identifiers, "CPU" carries the
 * processor and the GPU, "Penyimpanan" carries storage and RAM. Rendering each
 * collector into its own scroll view would give a tab two independent scroll
 * positions, so this builds one column and appends every section into it, with
 * a small group heading before each section after the first.
 *
 * The two hub tabs (Tes, Alat) are launchers rather than data, so they delegate
 * to {@link HubPage} and never touch the snapshot.
 */
public final class TabPage {

    private TabPage() {
    }

    public static View build(Activity activity, Snapshot snap, Tabs.Tab tab) {
        if ("tests".equals(tab.id)) {
            return HubPage.build(activity, "Tes perangkat",
                    "Periksa layar, tombol, sensor, dan baterai. Semua tes berjalan "
                            + "lokal dan tidak mengirim data ke mana pun.",
                    Hubs.tests(activity));
        }
        if ("tools".equals(tab.id)) {
            return HubPage.build(activity, "Alat",
                    "Tindakan yang tidak mengukur apa pun, tetapi sering dibutuhkan "
                            + "saat memeriksa perangkat.",
                    Hubs.tools(activity));
        }
        if ("location".equals(tab.id)) {
            return locationPage(activity);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        // 16dp sides, 12dp top: DevInfo's content starts close under the tab
        // strip and keeps a wider gutter than a top margin, which is what lets
        // a 62px row pitch read as a list rather than a stack.
        int pad = Cards.dp(activity, 16);
        root.setPadding(pad, Cards.dp(activity, 12), pad, pad);
        scroll.addView(root);

        boolean any = false;
        for (String sectionId : tab.sections) {
            List<SectionPage.Action> actions =
                    new ArrayList<>(TestLinks.forSection(sectionId));
            // Only the first section gets no heading: the tab's own label is
            // already on the strip, and repeating it wastes a row.
            if (any) {
                TextView heading = Cards.heading(activity,
                        SectionTitles.of(sectionId));
                root.addView(heading);
            }
            boolean rendered = SectionPage.appendSection(activity, snap, sectionId,
                    actions, root);
            any = any || rendered;
        }

        // DevInfo ends its Perangkat tab with the capability grid, so the tab
        // that answers "what is this device" also answers "what can it do".
        if ("device".equals(tab.id)) {
            root.addView(Cards.heading(activity, "Kemampuan"));
            View capabilities = Dashboard.capabilitiesCard(activity);
            root.addView(capabilities);
            Motion.enter(capabilities, 0);
            any = true;
        }

        if (!any) {
            root.addView(Cards.body(activity,
                    "Tidak ada data untuk bagian ini.", Palette.dim(activity)));
        }
        return scroll;
    }

    /**
     * The Lokasi tab.
     *
     * Satellite data is a live stream, not a snapshot value: there is nothing
     * to collect until a location listener is registered, so this tab explains
     * itself and hands off to the interactive screen.
     */
    private static View locationPage(Activity activity) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(activity, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        LinearLayout card = Cards.card(activity, Palette.accent("network"));
        card.addView(Cards.header(activity, "network", "Lokasi & satelit", null));
        card.addView(Cards.body(activity,
                "Peta satelit, akurasi, dan posisi hanya bisa dibaca saat layar "
                        + "peta sedang terbuka, karena datanya datang dari listener "
                        + "GNSS yang berjalan terus-menerus.",
                Palette.dim(activity)));

        TextView open = Cards.actionButton(activity, "Buka GPS & satelit");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Cards.dp(activity, 12);
        open.setLayoutParams(lp);
        Motion.attachPress(open);
        open.setOnClickListener(v -> activity.startActivity(
                new android.content.Intent(activity,
                        ai.cekdroid.test.GnssTestActivity.class)));
        card.addView(open);
        root.addView(card);

        if (!Perms.allGranted(activity, Perms.runtime("network"))) {
            LinearLayout permit = Cards.card(activity, Palette.accent("network"));
            permit.addView(Cards.header(activity, "network", "Izin diperlukan", null));
            permit.addView(Cards.hint(activity,
                    "Tanpa izin lokasi, Android tidak memberikan posisi maupun "
                            + "daftar satelit kepada app."));
            TextView grant = Cards.actionButton(activity, "Beri izin Lokasi");
            grant.setLayoutParams(lp);
            Motion.attachPress(grant);
            grant.setOnClickListener(v -> activity.requestPermissions(
                    Perms.runtime("network"), Perms.REQUEST_RUNTIME));
            permit.addView(grant);
            root.addView(permit);
        }
        return scroll;
    }

    /** Section titles for the group headings inside a combined tab. */
    private static final class SectionTitles {

        private SectionTitles() {
        }

        static String of(String sectionId) {
            switch (sectionId) {
                case "device": return "Identitas";
                case "id": return "Pengenal";
                case "cpu": return "Prosesor";
                case "gpu": return "Grafis";
                case "network": return "Koneksi";
                case "sim": return "SIM";
                case "storage": return "Penyimpanan";
                case "memory": return "Memori";
                case "apps": return "Aplikasi terpasang";
                case "codec": return "Codec media";
                case "system": return "Sistem";
                case "battery": return "Baterai";
                case "display": return "Layar";
                case "camera": return "Kamera";
                case "sensor": return "Sensor";
                default: return sectionId;
            }
        }
    }
}
