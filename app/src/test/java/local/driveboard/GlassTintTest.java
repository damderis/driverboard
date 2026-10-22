package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class GlassTintTest {
    @Test
    public void lighterScrimmedBackdropsKeepBothTextRolesReadable() {
        int[] seeds = {0xFF0F6E6E, 0xFF3FB978, 0xFF688FCB,
                0xFFC36B82, 0xFF818A91};
        for (int seed : seeds) {
            BoardColours colours = BoardColours.fromSeed(seed);
            int[] tints = {colours.surfaceContainerHigh, colours.primaryContainer,
                    colours.secondaryContainer, colours.tertiaryContainer};
            for (int scrimAlpha : new int[]{97, 140}) {
                for (int level = 0; level <= 255; level++) {
                    int pixel = 0xFF000000 | level << 16 | level << 8 | level;
                    int backdrop = BackdropPixels.scrim(pixel, colours.surface, scrimAlpha);
                    for (int tint : tints) {
                        double luminance = luminance(backdrop);
                        int alpha = colours.alphaForBackdrop(tint, luminance);
                        int composite = BackdropPixels.scrim(backdrop, tint, alpha);
                        assertTrue(BoardColours.contrast(colours.onSurface, composite) >= 4.5);
                        assertTrue(BoardColours.contrast(colours.onSurfaceVariant, composite) >= 4.5);
                    }
                }
            }
        }
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

    @Test
    public void darkAndBrightBackdropsChooseAdaptiveReadableAlphas() {
        BoardColours colours = BoardColours.fromSeed(0xFF688FCB);
        int tint = colours.surfaceContainerHigh;
        double dark = colours.alphaForBackdrop(tint, 0.01) / 255.0;
        double daylight = colours.alphaForBackdrop(tint, 1.0) / 255.0;
        double oldWorstCase = colours.worstCaseGlassAlpha(tint) / 255.0;
        System.out.println("adaptive glass alpha night-sky=" + dark
                + " daylight=" + daylight);
        assertTrue("dark night-sky should use near-minimum tint", dark >= .40 && dark <= .45);
        assertTrue("white daylight should be at least the old worst-case alpha",
                daylight + .002 >= oldWorstCase);
    }
}
