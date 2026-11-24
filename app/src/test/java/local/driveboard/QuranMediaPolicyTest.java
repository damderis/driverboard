package local.driveboard;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class QuranMediaPolicyTest {
    @Test public void formatsAyahAndSurahOnlyFromPhoneMetadata() {
        assertEquals("Al-Wāqiʿah · 92", QuranMediaPolicy.title(
                "Surah Al-Wāqiʿah, Ayah 92", "Mishary Al-Afasy", false));
        assertEquals("Al-Wāqiʿah", QuranMediaPolicy.title("Surah Al-Wāqiʿah", "", true));
        assertEquals("Quran", QuranMediaPolicy.title(null, null, false));
        assertEquals("Al-Wāqiʿah · 92 · Mishary Al-Afasy",
                QuranMediaPolicy.lastRecitation("Al-Wāqiʿah · 92", "Mishary Al-Afasy"));
    }

    @Test public void prefersPhoneTagEvenWhenAnotherSessionHasProgress() {
        assertEquals(1, QuranMediaPolicy.select(Arrays.asList("legacy", "QuranMediaSession"),
                Arrays.asList(candidate(3, 900, false), candidate(3, 0, false))));
    }

    @Test public void fallbackReusesStaleZeroRankingAndRejectsCarSession() {
        assertEquals(1, QuranMediaPolicy.select(Arrays.asList("legacy", "legacy"),
                Arrays.asList(candidate(3, 0, false), candidate(2, 1200, true))));
        assertEquals(-1, QuranMediaPolicy.select(
                Collections.singletonList("androidx.media3.session.id."),
                Collections.singletonList(candidate(3, 999, false))));
        assertFalse(QuranMediaPolicy.allowed("local.driveboard", "QuranMediaSession"));
        assertEquals(-1, QuranMediaPolicy.select(Collections.emptyList(),
                Collections.emptyList()));
    }

    @Test public void duplicatePhoneTagsStillApplyStaleZeroRanking() {
        assertEquals(1, QuranMediaPolicy.select(
                Arrays.asList("QuranMediaSession", "QuranMediaSession"),
                Arrays.asList(candidate(3, 0, false), candidate(2, 1200, true))));
    }

    @Test public void buildsLocalizedPrayerTitlesAndNextMarker() {
        QuranMediaPolicy.PrayerItem next = QuranMediaPolicy.prayerItem(
                2, "16:09", true, "dalam 2j 55min", true);
        assertEquals("Asar 16:09", next.title);
        assertEquals("seterusnya · dalam 2j 55min", next.subtitle);
        assertEquals("Asar 16:09 · dalam 2j 55min",
                QuranMediaPolicy.nextPrayer(2, "16:09", "dalam 2j 55min", true));
        QuranMediaPolicy.PrayerItem ordinary = QuranMediaPolicy.prayerItem(
                0, "05:45", false, "in 2h 55m", false);
        assertEquals("Fajr 05:45", ordinary.title);
        assertEquals("", ordinary.subtitle);
        assertEquals("next · in 5m", QuranMediaPolicy.prayerItem(
                4, "20:00", true, "in 5m", false).subtitle);
    }

    @Test public void resumeDecisionRequiresSettingAndNoPlayingProgress() {
        assertFalse(QuranMediaPolicy.resume(false, true, false, 0, 0));
        assertFalse(QuranMediaPolicy.resume(true, false, false, 0, 0));
        assertFalse(QuranMediaPolicy.resume(true, true, true, 200, 0));
        assertFalse(QuranMediaPolicy.resume(true, true, true, 0, 200));
        assertTrue(QuranMediaPolicy.resume(true, true, true, 0, 0));
        assertTrue(QuranMediaPolicy.resume(true, true, false, 200, 200));
    }

    @Test public void claimsOnlyOnceUntilDisconnectAndReconnect() {
        QuranMediaPolicy.ResumeGate gate = new QuranMediaPolicy.ResumeGate();
        assertFalse(gate.claim(true, false, 0, 0));
        assertTrue(gate.connectionChanged(true));
        assertTrue(gate.claim(true, false, 0, 0));
        assertFalse(gate.connectionChanged(true));
        assertFalse(gate.claim(true, false, 0, 0));
        assertFalse(gate.connectionChanged(false));
        assertFalse(gate.claim(true, false, 0, 0));
        assertTrue(gate.connectionChanged(true));
        assertTrue(gate.claim(true, false, 0, 0));
    }

    @Test public void disabledAndAlreadyPlayingConnectionsConsumeTheirAttempt() {
        QuranMediaPolicy.ResumeGate gate = new QuranMediaPolicy.ResumeGate();
        gate.connectionChanged(true);
        assertFalse(gate.claim(false, false, 0, 0));
        assertFalse(gate.claim(true, false, 0, 0));
        gate.connectionChanged(false);
        gate.connectionChanged(true);
        assertFalse(gate.claim(true, true, 1000, 0));
        assertFalse(gate.claim(true, false, 0, 0));
    }

    private static WidgetData.MediaCandidate candidate(int state, long position,
            boolean resumable) {
        return new WidgetData.MediaCandidate(state, position, 0, QuranMediaPolicy.PACKAGE,
                resumable);
    }
}
