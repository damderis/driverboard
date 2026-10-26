package local.driveboard;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class RoutineJsonTest {
    @Test
    public void oldSavedRoutineDefaultsToOneRepeatAndPreservesFields() throws Exception {
        String old = "[{\"id\":\"morning\",\"name\":\"Morning\",\"enabled\":false,"
                + "\"days\":[1,4],\"startTime\":\"05:00\",\"endTime\":\"11:00\","
                + "\"surahs\":[55,56],\"qariId\":7}]";
        Routine routine = RoutineJson.decode(old).get(0);
        assertEquals("morning", routine.id);
        assertEquals("Morning", routine.name);
        assertFalse(routine.enabled);
        assertEquals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), routine.days);
        assertEquals("05:00", routine.startTime);
        assertEquals("11:00", routine.endTime);
        assertEquals(Arrays.asList(55, 56), routine.surahs);
        assertEquals(7, routine.qariId);
        assertEquals(1, routine.repeatCount);
    }

    @Test
    public void roundTripsRepeatCountThroughRealJson() throws Exception {
        Routine source = new Routine("night", "Night", true,
                EnumSet.of(DayOfWeek.FRIDAY), "20:00", "23:00",
                Arrays.asList(18, 67), 2, 4);
        String saved = RoutineJson.encode(Collections.singletonList(source));
        assertEquals(4, new JSONArray(saved).getJSONObject(0).getInt("repeatCount"));
        Routine restored = RoutineJson.decode(saved).get(0);
        assertEquals(source.id, restored.id);
        assertEquals(source.days, restored.days);
        assertEquals(source.surahs, restored.surahs);
        assertEquals(source.qariId, restored.qariId);
        assertEquals(4, restored.repeatCount);
    }

    @Test
    public void migrationKeepsFirstThirtyValidSurahs() throws Exception {
        JSONObject item = new JSONObject();
        item.put("id", "legacy");
        item.put("name", "Legacy");
        item.put("days", new JSONArray().put(2));
        item.put("startTime", "07:00");
        item.put("endTime", "08:00");
        JSONArray chapters = new JSONArray().put(0).put(115);
        for (int i = 1; i <= 35; i++) {
            chapters.put(i);
        }
        item.put("surahs", chapters);
        item.put("qariId", 9);
        item.put("repeatCount", 99);
        List<Routine> restored = RoutineJson.decode(new JSONArray().put(item).toString());
        assertEquals(30, restored.get(0).surahs.size());
        assertEquals(Integer.valueOf(1), restored.get(0).surahs.get(0));
        assertEquals(Integer.valueOf(30), restored.get(0).surahs.get(29));
        assertEquals(1, restored.get(0).repeatCount);
        assertEquals(9, restored.get(0).qariId);
        assertEquals(EnumSet.of(DayOfWeek.TUESDAY), restored.get(0).days);
    }

    @Test
    public void malformedEntryDoesNotHideValidRoutinesAroundIt() throws Exception {
        JSONObject first = savedRoutine("first");
        JSONObject invalid = savedRoutine("broken");
        invalid.put("days", new JSONArray().put(9));
        JSONObject last = savedRoutine("last");
        JSONArray saved = new JSONArray().put(first).put(invalid).put(last);

        List<Routine> restored = RoutineJson.decode(saved.toString());
        assertEquals(2, restored.size());
        assertEquals("first", restored.get(0).id);
        assertEquals("last", restored.get(1).id);
    }

    private static JSONObject savedRoutine(String id) throws Exception {
        JSONObject item = new JSONObject();
        item.put("id", id);
        item.put("name", id);
        item.put("days", new JSONArray().put(1));
        item.put("startTime", "05:00");
        item.put("endTime", "11:00");
        item.put("surahs", new JSONArray().put(55));
        return item;
    }
}
