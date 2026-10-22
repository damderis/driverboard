package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class BackdropPixelsTest {
    @Test
    public void grainIsDeterministicFineAndBounded() {
        int[] first = new int[1024];
        java.util.Arrays.fill(first, 0xFF808080);
        int[] second = first.clone();
        BackdropPixels.grain(first);
        BackdropPixels.grain(second);
        assertArrayEquals(first, second);
        boolean varied = false;
        for (int i = 0; i < first.length; i++) {
            int red = (first[i] >>> 16) & 255;
            assertTrue(Math.abs(red - 128) <= 4);
            assertTrue(red == (first[i] & 255));
            varied |= first[i] != first[0];
        }
        assertTrue(varied);
    }

    @Test
    public void saturationPreservesNeutralAndBoostsColour() {
        int[] pixels = {0xFF808080, 0xFF906050};
        BackdropPixels.saturate(pixels);
        assertTrue(pixels[0] == 0xFF808080);
        int red = (pixels[1] >>> 16) & 255;
        int blue = pixels[1] & 255;
        assertTrue(red - blue > 0x90 - 0x50);
    }
}
