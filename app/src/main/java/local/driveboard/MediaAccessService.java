package local.driveboard;

import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Media access grant and the narrow Maps/Waze navigation notification bridge. */
public final class MediaAccessService extends NotificationListenerService {
    // Serial work preserves post/remove order while keeping Icon loading off the main thread.
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, NavigationState> active = new HashMap<>();
    private volatile int generation;

    private boolean source(StatusBarNotification sbn) {
        return sbn != null && ("com.google.android.apps.maps".equals(sbn.getPackageName())
                || "com.waze".equals(sbn.getPackageName()));
    }

    private boolean navigation(StatusBarNotification sbn) {
        if (sbn == null) return false;
        String pkg = sbn.getPackageName();
        return ("com.google.android.apps.maps".equals(pkg) || "com.waze".equals(pkg))
                && Notification.CATEGORY_NAVIGATION.equals(sbn.getNotification().category);
    }

    @Override public void onListenerConnected() {
        int epoch = ++generation;
        worker.execute(() -> {
            active.clear();
            try {
                StatusBarNotification[] notifications = getActiveNotifications();
                if (notifications != null) {
                    for (StatusBarNotification sbn : notifications) {
                        if (navigation(sbn)) active.put(sbn.getKey(), parse(sbn));
                    }
                }
            } catch (RuntimeException error) {
                CarDiagnostics.log(this, "Nav seed failed " + error.getClass().getSimpleName());
            }
            publish(epoch);
        });
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (!source(sbn)) return;
        int epoch = generation;
        worker.execute(() -> {
            if (navigation(sbn)) active.put(sbn.getKey(), parse(sbn));
            else active.remove(sbn.getKey());
            publish(epoch);
        });
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (!source(sbn)) return;
        int epoch = generation;
        worker.execute(() -> {
            active.remove(sbn.getKey());
            publish(epoch);
        });
    }

    private void publish(int epoch) {
        NavigationState latest = null;
        for (NavigationState candidate : active.values()) {
            if (latest == null || candidate.postedAt >= latest.postedAt) latest = candidate;
        }
        NavigationState snapshot = latest;
        getMainExecutor().execute(() -> {
            if (epoch == generation) NavigationRepository.get().publish(this, snapshot);
        });
    }

    private NavigationState parse(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras == null ? Bundle.EMPTY : notification.extras;
        NavigationParser parsed = NavigationParser.parse(string(extras, Notification.EXTRA_TITLE),
                string(extras, "android.shortCriticalText"),
                string(extras, Notification.EXTRA_SUB_TEXT),
                extras.getInt(Notification.EXTRA_PROGRESS, 0),
                extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0));
        Bitmap arrow = null;
        try {
            Icon icon = notification.getLargeIcon();
            if (icon != null) {
                Drawable drawable = icon.loadDrawable(this);
                if (drawable != null) {
                    int width = Math.max(1, Math.min(256, drawable.getIntrinsicWidth()));
                    int height = Math.max(1, Math.min(256, drawable.getIntrinsicHeight()));
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    drawable.setBounds(0, 0, width, height);
                    drawable.draw(new Canvas(bitmap));
                    arrow = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                    bitmap.recycle();
                }
            }
        } catch (RuntimeException error) {
            CarDiagnostics.log(this, "Nav arrow unavailable " + error.getClass().getSimpleName());
        }
        return new NavigationState(parsed, arrow, sbn.getPostTime());
    }

    private static String string(Bundle extras, String key) {
        CharSequence value = extras.getCharSequence(key);
        return value == null ? null : value.toString();
    }

    @Override public void onListenerDisconnected() {
        generation++;
        NavigationRepository.get().publish(this, null);
    }

    @Override public void onDestroy() {
        generation++;
        worker.shutdownNow();
        NavigationRepository.get().publish(this, null);
        super.onDestroy();
    }
}
