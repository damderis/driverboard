package local.driveboard;

import org.junit.Test;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class RoutineInputsTest {
    @Test
    public void parsesNumbersNamesAndAscendingRanges() {
        assertEquals(Arrays.asList(18, 55, 56, 67),
                RoutineInputs.parseSurahs("18, 55-56, Al-Mulk"));
    }

    @Test
    public void acceptsThirtySurahs() {
        assertEquals(30, RoutineInputs.parseSurahs("1-30").size());
    }

    @Test
    public void rejectsInvalidEntriesAndOversizedRanges() {
        for (String input : Arrays.asList("", "0", "115", "56-55", "1-31",
                "unknown chapter", "18,,19", "18-", "2147483648")) {
            try {
                RoutineInputs.parseSurahs(input);
                fail("Accepted invalid input: " + input);
            } catch (IllegalArgumentException expected) {
                // Each invalid value must be rejected before it reaches the routine.
            }
        }
    }

    @Test
    public void expandsTheWholeSequenceInOrder() {
        Routine routine = routine(Arrays.asList(55, 56), 3);
        assertEquals(Arrays.asList(55, 56, 55, 56, 55, 56), routine.expandedSurahs());
        assertEquals(1, new Routine("old", "Old", true, routine.days, "05:00", "11:00",
                Arrays.asList(55), 1).repeatCount);
    }

    @Test
    public void expansionRejectsInvalidMutableValues() {
        Routine routine = routine(Arrays.asList(55), 0);
        expectInvalid(routine);
        routine.repeatCount = 6;
        expectInvalid(routine);
        routine.repeatCount = 1;
        routine.surahs = Arrays.asList(0);
        expectInvalid(routine);
        routine.surahs = Collections.nCopies(31, 55);
        expectInvalid(routine);
    }

    private static Routine routine(java.util.List<Integer> surahs, int repeatCount) {
        return new Routine("test", "Test", true, EnumSet.of(DayOfWeek.MONDAY),
                "05:00", "11:00", surahs, 1, repeatCount);
    }

    private static void expectInvalid(Routine routine) {
        try {
            routine.expandedSurahs();
            fail("Expected invalid routine");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }
}
