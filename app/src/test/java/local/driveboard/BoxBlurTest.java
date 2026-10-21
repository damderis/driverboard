package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class BoxBlurTest {
    @Test
    public void constantImageRemainsConstant() {
        int[] pixels = new int[4 * 3];
        java.util.Arrays.fill(pixels, 0xFF2468AC);
        assertArrayEquals(pixels, BoxBlur.blur(pixels, 4, 3, 12));
    }

    @Test
    public void twoPassBlurClampsEdgesAndKeepsChannelsIndependent() {
        int[] pixels = {0xFFFF0000, 0xFF000000, 0xFF0000FF};
        assertArrayEquals(new int[]{0xFFAA0000, 0xFF550055, 0xFF0000AA},
                BoxBlur.blur(pixels, 3, 1, 1));
        assertArrayEquals(new int[]{0xFFAA0000, 0xFF550055, 0xFF0000AA},
                BoxBlur.blur(pixels, 1, 3, 1));
        assertArrayEquals(pixels, BoxBlur.blur(pixels, 3, 1, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMismatchedSize() {
        BoxBlur.blur(new int[2], 2, 2, 1);
    }
}
