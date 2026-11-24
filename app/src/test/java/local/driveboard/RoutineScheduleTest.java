package local.driveboard;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class RoutineScheduleTest {
    private final Routine morning = new Routine("morning", "Morning", true,
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), "05:00", "11:00",
            Arrays.asList(55, 56), 1);
    private final Routine friday = new Routine("friday", "Friday", true,
            EnumSet.of(DayOfWeek.FRIDAY), "16:00", "23:59", Arrays.asList(18), 1);
    private final List<Routine> routines = Arrays.asList(morning, friday);

    @Test
    public void mondayMorningIsDue() {
        assertEquals(morning, due("2026-10-26T07:00"));
    }

    @Test
    public void thursdayAfterWindowIsNotDue() {
        assertNull(due("2026-10-29T11:01"));
    }

    @Test
    public void fridayMorningIsNotDue() {
        assertNull(due("2026-10-30T07:00"));
    }

    @Test
    public void fridayEveningIsDue() {
        assertEquals(friday, due("2026-10-30T18:00"));
    }

    @Test
    public void alreadyRanTodayIsNotDue() {
        assertNull(RoutineSchedule.due(routines, LocalDateTime.parse("2026-10-26T07:00"),
                Collections.singletonMap("morning", LocalDate.parse("2026-10-26"))));
    }

    @Test
    public void automaticAttemptIsClaimedOnlyOncePerRoutineAndDay() {
        java.util.Map<String, LocalDate> runs = new java.util.HashMap<>();
        LocalDateTime firstConnection = LocalDateTime.parse("2026-10-26T07:00");
        assertEquals(morning, RoutineSchedule.claimDue(routines, firstConnection, runs,
                (id, date) -> {
                    if (date.equals(runs.get(id))) return false;
                    runs.put(id, date);
                    return true;
                }));
        for (String reconnect : new String[]{"2026-10-26T07:30", "2026-10-26T09:00",
                "2026-10-26T11:00"}) {
            assertNull(RoutineSchedule.claimDue(routines, LocalDateTime.parse(reconnect), runs,
                    (id, date) -> {
                        if (date.equals(runs.get(id))) return false;
                        runs.put(id, date);
                        return true;
                    }));
        }
        assertEquals(morning, RoutineSchedule.claimDue(routines,
                LocalDateTime.parse("2026-10-27T07:00"), runs, (id, date) -> true));
    }

    @Test
    public void oneRoutineClaimDoesNotBlockAnotherRoutine() {
        java.util.Map<String, LocalDate> runs = new java.util.HashMap<>();
        runs.put("morning", LocalDate.parse("2026-10-30"));
        assertEquals(friday, RoutineSchedule.claimDue(routines,
                LocalDateTime.parse("2026-10-30T18:00"), runs,
                (id, date) -> !date.equals(runs.get(id))));
    }

    @Test
    public void failedPersistentClaimDoesNotStartPlayback() {
        assertNull(RoutineSchedule.claimDue(routines, LocalDateTime.parse("2026-10-26T07:00"),
                Collections.emptyMap(), (id, date) -> false));
    }

    @Test
    public void disabledIsNotDue() {
        morning.enabled = false;
        assertNull(due("2026-10-26T07:00"));
    }

    @Test
    public void endpointsAreInclusive() {
        assertEquals(morning, due("2026-10-26T05:00"));
        assertEquals(morning, due("2026-10-26T11:00"));
        assertEquals(morning, due("2026-10-26T11:00:59"));
    }

    @Test
    public void earliestWindowWinsEvenWhenListedLater() {
        Routine earlier = new Routine("earlier", "Earlier", true, morning.days,
                "04:00", "10:00", Arrays.asList(18), 0);
        assertEquals(earlier, RoutineSchedule.due(Arrays.asList(morning, earlier),
                LocalDateTime.parse("2026-10-26T07:00"), Collections.emptyMap()));
    }

    @Test
    public void yesterdayDoesNotBlockToday() {
        assertEquals(morning, RoutineSchedule.due(routines,
                LocalDateTime.parse("2026-10-26T07:00"),
                Collections.singletonMap("morning", LocalDate.parse("2026-10-25"))));
    }

    private Routine due(String now) {
        return RoutineSchedule.due(routines, LocalDateTime.parse(now), Collections.emptyMap());
    }
}
