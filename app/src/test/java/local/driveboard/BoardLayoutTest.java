package local.driveboard;

import org.junit.Test;
import static org.junit.Assert.*;

public class BoardLayoutTest {
    @Test public void gridSnapClampAndJsonRoundTrip() {
        assertEquals(3, BoardLayout.snap(26, 10));
        assertEquals(0, BoardLayout.snap(10, 0));
        BoardLayout b = BoardLayout.defaults(); BoardLayout.Card c = b.cards.get(0);
        b.move(c, 99, -4); assertEquals(8, c.col); assertEquals(0, c.row);
        b.resize(c, 99, -3); assertEquals(4, c.w); assertEquals(1, c.h);
        b.move(c, 2, 2); b.resize(c, 3, 3);
        BoardLayout copy = BoardLayout.fromJson(b.toJson());
        assertEquals(b.toJson(), copy.toJson());
        assertEquals(3, copy.cards.size());
        assertEquals(BoardLayout.defaults().toJson(), BoardLayout.fromJson("broken").toJson());
        assertEquals(12, new BoardLayout.Card("clock",-9,9,90,0).w);
    }
}
