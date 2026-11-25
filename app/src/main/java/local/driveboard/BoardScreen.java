package local.driveboard;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
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
import androidx.car.app.model.ItemList;
import androidx.car.app.model.GridTemplate;
import androidx.car.app.model.GridItem;
import androidx.car.app.model.CarIcon;

import androidx.car.app.navigation.model.MapController;
import androidx.car.app.navigation.model.MapWithContentTemplate;
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
    private final WeatherRepository.Callback weatherCallback = snapshot -> this.requestDraw();
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (preferences, key) -> {
                if (!"surface_width".equals(key) && !"surface_height".equals(key)
                        && !"visible_width".equals(key) && !"visible_height".equals(key)
                        && !"full_width".equals(key) && !"full_height".equals(key)
                        && !"split_width".equals(key) && !"split_height".equals(key)
                        && !"narrow_width".equals(key) && !"narrow_height".equals(key)) {
                    requestDraw();
                    if (this.started) {
                        this.refreshShortcutGrid();
                    }
                }
            };
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshShortcutGrid();
            drawBoard();
            if (started) {
                scheduleTick();
            }
        }
    };
    private final Runnable lyricsTick = new Runnable() {
        @Override
        public void run() {
            if (!started || browserMode || surface == null || !surface.isValid()) {
                return;
            }
            WidgetData.MediaSnapshot media = WidgetData.mediaSnapshot(getCarContext());
            // Off the lyrics page this keeps song lyrics in step for the music card.
            if (!lyricsVisible && media.isQuranSession) {
                handler.postDelayed(this, 1000);
                return;
            }
            if (media.isQuranSession) {
                String metadataKey = media.title + "\u0000" + media.artist + "\u0000"
                        + (media.controller == null ? "" : media.controller.getSessionToken());
                if (!metadataKey.equals(quranMetadataKey)) {
                    quranMetadataKey = metadataKey;
                    refreshNeeded = true;
                    drawBoard();
                } else if (board.updateQuranPosition(media)) {
                    drawBoard();
                }
                handler.postDelayed(this, 250);
                return;
            }
            String key = media.hasTrack ? LyricsRepository.key(media) : "";
            if (!key.equals(lyricsTrackKey)) {
                refreshNeeded = true;
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

    // Two car apps share this screen: "DriveBoard" (navigation: full screen and the side slot)
    // and "DriveBoard Peta" (weather: the navigation app's map can sit beside it).
    private final boolean weather;

    private boolean usesWeatherTemplate() {
        return weather && getCarContext().getCarAppApiLevel() >= 7;
    }

    /** Whether the board is drawn on the car surface (weather needs car API 7). */
    private boolean drawsSurface() {
        return !weather || usesWeatherTemplate();
    }

    private String shortcutGridKey = "";

    private String shortcutGridKey() {
        return AppLocale.locale(getCarContext()).toLanguageTag() + ":"
                + AudioSwitchClient.read(getCarContext()).installed + ":" + browserMode
                + ":" + lyricsVisible + ":" + (Store.page(getCarContext()) == 1)
                + ":" + BoardColours.current(getCarContext()).primary
                + ":" + prefs.getString("panel_tiles", PanelShortcut.DEFAULT);
    }

    private void refreshShortcutGrid() {
        String key = shortcutGridKey();
        if (!key.equals(shortcutGridKey)) {
            shortcutGridKey = key;
            invalidate();
        }
    }

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
        if (!started || browserMode || surface == null || !surface.isValid()
                || (!board.isMediaPlaying() && !board.isTransitioning()
                        && !board.isQuranTransitioning() && !board.isNavigationTransitioning())) {
            return;
        }
        if (board.isTransitioning() || board.isNavigationTransitioning()
                || nanos - lastFrame >= 1_000_000_000L / fps) {
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
        if (started && !browserMode && surface != null && surface.isValid()
                && (board.isMediaPlaying() || board.isTransitioning()
                        || board.isQuranTransitioning() || board.isNavigationTransitioning()) && !frameScheduled) {
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
                if (!started || browserMode) {
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
    // Only this independent wrapper is ever connected to the Canvas CPU producer.
    private Surface canvasSurface;
    private boolean browserMode;
    private boolean browserClosing;
    private VirtualDisplay browserDisplay;
    private BrowserPresentation browser;
    private ParkedDetector parkedDetector;
    private ParkedState.State browserLock;
    private boolean browserPermission;
    private String browserUrl = "https://www.google.com";
    private Rect visibleArea = new Rect();
    private Rect stableArea = new Rect();
    private Rect lastDrawArea = new Rect();
    private boolean started;
    private boolean drawQueued;
    private long callbackEpoch;
    private boolean lyricsVisible;
    private String lyricsTrackKey = "";
    private String quranTrackKey = "";
    private long quranRetryAt;
    private boolean quranLoading;
    private String quranMetadataKey = "";
    private long quranEpoch;
    private Boolean lastNight;
    private String lastMode;
    private int lastDrawWidth;
    private int lastDrawHeight;

    public BoardScreen(@NonNull CarContext carContext, boolean weather) {
        super(carContext);
        this.weather = weather;
        prefs = Store.prefs(carContext);
        board = new BoardView(carContext);
        board.setBannerFullOnly(weather);
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
        android.content.Context strings = AppLocale.wrap(getCarContext());
        shortcutGridKey = shortcutGridKey();
        int api = getCarContext().getCarAppApiLevel();
        ActionStrip mapStrip = null;
        if (browserMode && api >= 2) {
            mapStrip = new ActionStrip.Builder().addAction(Action.PAN).build();
        } else if (!lyricsVisible && api >= 2 && Store.FULL.equals(lastMode)) {
            // Host-drawn arrows: they work even on head units that never deliver surface taps.
            // Full screen only; split/narrow boards have no page arrows.
            mapStrip = new ActionStrip.Builder()
                    .addAction(pageArrow(R.drawable.ic_page_previous, -1))
                    .addAction(pageArrow(R.drawable.ic_page_next, 1)).build();
        }
        if (!weather) {
            CarDiagnostics.log(getCarContext(), "Template=NavigationTemplate carApi=" + api);
            NavigationTemplate.Builder template = new NavigationTemplate.Builder()
                    .setActionStrip(navigationActions(strings));
            if (mapStrip != null) template.setMapActionStrip(mapStrip);
            return template.build();
        }
        if (usesWeatherTemplate()) {
            MapController.Builder controller = new MapController.Builder();
            if (mapStrip != null) {
                controller.setMapActionStrip(mapStrip);
            }
            CarDiagnostics.log(getCarContext(), "Template=MapWithContentTemplate carApi=" + api + " content=shortcutGrid");
            return new MapWithContentTemplate.Builder()
                    .setMapController(controller.build())
                    .setContentTemplate(shortcutContent(strings)).build();
        }
        CarDiagnostics.log(getCarContext(), "Template=GridTemplate carApi=" + api + " fallback=carApi<7");
        return shortcutContent(strings);
    }

    private GridTemplate shortcutContent(android.content.Context strings) {
        ItemList.Builder items = new ItemList.Builder();
        int tiles = 0;
        if (browserMode || lyricsVisible) {
            // The lyrics page (opened from the music card) needs a way back.
            items.addItem(shortcutTile(strings.getString(R.string.board), R.drawable.ic_music_note,
                    () -> {
                        if (browserMode) closeBrowser();
                        else toggleLyrics();
                    }));
            tiles++;
        }
        // The owner's picks from the phone app ("Shortcut tiles").
        for (PanelShortcut shortcut : PanelShortcut.parse(
                prefs.getString("panel_tiles", PanelShortcut.DEFAULT))) {
            items.addItem(shortcutTile(strings.getString(shortcut.label), shortcut.icon,
                    () -> runPanelShortcut(shortcut)));
            tiles++;
        }
        if (AudioSwitchClient.read(getCarContext()).installed) {
            items.addItem(shortcutTile(strings.getString(R.string.page_controls),
                    R.drawable.ic_shortcut_controls, () -> getScreenManager().push(
                            new ControlsScreen(getCarContext(), this))));
            tiles++;
        }
        shortcutTiles = tiles;
        return new GridTemplate.Builder().setTitle(strings.getString(R.string.app_name))
                .setSingleList(items.build()).build();
    }

    /** The navigation entry's action strip (Android Auto allows four actions). */
    private ActionStrip navigationActions(android.content.Context strings) {
        ActionStrip.Builder actions = new ActionStrip.Builder();
        if (browserMode || lyricsVisible) {
            actions.addAction(new Action.Builder().setTitle(strings.getString(R.string.board))
                    .setOnClickListener(() -> {
                        if (browserMode) closeBrowser();
                        else toggleLyrics();
                    }).build());
            return actions.build();
        }
        boolean media = Store.page(getCarContext()) == 1;
        actions.addAction(new Action.Builder()
                .setTitle(strings.getString(media ? R.string.board : R.string.page_media))
                .setOnClickListener(() -> changePage(media ? -1 : 1 - Store.page(getCarContext())))
                .build());
        actions.addAction(new Action.Builder().setTitle(strings.getString(R.string.lyrics))
                .setOnClickListener(this::toggleLyrics).build());
        actions.addAction(new Action.Builder().setTitle(strings.getString(R.string.web))
                .setOnClickListener(() -> openBrowser("https://www.google.com")).build());
        if (AudioSwitchClient.read(getCarContext()).installed) {
            actions.addAction(new Action.Builder().setTitle(strings.getString(R.string.page_controls))
                    .setOnClickListener(() -> getScreenManager().push(
                            new ControlsScreen(getCarContext(), this))).build());
        }
        return actions.build();
    }

    /** Tiles in the shortcut grid (must match shortcutContent). */
    private int shortcutTiles = 4;

    private int shortcutCount() {
        return shortcutTiles;
    }

    /** Board accent for the panel icons, so Android Auto's panel echoes the cards. */
    private androidx.car.app.model.CarColor accentTint() {
        int accent = BoardColours.current(getCarContext()).primary;
        return androidx.car.app.model.CarColor.createCustom(accent, accent);
    }

    private GridItem shortcutTile(String title, int icon, androidx.car.app.model.OnClickListener click) {
        return new GridItem.Builder().setTitle(title)
                .setImage(new CarIcon.Builder(androidx.core.graphics.drawable.IconCompat
                        .createWithResource(getCarContext(), icon))
                        .setTint(accentTint()).build(), GridItem.IMAGE_TYPE_ICON)
                .setOnClickListener(click).build();
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
        refreshShortcutGrid();
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        scheduleTick();
        if (!drawsSurface()) return;
        board.startNavigation();
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
                if (weather) {
                    CarDiagnostics.log(getCarContext(), "Weather surface onClick x=" + x + " y=" + y);
                }
                onSurfaceClick(epoch, x, y);
            }
            @Override
            public void onScroll(float distanceX, float distanceY) {
                if (weather) {
                    CarDiagnostics.log(getCarContext(), "Weather surface onScroll dx=" + distanceX
                            + " dy=" + distanceY);
                }
                handler.post(() -> {
                    if (started && epoch == callbackEpoch && browser != null) {
                        browser.scroll(distanceX, distanceY);
                    }
                });
            }
            @Override
            public void onFling(float velocityX, float velocityY) {
                if (weather) {
                    CarDiagnostics.log(getCarContext(), "Weather surface onFling vx=" + velocityX
                            + " vy=" + velocityY);
                }
                handler.post(() -> {
                    if (!started || epoch != callbackEpoch) {
                        return;
                    }
                    if (browser != null) {
                        browser.fling(velocityX, velocityY);
                    } else if (!browserMode && !lyricsVisible
                            && Math.abs(velocityX) > 2 * Math.abs(velocityY)) {
                        // Swipe left for the next page, right for the previous one.
                        changePage(velocityX < 0 ? 1 : -1);
                    }
                });
            }
            @Override
            public void onScale(float focusX, float focusY, float scaleFactor) {
                if (weather) {
                    CarDiagnostics.log(getCarContext(), "Weather surface onScale x=" + focusX
                            + " y=" + focusY + " factor=" + scaleFactor);
                }
                handler.post(() -> {
                    if (started && epoch == callbackEpoch && browser != null) {
                        browser.scale(scaleFactor);
                    }
                });
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
        if (pendingShortcut != null) {
            String action = pendingShortcut;
            pendingShortcut = null;
            handler.post(() -> runShortcut(action));
        }
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
        board.stopNavigation();
        closeBrowser();
        board.releaseMediaControls();
        board.cancelLayoutTransition();
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameScheduled = false;
        drawQueued = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(lyricsTick);
        ++callbackEpoch;
        lyricsTrackKey = "";
        quranTrackKey = "";
        quranMetadataKey = "";
        quranLoading = false;
        quranRetryAt = 0;
        quranEpoch++;
        board.setQuran(null, QuranRepository.EN_SAHIH);
        board.setLyrics(null);
        board.setLyricsLoading(false);
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        try {
            if (drawsSurface()) getCarContext().getCarService(AppManager.class).setSurfaceCallback(null);
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
        CarDiagnostics.log(getCarContext(), "Surface available weatherMode=" + weather + " " + nextContainer.getWidth()
                + "x" + nextContainer.getHeight() + " dpi=" + nextContainer.getDpi());
        Surface nextSurface = nextContainer.getSurface();
        releaseCanvasSurface();
        board.cancelLayoutTransition();
        lastMode = null;
        lastDrawWidth = 0;
        lastDrawHeight = 0;
        transitionLogging = false;
        lastDrawArea.setEmpty();
        visibleArea.setEmpty();
        stableArea.setEmpty();
        container = nextContainer;
        board.setSurfaceDpi(nextContainer.getDpi());
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
        if (browserMode) {
            attachBrowserSurface();
        } else {
            drawBoard();
        }
        handler.removeCallbacks(lyricsTick);
        handler.post(lyricsTick);
        if (pendingWeb) {
            pendingWeb = false;
            handler.postDelayed(() -> openBrowser("https://www.google.com"), 300);
        }
    }

    private boolean pendingWeb;

    /** DriveBoard Peta's Web tile opens the browser here, in the navigation entry. */
    public void openWebWhenReady() {
        if (started && surface != null && surface.isValid()) {
            openBrowser("https://www.google.com");
        } else {
            pendingWeb = true;
        }
    }

    /** The Home tile: the phone app's saved home ("lat,lng" or an address), else a "Home" search. */
    private void runPanelShortcut(PanelShortcut shortcut) {
        if (shortcut.place != null) {
            navigatePlace(shortcut.place, "work".equals(shortcut.place) ? "Work" : "Home");
        } else if (shortcut.query != null) {
            navigateTo(shortcut.query);
        } else {
            // The browser runs in the DriveBoard navigation entry, which can go full screen.
            try {
                getCarContext().startCarApp(new android.content.Intent()
                        .setComponent(new android.content.ComponentName(getCarContext(),
                                DriveBoardService.class))
                        .putExtra(DriveBoardService.EXTRA_OPEN, DriveBoardService.OPEN_WEB));
            } catch (RuntimeException error) {
                CarDiagnostics.log(getCarContext(), "Open DriveBoard web failed: " + error);
            }
        }
    }

    /** A saved place from the phone app ("lat,lng" or an address); else a search for its name. */
    private void navigatePlace(String key, String fallback) {
        String place = prefs.getString(key, "").trim();
        if (place.matches("-?\\d+(\\.\\d+)?\\s*,\\s*-?\\d+(\\.\\d+)?")) {
            String point = place.replaceAll("\\s", "");
            navigateUri("geo:" + point + "?q=" + point, key + " " + point);
        } else {
            navigateTo(place.isEmpty() ? fallback : place);
        }
    }

    private void navigateTo(String query) {
        navigateUri("geo:0,0?q=" + android.net.Uri.encode(query), query);
    }

    private void navigateUri(String uri, String query) {
        android.content.Intent intent = new android.content.Intent(CarContext.ACTION_NAVIGATE,
                android.net.Uri.parse(uri));
        String app = prefs.getString("nav_app", "");
        if (!app.isEmpty()) {
            // The phone app's "Navigation app" choice; Android Auto may still route it elsewhere.
            try {
                getCarContext().startCarApp(new android.content.Intent(intent).setPackage(app));
                CarDiagnostics.log(getCarContext(), "Navigate " + query + " via " + app);
                return;
            } catch (RuntimeException error) {
                CarDiagnostics.log(getCarContext(), "Navigate via " + app + " refused: " + error);
            }
        }
        try {
            getCarContext().startCarApp(intent);
        } catch (RuntimeException error) {
            CarDiagnostics.log(getCarContext(), "Navigate " + query + " failed: " + error);
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
        CarDiagnostics.log(getCarContext(), "Visible area=" + area + " weatherMode=" + weather);
        visibleArea = new Rect(area);
        if (browserMode) attachBrowserSurface();
        updateBrowserArea();
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
        CarDiagnostics.log(getCarContext(), "Stable area=" + area + " weatherMode=" + weather);
        stableArea = new Rect(area);
        if (browserMode) attachBrowserSurface();
        updateBrowserArea();
        drawBoard();
    }

    private void onDestroyed(long epoch, SurfaceContainer destroyedContainer) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> onDestroyed(epoch, destroyedContainer));
            return;
        }
        CarDiagnostics.log(getCarContext(), "Surface destroyed weatherMode=" + weather);
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
        if (browserDisplay != null) {
            try {
                browserDisplay.setSurface(null);
            } catch (RuntimeException error) {
                CarDiagnostics.log(getCarContext(), "Browser surface detach failed: " + error);
                closeBrowser();
            }
        }
        releaseCanvasSurface();
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

    private static Surface copySurface(Surface hostSurface) {
        Parcel parcel = Parcel.obtain();
        try {
            hostSurface.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return Surface.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    private void releaseCanvasSurface() {
        if (canvasSurface != null) {
            // Destroy the independent native Surface to disconnect its CPU producer.
            // unlockCanvasAndPost alone does not disconnect it. Never release host surfaces.
            canvasSurface.release();
            canvasSurface = null;
        }
    }

    private void openBrowser(String url) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> openBrowser(url));
            return;
        }
        if (!drawsSurface()) {
            androidx.car.app.CarToast.makeText(getCarContext(),
                    AppLocale.wrap(getCarContext()).getString(R.string.browser_host_unsupported),
                    androidx.car.app.CarToast.LENGTH_SHORT).show();
            return;
        }
        if (!started || browserClosing) {
            return;
        }
        browserUrl = url;
        if (browserMode) {
            if (browser != null) {
                browser.loadUrl(url);
            }
            return;
        }
        // Drawing and mode changes run on main; any current Canvas has already been posted.
        browserMode = true;
        lyricsVisible = false;
        board.setLyricsVisible(false);
        board.cancelLayoutTransition();
        board.releaseMediaControls();
        handler.removeCallbacks(lyricsTick);
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameScheduled = false;
        releaseCanvasSurface();
        CarDiagnostics.log(getCarContext(), "Browser open; Canvas producer disconnected");
        browserLock = null;
        browserPermission = false;
        parkedDetector = new ParkedDetector(getCarContext(), this::onBrowserLock);
        parkedDetector.start();
        attachBrowserSurface();
        refreshShortcutGrid();
    }

    private void attachBrowserSurface() {
        if (!browserMode || browserClosing || container == null || surface == null
                || !surface.isValid() || container.getWidth() <= 0 || container.getHeight() <= 0) {
            return;
        }
        int width = container.getWidth();
        int height = container.getHeight();
        Rect bounds = new Rect(0, 0, width, height);
        Rect available = new Rect(stableArea);
        if (!available.intersect(bounds) || available.isEmpty()) available.set(visibleArea);
        if (!available.intersect(bounds) || available.isEmpty()) return;
        int dpi = Math.max(1, container.getDpi());
        try {
            if (browserDisplay == null) {
                DisplayManager manager = getCarContext().getSystemService(DisplayManager.class);
                if (manager == null) {
                    throw new IllegalStateException("DisplayManager unavailable");
                }
                browserDisplay = manager.createVirtualDisplay("DriveBoardBrowser", width, height,
                        dpi, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
                if (browserDisplay == null) {
                    throw new IllegalStateException("VirtualDisplay unavailable");
                }
                CarDiagnostics.log(getCarContext(), "Browser VirtualDisplay create "
                        + width + "x" + height + " dpi=" + dpi);
                showBrowserPresentation();
            } else {
                browserDisplay.setSurface(surface);
                browserDisplay.resize(width, height, dpi);
                updateBrowserArea();
            }
        } catch (RuntimeException error) {
            CarDiagnostics.log(getCarContext(), "Browser display failed: " + error);
            closeBrowser();
        }
    }

    private void showBrowserPresentation() {
        BrowserPresentation presentation = new BrowserPresentation(getCarContext(),
                browserDisplay.getDisplay(), this::closeBrowser);
        browser = presentation;
        // Presentation cancels itself when display metrics change on some Android versions.
        // Recreate its display-specific resources after that cancellation, keeping Canvas gated.
        presentation.setOnCancelListener(dialog -> handler.post(() -> {
            if (!started || !browserMode || browserClosing || browser != presentation) {
                return;
            }
            try {
                browserUrl = presentation.currentUrl();
                presentation.close();
                browser = null;
                if (browserDisplay != null) {
                    showBrowserPresentation();
                }
            } catch (RuntimeException error) {
                CarDiagnostics.log(getCarContext(), "Browser presentation resize failed: " + error);
                closeBrowser();
            }
        }));
        presentation.show();
        updateBrowserArea();
        presentation.setLockState(browserLock == null ? ParkedState.State.UNKNOWN : browserLock,
                browserPermission);
        presentation.loadUrl(browserUrl);
    }

    private void updateBrowserArea() {
        if (browser == null || container == null) {
            return;
        }
        Rect bounds = new Rect(0, 0, container.getWidth(), container.getHeight());
        Rect area = new Rect(stableArea);
        if (area.isEmpty() || !area.intersect(bounds)) {
            // Before the stable callback, visible is the safest available host inset.
            area.set(visibleArea);
        }
        if (area.isEmpty() || !area.intersect(bounds)) {
            return;
        }
        browser.setStableArea(area, bounds.width(), bounds.height());
    }

    private void onBrowserLock(ParkedState.State state, boolean permission) {
        if (!browserMode) {
            return;
        }
        if (state == ParkedState.State.MOVING && state != browserLock
                && browserUrl.contains("youtube.com")) {
            androidx.car.app.CarToast.makeText(getCarContext(),
                    AppLocale.wrap(getCarContext()).getString(R.string.browser_moving),
                    androidx.car.app.CarToast.LENGTH_SHORT).show();
        }
        if (state != browserLock || permission != browserPermission) {
            CarDiagnostics.log(getCarContext(), "Browser lock=" + state
                    + " precisePermission=" + permission);
            browserLock = state;
            browserPermission = permission;
        }
        if (browser != null) {
            browser.setLockState(state, permission);
        }
    }

    private void closeBrowser() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::closeBrowser);
            return;
        }
        if (!browserMode || browserClosing) {
            return;
        }
        browserClosing = true;
        if (parkedDetector != null) {
            parkedDetector.stop();
            parkedDetector = null;
        }
        try {
            if (browser != null) {
                browser.close();
            }
        } catch (RuntimeException error) {
            CarDiagnostics.log(getCarContext(), "Browser dismissal failed: " + error);
        } finally {
            browser = null;
        }
        boolean disconnected = browserDisplay == null;
        if (browserDisplay != null) {
            try {
                // Presentation is dismissed first; detach and release the display producer
                // before allowing any new CPU Canvas connection to the host buffer queue.
                browserDisplay.setSurface(null);
            } catch (RuntimeException error) {
                CarDiagnostics.log(getCarContext(), "Browser display detach failed: " + error);
            }
            try {
                browserDisplay.release();
                disconnected = true;
                browserDisplay = null;
                CarDiagnostics.log(getCarContext(), "Browser VirtualDisplay release; producer disconnected");
            } catch (RuntimeException error) {
                CarDiagnostics.log(getCarContext(), "Browser display release failed: " + error);
            }
        }
        if (!disconnected) {
            // Fail closed: never reconnect Canvas while a display producer may still exist.
            getCarContext().finishCarApp();
            return;
        }
        browserMode = false;
        browserClosing = false;
        lastMode = null;
        lastDrawArea.setEmpty();
        consecutiveFailures = 0;
        refreshNeeded = true;
        CarDiagnostics.log(getCarContext(), "Browser close; board drawing may resume");
        refreshShortcutGrid();
        if (started) {
            scheduleTick();
            requestDraw();
        }
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
        if (!drawsSurface() || !started || browserMode || browserClosing || container == null || surface == null) {
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
        // hides host controls. Wait for valid host geometry rather than drawing beneath content.
        Rect drawArea = new Rect(stableArea);
        if (!drawArea.intersect(bounds) || drawArea.isEmpty()) {
            drawArea.set(visibleArea);
        }
        if (!drawArea.intersect(bounds) || drawArea.isEmpty()) {
            return;
        }
        // Android Auto reserves a left column for the weather template's panel but its card only
        // fills the top. Draw across the whole width and lay the board around that card.
        if (usesWeatherTemplate() && stableArea.left > width * .25f) {
            // ponytail: the host does not report its panel size. Its grid tiles scale with the
            // panel's width, so the height is estimated from that width: on the DHU a 415px-wide
            // card had an 80px header and 128px per row of two tiles. The e.MAS 5 head unit draws
            // the column 584px wide (DHU 440). The 5% slack keeps the weather card clear of it.
            float margin = height * .0112f;
            float cardWidth = stableArea.left - 2 * margin;
            int rows = (shortcutCount() + 1) / 2;
            float panelBottom = margin + cardWidth * (.193f + rows * .308f) * 1.05f;
            board.setPanelColumn(margin, stableArea.left - margin, margin,
                    panelBottom, stableArea.left);
            drawArea.set(0, 0, width, height);
        } else {
            board.setPanelColumn(0f, 0f, 0f, 0f, 0f);
        }
        boolean originChanged = !lastDrawArea.isEmpty()
                && (drawArea.left != lastDrawArea.left || drawArea.top != lastDrawArea.top);
        lastDrawArea.set(drawArea);

        int drawWidth = drawArea.width();
        int drawHeight = drawArea.height();
        String mode = BoardLayout.modeFor(drawWidth, drawHeight);
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
            boolean arrowsChange = Store.FULL.equals(lastMode) != Store.FULL.equals(mode);
            lastMode = mode;
            if (arrowsChange) {
                invalidate();
            }
            lastDrawWidth = drawWidth;
            lastDrawHeight = drawHeight;
        }
        board.setMode(mode);
        board.setCarNight(night);
        board.setPageIndicator(Store.page(getCarContext()), Store.PAGE_NAMES.length);
        long refreshNow = android.os.SystemClock.elapsedRealtime();
        if (refreshNeeded || refreshNow - lastRefresh >= 1000) {
            refreshNeeded = false;
            lastRefresh = refreshNow;
            board.refresh();
            if (lyricsVisible || !WidgetData.mediaSnapshot(getCarContext()).isQuranSession) {
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
        if (canvasSurface == null) {
            canvasSurface = copySurface(surface);
        }
        Surface drawingSurface = canvasSurface;
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
            refreshShortcutGrid();
            drawBoard();
        });
    }

    private void onSurfaceClick(long epoch, float x, float y) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> onSurfaceClick(epoch, x, y));
            return;
        }
        if (!started || epoch != callbackEpoch || getCarContext().getCarAppApiLevel() < 5) {
            return;
        }
        CarDiagnostics.log(getCarContext(), "Surface click " + Math.round(x) + "," + Math.round(y));
        if (browserMode) {
            if (browser != null) {
                browser.click(x, y);
            }
            return;
        }
        if (lastDrawArea.isEmpty() || !lastDrawArea.contains((int) x, (int) y)) {
            return;
        }
        float localX = x - lastDrawArea.left;
        float localY = y - lastDrawArea.top;
        if (board.hitNavigation(localX, localY)) {
            return;
        }
        if (board.hitMediaControl(localX, localY)) {
            return;
        }
        if (!lyricsVisible) {
            String shortcut = board.hitShortcut(localX, localY);
            if (shortcut != null) {
                runShortcut(shortcut);
                return;
            }
            String audio = board.hitAudio(localX, localY);
            if (audio != null) {
                runAudio(audio);
                return;
            }
        }
        if (lyricsVisible || board.hitNowPlaying(localX, localY)) {
            toggleLyrics();
        }
    }

    private void nextPage() {
        changePage(1);
    }

    private Action pageArrow(int icon, int delta) {
        return new Action.Builder()
                .setIcon(new androidx.car.app.model.CarIcon.Builder(
                        androidx.core.graphics.drawable.IconCompat.createWithResource(
                                getCarContext(), icon)).build())
                .setOnClickListener(() -> changePage(delta)).build();
    }

    private void changePage(int delta) {
        // DriveBoard Peta shows one board beside the map; no pages.
        if (usesWeatherTemplate()) return;
        Store.setPage(getCarContext(), Store.page(getCarContext()) + delta);
        CarDiagnostics.log(getCarContext(), "Page " + Store.page(getCarContext()));
        board.prepareLayoutTransition();
        refreshNeeded = true;
        refreshShortcutGrid();
        drawBoard();
    }

    private String pendingShortcut;

    void runShortcut(String action) {
        if (action.isEmpty()) return;
        if (!started) {
            // Called while Controls is closing; run once this screen restarts.
            pendingShortcut = action;
            return;
        }
        if (action.startsWith("web:")) {
            openBrowser(action.substring(4));
        } else if ("lyrics".equals(action)) {
            toggleLyrics();
        } else {
            launchShortcut(getCarContext(), action);
        }
    }

    /** The app's live media session, readable through DriveBoard's notification access. */
    private static android.media.session.MediaController activeSession(CarContext context, String app) {
        try {
            android.media.session.MediaSessionManager manager =
                    context.getSystemService(android.media.session.MediaSessionManager.class);
            // Quran keeps an idle phone session (reports PLAYING at 0:00, no PLAY action) next
            // to its real player session; only the one that accepts PLAY can start audio.
            android.media.session.MediaController fallback = null;
            for (android.media.session.MediaController session : manager.getActiveSessions(
                    new android.content.ComponentName(context, MediaAccessService.class))) {
                if (!app.equals(session.getPackageName())) continue;
                android.media.session.PlaybackState state = session.getPlaybackState();
                long actions = state == null ? 0 : state.getActions();
                if ((actions & (android.media.session.PlaybackState.ACTION_PLAY
                        | android.media.session.PlaybackState.ACTION_PLAY_PAUSE)) != 0) {
                    return session;
                }
                if (fallback == null) fallback = session;
            }
            return fallback;
        } catch (RuntimeException error) {
            CarDiagnostics.log(context, "Session lookup failed " + error);
        }
        return null;
    }

    /** media: actions; shared with the Controls list. */
    static void launchShortcut(CarContext context, String action) {
        CarDiagnostics.log(context, "Shortcut " + action);
        String app = action.substring(action.indexOf(':') + 1);
        if (QuranMediaPolicy.PACKAGE.equals(app)) {
            QuranPhone.play(context);
            return;
        }
        try {
            // Android Auto refuses to open other apps from a car app, and both Quran and Morphe
            // reject outside controller connections. A PLAY media key reaches either player.
            // ponytail: resumes whatever the app last had queued; picking a surah or playlist
            // needs the app's MediaBrowser, which these apps only open to Android Auto itself.
            android.view.KeyEvent down = new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY);
            android.view.KeyEvent up = new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY);
            android.media.session.MediaController session = activeSession(context, app);
            if (session != null) {
                session.dispatchMediaButtonEvent(down);
                session.dispatchMediaButtonEvent(up);
                CarDiagnostics.log(context, "Shortcut play key to session " + app);
            } else {
                // No live session: wake the app's media-button receiver directly.
                for (android.view.KeyEvent key : new android.view.KeyEvent[] {down, up}) {
                    context.sendBroadcast(new android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON)
                            .setPackage(app).putExtra(android.content.Intent.EXTRA_KEY_EVENT, key));
                }
                CarDiagnostics.log(context, "Shortcut play key broadcast " + app);
            }
        } catch (RuntimeException error) {
            CarDiagnostics.log(context, "Shortcut failed " + error);
            androidx.car.app.CarToast.makeText(context,
                    AppLocale.wrap(context).getString(R.string.shortcut_failed),
                    androidx.car.app.CarToast.LENGTH_SHORT).show();
        }
    }

    private void runAudio(String hit) {
        if (hit.isEmpty()) return;
        applyAudio(getCarContext(), hit);
        refreshNeeded = true;
        drawBoard();
    }

    /** Shared by the board card and the Controls list. */
    static void applyAudio(CarContext context, String hit) {
        boolean wasEnabled = AudioSwitchClient.read(context).enabled;
        String failure = AudioSwitchClient.apply(context, hit);
        CarDiagnostics.log(context, "Audio " + hit + ": " + (failure == null ? "saved" : failure));
        if (failure == null && "toggle".equals(hit) && !wasEnabled) {
            // Policy order matters: the separation rule only applies after the next reconnect.
            androidx.car.app.CarToast.makeText(context,
                    AppLocale.wrap(context).getString(R.string.audio_reconnect),
                    androidx.car.app.CarToast.LENGTH_LONG).show();
        }
    }

    private void toggleLyrics() {
        if (!drawsSurface()) {
            androidx.car.app.CarToast.makeText(getCarContext(),
                    AppLocale.wrap(getCarContext()).getString(R.string.surface_host_unsupported),
                    androidx.car.app.CarToast.LENGTH_SHORT).show();
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::toggleLyrics);
            return;
        }
        if (browserMode) {
            return;
        }
        lyricsVisible = !lyricsVisible;
        quranEpoch++;
        board.setLyricsVisible(lyricsVisible);
        refreshNeeded = true;
        if (lyricsVisible) {
            lyricsTrackKey = "";
            quranTrackKey = "";
            board.setLyrics(null);
            board.setLyricsLoading(false);
            handler.removeCallbacks(lyricsTick);
            handler.post(lyricsTick);
        }
        refreshShortcutGrid();
        drawBoard();
    }

    private void updateLyrics() {
        WidgetData.MediaSnapshot media = WidgetData.mediaSnapshot(getCarContext());
        if (media.isQuranSession) {
            lyricsTrackKey = "";
            board.setLyrics(null);
            if (media.quran == null) {
                quranTrackKey = "";
                quranLoading = false;
                quranRetryAt = 0;
                quranEpoch++;
                board.setQuran(null, QuranRepository.EN_SAHIH);
                return;
            }
            String edition = "ms".equals(AppLocale.locale(getCarContext()).getLanguage())
                    ? QuranRepository.MS_BASMEIH : QuranRepository.EN_SAHIH;
            String key = media.quran.surah.number + ":" + edition;
            if (!key.equals(quranTrackKey)) {
                quranEpoch++;
                quranTrackKey = key;
                quranLoading = false;
                quranRetryAt = 0;
            }
            if (!quranLoading && android.os.SystemClock.elapsedRealtime() >= quranRetryAt) {
                quranLoading = true;
                long epoch = quranEpoch;
                quranRetryAt = Long.MAX_VALUE;
                QuranRepository.request(getCarContext(), media.quran.surah.number, result -> {
                    if (!started || !lyricsVisible || epoch != quranEpoch
                            || !key.equals(quranTrackKey)) {
                        return;
                    }
                    quranLoading = false;
                    quranRetryAt = result == null
                            ? android.os.SystemClock.elapsedRealtime() + 30_000 : Long.MAX_VALUE;
                    board.setQuran(result, edition);
                    drawBoard();
                });
            }
            return;
        }
        quranTrackKey = "";
        quranMetadataKey = "";
        quranEpoch++;
        board.setQuran(null, QuranRepository.EN_SAHIH);
        String key = media.hasTrack ? LyricsRepository.key(media) : "";
        if (!key.equals(lyricsTrackKey)) {
            lyricsTrackKey = key;
            board.setLyrics(null);
            board.setLyricsLoading(!key.isEmpty());
            if (!key.isEmpty()) {
                LyricsRepository.request(getCarContext(), media, result -> {
                    if (!started || !key.equals(lyricsTrackKey)) {
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
