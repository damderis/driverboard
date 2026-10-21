package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.*;

public class LyricsDocumentTest {
    @Test public void parsesMultipleTagsFractionsAndOffset() {
        LyricsDocument lyrics = LyricsDocument.parse(
                "[offset:-250]\n[00:02.50][00:04.125]Hello\n[00:03.1]World", "");
        assertTrue(lyrics.isTimed());
        assertEquals(3, lyrics.lines.length);
        assertEquals(2250, lyrics.lines[0].timeMs);
        assertEquals("Hello", lyrics.lines[0].text);
        assertEquals(2850, lyrics.lines[1].timeMs);
        assertEquals("World", lyrics.lines[1].text);
        assertEquals(3875, lyrics.lines[2].timeMs);
        assertEquals(-1, lyrics.lineIndexAt(2249));
        assertEquals(0, lyrics.lineIndexAt(2250));
        assertEquals(1, lyrics.lineIndexAt(3000));
        assertEquals(2, lyrics.lineIndexAt(3875));
    }

    @Test public void blankTimedLineClearsVocalDuringGap() {
        LyricsDocument lyrics = LyricsDocument.parse(
                "[00:01.00]First\n[00:03.00]\n[00:06.00]Next", "");
        assertEquals(3, lyrics.lines.length);
        assertEquals("", lyrics.lines[lyrics.lineIndexAt(4000)].text);
    }

    @Test public void plainLyricsRemainStatic() {
        LyricsDocument lyrics = LyricsDocument.parse(null, "  First\nSecond  ");
        assertFalse(lyrics.isTimed());
        assertEquals(-1, lyrics.lineIndexAt(10_000));
        assertEquals("First\nSecond", lyrics.plainText);
    }

    @Test public void malformedMetadataDoesNotBecomeLyrics() {
        LyricsDocument lyrics = LyricsDocument.parse(
                "[ar:Artist]\n[offset:oops]\n[00:70.00]Invalid\n[00:01]Valid", "");
        assertEquals(1, lyrics.lines.length);
        assertEquals("Valid", lyrics.lines[0].text);
    }

    @Test public void timedEmptyLinesAloneAreUnavailable() {
        LyricsDocument lyrics = LyricsDocument.parse("[00:01.00]\n[00:02.00]", "");
        assertTrue(lyrics.isEmpty());
    }

    @Test public void artistMatchingIgnoresSeparators() {
        assertTrue(LyricsDocument.sameArtist("Aziz Harun & Dalia Farhana", "Aziz Harun, Dalia Farhana"));
        assertTrue(LyricsDocument.sameArtist("Skye Newman feat. Someone", "Skye Newman"));
        assertFalse(LyricsDocument.sameArtist("Lyla", "Aziz Harun"));
        assertFalse(LyricsDocument.sameArtist("", ""));
    }
}
