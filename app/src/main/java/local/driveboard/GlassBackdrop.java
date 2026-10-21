package local.driveboard;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared, asynchronously prepared full-board blur for matching card samples. */
final class GlassBackdrop {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ArrayList<Entry> ENTRIES = new ArrayList<>();

    private GlassBackdrop() {}

    static Bitmap get(Bitmap picture, int width, int height, BoardView waiter) {
        if (picture == null || picture.isRecycled() || width <= 0 || height <= 0) {
            return null;
        }
        synchronized (ENTRIES) {
            for (int i = ENTRIES.size() - 1; i >= 0; i--) {
                Entry entry = ENTRIES.get(i);
                if (entry.picture != picture || entry.width != width
                        || entry.height != height) {
                    entry.removeWaiter(waiter);
                    if (!entry.finished && entry.waiters.isEmpty()) {
                        ENTRIES.remove(i);
                    }
                }
            }
            for (Entry entry : ENTRIES) {
                if (entry.picture == picture && entry.width == width && entry.height == height) {
                    if (!entry.finished) {
                        entry.addWaiter(waiter);
                    }
                    return entry.result;
                }
            }
            Entry entry = new Entry(picture, width, height);
            entry.addWaiter(waiter);
            ENTRIES.add(entry);
            trimCompleted();
            WORKER.execute(() -> prepare(entry));
            return null;
        }
    }

    static double percentile90(Bitmap small, Path shape, int boardWidth, int boardHeight) {
        if (small == null || small.isRecycled() || boardWidth <= 0 || boardHeight <= 0) {
            return 0;
        }
        synchronized (small) {
            int width = small.getWidth();
            int height = small.getHeight();
            Region region = new Region();
            region.setPath(shape, new Region(0, 0, boardWidth, boardHeight));
            int[] pixels = new int[width * height];
            small.getPixels(pixels, 0, width, 0, 0, width, height);
            float[] point = new float[2];
            double[] values = new double[width * height];
            int count = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    point[0] = (x + .5f) * boardWidth / width;
                    point[1] = (y + .5f) * boardHeight / height;
                    if (region.contains(Math.round(point[0]), Math.round(point[1]))) {
                        values[count++] = pixelLuminance(pixels[y * width + x]);
                    }
                }
            }
            if (count == 0) {
                return 0;
            }
            java.util.Arrays.sort(values, 0, count);
            int index = (int) Math.ceil(count * .90) - 1;
            return values[Math.max(0, index)];
        }
    }

    static double percentile90(Bitmap picture, int width, int height, Path shape) {
        if (picture == null || picture.isRecycled() || width <= 0 || height <= 0) {
            return 0;
        }
        synchronized (ENTRIES) {
            for (Entry entry : ENTRIES) {
                if (entry.picture == picture && entry.width == width
                        && entry.height == height && entry.small != null
                        && !entry.small.isRecycled()) {
                    return percentile90(entry.small, shape, width, height);
                }
            }
        }
        return 0;
    }

    private static double pixelLuminance(int color) {
        return .2126 * linear((color >>> 16) & 255)
                + .7152 * linear((color >>> 8) & 255)
                + .0722 * linear(color & 255);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    private static void prepare(Entry entry) {
        synchronized (ENTRIES) {
            if (!ENTRIES.contains(entry) || entry.waiters.isEmpty()) {
                return;
            }
        }
        Bitmap result = null;
        Bitmap small = null;
        try {
            int width = Math.max(1, (entry.width + 7) / 8);
            int height = Math.max(1, (entry.height + 7) / 8);
            Bitmap sample = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(sample);
                Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                float scale = Math.max((float) entry.width / entry.picture.getWidth(),
                        (float) entry.height / entry.picture.getHeight());
                float sourceWidth = entry.width / scale;
                float sourceHeight = entry.height / scale;
                Rect source = new Rect(
                        Math.round((entry.picture.getWidth() - sourceWidth) / 2f),
                        Math.round((entry.picture.getHeight() - sourceHeight) / 2f),
                        Math.round((entry.picture.getWidth() + sourceWidth) / 2f),
                        Math.round((entry.picture.getHeight() + sourceHeight) / 2f));
                canvas.drawBitmap(entry.picture, source, new RectF(0, 0, width, height), paint);
                int[] pixels = new int[width * height];
                sample.getPixels(pixels, 0, width, 0, 0, width, height);
                int[] blurred = BoxBlur.blur(pixels, width, height, 12);
                small = Bitmap.createBitmap(blurred, width, height,
                        Bitmap.Config.ARGB_8888);
                result = Bitmap.createScaledBitmap(small, entry.width, entry.height, true);
            } finally {
                sample.recycle();
            }
        } catch (RuntimeException ignored) {
            // A valid tint remains available if the source bitmap disappears mid-build.
        }
        synchronized (ENTRIES) {
            entry.result = result;
            entry.small = small;
            entry.finished = true;
            for (WeakReference<BoardView> reference : entry.waiters) {
                BoardView view = reference.get();
                if (view != null) {
                    MAIN.post(view::onGlassBackdropReady);
                }
            }
            entry.waiters.clear();
            trimCompleted();
        }
    }

    private static void trimCompleted() {
        for (int i = 0; ENTRIES.size() > 3 && i < ENTRIES.size() - 1;) {
            if (ENTRIES.get(i).finished) {
                ENTRIES.remove(i);
            } else {
                i++;
            }
        }
    }

    private static final class Entry {
        final Bitmap picture;
        final int width;
        final int height;
        final ArrayList<WeakReference<BoardView>> waiters = new ArrayList<>();
        Bitmap result;
        Bitmap small;
        boolean finished;

        Entry(Bitmap picture, int width, int height) {
            this.picture = picture;
            this.width = width;
            this.height = height;
        }

        void addWaiter(BoardView view) {
            for (WeakReference<BoardView> reference : waiters) {
                if (reference.get() == view) {
                    return;
                }
            }
            waiters.add(new WeakReference<>(view));
        }

        void removeWaiter(BoardView view) {
            for (int i = waiters.size() - 1; i >= 0; i--) {
                BoardView waiting = waiters.get(i).get();
                if (waiting == null || waiting == view) {
                    waiters.remove(i);
                }
            }
        }
    }
}
