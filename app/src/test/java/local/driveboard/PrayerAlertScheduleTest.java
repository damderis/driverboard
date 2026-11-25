package local.driveboard;

import org.junit.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class PrayerAlertScheduleTest {
    private final PrayerAlertSchedule.Day day = new PrayerAlertSchedule.Day(LocalDate.of(2026, 11, 25),
            new LocalTime[] {LocalTime.of(6, 0), LocalTime.of(13, 0), LocalTime.of(16, 0), LocalTime.of(19, 0), LocalTime.of(20, 0)});
    private long at(int hour, int minute, int second) {
        return day.date.atTime(hour, minute, second).atZone(PrayerAlertSchedule.ZONE).toInstant().toEpochMilli();
    }
    @Test public void beforeExactBoundaryAndGrace() {
        assertTrue(PrayerAlertSchedule.due(List.of(day), 15, at(5, 44, 59), Set.of()).isEmpty());
        assertEquals(1, PrayerAlertSchedule.due(List.of(day), 15, at(5, 45, 0), Set.of()).size());
        assertEquals(1, PrayerAlertSchedule.due(List.of(day), 15, at(5, 47, 0), Set.of()).size());
        assertTrue(PrayerAlertSchedule.due(List.of(day), 15, at(5, 47, 1), Set.of()).isEmpty());
    }
    @Test public void atBoundaryAndLateConnectNeverCatchUpOldReminder() {
        List<PrayerAlertSchedule.Moment> due = PrayerAlertSchedule.due(List.of(day), 15, at(6, 0, 0), Set.of());
        assertEquals(1, due.size()); assertFalse(due.get(0).before);
        assertTrue(PrayerAlertSchedule.due(List.of(day), 15, at(6, 2, 1), Set.of()).isEmpty());
    }
    @Test public void postedBeforeSurvivesLeadChangeAndReconnection() {
        PrayerAlertSchedule.Moment moment = PrayerAlertSchedule.due(List.of(day), 15, at(5, 45, 0), Set.of()).get(0);
        Set<String> persisted = new HashSet<>(); persisted.add(moment.key());
        assertTrue(PrayerAlertSchedule.due(List.of(day), 10, at(5, 50, 0), persisted).isEmpty());
        assertEquals(1, PrayerAlertSchedule.due(List.of(day), 10, at(6, 0, 0), persisted).size());
    }
    @Test public void tomorrowBeforeCanCrossMidnightAndUsesTomorrowDate() {
        PrayerAlertSchedule.Day tomorrow = new PrayerAlertSchedule.Day(day.date.plusDays(1),
                new LocalTime[] {LocalTime.of(0, 5), LocalTime.of(13, 0), LocalTime.of(16, 0), LocalTime.of(19, 0), LocalTime.of(20, 0)});
        List<PrayerAlertSchedule.Moment> due = PrayerAlertSchedule.due(List.of(day, tomorrow), 15, at(23, 50, 0), Set.of());
        assertEquals(1, due.size()); assertEquals(tomorrow.date, due.get(0).date);
        assertEquals(at(23, 50, 0), PrayerAlertSchedule.next(List.of(day, tomorrow), 15, at(23, 49, 0), Set.of()));
    }
    @Test public void missingTomorrowNeverReusesTodayAndPostedAtIsSuppressed() {
        assertEquals(Long.MAX_VALUE, PrayerAlertSchedule.next(List.of(day), 15, at(23, 59, 0), Set.of()));
        PrayerAlertSchedule.Moment at = PrayerAlertSchedule.due(List.of(day), 15, at(6, 0, 0), Set.of()).get(0);
        assertTrue(PrayerAlertSchedule.due(List.of(day), 15, at(6, 1, 0), Set.of(at.key())).isEmpty());
    }
    @Test public void shortLeadDoesNotPostBeforeOncePrayerStarts() {
        List<PrayerAlertSchedule.Moment> due = PrayerAlertSchedule.due(List.of(day), 1, at(6, 0, 0), Set.of());
        assertEquals(1, due.size()); assertFalse(due.get(0).before);
    }

    @Test public void changingLeadMovesUnpostedReminderAndNextWake() {
        assertNull(PrayerAlertSchedule.nextDue(List.of(day), 10, at(5, 45, 0), Set.of()));
        assertEquals(at(5, 50, 0),
                PrayerAlertSchedule.next(List.of(day), 10, at(5, 45, 0), Set.of()));
        assertTrue(PrayerAlertSchedule.nextDue(List.of(day), 10, at(5, 50, 0), Set.of()).before);
    }
    @Test public void scheduleUsesMalaysiaRegardlessOfDeviceTimezone() {
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"));
            long malaysiaBefore = java.time.Instant.parse("2026-11-24T21:45:00Z").toEpochMilli();
            assertEquals(1, PrayerAlertSchedule.due(List.of(day), 15, malaysiaBefore, Set.of()).size());
            assertEquals(malaysiaBefore, PrayerAlertSchedule.next(List.of(day), 15, malaysiaBefore - 1, Set.of()));
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }
}
