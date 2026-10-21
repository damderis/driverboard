package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.*;

public class MediaControlLayoutTest {
    @Test
    public void laysOutThreeSquareTargetsAtBottomWithRequiredGaps() {
        MediaControlLayout layout = MediaControlLayout.create(0, 0, 360, 140, 1f);
        assertNotNull(layout.previous);
        assertNotNull(layout.next);
        assertEquals(layout.previous.right - layout.previous.left,
                layout.previous.bottom - layout.previous.top, 0.001f);
        assertEquals(140f, layout.previous.bottom, 0f);
        assertEquals(140f, layout.playPause.bottom, 0f);
        assertEquals(140f, layout.next.bottom, 0f);
        assertTrue(layout.playPause.left - layout.previous.right >= 12f);
        assertTrue(layout.next.left - layout.playPause.right >= 12f);
    }

    @Test
    public void hitUsesInclusiveEdgesAndLeavesGapsUnassigned() {
        MediaControlLayout layout = MediaControlLayout.create(0, 0, 360, 140, 1f);
        float y = (layout.previous.top + layout.previous.bottom) / 2f;
        assertEquals(MediaControlLayout.Control.PREVIOUS,
                layout.hit(layout.previous.left, y));
        assertEquals(MediaControlLayout.Control.PLAY_PAUSE,
                layout.hit(layout.playPause.right, y));
        assertEquals(MediaControlLayout.Control.NEXT,
                layout.hit(layout.next.right, y));
        assertNull(layout.hit((layout.previous.right + layout.playPause.left) / 2f, y));
        assertNull(layout.hit(-1f, y));
        assertNull(layout.hit(Float.NaN, y));
        assertNull(layout.hit(Float.POSITIVE_INFINITY, y));
    }

    @Test
    public void fallsBackToCenteredPlayTargetWhenRowOrHeightCannotFit() {
        MediaControlLayout narrow = MediaControlLayout.create(10, 20, 190, 160, 1f);
        assertNull(narrow.previous);
        assertNull(narrow.next);
        assertEquals(100f, (narrow.playPause.left + narrow.playPause.right) / 2f, 0f);
        assertEquals(160f, narrow.playPause.bottom, 0f);

        MediaControlLayout shortTile = MediaControlLayout.create(10, 20, 310, 80, 1f);
        assertNull(shortTile.previous);
        assertNull(shortTile.next);
        assertEquals(80f, shortTile.playPause.bottom, 0f);
        assertEquals(76f, shortTile.playPause.right - shortTile.playPause.left, 0f);
    }

    @Test
    public void scalesMinimumTargetAndCentersItForImpossiblySmallRect() {
        MediaControlLayout layout = MediaControlLayout.create(4, 8, 30, 28, 2f);
        assertNull(layout.previous);
        assertNull(layout.next);
        assertEquals(152f, layout.playPause.right - layout.playPause.left, 0f);
        assertEquals(17f, (layout.playPause.left + layout.playPause.right) / 2f, 0f);
        assertEquals(28f, layout.playPause.bottom, 0f);
        assertSame(layout.playPause, layout.target(MediaControlLayout.Control.PLAY_PAUSE));
        assertNull(layout.target(null));
    }
}
