package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LyricsMatchTest {
    @Test
    public void stripsOnlyDecorativeTrailingTitleBrackets() {
        assertEquals("Hello", LyricsMatch.title("Hello (Official Video)"));
        assertEquals("Hello", LyricsMatch.title("Hello [Music Video]"));
        assertEquals("Hello", LyricsMatch.title("Hello (Lyric Video)"));
        assertEquals("Hello", LyricsMatch.title("Hello [Audio]"));
        assertEquals("Hello", LyricsMatch.title("Hello (Remastered)"));
        assertEquals("Hello", LyricsMatch.title("Hello [Live]"));
        assertEquals("Hello", LyricsMatch.title("Hello (Official Video) [Audio]"));
        assertEquals("Hello", LyricsMatch.title("Hello [Remastered 2011 Edition]"));
        assertEquals("Hello", LyricsMatch.title("Hello (Live Wembley 1992)"));
        assertEquals("Hello (Live) Again", LyricsMatch.title("Hello (Live) Again"));
        assertEquals("Hello (Acoustic)", LyricsMatch.title("Hello (Acoustic)"));
    }

    @Test
    public void normalizesFeatureAndTopicOnlyForMatching() {
        assertEquals("Hello", LyricsMatch.title("Hello feat. Someone"));
        assertEquals("Hello", LyricsMatch.title("Hello ft Someone"));
        assertEquals("Hello", LyricsMatch.title("Hello featuring Someone"));
        assertEquals("Hello", LyricsMatch.title("Hello feat. Someone [Official Video]"));
        assertEquals("Left Behind", LyricsMatch.title("Left Behind"));
        assertEquals("Song", LyricsMatch.title("Song - feat Artist"));
        assertEquals("Adele", LyricsMatch.leadArtist("Adele - Topic"));
        assertTrue(LyricsMatch.sameArtist("Adele - Topic", "Adele feat. Someone"));
        assertTrue(LyricsMatch.sameTitle("Hello (Official Video)", "Hello"));
    }

    @Test
    public void durationWindowIsWiderForSoleMatch() {
        assertTrue(LyricsMatch.durationMatches(200_000, 219, 1));
        assertFalse(LyricsMatch.durationMatches(200_000, 219, 2));
        assertTrue(LyricsMatch.durationMatches(200_000, 212, 2));
    }
}
