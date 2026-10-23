package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuranPositionTest {
    @Test public void normalizesAccentsPunctuationAndCase() {
        assertEquals("misharialafasy", QuranPosition.normalize("Mishāri Al-`Afasy"));
        assertEquals("", QuranPosition.normalize(null));
    }

    @Test public void matchesPlayerNamesAndRecordingStyles() {
        assertEquals(6, QuranPosition.recitationId("Husary"));
        assertEquals(12, QuranPosition.recitationId("Husary Muallim"));
        assertEquals(9, QuranPosition.recitationId("Minshawi Mu..."));
        assertEquals(8, QuranPosition.recitationId("Minshawi Mujawwad"));
        assertEquals(7, QuranPosition.recitationId("Mishary Al-Afasy"));
        assertEquals(2, QuranPosition.recitationId("Abdul Basit"));
        assertEquals(1, QuranPosition.recitationId("AbdulBaset AbdulSamad Mujawwad"));
        assertEquals(3, QuranPosition.recitationId("Abdur-Rahman as-Sudais"));
        assertEquals(10, QuranPosition.recitationId("Sa`ud ash-Shuraym"));
        assertEquals(0, QuranPosition.recitationId("Saad Al Ghamdi"));
        assertEquals(0, QuranPosition.recitationId("Unknown"));
    }

    @Test public void intervalsHandleSeeksGapsBoundariesAndOverlaps() {
        long[] starts = {100, 1000, 1990};
        long[] ends = {900, 2000, 3000};
        assertEquals(0, QuranPosition.lookup(starts, ends, -1));
        assertEquals(0, QuranPosition.lookup(starts, ends, 0));
        assertEquals(1, QuranPosition.lookup(starts, ends, 100));
        assertEquals(0, QuranPosition.lookup(starts, ends, 900));
        assertEquals(2, QuranPosition.lookup(starts, ends, 1000));
        assertEquals(3, QuranPosition.lookup(starts, ends, 1990));
        assertEquals(1, QuranPosition.lookup(starts, ends, 500));
        assertEquals(0, QuranPosition.lookup(starts, ends, 3000));
    }

    @Test public void estimateWeightsTextAndClampsTrackEnd() {
        String[] verses = {null, "aa", "bbbbbb", "cc"};
        assertEquals(1, QuranPosition.estimate(verses, 0, 1000));
        assertEquals(2, QuranPosition.estimate(verses, 200, 1000));
        assertEquals(2, QuranPosition.estimate(verses, 700, 1000));
        assertEquals(3, QuranPosition.estimate(verses, 800, 1000));
        assertEquals(3, QuranPosition.estimate(verses, 1200, 1000));
        assertEquals(0, QuranPosition.estimate(verses, -1, 1000));
        assertEquals(0, QuranPosition.estimate(verses, 1, 0));
        assertEquals(0, QuranPosition.estimate(new String[] {null, "a", null}, 1, 100));
    }

    @Test public void metadataWinsAndTimingGapsDoNotBecomeEstimates() {
        assertEquals(4, QuranPosition.resolve(4, true, 2, 3));
        assertEquals(4, QuranPosition.resolve(4, false, 0, 3));
        assertEquals(2, QuranPosition.resolve(0, true, 2, 3));
        assertEquals(0, QuranPosition.resolve(0, true, 0, 3));
        assertEquals(3, QuranPosition.resolve(0, false, 0, 3));
        assertEquals(0, QuranPosition.resolve(0, false, 0, 0));
        assertFalse(QuranPosition.isApproximate(4, false, 3));
        assertFalse(QuranPosition.isApproximate(0, true, 3));
        assertTrue(QuranPosition.isApproximate(0, false, 3));
    }
}
