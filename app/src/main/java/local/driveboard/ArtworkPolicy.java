package local.driveboard;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Android-free artwork and mirror decisions. */
public final class ArtworkPolicy {
    public static final String AUTO = "auto", BOARD = "board", LYRICS = "lyrics";
    private ArtworkPolicy() {}
    public static String mode(String mode) {
        return BOARD.equals(mode) || LYRICS.equals(mode) ? mode : AUTO;
    }
    public static String cycle(String mode) {
        return AUTO.equals(mode(mode)) ? BOARD : BOARD.equals(mode) ? LYRICS : AUTO;
    }
    public static boolean lyrics(String mode, boolean playing, boolean available) {
        return LYRICS.equals(mode) || AUTO.equals(mode(mode)) && playing && available;
    }
    public static boolean allowed(String ownPackage, String sourcePackage, String tag) {
        return !ownPackage.equals(sourcePackage) && !(QuranMediaPolicy.PACKAGE.equals(sourcePackage)
                && tag != null && tag.toLowerCase(Locale.ROOT).contains("media3"));
    }
    public static List<String> actions(boolean audioAvailable) {
        return audioAvailable ? Arrays.asList("view", "audio") : Arrays.asList("view");
    }
    public static boolean shouldRender(long now, long last, boolean moving, boolean dirty,
            long minute, long lastMinute) {
        return last < 0 || now - last >= 2000 && (dirty || moving || minute != lastMinute);
    }
    public static boolean changed(byte[] previous, byte[] next) {
        return !Arrays.equals(previous, next);
    }
}
