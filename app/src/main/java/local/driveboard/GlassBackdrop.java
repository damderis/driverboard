package local.driveboard;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
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

    private static void prepare(Entry entry) {
        synchronized (ENTRIES) {
            if (!ENTRIES.contains(entry) || entry.waiters.isEmpty()) {
                return;
            }
        }
        Bitmap result = null;
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
                Bitmap small = Bitmap.createBitmap(blurred, width, height,
                        Bitmap.Config.ARGB_8888);
                try {
                    result = Bitmap.createScaledBitmap(small, entry.width, entry.height, true);
                } finally {
                    if (result != small) {
                        small.recycle();
                    }
                }
            } finally {
                sample.recycle();
            }
        } catch (RuntimeException ignored) {
            // A valid tint remains available if the source bitmap disappears mid-build.
        }
        synchronized (ENTRIES) {
            entry.result = result;
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
