package local.driveboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Private, downscaled board background shared by the phone preview and car. */
public final class BackgroundStore {
    private static final int MAX_SIDE = 1920;
    private static final Object FILE_WRITES = new Object();
    private static Bitmap cached;
    private static long cachedVersion = Long.MIN_VALUE;
    private static String cachedPath;
    private static long cachedModified = Long.MIN_VALUE;

    private BackgroundStore() {}

    private static File imageFile(Context context) {
        return new File(context.getFilesDir(), "board_background.jpg");
    }

    public static synchronized void cleanupLegacy(Context context) {
        SharedPreferences prefs = Store.prefs(context);
        if (prefs.getBoolean("car_files_removed_6a", false)) {
            return;
        }
        boolean removed = true;
        for (String name : new String[]{"car_source.png", "car_render.png",
                "car_source.png.tmp", "car_render.png.tmp"}) {
            File file = new File(context.getFilesDir(), name);
            removed &= !file.exists() || file.delete();
        }
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : new String[]{"car_source_at", "car_render_at", "car_tap_x",
                "car_tap_y", "car_target", "car_tolerance", "car_brightness", "tone"}) {
            edit.remove(key);
        }
        edit.putBoolean("car_files_removed_6a", removed).apply();
    }

    public static synchronized Bitmap load(Context context) {
        long version = Store.prefs(context).getLong("background_at", 0);
        File file = imageFile(context);
        String path = file.getAbsolutePath();
        long modified = file.lastModified();
        if (version != cachedVersion || !path.equals(cachedPath)
                || modified != cachedModified) {
            // A previous frame may still own the old bitmap; let GC reclaim it safely.
            cached = version == 0 ? null : BitmapFactory.decodeFile(path);
            cachedVersion = version;
            cachedPath = path;
            cachedModified = modified;
        }
        return cached;
    }

    public static String identity(Context context) {
        File file = imageFile(context);
        return file.getAbsolutePath() + ":" + file.lastModified() + ":"
                + Store.prefs(context).getLong("background_at", 0);
    }

    public static void importSource(Context context, Uri uri) throws IOException {
        synchronized (FILE_WRITES) {
            importImage(context, uri);
        }
    }

    private static void importImage(Context context, Uri uri) throws IOException {
        ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), uri);
        Bitmap image = ImageDecoder.decodeBitmap(source, (decoder, info, ignored) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            int width = info.getSize().getWidth();
            int height = info.getSize().getHeight();
            int longest = Math.max(width, height);
            if (longest > MAX_SIDE) {
                decoder.setTargetSize(Math.max(1, Math.round(width * (float) MAX_SIDE / longest)),
                        Math.max(1, Math.round(height * (float) MAX_SIDE / longest)));
            }
        });
        Bitmap scaled = image;
        File destination = imageFile(context);
        File temporary = new File(context.getFilesDir(), "board_background.jpg.tmp");
        try {
            int longest = Math.max(image.getWidth(), image.getHeight());
            if (longest > MAX_SIDE) {
                scaled = Bitmap.createScaledBitmap(image,
                        Math.max(1, Math.round(image.getWidth() * (float) MAX_SIDE / longest)),
                        Math.max(1, Math.round(image.getHeight() * (float) MAX_SIDE / longest)), true);
            }
            try (FileOutputStream stream = new FileOutputStream(temporary)) {
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                    throw new IOException("Cannot encode background");
                }
                stream.getFD().sync();
            }
            if (!temporary.renameTo(destination)) {
                throw new IOException("Cannot save background");
            }
            long last = Store.prefs(context).getLong("background_at", 0);
            SharedPreferences prefs = Store.prefs(context);
            SharedPreferences.Editor edit = prefs.edit().putLong("background_at",
                    Math.max(System.currentTimeMillis(), last + 1));
            edit.putString("colour_source", "background");
            edit.commit();
            synchronized (BackgroundStore.class) {
                cachedVersion = Long.MIN_VALUE;
            }
        } finally {
            temporary.delete();
            if (scaled != image) {
                scaled.recycle();
            }
            image.recycle();
        }
    }

    public static void remove(Context context) throws IOException {
        synchronized (FILE_WRITES) {
            File file = imageFile(context);
            if (file.exists() && !file.delete()) {
                throw new IOException("Cannot remove background");
            }
            SharedPreferences prefs = Store.prefs(context);
            SharedPreferences.Editor edit = prefs.edit().remove("background_at");
            if ("background".equals(prefs.getString("colour_source", "wallpaper"))) {
                edit.putString("colour_source", "wallpaper");
            }
            edit.commit();
            synchronized (BackgroundStore.class) {
                cached = null;
                cachedVersion = Long.MIN_VALUE;
            }
        }
    }
}
