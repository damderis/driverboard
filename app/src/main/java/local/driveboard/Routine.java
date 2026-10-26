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
    public int repeatCount;

    public Routine(String id, String name, boolean enabled, Set<DayOfWeek> days,
            String startTime, String endTime, List<Integer> surahs, int qariId) {
        this(id, name, enabled, days, startTime, endTime, surahs, qariId, 1);
    }

    public Routine(String id, String name, boolean enabled, Set<DayOfWeek> days,
            String startTime, String endTime, List<Integer> surahs, int qariId,
            int repeatCount) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
        this.days = new LinkedHashSet<>(days);
        this.startTime = startTime;
        this.endTime = endTime;
        this.surahs = new ArrayList<>(surahs);
        this.qariId = qariId;
        this.repeatCount = repeatCount;
    }

    /** Returns the playback queue, repeating the complete base sequence. */
    public List<Integer> expandedSurahs() {
        if (repeatCount < 1 || repeatCount > 5) {
            throw new IllegalArgumentException("Repeat count must be between 1 and 5");
        }
        if (surahs == null || surahs.size() > 30) {
            throw new IllegalArgumentException("A routine can contain at most 30 surahs");
        }
        for (Integer surah : surahs) {
            if (surah == null || surah < 1 || surah > 114) {
                throw new IllegalArgumentException("Surah number must be between 1 and 114");
            }
        }
        List<Integer> queue = new ArrayList<>(surahs.size() * repeatCount);
        for (int repeat = 0; repeat < repeatCount; repeat++) {
            queue.addAll(surahs);
        }
        return queue;
    }
}
