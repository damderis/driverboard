package local.driveboard;

import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationParserTest {
    @Test public void displayedDistancePrefixIsRemoved() {
        for (String text : new String[] {"600 m · Turn right", "600 m Turn right",
                "600 m — Turn right", "600 m\u00a0·\u00a0Turn right",
                "600\u00a0m · Turn right", "600\u202fm · Turn right"}) {
            assertEquals("Turn right", NavigationParser.parse(text, "600 m", "", 0, 0).instruction);
        }
        assertEquals("Continue", NavigationParser.withoutDistancePrefix("1.2 km · Continue", "1.2 km"));
    }
    @Test public void unrelatedOrMissingHeadlinePreservesInstruction() {
        assertEquals("600 m · Turn", NavigationParser.withoutDistancePrefix("600 m · Turn", ""));
        assertEquals("Turn onto Route 600", NavigationParser.withoutDistancePrefix("Turn onto Route 600", "600 m"));
        assertEquals("600 miles ahead", NavigationParser.withoutDistancePrefix("600 miles ahead", "600 m"));
        assertEquals("700 m · Turn", NavigationParser.withoutDistancePrefix("700 m · Turn", "600 m"));
        assertEquals("", NavigationParser.withoutDistancePrefix(null, "600 m"));
    }
    @Test public void emptyDistanceKeepsInstructionAndEta() {
        NavigationParser result = NavigationParser.parse("Head towards Jalan Padi Ria", "",
                "Arrive 08:48", 250, 1000);
        assertEquals("Head towards Jalan Padi Ria", result.instruction);
        assertEquals("", result.distance);
        assertEquals("Arrive 08:48", result.eta);
        assertEquals(.25f, result.progress, .0001f);
    }
    @Test public void nullSubTextAndTextAreSafe() {
        NavigationParser result = NavigationParser.parse(null, " 200 m ", null, 0, 100);
        assertEquals("", result.instruction);
        assertEquals("200 m", result.distance);
        assertEquals("", result.eta);
    }
    @Test public void zeroMaximumAndOutOfRangeProgressAreClamped() {
        assertEquals(0f, NavigationParser.parse("", "", "", 20, 0).progress, 0f);
        assertEquals(0f, NavigationParser.parse("", "", "", -20, 100).progress, 0f);
        assertEquals(1f, NavigationParser.parse("", "", "", 200, 100).progress, 0f);
    }
}
