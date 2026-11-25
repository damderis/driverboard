package local.driveboard;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Widget catalog shared by the board and the phone editor.
 * Add one catalog entry with its drawer reference and one BoardView draw method
 * for a new widget.
 */
public enum WidgetType {
    CLOCK("clock", R.string.widget_clock, "\u25F7", 4, 3, 2, 1, true, Padding.DEFAULT, Shape.CLOCK, BoardView::drawClock,
            choice("format", R.string.widget_time_format, "24h", "24h", "12h"),
            toggle("show_date", R.string.widget_show_date, true),
            toggle("show_hijri", R.string.widget_show_hijri, false)),
    PRAYER("prayer", R.string.widget_prayer, "\u262A", 8, 3, 3, 2, false, Padding.PRAYER, Shape.PRAYER, BoardView::drawPrayer,
            choice("view", R.string.widget_prayer_view, "full", "full", "next"),
            toggle("show_countdown", R.string.widget_show_countdown, true)),
    MEDIA("media", R.string.widget_media, "\u266B", 5, 3, 3, 2, false, Padding.MEDIA, Shape.MEDIA, BoardView::drawMedia,
            toggle("show_progress", R.string.widget_show_progress, true),
            toggle("show_controls", R.string.widget_show_controls, true)),
    WEATHER("weather", R.string.widget_weather, "\u2601", 3, 3, 2, 2, true, Padding.DEFAULT, Shape.WEATHER, BoardView::drawWeather,
            toggle("show_rain", R.string.widget_show_rain, true)),
    SHORTCUTS("shortcuts", R.string.widget_shortcuts, "\u2197", 6, 3, 3, 2, false, Padding.DEFAULT, Shape.CLOCK, BoardView::drawShortcuts),
    AUDIO("audio", R.string.widget_audio, "\u21C4", 4, 3, 3, 2, false, Padding.DEFAULT, Shape.CLOCK, BoardView::drawAudio);

    public interface Drawer {
        void draw(BoardView board, android.graphics.Canvas canvas, android.graphics.RectF bounds);
    }

    public enum Padding { DEFAULT, MEDIA, PRAYER }
    public enum Shape { CLOCK, PRAYER, MEDIA, WEATHER }

    public static final class OptionSpec {
        public final String key;
        public final int label;
        public final String kind;
        public final String defaultValue;
        public final String[] values;

        private OptionSpec(String key, int label, String kind, String defaultValue,
                String... values) {
            this.key = key;
            this.label = label;
            this.kind = kind;
            this.defaultValue = defaultValue;
            this.values = values.clone();
        }

        public boolean accepts(String value) {
            for (String allowed : values) {
                if (allowed.equals(value)) return true;
            }
            return false;
        }
    }

    public final String id;
    public final int label;
    public final String icon;
    public final int defaultW;
    public final int defaultH;
    public final int minW;
    public final int minH;
    public final boolean multiple;
    public final Padding padding;
    public final Shape shape;
    public final Drawer drawer;
    public final Map<String, OptionSpec> options;

    WidgetType(String id, int label, String icon, int defaultW, int defaultH,
            int minW, int minH, boolean multiple, Padding padding, Shape shape, Drawer drawer,
            OptionSpec... options) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.defaultW = defaultW;
        this.defaultH = defaultH;
        this.minW = minW;
        this.minH = minH;
        this.multiple = multiple;
        this.padding = padding;
        this.shape = shape;
        this.drawer = drawer;
        Map<String, OptionSpec> specs = new LinkedHashMap<>();
        for (OptionSpec option : options) specs.put(option.key, option);
        OptionSpec color = colorSpec();
        specs.put(color.key, color);
        this.options = Collections.unmodifiableMap(specs);
    }

    public Map<String, String> defaultOptions() {
        Map<String, String> defaults = new LinkedHashMap<>();
        for (OptionSpec spec : options.values()) defaults.put(spec.key, spec.defaultValue);
        return defaults;
    }

    public Map<String, String> normalizeOptions(Map<String, String> supplied) {
        Map<String, String> normalized = defaultOptions();
        if (supplied != null) {
            for (OptionSpec spec : options.values()) {
                String value = supplied.get(spec.key);
                if (spec.accepts(value)) normalized.put(spec.key, value);
            }
        }
        return normalized;
    }

    public static WidgetType fromId(String id) {
        if ("battery".equals(id)) return WEATHER;
        for (WidgetType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }

    /** Every card: grey glass by default, the theme's per-widget colour, or a fixed tint. */
    // A method, not a static field: enum constants are built before static fields exist.
    private static OptionSpec colorSpec() {
        return new OptionSpec("color", R.string.widget_color, "color", "grey",
                "grey", "theme", "teal", "blue", "purple", "green", "amber", "red");
    }

    /** Dark tints that keep light card text readable. */
    static int fixedColor(String value) {
        switch (value) {
            case "teal": return 0xFF0E4A46;
            case "blue": return 0xFF1F3A63;
            case "purple": return 0xFF3E2C61;
            case "green": return 0xFF24472E;
            case "amber": return 0xFF5C4414;
            case "red": return 0xFF5E2627;
            default: return 0;
        }
    }

    private static OptionSpec toggle(String key, int label, boolean defaultValue) {
        return new OptionSpec(key, label, "toggle", Boolean.toString(defaultValue),
                "true", "false");
    }

    private static OptionSpec choice(String key, int label, String defaultValue,
            String... values) {
        return new OptionSpec(key, label, "choice", defaultValue, values);
    }
}
