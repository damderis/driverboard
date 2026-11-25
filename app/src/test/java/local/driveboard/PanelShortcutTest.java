package local.driveboard;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

public class PanelShortcutTest {
    @Test
    public void parseKeepsOrderDropsUnknownAndCapsAtFour() {
        assertEquals(Arrays.asList(PanelShortcut.PETROL, PanelShortcut.HOME),
                PanelShortcut.parse("petrol, nope ,home,petrol"));
        assertEquals(4, PanelShortcut.parse("home,work,petrol,food,coffee").size());
        assertEquals(PanelShortcut.DEFAULT, PanelShortcut.join(PanelShortcut.parse(null)));
    }
}
