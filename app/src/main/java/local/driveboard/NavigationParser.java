package local.driveboard;

/** Notification text/progress normalization without Android dependencies. */
public final class NavigationParser {
    public final String instruction;
    public final String distance;
    public final String eta;
    public final float progress;

    private NavigationParser(String instruction, String distance, String eta, float progress) {
        this.instruction = instruction;
        this.distance = distance;
        this.eta = eta;
        this.progress = progress;
    }

    public static NavigationParser parse(String instruction, String distance, String subText,
            long progress, long progressMax) {
        float fraction = progressMax <= 0 ? 0f
                : Math.max(0f, Math.min(1f, (float) progress / progressMax));
        String headline = clean(distance);
        return new NavigationParser(withoutDistancePrefix(instruction, headline), headline,
                clean(subText), fraction);
    }

    /** Remove only the displayed distance at the start, preserving other road numbers. */
    public static String withoutDistancePrefix(String instruction, String distance) {
        String text = clean(instruction);
        String headline = clean(distance);
        if (headline.isEmpty()) return text;
        String[] parts = headline.split("[\\s\\u00a0\\u202f]+");
        StringBuilder distancePattern = new StringBuilder();
        for (String part : parts) {
            if (distancePattern.length() > 0) distancePattern.append("[\\s\\u00a0\\u202f]+");
            distancePattern.append(java.util.regex.Pattern.quote(part));
        }
        java.util.regex.Matcher prefix = java.util.regex.Pattern.compile("^"
                + distancePattern
                + "(?=$|[\\s\\u00a0\\u202f·•|:–—-])[\\s\\u00a0\\u202f·•|:–—-]*",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        return prefix.find() ? text.substring(prefix.end()).trim() : text;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
