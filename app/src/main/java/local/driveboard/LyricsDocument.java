package local.driveboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed lyrics with optional LRC timestamps. No Android dependency. */
public final class LyricsDocument {
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE);

    public static final class Line {
        public final long timeMs;
        public final String text;

        public Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    public final String plainText;
    public final Line[] lines;

    private LyricsDocument(String plainText, Line[] lines) {
        this.plainText = plainText == null ? "" : plainText.trim();
        this.lines = lines;
    }

    /** "Aziz Harun & Dalia Farhana" matches "Aziz Harun, Dalia Farhana": compare the lead artist. */
    public static boolean sameArtist(String first, String second) {
        String lead = leadArtist(first);
        return !lead.isEmpty() && lead.equals(leadArtist(second));
    }

    private static String leadArtist(String artists) {
        String lead = artists.toLowerCase(java.util.Locale.ROOT)
                .split("\\s*(,|&|\\+|/|;|\\bx\\b|\\bfeat\\.?|\\bft\\.?|\\bwith\\b)\\s*")[0];
        return lead.replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    public static LyricsDocument parse(String syncedLyrics, String plainLyrics) {
        List<Line> parsed = new ArrayList<>();
        long offset = 0;
        if (syncedLyrics != null) {
            Matcher om = OFFSET.matcher(syncedLyrics);
            if (om.find()) {
                try {
                    offset = Long.parseLong(om.group(1));
                } catch (NumberFormatException ignored) {
                    // An invalid offset does not discard valid timed lines.
                }
            }
            for (String raw : syncedLyrics.split("\\r?\\n")) {
                Matcher matcher = STAMP.matcher(raw);
                List<Long> times = new ArrayList<>();
                int end = 0;
                while (matcher.find()) {
                    try {
                        long minutes = Long.parseLong(matcher.group(1));
                        long seconds = Long.parseLong(matcher.group(2));
                        if (seconds >= 60) continue;
                        String fraction = matcher.group(3);
                        long ms = fraction == null ? 0 : Long.parseLong(
                                (fraction + "00").substring(0, 3));
                        times.add(Math.max(0, minutes * 60_000 + seconds * 1000 + ms + offset));
                        end = matcher.end();
                    } catch (NumberFormatException ignored) {
                        // Ignore only this malformed timestamp.
                    }
                }
                String text = raw.substring(end).trim();
                for (long time : times) parsed.add(new Line(time, text));
            }
        }
        parsed.sort(Comparator.comparingLong(line -> line.timeMs));
        return new LyricsDocument(plainLyrics, parsed.toArray(new Line[0]));
    }

    public boolean isTimed() {
        return lines.length > 0;
    }

    public boolean isEmpty() {
        if (!plainText.isEmpty()) return false;
        for (Line line : lines) {
            if (!line.text.isEmpty()) return false;
        }
        return true;
    }

    /** Evenly spaces nonblank plain lines for approximate progress display. */
    public LyricsDocument estimated(long durationMs) {
        if (isTimed() || durationMs <= 0 || plainText.isEmpty()) return this;
        List<String> text = new ArrayList<>();
        for (String raw : plainText.split("\\r?\\n")) {
            if (!raw.trim().isEmpty()) text.add(raw.trim());
        }
        if (text.isEmpty()) return this;
        Line[] estimated = new Line[text.size()];
        for (int i = 0; i < text.size(); i++) {
            estimated[i] = new Line(durationMs * i / text.size(), text.get(i));
        }
        return new LyricsDocument(plainText, estimated);
    }

    /** Returns -1 before the first timestamp, or when lyrics are not timed. */
    public int lineIndexAt(long positionMs) {
        int low = 0;
        int high = lines.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (lines[mid].timeMs <= positionMs) low = mid + 1;
            else high = mid;
        }
        return low - 1;
    }
}
