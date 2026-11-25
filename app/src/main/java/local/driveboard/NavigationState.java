package local.driveboard;

import android.graphics.Bitmap;

/** Immutable snapshot; arrow is an immutable bitmap owned by this snapshot. */
public final class NavigationState {
    public final String instruction;
    public final String distance;
    public final String eta;
    public final Bitmap arrow;
    public final float progress;
    public final long postedAt;

    public NavigationState(NavigationParser parsed, Bitmap arrow, long postedAt) {
        instruction = parsed.instruction;
        distance = parsed.distance;
        eta = parsed.eta;
        this.arrow = arrow;
        progress = parsed.progress;
        this.postedAt = postedAt;
    }
}
