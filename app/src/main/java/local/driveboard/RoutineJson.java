package local.driveboard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** SharedPreferences JSON format for Quran routines. */
public final class RoutineJson {
    private RoutineJson() {}

    public static List<Routine> decode(String saved) throws JSONException {
        JSONArray array = new JSONArray(saved);
        List<Routine> routines = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject item = array.getJSONObject(i);
                EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
                JSONArray dayValues = item.getJSONArray("days");
                for (int j = 0; j < dayValues.length(); j++) {
                    days.add(DayOfWeek.of(dayValues.getInt(j)));
                }
                List<Integer> surahs = validSurahs(item.getJSONArray("surahs"));
                int repeats = item.optInt("repeatCount", 1);
                if (repeats < 1 || repeats > 5) {
                    repeats = 1;
                }
                routines.add(new Routine(item.getString("id"), item.getString("name"),
                        item.optBoolean("enabled", true), days, item.getString("startTime"),
                        item.getString("endTime"), surahs, item.optInt("qariId", 1), repeats));
            } catch (JSONException | DateTimeException | IllegalArgumentException error) {
                // A damaged item must not hide other saved routines.
            }
        }
        return routines;
    }

    public static String encode(List<Routine> routines) throws JSONException {
        JSONArray array = new JSONArray();
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
            JSONArray surahs = new JSONArray();
            if (routine.surahs != null) {
                for (Integer surah : routine.surahs) {
                    if (surah != null && surah >= 1 && surah <= 114 && surahs.length() < 30) {
                        surahs.put(surah);
                    }
                }
            }
            item.put("surahs", surahs);
            item.put("qariId", routine.qariId);
            item.put("repeatCount", routine.repeatCount >= 1 && routine.repeatCount <= 5
                    ? routine.repeatCount : 1);
            array.put(item);
        }
        return array.toString();
    }

    private static List<Integer> validSurahs(JSONArray values) throws JSONException {
        List<Integer> surahs = new ArrayList<>();
        for (int i = 0; i < values.length() && surahs.size() < 30; i++) {
            int number = values.getInt(i);
            if (number >= 1 && number <= 114) {
                surahs.add(number);
            }
        }
        return surahs;
    }
}
