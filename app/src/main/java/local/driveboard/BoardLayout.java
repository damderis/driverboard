package local.driveboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java grid and version-one JSON codec; no Android runtime needed. */
public final class BoardLayout {
    public static final int COLS = 12, ROWS = 6;
    public static final String[] TYPES = {"clock", "prayer", "media"};
    public static final class Card {
        public final String type;
        public int col, row, w, h;
        public Card(String type, int col, int row, int w, int h) {
            this.type = type;
            this.w = clamp(w, 1, COLS); this.h = clamp(h, 1, ROWS);
            this.col = clamp(col, 0, COLS - this.w); this.row = clamp(row, 0, ROWS - this.h);
        }
    }
    public final List<Card> cards = new ArrayList<>();
    public static int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
    public static int snap(float pixels, float cell) { return cell <= 0 ? 0 : Math.round(pixels / cell); }
    public void move(Card c, int col, int row) { c.col = clamp(col, 0, COLS-c.w); c.row = clamp(row, 0, ROWS-c.h); }
    public void resize(Card c, int w, int h) { c.w = clamp(w, 1, COLS-c.col); c.h = clamp(h, 1, ROWS-c.row); }
    public static BoardLayout defaults() {
        BoardLayout b = new BoardLayout();
        b.cards.add(new Card("clock",0,0,4,6)); b.cards.add(new Card("prayer",4,0,4,6)); b.cards.add(new Card("media",8,0,4,6));
        return b;
    }
    public String toJson() {
        StringBuilder s = new StringBuilder("[");
        for (Card c : cards) {
            if (s.length()>1) s.append(',');
            s.append("{\"type\":\"").append(c.type).append("\",\"col\":").append(c.col)
                .append(",\"row\":").append(c.row).append(",\"w\":").append(c.w).append(",\"h\":").append(c.h).append('}');
        }
        return s.append(']').toString();
    }
    // Fixed schema written by this app, deliberately independent of org.json on Android.
    public static BoardLayout fromJson(String json) {
        if (json == null) return defaults();
        Pattern p = Pattern.compile("\\{\\s*\"type\"\\s*:\\s*\"(clock|prayer|media)\"\\s*,\\s*\"col\"\\s*:\\s*(-?\\d+)\\s*,\\s*\"row\"\\s*:\\s*(-?\\d+)\\s*,\\s*\"w\"\\s*:\\s*(-?\\d+)\\s*,\\s*\"h\"\\s*:\\s*(-?\\d+)\\s*\\}");
        BoardLayout b = new BoardLayout(); Matcher m = p.matcher(json);
        try {
            while (m.find()) {
                String type = m.group(1);
                for (Card c : b.cards) if (c.type.equals(type)) return defaults();
                b.cards.add(new Card(type,Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),Integer.parseInt(m.group(4)),Integer.parseInt(m.group(5))));
            }
        } catch (RuntimeException e) { return defaults(); }
        return b.cards.size() == 3 ? b : defaults();
    }
}
