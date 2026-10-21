package local.driveboard;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import java.util.Collections;

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
import androidx.car.app.hardware.CarHardwareManager;
import androidx.car.app.hardware.common.CarValue;
import androidx.car.app.hardware.info.CarInfo;
import androidx.car.app.hardware.info.EnergyLevel;
import androidx.car.app.hardware.info.EnergyProfile;
import androidx.car.app.hardware.common.OnCarDataAvailableListener;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

/** Renders the same board as the phone preview into the host's navigation surface. */
public final class BoardScreen extends Screen {
    private static final String TAG = "BoardScreen";
    private static final String CAR_TAG = "DriveBoardCar";
    private static final String CAR_FUEL = "com.google.android.gms.permission.CAR_FUEL";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final BoardView board;
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
            if (!lyricsVisible) {
                drawBoard();
            }
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
    private CarInfo carInfo;
    private boolean energyListening;
    private Boolean lastNight;
    private String lastMode;
    private int lastDrawWidth;
    private int lastDrawHeight;
    private OnCarDataAvailableListener<EnergyLevel> energyListener;

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
        getCarContext().getCarService(AppManager.class).setSurfaceCallback(new SurfaceCallback() {
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
        });
        if (getCarContext().getCarAppApiLevel() < 5) {
            Log.i(CAR_TAG, "Surface taps need car API 5; use the Lyrics action");
        }
        startCarEnergy();
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
        drawQueued = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(lyricsTick);
        ++callbackEpoch;
        lyricsTrackKey = "";
        board.setLyrics(null);
        board.setLyricsLoading(false);
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        getCarContext().getCarService(AppManager.class).setSurfaceCallback(null);
        stopCarEnergy();
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
        Surface nextSurface = nextContainer.getSurface();
        lastDrawArea.setEmpty();
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
        stableArea = new Rect(area);
        drawBoard();
    }

    private void onDestroyed(long epoch, SurfaceContainer destroyedContainer) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> onDestroyed(epoch, destroyedContainer));
            return;
        }
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
        surface = null;
        container = null;
        lastDrawArea.setEmpty();
        visibleArea.setEmpty();
        stableArea.setEmpty();
    }

    private void drawBoard() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::drawBoard);
            return;
        }
        if (!started || container == null || surface == null || !surface.isValid()) {
            return;
        }

        int width = container.getWidth();
        int height = container.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        Rect bounds = new Rect(0, 0, width, height);
        // Some hosts do not send a visible callback. Use stable, then full surface.
        Rect drawArea = new Rect(visibleArea);
        if (!drawArea.intersect(bounds) || drawArea.isEmpty()) {
            drawArea.set(stableArea);
        }
        if (!drawArea.intersect(bounds) || drawArea.isEmpty()) {
            drawArea.set(bounds);
        }
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
            Log.i(CAR_TAG, "Night mode=" + night);
            lastNight = night;
        }
        if (!mode.equals(lastMode) || drawWidth != lastDrawWidth || drawHeight != lastDrawHeight) {
            Log.i(CAR_TAG, "Layout mode=" + mode + " draw=" + drawWidth + "x" + drawHeight);
            lastMode = mode;
            lastDrawWidth = drawWidth;
            lastDrawHeight = drawHeight;
        }
        board.setMode(mode);
        board.setCarNight(night);
        board.refresh();
        if (lyricsVisible) {
            updateLyrics();
        }
        int measuredWidth = View.MeasureSpec.makeMeasureSpec(drawArea.width(), View.MeasureSpec.EXACTLY);
        int measuredHeight = View.MeasureSpec.makeMeasureSpec(drawArea.height(), View.MeasureSpec.EXACTLY);
        board.measure(measuredWidth, measuredHeight);
        board.layout(0, 0, drawArea.width(), drawArea.height());

        Surface drawingSurface = surface;
        Canvas canvas = null;
        try {
            if (!drawingSurface.isValid()) {
                return;
            }
            // Software canvas, as in the androidx car samples: a hardware renderer aborts the
            // process natively when the host swaps the surface mid-frame.
            canvas = drawingSurface.lockCanvas(null);
            canvas.drawColor(board.surfaceColor());
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
                    drawingSurface.unlockCanvasAndPost(canvas);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Surface detached while posting frame", e);
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

    private void startCarEnergy() {
        try {
            int level = getCarContext().getCarAppApiLevel();
            Log.i(CAR_TAG, "Host car API level=" + level);
            if (level < 3) {
                Log.i(CAR_TAG, "Energy requires car API 3");
                prefs.edit().putBoolean("battery_unsupported", true).apply();
                return;
            }
            carInfo = getCarContext().getCarService(CarHardwareManager.class).getCarInfo();
            final CarInfo requestedInfo = carInfo;
            final long epoch = callbackEpoch;
            carInfo.fetchEnergyProfile(getCarContext().getMainExecutor(),
                    profile -> onEnergyProfile(epoch, requestedInfo, profile));
        } catch (RuntimeException | LinkageError e) {
            Log.w(CAR_TAG, "Energy profile unavailable", e);
            prefs.edit().putBoolean("battery_unsupported", true).apply();
        }
    }

    private void onEnergyProfile(long epoch, CarInfo requestedInfo, EnergyProfile profile) {
        if (!started || epoch != callbackEpoch || carInfo != requestedInfo || profile == null) {
            return;
        }
        Log.i(CAR_TAG, "Energy profile fuel status=" + profile.getFuelTypes().getStatus()
                + " value=" + profile.getFuelTypes().getValue() + " connector status="
                + profile.getEvConnectorTypes().getStatus() + " value="
                + profile.getEvConnectorTypes().getValue());
        boolean electric = success(profile.getFuelTypes())
                && profile.getFuelTypes().getValue().contains(EnergyProfile.FUEL_TYPE_ELECTRIC);
        boolean connector = success(profile.getEvConnectorTypes())
                && !profile.getEvConnectorTypes().getValue().isEmpty();
        if (!electric && !connector) {
            Log.i(CAR_TAG, "EV profile absent; energy listener not started");
            prefs.edit().putBoolean("battery_unsupported", true).apply();
            return;
        }
        // Android Auto grants car-data permissions only at runtime.
        if (getCarContext().checkSelfPermission(CAR_FUEL) != PackageManager.PERMISSION_GRANTED) {
            Log.i(CAR_TAG, "Requesting " + CAR_FUEL);
            getCarContext().requestPermissions(Collections.singletonList(CAR_FUEL),
                    (granted, rejected) -> {
                        Log.i(CAR_TAG, "Fuel permission granted=" + granted.contains(CAR_FUEL));
                        if (granted.contains(CAR_FUEL)) {
                            startEnergyListener(epoch, requestedInfo);
                        }
                    });
            return;
        }
        startEnergyListener(epoch, requestedInfo);
    }

    private void startEnergyListener(long epoch, CarInfo requestedInfo) {
        if (!started || epoch != callbackEpoch || carInfo != requestedInfo) {
            return;
        }
        try {
            energyListener = level -> onEnergyLevel(epoch, level);
            carInfo.addEnergyLevelListener(getCarContext().getMainExecutor(), energyListener);
            energyListening = true;
            prefs.edit().putBoolean("battery_unsupported", false).apply();
            Log.i(CAR_TAG, "EV energy listener started");
        } catch (RuntimeException | LinkageError e) {
            Log.w(CAR_TAG, "Energy listener unavailable", e);
        }
    }

    private static <T> boolean success(CarValue<T> value) {
        return value != null && value.getStatus() == CarValue.STATUS_SUCCESS
                && value.getValue() != null;
    }

    private void onEnergyLevel(long epoch, EnergyLevel level) {
        if (!started || epoch != callbackEpoch || level == null) {
            return;
        }
        CarValue<Float> battery = level.getBatteryPercent();
        CarValue<Float> range = level.getRangeRemainingMeters();
        Log.i(CAR_TAG, "Energy level battery status=" + battery.getStatus() + " value="
                + battery.getValue() + " range status=" + range.getStatus() + " value="
                + range.getValue());
        SharedPreferences.Editor edit = prefs.edit();
        boolean have = false;
        if (success(battery) && Float.isFinite(battery.getValue())
                && battery.getValue() >= 0 && battery.getValue() <= 100) {
            edit.putFloat("battery_pct", battery.getValue());
            have = true;
        } else {
            edit.remove("battery_pct");
        }
        if (success(range) && Float.isFinite(range.getValue())
                && range.getValue() >= 0 && range.getValue() <= 2_000_000) {
            edit.putInt("range_m", Math.round(range.getValue()));
            have = true;
        } else {
            edit.remove("range_m");
        }
        if (have) {
            edit.putLong("battery_at", System.currentTimeMillis())
                    .putBoolean("battery_unsupported", false).apply();
            Log.i(CAR_TAG, "Energy level updated");
            requestDraw();
        } else {
            edit.remove("battery_at").apply();
            Log.i(CAR_TAG, "Energy level has no successful battery or range values");
        }
    }

    private void stopCarEnergy() {
        if (energyListening && carInfo != null && energyListener != null) {
            try {
                carInfo.removeEnergyLevelListener(energyListener);
            } catch (RuntimeException | LinkageError e) {
                Log.w(CAR_TAG, "Could not remove energy listener", e);
            }
        }
        energyListening = false;
        energyListener = null;
        carInfo = null;
    }
}
