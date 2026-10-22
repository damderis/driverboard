package local.driveboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java grid and version-one JSON codec; no Android runtime needed. */
public final class BoardLayout {
    public static final int COLS = 12;
    public static final int ROWS = 6;
    public static final String[] TYPES = {"clock", "prayer", "media", "weather"};
    public static final class Card {
        public final String type;
        public int col;
        public int row;
        public int w;
        public int h;

        public Card(String type, int col, int row, int w, int h) {
            this.type = type;
            this.w = clamp(w, 1, COLS);
            this.h = clamp(h, 1, ROWS);
            this.col = clamp(col, 0, COLS - this.w);
            this.row = clamp(row, 0, ROWS - this.h);
        }
    }

    public final List<Card> cards = new ArrayList<>();

    public static int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(max, n));
    }

    public static int snap(float pixels, float cell) {
        return cell <= 0 ? 0 : Math.round(pixels / cell);
    }

    public void move(Card c, int col, int row) {
        c.col = clamp(col, 0, COLS - c.w);
        c.row = clamp(row, 0, ROWS - c.h);
    }

    public void resize(Card c, int w, int h) {
        c.w = clamp(w, 1, COLS - c.col);
        c.h = clamp(h, 1, ROWS - c.row);
    }

    public static BoardLayout defaults() {
        BoardLayout b = new BoardLayout();
        b.cards.add(new Card("clock", 0, 0, 4, 6));
        b.cards.add(new Card("prayer", 4, 0, 8, 3));
        b.cards.add(new Card("media", 4, 3, 5, 3));
        b.cards.add(new Card("weather", 9, 3, 3, 3));
        return b;
    }

    public static String modeFor(float width, float height) {
        float aspect = width / Math.max(1f, height);
        return aspect < .85f ? "narrow" : aspect >= 1.4f ? "full" : "split";
    }

    public static float prayerRadius(float width, float height) {
        return Math.min(width, height) * (width / Math.max(1f, height) >= 1.8f ? .5f : .22f);
    }

    /** Returns five columns, a 3+2 grid, or zero when the summary needs the space. */
    public static int prayerRowColumns(float width, float availableHeight,
            float minColumnWidth, float rowHeight) {
        if (width >= 5 * minColumnWidth && availableHeight >= rowHeight) {
            return 5;
        }
        return width >= 3 * minColumnWidth && availableHeight >= 2 * rowHeight ? 3 : 0;
    }

    // Full-width clock and weather keep the vertical panel easy to read.
    public static BoardLayout narrowDefaults(boolean playing, BoardLayout split) {
        boolean hasMedia = false;
        for (Card card : split.cards) {
            hasMedia |= "media".equals(card.type);
        }
        BoardLayout b = new BoardLayout();
        b.cards.add(new Card("prayer", 0, 0, 12, 3));
        b.cards.add(new Card("clock", 0, 3, 12, 2));
        b.cards.add(new Card(playing && hasMedia ? "media" : "weather", 0, 5, 12, 1));
        return b;
    }

    public static BoardLayout splitDefaults() {
        BoardLayout b = new BoardLayout();
        b.cards.add(new Card("prayer", 0, 0, 12, 3));
        b.cards.add(new Card("clock", 0, 3, 6, 3));
        b.cards.add(new Card("weather", 6, 3, 6, 3));
        return b;
    }

    public String toJson() {
        StringBuilder s = new StringBuilder("[");
        for (Card c : cards) {
            if (s.length() > 1) {
                s.append(',');
            }
            s.append("{\"type\":\"").append(c.type).append("\",\"col\":").append(c.col)
                    .append(",\"row\":").append(c.row).append(",\"w\":").append(c.w)
                    .append(",\"h\":").append(c.h).append('}');
        }
        return s.append(']').toString();
    }

    // Fixed schema written by this app, deliberately independent of org.json on Android.
    public static BoardLayout fromJson(String json) {
        return fromJson(json, false);
    }

    public static BoardLayout fromSaved(String current, String legacy, boolean split) {
        return fromJson(current == null && !split ? legacy : current, split);
    }

    public static BoardLayout fromJson(String json, boolean split) {
        if (json == null) {
            return split ? splitDefaults() : defaults();
        }
        Pattern p = Pattern.compile("\\{\\s*\"type\"\\s*:\\s*\"(clock|prayer|media|weather|battery)\"\\s*,\\s*\"col\"\\s*:\\s*(-?\\d+)\\s*,\\s*\"row\"\\s*:\\s*(-?\\d+)\\s*,\\s*\"w\"\\s*:\\s*(-?\\d+)\\s*,\\s*\"h\"\\s*:\\s*(-?\\d+)\\s*\\}");
        BoardLayout b = new BoardLayout();
        Matcher m = p.matcher(json);
        try {
            while (m.find()) {
                String type = "battery".equals(m.group(1)) ? "weather" : m.group(1);
                for (Card c : b.cards) {
                    if (c.type.equals(type)) {
                        return split ? splitDefaults() : defaults();
                    }
                }
                b.cards.add(new Card(type, Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
                        Integer.parseInt(m.group(5))));
            }
        } catch (RuntimeException e) {
            return split ? splitDefaults() : defaults();
        }
        if (split) {
            boolean prayer = false;
            boolean clock = false;
            boolean weather = false;
            boolean media = false;
            for (Card c : b.cards) {
                prayer |= "prayer".equals(c.type);
                clock |= "clock".equals(c.type);
                weather |= "weather".equals(c.type);
                media |= "media".equals(c.type);
            }
            return b.cards.size() == 3 && prayer && clock && (weather || media)
                    ? b : splitDefaults();
        }
        if (b.cards.size() == 4) {
            return b;
        }
        if (b.cards.size() != 3) {
            return defaults();
        }
        boolean clock = false;
        boolean prayer = false;
        boolean media = false;
        for (Card c : b.cards) {
            clock |= "clock".equals(c.type);
            prayer |= "prayer".equals(c.type);
            media |= "media".equals(c.type);
        }
        if (!clock || !prayer || !media) {
            return defaults();
        }
        if (isOldDefault(b)) {
            return defaults();
        }
        // Older layouts had no weather. Keep every saved card in place and use any
        // available 3x3 region; overlapping the smallest area is the last resort.
        Card best = null;
        int leastOverlap = Integer.MAX_VALUE;
        for (int row = 0; row <= ROWS - 3; row++) {
            for (int col = 0; col <= COLS - 3; col++) {
                int overlap = 0;
                for (Card existing : b.cards) {
                    int left = Math.max(col, existing.col);
                    int right = Math.min(col + 3, existing.col + existing.w);
                    int top = Math.max(row, existing.row);
                    int bottom = Math.min(row + 3, existing.row + existing.h);
                    overlap += Math.max(0, right - left) * Math.max(0, bottom - top);
                }
                if (overlap < leastOverlap) {
                    leastOverlap = overlap;
                    best = new Card("weather", col, row, 3, 3);
                }
            }
        }
        b.cards.add(best);
        return b;
    }
    private static boolean isOldDefault(BoardLayout b) {
        for (Card c : b.cards) {
            int col = "clock".equals(c.type) ? 0 : "prayer".equals(c.type) ? 4 : 8;
            if (c.col != col || c.row != 0 || c.w != 4 || c.h != 6) {
                return false;
            }
        }
        return true;
    }
}
