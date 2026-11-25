package ai.cekdroid.collect.sections;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import ai.cekdroid.collect.Recorder;
import ai.cekdroid.collect.Section;
import ai.cekdroid.collect.SectionBase;

/**
 * Active connection, addresses, and WiFi/cellular state.
 *
 * The SSID requires location permission on API 29+; when it is not granted the
 * value is null with an explicit reason, and the rest still renders.
 */
public class NetworkSection implements Section {

    @Override
    public String id() {
        return "network";
    }

    @Override
    public String title() {
        return "Jaringan";
    }

    @Override
    public void collect(Context ctx, Recorder out) {
        ConnectivityManager cm =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            out.warn("ConnectivityManager tidak tersedia");
            return;
        }

        try {
            Network active = cm.getActiveNetwork();
            if (active == null) {
                out.put("connected", false);
                out.warn("tidak ada jaringan aktif");
            } else {
                out.put("connected", true);
                NetworkCapabilities caps = cm.getNetworkCapabilities(active);
                out.put("wifi", caps != null
                        && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
                out.put("cellular", caps != null
                        && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
                out.put("ethernet", caps != null
                        && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
                out.put("vpn", caps != null
                        && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
                out.put("bluetooth", caps != null
                        && caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
                if (caps != null) {
                    out.put("downstream_kbps", caps.getLinkDownstreamBandwidthKbps());
                    out.put("upstream_kbps", caps.getLinkUpstreamBandwidthKbps());
                }

                LinkProperties lp = cm.getLinkProperties(active);
                if (lp != null) {
                    out.put("interface", lp.getInterfaceName());
                    out.put("dns", joinAddresses(lp.getDnsServers()));
                    out.put("ipv4", joinAddresses(lp.getLinkAddresses().stream()
                            .map(la -> la.getAddress()).collect(java.util.stream.Collectors.toList())));
                    out.put("routes", String.valueOf(lp.getRoutes().size()));
                }
            }
        } catch (Throwable t) {
            out.warn("status koneksi: " + t.getMessage());
        }

        WifiManager wifi = (WifiManager) ctx.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            try {
                out.put("wifi_enabled", wifi.isWifiEnabled());
                WifiInfo info = wifi.getConnectionInfo();
                if (info != null) {
                    out.put("wifi_link_speed_mbps", info.getLinkSpeed());
                    out.put("wifi_rssi", info.getRssi());
                    out.put("wifi_frequency_mhz", info.getFrequency());
                    out.put("wifi_ip", intToIp(info.getIpAddress()));
                    out.put("wifi_bssid", SectionBase.s(info.getBSSID()));
                    putWifiStandard(out, info);
                    putWifiSecurity(out, info);
                    // getSSID returns the literal "<unknown ssid>" without
                    // location access on API 29+, and keeps returning it when
                    // the permission is granted but system location is off.
                    // Those are two different fixes, so name the right one.
                    String ssid = info.getSSID();
                    if (ssid == null || ssid.contains("unknown")) {
                        out.put("wifi_ssid", null);
                        if (!locationPermissionGranted(ctx)) {
                            out.warn("SSID butuh izin lokasi (Android 10+)");
                        } else if (!locationEnabled(ctx)) {
                            out.warn("SSID butuh lokasi sistem aktif "
                                    + "(izin sudah ada, GPS dimatikan)");
                        } else {
                            out.warn("SSID tidak terbaca meski izin dan lokasi aktif");
                        }
                    } else {
                        // Android returns the value quoted: "MyNet".
                        out.put("wifi_ssid", stripQuotes(ssid));
                    }
                }
            } catch (Throwable t) {
                out.warn("WiFi: " + t.getMessage());
            }
        }

        TelephonyManager tm =
                (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            try {
                out.put("network_type", networkTypeName(tm.getDataNetworkType()));
                out.put("network_operator", SectionBase.s(tm.getNetworkOperatorName()));
                out.put("data_enabled", tm.isDataEnabled());
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    out.put("data_state", String.valueOf(tm.getDataState()));
                }
            } catch (SecurityException e) {
                out.warn("status seluler butuh izin READ_PHONE_STATE");
            } catch (Throwable t) {
                out.warn("seluler: " + t.getMessage());
            }
        }

        putTraffic(ctx, out);
    }

    /**
     * Byte counters since boot, split by transport.
     *
     * TrafficStats is readable without any permission. The counters reset on
     * reboot, which is why the label says "sejak dinyalakan" rather than
     * implying a billing period.
     */
    private void putTraffic(Context ctx, Recorder out) {
        try {
            long mobileRx = android.net.TrafficStats.getMobileRxBytes();
            long mobileTx = android.net.TrafficStats.getMobileTxBytes();
            long totalRx = android.net.TrafficStats.getTotalRxBytes();
            long totalTx = android.net.TrafficStats.getTotalTxBytes();

            if (mobileRx < 0) {
                mobileRx = 0;
            }
            if (mobileTx < 0) {
                mobileTx = 0;
            }

            out.put("traffic_mobile_rx_kb", mobileRx / 1024);
            out.put("traffic_mobile_tx_kb", mobileTx / 1024);
            out.put("traffic_mobile_total_kb", (mobileRx + mobileTx) / 1024);

            long wifiRx = Math.max(0, totalRx - mobileRx);
            long wifiTx = Math.max(0, totalTx - mobileTx);
            out.put("traffic_wifi_rx_kb", wifiRx / 1024);
            out.put("traffic_wifi_tx_kb", wifiTx / 1024);
            out.put("traffic_wifi_total_kb", (wifiRx + wifiTx) / 1024);
            out.put("traffic_total_kb", (Math.max(0, totalRx) + Math.max(0, totalTx)) / 1024);
        } catch (Throwable t) {
            out.warn("statistik lalu lintas: " + t.getMessage());
        }
    }

    /**
     * Wi-Fi generation ("Wi-Fi 4/5/6/7"), from WifiInfo.getWifiStandard().
     *
     * Added in API 30, so older devices leave the row null rather than being
     * labelled with a generation the platform never reported.
     */
    private static void putWifiStandard(Recorder out, WifiInfo info) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            out.put("wifi_standard", null);
            return;
        }
        try {
            int standard = info.getWifiStandard();
            String name;
            switch (standard) {
                case 1: case 4: name = "Legacy (802.11a/b/g)"; break;
                case 5: name = "Wi-Fi 4 (802.11n)"; break;
                case 6: name = "Wi-Fi 5 (802.11ac)"; break;
                case 7: name = "Wi-Fi 6 (802.11ax)"; break;
                case 8: name = "Wi-Fi 6E (802.11ax 6GHz)"; break;
                case 9: name = "Wi-Fi 7 (802.11be)"; break;
                case 10: name = "WiGig (802.11ad)"; break;
                case 0: name = null; break;
                default: name = "standard_" + standard;
            }
            out.put("wifi_standard", name);
        } catch (Throwable t) {
            out.put("wifi_standard", null);
            out.warn("standar Wi-Fi: " + t.getMessage());
        }
    }

    /** Current Wi-Fi security type, API 30+, hidden behind the same guard. */
    private static void putWifiSecurity(Recorder out, WifiInfo info) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            out.put("wifi_security", null);
            return;
        }
        try {
            int type = info.getCurrentSecurityType();
            String name;
            switch (type) {
                case 0: name = "Terbuka"; break;
                case 1: name = "WEP"; break;
                case 2: name = "WPA/WPA2 PSK"; break;
                case 3: name = "WPA2 Enterprise"; break;
                case 4: name = "WPA3 PSK"; break;
                case 5: name = "WPA3 Enterprise"; break;
                case 6: name = "OSEN"; break;
                case 7: name = "OWE"; break;
                case 8: name = "WAPI PSK"; break;
                case 9: name = "WAPI Cert"; break;
                default: name = "type_" + type;
            }
            out.put("wifi_security", name);
        } catch (Throwable t) {
            out.put("wifi_security", null);
            out.warn("keamanan Wi-Fi: " + t.getMessage());
        }
    }

    /** SSID arrives wrapped in quotes from WifiInfo. */
    private static String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static boolean locationPermissionGranted(Context ctx) {
        return ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private static boolean locationEnabled(Context ctx) {
        android.location.LocationManager lm = (android.location.LocationManager)
                ctx.getSystemService(Context.LOCATION_SERVICE);
        try {
            return lm != null && lm.isLocationEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String joinAddresses(List<InetAddress> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (InetAddress a : addresses) {
            if (a.isLoopbackAddress()) {
                continue;
            }
            parts.add(a.getHostAddress());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String intToIp(int ip) {
        if (ip == 0) {
            return null;
        }
        try {
            byte[] bytes = new byte[]{
                    (byte) (ip & 0xff), (byte) ((ip >> 8) & 0xff),
                    (byte) ((ip >> 16) & 0xff), (byte) ((ip >> 24) & 0xff)};
            return Inet4Address.getByAddress(bytes).getHostAddress();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String networkTypeName(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_GPRS: return "GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE: return "EDGE";
            case TelephonyManager.NETWORK_TYPE_UMTS: return "UMTS";
            case TelephonyManager.NETWORK_TYPE_HSDPA: return "HSDPA";
            case TelephonyManager.NETWORK_TYPE_HSUPA: return "HSUPA";
            case TelephonyManager.NETWORK_TYPE_HSPA: return "HSPA";
            case TelephonyManager.NETWORK_TYPE_CDMA: return "CDMA";
            case TelephonyManager.NETWORK_TYPE_EVDO_0: return "EVDO_0";
            case TelephonyManager.NETWORK_TYPE_EVDO_A: return "EVDO_A";
            case TelephonyManager.NETWORK_TYPE_EVDO_B: return "EVDO_B";
            case TelephonyManager.NETWORK_TYPE_1xRTT: return "1xRTT";
            case TelephonyManager.NETWORK_TYPE_LTE: return "LTE";
            case TelephonyManager.NETWORK_TYPE_EHRPD: return "eHRPD";
            case TelephonyManager.NETWORK_TYPE_HSPAP: return "HSPAP";
            case TelephonyManager.NETWORK_TYPE_IDEN: return "iDEN";
            case TelephonyManager.NETWORK_TYPE_GSM: return "GSM";
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA: return "TD_SCDMA";
            case TelephonyManager.NETWORK_TYPE_IWLAN: return "IWLAN";
            case TelephonyManager.NETWORK_TYPE_NR: return "5G NR";
            case TelephonyManager.NETWORK_TYPE_UNKNOWN: return null;
            default: return "type_" + type;
        }
    }
}
