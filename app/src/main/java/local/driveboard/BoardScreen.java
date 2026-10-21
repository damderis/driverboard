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
public final class BoardScreen extends Screen implements SurfaceCallback {
    private static final String TAG = "BoardScreen";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final BoardView board;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (preferences, key) -> {
                if (!"surface_width".equals(key) && !"surface_height".equals(key)
                        && !"visible_width".equals(key) && !"visible_height".equals(key)) {
                    drawBoard();
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

    private SurfaceContainer container;
    private Surface surface;
    private Rect visibleArea = new Rect();
    private Rect stableArea = new Rect();
    private boolean started;

    public BoardScreen(@NonNull CarContext carContext) {
        super(carContext);
        prefs = Store.prefs(carContext);
        board = new BoardView(carContext);
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
        Action refresh = new Action.Builder()
                .setTitle("Refresh")
                .setOnClickListener(this::drawBoard)
                .build();
        return new NavigationTemplate.Builder()
                .setActionStrip(new ActionStrip.Builder().addAction(refresh).build())
                .build();
    }

    private void startRendering() {
        if (started) {
            return;
        }
        started = true;
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        getCarContext().getCarService(AppManager.class).setSurfaceCallback(this);
        scheduleTick();
    }

    private void stopRendering() {
        if (!started) {
            return;
        }
        started = false;
        handler.removeCallbacks(tick);
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        getCarContext().getCarService(AppManager.class).setSurfaceCallback(null);
        releaseSurface();
    }

    private void scheduleTick() {
        handler.removeCallbacks(tick);
        long delay = 1000L - (System.currentTimeMillis() % 1000L);
        handler.postDelayed(tick, delay);
    }

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer nextContainer) {
        Surface nextSurface = nextContainer.getSurface();
        if (surface != null && surface != nextSurface) {
            surface.release();
        }
        visibleArea.setEmpty();
        stableArea.setEmpty();
        container = nextContainer;
        surface = nextSurface;
        if (nextContainer.getWidth() > 0 && nextContainer.getHeight() > 0) {
            prefs.edit()
                    .putInt("surface_width", nextContainer.getWidth())
                    .putInt("surface_height", nextContainer.getHeight())
                    .apply();
        }
        drawBoard();
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect area) {
        visibleArea = new Rect(area);
        drawBoard();
    }

    @Override
    public void onStableAreaChanged(@NonNull Rect area) {
        stableArea = new Rect(area);
        // Track the stable region for future fixed controls; render only the visible region.
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer destroyedContainer) {
        // Binder may parcel a different Surface wrapper for the destroy callback.
        releaseSurface();
    }

    private void releaseSurface() {
        if (surface != null) {
            surface.release();
        }
        surface = null;
        container = null;
        visibleArea.setEmpty();
        stableArea.setEmpty();
    }

    private void drawBoard() {
        if (!started || container == null || surface == null || !surface.isValid()) {
            return;
        }

        int width = container.getWidth();
        int height = container.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        Rect bounds = new Rect(0, 0, width, height);
        // Wait for the host's visible area; an empty area means there is nothing safe to draw.
        Rect drawArea = new Rect(visibleArea);
        if (!drawArea.intersect(bounds) || drawArea.isEmpty()) {
            return;
        }

        if (prefs.getInt("visible_width", 0) != drawArea.width() || prefs.getInt("visible_height", 0) != drawArea.height()) {
            prefs.edit().putInt("visible_width", drawArea.width()).putInt("visible_height", drawArea.height()).apply();
        }

        board.refresh();
        int measuredWidth = View.MeasureSpec.makeMeasureSpec(drawArea.width(), View.MeasureSpec.EXACTLY);
        int measuredHeight = View.MeasureSpec.makeMeasureSpec(drawArea.height(), View.MeasureSpec.EXACTLY);
        board.measure(measuredWidth, measuredHeight);
        board.layout(0, 0, drawArea.width(), drawArea.height());

        Canvas canvas = null;
        try {
            canvas = surface.lockHardwareCanvas();
            int save = canvas.save();
            canvas.clipRect(drawArea);
            canvas.translate(drawArea.left, drawArea.top);
            board.draw(canvas);
            canvas.restoreToCount(save);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not draw car surface", e);
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Surface detached while posting frame", e);
                }
            }
        }
    }
}
