package local.driveboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Private image files and preferences shared by the editor and car surface. */
public final class CarPictureStore {
    private static final String SOURCE = "car_source.png";
    private static final String RENDER = "car_render.png";

    private CarPictureStore() {}

    public static File sourceFile(Context context) {
        return new File(context.getFilesDir(), SOURCE);
    }

    public static File renderFile(Context context) {
        return new File(context.getFilesDir(), RENDER);
    }

    public static synchronized void importSource(Context context, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                throw new IOException("Cannot open selected image");
            }
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Selected file is not an image");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2048) {
            options.inSampleSize *= 2;
        }
        Bitmap image;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                throw new IOException("Cannot reopen selected image");
            }
            image = BitmapFactory.decodeStream(stream, null, options);
        }
        if (image == null) {
            throw new IOException("Cannot decode selected image");
        }
        int longest = Math.max(image.getWidth(), image.getHeight());
        Bitmap scaled = image;
        if (longest > 1024) {
            int width = Math.max(1, Math.round(image.getWidth() * 1024f / longest));
            int height = Math.max(1, Math.round(image.getHeight() * 1024f / longest));
            scaled = Bitmap.createScaledBitmap(image, width, height, true);
        }
        try {
            writeBitmap(sourceFile(context), scaled);
        } finally {
            if (scaled != image) {
                scaled.recycle();
            }
            image.recycle();
        }
        long stamp = nextStamp(context);
        Store.prefs(context).edit().remove("car_tap_x").remove("car_tap_y")
                .remove("car_target").remove("car_tolerance").remove("car_brightness")
                .putLong("car_source_at", stamp).commit();
    }

    public static synchronized boolean saveRender(Context context, Bitmap image,
            long sourceStamp, float tapX, float tapY, int tolerance, int target,
            float brightness) throws IOException {
        if (Store.prefs(context).getLong("car_source_at", 0) != sourceStamp) {
            return false;
        }
        writeBitmap(renderFile(context), image);
        if (Store.prefs(context).getLong("car_source_at", 0) != sourceStamp) {
            renderFile(context).delete();
            return false;
        }
        Store.prefs(context).edit().putFloat("car_tap_x", tapX)
                .putFloat("car_tap_y", tapY).putInt("car_tolerance", tolerance)
                .putInt("car_target", target).putFloat("car_brightness", brightness)
                .putLong("car_render_at", nextStamp(context)).commit();
        return true;
    }

    private static long nextStamp(Context context) {
        long last = Math.max(Store.prefs(context).getLong("car_render_at", 0),
                Store.prefs(context).getLong("car_source_at", 0));
        return Math.max(System.currentTimeMillis(), last + 1);
    }

    private static void writeBitmap(File destination, Bitmap bitmap) throws IOException {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(temporary)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new IOException("Cannot encode car image");
            }
            stream.getFD().sync();
        }
        if (!temporary.renameTo(destination)) {
            if (destination.exists() && destination.delete() && temporary.renameTo(destination)) {
                return;
            }
            throw new IOException("Cannot save car image");
        }
    }
}
