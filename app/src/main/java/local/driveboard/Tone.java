package local.driveboard;

import android.content.Context;

/** Compatibility values for the existing phone and car surfaces. */
public final class Tone {
    public final int ground;
    public final int panel;

    private Tone(BoardColours colours) {
        ground = colours.surface;
        panel = colours.surfaceContainerHigh;
    }

    public static Tone current(Context context) {
        return new Tone(BoardColours.current(context));
    }
}
