package local.driveboard;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Date-based eligibility policy. Missing dates never inherit another day's times. */
public final class PrayerAlertSchedule {
    public static final ZoneId ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    public static final long GRACE_MILLIS = 120_000;
    private PrayerAlertSchedule() {}

    public static final class Day {
        public final LocalDate date;
        public final LocalTime[] times;
        public Day(LocalDate date, LocalTime[] times) {
            if (times.length != 5) {
                throw new IllegalArgumentException("Five prayers required");
            }
            this.date = date;
            this.times = times.clone();
        }
    }
    public static final class Moment {
        public final LocalDate date;
        public final int prayer;
        public final boolean before;
        public final long epochMillis;
        public final LocalTime time;
        Moment(Day day, int prayer, boolean before, int lead) {
            this.date = day.date;
            this.prayer = prayer;
            this.before = before;
            this.time = day.times[prayer];
            epochMillis = day.date.atTime(time).atZone(ZONE).toInstant().toEpochMilli() - (before ? lead * 60_000L : 0);
        }
        public String key() {
            return date + ":" + prayer + ":" + (before ? "before" : "at");
        }
    }
    public static List<Moment> moments(List<Day> days, int lead) {
        List<Moment> result = new ArrayList<>();
        for (Day day : days) {
            for (int i = 0; i < 5; i++) {
                result.add(new Moment(day, i, true, lead));
                result.add(new Moment(day, i, false, lead));
            }
        }
        result.sort(Comparator.comparingLong(m -> m.epochMillis));
        return result;
    }
    public static List<Moment> due(List<Day> days, int lead, long now, Set<String> posted) {
        List<Moment> result = new ArrayList<>();
        for (Moment m : moments(days, lead)) {
            long prayerAt = m.date.atTime(m.time).atZone(ZONE).toInstant().toEpochMilli();
            if (!posted.contains(m.key()) && now >= m.epochMillis && now - m.epochMillis <= GRACE_MILLIS
                    && (!m.before || now < prayerAt)) {
                result.add(m);
            }
        }
        return result;
    }
    public static Moment nextDue(List<Day> days, int lead, long now, Set<String> posted) {
        List<Moment> eligible = due(days, lead, now, posted);
        return eligible.isEmpty() ? null : eligible.get(0);
    }

    public static long next(List<Day> days, int lead, long now, Set<String> posted) {
        long next = Long.MAX_VALUE;
        for (Moment m : moments(days, lead)) {
            if (m.epochMillis > now && !posted.contains(m.key())) {
                next = Math.min(next, m.epochMillis);
            }
        }
        return next;
    }
}
