package local.driveboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextDirectionHeuristics;
import android.view.MotionEvent;
import android.view.Choreographer;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** Shared proportional renderer for the phone preview and car surface. */
public final class BoardView extends View {
    private int CREAM = 0xFFF4EFE4;
    private int SAGE = 0xFFA9C2BD;
    private int BRASS = 0xFFC8A55A;
    private int SOFT = 0xFFCFDCD8;
    private int DIM = 0xFF9DB3AF;
    private static final String DASH = "\u2014";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint weatherIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BoardLayout layout = BoardLayout.defaults();
    private String mode = Store.FULL;
    private WidgetData.PrayerSnapshot prayer;
    private WeatherRepository.Snapshot weather;
    private WidgetData.MediaSnapshot media;
    private float surfaceDpScale;
    private MediaController controlController;
    private PlaybackState controlState;
    private Boolean optimisticPlaying;
    private MediaControlLayout.Control pressedControl;
    private MediaControlLayout drawnControls;
    private RectF drawnControlBounds;
    private final Handler controlHandler = new Handler(
            Looper.getMainLooper());
    private final MediaController.Callback controlCallback =
            new MediaController.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    controlState = state;
                    optimisticPlaying = null;
                    requestControlDraw();
                }

                @Override
                public void onSessionDestroyed() {
                    releaseMediaControls();
                    requestControlDraw();
                }
            };
    private String time = DASH;
    private String date = DASH;
    private Bitmap background;
    private Bitmap glassBackdrop;
    private int frameW;
    private int frameH;
    private float frameX;
    private float frameY;
    private boolean externalBackground;
    private final Map<String, Integer> glassAlphas = new HashMap<>();
    private BoardColours colours;
    private Context words;
    private boolean editing;
    private boolean carNight;
    private boolean resizing;
    private BoardLayout.Card dragging;
    private float downX;
    private float downY;
    private int startCol;
    private int startRow;
    private int startW;
    private int startH;
    private Runnable onEdit;
    private LyricsDocument lyrics;
    private LyricsDocument displayLyrics;
    private boolean lyricsVisible;
    private boolean lyricsLoading;
    private long lyricsPosition;
    private QuranRepository.Document quranDocument;
    private String quranEdition = QuranRepository.EN_SAHIH;
    private QuranMetadata displayedQuran;
    private QuranMetadata outgoingQuran;
    private long quranTransitionAt;
    private Typeface quranTypeface;
    private final Map<String, StaticLayout> quranLayouts = new HashMap<>();

    public boolean isQuranTransitioning() {
        return lyricsVisible && media != null && media.quran != null
                && outgoingQuran != null
                && SystemClock.uptimeMillis() - quranTransitionAt < 320;
    }
    private Bitmap staticLayer;
    private boolean staticDirty = true;
    private long staticDrawnAt;
    private Runnable frameRequestHook;
    private boolean frameScheduled;
    private int slowFrames;
    private boolean reducedFrameRate;
    private long lastRenderedFrameNanos;
    private long frameLogAt;
    private long frameTotalMs;
    private int frameCount;
    private int displayedLyric = Integer.MIN_VALUE;
    private int outgoingLyric = -1;
    private long lyricTransitionAt;
    private LyricBlock currentLyricBlock;
    private LyricBlock outgoingLyricBlock;
    private final Map<String, BoardTransition.Rect> transitionFrom = new HashMap<>();
    private final Map<String, Float> transitionStartAlpha = new HashMap<>();
    private final Map<String, Bitmap> transitionSource = new HashMap<>();
    private final Map<String, Integer> transitionGlassAlphas = new HashMap<>();
    private final Map<String, Integer> lastCardGlassAlphas = new HashMap<>();
    private Path cookieTemplate;
    private Bitmap artworkTile;
    private Bitmap artworkTileSource;
    private long transitionStartedAt;
    private boolean transitionPending;
    private String renderingCardType;

    private static final class LyricBlock {
        final LyricsDocument document;
        final int index;
        final float width;
        final float height;
        final StaticLayout[] lines;
        final float gap;
        final float totalHeight;

        LyricBlock(LyricsDocument document, int index, RectF area,
                StaticLayout[] lines, float gap, float totalHeight) {
            this.document = document;
            this.index = index;
            width = area.width();
            height = area.height();
            this.lines = lines;
            this.gap = gap;
            this.totalHeight = totalHeight;
        }

        boolean matches(LyricsDocument document, int index, RectF area) {
            return this.document == document && this.index == index
                    && width == area.width() && height == area.height();
        }
    }
    private final Choreographer.FrameCallback frameCallback = frameTimeNanos -> {
        frameScheduled = false;
        if (!isAttachedToWindow() || getVisibility() != VISIBLE
                || frameRequestHook != null) {
            return;
        }
        if (isMediaPlaying() && !isTransitioning()) {
            long spacing = reducedFrameRate ? 65_000_000L : 32_000_000L;
            if (lastRenderedFrameNanos > 0
                    && frameTimeNanos - lastRenderedFrameNanos < spacing) {
                scheduleFrame();
                return;
            }
        }
        lastRenderedFrameNanos = frameTimeNanos;
        invalidate();
        scheduleFrame();
    };

    public BoardView(Context c) {
        super(c);
        refresh();
    }

    public void setEditing(boolean value, Runnable callback) {
        boolean changed = editing != value;
        editing = value;
        onEdit = callback;
        if (changed) {
            invalidateStatic();
        }
    }

    public void setCarNight(boolean value) {
        if (carNight == value) {
            return;
        }
        carNight = value;
        invalidateStatic();
    }

    public void setLyricsVisible(boolean value) {
        if (lyricsVisible == value) {
            return;
        }
        cancelLayoutTransition();
        lyricsVisible = value;
        displayedLyric = Integer.MIN_VALUE;
        clearLyricBlocks();
        invalidateStatic();
    }

    public void setLyricsLoading(boolean value) {
        if (lyricsLoading == value) {
            return;
        }
        lyricsLoading = value;
        if (lyricsVisible) {
            invalidateStatic();
        }
    }

    public void setLyrics(LyricsDocument document) {
        if (lyrics == document) {
            return;
        }
        lyrics = document;
        displayLyrics = document == null ? null : document.estimated(media == null ? 0 : media.duration);
        displayedLyric = Integer.MIN_VALUE;
        clearLyricBlocks();
        if (lyricsVisible) {
            invalidateStatic();
        }
    }

    public void setLyricsPosition(long positionMs) {
        lyricsPosition = Math.max(0, positionMs);
        if (lyricsVisible) {
            invalidate();
        }
    }

    public int lyricsLineIndex() {
        return displayLyrics == null ? -1 : displayLyrics.lineIndexAt(currentLyricsPosition());
    }

    public void setQuran(QuranRepository.Document document, String edition) {
        if (quranDocument == document && quranEdition.equals(edition)) {
            return;
        }
        quranDocument = document;
        quranEdition = edition;
        invalidateStatic();
    }

    public boolean isMediaPlaying() {
        return media != null && media.playing;
    }

    /** Car surfaces request their own frames; phone previews use Choreographer. */
    public void setFrameRequestHook(Runnable hook) {
        frameRequestHook = hook;
        if (hook == null) {
            scheduleFrame();
        }
    }

    public void setSurfaceDpi(int dpi) {
        surfaceDpScale = dpi > 0 ? dpi / 160f : getResources().getDisplayMetrics().density;
    }

    public void releaseMediaControls() {
        if (controlController != null) {
            try {
                controlController.unregisterCallback(controlCallback);
            } catch (RuntimeException ignored) {
                // The session may already have disappeared.
            }
        }
        controlController = null;
        controlState = null;
        optimisticPlaying = null;
        pressedControl = null;
        drawnControls = null;
        drawnControlBounds = null;
        controlHandler.removeCallbacksAndMessages(null);
    }

    private void bindMediaControls() {
        MediaController next = media == null ? null : media.controller;
        if (frameRequestHook == null) {
            controlState = media == null ? null : media.playbackState;
            return;
        }
        boolean same = next == null ? controlController == null
                : controlController != null
                && next.getSessionToken().equals(controlController.getSessionToken());
        if (!same) {
            releaseMediaControls();
            controlController = next;
            if (next != null) {
                try {
                    next.registerCallback(controlCallback, controlHandler);
                } catch (RuntimeException ignored) {
                    // Polling snapshots still reconcile the state if the session disappears.
                }
            }
        }
        if (optimisticPlaying == null) {
            controlState = media == null ? null : media.playbackState;
        }
    }

    private void requestControlDraw() {
        invalidateStatic();
        if (frameRequestHook != null) {
            frameRequestHook.run();
        }
    }

    private boolean controlPlaying() {
        return optimisticPlaying != null ? optimisticPlaying
                : controlState != null && controlState.getState()
                == PlaybackState.STATE_PLAYING;
    }

    private static String controlAction(MediaControlLayout.Control control) {
        switch (control) {
            case PREVIOUS:
                return WidgetData.MEDIA_PREVIOUS;
            case NEXT:
                return WidgetData.MEDIA_NEXT;
            default:
                return WidgetData.MEDIA_PLAY_PAUSE;
        }
    }

    /** Only the car calls this; phone gestures continue to edit the layout. */
    public boolean hitMediaControl(float x, float y) {
        MediaControlLayout targets = drawnControls;
        if (drawnControlBounds == null || !drawnControlBounds.contains(x, y)) {
            return false;
        }
        MediaControlLayout.Control control = targets == null ? null : targets.hit(x, y);
        if (control == null) {
            return false;
        }
        pressedControl = control;
        String action = controlAction(control);
        boolean wasPlaying = controlPlaying();
        try {
            PlaybackState current = media == null || media.controller == null
                    ? null : media.controller.getPlaybackState();
            if (current != null) {
                wasPlaying = current.getState() == PlaybackState.STATE_PLAYING;
            }
        } catch (RuntimeException ignored) {
            // Dispatch below reports a disappeared session.
        }
        String result = WidgetData.performMediaAction(media, action);
        if (result == null && control == MediaControlLayout.Control.PLAY_PAUSE) {
            optimisticPlaying = !wasPlaying;
            controlHandler.removeCallbacks(reconcileControlState);
            controlHandler.postDelayed(reconcileControlState, 2000);
        }
        CarDiagnostics.log(getContext(), "Media control " + action + ": "
                + (result == null ? "dispatched" : result));
        requestControlDraw();
        controlHandler.removeCallbacks(clearControlPress);
        controlHandler.postDelayed(clearControlPress, 150);
        return true;
    }

    private final Runnable clearControlPress = () -> {
        pressedControl = null;
        requestControlDraw();
    };

    private final Runnable reconcileControlState = () -> {
        optimisticPlaying = null;
        try {
            controlState = controlController == null ? null : controlController.getPlaybackState();
        } catch (RuntimeException ignored) {
            controlState = null;
        }
        requestControlDraw();
    };

    private float controlDpScale() {
        return surfaceDpScale > 0 ? surfaceDpScale : getResources().getDisplayMetrics().density;
    }

    private RectF mediaContent(RectF bounds) {
        float pad = Math.max(2f * unit(), Math.min(bounds.width(), bounds.height()) * .08f);
        return new RectF(bounds.left + pad, bounds.top + pad,
                bounds.right - pad, bounds.bottom - pad);
    }

    private MediaControlLayout mediaControlLayout(RectF r) {
        float artSize = Math.min(r.height() * .95f, r.width() * .45f);
        return MediaControlLayout.create(r.left + artSize + 10f * unit(), r.top,
                r.right, r.bottom, controlDpScale());
    }

    private MediaControlLayout lyricsControlLayout() {
        float inset = Math.max(12f * unit(), getWidth() * .035f);
        float height = getHeight() - 2f * inset;
        float width = Math.min(height * .50f, (getWidth() - 2f * inset) * .31f);
        return MediaControlLayout.create(inset, inset, inset + width,
                getHeight() - inset, controlDpScale());
    }

    private MediaControlLayout currentControlLayout() {
        if (lyricsVisible) {
            return lyricsControlLayout();
        }
        BoardLayout.Card card = findCard("media");
        return card == null ? null : mediaControlLayout(mediaContent(visualBounds(card)));
    }

    private void drawMediaControls(Canvas canvas) {
        MediaControlLayout targets = currentControlLayout();
        drawnControls = targets;
        drawnControlBounds = lyricsVisible ? new RectF(0, 0, getWidth(), getHeight())
                : findCard("media") == null ? null : visualBounds(findCard("media"));
        if (targets == null) {
            return;
        }
        int save = canvas.save();
        if (!lyricsVisible) {
            BoardLayout.Card card = findCard("media");
            RectF bounds = visualBounds(card);
            float alpha = transitionAlpha(card.type, SystemClock.uptimeMillis());
            canvas.saveLayerAlpha(bounds, Math.round(alpha * 255f));
            canvas.clipPath(cardPath(card.type, bounds));
        }
        for (MediaControlLayout.Control control : MediaControlLayout.Control.values()) {
            MediaControlLayout.Target target = targets.target(control);
            if (target == null) {
                continue;
            }
            float cx = (target.left + target.right) / 2f;
            float cy = (target.top + target.bottom) / 2f;
            float radius = (target.right - target.left) * .43f;
            boolean enabled = WidgetData.supportsMediaAction(controlState, controlAction(control));
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(colours.primary);
            paint.setAlpha(enabled ? 255 : 80);
            if (control == MediaControlLayout.Control.PLAY_PAUSE) {
                canvas.drawCircle(cx, cy, radius, paint);
            }
            if (control == pressedControl) {
                paint.setColor((colours.onSurface & 0x00FFFFFF) | 0x1F000000);
                canvas.drawCircle(cx, cy, radius, paint);
            }
            paint.setColor(control == MediaControlLayout.Control.PLAY_PAUSE
                    ? colours.onPrimary : colours.onSurface);
            paint.setAlpha(enabled ? 255 : 80);
            float g = radius * .47f;
            Path glyph = new Path();
            if (control == MediaControlLayout.Control.PLAY_PAUSE && controlPlaying()) {
                glyph.addRect(cx - g, cy - g, cx - g * .3f, cy + g, Path.Direction.CW);
                glyph.addRect(cx + g * .3f, cy - g, cx + g, cy + g, Path.Direction.CW);
            } else {
                boolean previous = control == MediaControlLayout.Control.PREVIOUS;
                float direction = previous ? -1f : 1f;
                glyph.moveTo(cx - direction * g * .65f, cy - g);
                glyph.lineTo(cx + direction * g, cy);
                glyph.lineTo(cx - direction * g * .65f, cy + g);
                glyph.close();
                if (control != MediaControlLayout.Control.PLAY_PAUSE) {
                    float bar = cx + direction * g * 1.2f;
                    glyph.addRect(bar - g * .16f, cy - g, bar + g * .16f,
                            cy + g, Path.Direction.CW);
                }
            }
            canvas.drawPath(glyph, paint);
        }
        paint.setAlpha(255);
        canvas.restoreToCount(save);
    }

    private long currentLyricsPosition() {
        return isMediaPlaying() ? media.estimatedPositionMs() : lyricsPosition;
    }

    private void invalidateStatic() {
        staticDirty = true;
        invalidate();
    }

    void onGlassBackdropReady() {
        glassAlphas.clear();
        invalidateStatic();
        if (frameRequestHook != null) {
            frameRequestHook.run();
        }
    }

    private void clearLyricBlocks() {
        currentLyricBlock = null;
        outgoingLyricBlock = null;
    }

    private void scheduleFrame() {
        if (frameScheduled || frameRequestHook != null || !isAttachedToWindow()
                || getVisibility() != VISIBLE) {
            return;
        }
        frameScheduled = true;
        if (isMediaPlaying() || isTransitioning() || isQuranTransitioning()) {
            Choreographer.getInstance().postFrameCallback(frameCallback);
        } else {
            Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 1000);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scheduleFrame();
    }

    @Override
    protected void onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameScheduled = false;
        lastRenderedFrameNanos = 0;
        slowFrames = 0;
        reducedFrameRate = false;
        cancelLayoutTransition();
        if (staticLayer != null) {
            staticLayer.recycle();
            staticLayer = null;
        }
        if (artworkTile != null) {
            artworkTile.recycle();
            artworkTile = null;
            artworkTileSource = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            scheduleFrame();
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (oldWidth > 0 && oldHeight > 0 && !transitionPending
                && (width != oldWidth || height != oldHeight)) {
            prepareLayoutTransition(oldWidth, oldHeight);
        }
        clearLyricBlocks();
        invalidateStatic();
    }

    public int surfaceColor() {
        return colours == null ? BoardColours.current(getContext()).surface : colours.surface;
    }

    public boolean hitNowPlaying(float x, float y) {
        if (lyricsVisible) {
            return true;
        }
        for (BoardLayout.Card card : layout.cards) {
            if ("media".equals(card.type) && visualBounds(card).contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    public void setMode(String value) {
        String next = Store.SPLIT.equals(value) ? Store.SPLIT : Store.FULL;
        if (!next.equals(mode)) {
            if (!transitionPending) {
                prepareLayoutTransition(getWidth(), getHeight());
            }
            mode = next;
            dragging = null;
            refresh();
        }
    }

    public void refresh() {
        if (dragging == null) {
            layout = Store.layout(getContext(), mode);
        }
        words = AppLocale.wrap(getContext());
        Date now = new Date();
        time = android.text.format.DateFormat.getTimeFormat(words).format(now);
        date = new SimpleDateFormat("EEE d MMM", AppLocale.locale(getContext())).format(now);
        prayer = WidgetData.prayerSnapshot(words);
        weather = WeatherRepository.snapshot(words);
        media = WidgetData.mediaSnapshot(words);
        QuranMetadata nextQuran = media.quran;
        if (nextQuran == null || displayedQuran == null
                || nextQuran.surah.number != displayedQuran.surah.number
                || nextQuran.ayah != displayedQuran.ayah) {
            outgoingQuran = nextQuran != null && displayedQuran != null
                    && nextQuran.surah.number == displayedQuran.surah.number
                    ? displayedQuran : null;
            displayedQuran = nextQuran;
            quranTransitionAt = SystemClock.uptimeMillis();
        }
        if (lyrics != null && !lyrics.isTimed()) {
            long oldDuration = displayLyrics == null || displayLyrics.lines.length == 0 ? 0
                    : displayLyrics.lines[displayLyrics.lines.length - 1].timeMs;
            displayLyrics = lyrics.estimated(media == null ? 0 : media.duration);
            if (oldDuration != (displayLyrics.lines.length == 0 ? 0
                    : displayLyrics.lines[displayLyrics.lines.length - 1].timeMs)) {
                displayedLyric = Integer.MIN_VALUE;
            }
        }
        bindMediaControls();
        background = BackgroundStore.load(getContext());
        colours = BoardColours.current(getContext(), media == null ? null : media.art);
        clearLyricBlocks();
        invalidateStatic();
        scheduleFrame();
    }

    private float unit() {
        return Math.max(.25f, Math.min(getWidth() / 390f, getHeight() / 150f));
    }

    private float edge() {
        return Math.max(2f, 8f * unit());
    }

    private RectF bounds(BoardLayout.Card c) {
        float e = edge();
        float cw = (getWidth() - 2 * e) / 12f;
        float ch = (getHeight() - 2 * e) / 6f;
        float gap = getWidth() * .015f;
        return new RectF(e + c.col * cw + gap / 2f,
                e + c.row * ch + gap / 2f,
                e + (c.col + c.w) * cw - gap / 2f,
                e + (c.row + c.h) * ch - gap / 2f);
    }

    public boolean isTransitioning() {
        return transitionPending || transitionStartedAt != 0;
    }

    /** Capture the currently displayed positions before a mode or drawing-area change. */
    public void prepareLayoutTransition() {
        prepareLayoutTransition(getWidth(), getHeight());
    }

    private void prepareLayoutTransition(int oldWidth, int oldHeight) {
        if (oldWidth <= 0 || oldHeight <= 0 || lyricsVisible || editing) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        Map<String, BoardTransition.Rect> positions = new HashMap<>();
        Map<String, Bitmap> sources = new HashMap<>();
        Map<String, Float> alphas = new HashMap<>();
        float previousElapsed = transitionStartedAt == 0 ? 0f
                : Math.min(1f, (now - transitionStartedAt)
                / (float) BoardTransition.DURATION_MS);
        float previousProgress = transitionPending ? 0f
                : BoardTransition.progress(now - transitionStartedAt);
        for (BoardLayout.Card card : layout.cards) {
            RectF visible = captureBounds(card, oldWidth, oldHeight, now);
            positions.put(card.type, new BoardTransition.Rect(visible.left + frameX,
                    visible.top + frameY, visible.right + frameX, visible.bottom + frameY));
            Float previousAlpha = transitionStartAlpha.get(card.type);
            alphas.put(card.type, !isTransitioning() ? 1f
                    : previousAlpha == null ? BoardTransition.incomingAlpha(previousProgress)
                    : previousAlpha + (1f - previousAlpha) * previousProgress);
            Bitmap bitmap = null;
            if (isTransitioning()) {
                bitmap = renderCardBitmap(card, visible);
            }
            if (bitmap == null) {
                bitmap = transitionSource.get(card.type);
            }
            if (bitmap == null && staticLayer != null && !staticLayer.isRecycled()) {
                RectF old = bounds(card, oldWidth, oldHeight);
                int left = Math.max(0, Math.min(staticLayer.getWidth() - 1, (int) old.left));
                int top = Math.max(0, Math.min(staticLayer.getHeight() - 1, (int) old.top));
                int right = Math.min(staticLayer.getWidth(), Math.max(left + 1, (int) Math.ceil(old.right)));
                int bottom = Math.min(staticLayer.getHeight(), Math.max(top + 1, (int) Math.ceil(old.bottom)));
                bitmap = Bitmap.createBitmap(staticLayer, left, top, right - left, bottom - top);
            }
            if (bitmap != null) {
                sources.put(card.type, bitmap);
            }
        }
        for (Map.Entry<String, BoardTransition.Rect> entry : transitionFrom.entrySet()) {
            if (positions.containsKey(entry.getKey())) {
                continue;
            }
            Bitmap bitmap = transitionSource.get(entry.getKey());
            if (bitmap != null && !bitmap.isRecycled()) {
                positions.put(entry.getKey(), entry.getValue());
                sources.put(entry.getKey(), bitmap);
                Float previousAlpha = transitionStartAlpha.get(entry.getKey());
                alphas.put(entry.getKey(), (previousAlpha == null ? 1f : previousAlpha)
                        * BoardTransition.outgoingAlpha(previousElapsed));
            }
        }
        transitionFrom.clear();
        transitionFrom.putAll(positions);
        transitionStartAlpha.clear();
        transitionStartAlpha.putAll(alphas);
        transitionSource.clear();
        transitionSource.putAll(sources);
        transitionGlassAlphas.putAll(lastCardGlassAlphas);
        transitionStartedAt = 0;
        transitionPending = !transitionFrom.isEmpty();
        invalidate();
        if (frameScheduled && frameRequestHook == null) {
            Choreographer.getInstance().removeFrameCallback(frameCallback);
            frameScheduled = false;
        }
        scheduleFrame();
        if (frameRequestHook != null) {
            frameRequestHook.run();
        }
    }

    public void cancelLayoutTransition() {
        transitionPending = false;
        transitionStartedAt = 0;
        transitionFrom.clear();
        transitionStartAlpha.clear();
        transitionSource.clear();
        transitionGlassAlphas.clear();
        glassAlphas.clear();
        invalidateStatic();
    }

    private RectF bounds(BoardLayout.Card card, int width, int height) {
        float unit = Math.max(.25f, Math.min(width / 390f, height / 150f));
        float edge = Math.max(2f, 8f * unit);
        float cellWidth = (width - 2 * edge) / 12f;
        float cellHeight = (height - 2 * edge) / 6f;
        float gap = width * .015f;
        return new RectF(edge + card.col * cellWidth + gap / 2f,
                edge + card.row * cellHeight + gap / 2f,
                edge + (card.col + card.w) * cellWidth - gap / 2f,
                edge + (card.row + card.h) * cellHeight - gap / 2f);
    }

    private RectF visualBounds(BoardLayout.Card card) {
        return visualBounds(card, SystemClock.uptimeMillis());
    }

    private RectF captureBounds(BoardLayout.Card card, int oldWidth, int oldHeight, long now) {
        RectF target = bounds(card, oldWidth, oldHeight);
        if (!isTransitioning()) {
            return target;
        }
        float progress = transitionPending ? 0f
                : BoardTransition.progress(now - transitionStartedAt);
        BoardTransition.Rect from = transitionFrom.get(card.type);
        if (from == null) {
            BoardTransition.Rect scaled = new BoardTransition.Rect(target.left, target.top,
                    target.right, target.bottom).scale(.92f + .08f * progress);
            return new RectF(scaled.left, scaled.top, scaled.right, scaled.bottom);
        }
        BoardTransition.Rect destination = new BoardTransition.Rect(target.left + frameX,
                target.top + frameY, target.right + frameX, target.bottom + frameY);
        BoardTransition.Rect current = BoardTransition.Rect.interpolate(from, destination, progress)
                .offset(-frameX, -frameY);
        return new RectF(current.left, current.top, current.right, current.bottom);
    }

    private RectF visualBounds(BoardLayout.Card card, long now) {
        RectF target = bounds(card);
        BoardTransition.Rect from = transitionFrom.get(card.type);
        if (!isTransitioning()) {
            return target;
        }
        float progress = transitionPending ? 0f
                : BoardTransition.progress(now - transitionStartedAt);
        if (from == null) {
            BoardTransition.Rect scaled = new BoardTransition.Rect(target.left, target.top,
                    target.right, target.bottom).scale(.92f + .08f * progress);
            return new RectF(scaled.left, scaled.top, scaled.right, scaled.bottom);
        }
        BoardTransition.Rect destination = new BoardTransition.Rect(target.left + frameX,
                target.top + frameY, target.right + frameX, target.bottom + frameY);
        BoardTransition.Rect current = BoardTransition.Rect.interpolate(from, destination, progress)
                .offset(-frameX, -frameY);
        return new RectF(current.left, current.top, current.right, current.bottom);
    }

    private float transitionAlpha(String type, long now) {
        if (!isTransitioning()) {
            return 1f;
        }
        float progress = transitionPending ? 0f
                : BoardTransition.progress(now - transitionStartedAt);
        Float initial = transitionStartAlpha.get(type);
        return initial == null ? BoardTransition.incomingAlpha(progress)
                : initial + (1f - initial) * progress;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long drawStarted = SystemClock.uptimeMillis();
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (isTransitioning()) {
            drawTransition(canvas, now);
            drawMediaControls(canvas);
            recordDrawTime(drawStarted);
            return;
        }
        if (staticLayer == null || staticLayer.isRecycled()
                || staticLayer.getWidth() != width || staticLayer.getHeight() != height) {
            if (staticLayer != null && !staticLayer.isRecycled()) {
                staticLayer.recycle();
            }
            staticLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            staticDirty = true;
        }
        if (staticDirty || now - staticDrawnAt >= 1000) {
            long staticStarted = SystemClock.uptimeMillis();
            staticLayer.eraseColor(0);
            drawStatic(new Canvas(staticLayer));
            staticDrawnAt = now;
            staticDirty = false;
            CarDiagnostics.log(getContext(), "Board static build "
                    + (SystemClock.uptimeMillis() - staticStarted) + "ms "
                    + width + "x" + height);
        }
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawBitmap(staticLayer, 0, 0, paint);
        if (lyricsVisible) {
            drawMovingLyrics(canvas);
        } else {
            drawProgress(canvas);
        }
        drawMediaControls(canvas);
        recordDrawTime(drawStarted);
    }

    private void recordDrawTime(long drawStarted) {
        long drawMs = SystemClock.uptimeMillis() - drawStarted;
        slowFrames = drawMs > 33 ? slowFrames + 1 : 0;
        if (slowFrames >= 3) {
            reducedFrameRate = true;
        }
        frameTotalMs += drawMs;
        frameCount++;
        long loggedAt = SystemClock.uptimeMillis();
        if (frameLogAt == 0) {
            frameLogAt = loggedAt;
        } else if (loggedAt - frameLogAt >= 30_000) {
            CarDiagnostics.log(getContext(), "Board preview avg draw "
                    + (frameTotalMs / Math.max(1, frameCount)) + "ms / "
                    + frameCount + " frames");
            frameLogAt = loggedAt;
            frameTotalMs = 0;
            frameCount = 0;
        }
    }

    private void drawStatic(Canvas canvas) {
        if (colours == null) {
            colours = BoardColours.current(getContext(), media == null ? null : media.art);
        }
        CREAM = colours.onSurface;
        SAGE = colours.onSurfaceVariant;
        BRASS = colours.primary;
        SOFT = colours.onSurface;
        DIM = colours.onSurfaceVariant;
        glassBackdrop = GlassBackdrop.get(background, frameWidth(), frameHeight(), this);
        if (!externalBackground) {
            drawFrameBackground(canvas, frame());
        }
        if (lyricsVisible) {
            drawLyrics(canvas, true);
            return;
        }
        if (editing) {
            drawGrid(canvas);
        }
        for (BoardLayout.Card c : layout.cards) {
            RectF r = bounds(c);
            if (r.width() <= 0 || r.height() <= 0) {
                continue;
            }
            drawCard(canvas, c, r);
        }
    }

    private void drawCard(Canvas canvas, BoardLayout.Card c, RectF r) {
        canvas.save();
        renderingCardType = c.type;
        drawCardSurface(canvas, c.type, r);
        canvas.clipPath(cardPath(c.type, r));
        float pad = Math.max(4.5f * unit(),
                Math.min(18f * unit(), Math.min(r.width(), r.height()) * .15f));
        if ("media".equals(c.type)) {
            pad = Math.max(2f * unit(), Math.min(r.width(), r.height()) * .08f);
        } else if ("prayer".equals(c.type)) {
            pad = Math.max(7f * unit(),
                    Math.min(22f * unit(), Math.min(r.width(), r.height()) * .18f));
        }
        RectF content = new RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad);
        if ("clock".equals(c.type)) {
            drawClock(canvas, content);
        } else if ("prayer".equals(c.type)) {
            drawPrayer(canvas, content, c.h);
        } else if ("media".equals(c.type)) {
            drawMedia(canvas, content);
        } else if ("weather".equals(c.type)) {
            float aspect = r.width() / r.height();
            if (aspect > .75f && aspect < 1.4f) {
                // Keep text inside the circle's inscribed square, with padding.
                float half = Math.min(r.width(), r.height()) / (float) Math.sqrt(8);
                half *= .90f;
                content = new RectF(r.centerX() - half, r.centerY() - half,
                        r.centerX() + half, r.centerY() + half);
            }
            drawWeather(canvas, content);
        }
        if (editing) {
            drawHandle(canvas, r);
        }
        canvas.restore();
        renderingCardType = null;
    }

    private void drawTransition(Canvas canvas, long now) {
        if (transitionPending) {
            transitionStartedAt = now;
            transitionPending = false;
        }
        float progress = BoardTransition.progress(now - transitionStartedAt);
        if (progress >= 1f) {
            transitionStartedAt = 0;
            transitionFrom.clear();
            transitionStartAlpha.clear();
            transitionSource.clear();
            transitionGlassAlphas.clear();
            glassAlphas.clear();
            staticDirty = true;
            drawStatic(canvas);
            drawProgress(canvas);
            return;
        }
        if (colours == null) {
            colours = BoardColours.current(getContext(), media == null ? null : media.art);
        }
        CREAM = colours.onSurface;
        SAGE = colours.onSurfaceVariant;
        BRASS = colours.primary;
        SOFT = colours.onSurface;
        DIM = colours.onSurfaceVariant;
        glassBackdrop = GlassBackdrop.get(background, frameWidth(), frameHeight(), this);
        if (!externalBackground) {
            drawFrameBackground(canvas, frame());
        }
        for (Map.Entry<String, BoardTransition.Rect> entry : transitionFrom.entrySet()) {
            if (findCard(entry.getKey()) != null) {
                continue;
            }
            Bitmap bitmap = transitionSource.get(entry.getKey());
            if (bitmap != null && !bitmap.isRecycled()) {
                BoardTransition.Rect absolute = entry.getValue();
                RectF rect = new RectF(absolute.left - frameX, absolute.top - frameY,
                        absolute.right - frameX, absolute.bottom - frameY);
                drawTransitionBitmap(canvas, entry.getKey(), bitmap, rect,
                        transitionStartAlpha.get(entry.getKey())
                                * BoardTransition.outgoingAlpha(Math.min(1f,
                                (now - transitionStartedAt) / (float) BoardTransition.DURATION_MS)));
            }
        }
        for (BoardLayout.Card card : layout.cards) {
            RectF current = visualBounds(card, now);
            float alpha = transitionAlpha(card.type, now);
            if (alpha > 0f && current.width() > 0f && current.height() > 0f) {
                int save = canvas.save();
                if (alpha < 1f) {
                    canvas.saveLayerAlpha(current, Math.round(alpha * 255f));
                }
                drawCard(canvas, card, current);
                if (alpha < 1f) {
                    canvas.restore();
                }
                canvas.restoreToCount(save);
            }
        }
        drawProgress(canvas);
    }

    private BoardLayout.Card findCard(String type) {
        for (BoardLayout.Card card : layout.cards) {
            if (type.equals(card.type)) {
                return card;
            }
        }
        return null;
    }

    private Bitmap renderCardBitmap(BoardLayout.Card card, RectF rect) {
        int width = Math.max(1, (int) Math.ceil(rect.width()));
        int height = Math.max(1, (int) Math.ceil(rect.height()));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(-rect.left, -rect.top);
        drawCard(canvas, card, rect);
        return bitmap;
    }

    private void drawTransitionBitmap(Canvas canvas, String type, Bitmap bitmap,
            RectF rect, float alpha) {
        if (alpha <= 0f || rect.width() <= 0f || rect.height() <= 0f) {
            return;
        }
        int save = canvas.save();
        canvas.clipPath(cardPath(type, rect));
        paint.setShader(null);
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.Style.FILL);
        paint.setFilterBitmap(true);
        paint.setAlpha(Math.max(0, Math.min(255, Math.round(alpha * 255f))));
        canvas.drawBitmap(bitmap, null, rect, paint);
        paint.setAlpha(255);
        canvas.restoreToCount(save);
    }

    /**
     * The car surface is larger than the board (host chrome sits in the margins), so the
     * screen draws the background across the whole surface and tells the board where it sits.
     */
    public void setFrame(int width, int height, float left, float top) {
        if (width != frameW || height != frameH || left != frameX || top != frameY
                || !externalBackground) {
            frameW = width;
            frameH = height;
            frameX = left;
            frameY = top;
            externalBackground = true;
            staticDirty = true;
        }
    }

    /** Surface colour, background picture and scrim over {@code full} (frame coordinates). */
    public void drawFrameBackground(Canvas canvas, RectF full) {
        paint.setShader(null);
        paint.setColor(colours.surface);
        paint.setStyle(Paint.Style.FILL);
        paint.setFilterBitmap(true);
        canvas.drawRect(full, paint);
        if (background != null && !background.isRecycled()) {
            drawCenterCrop(canvas, background, full);
            paint.setColor(colours.surface);
            paint.setAlpha(backgroundScrimAlpha());
            canvas.drawRect(full, paint);
            paint.setAlpha(255);
        }
    }

    private int backgroundScrimAlpha() {
        return carNight ? 140 : 97;
    }

    private int frameWidth() {
        return frameW > 0 ? frameW : getWidth();
    }

    private int frameHeight() {
        return frameH > 0 ? frameH : getHeight();
    }

    /** The whole frame in this view's coordinates. */
    private RectF frame() {
        return new RectF(-frameX, -frameY, frameWidth() - frameX, frameHeight() - frameY);
    }

    private void drawCenterCrop(Canvas canvas, Bitmap bitmap, RectF destination) {
        float scale = Math.max(destination.width() / bitmap.getWidth(),
                destination.height() / bitmap.getHeight());
        float sourceWidth = destination.width() / scale;
        float sourceHeight = destination.height() / scale;
        RectF source = new RectF((bitmap.getWidth() - sourceWidth) / 2f,
                (bitmap.getHeight() - sourceHeight) / 2f,
                (bitmap.getWidth() + sourceWidth) / 2f,
                (bitmap.getHeight() + sourceHeight) / 2f);
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(255);
        canvas.drawBitmap(bitmap, new android.graphics.Rect(
                Math.round(source.left), Math.round(source.top),
                Math.round(source.right), Math.round(source.bottom)), destination, paint);
    }

    private Path cardPath(String type, RectF bounds) {
        Path path = new Path();
        float radius = Math.min(bounds.width(), bounds.height());
        if ("weather".equals(type)) {
            float aspect = bounds.width() / bounds.height();
            if (aspect > .75f && aspect < 1.4f) {
                float half = radius / 2f;
                path.addOval(new RectF(bounds.centerX() - half,
                        bounds.centerY() - half, bounds.centerX() + half,
                        bounds.centerY() + half), Path.Direction.CW);
            } else {
                path.addRoundRect(bounds, radius * .46f, radius * .46f,
                        Path.Direction.CW);
            }
        } else if ("prayer".equals(type)) {
            path.addRoundRect(bounds, radius * .5f, radius * .5f, Path.Direction.CW);
        } else if ("media".equals(type)) {
            path.addRoundRect(bounds, radius * .22f, radius * .22f, Path.Direction.CW);
        } else {
            path.addRoundRect(bounds, radius * .28f, radius * .28f, Path.Direction.CW);
        }
        return path;
    }

    private void drawCardSurface(Canvas canvas, String type, RectF bounds) {
        int color = "clock".equals(type) ? colours.surfaceContainerHigh
                : "prayer".equals(type) ? colours.primaryContainer
                : "media".equals(type) ? colours.secondaryContainer
                : colours.tertiaryContainer;
        drawGlassSurface(canvas, cardPath(type, bounds), bounds, color);
    }

    private void drawGlassSurface(Canvas canvas, Path shape, RectF bounds, int tint) {
        float height = bounds.height();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x01000000);
        paint.setShadowLayer(Math.max(1f, height * .04f), 0, height * .015f, 0x40000000);
        canvas.drawPath(shape, paint);
        paint.clearShadowLayer();

        canvas.save();
        canvas.clipPath(shape);
        if (glassBackdrop != null && !glassBackdrop.isRecycled()) {
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(255);
            paint.setFilterBitmap(true);
            canvas.drawBitmap(glassBackdrop, null, frame(), paint);
            paint.setColor(colours.surface);
            paint.setAlpha(backgroundScrimAlpha());
            canvas.drawPath(shape, paint);
            paint.setAlpha(255);
        }
        int alpha = background == null ? GlassTint.NO_PICTURE_ALPHA
                : glassAlpha(shape, bounds, tint);
        paint.setColor((alpha << 24) | (tint & 0x00FFFFFF));
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.STROKE);
        // The centered 2 px stroke is clipped to leave a 1 px inner edge.
        paint.setStrokeWidth(2f);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(bounds.left, bounds.top,
                bounds.left, bounds.top + bounds.height() * .35f,
                0x47FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private int glassAlpha(Path shape, RectF bounds, int tint) {
        if (glassBackdrop == null || glassBackdrop.isRecycled()) {
            return colours.worstCaseGlassAlpha(tint);
        }
        if (isTransitioning() && renderingCardType != null) {
            Integer held = transitionGlassAlphas.get(renderingCardType);
            if (held != null) {
                lastCardGlassAlphas.put(renderingCardType, held);
                return held;
            }
            Path inFrame = new Path(shape);
            inFrame.offset(frameX, frameY);
            double backdrop = GlassBackdrop.percentile90(background,
                    frameWidth(), frameHeight(), inFrame, colours.surface, backgroundScrimAlpha());
            int alpha = colours.alphaForBackdrop(tint, backdrop);
            transitionGlassAlphas.put(renderingCardType, alpha);
            lastCardGlassAlphas.put(renderingCardType, alpha);
            return alpha;
        }
        String key = System.identityHashCode(background) + ":" + frameWidth() + "x" + frameHeight()
                + "@" + frameX + "," + frameY
                + ":" + backgroundScrimAlpha() + ":" + layout.toJson() + ":" + tint
                + ":" + colours.onSurface + ":"
                + colours.onSurfaceVariant + ":" + bounds.left + "," + bounds.top + ","
                + bounds.right + "," + bounds.bottom;
        Integer cached = glassAlphas.get(key);
        if (cached != null) {
            if (renderingCardType != null) {
                lastCardGlassAlphas.put(renderingCardType, cached);
            }
            return cached;
        }
        Path inFrame = new Path(shape);
        inFrame.offset(frameX, frameY);
        double backdrop = GlassBackdrop.percentile90(background, frameWidth(), frameHeight(),
                inFrame, colours.surface, backgroundScrimAlpha());
        int alpha = colours.alphaForBackdrop(tint, backdrop);
        if (glassAlphas.size() >= 64) {
            glassAlphas.clear();
        }
        glassAlphas.put(key, alpha);
        if (renderingCardType != null) {
            lastCardGlassAlphas.put(renderingCardType, alpha);
        }
        return alpha;
    }

    private void drawLyrics(Canvas canvas, boolean staticPass) {
        float inset = Math.max(12f * unit(), getWidth() * .035f);
        RectF content = new RectF(inset, inset,
                getWidth() - inset, getHeight() - inset);
        if (staticPass) {
            float leftWidth = Math.min(content.height() * .50f, content.width() * .31f);
            RectF leftPanel = new RectF(content.left - 6f * unit(),
                    content.top - 6f * unit(), content.left + leftWidth + 6f * unit(),
                    content.bottom + 6f * unit());
            RectF rightPanel = new RectF(leftPanel.right + 6f * unit(),
                    content.top - 6f * unit(), content.right + 6f * unit(),
                    content.bottom + 6f * unit());
            Path leftShape = new Path();
            Path rightShape = new Path();
            float radius = Math.min(18f * unit(), content.height() * .12f);
            leftShape.addRoundRect(leftPanel, radius, radius, Path.Direction.CW);
            rightShape.addRoundRect(rightPanel, radius, radius, Path.Direction.CW);
            drawGlassSurface(canvas, leftShape, leftPanel, colours.surfaceContainerHigh);
            drawGlassSurface(canvas, rightShape, rightPanel, colours.surfaceContainerHigh);
        }
        canvas.save();
        canvas.clipRect(content);
        MediaControlLayout controls = lyricsControlLayout();
        float titleReserve = Math.min(20f * unit(), content.height() * .15f) * .80f * 2.5f
                + 16f * unit();
        float artSize = Math.max(0f, Math.min(Math.min(content.height() * .50f,
                content.width() * .31f), controls.playPause.top - content.top - titleReserve));
        RectF artBounds = new RectF(content.left, content.top,
                content.left + artSize, content.top + artSize);
        if (staticPass && artSize > 0f) {
            drawLyricsArt(canvas, artBounds);
        }
        float leftWidth = Math.min(content.height() * .50f, content.width() * .31f);
        float titleSize = Math.min(20f * unit(), content.height() * .15f) * .80f;
        float titleY = artBounds.bottom + 8f * unit() + titleSize;
        if (staticPass && media != null) {
            text(canvas, media.quran == null ? media.title
                            : media.quran.surah.transliteration, content.left, titleY, titleSize,
                    CREAM, Fonts.semibold(getContext()), leftWidth);
            text(canvas, media.artist, content.left,
                    titleY + titleSize * 1.2f, titleSize * .72f,
                    SAGE, Fonts.regular(getContext()), leftWidth);
        }
        float lyricLeft = content.left + leftWidth
                + Math.max(16f * unit(), content.width() * .05f);
        RectF lyricArea = new RectF(lyricLeft, content.top,
                content.right, content.bottom);
        if (media != null && media.isQuranSession) {
            if (!staticPass) {
                drawQuran(canvas, lyricArea);
            }
            canvas.restore();
            return;
        }
        if (lyricsLoading || lyrics == null || lyrics.isEmpty()) {
            if (staticPass) {
                int message = lyricsLoading ? R.string.lyrics_loading
                        : media == null || !media.hasTrack ? R.string.lyrics_no_track
                        : R.string.lyrics_unavailable;
                stateText(canvas, words.getString(message), lyricArea,
                        Math.min(24f * unit(), lyricArea.height() * .24f), CREAM);
            }
            canvas.restore();
            return;
        }
        if (!lyrics.isTimed()) {
            if (staticPass) {
                text(canvas, words.getString(R.string.lyrics_not_timed), lyricArea.left,
                        lyricArea.top + 10f * unit(), 9f * unit(), DIM,
                        Fonts.regular(getContext()), lyricArea.width());
            }
            lyricArea.top += 15f * unit();
        }
        if (displayLyrics != null && displayLyrics.isTimed()) {
            if (!staticPass) {
                int current = Math.max(0, lyricsLineIndex());
                long now = SystemClock.uptimeMillis();
                if (displayedLyric != current) {
                    outgoingLyric = displayedLyric;
                    outgoingLyricBlock = currentLyricBlock;
                    currentLyricBlock = null;
                    displayedLyric = current;
                    lyricTransitionAt = now;
                }
                float t = Math.min(1f, (now - lyricTransitionAt) / 320f);
                float eased = 1f - (1f - t) * (1f - t) * (1f - t);
                float travel = Math.min(18f * unit(), lyricArea.height() * .18f);
                if (outgoingLyric >= 0 && t < 1f) {
                    drawLyricBlock(canvas, lyricArea, outgoingLyric, true,
                            -travel * eased, Math.round(255f * (1f - eased)));
                }
                drawLyricBlock(canvas, lyricArea, current, false,
                        travel * (1f - eased), outgoingLyric < 0 ? 255
                                : Math.round(255f * eased));
            }
        } else if (staticPass) {
            TextPaint lyricPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            lyricPaint.setColor(CREAM);
            lyricPaint.setTypeface(Fonts.regular(getContext()));
            Fonts.cardText(lyricPaint);
            lyricPaint.setTextSize(Math.min(17f * unit(), lyricArea.height() * .14f));
            StaticLayout lines = StaticLayout.Builder.obtain(lyrics.plainText, 0,
                            lyrics.plainText.length(), lyricPaint,
                            Math.max(1, (int) lyricArea.width()))
                    .setIncludePad(false)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build();
            canvas.translate(lyricArea.left, lyricArea.top);
            lines.draw(canvas);
        }
        canvas.restore();
    }

    private void drawMovingLyrics(Canvas canvas) {
        if (media != null && media.isQuranSession) {
            drawLyrics(canvas, false);
            return;
        }
        if (displayLyrics != null && displayLyrics.isTimed() && !lyricsLoading
                && !displayLyrics.isEmpty()) {
            drawLyrics(canvas, false);
        }
    }

    private void drawLyricBlock(Canvas canvas, RectF area, int current,
            boolean outgoing, float offsetY, int alpha) {
        LyricBlock block = outgoing ? outgoingLyricBlock : currentLyricBlock;
        if (block == null || !block.matches(displayLyrics, current, area)) {
            block = createLyricBlock(area, current);
            if (outgoing) {
                outgoingLyricBlock = block;
            } else {
                currentLyricBlock = block;
            }
        }
        float y = area.centerY() - block.totalHeight / 2f + offsetY;
        canvas.saveLayerAlpha(area, Math.max(0, Math.min(255, alpha)));
        canvas.clipRect(area);
        for (StaticLayout line : block.lines) {
            if (line == null) {
                continue;
            }
            canvas.save();
            canvas.translate(area.left, y);
            line.draw(canvas);
            canvas.restore();
            y += line.getHeight() + block.gap;
        }
        canvas.restore();
    }

    private LyricBlock createLyricBlock(RectF area, int current) {
        int[] indexes = {current - 1, current, current + 1, current + 2};
        boolean timed = lyrics != null && lyrics.isTimed();
        int[] colors = timed
                ? new int[] {withAlpha(SAGE, .5f), BRASS,
                        withAlpha(CREAM, .75f), withAlpha(CREAM, .48f)}
                : new int[] {CREAM, CREAM, CREAM, CREAM};
        float mainSize = Math.min(25f * unit(), area.height() * .22f) * .70f;
        float sideSize = Math.min(16f * unit(), area.height() * .14f) * .70f;
        if (!timed) mainSize = sideSize;
        float gap = Math.max(2f * unit(), area.height() * .045f);
        StaticLayout[] lines = lyricLayouts(indexes, colors, area.width(),
                mainSize, sideSize, true);
        float total = lyricBlockHeight(lines, gap);
        if (total > area.height() * .95f) {
            float scale = area.height() * .95f / total;
            gap *= scale;
            lines = lyricLayouts(indexes, colors, area.width(),
                    mainSize * scale, sideSize * scale, true);
            total = lyricBlockHeight(lines, gap);
        }
        return new LyricBlock(displayLyrics, current, area, lines, gap, total);
    }

    private void drawQuran(Canvas canvas, RectF area) {
        QuranMetadata current = media.quran;
        if (current == null) {
            drawQuranText(canvas, DASH, area, Math.min(30f * unit(), area.height() * .2f),
                    colours.onSurface, false, 1);
            return;
        }
        float t = Math.min(1f, (SystemClock.uptimeMillis() - quranTransitionAt) / 320f);
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);
        float travel = Math.min(18f * unit(), area.height() * .18f);
        if (outgoingQuran != null && t < 1f) {
            drawQuranAyah(canvas, area, outgoingQuran,
                    -travel * eased, Math.round(255f * (1f - eased)));
        }
        drawQuranAyah(canvas, area, current, outgoingQuran == null ? 0 : travel * (1f - eased),
                outgoingQuran == null ? 255 : Math.round(255f * eased));
    }

    private void drawQuranAyah(Canvas canvas, RectF area, QuranMetadata metadata,
            float offset, int alpha) {
        canvas.saveLayerAlpha(area, alpha);
        canvas.clipRect(area);
        canvas.translate(0, offset);
        float h = area.height();
        float gap = Math.max(2f * unit(), h * .025f);
        if (metadata.ayah <= 0) {
            drawQuranText(canvas, metadata.surah.arabic,
                    new RectF(area.left, area.top, area.right, area.top + h * .6f),
                    Math.min(36f * unit(), h * .27f), colours.primary, true, 4);
            drawQuranText(canvas, DASH,
                    new RectF(area.left, area.top + h * .65f, area.right, area.bottom),
                    Math.min(17f * unit(), h * .13f), colours.onSurface, false, 4);
            canvas.restore();
            return;
        }
        String previous = quranDocument == null || metadata.ayah <= 1 ? ""
                : quranDocument.text(QuranRepository.QURAN_UTHMANI, metadata.ayah - 1);
        String arabic = quranDocument == null ? DASH
                : quranDocument.text(QuranRepository.QURAN_UTHMANI, metadata.ayah);
        String translation = quranDocument == null ? DASH
                : quranDocument.text(quranEdition, metadata.ayah);
        drawQuranText(canvas, previous,
                new RectF(area.left, area.top, area.right, area.top + h * .16f),
                Math.min(16f * unit(), h * .12f), withAlpha(colours.onSurface, .45f), true, 2);
        drawQuranText(canvas, arabic,
                new RectF(area.left, area.top + h * .16f + gap,
                        area.right, area.top + h * .61f),
                Math.min(34f * unit(), h * .25f), colours.primary, true, 4);
        float radius = Math.min(12f * unit(), h * .075f);
        float centerY = area.top + h * .69f;
        paint.setColor(colours.primary);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, unit()));
        canvas.drawCircle(area.centerX(), centerY, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        drawQuranText(canvas, "\u06dd", new RectF(area.centerX() - radius * 1.5f,
                        centerY - radius * 1.5f, area.centerX() + radius * 1.5f,
                        centerY + radius * 1.5f), radius * 2f,
                withAlpha(colours.primary, .32f), true, 1);
        drawQuranText(canvas, String.valueOf(metadata.ayah),
                new RectF(area.centerX() - radius, centerY - radius,
                        area.centerX() + radius, centerY + radius), radius * 1.05f,
                colours.primary, false, 1);
        drawQuranText(canvas, translation,
                new RectF(area.left, area.top + h * .78f, area.right, area.bottom),
                Math.min(16f * unit(), h * .12f), colours.onSurface, false, 4);
        canvas.restore();
    }

    private void drawQuranText(Canvas canvas, String value, RectF area, float size,
            int color, boolean arabic, int maxLines) {
        if (value == null || value.isEmpty() || area.width() <= 0 || area.height() <= 0) {
            return;
        }
        if (arabic && quranTypeface == null) {
            quranTypeface = getResources().getFont(R.font.amiri_quran);
        }
        String key = value + "\u0000" + area.width() + ":" + area.height() + ":"
                + size + ":" + color + ":" + arabic + ":" + maxLines;
        StaticLayout cached = quranLayouts.get(key);
        if (cached != null) {
            canvas.save();
            canvas.clipRect(area);
            canvas.translate(area.left, area.centerY() - cached.getHeight() / 2f);
            cached.draw(canvas);
            canvas.restore();
            return;
        }
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(arabic ? quranTypeface : Fonts.regular(getContext()));
        textPaint.setColor(color);
        StaticLayout lines = null;
        float candidate = Math.max(1f, size);
        for (int attempt = 0; attempt < 60; attempt++) {
            textPaint.setTextSize(candidate);
            StaticLayout full = StaticLayout.Builder.obtain(value, 0, value.length(),
                            textPaint, Math.max(1, (int) area.width()))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setTextDirection(arabic ? TextDirectionHeuristics.RTL
                            : TextDirectionHeuristics.FIRSTSTRONG_LTR)
                    .setIncludePad(arabic)
                    .build();
            lines = full;
            if ((full.getLineCount() <= maxLines && full.getHeight() <= area.height())
                    || candidate <= 1f) {
                break;
            }
            candidate = Math.max(1f, candidate * .90f);
        }
        canvas.save();
        if (quranLayouts.size() >= 48) {
            quranLayouts.clear();
        }
        quranLayouts.put(key, lines);
        canvas.clipRect(area);
        canvas.translate(area.left, area.centerY() - lines.getHeight() / 2f);
        lines.draw(canvas);
        canvas.restore();
    }

    private void drawLyricsArt(Canvas canvas, RectF bounds) {
        if (media != null && media.isQuranSession
                && (media.art == null || media.art.isRecycled())) {
            paint.setColor(colours.surfaceContainerHighest);
            canvas.drawRoundRect(bounds, 12f * unit(), 12f * unit(), paint);
            drawQuranText(canvas, media.quran == null ? DASH : media.quran.surah.arabic, bounds,
                    bounds.height() * .30f, colours.primary, true, 4);
            return;
        }
        Path shape = ExpressiveShapes.cookie(bounds);
        paint.setColor(colours.surfaceContainerHighest);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(shape, paint);
        if (media == null || media.art == null || media.art.isRecycled()) {
            text(canvas, "♫", bounds.left + bounds.width() * .34f,
                    bounds.centerY() + bounds.height() * .15f,
                    bounds.height() * .43f, SAGE,
                    Fonts.semibold(getContext()), bounds.width() * .5f);
            return;
        }
        Bitmap art = media.art;
        BitmapShader shader = new BitmapShader(art,
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float scale = Math.max(bounds.width() / art.getWidth(),
                bounds.height() / art.getHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(bounds.centerX() - art.getWidth() * scale / 2f,
                bounds.centerY() - art.getHeight() * scale / 2f);
        shader.setLocalMatrix(matrix);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(shader);
        canvas.drawPath(shape, paint);
        paint.setShader(null);
    }

    private StaticLayout[] lyricLayouts(int[] indexes, int[] colors,
            float width, float mainSize, float sideSize, boolean hasCurrent) {
        StaticLayout[] layouts = new StaticLayout[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            int index = indexes[i];
            if (index < 0 || index >= displayLyrics.lines.length) {
                continue;
            }
            String line = displayLyrics.lines[index].text;
            if (line == null || line.isEmpty()) {
                continue;
            }
            boolean current = i == 1 && hasCurrent;
            TextPaint lyricPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            lyricPaint.setColor(colors[i]);
            lyricPaint.setTypeface(current && lyrics != null && lyrics.isTimed()
                    ? Fonts.semibold(getContext())
                    : Fonts.regular(getContext()));
            Fonts.cardText(lyricPaint);
            lyricPaint.setTextSize(i == 1 ? mainSize : sideSize);
            layouts[i] = StaticLayout.Builder.obtain(line, 0, line.length(),
                            lyricPaint, Math.max(1, (int) width))
                    .setIncludePad(false)
                    .setMaxLines(current ? 3 : 1)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build();
        }
        return layouts;
    }

    private static float lyricBlockHeight(StaticLayout[] lines, float gap) {
        float height = 0f;
        int count = 0;
        for (StaticLayout line : lines) {
            if (line != null) {
                height += line.getHeight();
                count++;
            }
        }
        return height + Math.max(0, count - 1) * gap;
    }

    private static int withAlpha(int color, float alpha) {
        return (Math.round(255f * alpha) << 24) | (color & 0x00FFFFFF);
    }

    private void drawDividers(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor((SAGE & 0x00FFFFFF) | 0x2E000000);
        float inset = 8f * unit();
        for (BoardLayout.Card a : layout.cards) {
            RectF first = bounds(a);
            for (BoardLayout.Card b : layout.cards) {
                if (a == b) {
                    continue;
                }
                RectF second = bounds(b);
                if (a.col + a.w == b.col) {
                    float top = Math.max(first.top, second.top) + inset;
                    float bottom = Math.min(first.bottom, second.bottom) - inset;
                    if (bottom > top) {
                        canvas.drawLine(first.right, top, first.right, bottom, paint);
                    }
                }
                if (a.row + a.h == b.row) {
                    float left = Math.max(first.left, second.left) + inset;
                    float right = Math.min(first.right, second.right) - inset;
                    if (right > left) {
                        canvas.drawLine(left, first.bottom, right, first.bottom, paint);
                    }
                }
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }
    private void drawGrid(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(.6f, unit() * .6f));
        paint.setColor(0x557FA7A1);
        float e = edge();
        float cw = (getWidth() - 2 * e) / 12f;
        float ch = (getHeight() - 2 * e) / 6f;
        for (int i = 1; i < 12; i++) {
            canvas.drawLine(e + i * cw, e, e + i * cw, getHeight() - e, paint);
        }
        for (int i = 1; i < 6; i++) {
            canvas.drawLine(e, e + i * ch, getWidth() - e, e + i * ch, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawClock(Canvas canvas, RectF r) {
        Typeface clockFace = Fonts.medium(getContext());
        float size = fit(time, r.height() * .60f, r.width(), clockFace);
        float dateSize = fit(date, Math.min(18f * unit(), r.height() * .16f),
                r.width(), Fonts.regular(getContext()));
        paint.setTypeface(clockFace);
        paint.setTextSize(size);
        Paint.FontMetrics timeMetrics = paint.getFontMetrics();
        paint.setTypeface(Fonts.regular(getContext()));
        paint.setTextSize(dateSize);
        Paint.FontMetrics dateMetrics = paint.getFontMetrics();
        float spacing = 5f * unit();
        float blockHeight = timeMetrics.descent - timeMetrics.ascent + spacing
                + dateMetrics.descent - dateMetrics.ascent;
        float blockTop = r.top + (r.height() - blockHeight) / 2f;
        float y = blockTop - timeMetrics.ascent;
        text(canvas, time, r.left, y, size, CREAM,
                clockFace, r.width());
        float dateY = y + timeMetrics.descent + spacing - dateMetrics.ascent;
        text(canvas, date, r.left, dateY, dateSize, SAGE,
                Fonts.regular(getContext()), r.width());
    }

    private void drawPrayer(Canvas canvas, RectF r, int gridRows) {
        WidgetData.PrayerRow next = null;
        if (prayer != null && prayer.rows != null) {
            for (WidgetData.PrayerRow row : prayer.rows) {
                if (row.next) {
                    next = row;
                    break;
                }
            }
        }
        boolean stale = prayer != null && "stale".equals(prayer.status);
        float limit = gridRows >= 3 ? r.top + r.height() * .72f : r.bottom;
        float star = Math.min(28f * unit(), Math.max(10f * unit(), r.height() * .36f));
        star = Math.min(star, Math.min(r.width() * .15f, (limit - r.top) * .62f));
        float x = r.left;
        if (next != null && r.width() >= star * 3.2f && r.height() >= star * 1.5f) {
            drawStar(canvas, x + star / 2,
                    r.top + (limit - r.top) / 2f, star, true);
            x += star + Math.max(5f * unit(), star * .25f);
        }
        float width = r.right - x;
        String title = next == null ? DASH : next.name;
        if (next == null) {
            int message = prayer != null && "unavailable".equals(prayer.status)
                    ? R.string.prayer_unavailable
                    : prayer != null && "tomorrow_waiting".equals(prayer.status)
                    ? R.string.prayer_tomorrow_waiting : R.string.prayer_waiting;
            String stateMessage = words.getString(message);
            if (stale) {
                stateMessage = words.getString(R.string.board_stale)
                        + (prayer.updated.isEmpty() ? "" : " \u00b7 "
                        + words.getString(R.string.board_updated, prayer.updated));
            }
            stateText(canvas, stateMessage, new RectF(x, r.top, r.right, limit),
                    Math.min(16f * unit(), r.height() * .22f), stale ? DIM : SOFT);
            if (gridRows >= 3 && prayer != null && prayer.rows != null && prayer.rows.length == 5) {
                drawPrayerRow(canvas, r, prayer.rows);
            }
            return;
        }
        float available = Math.max(1f, limit - r.top);
        float titleSize = Math.min(32f * unit(), Math.min(r.height() * .40f,
                available * .57f)) * .80f;
        float countSize = Math.min(18f * unit(), Math.min(r.height() * .22f,
                available * .42f)) * .85f;
        float blockHeight = titleSize + countSize * 1.12f;
        float titleY = r.top + (available - blockHeight) / 2f + titleSize;
        titleSize = fit(title + "  " + next.time, titleSize, width,
                Fonts.semibold(getContext()));
        text(canvas, title, x, titleY, titleSize,
                stale ? DIM : colours.onPrimaryContainer,
                Fonts.semibold(getContext()), width);
        paint.setTypeface(Fonts.semibold(getContext()));
        paint.setTextSize(titleSize);
        float timeX = x + paint.measureText(title + "  ");
        text(canvas, next.time, timeX, titleY, titleSize,
                stale ? DIM : colours.onPrimaryContainer,
                Fonts.semibold(getContext()), r.right - timeX);
        String countdown = prayer == null ? DASH : prayer.countdown;
        if (prayer != null) {
            if ("stale".equals(prayer.status)) {
                countdown = words.getString(R.string.board_stale)
                        + (prayer.updated.isEmpty() ? "" : " \u00b7 "
                        + words.getString(R.string.board_updated, prayer.updated));
            } else if ("waiting".equals(prayer.status)) {
                countdown = words.getString(R.string.prayer_waiting);
            } else if ("unavailable".equals(prayer.status)) {
                countdown = words.getString(R.string.prayer_unavailable);
            } else if ("tomorrow_waiting".equals(prayer.status)) {
                countdown = words.getString(R.string.prayer_tomorrow_waiting);
            }
        }
        if (countdown == null || countdown.isEmpty()) {
            countdown = DASH;
        }
        String displayCountdown = countdown;
        float countdownY = Math.min(limit, titleY + countSize * 1.12f);
        text(canvas, displayCountdown, x, countdownY, countSize,
                stale ? DIM : withAlpha(colours.onPrimaryContainer, .8f),
                Fonts.medium(getContext()), width);
        if (prayer != null && prayer.zoneCode != null && !prayer.zoneCode.isEmpty()) {
            paint.setTypeface(Fonts.regular(getContext()));
            paint.setTextSize(countSize);
            float suffixX = x + paint.measureText(displayCountdown + "  ");
            float zoneSize = Math.min(11f * unit(), r.height() * .42f);
            if (suffixX < r.right - 8f * unit()) {
                text(canvas, "\u00b7 " + prayer.zoneCode, suffixX, countdownY, zoneSize,
                        SAGE, Fonts.regular(getContext()), r.right - suffixX);
            }
        }
        if (gridRows >= 3 && prayer != null && prayer.rows != null
                && prayer.rows.length == 5) {
            drawPrayerRow(canvas, r, prayer.rows);
        }
    }

    private void drawPrayerRow(Canvas canvas, RectF r, WidgetData.PrayerRow[] rows) {
        float top = r.top + r.height() * .72f;
        float col = r.width() / 5f;
        float height = r.bottom - top;
        float timeSize = Math.min(11f * unit(), height / 2.6f);
        float nameSize = timeSize * .82f;
        paint.setTypeface(Fonts.medium(getContext()));
        paint.setTextSize(nameSize);
        Paint.FontMetrics nameMetrics = paint.getFontMetrics();
        paint.setTypeface(Fonts.regular(getContext()));
        paint.setTextSize(timeSize);
        Paint.FontMetrics timeMetrics = paint.getFontMetrics();
        float gap = timeSize * .28f;
        float blockHeight = nameMetrics.descent - nameMetrics.ascent + gap
                + timeMetrics.descent - timeMetrics.ascent;
        float nameY = top + (height - blockHeight) / 2f - nameMetrics.ascent;
        float timeY = nameY + nameMetrics.descent + gap - timeMetrics.ascent;
        for (int i = 0; i < 5; i++) {
            WidgetData.PrayerRow row = rows[i];
            float center = r.left + (i + .5f) * col;
            int color = row.past || (prayer != null && "stale".equals(prayer.status))
                    ? DIM : CREAM;
            centeredText(canvas, row.name, center, nameY,
                    nameSize, color, Fonts.medium(getContext()), col - 2f * unit());
            centeredText(canvas, row.time, center, timeY,
                    timeSize, color, Fonts.regular(getContext()), col - 2f * unit());
        }
    }

    private void drawStar(Canvas canvas, float x, float y, float size, boolean filled) {
        paint.setColor(BRASS);
        paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, 1.6f * unit()));
        float half = size * .5f;
        canvas.drawPath(ExpressiveShapes.star(new RectF(
                x - half, y - half, x + half, y + half)), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private Path cookiePath(RectF bounds) {
        if (cookieTemplate == null) {
            cookieTemplate = ExpressiveShapes.cookie(new RectF(0f, 0f, 100f, 100f));
        }
        Matrix transform = new Matrix();
        transform.setScale(bounds.width() / 100f, bounds.height() / 100f);
        transform.postTranslate(bounds.left, bounds.top);
        Path result = new Path();
        cookieTemplate.transform(transform, result);
        return result;
    }

    private Bitmap artworkTile(Bitmap art) {
        if (artworkTile != null && !artworkTile.isRecycled()
                && artworkTileSource == art) {
            return artworkTile;
        }
        if (artworkTile != null && !artworkTile.isRecycled()) {
            artworkTile.recycle();
        }
        final int size = 384;
        artworkTile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        artworkTileSource = art;
        Canvas tileCanvas = new Canvas(artworkTile);
        RectF square = new RectF(0f, 0f, size, size);
        Path shape = cookiePath(square);
        BitmapShader shader = new BitmapShader(art,
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float scale = Math.max(size / (float) art.getWidth(), size / (float) art.getHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((size - art.getWidth() * scale) / 2f,
                (size - art.getHeight() * scale) / 2f);
        shader.setLocalMatrix(matrix);
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(255);
        paint.setShader(shader);
        paint.setStyle(Paint.Style.FILL);
        tileCanvas.drawPath(shape, paint);
        paint.setShader(null);
        return artworkTile;
    }

    private void drawMedia(Canvas canvas, RectF r) {
        float artworkSize = Math.min(r.height() * .95f, r.width() * .45f);
        float artworkTop = r.centerY() - artworkSize / 2f;
        RectF artwork = new RectF(r.left, artworkTop,
                r.left + artworkSize, artworkTop + artworkSize);
        Path artShape = cookiePath(artwork);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setShadowLayer(artworkSize * .06f, 0,
                artworkSize * .02f, 0x59000000);
        canvas.drawPath(artShape, paint);
        paint.clearShadowLayer();
        if (media != null && media.art != null && !media.art.isRecycled()) {
            paint.setShader(null);
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(255);
            paint.setFilterBitmap(true);
            canvas.drawBitmap(artworkTile(media.art), null, artwork, paint);
        } else {
            paint.setColor(colours.surfaceContainerHighest);
            canvas.drawPath(artShape, paint);
            float cx = artwork.centerX();
            float cy = artwork.centerY();
            paint.setColor(SAGE);
            Path note = new Path();
            note.moveTo(cx + artworkSize * .02f, cy - artworkSize * .23f);
            note.lineTo(cx + artworkSize * .23f, cy - artworkSize * .28f);
            note.lineTo(cx + artworkSize * .23f, cy + artworkSize * .10f);
            note.lineTo(cx + artworkSize * .18f, cy + artworkSize * .10f);
            note.lineTo(cx + artworkSize * .18f, cy - artworkSize * .17f);
            note.lineTo(cx + artworkSize * .07f, cy - artworkSize * .14f);
            note.lineTo(cx + artworkSize * .07f, cy + artworkSize * .17f);
            note.lineTo(cx + artworkSize * .02f, cy + artworkSize * .17f);
            note.close();
            note.addCircle(cx - artworkSize * .04f, cy + artworkSize * .17f,
                    artworkSize * .065f, Path.Direction.CW);
            note.addCircle(cx + artworkSize * .17f, cy + artworkSize * .11f,
                    artworkSize * .065f, Path.Direction.CW);
            canvas.drawPath(note, paint);
        }
        float x = artwork.right + 10f * unit();
        float width = Math.max(1f, r.right - x);
        String title = media == null || media.title == null ? DASH : media.title;
        String artist = media == null || media.artist == null ? "" : media.artist;
        boolean playing = media != null && media.playing;
        float titleSize = Math.min(19f * unit(), r.height() * .19f) * .75f;
        float artistSize = Math.min(13f * unit(), r.height() * .14f) * .80f;
        MediaControlLayout controls = mediaControlLayout(r);
        float textTop = r.top;
        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTypeface(Fonts.semibold(getContext()));
        titlePaint.setTextSize(titleSize);
        Fonts.cardText(titlePaint);
        titlePaint.setColor(playing ? CREAM : SAGE);
        float widestWord = 0f;
        for (String word : title.trim().split("\\s+")) {
            widestWord = Math.max(widestWord, titlePaint.measureText(word));
        }
        if (widestWord > width) {
            // Preserve ordinary song words before resorting to a mid-word break.
            titlePaint.setTextSize(Math.max(titleSize * .82f, titleSize * width / widestWord));
        }
        float titleRoom = controls.playPause.top - textTop - artistSize * 2.3f - 8f * unit();
        int titleLines = Math.max(1, (int) (titleRoom / titlePaint.getFontSpacing()));
        StaticLayout titleLayout = StaticLayout.Builder.obtain(title, 0, title.length(),
                        titlePaint, Math.max(1, Math.round(width)))
                .setIncludePad(false)
                .setMaxLines(titleLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
        canvas.save();
        canvas.clipRect(x, textTop, r.right,
                Math.max(textTop, textTop + titleRoom));
        canvas.translate(x, textTop);
        titleLayout.draw(canvas);
        canvas.restore();
        float artistY = Math.min(controls.playPause.top - artistSize - 8f * unit(),
                textTop + titleLayout.getHeight() + artistSize * 1.3f);
        if (!artist.isEmpty() && artistY >= r.top + artistSize) {
            text(canvas, artist, x, artistY, artistSize, SAGE,
                    Fonts.regular(getContext()), width);
        }
    }

    private void drawProgress(Canvas canvas) {
        if (!isMediaPlaying() || media.duration <= 0) {
            return;
        }
        for (BoardLayout.Card card : layout.cards) {
            if (!"media".equals(card.type)) {
                continue;
            }
            RectF bounds = visualBounds(card);
            float pad = Math.max(2f * unit(),
                    Math.min(bounds.width(), bounds.height()) * .08f);
            RectF r = new RectF(bounds.left + pad, bounds.top + pad,
                    bounds.right - pad, bounds.bottom - pad);
            float artworkSize = Math.min(r.height() * .95f, r.width() * .45f);
            float x = r.left + artworkSize + 10f * unit();
            float width = Math.max(1f, r.right - x);
            float progress = Math.max(0f, Math.min(1f,
                    (float) media.estimatedPositionMs() / media.duration));
            float lineY = mediaControlLayout(r).playPause.top - 5f * unit();
            canvas.save();
            float alpha = transitionAlpha(card.type, SystemClock.uptimeMillis());
            if (alpha < 1f) {
                canvas.saveLayerAlpha(bounds, Math.round(alpha * 255f));
            }
            canvas.clipPath(cardPath(card.type, bounds));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.5f, r.height() * .015f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            float end = x + width * progress;
            float gap = Math.max(3f * unit(), r.width() * .025f);
            paint.setColor((BRASS & 0x00FFFFFF) | 0x4D000000);
            float unplayedStart = progress <= 0f ? x : end + gap;
            if (unplayedStart < r.right) {
                canvas.drawLine(unplayedStart, lineY, r.right, lineY, paint);
            }
            paint.setColor(BRASS);
            Path wave = new Path();
            wave.moveTo(x, lineY);
            float step = Math.max(1f, r.width() * .01f);
            for (float point = x + step; point < end; point += step) {
                float phase = (point - x) / Math.max(1f, r.width() * .1f);
                wave.lineTo(point, lineY + (float) Math.sin(phase * Math.PI * 2)
                        * r.height() * .03f);
            }
            wave.lineTo(end, lineY);
            canvas.drawPath(wave, paint);
            paint.setStyle(Paint.Style.FILL);
            if (alpha < 1f) {
                canvas.restore();
            }
            canvas.restore();
        }
    }

    private void drawWeather(Canvas canvas, RectF r) {
        boolean valid = weather != null && !Double.isNaN(weather.tempC);
        boolean stale = valid && weather.observedAtMillis > 0
                && System.currentTimeMillis() - weather.observedAtMillis > 60 * 60 * 1000L;
        float center = r.centerX();
        float iconSize = Math.min(r.width() * .34f, r.height() * .29f);
        float iconTop = r.top + r.height() * (stale ? .06f : .09f);
        if (valid) {
            canvas.save();
            if (stale) {
                canvas.saveLayerAlpha(r, 160);
            }
            drawWeatherIcon(canvas, center, iconTop + iconSize / 2f, iconSize,
                    weather.weatherCode, weather.isDay);
            String temperature = Math.round(weather.tempC) + "°";
            float size = Math.min(34f * unit(), r.height() * .29f);
            centeredText(canvas, temperature, center,
                    r.top + r.height() * .59f, size, colours.onSurface,
                    Fonts.semibold(getContext()), r.width());
            centeredText(canvas, weatherCondition(weather.weatherCode), center,
                    r.top + r.height() * .76f,
                    Math.min(13f * unit(), r.height() * .14f), colours.onSurface,
                    Fonts.regular(getContext()), r.width());
            String rain = weather.rainProbability < 0 ? DASH
                    : weather.rainProbability + "%";
            centeredText(canvas, words.getString(R.string.weather_rain, rain), center,
                    r.top + r.height() * (stale ? .85f : .91f),
                    Math.min(11f * unit(), r.height() * .12f), colours.primary,
                    Fonts.regular(getContext()), r.width());
            if (stale) {
                canvas.restore();
            }
            canvas.restore();
        } else {
            centeredText(canvas, DASH, center, r.centerY() + r.height() * .1f,
                    Math.min(32f * unit(), r.height() * .35f), colours.onSurface,
                    Fonts.regular(getContext()), r.width());
        }
        if (stale) {
            String updated = words.getString(R.string.updated,
                    new SimpleDateFormat("HH:mm", AppLocale.locale(getContext()))
                            .format(new Date(weather.observedAtMillis)));
            centeredText(canvas, updated, center, r.top + r.height() * .97f,
                    Math.min(9f * unit(), r.height() * .09f), colours.onSurfaceVariant,
                    Fonts.regular(getContext()), r.width());
        }
    }

    private String weatherCondition(int code) {
        int label;
        switch (WeatherCode.kind(code)) {
            case CLEAR:
                label = R.string.weather_clear;
                break;
            case CLOUDY:
                label = R.string.weather_cloudy;
                break;
            case PARTLY_CLOUDY:
                label = R.string.weather_partly_cloudy;
                break;
            case FOG:
                label = R.string.weather_fog;
                break;
            case DRIZZLE:
                label = R.string.weather_drizzle;
                break;
            case RAIN:
                label = R.string.weather_rainy;
                break;
            case SNOW:
                label = R.string.weather_snow;
                break;
            case STORM:
                label = R.string.weather_thunder;
                break;
            default:
                return DASH;
        }
        return words.getString(label);
    }

    private void drawWeatherIcon(Canvas canvas, float cx, float cy, float size,
            int code, boolean day) {
        Paint icon = weatherIconPaint;
        canvas.save();
        canvas.translate(cx - size / 2f, cy - size / 2f);
        canvas.scale(size / 100f, size / 100f);
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setStrokeJoin(Paint.Join.ROUND);
        icon.setStrokeWidth(6f);
        icon.setColor(colours.primary);
        WeatherCode.Kind kind = WeatherCode.kind(code);
        boolean cloudy = kind != WeatherCode.Kind.CLEAR;
        if (!cloudy || kind == WeatherCode.Kind.PARTLY_CLOUDY) {
            if (!WeatherCode.isNightVariant(code, day)) {
                Path sun = new Path();
                float x = cloudy ? 35f : 50f;
                float y = cloudy ? 36f : 50f;
                sun.addCircle(x, y, 17f, Path.Direction.CW);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    float dx = (float) Math.cos(angle);
                    float dy = (float) Math.sin(angle);
                    sun.moveTo(x + dx * 25f, y + dy * 25f);
                    sun.lineTo(x + dx * 32f, y + dy * 32f);
                }
                canvas.drawPath(sun, icon);
            } else {
                Path moon = new Path();
                moon.addCircle(cloudy ? 35f : 50f, cloudy ? 36f : 50f,
                        24f, Path.Direction.CW);
                Path cutout = new Path();
                cutout.addCircle(cloudy ? 45f : 62f, cloudy ? 26f : 38f,
                        22f, Path.Direction.CW);
                moon.op(cutout, Path.Op.DIFFERENCE);
                icon.setStyle(Paint.Style.FILL);
                canvas.drawPath(moon, icon);
                icon.setStyle(Paint.Style.STROKE);
            }
        }
        if (cloudy) {
            icon.setColor(colours.onSurface);
            Path cloud = new Path();
            cloud.moveTo(22f, 66f);
            cloud.cubicTo(7f, 65f, 8f, 48f, 23f, 46f);
            cloud.cubicTo(27f, 25f, 54f, 26f, 62f, 43f);
            cloud.cubicTo(84f, 39f, 93f, 65f, 76f, 70f);
            cloud.lineTo(24f, 70f);
            canvas.drawPath(cloud, icon);
        }
        if (kind == WeatherCode.Kind.FOG) {
            icon.setColor(colours.primary);
            Path fog = new Path();
            fog.moveTo(25f, 80f);
            fog.lineTo(75f, 80f);
            fog.moveTo(34f, 91f);
            fog.lineTo(68f, 91f);
            canvas.drawPath(fog, icon);
        }
        if (kind == WeatherCode.Kind.STORM) {
            icon.setColor(colours.primary);
            Path bolt = new Path();
            bolt.moveTo(52f, 73f);
            bolt.lineTo(43f, 87f);
            bolt.lineTo(54f, 85f);
            bolt.lineTo(47f, 99f);
            canvas.drawPath(bolt, icon);
        } else if (kind == WeatherCode.Kind.DRIZZLE || kind == WeatherCode.Kind.RAIN) {
            icon.setColor(colours.primary);
            Path rain = new Path();
            rain.moveTo(36f, 77f);
            rain.cubicTo(31f, 84f, 27f, 91f, 32f, 93f);
            rain.cubicTo(39f, 95f, 41f, 88f, 36f, 77f);
            rain.moveTo(58f, 77f);
            rain.cubicTo(53f, 84f, 49f, 91f, 54f, 93f);
            rain.cubicTo(61f, 95f, 63f, 88f, 58f, 77f);
            icon.setStyle(Paint.Style.FILL);
            canvas.drawPath(rain, icon);
        }
        canvas.restore();
    }

    /** Wrap at a word boundary; only an oversized first word may split. */
    private String wordWrappedTitle(String title, TextPaint textPaint, float width) {
        String content = title.trim().replaceAll("\\s+", " ");
        if (textPaint.measureText(content) <= width) {
            return content;
        }
        int fit = textPaint.breakText(content, true, Math.max(1, width), null);
        int boundary = content.lastIndexOf(' ', Math.min(fit, content.length() - 1));
        if (boundary > 0) {
            return content.substring(0, boundary) + "\n" + content.substring(boundary + 1);
        }
        int end = Math.max(1, fit);
        if (end < content.length() && Character.isHighSurrogate(content.charAt(end - 1))) {
            end = end > 1 ? end - 1 : Math.min(2, content.length());
        }
        return content.substring(0, end) + "\n" + content.substring(end).trim();
    }

    private void centeredText(Canvas canvas, String value, float center, float baseline,
            float size, int color, Typeface face, float width) {
        String content = value == null ? DASH : value;
        float fitted = fit(content, size, width, face);
        paint.setTypeface(face);
        paint.setTextSize(fitted);
        float measured = paint.measureText(content);
        text(canvas, content, center - measured / 2f, baseline,
                fitted, color, face, width);
    }
    private void stateText(Canvas canvas, String value, RectF bounds, float size, int color) {
        if (bounds.width() < 1 || bounds.height() <= 0) {
            return;
        }
        String message = value == null || value.isEmpty() ? DASH : value;
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(color);
        textPaint.setTypeface(Fonts.regular(getContext()));
        Fonts.cardText(textPaint);
        textPaint.setTextSize(size);
        StaticLayout lines = stateLayout(message, textPaint, bounds.width());
        // Keep title-small sizing unless the actual wrapped lines exceed the card.
        for (int attempt = 0; attempt < 4 && lines.getHeight() > bounds.height(); attempt++) {
            textPaint.setTextSize(textPaint.getTextSize()
                    * Math.max(0, bounds.height() - 1f) / lines.getHeight());
            lines = stateLayout(message, textPaint, bounds.width());
        }
        canvas.save();
        canvas.clipRect(bounds);
        canvas.translate(bounds.left, bounds.top + Math.max(0, (bounds.height() - lines.getHeight()) / 2f));
        lines.draw(canvas);
        canvas.restore();
    }

    private StaticLayout stateLayout(String message, TextPaint textPaint, float width) {
        return StaticLayout.Builder.obtain(message, 0, message.length(),
                        textPaint, Math.max(1, (int) width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build();
    }

    private float fit(String value, float size, float width, Typeface face) {
        paint.setTypeface(face);
        if (value != null && value.equals(time)) {
            Fonts.displayText(paint);
        } else {
            Fonts.cardText(paint);
        }
        paint.setTextSize(Math.max(1f, size));
        float measured = paint.measureText(value == null ? DASH : value);
        // 4% margin: variable-font widths don't scale exactly linearly, and a one-pixel
        // overshoot made text() ellipsize the clock ("13:...").
        return Math.max(1f, measured > width * .96f && measured > 0
                ? size * width * .96f / measured : size);
    }

    private void text(Canvas canvas, String value, float x, float baseline,
            float size, int color, Typeface face, float width) {
        if (width <= 0 || size <= 0) {
            return;
        }
        String s = value == null || value.isEmpty() ? DASH : value;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTypeface(face);
        if (s.equals(time)) {
            Fonts.displayText(paint);
        } else {
            Fonts.cardText(paint);
        }
        paint.setTextSize(size);
        if (paint.measureText(s) > width) {
            String ellipsis = "\u2026";
            int count = paint.breakText(s, true,
                    Math.max(0, width - paint.measureText(ellipsis)), null);
            s = s.substring(0, count) + ellipsis;
        }
        canvas.drawText(s, x, baseline, paint);
    }

    private float handleSize() {
        return Math.max(10f * unit(), Math.min(getWidth(), getHeight()) * .07f);
    }

    private void drawHandle(Canvas canvas, RectF r) {
        float h = handleSize();
        float inset = Math.max(2f, h * .2f);
        paint.setColor(SAGE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, unit()));
        canvas.drawLine(r.right - h, r.bottom - inset, r.right - inset,
                r.bottom - inset, paint);
        canvas.drawLine(r.right - inset, r.bottom - h, r.right - inset,
                r.bottom - inset, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editing || getWidth() == 0 || getHeight() == 0) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                for (int i = layout.cards.size() - 1; i >= 0; i--) {
                    BoardLayout.Card c = layout.cards.get(i);
                    RectF r = bounds(c);
                    if (r.contains(event.getX(), event.getY())) {
                        dragging = c;
                        downX = event.getX();
                        downY = event.getY();
                        startCol = c.col;
                        startRow = c.row;
                        startW = c.w;
                        startH = c.h;
                        resizing = event.getX() >= r.right - handleSize() * 1.6f
                                && event.getY() >= r.bottom - handleSize() * 1.6f;
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    }
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (dragging == null) {
                    return false;
                }
                int dx = BoardLayout.snap(event.getX() - downX,
                        (getWidth() - 2 * edge()) / 12f);
                int dy = BoardLayout.snap(event.getY() - downY,
                        (getHeight() - 2 * edge()) / 6f);
                if (resizing) {
                    layout.resize(dragging, startW + dx, startH + dy);
                } else {
                    layout.move(dragging, startCol + dx, startRow + dy);
                }
                Store.prefs(getContext()).edit()
                        .putString(Store.layoutKey(mode), layout.toJson()).apply();
                invalidateStatic();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging == null) {
                    return false;
                }
                dragging = null;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (onEdit != null) {
                    onEdit.run();
                }
                performClick();
                return true;
            default:
                return dragging != null;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
