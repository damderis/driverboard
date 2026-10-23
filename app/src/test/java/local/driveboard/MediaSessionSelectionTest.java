package local.driveboard;

import android.media.session.PlaybackState;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public class MediaSessionSelectionTest {
    @Test public void playingSessionWithProgressBeatsStaleZeroSessionFromSamePackage() {
        ArrayList<WidgetData.MediaCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(PlaybackState.STATE_PLAYING, 0, 0, "quran", false));
        candidates.add(candidate(PlaybackState.STATE_PLAYING, 1200, 9000, "quran", false));

        assertEquals(1, WidgetData.selectMediaCandidate(candidates));
    }

    @Test public void pausedSessionBeatsStaleZeroSessionFromSamePackage() {
        ArrayList<WidgetData.MediaCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(PlaybackState.STATE_PLAYING, 0, 0, "quran", false));
        candidates.add(candidate(PlaybackState.STATE_PAUSED, 1200, 9000, "quran", true));

        assertEquals(1, WidgetData.selectMediaCandidate(candidates));
    }

    @Test public void loneZeroPositionPlayingSessionIsSelected() {
        ArrayList<WidgetData.MediaCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(PlaybackState.STATE_PLAYING, 0, 0, "music", false));

        assertEquals(0, WidgetData.selectMediaCandidate(candidates));
    }

    private static WidgetData.MediaCandidate candidate(int state, long position,
            long bufferedPosition, String packageName, boolean resumable) {
        return new WidgetData.MediaCandidate(state, position, bufferedPosition, packageName,
                resumable);
    }
}
