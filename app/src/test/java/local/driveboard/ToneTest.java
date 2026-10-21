package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.*;

public class ToneTest {
    private double luminance(int color) {
        double[] channels = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = ((color >> (16 - 8 * i)) & 255) / 255.0;
            channels[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return .2126 * channels[0] + .7152 * channels[1] + .0722 * channels[2];
    }

    @Test
    public void allPresetsHaveAccessibleText() {
        assertEquals(5, Tone.values().length);
        for (Tone t : Tone.values()) {
            assertSame(t, Tone.from(t.id));
            for (int text : new int[]{0xFFF4EFE4, 0xFFA9C2BD}) {
                assertTrue(t.id, (luminance(text) + .05) / (luminance(t.panel) + .05) >= 4.5);
            }
        }
        assertSame(Tone.TEAL, Tone.from("unknown"));
    }
}
