package local.driveboard;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.car.app.notification.CarAppExtender;
import androidx.car.app.notification.CarPendingIntent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/** Projection-scoped reminders; no background alarms or phone-only reminders. */
public final class PrayerAlerts {
    private static final String CHANNEL = "prayer_alerts_v1";
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final String[] NAMES = {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"};
    private static Context app;
    private static boolean projection;
    private static final Runnable TICK = PrayerAlerts::tick;
    private PrayerAlerts() {}

    public static void connectionChanged(Context context, boolean connected) {
        HANDLER.post(() -> {
            app = context.getApplicationContext();
            projection = connected;
            HANDLER.removeCallbacks(TICK);
            if (connected) {
                tick();
            } else {
                cancel();
            }
        });
    }
    public static void refresh(Context context) {
        HANDLER.post(() -> {
            app = context.getApplicationContext();
            HANDLER.removeCallbacks(TICK);
            if (projection) {
                tick();
            }
        });
    }
    public static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false;
        }
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    private static boolean allowed() {
        if (app == null || !projection || !Store.prayerAlertsEnabled(app)) {
            return false;
        }
        return canNotify(app);
    }
    private static void tick() {
        HANDLER.removeCallbacks(TICK);
        if (!projection || app == null) {
            return;
        }
        long delay = 30_000;
        try {
            if (allowed()) {
                List<PrayerAlertSchedule.Day> days = WidgetData.prayerAlertDays(app);
                int lead = Store.prayerAlertLeadMinutes(app);
                long now = System.currentTimeMillis();
                Set<String> posted = Store.prayerAlertsPosted(app);
                for (PrayerAlertSchedule.Moment moment : PrayerAlertSchedule.due(days, lead, now, posted)) {
                    if (post(moment.prayer, moment.before, lead, moment.time.format(DateTimeFormatter.ofPattern("HH:mm")), false)) {
                        Store.markPrayerAlertPosted(app, moment.key(), LocalDate.now(PrayerAlertSchedule.ZONE).minusDays(1).toString());
                        posted.add(moment.key());
                    }
                }
                long next = PrayerAlertSchedule.next(days, lead, now, posted);
                if (next != Long.MAX_VALUE) {
                    delay = Math.min(delay, Math.max(1, next - System.currentTimeMillis()));
                }
            } else if (!Store.prayerAlertsEnabled(app)) {
                cancel();
            }
        } catch (RuntimeException error) {
            CarDiagnostics.log(app, "PrayerAlerts tick failed " + error);
        }
        HANDLER.postDelayed(TICK, delay);
    }
    private static void cancel() {
        if (app != null) {
            for (int i = 0; i < 5; i++) {
                NotificationManagerCompat.from(app).cancel(7400 + i);
            }
        }
    }
    private static boolean post(int prayer, boolean before, int lead, String time, boolean sample) {
        if (!allowed()) {
            return false;
        }
        try {
            Context words = AppLocale.wrap(app);
            NotificationManager manager = app.getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL, words.getString(R.string.prayer_alert_channel), NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null, null);
            channel.enableVibration(false);
            manager.createNotificationChannel(channel);
            String title = before
                    ? words.getString(R.string.prayer_alert_before, NAMES[prayer], lead)
                    : words.getString(R.string.prayer_alert_at, NAMES[prayer]);
            String text = time + " \u00b7 " + WidgetData.zone(app);
            PendingIntent phone = PendingIntent.getActivity(app, 7400 + prayer, new Intent(app, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent car = CarPendingIntent.getCarApp(app, 7400 + prayer, new Intent(Intent.ACTION_VIEW)
                    .setComponent(new ComponentName(app, DriveBoardService.class)), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            Notification notification = new NotificationCompat.Builder(app, CHANNEL)
                    .setSmallIcon(R.drawable.ic_prayer_notification).setContentTitle(title).setContentText(text)
                    .setContentIntent(phone).setAutoCancel(true).setTimeoutAfter(600_000)
                    .setDefaults(Notification.DEFAULT_SOUND)
                    .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .extend(new CarAppExtender.Builder().setContentTitle(title).setContentText(text)
                            .setSmallIcon(R.drawable.ic_prayer_notification).setContentIntent(car)
                            .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH).build()).build();
            NotificationManagerCompat.from(app).notify(7400 + prayer, notification);
            CarDiagnostics.log(app, "Alert posted " + (sample ? "sample" : NAMES[prayer] + (before ? " before" : " at")));
            return true;
        } catch (RuntimeException error) {
            CarDiagnostics.log(app, "Alert post failed " + error);
            return false;
        }
    }
    public static void postDebugSample(Context context) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        HANDLER.post(() -> {
            app = context.getApplicationContext();
            if (!allowed()) {
                CarDiagnostics.log(app, "Alert sample skipped: projection/settings/permission/channel");
                return;
            }
            post(0, true, Store.prayerAlertLeadMinutes(app), LocalTime.now(PrayerAlertSchedule.ZONE).format(DateTimeFormatter.ofPattern("HH:mm")), true);
        });
    }
}
