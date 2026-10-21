package local.driveboard;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.View;


import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

/** Renders the same board as the phone preview into the host's navigation surface. */
public final class BoardScreen extends Screen {
    private static final String TAG = "BoardScreen";
    private static final String CAR_TAG = "DriveBoardCar";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final BoardView board;
    private final WeatherRepository.Callback weatherCallback = snapshot -> requestDraw();
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (preferences, key) -> {
                if (!"surface_width".equals(key) && !"surface_height".equals(key)
                        && !"visible_width".equals(key) && !"visible_height".equals(key)
                        && !"full_width".equals(key) && !"full_height".equals(key)
                        && !"split_width".equals(key) && !"split_height".equals(key)) {
                    requestDraw();
                }
            };
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            drawBoard();
            if (started) {
                scheduleTick();
            }
        }
    };
    private final Runnable lyricsTick = new Runnable() {
        @Override
        public void run() {
            if (!started || !lyricsVisible || surface == null || !surface.isValid()) {
                return;
            }
            WidgetData.MediaSnapshot media = WidgetData.mediaSnapshot(getCarContext());
            String key = media.hasTrack ? LyricsRepository.key(media) : "";
            if (!key.equals(lyricsTrackKey)) {
                drawBoard();
            } else {
                int previous = board.lyricsLineIndex();
                board.setLyricsPosition(media.estimatedPositionMs());
                if (previous != board.lyricsLineIndex()) {
                    drawBoard();
                }
            }
            handler.postDelayed(this, 250);
        }
    };

    private SurfaceCallback surfaceCallback;
    private int consecutiveFailures;
    private long firstFailureAt;
    private long lastRecovery = -5000;
    private final android.view.Choreographer.FrameCallback frameCallback = this::onFrame;
    private long lastFrame;
    private boolean frameScheduled;
    private int slowFrames;
    private int fps = 30;
    private long lastRefresh;
    private boolean refreshNeeded = true;
    private boolean transitionLogging;
    private long transitionFrameTotalNanos;
    private int transitionFrameCount;

    private void onFrame(long nanos) {
        frameScheduled = false;
        if (!started || surface == null || !surface.isValid()
                || (!board.isMediaPlaying() && !board.isTransitioning())) {
            return;
        }
        if (board.isTransitioning() || nanos - lastFrame >= 1_000_000_000L / fps) {
            lastFrame = nanos;
            long start = android.os.SystemClock.elapsedRealtimeNanos();
            drawBoard();
            long duration = android.os.SystemClock.elapsedRealtimeNanos() - start;
            slowFrames = duration > 33_000_000 ? slowFrames + 1 : 0;
            if (slowFrames >= 3 && fps != 15) {
                fps = 15;
                CarDiagnostics.log(getCarContext(), "Car frame budget exceeded; dropping to 15 fps");
            }
        }
        scheduleFrame();
    }

    private void scheduleFrame() {
        if (started && surface != null && surface.isValid()
                && (board.isMediaPlaying() || board.isTransitioning()) && !frameScheduled) {
            frameScheduled = true;
            android.view.Choreographer.getInstance().postFrameCallback(frameCallback);
        }
    }

    private void drawFailed(RuntimeException error) {
        consecutiveFailures++;
        long now = android.os.SystemClock.elapsedRealtime();
        if (consecutiveFailures == 1) {
            firstFailureAt = now;
        }
        if (consecutiveFailures == 1 || consecutiveFailures % 10 == 0) {
            CarDiagnostics.log(getCarContext(), "Draw failure " + error.getClass().getName()
                    + ": " + error.getMessage() + " consecutive=" + consecutiveFailures);
        }
        // A host buffer queue still held by a dead process never recovers; closing the app
        // makes Android Auto build a fresh surface on the next launch instead of a frozen frame.
        if (now - firstFailureAt >= 15000) {
            CarDiagnostics.log(getCarContext(), "Surface unusable for 15 s; finishing car app");
            consecutiveFailures = 0;
            handler.post(() -> getCarContext().finishCarApp());
            return;
        }
        if (consecutiveFailures >= 3 && now - lastRecovery >= 5000) {
            lastRecovery = now;
            handler.post(() -> {
                if (!started) {
                    return;
                }
                CarDiagnostics.log(getCarContext(), "Re-registering surface callback after failures");
                try {
                    AppManager manager = getCarContext().getCarService(AppManager.class);
                    manager.setSurfaceCallback(null);
                    manager.setSurfaceCallback(surfaceCallback);
                } catch (RuntimeException failure) {
                    CarDiagnostics.log(getCarContext(), "Surface recovery failed: " + failure);
                }
            });
        }
    }

    private SurfaceContainer container;
    private Surface surface;
    private Rect visibleArea = new Rect();
    private Rect stableArea = new Rect();
    private Rect lastDrawArea = new Rect();
    private boolean started;
    private boolean drawQueued;
    private long callbackEpoch;
    private boolean lyricsVisible;
    private String lyricsTrackKey = "";
    private Boolean lastNight;
    private String lastMode;
    private int lastDrawWidth;
    private int lastDrawHeight;

    public BoardScreen(@NonNull CarContext carContext) {
        super(carContext);
        prefs = Store.prefs(carContext);
        board = new BoardView(carContext);
        board.setFrameRequestHook(this::requestDraw);
        getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event == Lifecycle.Event.ON_START) {
                startRendering();
            } else if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) {
                stopRendering();
            }
        });
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        return new NavigationTemplate.Builder()
                .setActionStrip(new ActionStrip.Builder()
                        .addAction(new Action.Builder()
                                .setTitle(AppLocale.wrap(getCarContext()).getString(
                                        lyricsVisible ? R.string.board : R.string.lyrics))
                                .setOnClickListener(this::toggleLyrics).build()).build())
                .build();
    }

    private void startRendering() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::startRendering);
            return;
        }
        if (started) {
            return;
        }
        started = true;
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        final long epoch = ++callbackEpoch;
        surfaceCallback = new SurfaceCallback() {
            @Override
            public void onSurfaceAvailable(@NonNull SurfaceContainer next) {
                onAvailable(epoch, next);
            }
            @Override
            public void onVisibleAreaChanged(@NonNull Rect area) {
                onVisible(epoch, new Rect(area));
            }
            @Override
            public void onStableAreaChanged(@NonNull Rect area) {
                onStable(epoch, new Rect(area));
            }
            @Override
            public void onSurfaceDestroyed(@NonNull SurfaceContainer destroyed) {
                onDestroyed(epoch, destroyed);
            }
            @Override
            public void onClick(float x, float y) {
                onSurfaceClick(epoch, x, y);
            }
        };
        try {
            getCarContext().getCarService(AppManager.class).setSurfaceCallback(surfaceCallback);
        } catch (RuntimeException error) {
            CarDiagnostics.log(getCarContext(), "Surface registration failed: " + error);
        }
        if (getCarContext().getCarAppApiLevel() < 5) {
            Log.i(CAR_TAG, "Surface taps need car API 5; use the Lyrics action");
        }
        WeatherRepository.start(getCarContext(), weatherCallback);
        scheduleTick();
    }

    private void stopRendering() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::stopRendering);
            return;
        }
        if (!started) {
            return;
        }
        started = false;
        board.cancelLayoutTransition();
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameScheduled = false;
        drawQueued = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(lyricsTick);
        ++callbackEpoch;
        lyricsTrackKey = "";
        board.setLyrics(null);
        board.setLyricsLoading(false);
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        try {
            getCarContext().getCarService(AppManager.class).setSurfaceCallback(null);
        } catch (RuntimeException error) {
            CarDiagnostics.log(getCarContext(), "Surface unregistration failed: " + error);
        }
        WeatherRepository.stop(weatherCallback);
        releaseSurface();
    }

    private void scheduleTick() {
        handler.removeCallbacks(tick);
        long delay = 1000L - (System.currentTimeMillis() % 1000L);
        handler.postDelayed(tick, delay);
    }

    private void requestDraw() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::requestDraw);
            return;
        }
        refreshNeeded = true;
        if (drawQueued) {
            return;
        }
        drawQueued = true;
        handler.post(() -> {
            drawQueued = false;
            drawBoard();
        });
    }

    private void onAvailable(long epoch, SurfaceContainer nextContainer) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> onAvailable(epoch, nextContainer));
            return;
        }
        if (!started || epoch != callbackEpoch) {
            return;
        }
        // The host owns these surfaces; releasing one it still renders to aborts RenderThread.
        CarDiagnostics.log(getCarContext(), "Surface available " + nextContainer.getWidth()
                + "x" + nextContainer.getHeight() + " dpi=" + nextContainer.getDpi());
        Surface nextSurface = nextContainer.getSurface();
        board.cancelLayoutTransition();
        lastMode = null;
        lastDrawWidth = 0;
        lastDrawHeight = 0;
        transitionLogging = false;
        lastDrawArea.setEmpty();
        visibleArea.setEmpty();
        stableArea.setEmpty();
        container = nextContainer;
        surface = nextSurface;
        fps = 30;
        slowFrames = 0;
        refreshNeeded = true;
        if (nextContainer.getWidth() > 0 && nextContainer.getHeight() > 0) {
            prefs.edit()
                    .putInt("surface_width", nextContainer.getWidth())
                    .putInt("surface_height", nextContainer.getHeight())
                    .apply();
        }
        drawBoard();
        if (lyricsVisible) {
            handler.removeCallbacks(lyricsTick);
            handler.post(lyricsTick);
        }
    }

    private void onVisible(long epoch, Rect area) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> onVisible(epoch, area));
            return;
        }
        if (!started || epoch != callbackEpoch) {
            return;
        }
        CarDiagnostics.log(getCarContext(), "Visible area=" + area);
        visibleArea = new Rect(area);
        drawBoard();
    }

    private void onStable(long epoch, Rect area) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> onStable(epoch, area));
            return;
        }
        if (!started || epoch != callbackEpoch) {
            return;
        }
        CarDiagnostics.log(getCarContext(), "Stable area=" + area);
        stableArea = new Rect(area);
        drawBoard();
    }

    private void onDestroyed(long epoch, SurfaceContainer destroyedContainer) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> onDestroyed(epoch, destroyedContainer));
            return;
        }
        CarDiagnostics.log(getCarContext(), "Surface destroyed");
        Surface destroyed = destroyedContainer.getSurface();
        if (!started || epoch != callbackEpoch || surface == null) {
            return;
        }
        if (destroyedContainer == container || destroyed == surface
                || (destroyed != null && destroyed.equals(surface)) || !surface.isValid()) {
            releaseSurface();
        } else {
            // A replacement may already be active. Dimensions do not identify a surface.
            Log.i(TAG, "Ignoring stale or ambiguous surface destroy");
        }
    }

    private void releaseSurface() {
        handler.removeCallbacks(lyricsTick);
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameScheduled = false;
        board.cancelLayoutTransition();
        lastMode = null;
        lastDrawWidth = 0;
        lastDrawHeight = 0;
        transitionLogging = false;
        surface = null;
        container = null;
        lastDrawArea.setEmpty();
        visibleArea.setEmpty();
        stableArea.setEmpty();
    }

    private void drawBoard() {
        try {
            drawBoardInternal();
        } catch (RuntimeException error) {
            drawFailed(error);
        }
    }

    private void drawBoardInternal() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::drawBoard);
            return;
        }
        if (!started || container == null || surface == null) {
            return;
        }
        if (!surface.isValid()) {
            drawFailed(new IllegalStateException("Host surface invalid"));
            return;
        }

        int width = container.getWidth();
        int height = container.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        Rect bounds = new Rect(0, 0, width, height);
        // Lay out in the stable area: the visible area shrinks and grows as the host shows and
        // hides the action strip, which made every widget jump. Fall back to visible, then full.
        Rect drawArea = new Rect(stableArea);
        if (!drawArea.intersect(bounds) || drawArea.isEmpty()) {
            drawArea.set(visibleArea);
        }
        if (!drawArea.intersect(bounds) || drawArea.isEmpty()) {
            drawArea.set(bounds);
        }
        boolean originChanged = !lastDrawArea.isEmpty()
                && (drawArea.left != lastDrawArea.left || drawArea.top != lastDrawArea.top);
        lastDrawArea.set(drawArea);

        int drawWidth = drawArea.width();
        int drawHeight = drawArea.height();
        String mode = drawWidth / (float) drawHeight < 1.4f ? Store.SPLIT : Store.FULL;
        String prefix = mode + "_";
        if (prefs.getInt(prefix + "width", 0) != drawWidth
                || prefs.getInt(prefix + "height", 0) != drawHeight) {
            prefs.edit().putInt(prefix + "width", drawWidth)
                    .putInt(prefix + "height", drawHeight).apply();
        }
        boolean night = getCarContext().isDarkMode();
        if (lastNight == null || lastNight != night) {
            CarDiagnostics.log(getCarContext(), "Night mode=" + night);
            lastNight = night;
        }
        boolean freshSurface = lastMode == null;
        boolean geometryChanged = !mode.equals(lastMode)
                || drawWidth != lastDrawWidth || drawHeight != lastDrawHeight
                || originChanged;
        if (geometryChanged) {
            if (lastMode != null) {
                board.prepareLayoutTransition();
            }
            CarDiagnostics.log(getCarContext(), "Layout mode=" + mode + " draw=" + drawWidth + "x" + drawHeight);
            lastMode = mode;
            lastDrawWidth = drawWidth;
            lastDrawHeight = drawHeight;
        }
        board.setMode(mode);
        board.setCarNight(night);
        long refreshNow = android.os.SystemClock.elapsedRealtime();
        if (refreshNeeded || refreshNow - lastRefresh >= 1000) {
            refreshNeeded = false;
            lastRefresh = refreshNow;
            board.refresh();
            if (lyricsVisible) {
                updateLyrics();
            }
        }
        int measuredWidth = View.MeasureSpec.makeMeasureSpec(drawArea.width(), View.MeasureSpec.EXACTLY);
        int measuredHeight = View.MeasureSpec.makeMeasureSpec(drawArea.height(), View.MeasureSpec.EXACTLY);
        board.setFrame(width, height, drawArea.left, drawArea.top);
        board.measure(measuredWidth, measuredHeight);
        board.layout(0, 0, drawArea.width(), drawArea.height());
        if (freshSurface) {
            board.cancelLayoutTransition();
        }

        scheduleFrame();
        boolean transitioningFrame = board.isTransitioning();
        if (transitioningFrame && !transitionLogging) {
            transitionLogging = true;
            transitionFrameTotalNanos = 0;
            transitionFrameCount = 0;
            CarDiagnostics.log(getCarContext(), "Board transition start mode=" + mode
                    + " draw=" + drawWidth + "x" + drawHeight);
        }
        long frameStarted = android.os.SystemClock.elapsedRealtimeNanos();
        Surface drawingSurface = surface;
        Canvas canvas = null;
        boolean drawn = false;
        try {
            if (!drawingSurface.isValid()) {
                return;
            }
            // Software canvas, as in the androidx car samples: a hardware renderer aborts the
            // process natively when the host swaps the surface mid-frame.
            canvas = drawingSurface.lockCanvas(null);
            board.drawFrameBackground(canvas, new android.graphics.RectF(0, 0, width, height));
            int save = canvas.save();
            canvas.clipRect(drawArea);
            canvas.translate(drawArea.left, drawArea.top);
            board.draw(canvas);
            canvas.restoreToCount(save);
            drawn = true;
        } catch (RuntimeException e) {
            drawFailed(e);
        } finally {
            if (canvas != null) {
                try {
                    drawingSurface.unlockCanvasAndPost(canvas);
                    if (drawn) {
                        consecutiveFailures = 0;
                        if (transitionLogging) {
                            transitionFrameTotalNanos += android.os.SystemClock.elapsedRealtimeNanos()
                                    - frameStarted;
                            transitionFrameCount++;
                            if (!board.isTransitioning()) {
                                CarDiagnostics.log(getCarContext(), "Board transition end avg frame="
                                        + (transitionFrameTotalNanos / Math.max(1, transitionFrameCount)
                                        / 1_000_000f) + "ms frames=" + transitionFrameCount);
                                transitionLogging = false;
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    drawFailed(e);
                }
            }
        }
    }

    void onCarConfigurationChanged() {
        handler.post(() -> {
            board.invalidate();
            drawBoard();
        });
    }

    private void onSurfaceClick(long epoch, float x, float y) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> onSurfaceClick(epoch, x, y));
            return;
        }
        if (!started || epoch != callbackEpoch || getCarContext().getCarAppApiLevel() < 5
                || lastDrawArea.isEmpty() || !lastDrawArea.contains((int) x, (int) y)) {
            return;
        }
        if (lyricsVisible || board.hitNowPlaying(x - lastDrawArea.left, y - lastDrawArea.top)) {
            toggleLyrics();
        }
    }

    private void toggleLyrics() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::toggleLyrics);
            return;
        }
        lyricsVisible = !lyricsVisible;
        board.setLyricsVisible(lyricsVisible);
        refreshNeeded = true;
        if (lyricsVisible) {
            lyricsTrackKey = "";
            board.setLyrics(null);
            board.setLyricsLoading(false);
            handler.removeCallbacks(lyricsTick);
            handler.post(lyricsTick);
        } else {
            handler.removeCallbacks(lyricsTick);
        }
        invalidate();
        drawBoard();
    }

    private void updateLyrics() {
        WidgetData.MediaSnapshot media = WidgetData.mediaSnapshot(getCarContext());
        String key = media.hasTrack ? LyricsRepository.key(media) : "";
        if (!key.equals(lyricsTrackKey)) {
            lyricsTrackKey = key;
            board.setLyrics(null);
            board.setLyricsLoading(!key.isEmpty());
            if (!key.isEmpty()) {
                LyricsRepository.request(getCarContext(), media, result -> {
                    if (!started || !lyricsVisible || !key.equals(lyricsTrackKey)) {
                        return;
                    }
                    board.setLyrics(result);
                    board.setLyricsLoading(false);
                    drawBoard();
                });
            }
        }
        board.setLyricsPosition(media.estimatedPositionMs());
    }

}
