package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static local.driveboard.ParkedState.State.*;

public class ParkedStateTest {
    @Test
    public void movingFixExpiresAfterTenSeconds() {
        assertEquals(MOVING, ParkedState.evaluate(true, 20_000, 10_000, true, 8.1, -1));
        assertEquals(UNKNOWN, ParkedState.evaluate(true, 20_001, 10_000, true, 30, -1));
    }

    @Test
    public void parkedThresholdAndFreshnessAreInclusive() {
        assertEquals(PARKED, ParkedState.evaluate(true, 130_000, 10_000, true, 8, -1));
        assertEquals(UNKNOWN, ParkedState.evaluate(true, 130_001, 10_000, true, 0, -1));
    }

    @Test
    public void noSpeedRequiresObservedStationaryFixesForTenSeconds() {
        assertEquals(UNKNOWN, ParkedState.evaluate(true, 30_000, 19_999, false, 0, 10_000));
        assertEquals(PARKED, ParkedState.evaluate(true, 30_000, 20_000, false, 0, 10_000));
        assertEquals(UNKNOWN, ParkedState.evaluate(true, 30_000, 20_000, false, 0, -1));
        assertEquals(UNKNOWN, ParkedState.evaluate(true, 150_001, 20_000, false, 0, 0));
    }

    @Test
    public void missingPermissionFixOrInvalidSpeedAlwaysLocks() {
        assertEquals(UNKNOWN, ParkedState.evaluate(false, 20_000, 20_000, true, 0, 0));
        assertEquals(UNKNOWN, ParkedState.evaluate(true, 20_000, -1, true, 0, 0));
        assertEquals(UNKNOWN, ParkedState.evaluate(true, 20_000, 20_001, true, 0, 0));
        assertEquals(UNKNOWN, ParkedState.evaluate(true, 20_000, 20_000, true, Double.NaN, 0));
    }
}
