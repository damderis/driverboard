package local.driveboard;

/** Pure Java hit targets for the three media controls in a tile. */
public final class MediaControlLayout {
    private static final float MIN_TARGET_DP = 76f;
    private static final float GAP_DP = 12f;

    public enum Control {
        PREVIOUS,
        PLAY_PAUSE,
        NEXT
    }

    public static final class Target {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        private Target(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        private boolean contains(float x, float y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }

    public final Target previous;
    public final Target playPause;
    public final Target next;

    private MediaControlLayout(Target previous, Target playPause, Target next) {
        this.previous = previous;
        this.playPause = playPause;
        this.next = next;
    }

    /** Creates bottom-aligned square targets within the supplied tile rectangle. */
    public static MediaControlLayout create(float left, float top, float right,
                                            float bottom, float dpScale) {
        return create(left, top, right, bottom, dpScale,
                right - left, bottom - top);
    }

    /** Card dimensions cap compact visuals independently of screen density. */
    public static MediaControlLayout create(float left, float top, float right,
            float bottom, float dpScale, float cardWidth, float cardHeight) {
        float scale = Float.isFinite(dpScale) && dpScale > 0f ? dpScale : 1f;
        float width = Math.max(0f, right - left);
        float height = Math.max(0f, bottom - top);
        if (isCompact(cardHeight, width, scale)) {
            float size = Math.max(0f, Math.min(height,
                    Math.min(cardHeight * .34f, cardWidth * .18f)));
            size = Math.min(size, width);
            return new MediaControlLayout(null,
                    bottomAlignedCentered(left, right, bottom, size), null);
        }
        float minSize = MIN_TARGET_DP * scale;
        float gap = GAP_DP * scale;
        // Give the controls a comfortable size while reserving room around the row.
        float availableSize = Math.max(0f, (width - 2f * gap) / 3f);
        float preferredSize = Math.min(width / 4f, height * 0.30f);
        float size = Math.max(minSize, Math.min(preferredSize, availableSize));

        if (width < 3f * size + 2f * gap || height < size) {
            Target only = bottomAlignedCentered(left, right, bottom, minSize);
            return new MediaControlLayout(null, only, null);
        }

        float total = 3f * size + 2f * gap;
        float rowLeft = left + (width - total) / 2f;
        float rowTop = bottom - size;
        Target previous = square(rowLeft, rowTop, size);
        Target playPause = square(rowLeft + size + gap, rowTop, size);
        Target next = square(rowLeft + 2f * (size + gap), rowTop, size);
        return new MediaControlLayout(previous, playPause, next);
    }

    /** Three buttons of {@code size} along the bottom of [left, right]: edges and centre. */
    public static MediaControlLayout row(float left, float right, float bottom, float size) {
        float top = bottom - size;
        return new MediaControlLayout(square(left, top, size),
                square((left + right - size) / 2f, top, size), square(right - size, top, size));
    }

    /** Same-size buttons spread over [left, right]: previous, play centred, next at the edge. */
    public static MediaControlLayout spread(MediaControlLayout normal, float left, float right) {
        if (normal.previous == null || normal.next == null) return normal;
        float size = normal.playPause.right - normal.playPause.left;
        float top = normal.playPause.top;
        return new MediaControlLayout(square(left, top, size),
                square((left + right - size) / 2f, top, size), square(right - size, top, size));
    }

    public static boolean isCompact(float cardHeight, float textColumnWidth) {
        return cardHeight < 110f || textColumnWidth < 120f;
    }

    public static boolean isCompact(float cardHeight, float textColumnWidth, float dpScale) {
        float scale = Float.isFinite(dpScale) && dpScale > 0f ? dpScale : 1f;
        // A dense phone preview may exceed the pixel threshold while still being shorter
        // than the normal 76dp control plus title headroom. Keep both visible.
        return isCompact(cardHeight, textColumnWidth) || cardHeight < MIN_TARGET_DP * scale * 1.35f;
    }

    private static Target bottomAlignedCentered(float left, float right, float bottom,
                                                float size) {
        float centerX = left + (right - left) / 2f;
        return square(centerX - size / 2f, bottom - size, size);
    }

    private static Target square(float left, float top, float size) {
        return new Target(left, top, left + size, top + size);
    }

    public Target target(Control control) {
        if (control == null) {
            return null;
        }
        switch (control) {
            case PREVIOUS:
                return previous;
            case PLAY_PAUSE:
                return playPause;
            case NEXT:
                return next;
            default:
                return null;
        }
    }

    /** Returns the matching control, or null for points in the gaps/outside the targets. */
    public Control hit(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            return null;
        }
        if (previous != null && previous.contains(x, y)) {
            return Control.PREVIOUS;
        }
        if (playPause != null && playPause.contains(x, y)) {
            return Control.PLAY_PAUSE;
        }
        if (next != null && next.contains(x, y)) {
            return Control.NEXT;
        }
        return null;
    }
}
