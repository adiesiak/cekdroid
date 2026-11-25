package ai.cekdroid.test;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Local network addresses, with their scope.
 *
 * DevInfo calls this "Pencarian IP", but the lookup it performs is of the
 * device's own addresses, not a query to an external service — and that is the
 * only defensible version without sending the user's IP to a third party. Each
 * address is classified (private, loopback, link-local, public) so a reader can
 * tell a LAN address from a carrier-NAT one at a glance.
 */
public class IpLookupActivity extends Activity {

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

        Chrome.addBar(this, root, "Pencarian IP");
        setContentView(root);

        ConnectivityManager cm = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            status.setText("ConnectivityManager tidak tersedia.");
            return;
        }

        Network active = cm.getActiveNetwork();
        if (active == null) {
            status.setText("Tidak ada jaringan aktif.");
            return;
        }
        LinkProperties lp = cm.getLinkProperties(active);
        if (lp == null) {
            status.setText("Detail jaringan tidak terbaca.");
            return;
        }

        status.setText("Antarmuka " + lp.getInterfaceName());
        list.addView(Cards.row(this, "Antarmuka", lp.getInterfaceName()));
        list.addView(Cards.row(this, "Rute", String.valueOf(lp.getRoutes().size())));

        List<String> v4 = new ArrayList<>();
        List<String> v6 = new ArrayList<>();
        Map<String, String> scopes = new LinkedHashMap<>();
        for (android.net.LinkAddress la : lp.getLinkAddresses()) {
            InetAddress address = la.getAddress();
            String text = address.getHostAddress();
            int prefix = la.getPrefixLength();
            if (address instanceof Inet4Address) {
                v4.add(text + "/" + prefix);
            } else if (address instanceof Inet6Address) {
                v6.add(text + "/" + prefix);
            }
            scopes.put(text, scopeOf(address));
        }

        list.addView(Cards.heading(this, "IPv4"));
        if (v4.isEmpty()) {
            list.addView(Cards.body(this, "Tidak ada alamat IPv4.", Palette.faint(this)));
        }
        for (String text : v4) {
            String address = text.split("/")[0];
            list.addView(Cards.row(this, text, scopes.get(address)));
        }

        list.addView(Cards.heading(this, "IPv6"));
        if (v6.isEmpty()) {
            list.addView(Cards.body(this, "Tidak ada alamat IPv6.", Palette.faint(this)));
        }
        for (String text : v6) {
            String address = text.split("/")[0];
            list.addView(Cards.row(this, text, scopes.get(address)));
        }

        List<InetAddress> dns = lp.getDnsServers();
        list.addView(Cards.heading(this, "DNS"));
        if (dns == null || dns.isEmpty()) {
            list.addView(Cards.body(this, "Tidak ada DNS yang dilaporkan.",
                    Palette.faint(this)));
        } else {
            for (InetAddress server : dns) {
                list.addView(Cards.row(this, server.getHostAddress(),
                        scopeOf(server)));
            }
        }

        list.addView(Cards.hint(this,
                "Alamat publik (WAN) tidak ditampilkan: membacanya memerlukan "
                        + "permintaan ke layanan luar, dan alamat itu bukan milik "
                        + "perangkat ini melainkan milik jaringan di atasnya."));
    }

    /** Human scope: private, loopback, link-local, multicast, public. */
    private static String scopeOf(InetAddress address) {
        boolean v4 = address instanceof Inet4Address;
        String host = address.getHostAddress();
        if (address.isLoopbackAddress()) {
            return "loopback";
        }
        if (address.isLinkLocalAddress()) {
            return "link-local";
        }
        if (address.isMulticastAddress()) {
            return "multicast";
        }
        if (address.isSiteLocalAddress()) {
            return "privat";
        }
        if (v4) {
            return "publik";
        }
        // IPv6 unique-local (fc00::/7) is private but not "site local" on older
        // runtime checks; the prefix test is the reliable form.
        if (host.startsWith("fc") || host.startsWith("fd")) {
            return "privat (ULA)";
        }
        return "publik";
    }
}
