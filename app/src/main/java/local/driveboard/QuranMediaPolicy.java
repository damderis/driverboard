package local.driveboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure formatting and selection policy shared by the browser and connection observer. */
public final class QuranMediaPolicy {
    public static final String PACKAGE = "com.quran.labs.androidquran";
    private static final String PHONE_TAG = "QuranMediaSession";

    private QuranMediaPolicy() {}

    /** Prefer the phone tag; use the dashboard's stale-zero ranking within each group. */
    public static int select(List<String> tags, List<WidgetData.MediaCandidate> candidates) {
        ArrayList<WidgetData.MediaCandidate> preferred = new ArrayList<>();
        ArrayList<Integer> preferredIndices = new ArrayList<>();
        ArrayList<WidgetData.MediaCandidate> fallback = new ArrayList<>();
        ArrayList<Integer> fallbackIndices = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            WidgetData.MediaCandidate candidate = candidates.get(i);
            String tag = tags.get(i);
            if (!allowed(candidate.packageName, tag)) {
                continue;
            }
            fallback.add(candidate);
            fallbackIndices.add(i);
            if (PHONE_TAG.equals(tag)) {
                preferred.add(candidate);
                preferredIndices.add(i);
            }
        }
        return preferred.isEmpty() ? rankedIndex(fallback, fallbackIndices)
                : rankedIndex(preferred, preferredIndices);
    }

    private static int rankedIndex(ArrayList<WidgetData.MediaCandidate> candidates,
            ArrayList<Integer> indices) {
        if (candidates.isEmpty()) {
            return -1;
        }
        int selected = WidgetData.selectMediaCandidate(candidates);
        // A real phone session with no state still accepts transport play.
        return indices.get(selected < 0 ? 0 : selected);
    }

    public static boolean allowed(String packageName, String tag) {
        return PACKAGE.equals(packageName)
                && (tag == null || !tag.toLowerCase(Locale.ROOT).contains("media3"));
    }

    public static boolean resume(boolean enabled, boolean connected, boolean playing,
            long position, long buffered) {
        return enabled && connected && !(playing && (position > 0 || buffered > 0));
    }

    /** Consume one attempt per projection connection, including skipped attempts. */
    public static final class ResumeGate {
        private boolean connected;
        private boolean attempted;

        public boolean connectionChanged(boolean projection) {
            boolean started = projection && !connected;
            if (projection != connected) {
                attempted = false;
            }
            connected = projection;
            return started;
        }

        public boolean claim(boolean enabled, boolean playing, long position, long buffered) {
            return claim(enabled, playing && (position > 0 || buffered > 0));
        }

        public boolean claim(boolean enabled, boolean playingWithProgress) {
            if (!connected || attempted) {
                return false;
            }
            attempted = true;
            return enabled && !playingWithProgress;
        }
    }

    public static String title(String title, String description, boolean malay) {
        QuranMetadata parsed = QuranMetadata.parse(PACKAGE, title, description);
        return parsed == null ? nonempty(title, "Quran") : parsed.heading();
    }

    public static String lastRecitation(String title, String reciter) {
        String heading = nonempty(title, "");
        String artist = nonempty(reciter, "");
        return heading.isEmpty() ? artist : heading + (artist.isEmpty() ? "" : " · " + artist);
    }

    public static String prayerName(int index, boolean malay) {
        String[] names = malay ? new String[] {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"}
                : new String[] {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
        return names[index];
    }

    public static final class PrayerItem {
        public final String title;
        public final String subtitle;

        private PrayerItem(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    /** Countdown is already localized by WidgetData's JAKIM snapshot. */
    public static PrayerItem prayerItem(int index, String time, boolean next, String countdown,
            boolean malay) {
        String title = prayerName(index, malay) + " " + nonempty(time, "—");
        String subtitle = next ? (malay ? "seterusnya" : "next") : "";
        if (next && countdown != null && !countdown.trim().isEmpty()
                && !"—".equals(countdown)) {
            subtitle += " · " + countdown;
        }
        return new PrayerItem(title, subtitle);
    }

    public static String nextPrayer(int index, String time, String countdown, boolean malay) {
        return prayerName(index, malay) + " " + nonempty(time, "—")
                + (countdown == null || countdown.trim().isEmpty() || "—".equals(countdown)
                ? "" : " · " + countdown);
    }

    private static String nonempty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
