package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public final class CarPaintTest {
    @Test
    public void sampleAveragesFiveByFive() {
        int[] pixels = new int[25];
        java.util.Arrays.fill(pixels, 0xFF646464);
        pixels[12] = 0xFF969696;
        assertEquals(0xFF666666, CarPaint.sample(pixels, 5, 5, 2, 2));
    }

    @Test
    public void selectionStaysInConnectedColourRegion() {
        int width = 9;
        int height = 7;
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, 0xFF555555);
        for (int y = 0; y < height; y++) {
            pixels[y * width + 4] = 0xFF000000;
        }
        int[] painted = CarPaint.recolor(pixels, width, height, 1, 3, 25,
                0xFFAA2222, 1f);
        assertNotEquals(pixels[3 * width + 2], painted[3 * width + 2]);
        assertEquals(pixels[3 * width + 7], painted[3 * width + 7]);
        assertEquals(pixels[3 * width + 4], painted[3 * width + 4]);
    }

    @Test
    public void preservesAlphaAndOriginalLightDifference() {
        int[] pixels = new int[49];
        java.util.Arrays.fill(pixels, 0x80505050);
        pixels[3 * 7 + 2] = 0x80606060;
        pixels[3 * 7 + 3] = 0x80707070;
        int[] painted = CarPaint.recolor(pixels, 7, 7, 3, 3, 40,
                0xFF808080, 1f);
        assertEquals(0x80, painted[3 * 7 + 2] >>> 24);
        assertEquals(0x80, painted[3 * 7 + 3] >>> 24);
        assertEquals(16, (painted[3 * 7 + 3] & 255) - (painted[3 * 7 + 2] & 255));
    }

    @Test
    public void transparentTapDoesNotPaint() {
        int[] pixels = {0x00000000, 0xFF777777};
        int[] painted = CarPaint.recolor(pixels, 2, 1, 0, 0, 25, 0xFFFF0000, 1f);
        assertEquals(pixels[0], painted[0]);
        assertEquals(pixels[1], painted[1]);
    }
}

