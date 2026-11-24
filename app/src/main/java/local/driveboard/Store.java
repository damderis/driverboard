package local.driveboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Store {
    public static final String FULL = "full";
    public static final String SPLIT = "split";
    public static final String NARROW = "narrow";
    private Store() {}
    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("driveboard", Context.MODE_PRIVATE);
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
        String base = SPLIT.equals(mode) ? "split_layout" : "full_layout";
        return page == 0 ? base : base + "_p" + page;
    }

    public static synchronized List<Routine> routines(Context context) {
        String saved = prefs(context).getString("quran_routines", null);
        List<Routine> result = new ArrayList<>();
        if (saved == null) {
            Context localized = AppLocale.wrap(context);
            result.add(new Routine("morning", localized.getString(R.string.routine_morning), true,
                    EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), "05:00", "11:00",
                    Arrays.asList(55, 56), 1));
            result.add(new Routine("friday", localized.getString(R.string.routine_friday), true,
                    EnumSet.of(DayOfWeek.FRIDAY), "16:00", "23:59", Arrays.asList(18), 1));
            saveRoutines(context, result);
            return result;
        }
        try {
            result.addAll(RoutineJson.decode(saved));
        } catch (Exception error) {
            CarDiagnostics.log(context, "Routine read failed " + error);
        }
        return result;
    }

    public static synchronized void saveRoutines(Context context, List<Routine> routines) {
        try {
            prefs(context).edit().putString("quran_routines", RoutineJson.encode(routines)).apply();
        } catch (Exception error) {
            CarDiagnostics.log(context, "Routine save failed " + error);
        }
    }

    public static String qariCatalogJson(Context context) {
        return prefs(context).getString("qari_catalog", null);
    }

    public static void saveQariCatalogJson(Context context, String json) {
        prefs(context).edit().putString("qari_catalog", json).apply();
    }
    public static synchronized Map<String, LocalDate> routineLastRuns(Context context) {
        Map<String, LocalDate> result = new HashMap<>();
        try {
            JSONObject saved = new JSONObject(prefs(context).getString("quran_routine_runs", "{}"));
            java.util.Iterator<String> keys = saved.keys();
            while (keys.hasNext()) {
                String id = keys.next();
                result.put(id, LocalDate.parse(saved.getString(id)));
            }
        } catch (Exception error) {
            CarDiagnostics.log(context, "Routine last-run read failed " + error);
        }
        return result;
    }

    /** Claims one automatic attempt per routine and local day, before any media connection. */
    public static synchronized boolean claimRoutineRun(Context context, String id, LocalDate date) {
        if (id == null || date == null || date.equals(routineLastRuns(context).get(id))) {
            return false;
        }
        try {
            JSONObject saved = new JSONObject(prefs(context).getString("quran_routine_runs", "{}"));
            saved.put(id, date.toString());
            // Commit before starting playback: process death or a failed connection must not
            // make the same routine start again on the next car connection that day.
            return prefs(context).edit().putString("quran_routine_runs", saved.toString()).commit();
        } catch (Exception error) {
            CarDiagnostics.log(context, "Routine last-run save failed " + error);
            return false;
        }
    }

    /** Play now is manual and does not consume the routine's daily automatic attempt. */
    public static synchronized void markRoutineRun(Context context, String id, LocalDate date) {
        claimRoutineRun(context, id, date);
    }

    public static BoardLayout layout(Context context, String mode) {
        return layout(context, mode, page(context));
    }

    public static BoardLayout layout(Context context, String mode, int page) {
        if (NARROW.equals(mode)) {
            // ponytail: the narrow strip always derives from the Drive page; give pages
            // their own narrow layouts if the e.MAS ever shows a narrow panel with pages.
            return BoardLayout.narrowDefaults(WidgetData.mediaSnapshot(context).playing,
                    layout(context, SPLIT, 0));
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
