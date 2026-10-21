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
    public void translucentContainersRemainReadableOverBlackAndWhitePictures() {
        int[] seeds = {0xFF0F6E6E, 0xFF3FB978, 0xFF688FCB,
                0xFFC36B82, 0xFF818A91, 0xFFFFFFFF, 0xFF000000};
        for (int seed : seeds) {
            BoardColours colours = BoardColours.fromSeed(seed);
            System.out.println("glass tint seed=" + Integer.toHexString(seed)
                    + " high=" + colours.alphaForContainer(colours.surfaceContainerHigh)
                    + " primary=" + colours.alphaForContainer(colours.primaryContainer)
                    + " secondary=" + colours.alphaForContainer(colours.secondaryContainer)
                    + " tertiary=" + colours.alphaForContainer(colours.tertiaryContainer));
            assertTrue(colours.containerAlpha >= 0 && colours.containerAlpha <= 255);
            int[] containers = {colours.surface, colours.surfaceContainerLow,
                    colours.surfaceContainerHigh, colours.surfaceContainerHighest,
                    colours.primaryContainer, colours.secondaryContainer,
                    colours.tertiaryContainer};
            for (int container : containers) {
                int alpha = colours.alphaForContainer(container);
                assertEquals(alpha,
                        colours.translucentContainer(container) >>> 24);
                for (int backdrop : new int[]{0xFF000000, 0xFFFFFFFF}) {
                    int blended = BoardColours.composite(container, backdrop,
                            alpha);
                    assertTrue("text seed=" + Integer.toHexString(seed)
                                    + " container=" + Integer.toHexString(container)
                                    + " backdrop=" + Integer.toHexString(backdrop),
                            BoardColours.contrast(colours.onSurface, blended) >= 4.5);
                    assertTrue("secondary text seed=" + Integer.toHexString(seed)
                                    + " container=" + Integer.toHexString(container)
                                    + " backdrop=" + Integer.toHexString(backdrop),
                            BoardColours.contrast(colours.onSurfaceVariant, blended) >= 4.5);
                }
                int noPicture = BoardColours.composite(container, colours.surface, 179);
                assertTrue(BoardColours.contrast(colours.onSurface, noPicture) >= 4.5);
                assertTrue(BoardColours.contrast(colours.onSurfaceVariant, noPicture) >= 4.5);
                if (alpha > 0) {
                    boolean lowerFails = false;
                    for (int backdrop : new int[]{0xFF000000, 0xFFFFFFFF}) {
                        int lower = BoardColours.composite(container, backdrop,
                                alpha - 1);
                        lowerFails |= BoardColours.contrast(colours.onSurface, lower) < 4.5
                                || BoardColours.contrast(colours.onSurfaceVariant, lower) < 4.5;
                    }
                    assertTrue("minimum glass tint seed=" + Integer.toHexString(seed)
                            + " container=" + Integer.toHexString(container), lowerFails);
                }
            }
        }
    }
}
