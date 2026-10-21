package local.driveboard;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;

/** Bundled Google Sans Flex variable typeface; all weights share one font file. */
public final class Fonts {
    private static Typeface regular;
    private static Typeface medium;
    private static Typeface semibold;
    private static Typeface extraBold;

    private Fonts() {}

    private static synchronized void ensure(Context context) {
        if (regular != null) {
            return;
        }
        Typeface base = context.getResources().getFont(R.font.google_sans_flex);
        regular = Typeface.create(base, 400, false);
        medium = Typeface.create(base, 500, false);
        semibold = Typeface.create(base, 600, false);
        extraBold = Typeface.create(base, 800, false);
    }

    public static Typeface regular(Context context) {
        ensure(context);
        return regular;
    }

    public static Typeface semibold(Context context) {
        ensure(context);
        return semibold;
    }

    public static Typeface medium(Context context) {
        ensure(context);
        return medium;
    }

    public static Typeface extraBold(Context context) {
        ensure(context);
        return extraBold;
    }

    public static void cardText(Paint paint) {
        int weight = paint.getTypeface() == null ? 400 : paint.getTypeface().getWeight();
        paint.setFontVariationSettings("'wght' " + weight + ", 'wdth' 94, 'opsz' 18");
        paint.setFontFeatureSettings("tnum");
    }

    public static void displayText(Paint paint) {
        paint.setFontVariationSettings("'wght' 500, 'wdth' 110, 'opsz' 48");
        paint.setFontFeatureSettings("tnum");
    }
}
