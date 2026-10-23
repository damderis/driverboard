package local.driveboard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Quran playback position exposed by the Quran for Android media notification. */
public final class QuranMetadata {
    private static final String PACKAGE = "com.quran.labs.androidquran";
    private static final Pattern AYAH = Pattern.compile(
            "(?iu)\\b(?:ayah|ayat|verse)\\s*(?:no\\.?\\s*)?[#:]?\\s*(\\d{1,3})\\b");

    public final QuranCatalog.Entry surah;
    /** One-based ayah, or zero when the player only identifies a surah. */
    public final int ayah;

    private QuranMetadata(QuranCatalog.Entry surah, int ayah) {
        this.surah = surah;
        this.ayah = ayah;
    }

    /** Retain the identified surah while resolving a missing ayah from playback position. */
    public QuranMetadata withAyah(int ayah) {
        return new QuranMetadata(surah, ayah >= 1 && ayah <= surah.ayahCount ? ayah : 0);
    }

    public static QuranMetadata parse(String packageName, String title, String description) {
        if (!PACKAGE.equals(packageName)) return null;
        QuranCatalog.Entry fromTitle = QuranCatalog.match(chapterPart(title));
        QuranCatalog.Entry surah = fromTitle != null
                ? fromTitle : QuranCatalog.match(chapterPart(description));
        if (surah == null) {
            return null;
        }

        // A description may name a reciter or a different track. When the title already
        // names a surah, only its explicit ayah should control the displayed position.
        String positionSource = fromTitle != null ? title : description;
        int ayah = parseAyah(positionSource);
        if (ayah > surah.ayahCount) {
            ayah = 0;
        }
        return new QuranMetadata(surah, ayah);
    }

    private static String chapterPart(String value) {
        if (value == null) {
            return null;
        }
        Matcher ayah = AYAH.matcher(value);
        String name = ayah.find() ? value.substring(0, ayah.start()) : value;
        name = name.split("\\s+(?:[-–—·|])\\s+|[,|:]", 2)[0];
        return name.trim().replaceFirst("[\\p{Punct}·–—\\s]+$", "");
    }

    private static int parseAyah(String value) {
        if (value == null) {
            return 0;
        }
        Matcher matcher = AYAH.matcher(value);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public String heading() {
        return surah.transliteration + (ayah > 0 ? " · " + ayah : "");
    }
}
