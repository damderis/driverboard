package local.driveboard;

/** Geometry and timing shared by the phone preview and car board. */
final class BoardTransition {
    static final long DURATION_MS = 420L;

    static final class Rect {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Rect(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        Rect offset(float x, float y) {
            return new Rect(left + x, top + y, right + x, bottom + y);
        }

        Rect scale(float factor) {
            float centerX = (left + right) / 2f;
            float centerY = (top + bottom) / 2f;
            float halfWidth = (right - left) * factor / 2f;
            float halfHeight = (bottom - top) * factor / 2f;
            return new Rect(centerX - halfWidth, centerY - halfHeight,
                    centerX + halfWidth, centerY + halfHeight);
        }

        static Rect interpolate(Rect from, Rect to, float fraction) {
            if (fraction <= 0f) {
                return from;
            }
            if (fraction >= 1f) {
                return to;
            }
            return new Rect(lerp(from.left, to.left, fraction),
                    lerp(from.top, to.top, fraction),
                    lerp(from.right, to.right, fraction),
                    lerp(from.bottom, to.bottom, fraction));
        }
    }

    private BoardTransition() {
    }

    static float progress(long elapsedMs) {
        if (elapsedMs <= 0) {
            return 0f;
        }
        if (elapsedMs >= DURATION_MS) {
            return 1f;
        }
        float x = elapsedMs / (float) DURATION_MS;
        float low = 0f;
        float high = 1f;
        // Invert the x component of cubic-bezier(.05, .7, .1, 1).
        for (int i = 0; i < 20; i++) {
            float mid = (low + high) / 2f;
            if (bezier(mid, .05f, .1f) < x) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return bezier((low + high) / 2f, .7f, 1f);
    }

    static float outgoingAlpha(float elapsedFraction) {
        return Math.max(0f, 1f - elapsedFraction / .6f);
    }

    static float incomingAlpha(float progress) {
        return progress;
    }

    private static float bezier(float t, float first, float second) {
        float inverse = 1f - t;
        return 3f * inverse * inverse * t * first
                + 3f * inverse * t * t * second + t * t * t;
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }
}
