package local.driveboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

public final class Store {
    public static final String FULL = "full";
    public static final String SPLIT = "split";
    public static final String NARROW = "narrow";
    private Store() {}
    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("driveboard", Context.MODE_PRIVATE);
    }

    public static boolean prayerAlertsEnabled(Context c) {
        return prefs(c).getBoolean("prayer_alerts_enabled", true);
    }
    public static void setPrayerAlertsEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("prayer_alerts_enabled", enabled).apply();
        PrayerAlerts.refresh(c);
    }
    public static int prayerAlertLeadMinutes(Context c) {
        return Math.max(1, Math.min(60, prefs(c).getInt("prayer_alert_lead", 15)));
    }
    public static void setPrayerAlertLeadMinutes(Context c, int minutes) {
        prefs(c).edit().putInt("prayer_alert_lead", Math.max(1, Math.min(60, minutes))).apply();
        PrayerAlerts.refresh(c);
    }
    public static Set<String> prayerAlertsPosted(Context c) {
        return new HashSet<>(prefs(c).getStringSet("prayer_alerts_posted", Collections.emptySet()));
    }
    public static void markPrayerAlertPosted(Context c, String key, String oldestDate) {
        Set<String> keys = prayerAlertsPosted(c);
        keys.removeIf(value -> value.compareTo(oldestDate) < 0);
        keys.add(key);
        prefs(c).edit().putStringSet("prayer_alerts_posted", keys).apply();
    }

    /** Car pages in action-strip order; page 0 keeps the original layout keys. */
    public static final int[] PAGE_NAMES = {R.string.page_drive, R.string.page_media,
            R.string.page_controls};

    public static int page(Context context) {
        return BoardLayout.clamp(prefs(context).getInt("car_page", 0), 0, PAGE_NAMES.length - 1);
    }

    public static void setPage(Context context, int page) {
        prefs(context).edit().putInt("car_page", Math.floorMod(page, PAGE_NAMES.length)).apply();
    }

    public static String layoutKey(String mode, int page) {
        String base = NARROW.equals(mode) ? "narrow_layout"
                : SPLIT.equals(mode) ? "split_layout" : "full_layout";
        return page == 0 ? base : base + "_p" + page;
    }

    public static BoardLayout layout(Context context, String mode) {
        return layout(context, mode, page(context));
    }

    public static BoardLayout narrowDefault(Context context) {
        return BoardLayout.narrowDefaults(WidgetData.mediaSnapshot(context).playing,
                layout(context, SPLIT, 0));
    }

    public static BoardLayout layout(Context context, String mode, int page) {
        if (NARROW.equals(mode)) {
            String saved = prefs(context).getString(layoutKey(mode, page), null);
            return saved == null ? narrowDefault(context) : BoardLayout.fromJson(saved, true);
        }
        SharedPreferences p = prefs(context);
        boolean split = SPLIT.equals(mode);
        String key = layoutKey(mode, page);
        String saved = p.getString(key, null);
        if (page > 0) {
            return saved == null ? BoardLayout.pageDefaults(page, split)
                    : BoardLayout.fromJson(saved, split);
        }
        String legacy = p.getString("layout", null);
        BoardLayout layout = BoardLayout.fromSaved(saved, legacy, split);
        boolean legacyFull = !split && saved == null && p.contains("layout");
        if (legacyFull || (saved != null && !saved.equals(layout.toJson()))) {
            SharedPreferences.Editor edit = p.edit().putString(key, layout.toJson());
            if (legacyFull) {
                edit.remove("layout");
            }
            edit.apply();
        }
        return layout;
    }
}
