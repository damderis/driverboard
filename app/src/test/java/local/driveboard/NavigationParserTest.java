package local.driveboard;

import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationParserTest {
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
