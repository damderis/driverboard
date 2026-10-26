package local.driveboard;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Editable phone-side Quran playback routine. */
public final class Routine {
    public String id;
    public String name;
    public boolean enabled;
    public Set<DayOfWeek> days;
    public String startTime;
    public String endTime;
    public List<Integer> surahs;
    public int qariId;

    public Routine(String id, String name, boolean enabled, Set<DayOfWeek> days,
            String startTime, String endTime, List<Integer> surahs, int qariId) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
        this.days = new LinkedHashSet<>(days);
        this.startTime = startTime;
        this.endTime = endTime;
        this.surahs = new ArrayList<>(surahs);
        this.qariId = qariId;
    }
}
