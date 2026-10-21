package local.driveboard;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.QuantizerCelebi;
import com.google.android.material.color.utilities.SchemeTonalSpot;
import com.google.android.material.color.utilities.Score;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Dark Material tonal roles shared by the phone preview and car renderer. */
public final class BoardColours {
    private static final Map<Bitmap, Integer> ART_SEEDS = new WeakHashMap<>();
    private static final LinkedHashMap<Integer, BoardColours> SCHEMES = new LinkedHashMap<>();
    public final int seed;
    public final int surface;
    public final int surfaceContainerLow;
    public final int surfaceContainerHigh;
    public final int surfaceContainerHighest;
    public final int primaryContainer;
    public final int secondaryContainer;
    public final int tertiaryContainer;
    public final int onSurface;
    public final int onSurfaceVariant;
    public final int primary;
    public final int onPrimary;
    public final int onPrimaryContainer;
    public final int outlineVariant;
    /** Highest role opacity, kept for callers that need a single conservative value. */
    public final int containerAlpha;
    private final int[] containerColours;
    private final int[] containerAlphas;

    private BoardColours(int seed) {
        this.seed = seed | 0xFF000000;
        SchemeTonalSpot scheme = new SchemeTonalSpot(Hct.fromInt(this.seed), true, 0.0);
        surface = scheme.getSurface();
        surfaceContainerLow = scheme.getSurfaceContainerLow();
        surfaceContainerHigh = scheme.getSurfaceContainerHigh();
        surfaceContainerHighest = scheme.getSurfaceContainerHighest();
        primaryContainer = scheme.getPrimaryContainer();
        secondaryContainer = scheme.getSecondaryContainer();
        tertiaryContainer = scheme.getTertiaryContainer();
        primary = scheme.getPrimary();
        onPrimary = scheme.getOnPrimary();
        onPrimaryContainer = scheme.getOnPrimaryContainer();
        outlineVariant = scheme.getOutlineVariant();
        containerColours = new int[]{surface, surfaceContainerLow, surfaceContainerHigh,
                surfaceContainerHighest, primaryContainer,
                secondaryContainer, tertiaryContainer};
        onSurface = accessible(scheme.getOnSurface(), scheme, containerColours);
        onSurfaceVariant = accessible(scheme.getOnSurfaceVariant(), scheme, containerColours);
        containerAlphas = new int[containerColours.length];
        int highest = 0;
        for (int i = 0; i < containerColours.length; i++) {
            containerAlphas[i] = readableContainerAlpha(containerColours[i],
                    onSurface, onSurfaceVariant);
            highest = Math.max(highest, containerAlphas[i]);
        }
        containerAlpha = highest;
    }

    public static synchronized BoardColours fromSeed(int seed) {
        int opaque = seed | 0xFF000000;
        BoardColours found = SCHEMES.get(opaque);
        if (found != null) {
            return found;
        }
        BoardColours made = new BoardColours(opaque);
        if (SCHEMES.size() >= 12) {
            SCHEMES.remove(SCHEMES.keySet().iterator().next());
        }
        SCHEMES.put(opaque, made);
        return made;
    }

    public static BoardColours current(Context context) {
        return current(context, null);
    }

    public static BoardColours current(Context context, Bitmap albumArt) {
        String source = Store.prefs(context).getString("colour_source", "wallpaper");
        int seed;
        Bitmap artwork = "background".equals(source) ? BackgroundStore.load(context) : albumArt;
        if (("album".equals(source) || "background".equals(source))
                && artwork != null && !artwork.isRecycled()) {
            synchronized (ART_SEEDS) {
                Integer cached = ART_SEEDS.get(artwork);
                if (cached == null) {
                    cached = artSeed(artwork);
                    ART_SEEDS.put(artwork, cached);
                }
                seed = cached;
            }
        } else if ("teal".equals(source)) {
            seed = 0xFF0F6E6E;
        } else if ("emerald".equals(source)) {
            seed = 0xFF3FB978;
        } else if ("navy".equals(source)) {
            seed = 0xFF688FCB;
        } else if ("maroon".equals(source)) {
            seed = 0xFFC36B82;
        } else if ("graphite".equals(source)) {
            seed = 0xFF818A91;
        } else {
            seed = context.getColor(android.R.color.system_accent1_500);
        }
        return fromSeed(seed);
    }

    private static int artSeed(Bitmap art) {
        Bitmap scaled = null;
        try {
            Bitmap software = art.getConfig() == Bitmap.Config.HARDWARE
                    ? art.copy(Bitmap.Config.ARGB_8888, false) : art;
            int width = Math.min(128, software.getWidth());
            int height = Math.min(128, software.getHeight());
            scaled = Bitmap.createScaledBitmap(software, width, height, true);
            int[] pixels = new int[width * height];
            scaled.getPixels(pixels, 0, width, 0, 0, width, height);
            if (software != art && software != scaled) {
                software.recycle();
            }
            return Score.score(QuantizerCelebi.quantize(pixels, 16),
                    1, 0xFF0F6E6E).get(0);
        } catch (RuntimeException ignored) {
            return 0xFF0F6E6E;
        } finally {
            if (scaled != null && scaled != art) {
                scaled.recycle();
            }
        }
    }

    private static int accessible(int preferred, SchemeTonalSpot scheme, int[] containers) {
        if (readableOnAll(preferred, containers)) {
            return preferred;
        }
        int alternative = scheme.neutralPalette.tone(95);
        return readableOnAll(alternative, containers) ? alternative : 0xFFFFFFFF;
    }

    private static boolean readableOnAll(int foreground, int[] containers) {
        for (int container : containers) {
            if (contrast(foreground, container) < 4.5) {
                return false;
            }
        }
        return true;
    }

    public int translucentContainer(int opaqueContainer) {
        return (alphaForContainer(opaqueContainer) << 24)
                | (opaqueContainer & 0x00FFFFFF);
    }

    public int alphaForContainer(int opaqueContainer) {
        for (int i = 0; i < containerColours.length; i++) {
            if (containerColours[i] == opaqueContainer) {
                return containerAlphas[i];
            }
        }
        return containerAlpha;
    }

    private static int readableContainerAlpha(int container, int text, int secondaryText) {
        for (int alpha = 0; alpha <= 255; alpha++) {
            int overWhite = composite(container, 0xFFFFFFFF, alpha);
            int overBlack = composite(container, 0xFF000000, alpha);
            if (contrast(text, overWhite) >= 4.5 && contrast(text, overBlack) >= 4.5
                    && contrast(secondaryText, overWhite) >= 4.5
                    && contrast(secondaryText, overBlack) >= 4.5) {
                return alpha;
            }
        }
        return 255;
    }

    /** Matches an 8-bit SRC_OVER fill on an opaque picture pixel. */
    static int composite(int foreground, int background, int alpha) {
        int inverse = 255 - alpha;
        int red = (((foreground >>> 16) & 255) * alpha
                + ((background >>> 16) & 255) * inverse + 127) / 255;
        int green = (((foreground >>> 8) & 255) * alpha
                + ((background >>> 8) & 255) * inverse + 127) / 255;
        int blue = ((foreground & 255) * alpha + (background & 255) * inverse + 127) / 255;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    public static double contrast(int a, int b) {
        double light = Math.max(luminance(a), luminance(b));
        double dark = Math.min(luminance(a), luminance(b));
        return (light + .05) / (dark + .05);
    }

    private static double luminance(int color) {
        double r = linear((color >>> 16) & 255);
        double g = linear((color >>> 8) & 255);
        double b = linear(color & 255);
        return .2126 * r + .7152 * g + .0722 * b;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
