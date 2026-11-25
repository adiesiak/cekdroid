package ai.cekdroid.test;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

import ai.cekdroid.ui.Cards;
import ai.cekdroid.ui.Chrome;
import ai.cekdroid.ui.Palette;

/**
 * Live GNSS view: satellites in view, fix quality, and position.
 *
 * Requires ACCESS_FINE_LOCATION. Indoors the satellite list stays empty, which
 * is accurate rather than an error, so the empty state says so plainly.
 */
public class GnssTestActivity extends Activity {

    private LocationManager lm;
    private TextView status;
    private TextView summary;
    private LinearLayout satRows;
    private GnssStatus.Callback gnssCallback;
    private LocationListener locationListener;

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

        summary = new TextView(this);
        summary.setTextSize(13f);
        summary.setTextColor(Palette.text(this));
        summary.setTypeface(android.graphics.Typeface.MONOSPACE);
        root.addView(summary);

        satRows = new LinearLayout(this);
        satRows.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(satRows);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

                Chrome.addBar(this, root, "GPS & satelit");
        setContentView(root);
        start();
    }

    private void start() {
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) {
            status.setText("LocationManager tidak tersedia.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            status.setText("Izin lokasi presisi belum diberikan. "
                    + "Beri lewat Info aplikasi > Izin.");
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 42);
            return;
        }
        status.setText("Menunggu sinyal. Di dalam ruangan daftar satelit akan kosong.");

        gnssCallback = new GnssCallback(this);
        locationListener = new LocListener(this);

        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
            lm.registerGnssStatusCallback(gnssCallback, null);
        } catch (SecurityException e) {
            status.setText("Izin lokasi ditolak: " + e.getMessage());
        } catch (Throwable t) {
            status.setText("GPS tidak tersedia: " + t.getMessage());
        }
    }

    /**
     * Static nested classes calling named methods, deliberately.
     *
     * A NON-static inner class that contains a lambda capturing the enclosing
     * Activity makes d8 8.2.2 crash with an internal NullPointerException
     * ("String.length() ... parameter1 is null") while desugaring the synthetic
     * accessor, and the whole build aborts with no usable message. Confirmed
     * with a minimal reproduction: static inner class dexes fine, non-static
     * with a lambda does not.
     */
    private static class GnssCallback extends GnssStatus.Callback {
        private final GnssTestActivity host;

        GnssCallback(GnssTestActivity host) {
            this.host = host;
        }

        @Override
        public void onSatelliteStatusChanged(GnssStatus s) {
            host.postSatellites(s);
        }

        @Override
        public void onStarted() {
            host.postText("GNSS aktif, mencari satelit…");
        }

        @Override
        public void onStopped() {
            host.postText("GNSS berhenti.");
        }
    }

    private static class LocListener implements LocationListener {
        private final GnssTestActivity host;

        LocListener(GnssTestActivity host) {
            this.host = host;
        }

        @Override
        public void onLocationChanged(Location location) {
            host.postLocation(location);
        }

        @Override
        public void onProviderEnabled(String provider) {
            // reflected by onLocationChanged
        }

        @Override
        public void onProviderDisabled(String provider) {
            host.postText("Provider lokasi dimatikan.");
        }
    }

    void postText(final String text) {
        runOnUiThread(() -> status.setText(text));
    }

    void postSatellites(final GnssStatus s) {
        runOnUiThread(() -> renderSatellites(s));
    }

    void postLocation(final Location location) {
        runOnUiThread(() -> renderLocation(location));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == 42 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            start();
        } else {
            status.setText("Izin lokasi tidak diberikan.");
        }
    }

    private void renderLocation(Location location) {
        summary.setText(String.format(Locale.US,
                "Lat  %.6f\nLon  %.6f\nAlt  %.1f m\nAkurasi %.1f m\nSatelit %s",
                location.getLatitude(), location.getLongitude(),
                location.getAltitude(), location.getAccuracy(),
                location.getExtras() == null ? "—"
                        : String.valueOf(location.getExtras().getInt("satellites", -1))));
    }

    private void renderSatellites(GnssStatus s) {
        satRows.removeAllViews();
        int visible = s.getSatelliteCount();
        int usedInFix = 0;

        // Count per constellation first: DevInfo leads with "Beidou 0 satelit,
        // GPS 0 satelit, ..." which is far easier to scan than a flat list.
        java.util.LinkedHashMap<Integer, Integer> perConstellation =
                new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<Integer, Integer> usedPerConstellation =
                new java.util.LinkedHashMap<>();
        for (int i = 0; i < visible; i++) {
            int type = s.getConstellationType(i);
            perConstellation.put(type, get(perConstellation, type) + 1);
            if (s.usedInFix(i)) {
                usedInFix++;
                usedPerConstellation.put(type, get(usedPerConstellation, type) + 1);
            }
        }

        int[] order = {
                GnssStatus.CONSTELLATION_BEIDOU, GnssStatus.CONSTELLATION_GPS,
                GnssStatus.CONSTELLATION_GALILEO, GnssStatus.CONSTELLATION_GLONASS,
                GnssStatus.CONSTELLATION_QZSS, GnssStatus.CONSTELLATION_IRNSS,
                GnssStatus.CONSTELLATION_SBAS,
        };
        for (int type : order) {
            int count = get(perConstellation, type);
            int used = get(usedPerConstellation, type);
            String label = String.format(Locale.US, "%-5s  %d satelit",
                    constellation(type), count);
            String detail = used > 0 ? "  (" + used + " dipakai fix)" : "";
            TextView row = Cards.body(this, flag(type) + "  " + label + detail,
                    count > 0 ? Palette.text(this) : Palette.faint(this));
            row.setTypeface(android.graphics.Typeface.MONOSPACE);
            row.setPadding(0, Cards.dp(this, 5), 0, Cards.dp(this, 5));
            satRows.addView(row);
        }

        if (visible > 0) {
            satRows.addView(Cards.divider(this));
            satRows.addView(Cards.body(this, "Detail satelit", Palette.dim(this)));
            for (int i = 0; i < visible; i++) {
                String line = String.format(Locale.US,
                        "%s%02d  %-3s  el %5.1f°  az %5.1f°  %4.1f dB",
                        s.usedInFix(i) ? "*" : " ",
                        s.getSvid(i),
                        constellation(s.getConstellationType(i)),
                        s.getElevationDegrees(i),
                        s.getAzimuthDegrees(i),
                        s.getCn0DbHz(i));
                TextView row = Cards.body(this, line,
                        s.usedInFix(i) ? Palette.OK : Palette.dim(this));
                row.setTypeface(android.graphics.Typeface.MONOSPACE);
                satRows.addView(row);
            }
        } else {
            satRows.addView(Cards.body(this,
                    "Belum ada satelit terlihat. Coba dekat jendela.", Palette.faint(this)));
        }
        status.setText(visible + " terlihat, " + usedInFix + " dipakai untuk fix.");
    }

    private static int get(java.util.Map<Integer, Integer> map, int key) {
        Integer v = map.get(key);
        return v == null ? 0 : v;
    }

    /** Emoji flag as a stand-in for DevInfo's flag images: no assets needed. */
    private static String flag(int type) {
        switch (type) {
            case GnssStatus.CONSTELLATION_BEIDOU: return "🇨🇳";
            case GnssStatus.CONSTELLATION_GPS: return "🇺🇸";
            case GnssStatus.CONSTELLATION_GALILEO: return "🇪🇺";
            case GnssStatus.CONSTELLATION_GLONASS: return "🇷🇺";
            case GnssStatus.CONSTELLATION_QZSS: return "🇯🇵";
            case GnssStatus.CONSTELLATION_IRNSS: return "🇮🇳";
            default: return "🌐";
        }
    }

    private static String constellation(int type) {
        switch (type) {
            case GnssStatus.CONSTELLATION_GPS: return "GPS";
            case GnssStatus.CONSTELLATION_GLONASS: return "GLO";
            case GnssStatus.CONSTELLATION_BEIDOU: return "BDS";
            case GnssStatus.CONSTELLATION_GALILEO: return "GAL";
            case GnssStatus.CONSTELLATION_SBAS: return "SBA";
            case GnssStatus.CONSTELLATION_QZSS: return "QZS";
            case GnssStatus.CONSTELLATION_IRNSS: return "IRN";
            default: return "?";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lm != null) {
            try {
                if (locationListener != null) {
                    lm.removeUpdates(locationListener);
                }
                if (gnssCallback != null) {
                    lm.unregisterGnssStatusCallback(gnssCallback);
                }
            } catch (Throwable ignored) {
                // provider may already be gone
            }
        }
    }
}
