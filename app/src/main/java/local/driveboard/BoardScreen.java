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
import androidx.car.app.hardware.CarHardwareManager;
import androidx.car.app.hardware.common.CarValue;
import androidx.car.app.hardware.info.CarInfo;
import androidx.car.app.hardware.info.EnergyLevel;
import androidx.car.app.hardware.info.EnergyProfile;
import androidx.car.app.hardware.common.OnCarDataAvailableListener;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

/** Renders the same board as the phone preview into the host's navigation surface. */
public final class BoardScreen extends Screen implements SurfaceCallback {
    private static final String TAG = "BoardScreen";
    private static final String CAR_TAG = "DriveBoardCar";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final BoardView board;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (preferences, key) -> {
                if (!"surface_width".equals(key) && !"surface_height".equals(key)
                        && !"visible_width".equals(key) && !"visible_height".equals(key)
                        && !"full_width".equals(key) && !"full_height".equals(key)
                        && !"split_width".equals(key) && !"split_height".equals(key)) {
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
    private CarInfo carInfo;
    private boolean energyListening;
    private Boolean lastNight;
    private String lastMode;
    private int lastDrawWidth;
    private int lastDrawHeight;
    private final OnCarDataAvailableListener<EnergyLevel> energyListener = this::onEnergyLevel;

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
                .setTitle(AppLocale.wrap(getCarContext()).getString(R.string.refresh))
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
        startCarEnergy();
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
        stopCarEnergy();
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
        drawBoard();
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
        // Some hosts do not send a visible callback. Use stable, then full surface.
        Rect drawArea = new Rect(visibleArea);
        if (!drawArea.intersect(bounds) || drawArea.isEmpty()) {
            drawArea.set(stableArea);
        }
        if (!drawArea.intersect(bounds) || drawArea.isEmpty()) {
            drawArea.set(bounds);
        }

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
        int measuredWidth = View.MeasureSpec.makeMeasureSpec(drawArea.width(), View.MeasureSpec.EXACTLY);
        int measuredHeight = View.MeasureSpec.makeMeasureSpec(drawArea.height(), View.MeasureSpec.EXACTLY);
        board.measure(measuredWidth, measuredHeight);
        board.layout(0, 0, drawArea.width(), drawArea.height());

        Canvas canvas = null;
        try {
            canvas = surface.lockHardwareCanvas();
            canvas.drawColor(Tone.current(getCarContext()).ground);
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

    void onCarConfigurationChanged() {
        board.invalidate();
        drawBoard();
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
            carInfo.fetchEnergyProfile(getCarContext().getMainExecutor(), this::onEnergyProfile);
        } catch (RuntimeException | LinkageError e) {
            Log.w(CAR_TAG, "Energy profile unavailable", e);
            prefs.edit().putBoolean("battery_unsupported", true).apply();
        }
    }

    private void onEnergyProfile(EnergyProfile profile) {
        if (!started || profile == null) {
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
        try {
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

    private void onEnergyLevel(EnergyLevel level) {
        if (!started || level == null) {
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
            drawBoard();
        } else {
            edit.remove("battery_at").apply();
            Log.i(CAR_TAG, "Energy level has no successful battery or range values");
        }
    }

    private void stopCarEnergy() {
        if (energyListening && carInfo != null) {
            try {
                carInfo.removeEnergyLevelListener(energyListener);
            } catch (RuntimeException | LinkageError e) {
                Log.w(CAR_TAG, "Could not remove energy listener", e);
            }
        }
        energyListening = false;
        carInfo = null;
    }
}
