package ai.cekdroid.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;

import ai.cekdroid.MainActivity;

/**
 * Live CPU and RAM monitor.
 *
 * DevInfo keeps this in a normal notification, and that is the better default:
 * a notification is always dismissible, never covers the screen, needs no
 * SYSTEM_ALERT_WINDOW grant, and can carry a "Matikan" action. The floating
 * overlay still exists — it is the more useful form while gaming or when the
 * shade is out of reach — but it is now opt-in through an extra, and the
 * notification is what runs by default.
 *
 * The notification is a foreground-service notification, so it cannot be
 * swiped away while the monitor runs; the explicit stop action is the way out.
 */
public class FloatingMonitorService extends Service {

    public static final String CHANNEL_ID = "cekdroid_monitor";

    /** Extra: also draw the floating overlay. Absent or false = notification only. */
    public static final String EXTRA_OVERLAY = "overlay";
    public static final String ACTION_STOP = "ai.cekdroid.action.STOP_MONITOR";

    private static final int NOTIFY_ID = 1001;
    private static final long TICK_MS = 1000;
    /**
     * Poll interval while the screen is off.
     *
     * Nothing is visible then, so a tick only has to notice the screen coming
     * back. Five seconds cuts the wakeups by five without delaying the resume.
     */
    private static final long IDLE_TICK_MS = 5000;

    private WindowManager wm;
    private View view;
    private TextView line1;
    private TextView line2;
    private final Handler handler = new Handler(android.os.Looper.getMainLooper());

    private long lastCpuTotal;
    private long lastCpuIdle;
    private boolean overlayWanted;

    public static boolean canDrawOverlays(Context ctx) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx);
    }

    public static Intent overlayPermissionIntent(Context ctx) {
        return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + ctx.getPackageName()));
    }

    /** Start the monitor in notification-only mode. */
    public static void startNotification(Context ctx) {
        Intent intent = new Intent(ctx, FloatingMonitorService.class);
        ctx.startService(intent);
    }

    /** Start the monitor and draw the floating overlay as well. */
    public static void startWithOverlay(Context ctx) {
        Intent intent = new Intent(ctx, FloatingMonitorService.class);
        intent.putExtra(EXTRA_OVERLAY, true);
        ctx.startService(intent);
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, FloatingMonitorService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        overlayWanted = intent != null && intent.getBooleanExtra(EXTRA_OVERLAY, false);
        // An overlay without the grant would silently fail to appear, so the
        // request degrades to notification-only rather than doing nothing.
        if (overlayWanted && !canDrawOverlays(this)) {
            overlayWanted = false;
        }

        startForeground(NOTIFY_ID, buildNotification());

        if (overlayWanted) {
            showOverlay();
        } else {
            hideOverlay();
        }
        handler.removeCallbacksAndMessages(null);
        handler.post(this::tick);
        // START_STICKY: the monitor should survive a low-memory kill, since the
        // user asked for it to keep running.
        return START_STICKY;
    }

    private Notification buildNotification() {
        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            // DEFAULT, not LOW: a LOW-importance foreground notification is
            // collapsed to a single line on many ROMs, which hides the action
            // buttons — including the "Matikan" this service depends on.
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Monitor CPU & RAM", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setShowBadge(false);
            channel.setDescription("Angka CPU dan RAM terpakai, diperbarui tiap detik.");
            nm.createNotificationChannel(channel);
        }

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), pendingFlags());

        // The stop action is the whole point of this notification: without it a
        // foreground notification can only be dismissed by killing the app.
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, FloatingMonitorService.class).setAction(ACTION_STOP),
                pendingFlags());

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setContentTitle("Monitor CPU & RAM")
                .setContentText(buildStatusLine())
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Matikan", stop).build());

        // A big-text body makes the two numbers readable when the shade is
        // pulled down, which is where DevInfo's monitor is actually read.
        Notification.BigTextStyle style = new Notification.BigTextStyle()
                .bigText(buildStatusLine() + (overlayWanted
                        ? "\nOverlay mengambang aktif."
                        : "\nHanya di notifikasi ini."));
        builder.setStyle(style);
        return builder.build();
    }

    /** "CPU 23% · RAM 2.1G / 3.7G" — one line, refreshed each tick. */
    private String buildStatusLine() {
        long[] stat = readCpuStat();
        long dTotal = stat[0] - lastCpuTotal;
        long dIdle = stat[1] - lastCpuIdle;
        double busy = dTotal > 0 ? 100.0 * (dTotal - dIdle) / dTotal : 0;
        String[] mem = readMeminfo();
        String ram = mem == null ? "RAM —"
                : "RAM " + mem[0] + " / " + mem[1];
        return String.format(java.util.Locale.US, "CPU %.0f%%  ·  %s", busy, ram);
    }

    private static int pendingFlags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;
    }

    private void showOverlay() {
        if (view != null) {
            return;
        }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) {
            overlayWanted = false;
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(10);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E60F1115"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(Math.max(1, dp(1)), Color.parseColor("#334CC2FF"));
        box.setBackground(bg);

        line1 = new TextView(this);
        line1.setTextColor(Color.parseColor("#4CC2FF"));
        line1.setTextSize(12f);
        line1.setTypeface(android.graphics.Typeface.MONOSPACE);

        line2 = new TextView(this);
        line2.setTextColor(Color.parseColor("#E6E9EF"));
        line2.setTextSize(12f);
        line2.setTypeface(android.graphics.Typeface.MONOSPACE);

        box.addView(line1);
        box.addView(line2);

        // Tapping the overlay opens the app, where the monitor can be turned
        // off; the notification's "Matikan" is the one-tap route.
        box.setOnClickListener(v -> {
            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(open);
        });

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(12);
        lp.y = dp(80);

        try {
            wm.addView(box, lp);
            view = box;
        } catch (Throwable t) {
            // A ROM that rejects the window type leaves the notification, which
            // is still a working monitor.
            view = null;
            overlayWanted = false;
        }
    }

    private void hideOverlay() {
        if (view != null && wm != null) {
            try {
                wm.removeView(view);
            } catch (Throwable ignored) {
                // the window may already be gone with the process
            }
        }
        view = null;
        line1 = null;
        line2 = null;
    }

    /**
     * Self-posting tick.
     *
     * Lambdas only. d8 8.2.2 in build-tools 34.0.0 crashes with an internal
     * NullPointerException on any ANONYMOUS inner class, aborting the build with
     * no usable message. Lambdas and named nested classes dex fine.
     */
    private void tick() {
        update();
        handler.postDelayed(this::tick, isInteractive() ? TICK_MS : IDLE_TICK_MS);
    }

    /** True while the display is on; false when the device is asleep. */
    private boolean isInteractive() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager)
                    getSystemService(POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (Throwable t) {
            return true;
        }
    }

    private void update() {
        String status = buildStatusLine();
        long[] stat = readCpuStat();
        long total = stat[0];
        long idle = stat[1];
        lastCpuTotal = total;
        lastCpuIdle = idle;

        if (line1 != null) {
            String[] mem = readMeminfo();
            line1.setText(status);
            line2.setText(mem == null ? "RAM —"
                    : String.format(java.util.Locale.US, "up %s", uptime()));
        }

        // The notification text is refreshed too, so the shade shows live
        // numbers without the overlay being on at all.
        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIFY_ID, buildNotification());
            }
        } catch (Throwable ignored) {
            // a rate-limited notification update is not worth failing over
        }
    }

    private static String uptime() {
        long mins = SystemClock.elapsedRealtime() / 60000;
        return (mins / 60) + "j " + (mins % 60) + "m";
    }

    /** {totalJiffies, idleJiffies} from the aggregate cpu line. */
    private static long[] readCpuStat() {
        long[] result = {0, 0};
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = r.readLine();
            if (line == null || !line.startsWith("cpu ")) {
                return result;
            }
            String[] parts = line.trim().split("\\s+");
            for (int i = 1; i < parts.length && i <= 8; i++) {
                if (i == 4 || i == 5) {
                    result[1] += Long.parseLong(parts[i]);
                }
                result[0] += Long.parseLong(parts[i]);
            }
        } catch (Throwable ignored) {
            // an unreadable /proc/stat leaves the numbers at zero
        }
        return result;
    }

    /** {"used", "total"} as GB strings, or null. */
    private static String[] readMeminfo() {
        long total = 0;
        long available = 0;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    total = parseKb(line);
                } else if (line.startsWith("MemAvailable:")) {
                    available = parseKb(line);
                }
            }
        } catch (Throwable t) {
            return null;
        }
        if (total <= 0) {
            return null;
        }
        return new String[]{
                String.format(java.util.Locale.US, "%.1fG",
                        (total - available) / 1024.0 / 1024.0),
                String.format(java.util.Locale.US, "%.1fG", total / 1024.0 / 1024.0),
        };
    }

    private static long parseKb(String line) {
        String[] parts = line.trim().split("\\s+");
        return parts.length >= 2 ? Long.parseLong(parts[1]) : 0;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        hideOverlay();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
