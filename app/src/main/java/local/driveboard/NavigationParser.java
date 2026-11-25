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
        return new NavigationParser(clean(instruction), clean(distance), clean(subText), fraction);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
