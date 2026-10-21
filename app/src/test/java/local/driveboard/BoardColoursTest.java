package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BoardColoursTest {
    @Test
    public void presetTextContrastsAgainstEveryOpaqueContainer() {
        int[] seeds = {0xFF0F6E6E, 0xFF3FB978, 0xFF688FCB,
                0xFFC36B82, 0xFF818A91};
        for (int seed : seeds) {
            BoardColours colours = BoardColours.fromSeed(seed);
            int[] containers = {colours.surface, colours.surfaceContainerLow,
                    colours.surfaceContainerHigh, colours.surfaceContainerHighest,
                    colours.primaryContainer, colours.secondaryContainer,
                    colours.tertiaryContainer};
            for (int container : containers) {
                assertEquals("opaque container", 255, container >>> 24);
                assertTrue("onSurface seed=" + Integer.toHexString(seed)
                                + " container=" + Integer.toHexString(container)
                                + " ratio=" + BoardColours.contrast(colours.onSurface, container),
                        BoardColours.contrast(colours.onSurface, container) >= 4.5);
                assertTrue("onSurfaceVariant seed=" + Integer.toHexString(seed)
                                + " container=" + Integer.toHexString(container)
                                + " ratio=" + BoardColours.contrast(
                                        colours.onSurfaceVariant, container),
                        BoardColours.contrast(colours.onSurfaceVariant, container) >= 4.5);
            }
        }
    }

    @Test
    public void noPictureTintRemainsSeventyPercent() {
        int[] seeds = {0xFF0F6E6E, 0xFF3FB978, 0xFF688FCB,
                0xFFC36B82, 0xFF818A91, 0xFFFFFFFF, 0xFF000000};
        for (int seed : seeds) {
            BoardColours colours = BoardColours.fromSeed(seed);
            assertEquals(179, GlassTint.NO_PICTURE_ALPHA);
            int[] containers = {colours.surface, colours.surfaceContainerLow,
                    colours.surfaceContainerHigh, colours.surfaceContainerHighest,
                    colours.primaryContainer, colours.secondaryContainer,
                    colours.tertiaryContainer};
            for (int container : containers) {
                double alpha = GlassTint.minimumAlpha(1.0, container,
                        colours.onSurface, colours.onSurfaceVariant);
                assertTrue("bright backdrop seed=" + Integer.toHexString(seed),
                        alpha >= .40 && alpha <= .95);
            }
        }
    }
}
