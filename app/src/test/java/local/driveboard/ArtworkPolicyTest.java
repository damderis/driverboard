package local.driveboard;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ArtworkPolicyTest {
    @Test public void autoRequiresPlayingAndAvailable() {
        assertFalse(ArtworkPolicy.lyrics("auto", false, true));
        assertFalse(ArtworkPolicy.lyrics("auto", true, false));
        assertTrue(ArtworkPolicy.lyrics("auto", true, true));
        assertTrue(ArtworkPolicy.lyrics("lyrics", false, false));
        assertFalse(ArtworkPolicy.lyrics("board", true, true));
        assertEquals("board", ArtworkPolicy.cycle("auto"));
        assertEquals("lyrics", ArtworkPolicy.cycle("board"));
        assertEquals("auto", ArtworkPolicy.cycle("lyrics"));
    }
    @Test public void throttleAndVersionUseActualContent() {
        assertTrue(ArtworkPolicy.shouldRender(0, -1, false, false, 1, 1));
        assertFalse(ArtworkPolicy.shouldRender(1999, 0, true, true, 2, 1));
        assertTrue(ArtworkPolicy.shouldRender(2000, 0, true, false, 1, 1));
        assertFalse(ArtworkPolicy.shouldRender(2000, 0, false, false, 1, 1));
        assertTrue(ArtworkPolicy.shouldRender(2000, 0, false, false, 2, 1));
        assertFalse(ArtworkPolicy.changed(new byte[]{1}, new byte[]{1}));
        assertTrue(ArtworkPolicy.changed(new byte[]{1}, new byte[]{2}));
    }
    @Test public void excludesMirrorAndQuranCarOnly() {
        assertFalse(ArtworkPolicy.allowed("local.driveboard", "local.driveboard", "DriveBoardMirror"));
        assertFalse(ArtworkPolicy.allowed("local.driveboard", QuranMediaPolicy.PACKAGE, "media3.Session"));
        assertTrue(ArtworkPolicy.allowed("local.driveboard", QuranMediaPolicy.PACKAGE, "QuranMediaSession"));
        assertTrue(ArtworkPolicy.allowed("local.driveboard", "morphe", "media3.Session"));
    }
    @Test public void audioActionRequiresAvailability() {
        assertEquals(1, ArtworkPolicy.actions(false).size());
        assertEquals("view", ArtworkPolicy.actions(false).get(0));
        assertEquals(2, ArtworkPolicy.actions(true).size());
        assertEquals("audio", ArtworkPolicy.actions(true).get(1));
    }
}
