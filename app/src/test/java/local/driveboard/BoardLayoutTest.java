package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.*;

public class BoardLayoutTest {
    @Test
    public void gridSnapClampAndJsonRoundTrip() {
        assertEquals(3, BoardLayout.snap(26, 10));
        assertEquals(0, BoardLayout.snap(10, 0));
        BoardLayout b = BoardLayout.defaults();
        BoardLayout.Card c = b.cards.get(0);
        b.move(c, 99, -4);
        assertEquals(8, c.col);
        assertEquals(0, c.row);
        b.resize(c, 99, -3);
        assertEquals(4, c.w);
        assertEquals(1, c.h);
        b.move(c, 2, 2);
        b.resize(c, 3, 3);
        BoardLayout copy = BoardLayout.fromJson(b.toJson());
        assertEquals(b.toJson(), copy.toJson());
        assertEquals(4, copy.cards.size());
        assertEquals(BoardLayout.defaults().toJson(), BoardLayout.fromJson("broken").toJson());
        assertEquals(12, new BoardLayout.Card("clock", -9, 9, 90, 0).w);
    }

    @Test
    public void defaultHasFourCardsInRequestedGrid() {
        BoardLayout b = BoardLayout.defaults();
        assertEquals("clock:0,0,4,6;prayer:4,0,8,3;media:4,3,5,3;weather:9,3,3,3", positions(b));
    }

    @Test
    public void splitDefaultAndSavedLayoutsStayIndependent() {
        BoardLayout split = BoardLayout.splitDefaults();
        assertEquals("prayer:0,0,12,3;clock:0,3,6,3;weather:6,3,6,3",
                positions(split));
        BoardLayout.Card clock = split.cards.get(1);
        split.move(clock, 2, 3);
        assertEquals(split.toJson(), BoardLayout.fromJson(split.toJson(), true).toJson());
        assertEquals(BoardLayout.splitDefaults().toJson(),
                BoardLayout.fromJson(BoardLayout.defaults().toJson(), true).toJson());
        assertEquals(BoardLayout.defaults().toJson(),
                BoardLayout.fromJson(split.toJson()).toJson());
    }

    @Test
    public void legacySingleLayoutMigratesOnlyToUnsavedFullMode() {
        BoardLayout legacy = BoardLayout.defaults();
        legacy.move(legacy.cards.get(0), 2, 0);
        String legacyJson = legacy.toJson();
        assertEquals(legacyJson, BoardLayout.fromSaved(null, legacyJson, false).toJson());
        assertEquals(BoardLayout.splitDefaults().toJson(),
                BoardLayout.fromSaved(null, legacyJson, true).toJson());
        assertEquals(BoardLayout.defaults().toJson(),
                BoardLayout.fromSaved(BoardLayout.defaults().toJson(), legacyJson, false).toJson());
    }

    @Test
    public void migratesOldDefaultAndPreservesCustomCards() {
        String old = "[{\"type\":\"clock\",\"col\":0,\"row\":0,\"w\":4,\"h\":6},"
                + "{\"type\":\"prayer\",\"col\":4,\"row\":0,\"w\":4,\"h\":6},"
                + "{\"type\":\"media\",\"col\":8,\"row\":0,\"w\":4,\"h\":6}]";
        assertEquals(BoardLayout.defaults().toJson(), BoardLayout.fromJson(old).toJson());
        BoardLayout custom = new BoardLayout();
        custom.cards.add(new BoardLayout.Card("clock", 0, 0, 4, 6));
        custom.cards.add(new BoardLayout.Card("prayer", 4, 0, 8, 3));
        custom.cards.add(new BoardLayout.Card("media", 4, 3, 5, 3));
        BoardLayout migrated = BoardLayout.fromJson(custom.toJson());
        assertEquals(4, migrated.cards.size());
        assertEquals("weather:9,3,3,3", position(migrated.cards.get(3)));
        for (int i = 0; i < 3; i++) {
            assertEquals(position(custom.cards.get(i)), position(migrated.cards.get(i)));
        }

    }
    @Test
    public void rejectsMalformedOrDuplicateLayout() {
        String duplicate = "[{\"type\":\"clock\",\"col\":0,\"row\":0,\"w\":4,\"h\":6},"
                + "{\"type\":\"clock\",\"col\":4,\"row\":0,\"w\":4,\"h\":6}]";
        assertEquals(BoardLayout.defaults().toJson(), BoardLayout.fromJson(duplicate).toJson());
        assertEquals(BoardLayout.defaults().toJson(), BoardLayout.fromJson("[]").toJson());
    }

    @Test
    public void migratesBatteryInFullAndSplitWithoutMovingOrResizing() {
        for (boolean split : new boolean[] {false, true}) {
            BoardLayout saved = split ? BoardLayout.splitDefaults() : BoardLayout.defaults();
            BoardLayout.Card weather = saved.cards.get(saved.cards.size() - 1);
            saved.move(weather, 5, 1);
            saved.resize(weather, 4, 4);
            String legacy = saved.toJson().replace("weather", "battery");
            assertEquals(saved.toJson(), BoardLayout.fromJson(legacy, split).toJson());
            assertEquals(saved.toJson(), BoardLayout.fromSaved(legacy, null, split).toJson());
            if (!split) {
                assertEquals(saved.toJson(), BoardLayout.fromSaved(null, legacy, false).toJson());
            }
        }
    }

    private static String positions(BoardLayout b) {
        StringBuilder s = new StringBuilder();
        for (BoardLayout.Card c : b.cards) {
            if (s.length() > 0) {
                s.append(';');
            }

            s.append(position(c));
        }

        return s.toString();
    }

    private static String position(BoardLayout.Card c) {
        return c.type + ":" + c.col + "," + c.row + "," + c.w + "," + c.h;
    }
}
