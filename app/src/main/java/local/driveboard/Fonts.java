package local.driveboard;

import android.content.Context;
import android.graphics.Typeface;

public final class Fonts {
    private static Typeface regular;
    private static Typeface semibold;
    private static Typeface extraBold;

    private Fonts() {}

    public static Typeface regular(Context context) {
        if (regular == null) {
            regular = context.getResources().getFont(R.font.atkinson_regular);
        }
        return regular;
    }

    public static Typeface semibold(Context context) {
        if (semibold == null) {
            semibold = context.getResources().getFont(R.font.atkinson_semibold);
        }
        return semibold;
    }

    public static Typeface extraBold(Context context) {
        if (extraBold == null) {
            extraBold = context.getResources().getFont(R.font.atkinson_extrabold);
        }
        return extraBold;
    }
}
