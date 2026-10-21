package local.driveboard;

/** Pure-Java tint alpha selection for readable text over a blurred backdrop. */
final class GlassTint {
    static final double MIN_ALPHA = .40;
    static final double MAX_ALPHA = .95;
    static final int NO_PICTURE_ALPHA = 179;

    private GlassTint() {}

    static double minimumAlpha(double backdropLuminance, int tint,
            int onSurface, int onSurfaceVariant) {
        return minimumAlpha(backdropLuminance, tint, onSurface, onSurfaceVariant, .40);
    }

    static double minimumAlpha(double backdropLuminance, int tint,
            int onSurface, int onSurfaceVariant, double minimum) {
        double backdrop = Math.max(0, Math.min(1, backdropLuminance));
        int firstStep = (int) Math.ceil(Math.max(MIN_ALPHA, minimum) * 1000);
        for (int step = firstStep; step <= MAX_ALPHA * 1000; step++) {
            double alpha = step / 1000.0;
            double luminance = compositeLuminance(tint, backdrop, alpha);
            if (contrast(onSurface, luminance) >= 4.5
                    && contrast(onSurfaceVariant, luminance) >= 4.5) {
                return Math.min(MAX_ALPHA, alpha);
            }
        }
        return MAX_ALPHA;
    }

    private static double compositeLuminance(int tint, double backdrop, double alpha) {
        double r = linear((tint >>> 16) & 255) * alpha
                + backdrop * (1 - alpha);
        double g = linear((tint >>> 8) & 255) * alpha
                + backdrop * (1 - alpha);
        double b = linear(tint & 255) * alpha
                + backdrop * (1 - alpha);
        return .2126 * r + .7152 * g + .0722 * b;
    }

    private static double contrast(int foreground, double backgroundLuminance) {
        double luminance = luminance(foreground);
        return (Math.max(luminance, backgroundLuminance) + .05)
                / (Math.min(luminance, backgroundLuminance) + .05);
    }

    private static double luminance(int color) {
        return .2126 * linear((color >>> 16) & 255)
                + .7152 * linear((color >>> 8) & 255)
                + .0722 * linear(color & 255);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
