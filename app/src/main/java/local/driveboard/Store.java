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

    public static String layoutKey(String mode) {
        return SPLIT.equals(mode) ? "split_layout" : "full_layout";
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
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
                JSONArray dayValues = item.getJSONArray("days");
                for (int j = 0; j < dayValues.length(); j++) {
                    days.add(DayOfWeek.of(dayValues.getInt(j)));
                }
                List<Integer> surahs = new ArrayList<>();
                JSONArray surahValues = item.getJSONArray("surahs");
                for (int j = 0; j < surahValues.length(); j++) {
                    int surah = surahValues.getInt(j);
                    if (surah >= 1 && surah <= 114) {
                        surahs.add(surah);
                    }
                }
                result.add(new Routine(item.getString("id"), item.getString("name"),
                        item.optBoolean("enabled", true), days, item.getString("startTime"),
                        item.getString("endTime"), surahs, item.optInt("qariId", 1)));
            }
        } catch (Exception error) {
            CarDiagnostics.log(context, "Routine read failed " + error);
        }
        return result;
    }

    public static synchronized void saveRoutines(Context context, List<Routine> routines) {
        JSONArray array = new JSONArray();
        try {
            for (Routine routine : routines) {
                JSONObject item = new JSONObject();
                item.put("id", routine.id);
                item.put("name", routine.name);
                item.put("enabled", routine.enabled);
                JSONArray days = new JSONArray();
                for (DayOfWeek day : routine.days) {
                    days.put(day.getValue());
                }
                item.put("days", days);
                item.put("startTime", routine.startTime);
                item.put("endTime", routine.endTime);
                item.put("surahs", new JSONArray(routine.surahs));
                item.put("qariId", routine.qariId);
                array.put(item);
            }
            prefs(context).edit().putString("quran_routines", array.toString()).apply();
        } catch (Exception error) {
            CarDiagnostics.log(context, "Routine save failed " + error);
        }
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

    public static synchronized void markRoutineRun(Context context, String id, LocalDate date) {
        Map<String, LocalDate> runs = routineLastRuns(context);
        runs.put(id, date);
        JSONObject saved = new JSONObject();
        try {
            for (Map.Entry<String, LocalDate> run : runs.entrySet()) {
                saved.put(run.getKey(), run.getValue().toString());
            }
            prefs(context).edit().putString("quran_routine_runs", saved.toString()).apply();
        } catch (Exception error) {
            CarDiagnostics.log(context, "Routine last-run save failed " + error);
        }
    }

    public static BoardLayout layout(Context context, String mode) {
        if (NARROW.equals(mode)) {
            return BoardLayout.narrowDefaults(WidgetData.mediaSnapshot(context).playing,
                    layout(context, SPLIT));
        }
        SharedPreferences p = prefs(context);
        String saved = p.getString(layoutKey(mode), null);
        String legacy = p.getString("layout", null);
        boolean split = SPLIT.equals(mode);
        BoardLayout layout = BoardLayout.fromSaved(saved, legacy, split);
        boolean legacyFull = !split && saved == null && p.contains("layout");
        if (legacyFull || (saved != null && saved.contains("\"battery\""))) {
            SharedPreferences.Editor edit = p.edit().putString(layoutKey(mode), layout.toJson());
            if (legacyFull) {
                edit.remove("layout");
            }
            edit.apply();
        }
        return layout;
    }
}
