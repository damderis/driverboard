package local.driveboard;

/** A two-pass, edge-clamped box blur on opaque ARGB pixels. */
final class BoxBlur {
    private BoxBlur() {}

    static int[] blur(int[] source, int width, int height, int radius) {
        if (width <= 0 || height <= 0 || source.length != width * height || radius < 0) {
            throw new IllegalArgumentException("Invalid blur dimensions or radius");
        }
        int[] horizontal = new int[source.length];
        int[] result = new int[source.length];
        int count = 2 * radius + 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = 0;
                int green = 0;
                int blue = 0;
                for (int offset = -radius; offset <= radius; offset++) {
                    int color = source[y * width + clamp(x + offset, width)];
                    red += (color >>> 16) & 255;
                    green += (color >>> 8) & 255;
                    blue += color & 255;
                }
                horizontal[y * width + x] = opaque(red / count, green / count, blue / count);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = 0;
                int green = 0;
                int blue = 0;
                for (int offset = -radius; offset <= radius; offset++) {
                    int color = horizontal[clamp(y + offset, height) * width + x];
                    red += (color >>> 16) & 255;
                    green += (color >>> 8) & 255;
                    blue += color & 255;
                }
                result[y * width + x] = opaque(red / count, green / count, blue / count);
            }
        }
        return result;
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    private static int opaque(int red, int green, int blue) {
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
