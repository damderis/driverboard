package local.driveboard;

import java.util.Random;

/** Pure-Java colour processing, performed only while building a backdrop. */
final class BackdropPixels {
    private BackdropPixels() {}

    static void saturate(int[] pixels) {
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int r = (color >>> 16) & 255;
            int g = (color >>> 8) & 255;
            int b = color & 255;
            double grey = .2126 * r + .7152 * g + .0722 * b;
            pixels[i] = rgb(grey + (r - grey) * 1.15,
                    grey + (g - grey) * 1.15, grey + (b - grey) * 1.15);
        }
    }

    static void grain(int[] pixels) {
        Random random = new Random(0x4452495645424F41L);
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            double noise = (random.nextDouble() * 2 - 1) * 255 * .015;
            pixels[i] = rgb(((color >>> 16) & 255) + noise,
                    ((color >>> 8) & 255) + noise, (color & 255) + noise);
        }
    }

    static int scrim(int pixel, int tint, int alpha) {
        double a = alpha / 255.0;
        return rgb(((pixel >>> 16) & 255) * (1 - a) + ((tint >>> 16) & 255) * a,
                ((pixel >>> 8) & 255) * (1 - a) + ((tint >>> 8) & 255) * a,
                (pixel & 255) * (1 - a) + (tint & 255) * a);
    }

    private static int rgb(double r, double g, double b) {
        return 0xFF000000 | channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    private static int channel(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }
}
