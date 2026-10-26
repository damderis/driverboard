package local.driveboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public class QariProbeTest {
    @Test public void noHitStopsAfterFifteenIds() {
        assertEquals(14, QariProbe.lastIdToCheck(-1));
        assertEquals(7, QariProbe.batchEnd(0, -1, 8));
        assertEquals(14, QariProbe.batchEnd(8, -1, 8));
        assertTrue(QariProbe.complete(15, -1));
    }

    @Test public void lateHitExtendsWindowExactlyFifteenMisses() {
        assertEquals(29, QariProbe.lastIdToCheck(14));
        assertEquals(21, QariProbe.batchEnd(14, 14, 8));
        assertEquals(29, QariProbe.batchEnd(22, 14, 8));
        assertTrue(QariProbe.complete(30, 14));
        assertEquals(44, QariProbe.lastIdToCheck(29));
    }

    @Test public void scanNeverPassesConfiguredMaximum() {
        assertEquals(119, QariProbe.lastIdToCheck(118));
        assertEquals(119, QariProbe.batchEnd(116, 118, 8));
        assertTrue(QariProbe.complete(120, 119));
    }

    @Test public void noHitSequenceVisitsExactlyZeroThroughFourteen() {
        assertEquals(ids(0, 14), scan());
    }

    @Test public void hitAtTrailingBatchBoundaryExtendsTheScan() {
        // Seven is the last ID of the first batch. Twenty-two is the end of
        // its fifteen-miss window and becomes another hit at that boundary.
        assertEquals(ids(0, 37), scan(7, 22));
    }

    @Test public void repeatedLateHitsReachButNeverPassOneHundredNineteen() {
        assertEquals(ids(0, 119), scan(14, 29, 44, 59, 74, 89, 104, 119));
    }

    private static List<Integer> scan(int... hits) {
        Set<Integer> hitIds = new HashSet<>();
        for (int hit : hits) hitIds.add(hit);
        List<Integer> visited = new ArrayList<>();
        int next = 0;
        int highestHit = -1;
        while (!QariProbe.complete(next, highestHit)) {
            int end = QariProbe.batchEnd(next, highestHit, 8);
            for (int id = next; id <= end; id++) {
                visited.add(id);
                if (hitIds.contains(id)) highestHit = Math.max(highestHit, id);
            }
            next = end + 1;
        }
        return visited;
    }

    private static List<Integer> ids(int first, int last) {
        return IntStream.rangeClosed(first, last).boxed().toList();
    }
}
