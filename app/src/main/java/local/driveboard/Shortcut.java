package local.driveboard;

/**
 * One-tap car actions. {@link #action} is dispatched by BoardScreen.
 * No navigation shortcuts: Android Auto rejects NAVIGATE intents with a component, and
 * without one the host routes them back to DriveBoard (itself a navigation app).
 */
public enum Shortcut {
    QURAN(R.string.sc_quran, "media:com.quran.labs.androidquran"),
    MUSIC(R.string.sc_music, "media:app.morphe.android.apps.youtube.music"),
    LYRICS(R.string.lyrics, "lyrics"),
    YOUTUBE(R.string.browser_youtube, "web:https://m.youtube.com");

    public final int label;
    public final String action;

    Shortcut(int label, String action) {
        this.label = label;
        this.action = action;
    }

    /** Audio Switch volume step: snaps to the nearest 10 first, so 35 + 10 is 50 and 35 - 10 is 30. */
    public static int volumeStep(int level, int delta) {
        return Math.max(0, Math.min(100, Math.round(level / 10f) * 10 + delta));
    }

    /** Columns whose cells come closest to a 1.6:1 tile for {@code count} tiles in w x h. */
    public static int columns(int count, float width, float height) {
        int best = 1;
        double bestError = Double.MAX_VALUE;
        for (int cols = 1; cols <= count; cols++) {
            int rows = (count + cols - 1) / cols;
            double error = Math.abs(Math.log((width / cols) / (height / rows) / 1.6));
            if (error < bestError) {
                bestError = error;
                best = cols;
            }
        }
        return best;
    }
}
