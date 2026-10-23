package local.driveboard;

import java.text.Normalizer;
import java.util.Locale;

/** Pure playback-position math and reciter aliases; no Android dependencies. */
public final class QuranPosition {
    private QuranPosition() {}

    public static String normalize(String name) {
        return name == null ? "" : Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** IDs verified against resources/recitations. Ghamdi is not offered by that API. */
    public static int recitationId(String name) {
        String key = normalize(name);
        if (key.contains("ghamdi") || key.contains("غامدي")) return 0;
        if (key.contains("husary") || key.contains("hussary") || key.contains("حصري")) {
            return key.contains("muallim") ? 12 : 6;
        }
        if (key.contains("minsh") || key.contains("منشاوي")) {
            return key.contains("mujawwad") ? 8 : 9;
        }
        if (key.contains("afasy") || key.contains("alafasi") || key.contains("عفاسي")) return 7;
        if (key.contains("abdulbas") || key.contains("عبدالباسط")) {
            return key.contains("mujawwad") ? 1 : 2;
        }
        if (key.contains("sudais") || key.contains("سديس")) return 3;
        if (key.contains("shuraym") || key.contains("shuraim") || key.contains("شريم")) return 10;
        return 0;
    }

    /** Half-open intervals. Prefer the later verse in small API overlaps. */
    public static int lookup(long[] starts, long[] ends, long position) {
        if (position < 0 || starts == null || ends == null || starts.length != ends.length) return 0;
        for (int i = starts.length - 1; i >= 0; i--) {
            if (position >= starts[i] && position < ends[i]) return i + 1;
        }
        return 0;
    }

    /** One-based Uthmani text array. Unknown position/duration/text remains unresolved. */
    public static int estimate(String[] verses, long position, long duration) {
        if (verses == null || verses.length < 2 || position < 0 || duration <= 0) return 0;
        long total = 0;
        for (int i = 1; i < verses.length; i++) {
            if (verses[i] == null || verses[i].isEmpty()) return 0;
            total += verses[i].codePointCount(0, verses[i].length());
        }
        double target = Math.min(1d, position / (double) duration) * total;
        long cumulative = 0;
        for (int i = 1; i < verses.length; i++) {
            cumulative += verses[i].codePointCount(0, verses[i].length());
            if (target < cumulative) return i;
        }
        return verses.length - 1;
    }

    /** Notification ayah wins. A loaded timing table's gap must stay unresolved. */
    public static int resolve(int metadataAyah, boolean timingAvailable,
            int timedAyah, int estimatedAyah) {
        if (metadataAyah > 0) return metadataAyah;
        return timingAvailable ? Math.max(0, timedAyah) : Math.max(0, estimatedAyah);
    }

    public static boolean isApproximate(int metadataAyah, boolean timingAvailable,
            int estimatedAyah) {
        return metadataAyah <= 0 && !timingAvailable && estimatedAyah > 0;
    }
}
