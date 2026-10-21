package local.driveboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BoardTransitionTest {
    @Test
    public void easingHasExactEndpointsAndAcceleratesEarly() {
        assertEquals(0f, BoardTransition.progress(-1), 0f);
        assertEquals(0f, BoardTransition.progress(0), 0f);
        assertEquals(1f, BoardTransition.progress(BoardTransition.DURATION_MS), 0f);
        assertEquals(1f, BoardTransition.progress(999), 0f);
        assertTrue(BoardTransition.progress(105) > .5f);
        assertTrue(BoardTransition.progress(210) > BoardTransition.progress(105));
        float previous = 0f;
        for (long elapsed = 1; elapsed <= BoardTransition.DURATION_MS; elapsed++) {
            float current = BoardTransition.progress(elapsed);
            assertTrue(current >= previous);
            assertTrue(current >= 0f && current <= 1f);
            previous = current;
        }
    }

    @Test
    public void rectsInterpolateInFrameCoordinates() {
        BoardTransition.Rect old = new BoardTransition.Rect(10, 20, 110, 70);
        BoardTransition.Rect target = new BoardTransition.Rect(100, 80, 300, 180);
        BoardTransition.Rect halfway = BoardTransition.Rect.interpolate(old, target, .5f);
        assertEquals(55f, halfway.left, 0.001f);
        assertEquals(50f, halfway.top, 0.001f);
        assertEquals(205f, halfway.right, 0.001f);
        assertEquals(125f, halfway.bottom, 0.001f);
        BoardTransition.Rect local = halfway.offset(-25, -30);
        assertEquals(30f, local.left, 0.001f);
        assertEquals(20f, local.top, 0.001f);
        BoardTransition.Rect irregular = new BoardTransition.Rect(11.3f, 29.7f,
                112.4f, 73.8f);
        assertTrue(BoardTransition.Rect.interpolate(irregular, target, 0f) == irregular);
        assertTrue(BoardTransition.Rect.interpolate(old, irregular, 1f) == irregular);
    }

    @Test
    public void newAndOldCardsHaveExpectedInitialVisuals() {
        BoardTransition.Rect target = new BoardTransition.Rect(0, 0, 100, 50);
        BoardTransition.Rect initial = target.scale(.92f);
        assertEquals(4f, initial.left, 0.001f);
        assertEquals(2f, initial.top, 0.001f);
        assertEquals(0f, BoardTransition.incomingAlpha(0), 0f);
        assertEquals(0f, BoardTransition.outgoingAlpha(.6f), 0f);
        assertEquals(0f, BoardTransition.outgoingAlpha(1f), 0f);
    }
}
