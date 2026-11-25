package local.driveboard;

import org.junit.Test;

import static org.junit.Assert.*;

public class BoardLayoutTest {
    @Test
    public void gridSnapClampAndJsonRoundTrip() {
        assertEquals(3, BoardLayout.snap(26, 10));
        assertEquals(0, BoardLayout.snap(10, 0));
        BoardLayout b = new BoardLayout();
        BoardLayout.Card c = new BoardLayout.Card("clock", 0, 0, 4, 6);
        b.cards.add(c);
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
        assertEquals(1, copy.cards.size());
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
        split.resize(clock, 5, 3);
        split.move(clock, 1, 3);
        assertEquals(split.toJson(), BoardLayout.fromJson(split.toJson(), true).toJson());
        assertEquals(BoardLayout.defaults().toJson(),
                BoardLayout.fromJson(BoardLayout.defaults().toJson(), true).toJson());
        assertEquals(split.toJson(), BoardLayout.fromJson(split.toJson()).toJson());
    }

    @Test
    public void legacySingleLayoutMigratesOnlyToUnsavedFullMode() {
        BoardLayout legacy = BoardLayout.defaults();
        legacy.cards.get(0).col = 2;
        String legacyJson = legacyJson(legacy);
        assertEquals(legacy.toJson(), BoardLayout.fromSaved(null, legacyJson, false).toJson());
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
        BoardLayout migrated = BoardLayout.fromJson(legacyJson(custom));
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
            weather.col = 5;
            weather.row = 1;
            weather.w = 4;
            weather.h = 4;
            String legacy = legacyJson(saved).replace("\"weather\"", "\"battery\"");
            assertEquals(saved.toJson(), BoardLayout.fromJson(legacy, split).toJson());
            assertEquals(saved.toJson(), BoardLayout.fromSaved(legacy, null, split).toJson());
            if (!split) {
                assertEquals(saved.toJson(), BoardLayout.fromSaved(null, legacy, false).toJson());
            }
        }
    }

    @Test
    public void automaticModesAndNarrowStack() {
        assertEquals("narrow", BoardLayout.modeFor(588, 1008));
        assertEquals("narrow", BoardLayout.modeFor(849, 1000));
        assertEquals("split", BoardLayout.modeFor(850, 1000));
        assertEquals("split", BoardLayout.modeFor(1399, 1000));
        assertEquals("full", BoardLayout.modeFor(1400, 1000));
        BoardLayout split = BoardLayout.splitDefaults();
        assertEquals("prayer:0,0,12,3;clock:0,3,12,2;weather:0,5,12,1",
                positions(BoardLayout.narrowDefaults(true, split)));
        split.cards.set(2, new BoardLayout.Card("media", 6, 3, 6, 3));
        assertEquals(split.toJson(), BoardLayout.fromJson(split.toJson(), true).toJson());
        // With a music card: prayer on top, music in the middle, the remaining card at the bottom.
        assertEquals("prayer:0,0,12,2;media:0,2,12,3;clock:0,5,12,1",
                positions(BoardLayout.narrowDefaults(true, split)));
        assertEquals("prayer:0,0,12,2;media:0,2,12,3;clock:0,5,12,1",
                positions(BoardLayout.narrowDefaults(false, split)));
    }

    @Test
    public void prayerShapeAndRowFitRespectBoundaries() {
        assertEquals(50f, BoardLayout.prayerRadius(180, 100), .001f);
        assertEquals(22f, BoardLayout.prayerRadius(179, 100), .001f);
        assertEquals(22f, BoardLayout.prayerRadius(100, 180), .001f);
        assertEquals(5, BoardLayout.prayerRowColumns(500, 40, 100, 40));
        assertEquals(3, BoardLayout.prayerRowColumns(499, 80, 100, 40));
        assertEquals(3, BoardLayout.prayerRowColumns(300, 80, 100, 40));
        assertEquals(0, BoardLayout.prayerRowColumns(300, 79, 100, 40));
        assertEquals(0, BoardLayout.prayerRowColumns(299, 80, 100, 40));
        assertEquals(0, BoardLayout.prayerRowColumns(500, 39, 100, 40));
    }

    @Test
    public void v2AllowsEmptyAndMultipleCardsWithStableIdsAndOptions() {
        BoardLayout empty = new BoardLayout();
        assertEquals("{\"version\":2,\"cards\":[]}", empty.toJson());
        assertEquals(empty.toJson(), BoardLayout.fromJson(empty.toJson()).toJson());

        BoardLayout board = new BoardLayout();
        BoardLayout.Card first = board.add("clock", 0, 0);
        BoardLayout.Card second = board.add("clock", 4, 0);
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first.id, second.id);
        first.options.put("format", "12h");
        first.options.put("show_hijri", "true");
        BoardLayout restored = BoardLayout.fromJson(board.toJson());
        assertEquals(board.toJson(), restored.toJson());
        assertEquals("12h", restored.cards.get(0).options.get("format"));
        assertEquals("true", restored.cards.get(0).options.get("show_hijri"));
        assertEquals(first.id, restored.cards.get(0).id);
    }

    @Test
    public void catalogDefaultsAndInvalidOptionsNormalize() {
        assertEquals("24h", WidgetType.CLOCK.defaultOptions().get("format"));
        assertEquals("true", WidgetType.CLOCK.defaultOptions().get("show_date"));
        assertEquals("false", WidgetType.CLOCK.defaultOptions().get("show_hijri"));
        assertEquals("full", WidgetType.PRAYER.defaultOptions().get("view"));
        assertEquals("true", WidgetType.PRAYER.defaultOptions().get("show_countdown"));
        assertEquals("true", WidgetType.MEDIA.defaultOptions().get("show_progress"));
        assertEquals("true", WidgetType.MEDIA.defaultOptions().get("show_controls"));
        assertEquals("true", WidgetType.WEATHER.defaultOptions().get("show_rain"));
        assertFalse(WidgetType.PRAYER.multiple);
        assertFalse(WidgetType.MEDIA.multiple);
        assertTrue(WidgetType.CLOCK.multiple);
        assertTrue(WidgetType.WEATHER.multiple);

        BoardLayout board = new BoardLayout();
        BoardLayout.Card clock = board.add("clock", 0, 0);
        clock.options.put("format", "invalid");
        clock.options.put("unknown", "ignored");
        BoardLayout restored = BoardLayout.fromJson(board.toJson());
        assertEquals("24h", restored.cards.get(0).options.get("format"));
        assertFalse(restored.cards.get(0).options.containsKey("unknown"));
    }

    @Test
    public void placementRejectsCollisionsAndAddShrinksToFit() {
        BoardLayout board = BoardLayout.defaults();
        BoardLayout.Card clock = board.cards.get(0);
        assertFalse(board.tryMove(clock, 4, 0));
        assertEquals(0, clock.col);
        assertFalse(board.tryResize(clock, 8, 6));
        assertEquals(4, clock.w);
        assertNull(board.add("prayer"));
        assertNull(board.add("clock"));

        BoardLayout narrowGap = new BoardLayout();
        narrowGap.cards.add(new BoardLayout.Card("weather", 0, 0, 10, 6));
        BoardLayout.Card added = narrowGap.add("clock");
        assertNotNull(added);
        assertEquals(10, added.col);
        assertEquals(2, added.w);
        assertEquals(3, added.h);
    }

    @Test
    public void legacyOverlapAndSmallCardsSurviveSecondLoad() {
        String v1 = "[{\"type\":\"clock\",\"col\":0,\"row\":0,\"w\":1,\"h\":1},"
                + "{\"type\":\"prayer\",\"col\":0,\"row\":0,\"w\":4,\"h\":3},"
                + "{\"type\":\"media\",\"col\":4,\"row\":0,\"w\":4,\"h\":3}]";
        BoardLayout migrated = BoardLayout.fromJson(v1);
        BoardLayout reloaded = BoardLayout.fromJson(migrated.toJson());
        assertEquals(migrated.toJson(), reloaded.toJson());
        assertEquals("clock:0,0,1,1", position(reloaded.cards.get(0)));
    }

    @Test
    public void copyDuplicateAndRemoveKeepOptionsIndependent() {
        BoardLayout original = new BoardLayout();
        BoardLayout.Card first = original.add("clock", 0, 0);
        first.options.put("format", "12h");
        BoardLayout copy = original.copy();
        copy.cards.get(0).options.put("format", "24h");
        assertEquals("12h", first.options.get("format"));

        BoardLayout.Card second = original.duplicate(first);
        assertNotNull(second);
        assertNotEquals(first.id, second.id);
        assertEquals("12h", second.options.get("format"));
        assertTrue(original.remove(second));
        assertEquals(1, original.cards.size());
    }

    @Test
    public void v2RejectsDuplicateIdsAndUnsupportedVersion() {
        BoardLayout board = new BoardLayout();
        BoardLayout.Card first = board.add("clock", 0, 0);
        board.cards.add(new BoardLayout.Card("clock", first.id, 4, 0, 4, 3, null));
        assertEquals(BoardLayout.defaults().toJson(), BoardLayout.fromJson(board.toJson()).toJson());
        assertEquals(BoardLayout.defaults().toJson(),
                BoardLayout.fromJson("{\"version\":3,\"cards\":[]}").toJson());
    }

    @Test
    public void unknownTypesAreSkippedAndNarrowKeepsSurvivingOptions() {
        BoardLayout split = new BoardLayout();
        assertTrue(BoardLayout.narrowDefaults(false, split).cards.isEmpty());
        BoardLayout.Card clock = split.add("clock", 0, 0);
        clock.options.put("format", "12h");
        BoardLayout narrow = BoardLayout.narrowDefaults(false, split);
        assertEquals(1, narrow.cards.size());
        assertEquals(clock.id, narrow.cards.get(0).id);
        assertEquals("12h", narrow.cards.get(0).options.get("format"));
        assertEquals("clock:0,3,12,2", position(narrow.cards.get(0)));

        String unknown = split.toJson().replace("\"cards\":[", "\"cards\":["
                + "{\"id\":\"future-1\",\"type\":\"future\",\"col\":0,"
                + "\"row\":0,\"w\":2,\"h\":2,\"options\":{}},");
        assertEquals(split.toJson(), BoardLayout.fromJson(unknown).toJson());
    }

    private static String legacyJson(BoardLayout board) {
        StringBuilder json = new StringBuilder("[");
        for (BoardLayout.Card card : board.cards) {
            if (json.length() > 1) json.append(',');
            json.append("{\"type\":\"").append(card.type).append("\",\"col\":")
                    .append(card.col).append(",\"row\":").append(card.row)
                    .append(",\"w\":").append(card.w).append(",\"h\":")
                    .append(card.h).append('}');
        }
        return json.append(']').toString();
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
