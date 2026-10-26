package local.driveboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Local wall-clock scheduling, independent of Android. */
public final class RoutineSchedule {
    private RoutineSchedule() {}

    public static Routine due(List<Routine> routines, LocalDateTime now,
            Map<String, LocalDate> lastRuns) {
        Routine selected = null;
        LocalTime earliest = null;
        LocalTime time = now.toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        for (Routine routine : routines) {
            if (!routine.enabled || !routine.days.contains(now.getDayOfWeek())
                    || now.toLocalDate().equals(lastRuns.get(routine.id))) {
                continue;
            }
            try {
                LocalTime start = LocalTime.parse(routine.startTime);
                LocalTime end = LocalTime.parse(routine.endTime);
                if (!time.isBefore(start) && !time.isAfter(end)
                        && (earliest == null || start.isBefore(earliest))) {
                    selected = routine;
                    earliest = start;
                }
            } catch (DateTimeParseException ignored) {
                // An invalid saved window cannot become due.
            }
        }
        return selected;
    }
}
