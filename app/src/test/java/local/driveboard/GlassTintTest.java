package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class GlassTintTest {
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
