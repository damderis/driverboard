package local.driveboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PagesTest {
    @Test
    public void pageZeroKeepsOriginalKeysAndOtherPagesAreSeparate() {
        assertEquals("full_layout", Store.layoutKey(Store.FULL, 0));
        assertEquals("split_layout", Store.layoutKey(Store.SPLIT, 0));
        assertEquals("full_layout_p2", Store.layoutKey(Store.FULL, 2));
    }

    @Test
    public void pageDefaultsFitTheGridAndRoundTrip() {
        for (int page = 0; page < 3; page++) {
            for (boolean split : new boolean[] {false, true}) {
                BoardLayout layout = BoardLayout.pageDefaults(page, split);
                assertTrue(!layout.cards.isEmpty());
                for (BoardLayout.Card card : layout.cards) {
                    assertTrue(layout.canPlace(card, card.col, card.row, card.w, card.h));
                }
                assertEquals(layout.toJson(), BoardLayout.fromJson(layout.toJson(), split).toJson());
            }
        }
    }

    @Test
    public void shortcutColumnsPreferWideTiles() {
        assertEquals(3, Shortcut.columns(6, 700, 300));
        assertEquals(2, Shortcut.columns(6, 300, 400));
        assertEquals(2, Shortcut.columns(4, 400, 300));
    }

    @Test
    public void volumeStepsSnapToTensAndClamp() {
        assertEquals(50, Shortcut.volumeStep(35, 10));
        assertEquals(30, Shortcut.volumeStep(35, -10));
        assertEquals(100, Shortcut.volumeStep(100, 10));
        assertEquals(0, Shortcut.volumeStep(0, -10));
    }
}
