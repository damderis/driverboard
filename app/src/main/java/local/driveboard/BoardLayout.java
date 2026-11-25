package local.driveboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Grid placement and the versioned board layout codec. */
public final class BoardLayout {
    public static final int COLS = 12;
    public static final int ROWS = 6;
    public static final class Card {
        public final String id;
        public final String type;
        public int col;
        public int row;
        public int w;
        public int h;
        public final Map<String, String> options;

        public Card(String type, int col, int row, int w, int h) {
            this(type, type + "-1", col, row, w, h, null);
        }

        public Card(String type, String id, int col, int row, int w, int h,
                Map<String, String> options) {
            WidgetType widget = WidgetType.fromId(type);
            if (widget == null) throw new IllegalArgumentException("Unknown widget: " + type);
            this.type = widget.id;
            this.id = id;
            this.options = widget.normalizeOptions(options);
            this.w = clamp(w, 1, COLS);
            this.h = clamp(h, 1, ROWS);
            this.col = clamp(col, 0, COLS - this.w);
            this.row = clamp(row, 0, ROWS - this.h);
        }
    }

    public final List<Card> cards = new ArrayList<>();

    public BoardLayout copy() {
        BoardLayout copy = new BoardLayout();
        for (Card c : cards) {
            copy.cards.add(new Card(c.type, c.id, c.col, c.row, c.w, c.h, c.options));
        }
        return copy;
    }

    public boolean remove(Card card) {
        return cards.remove(card);
    }

    public Card add(String type) {
        WidgetType widget = WidgetType.fromId(type);
        if (widget == null || (!widget.multiple && containsType(widget.id))) return null;
        for (int area = widget.defaultW * widget.defaultH;
                area >= widget.minW * widget.minH; area--) {
            for (int h = widget.defaultH; h >= widget.minH; h--) {
                for (int w = widget.defaultW; w >= widget.minW; w--) {
                    if (w * h != area) continue;
                    for (int row = 0; row <= ROWS - h; row++) {
                        for (int col = 0; col <= COLS - w; col++) {
                            if (!canPlace(null, col, row, w, h)) continue;
                            Card card = new Card(widget.id, nextId(widget.id), col, row,
                                    w, h, null);
                            cards.add(card);
                            return card;
                        }
                    }
                }
            }
        }
        return null;
    }

    public Card add(String type, int col, int row) {
        WidgetType widget = WidgetType.fromId(type);
        if (widget == null || (!widget.multiple && containsType(widget.id))) return null;
        if (!canPlace(null, col, row, widget.defaultW, widget.defaultH)) return null;
        Card card = new Card(widget.id, nextId(widget.id), col, row,
                widget.defaultW, widget.defaultH, null);
        cards.add(card);
        return card;
    }

    public Card duplicate(Card source) {
        if (source == null || !cards.contains(source)) return null;
        WidgetType widget = WidgetType.fromId(source.type);
        if (widget == null || !widget.multiple) return null;
        int width = Math.max(widget.minW, source.w);
        int height = Math.max(widget.minH, source.h);
        for (int distance = 0; distance <= COLS + ROWS; distance++) {
            for (int row = 0; row <= ROWS - height; row++) {
                for (int col = 0; col <= COLS - width; col++) {
                    if (Math.abs(col - source.col) + Math.abs(row - source.row) != distance
                            || !canPlace(null, col, row, width, height)) continue;
                    Card card = new Card(source.type, nextId(source.type), col, row,
                            width, height, source.options);
                    cards.add(card);
                    return card;
                }
            }
        }
        return null;
    }

    public boolean canPlace(Card moving, int col, int row, int w, int h) {
        if (col < 0 || row < 0 || w < 1 || h < 1 || col + w > COLS || row + h > ROWS)
            return false;
        for (Card card : cards) {
            if (card != moving && col < card.col + card.w && card.col < col + w
                    && row < card.row + card.h && card.row < row + h) return false;
        }
        return true;
    }

    public boolean tryMove(Card c, int col, int row) {
        if (!cards.contains(c)) return false;
        col = clamp(col, 0, COLS - c.w);
        row = clamp(row, 0, ROWS - c.h);
        if (!canPlace(c, col, row, c.w, c.h)) return false;
        c.col = col;
        c.row = row;
        return true;
    }

    public boolean tryResize(Card c, int w, int h) {
        if (!cards.contains(c)) return false;
        WidgetType widget = WidgetType.fromId(c.type);
        if (COLS - c.col < widget.minW || ROWS - c.row < widget.minH) return false;
        w = clamp(w, widget.minW, COLS - c.col);
        h = clamp(h, widget.minH, ROWS - c.row);
        if (!canPlace(c, c.col, c.row, w, h)) return false;
        c.w = w;
        c.h = h;
        return true;
    }

    private boolean containsType(String type) {
        for (Card card : cards) if (card.type.equals(type)) return true;
        return false;
    }

    private String nextId(String type) {
        return type + "-" + UUID.randomUUID();
    }

    public static int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(max, n));
    }

    public static int snap(float pixels, float cell) {
        return cell <= 0 ? 0 : Math.round(pixels / cell);
    }

    public void move(Card c, int col, int row) {
        tryMove(c, col, row);
    }

    public void resize(Card c, int w, int h) {
        tryResize(c, w, h);
    }

    public static BoardLayout defaults() {
        BoardLayout b = new BoardLayout();
        b.cards.add(new Card("clock", 0, 0, 4, 6));
        b.cards.add(new Card("prayer", 4, 0, 8, 3));
        b.cards.add(new Card("media", 4, 3, 5, 3));
        b.cards.add(new Card("weather", 9, 3, 3, 3));
        return b;
    }

    /** Defaults for each car page: Drive, Media, Controls. */
    public static BoardLayout pageDefaults(int page, boolean split) {
        BoardLayout b = new BoardLayout();
        if (page == 1) {
            if (split) {
                b.cards.add(new Card("media", 0, 0, 12, 4));
                b.cards.add(new Card("clock", 0, 4, 12, 2));
            } else {
                b.cards.add(new Card("media", 0, 0, 8, 6));
                b.cards.add(new Card("clock", 8, 0, 4, 3));
                b.cards.add(new Card("prayer", 8, 3, 4, 3));
            }
            return b;
        }
        if (page == 2) {
            if (split) {
                b.cards.add(new Card("shortcuts", 0, 0, 12, 4));
                b.cards.add(new Card("audio", 0, 4, 12, 2));
            } else {
                b.cards.add(new Card("shortcuts", 0, 0, 7, 6));
                b.cards.add(new Card("audio", 7, 0, 5, 4));
                b.cards.add(new Card("clock", 7, 4, 5, 2));
            }
            return b;
        }
        return split ? splitDefaults() : defaults();
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
        Card prayer = findType(split, "prayer");
        Card clock = findType(split, "clock");
        Card media = findType(split, "media");
        Card weather = findType(split, "weather");
        BoardLayout b = new BoardLayout();
        // Beside Android Auto's panel the side card usually shows the map, so music lives on the
        // board; the clock is already in Android Auto's status bar.
        if (media != null) {
            if (prayer != null) b.cards.add(derived(prayer, 0, 0, 12, 2));
            b.cards.add(derived(media, 0, 2, 12, 3));
            Card bottom = weather != null ? weather : clock;
            if (bottom != null) b.cards.add(derived(bottom, 0, 5, 12, 1));
            return b;
        }
        if (prayer != null) b.cards.add(derived(prayer, 0, 0, 12, 3));
        if (clock != null) b.cards.add(derived(clock, 0, 3, 12, 2));
        if (weather != null) b.cards.add(derived(weather, 0, 5, 12, 1));
        return b;
    }

    private static Card findType(BoardLayout layout, String type) {
        for (Card card : layout.cards) if (type.equals(card.type)) return card;
        return null;
    }

    private static Card derived(Card source, int col, int row, int w, int h) {
        return new Card(source.type, source.id, col, row, w, h, source.options);
    }

    public static BoardLayout splitDefaults() {
        BoardLayout b = new BoardLayout();
        b.cards.add(new Card("prayer", 0, 0, 12, 3));
        b.cards.add(new Card("clock", 0, 3, 6, 3));
        b.cards.add(new Card("weather", 6, 3, 6, 3));
        return b;
    }

    public String toJson() {
        StringBuilder s = new StringBuilder("{\"version\":2,\"cards\":[");
        for (Card c : cards) {
            if (s.charAt(s.length() - 1) != '[') s.append(',');
            s.append("{\"id\":").append(JSONObject.quote(c.id))
                    .append(",\"type\":").append(JSONObject.quote(c.type))
                    .append(",\"col\":").append(c.col)
                    .append(",\"row\":").append(c.row).append(",\"w\":").append(c.w)
                    .append(",\"h\":").append(c.h).append(",\"options\":{");
            for (Map.Entry<String, String> option : c.options.entrySet()) {
                if (s.charAt(s.length() - 1) != '{') s.append(',');
                s.append(JSONObject.quote(option.getKey())).append(':')
                        .append(JSONObject.quote(option.getValue()));
            }
            s.append("}}");
        }
        return s.append("]}").toString();
    }

    public static BoardLayout fromJson(String json) {
        return fromJson(json, false);
    }

    public static BoardLayout fromSaved(String current, String legacy, boolean split) {
        return fromJson(current == null && !split ? legacy : current, split);
    }

    public static BoardLayout fromJson(String json, boolean split) {
        try {
            if (json == null) return fallback(split);
            String trimmed = json.trim();
            if (trimmed.startsWith("{")) return parseV2(new JSONObject(trimmed));
            return migrateV1(new JSONArray(trimmed), split);
        } catch (JSONException | IllegalArgumentException e) {
            return fallback(split);
        }
    }

    private static BoardLayout parseV2(JSONObject root) throws JSONException {
        if (root.getInt("version") != 2) throw new JSONException("Unsupported layout version");
        JSONArray source = root.getJSONArray("cards");
        BoardLayout b = new BoardLayout();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.getJSONObject(i);
            String type = item.getString("type");
            WidgetType widget = WidgetType.fromId(type);
            if (widget == null || (!widget.multiple && b.containsType(widget.id))) continue;
            String id = item.getString("id");
            if (id.isEmpty() || !ids.add(id)) throw new JSONException("Duplicate widget ID");
            int col = item.getInt("col");
            int row = item.getInt("row");
            int w = item.getInt("w");
            int h = item.getInt("h");
            // Old layouts can contain small or overlapping cards. Keep them intact
            // across the first v2 save; new editor actions enforce catalog minima.
            if (col < 0 || row < 0 || w < 1 || h < 1
                    || col + w > COLS || row + h > ROWS)
                throw new JSONException("Invalid widget placement");
            Map<String, String> options = new LinkedHashMap<>();
            JSONObject supplied = item.optJSONObject("options");
            if (supplied != null) {
                for (String key : widget.options.keySet()) {
                    if (supplied.has(key)) options.put(key, supplied.optString(key));
                }
            }
            b.cards.add(new Card(widget.id, id, col, row, w, h, options));
        }
        return b;
    }

    private static BoardLayout migrateV1(JSONArray source, boolean split) throws JSONException {
        BoardLayout b = new BoardLayout();
        boolean sawUnknown = false;
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.getJSONObject(i);
            WidgetType widget = WidgetType.fromId(item.getString("type"));
            if (widget == null) {
                sawUnknown = true;
                continue;
            }
            if (b.containsType(widget.id)) return fallback(split);
            b.cards.add(new Card(widget.id, widget.id + "-1", item.getInt("col"),
                    item.getInt("row"), item.getInt("w"), item.getInt("h"), null));
        }
        if (sawUnknown) return b.cards.isEmpty() ? fallback(split) : b;
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

    private static BoardLayout fallback(boolean split) {
        return split ? splitDefaults() : defaults();
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
