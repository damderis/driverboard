package local.driveboard;

import java.util.Locale;
import java.util.regex.Pattern;

/** Matching rules for media metadata; display strings are never changed. */
public final class LyricsMatch {
    private static final Pattern DECORATION = Pattern.compile(
            "(?i)\\s*[-–—:]?\\s*[\\[(]\\s*(?:official\\s+)?(?:music\\s+video|lyric\\s+video|video|audio|remastered(?:\\s+[^\\]\\)]+)?|live(?:\\s+[^\\]\\)]+)?)\\s*[\\])]\\s*$");
    private static final Pattern FEAT = Pattern.compile(
            "(?i)\\s+(?:[-–—:]\\s*)?(?:\\(|\\[)?\\s*(?:featuring|feat\\.?|ft\\.?)\\s+.+$");

    private LyricsMatch() {}

    public static String title(String value) {
        String result = value == null ? "" : value.trim();
        String previous;
        do {
            previous = result;
            result = DECORATION.matcher(result).replaceFirst("").trim();
        } while (!previous.equals(result));
        result = FEAT.matcher(result).replaceFirst("").trim();
        do {
            previous = result;
            result = DECORATION.matcher(result).replaceFirst("").trim();
        } while (!previous.equals(result));
        return result.replaceAll("\\s+", " ");
    }

    public static String leadArtist(String value) {
        if (value == null) return "";
        String artist = value.trim().replaceFirst("(?i)\\s+-\\s+topic\\s*$", "");
        return artist.split("(?i)\\s*(?:,|&|\\+|/|;|\\bx\\b|\\bfeat\\.?|\\bft\\.?|\\bfeaturing\\b|\\bwith\\b)\\s*", 2)[0].trim();
    }

    public static boolean sameTitle(String first, String second) {
        return key(title(first)).equals(key(title(second))) && !title(first).isEmpty();
    }

    public static boolean sameArtist(String first, String second) {
        return key(leadArtist(first)).equals(key(leadArtist(second))) && !leadArtist(first).isEmpty();
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    public static boolean durationMatches(long expectedMs, double candidateSeconds, int matchingCount) {
        if (expectedMs <= 0 || candidateSeconds <= 0) return true;
        int tolerance = matchingCount == 1 ? 20 : 12;
        return Math.abs(expectedMs / 1000.0 - candidateSeconds) <= tolerance;
    }
}
