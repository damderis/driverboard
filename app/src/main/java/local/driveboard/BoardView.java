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
    private static final long NAVIGATION_DURATION_MS = 250;
    private final NavigationRepository.Listener navigationListener = () -> {
        updateNavigation();
        requestOverlayDraw();
        scheduleFrame();
    };
    private NavigationState navigationState;
    private final RectF navigationBounds = new RectF();
    private boolean navigationListening;
    private boolean navigationShown;
    private float navigationFrom;
    private long navigationAt;
    private boolean navigationPressed;

    public void startNavigation() {
        if (!navigationListening) {
            navigationListening = true;
            NavigationRepository.get().addListener(navigationListener);
        }
        updateNavigation();
    }

    public void stopNavigation() {
        navigationListening = false;
        NavigationRepository.get().removeListener(navigationListener);
    }

    private float navigationAlpha() {
        float target = navigationShown ? 1f : 0f;
        if (navigationAt == 0) return target;
        return navigationFrom + (target - navigationFrom)
                * BoardTransition.progress((SystemClock.uptimeMillis() - navigationAt)
                * BoardTransition.DURATION_MS / NAVIGATION_DURATION_MS);
    }

    private void updateNavigation() {
        NavigationState next = NavigationRepository.get().state();
        boolean shown = !editing && next != null
                && Store.prefs(getContext()).getBoolean("navigation_overlay", true);
        if (shown != navigationShown) {
            navigationFrom = navigationAlpha();
            navigationShown = shown;
            navigationAt = SystemClock.uptimeMillis();
        }
        if (next != null) navigationState = next;
    }

    public boolean isNavigationTransitioning() {
        return navigationAt != 0 && SystemClock.uptimeMillis() - navigationAt
                < NAVIGATION_DURATION_MS;
    }

    public boolean hitNavigation(float x, float y) {
        updateNavigation();
        if (!navigationShown || !navigationBounds.contains(x, y)) return false;
        NavigationRepository.get().toggleCollapsed(getContext());
        return true;
    }

    private boolean bannerFullOnly;

    /** Weather entry: hide the directions banner unless full screen (the map is beside it). */
    public void setBannerFullOnly(boolean value) {
        bannerFullOnly = value;
    }

    private void drawNavigation(Canvas canvas) {
        updateNavigation();
        float alpha = navigationAlpha();
        // The weather entry's split/narrow sit next to Android Auto's side panel showing the map.
        if (navigationState == null || alpha <= 0f || editing
                || (bannerFullOnly && !Store.FULL.equals(mode))) {
            navigationBounds.setEmpty();
            return;
        }
        NavigationState nav = navigationState;
        float scale = Math.max(.5f, Math.min(getWidth() / 700f, getHeight() / 600f));
        float margin = edge();
        float bannerHeight = Math.min(getHeight() - 2 * margin,
                Math.max(92f * scale, getHeight() * .20f));
        boolean collapsed = NavigationRepository.get().collapsed();
        float height = collapsed ? bannerHeight * .48f
                : nav.distance.isEmpty() ? bannerHeight * .78f : bannerHeight;
        float width = Store.FULL.equals(mode) ? getWidth() * .62f
                : getWidth() - 2 * margin;
        if (collapsed) width = Math.min(getWidth() - 2 * margin, 240f * scale);
        float left = collapsed ? margin : (getWidth() - width) / 2f;
        float top = margin - (1f - alpha) * 20f * scale;
        navigationBounds.set(left, top, left + width, top + height);
        RectF r = navigationBounds;
        int save = canvas.saveLayerAlpha(r.left - 8, r.top - 8,
                r.right + 8, r.bottom + 8, Math.round(alpha * 255));
        Path shape = new Path();
        shape.addRoundRect(r, height * .24f, height * .24f, Path.Direction.CW);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colours.surfaceContainerHigh);
        paint.setShadowLayer(Math.max(1f, height * .04f), 0, height * .015f, 0x40000000);
        canvas.drawPath(shape, paint);
        paint.clearShadowLayer();
        float pad = Math.min(18f * scale, height * .14f);
        float diameter = Math.min(height - 2 * pad, 64f * scale);
        float cx = r.left + pad + diameter / 2f;
        float cy = r.centerY();
        paint.setColor(colours.primaryContainer);
        canvas.drawCircle(cx, cy, diameter / 2f, paint);
        if (nav.arrow != null) {
            float half = diameter * .34f;
            paint.setColor(0xFFFFFFFF);
            paint.setFilterBitmap(true);
            paint.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    colours.onSurface, android.graphics.PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(nav.arrow, null,
                    new RectF(cx - half, cy - half, cx + half, cy + half), paint);
            paint.setColorFilter(null);
        } else {
            float half = diameter * .22f;
            paint.setColor(colours.onSurface);
            paint.setStrokeWidth(Math.max(2f, 3f * scale));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(cx, cy + half, cx, cy - half, paint);
            canvas.drawLine(cx, cy - half, cx - half * .7f, cy - half * .3f, paint);
            canvas.drawLine(cx, cy - half, cx + half * .7f, cy - half * .3f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        float x = cx + diameter / 2f + pad;
        float titleSize = Math.min(28f * scale, height * (collapsed ? .42f : .25f));
        float etaWidth = collapsed || nav.eta.isEmpty() ? 0f : width * .25f;
        float textWidth = Math.max(1f, r.right - pad * 1.4f - etaWidth - x);
        if (collapsed) {
            text(canvas, nav.distance.isEmpty() ? nav.instruction : nav.distance,
                    x, r.centerY() + titleSize * .35f, titleSize, colours.onSurface,
                    Fonts.semibold(getContext()), textWidth);
        } else {
            // Distance is the headline with the instruction under it; without a distance
            // the instruction itself is the headline. The block is centred vertically.
            boolean hasDistance = !nav.distance.isEmpty();
            float labelSize = Math.min(19f * scale, titleSize * .72f);
            TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            label.setColor(hasDistance ? colours.onSurfaceVariant : colours.onSurface);
            label.setTypeface(hasDistance ? Fonts.regular(getContext()) : Fonts.semibold(getContext()));
            label.setTextSize(hasDistance ? labelSize : Math.min(titleSize * .86f, 24f * scale));
            StaticLayout lines = StaticLayout.Builder.obtain(nav.instruction, 0,
                            nav.instruction.length(), label, Math.max(1, Math.round(textWidth)))
                    .setIncludePad(false).setMaxLines(hasDistance ? 1 : 2)
                    .setEllipsize(TextUtils.TruncateAt.END).build();
            float headline = hasDistance ? titleSize * 1.2f : 0f;
            float blockTop = r.centerY() - (headline + lines.getHeight()) / 2f;
            if (hasDistance) {
                text(canvas, nav.distance, x, blockTop + titleSize, titleSize,
                        colours.onSurface, Fonts.semibold(getContext()), textWidth);
            }
            canvas.save();
            canvas.clipRect(x, r.top, x + textWidth, r.bottom - pad);
            canvas.translate(x, blockTop + headline);
            lines.draw(canvas);
            canvas.restore();
            if (etaWidth > 0f) {
                float etaSize = Math.min(labelSize, 18f * scale);
                Paint etaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                etaPaint.setTypeface(Fonts.semibold(getContext()));
                etaPaint.setTextSize(etaSize);
                float etaX = r.right - pad * 1.4f
                        - Math.min(etaWidth, etaPaint.measureText(nav.eta));
                float instructionCenter = blockTop + headline + lines.getHeight() / 2f;
                float etaBaseline = instructionCenter
                        - (etaPaint.ascent() + etaPaint.descent()) / 2f;
                text(canvas, nav.eta, etaX, etaBaseline, etaSize,
                        colours.primary, Fonts.semibold(getContext()), etaWidth);
            }
            float stroke = Math.max(2f, 2.5f * scale);
            float lineY = r.bottom - stroke * 1.4f;
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor((colours.primary & 0x00FFFFFF) | 0x4D000000);
            canvas.drawLine(r.left + pad, lineY, r.right - pad, lineY, paint);
            paint.setColor(colours.primary);
            float trackLength = width - 2 * pad;
            float activeLength = Math.min(trackLength, Math.max(stroke,
                    trackLength * Math.max(0f, Math.min(1f, nav.progress))));
            canvas.drawLine(r.left + pad, lineY,
                    r.left + pad + activeLength, lineY, paint);
        }
        paint.setAlpha(255);
        canvas.restoreToCount(save);
    }

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
    private String clockDisplayTime;
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
    private BoardLayout.Card selectedCard;
    private BoardLayout.Card renderingCard;
    private java.util.function.Consumer<BoardLayout.Card> selectionListener;
    private Runnable editStartListener;
    private boolean editorLayout;
    private boolean editorTap;
    public boolean isEditorTap() { return editorTap; }
    public BoardLayout getBoardLayout() { return layout; }
    public void setEditorLayout(BoardLayout value) {
        editorLayout = true; layout = value;
        if (!layout.cards.contains(selectedCard)) { selectedCard = null; }
        cancelLayoutTransition(); invalidateStatic();
    }
    public void setSelectionListener(java.util.function.Consumer<BoardLayout.Card> listener) { selectionListener = listener; }
    public void setEditStartListener(Runnable listener) { editStartListener = listener; }
    public void selectCard(BoardLayout.Card card) { selectedCard = card; invalidateStatic(); }
    private boolean option(BoardLayout.Card card, String key) { return card == null || !"false".equals(card.options.get(key)); }
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
    private long displayLyricsDuration = Long.MIN_VALUE;
    private String lyricStyleLocale;
    private boolean lyricsVisible;
    private boolean lyricsLoading;
    private long lyricsPosition;
    private QuranRepository.Document quranDocument;
    private String quranEdition = QuranRepository.EN_SAHIH;
    private QuranMetadata displayedQuran;
    private QuranMetadata outgoingQuran;
    private long quranTransitionAt;
    private boolean quranApproximate;
    private QuranTimingRepository.Timing quranTiming;
    private String quranTimingKey = "";
    private boolean quranTimingLoading;
    private long quranTimingRetryAt;
    private String quranDocumentKey = "";
    private boolean quranDocumentLoading;
    private long quranDocumentRetryAt;
    private String[] quranVerses;
    private Typeface quranTypeface;
    private final Map<String, StaticLayout> quranLayouts = new HashMap<>();

    public boolean isQuranTransitioning() {
        return lyricsVisible && media != null && displayedQuran != null
                && outgoingQuran != null
                && SystemClock.uptimeMillis() - quranTransitionAt < 320;
    }
    private Bitmap staticLayer;
    private boolean staticDirty = true;
    private BoardStaticSignature staticSignature;
    private Runnable frameRequestHook;
    private boolean frameScheduled;
    private boolean frameDelayed;
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
    private int artworkTileGeneration;
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
        frameDelayed = false;
        if (!isAttachedToWindow() || getVisibility() != VISIBLE
                || frameRequestHook != null) {
            return;
        }
        if (isMediaPlaying() && !isTransitioning() && !isNavigationTransitioning()) {
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
        displayLyricsDuration = media == null ? 0 : media.duration;
        displayLyrics = document == null ? null : document.estimated(displayLyricsDuration);
        displayedLyric = Integer.MIN_VALUE;
        clearLyricBlocks();
        // A tall music card moves its artwork up when lyrics arrive.
        invalidateStatic();
    }

    /** Tall music cards get a header (art + title) with lyrics, or a centred title, below. */
    private boolean tallMediaCard(RectF r) {
        return r.height() > r.width() * .55f && r.height() > 180f * Math.max(1f, unit() * .5f);
    }

    /** Timed song lyrics to show inside a tall music card (not Quran, not the lyrics page). */
    private boolean cardLyrics() {
        return !lyricsVisible && displayLyrics != null && displayLyrics.isTimed()
                && !displayLyrics.isEmpty() && media != null && !media.isQuranSession;
    }

    private RectF cardLyricsArea(RectF r) {
        float art = Math.min(r.height() * .26f, r.width() * .24f);
        float timeline = mediaControlLayout(r).playPause.top - 5f * unit();
        return new RectF(r.left, r.top + art + 12f * unit(), r.right, timeline - 10f * unit());
    }

    private void drawCardLyrics(Canvas canvas, RectF r) {
        RectF area = cardLyricsArea(r);
        int index = lyricsLineIndex();
        LyricsDocument.Line[] lines = displayLyrics.lines;
        if (area.height() < 20f * unit() || lines.length == 0) return;
        // Intro, before the first line: the same layout, dimmed until the singing starts.
        boolean intro = index < 0;
        if (intro) index = 0;
        float size = Math.min(17f * unit(), area.height() * .22f);
        TextPaint current = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        current.setTypeface(Fonts.semibold(getContext()));
        current.setTextSize(size);
        current.setColor(intro ? SAGE : BRASS);
        StaticLayout now = StaticLayout.Builder.obtain(lines[index].text, 0,
                        lines[index].text.length(), current, Math.max(1, Math.round(area.width())))
                .setIncludePad(false).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build();
        float y = area.centerY() - now.getHeight() / 2f - size * .5f;
        canvas.save();
        canvas.clipRect(area);
        canvas.translate(area.left, y);
        now.draw(canvas);
        canvas.restore();
        if (index + 1 < lines.length) {
            text(canvas, lines[index + 1].text, area.left, y + now.getHeight() + size * 1.25f,
                    size * .78f, SAGE, Fonts.regular(getContext()), area.width());
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
        quranVerses = null;
        QuranCatalog.Entry surah = document == null ? null : QuranCatalog.get(document.surah);
        if (surah != null) {
            quranVerses = new String[surah.ayahCount + 1];
            for (int i = 1; i < quranVerses.length; i++) {
                quranVerses[i] = document.arabic(i);
            }
        }
        updateQuranPosition(media);
        invalidateStatic();
    }

    /** Return true when the visible ayah changed, so the car surface can draw a transition. */
    public boolean updateQuranPosition(WidgetData.MediaSnapshot snapshot) {
        QuranMetadata source = snapshot == null ? null : snapshot.quran;
        int metadataAyah = source == null ? 0 : source.ayah;
        int timedAyah = 0;
        int estimatedAyah = 0;
        if (source != null && metadataAyah == 0 && snapshot.position >= 0) {
            long position = snapshot.estimatedPositionMs();
            if (quranTiming != null) {
                timedAyah = quranTiming.ayahAt(position);
            }
            if (quranTiming == null && quranVerses != null && quranDocument != null
                    && quranDocument.surah == source.surah.number && snapshot.duration > 0) {
                estimatedAyah = QuranPosition.estimate(quranVerses, position, snapshot.duration);
            }
        }
        int ayah = QuranPosition.resolve(metadataAyah, quranTiming != null,
                timedAyah, estimatedAyah);
        boolean approximate = QuranPosition.isApproximate(metadataAyah,
                quranTiming != null, estimatedAyah);
        QuranMetadata next = source == null ? null : source.withAyah(ayah);
        if ((next == null) != (displayedQuran == null)
                || next != null && (next.surah.number != displayedQuran.surah.number
                || next.ayah != displayedQuran.ayah)) {
            outgoingQuran = next != null && displayedQuran != null
                    && next.surah.number == displayedQuran.surah.number
                    ? displayedQuran : null;
            displayedQuran = next;
            quranTransitionAt = SystemClock.uptimeMillis();
            quranApproximate = approximate;
            invalidateStatic();
            scheduleFrame();
            return true;
        }
        if (quranApproximate != approximate) {
            quranApproximate = approximate;
            invalidateStatic();
        }
        return false;
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
        requestOverlayDraw();
    }

    private void requestOverlayDraw() {
        invalidate();
        if (frameRequestHook != null) {
            frameRequestHook.run();
        }
    }

    private boolean controlPlaying() {
        return optimisticPlaying != null ? optimisticPlaying
                : controlState != null && WidgetData.isActive(controlState.getState());
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
        if (control == null || !WidgetData.supportsMediaAction(controlState,
                controlAction(control))) {
            return false;
        }
        pressedControl = control;
        String action = controlAction(control);
        boolean wasPlaying = controlPlaying();
        try {
            PlaybackState current = media == null || media.controller == null
                    ? null : media.controller.getPlaybackState();
            if (current != null) {
                wasPlaying = WidgetData.isActive(current.getState());
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

    /** Left edge of the music card's controls and timeline: full width in tall cards. */
    private float mediaControlsLeft(RectF r) {
        if (tallMediaCard(r)) return r.left;
        return r.left + Math.min(r.height() * .95f, r.width() * .45f) + 10f * unit();
    }

    private MediaControlLayout mediaControlLayout(RectF r) {
        if (tallMediaCard(r)) {
            // Tall cards: previous / play / next across the card at a fixed, density-aware size
            // (48-64 dp), so a dense head unit never drops to a lone off-centre play button.
            float dp = controlDpScale();
            float size = Math.max(48f * dp, Math.min(64f * dp,
                    Math.min(r.height() * .15f, r.width() / 5f)));
            return MediaControlLayout.row(r.left, r.right, r.bottom, size);
        }
        return MediaControlLayout.create(mediaControlsLeft(r), r.top,
                r.right, r.bottom, controlDpScale(), r.width(), r.height());
    }

    /**
     * Lyrics page areas: [0] song column card, [1] lyrics card, [2] song content, [3] lyrics content.
     * Beside Android Auto's panel the song column sits under the panel, as the weather card does.
     */
    private RectF[] lyricsAreas() {
        float w = getWidth();
        float h = getHeight();
        float pad = 6f * unit();
        if (panelColumn()) {
            float gutter = panelLeft * 2f;
            RectF song = new RectF(panelLeft, Math.min(panelBottom + gutter, h - panelTop - 1f),
                    panelRight, h - panelTop);
            RectF text = new RectF(Math.max(panelRight + gutter, panelStableLeft), panelTop,
                    w - panelLeft, h - panelTop);
            float inner = Math.max(pad * 2f, Math.min(song.width(), song.height()) * .06f);
            return new RectF[] {song, text,
                    new RectF(song.left + inner, song.top + inner, song.right - inner, song.bottom - inner),
                    new RectF(text.left + inner * 1.5f, text.top + inner, text.right - inner, text.bottom - inner)};
        }
        float inset = Math.max(12f * unit(), w * .035f);
        RectF content = new RectF(inset, inset, w - inset, h - inset);
        if (w < h * .85f) {
            // Narrow (one-third) panel: stack a compact song card on top and the lyrics below,
            // instead of squeezing two columns side by side.
            // Cards fill the panel with one gap; content sits an even 20 dp inside each card.
            // The song card is just tall enough for the cover row, timeline and controls,
            // so the lyrics card gets the rest.
            float dp = controlDpScale();
            float inner = 20f * dp;
            float gap = 12f * dp;
            RectF panel = new RectF(edge(), edge(), w - edge(), h - edge());
            float songHeight = Math.min(panel.height() * .34f,
                    panel.width() * .20f + 56f * dp + 30f * dp + 2f * inner);
            RectF song = new RectF(panel.left, panel.top, panel.right, panel.top + songHeight);
            RectF text = new RectF(panel.left, song.bottom + gap, panel.right, panel.bottom);
            return new RectF[] {song, text,
                    new RectF(song.left + inner, song.top + inner, song.right - inner,
                            song.bottom - inner),
                    new RectF(text.left + inner, text.top + inner, text.right - inner,
                            text.bottom - inner)};
        }
        float leftWidth = Math.min(content.height() * .50f, content.width() * .31f);
        RectF songContent = new RectF(content.left, content.top, content.left + leftWidth, content.bottom);
        RectF textContent = new RectF(content.left + leftWidth
                + Math.max(16f * unit(), content.width() * .05f), content.top, content.right, content.bottom);
        RectF song = new RectF(content.left - pad, content.top - pad, songContent.right + pad,
                content.bottom + pad);
        RectF text = new RectF(song.right + pad, content.top - pad, content.right + pad,
                content.bottom + pad);
        return new RectF[] {song, text, songContent, textContent};
    }

    private MediaControlLayout lyricsControlLayout() {
        RectF song = lyricsAreas()[2];
        // Same row as the music card: previous / play / next across the song card.
        float dp = controlDpScale();
        float size = Math.max(44f * dp, Math.min(60f * dp,
                Math.min(song.height() * .22f, song.width() / 5f)));
        return MediaControlLayout.row(song.left, song.right, song.bottom, size);
    }

    private MediaControlLayout currentControlLayout() {
        if (media == null || media.controller == null
                || !WidgetData.supportsMediaAction(controlState, WidgetData.MEDIA_PLAY_PAUSE)) {
            return null;
        }
        if (lyricsVisible) {
            return lyricsControlLayout();
        }
        BoardLayout.Card card = findCard("media");
        return card == null || !option(card, "show_controls") ? null : mediaControlLayout(mediaContent(visualBounds(card)));
    }

    private void drawMediaControls(Canvas canvas) {
        MediaControlLayout targets = currentControlLayout();
        drawnControls = targets;
        drawnControlBounds = targets == null ? null
                : lyricsVisible ? new RectF(0, 0, getWidth(), getHeight())
                : findCard("media") == null ? null : visualBounds(findCard("media"));
        if (targets == null) {
            return;
        }
        if (lyricsVisible && media.duration > 0) {
            RectF song = lyricsAreas()[2];
            float inset = song.left;
            float width = song.width();
            float y = targets.playPause.top - 5f * unit();
            float progress = Math.max(0f, Math.min(1f,
                    (float) media.estimatedPositionMs() / media.duration));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.5f, unit()));
            paint.setColor((BRASS & 0x00FFFFFF) | 0x4D000000);
            canvas.drawLine(inset, y, inset + width, y, paint);
            paint.setColor(BRASS);
            canvas.drawLine(inset, y, inset + width * progress, y, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        int save = canvas.save();
        if (!lyricsVisible) {
            BoardLayout.Card card = findCard("media");
            RectF bounds = visualBounds(card);
            float alpha = transitionAlpha(card.id, SystemClock.uptimeMillis());
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
        if (frameRequestHook != null || !isAttachedToWindow()
                || getVisibility() != VISIBLE) {
            return;
        }
        boolean urgent = isMediaPlaying() || isTransitioning()
                || isQuranTransitioning() || isNavigationTransitioning();
        if (frameScheduled) {
            if (!urgent || !frameDelayed) return;
            Choreographer.getInstance().removeFrameCallback(frameCallback);
            frameScheduled = false;
        }
        frameScheduled = true;
        frameDelayed = !urgent;
        if (urgent) {
            Choreographer.getInstance().postFrameCallback(frameCallback);
        } else {
            Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 1000);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startNavigation();
        scheduleFrame();
    }

    @Override
    protected void onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameScheduled = false;
        frameDelayed = false;
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
            artworkTileGeneration = 0;
        }
        stopNavigation();
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
        String next = Store.NARROW.equals(value) ? Store.NARROW
                : Store.SPLIT.equals(value) ? Store.SPLIT : Store.FULL;
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
        if (dragging == null && !editorLayout) {
            layout = panelColumn()
                    ? BoardLayout.panelLayout(Store.layout(getContext(), Store.SPLIT, 0))
                    : Store.layout(getContext(), mode);
        }
        updateNavigation();
        words = AppLocale.wrap(getContext());
        Date now = new Date();
        time = android.text.format.DateFormat.getTimeFormat(words).format(now);
        date = new SimpleDateFormat("EEE d MMM", AppLocale.locale(getContext())).format(now);
        prayer = WidgetData.prayerSnapshot(words);
        weather = WeatherRepository.snapshot(words);
        if (findCard("audio") != null) audio = AudioSwitchClient.read(getContext());
        media = WidgetData.mediaSnapshot(words);
        requestQuranPositionData();
        updateQuranPosition(media);
        if (lyrics != null && !lyrics.isTimed()
                && displayLyricsDuration != (media == null ? 0 : media.duration)) {
            long oldDuration = displayLyrics == null || displayLyrics.lines.length == 0 ? 0
                    : displayLyrics.lines[displayLyrics.lines.length - 1].timeMs;
            displayLyricsDuration = media == null ? 0 : media.duration;
            displayLyrics = lyrics.estimated(displayLyricsDuration);
            clearLyricBlocks();
            if (oldDuration != (displayLyrics.lines.length == 0 ? 0
                    : displayLyrics.lines[displayLyrics.lines.length - 1].timeMs)) {
                displayedLyric = Integer.MIN_VALUE;
            }
        }
        bindMediaControls();
        background = BackgroundStore.load(getContext());
        int previousSeed = colours == null ? 0 : colours.seed;
        colours = BoardColours.current(getContext(), media == null ? null : media.art);
        String localeTag = AppLocale.locale(getContext()).toLanguageTag();
        if (previousSeed != colours.seed || !localeTag.equals(lyricStyleLocale)) {
            clearLyricBlocks();
            lyricStyleLocale = localeTag;
        }
        BoardStaticSignature nextSignature = staticSignature();
        if (!nextSignature.equals(staticSignature)) {
            staticSignature = nextSignature;
            invalidateStatic();
        }
        scheduleFrame();
    }

    private BoardStaticSignature staticSignature() {
        BoardStaticSignature.Builder key = BoardStaticSignature.builder()
                .add(mode).add(layout.toJson())
                .add(AppLocale.locale(getContext()).toLanguageTag())
                .add(time).add(date)
                .add(BackgroundStore.identity(getContext()))
                .add(background == null ? 0 : System.identityHashCode(background))
                .add(colours == null ? 0 : colours.seed)
                .add(carNight).add(editing).add(lyricsVisible)
                .add(selectedCard == null ? null : selectedCard.id)
                .add(pageIndex).add(pageCount)
                .add(quranEdition).add(quranApproximate).add(lyricsLoading)
                .add(displayedQuran == null ? null : displayedQuran.heading())
                .add(displayedQuran == null ? 0 : displayedQuran.ayah)
                .add(System.identityHashCode(quranDocument));
        if (prayer == null) {
            key.add(false);
        } else {
            key.add(true).add(prayer.zoneName).add(prayer.zoneCode)
                    .add(prayer.countdown).add(prayer.status).add(prayer.updated);
            WidgetData.PrayerRow[] rows = prayer.rows;
            key.add(rows == null ? -1 : rows.length);
            if (rows != null) {
                for (WidgetData.PrayerRow row : rows) {
                    if (row == null) {
                        key.add(false);
                    } else {
                        key.add(true).add(row.name).add(row.time).add(row.past).add(row.next);
                    }
                }
            }
        }
        if (weather == null) {
            key.add(false);
        } else {
            key.add(true).add(Double.doubleToLongBits(weather.tempC))
                    .add(weather.condition).add(weather.rainProbability)
                    .add(weather.observedAtMillis).add(weather.place)
                    .add(weather.weatherCode).add(weather.isDay)
                    .add(weather.observedAtMillis > 0 && System.currentTimeMillis()
                            - weather.observedAtMillis > 60 * 60 * 1000L);
        }
        if (media == null) {
            key.add(false);
        } else {
            key.add(true).add(media.title).add(media.artist).add(media.album)
                    .add(media.duration).add(media.playing).add(media.hasTrack)
                    .add(media.controller != null).add(media.isQuranSession)
                    .add(media.recitationId)
                    .add(media.art == null ? 0 : System.identityHashCode(media.art))
                    .add(media.art == null || media.art.isRecycled()
                            ? 0 : media.art.getGenerationId());
        }
        key.add(audio.installed).add(audio.enabled).add(audio.helpers)
                .add(audio.car).add(audio.phone).add(audio.status);
        return key.build();
    }

    private void requestQuranPositionData() {
        QuranMetadata source = media.quran;
        if (source == null) {
            quranTimingKey = "";
            quranTiming = null;
            quranTimingLoading = false;
            quranDocumentKey = "";
            quranDocumentLoading = false;
            return;
        }
        int surah = source.surah.number;
        String documentKey = String.valueOf(surah);
        if (!documentKey.equals(quranDocumentKey)) {
            quranDocumentKey = documentKey;
            quranDocumentLoading = false;
            quranDocumentRetryAt = 0;
            if (quranDocument == null || quranDocument.surah != surah) {
                setQuran(null, quranEdition);
            }
        }
        if (quranDocument == null && !quranDocumentLoading
                && SystemClock.elapsedRealtime() >= quranDocumentRetryAt) {
            quranDocumentLoading = true;
            QuranRepository.request(getContext(), surah, result -> {
                if (!documentKey.equals(quranDocumentKey)) return;
                quranDocumentLoading = false;
                quranDocumentRetryAt = result == null
                        ? SystemClock.elapsedRealtime() + 30_000 : Long.MAX_VALUE;
                if (result != null) {
                    setQuran(result, quranEdition);
                    if (frameRequestHook != null) frameRequestHook.run();
                }
            });
        }
        String timingKey = surah + ":" + media.recitationId;
        if (!timingKey.equals(quranTimingKey)) {
            quranTimingKey = timingKey;
            quranTiming = null;
            quranTimingLoading = false;
            quranTimingRetryAt = 0;
        }
        if (media.recitationId == 0 || quranTiming != null || quranTimingLoading
                || SystemClock.elapsedRealtime() < quranTimingRetryAt) return;
        quranTimingLoading = true;
        QuranTimingRepository.request(getContext(), media.recitationId, surah, result -> {
            if (!timingKey.equals(quranTimingKey)) return;
            quranTimingLoading = false;
            quranTiming = result;
            quranTimingRetryAt = result == null
                    ? SystemClock.elapsedRealtime() + 30_000 : Long.MAX_VALUE;
            updateQuranPosition(media);
            if (frameRequestHook != null) frameRequestHook.run();
        });
    }

    private float unit() {
        return Math.max(.25f, Math.min(getWidth() / 390f, getHeight() / 150f));
    }

    private float edge() {
        return Math.max(2f, 8f * unit());
    }

    // Android Auto's content panel column (weather template), in board coordinates; 0 = none.
    private float panelLeft, panelRight, panelTop, panelBottom, panelStableLeft;

    /** Lays the board around Android Auto's panel: weather under the panel, prayer and music right. */
    public void setPanelColumn(float left, float right, float top, float bottom, float stableLeft) {
        if (left == panelLeft && right == panelRight && top == panelTop && bottom == panelBottom
                && stableLeft == panelStableLeft) return;
        panelLeft = left;
        panelRight = right;
        panelTop = top;
        panelBottom = bottom;
        panelStableLeft = stableLeft;
        CarDiagnostics.log(getContext(), "Board panel column left=" + left + " right=" + right
                + " bottom=" + bottom + " stableLeft=" + stableLeft);
        refresh();
    }

    private boolean panelColumn() {
        return panelRight > 0f;
    }

    private RectF panelBounds(BoardLayout.Card card, float width, float height) {
        // Mirror Android Auto's panel: its outer margins on every side, one gutter between cards.
        float gutter = panelLeft * 2f;
        float top = panelTop;
        float bottom = height - panelTop;
        float right = width - panelLeft;
        float rightLeft = Math.max(panelRight + gutter, panelStableLeft);
        // One row line for both columns: below the panel on the left, prayer/music on the
        // right. A taller panel (more tiles) moves the line down, up to half the height.
        float split = Math.min(top + (bottom - top) * .5f,
                Math.max(top + (bottom - top) * .25f, panelBottom + gutter / 2f));
        switch (card.type) {
            case "prayer":
                return new RectF(rightLeft, top, right, split - gutter / 2f);
            case "media":
                return new RectF(rightLeft, split + gutter / 2f, right, bottom);
            case "weather":
                return new RectF(panelLeft, Math.max(split + gutter / 2f, panelBottom + gutter),
                        panelRight, bottom);
            default:
                return new RectF();
        }
    }

    private RectF bounds(BoardLayout.Card c) {
        if (panelColumn()) return panelBounds(c, getWidth(), getHeight());
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
            positions.put(card.id, new BoardTransition.Rect(visible.left + frameX,
                    visible.top + frameY, visible.right + frameX, visible.bottom + frameY));
            Float previousAlpha = transitionStartAlpha.get(card.id);
            alphas.put(card.id, !isTransitioning() ? 1f
                    : previousAlpha == null ? BoardTransition.incomingAlpha(previousProgress)
                    : previousAlpha + (1f - previousAlpha) * previousProgress);
            Bitmap bitmap = null;
            if (isTransitioning()) {
                bitmap = renderCardBitmap(card, visible);
            }
            if (bitmap == null) {
                bitmap = transitionSource.get(card.id);
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
                sources.put(card.id, bitmap);
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
        if (panelColumn()) return panelBounds(card, width, height);
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
        BoardTransition.Rect from = transitionFrom.get(card.id);
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
        BoardTransition.Rect from = transitionFrom.get(card.id);
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
            drawNavigation(canvas);
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
        if (staticDirty) {
            long staticStarted = SystemClock.uptimeMillis();
            staticLayer.eraseColor(0);
            drawStatic(new Canvas(staticLayer));
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
        drawNavigation(canvas);
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
        for (BoardLayout.Card c : layout.cards) {
            RectF r = bounds(c);
            if (r.width() <= 0 || r.height() <= 0) {
                continue;
            }
            drawCard(canvas, c, r);
        }
        if (editing) { drawGrid(canvas); }
        drawPageDots(canvas);
    }

    private int pageIndex;
    private int pageCount;

    /** Car only: dots in the bottom margin show which page is on screen. */
    public void setPageIndicator(int page, int count) {
        if (page != pageIndex || count != pageCount) {
            pageIndex = page;
            pageCount = count;
            invalidateStatic();
        }
    }

    private void drawPageDots(Canvas canvas) {
        if (pageCount < 2 || editing || panelColumn()) return;
        float radius = Math.max(1.5f, edge() * .28f);
        float gap = radius * 3.2f;
        float y = getHeight() - edge() / 2f;
        float x = getWidth() / 2f - gap * (pageCount - 1) / 2f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < pageCount; i++) {
            paint.setColor(i == pageIndex ? colours.primary : withAlpha(colours.onSurface, .35f));
            canvas.drawCircle(x + i * gap, y, radius, paint);
        }
    }

    private void drawCard(Canvas canvas, BoardLayout.Card c, RectF r) {
        canvas.save();
        renderingCardType = c.id;
        renderingCard = c;
        drawCardSurface(canvas, c, r);
        canvas.clipPath(cardPath(c.type, r));
        float pad = Math.max(4.5f * unit(),
                Math.min(18f * unit(), Math.min(r.width(), r.height()) * .15f));
        if (WidgetType.fromId(c.type).padding == WidgetType.Padding.MEDIA) {
            pad = Math.max(2f * unit(), Math.min(r.width(), r.height()) * .08f);
        } else if (panelColumn()) {
            // Rounded panel-style cards: an even inset matching the corner.
            pad = 20f * controlDpScale();
        } else if (WidgetType.fromId(c.type).padding == WidgetType.Padding.PRAYER) {
            float radius = BoardLayout.prayerRadius(r.width(), r.height());
            pad = Math.min(Math.min(r.width(), r.height()) * .28f,
                    Math.max(3f * unit(), radius * .40f));
        }
        RectF content = new RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad);
        if (WidgetType.fromId(c.type) == WidgetType.WEATHER) {
            float aspect = r.width() / r.height();
            if (aspect > .75f && aspect < 1.4f && !panelColumn()) {
                float half = Math.min(r.width(), r.height()) / (float) Math.sqrt(8) * .90f;
                content = new RectF(r.centerX() - half, r.centerY() - half, r.centerX() + half, r.centerY() + half);
            }
        }
        WidgetType.fromId(c.type).drawer.draw(this, canvas, content);
        if (editing) {
            drawHandle(canvas, r);
            if (selectedCard == c) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(2f, 2f * unit()));
                paint.setColor(BRASS); canvas.drawRoundRect(r, 8f * unit(), 8f * unit(), paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
        canvas.restore();
        renderingCardType = null;
        renderingCard = null;
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
            if (layout.cards.stream().anyMatch(card -> card.id.equals(entry.getKey()))) {
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
            float alpha = transitionAlpha(card.id, now);
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
        // The captured bitmap already contains the instance shape.
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
        if (panelColumn()) {
            // Beside Android Auto's panel every card takes the panel's own corner, so the
            // tiles and the board read as one set.
            float corner = 20f * controlDpScale();
            path.addRoundRect(bounds, corner, corner, Path.Direction.CW);
            return path;
        }
        if (WidgetType.fromId(type).shape == WidgetType.Shape.WEATHER) {
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
        } else if (WidgetType.fromId(type).shape == WidgetType.Shape.PRAYER) {
            float prayerRadius = BoardLayout.prayerRadius(bounds.width(), bounds.height());
            path.addRoundRect(bounds, prayerRadius, prayerRadius, Path.Direction.CW);
        } else if (WidgetType.fromId(type).shape == WidgetType.Shape.MEDIA) {
            path.addRoundRect(bounds, radius * .22f, radius * .22f, Path.Direction.CW);
        } else {
            path.addRoundRect(bounds, radius * .28f, radius * .28f, Path.Direction.CW);
        }
        return path;
    }

    private void drawCardSurface(Canvas canvas, BoardLayout.Card card, RectF bounds) {
        String choice = card.options.get("color");
        int color;
        if ("theme".equals(choice)) {
            WidgetType widget = WidgetType.fromId(card.type);
            color = widget == WidgetType.CLOCK || widget == WidgetType.SHORTCUTS
                    ? colours.surfaceContainerHigh
                    : widget == WidgetType.PRAYER ? colours.primaryContainer
                    : widget == WidgetType.MEDIA || widget == WidgetType.AUDIO
                    ? colours.secondaryContainer : colours.tertiaryContainer;
        } else {
            int fixed = choice == null ? 0 : WidgetType.fixedColor(choice);
            color = fixed != 0 ? fixed : colours.surfaceContainerHigh;
        }
        drawGlassSurface(canvas, cardPath(card.type, bounds), bounds, color);
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
        RectF[] areas = lyricsAreas();
        RectF songArea = areas[2];
        if (staticPass) {
            Path leftShape = new Path();
            Path rightShape = new Path();
            float radius = Math.min(18f * unit(), areas[1].height() * .12f);
            leftShape.addRoundRect(areas[0], radius, radius, Path.Direction.CW);
            rightShape.addRoundRect(areas[1], radius, radius, Path.Direction.CW);
            drawGlassSurface(canvas, leftShape, areas[0], colours.surfaceContainerHigh);
            drawGlassSurface(canvas, rightShape, areas[1], colours.surfaceContainerHigh);
        }
        RectF content = new RectF(songArea);
        content.union(areas[3]);
        canvas.save();
        canvas.clipRect(content);
        MediaControlLayout controls = lyricsControlLayout();
        float titleReserve = Math.min(20f * unit(), songArea.height() * .15f) * .80f * 2.5f
                + 16f * unit();
        // A wide song card (narrow panel, or under Android Auto's panel) puts the cover beside
        // the title; a tall one stacks the cover above it.
        boolean wide = songArea.width() > songArea.height() * 1.15f;
        float dp = controlDpScale();
        float artSize = wide
                ? Math.max(0f, Math.min(songArea.width() * .22f,
                        controls.playPause.top - songArea.top - 30f * dp))
                : Math.max(0f, Math.min(Math.min(songArea.height() * .50f, songArea.width()),
                        controls.playPause.top - songArea.top - titleReserve));
        float titleSize = wide ? Math.max(18f * dp, artSize * .20f)
                : Math.min(20f * unit(), songArea.height() * .15f) * .80f;
        // Wide card: cover and title/singer as one group, centred in the card.
        float artLeft = songArea.left;
        float textGap = 16f * dp;
        if (wide && media != null && media.controller != null) {
            Paint measure = new Paint(Paint.ANTI_ALIAS_FLAG);
            measure.setTypeface(Fonts.semibold(getContext()));
            measure.setTextSize(titleSize);
            String title = media.quran == null ? media.title : media.quran.surah.transliteration;
            float textWidth = measure.measureText(title == null ? "" : title);
            measure.setTextSize(titleSize * .72f);
            textWidth = Math.max(textWidth, measure.measureText(
                    media.artist == null ? "" : media.artist));
            float group = Math.min(songArea.width(), artSize + textGap + textWidth);
            artLeft = songArea.centerX() - group / 2f;
        }
        RectF artBounds = new RectF(artLeft, songArea.top, artLeft + artSize,
                songArea.top + artSize);
        if (staticPass && artSize > 0f) {
            drawLyricsArt(canvas, artBounds);
        }
        float textLeft = wide ? artBounds.right + textGap : songArea.left;
        float leftWidth = songArea.right - textLeft;
        float titleY = wide ? artBounds.centerY() - titleSize * .1f
                : artBounds.bottom + 8f * unit() + titleSize;
        if (staticPass && media != null) {
            if (media.controller == null) {
                drawEmptyMediaText(canvas, new RectF(songArea.left, artBounds.bottom,
                        songArea.right, songArea.bottom), titleSize, titleSize * .72f);
            } else {
                text(canvas, media.quran == null ? media.title
                            : media.quran.surah.transliteration, textLeft, titleY, titleSize,
                        CREAM, Fonts.semibold(getContext()), leftWidth);
                text(canvas, media.artist, textLeft,
                        titleY + titleSize * 1.2f, titleSize * .72f,
                        SAGE, Fonts.regular(getContext()), leftWidth);
                if (displayedQuran != null && quranApproximate) {
                    String label = "ms".equals(AppLocale.locale(getContext()).getLanguage())
                            ? "Anggaran" : "Approximate";
                    text(canvas, label, textLeft, titleY + titleSize * 2.05f,
                            titleSize * .55f, SAGE, Fonts.regular(getContext()), leftWidth);
                }
            }
        }
        RectF lyricArea = new RectF(areas[3]);
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
        // Density floors keep lyrics readable in the narrow panel, where unit() is small.
        float dp = controlDpScale();
        float mainSize = Math.max(26f * dp, Math.min(25f * unit(), area.height() * .22f) * .70f);
        float sideSize = Math.max(19f * dp, Math.min(16f * unit(), area.height() * .14f) * .70f);
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
        QuranMetadata current = displayedQuran;
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
        // Current ayah, marker, translation, then the NEXT ayah dimmed as a preview.
        String next = quranDocument == null ? null
                : quranDocument.text(QuranRepository.QURAN_UTHMANI, metadata.ayah + 1);
        if (next == null) next = "";
        String arabic = quranDocument == null ? DASH
                : quranDocument.text(QuranRepository.QURAN_UTHMANI, metadata.ayah);
        String translation = quranDocument == null ? DASH
                : quranDocument.text(quranEdition, metadata.ayah);
        drawQuranText(canvas, arabic,
                new RectF(area.left, area.top, area.right, area.top + h * .47f),
                Math.min(34f * unit(), h * .25f), colours.primary, true, 4);
        // Ayah marker: an eight-pointed star (two overlaid squares) with the number centred,
        // drawn as geometry so it never shifts with the Arabic font's glyph metrics.
        float radius = Math.max(14f * controlDpScale(), Math.min(16f * unit(), h * .075f));
        float centerX = area.centerX();
        float centerY = area.top + h * .55f;
        Path star = new Path();
        float side = radius * 1.42f;
        star.addRect(centerX - side / 2f, centerY - side / 2f, centerX + side / 2f,
                centerY + side / 2f, Path.Direction.CW);
        Path diamond = new Path();
        diamond.moveTo(centerX, centerY - side * .707f);
        diamond.lineTo(centerX + side * .707f, centerY);
        diamond.lineTo(centerX, centerY + side * .707f);
        diamond.lineTo(centerX - side * .707f, centerY);
        diamond.close();
        star.op(diamond, Path.Op.UNION);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(colours.primary, .14f));
        canvas.drawPath(star, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, 1.2f * controlDpScale()));
        paint.setColor(colours.primary);
        canvas.drawPath(star, paint);
        paint.setStyle(Paint.Style.FILL);
        Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG);
        digits.setTypeface(Fonts.semibold(getContext()));
        digits.setColor(colours.primary);
        digits.setTextAlign(Paint.Align.CENTER);
        String number = SurahArtText.arabicIndic(metadata.ayah);
        digits.setTextSize(radius * (number.length() > 2 ? .62f : .8f));
        Paint.FontMetrics metrics = digits.getFontMetrics();
        canvas.drawText(number, centerX, centerY - (metrics.ascent + metrics.descent) / 2f, digits);
        drawQuranText(canvas, translation,
                new RectF(area.left, area.top + h * .62f, area.right, area.top + h * .80f),
                Math.min(16f * unit(), h * .12f), colours.onSurface, false, 3);
        if (!next.isEmpty()) {
            // A preview: readable size, one line, cut with an ellipsis rather than shrunk.
            if (quranTypeface == null) quranTypeface = getResources().getFont(R.font.amiri_quran);
            TextPaint preview = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            preview.setTypeface(quranTypeface);
            preview.setColor(withAlpha(colours.onSurface, .45f));
            preview.setTextSize(Math.max(20f * controlDpScale(), Math.min(22f * unit(), h * .08f)));
            StaticLayout line = StaticLayout.Builder.obtain(next, 0, next.length(), preview,
                            Math.max(1, (int) area.width()))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setTextDirection(TextDirectionHeuristics.RTL)
                    .setMaxLines(1).setEllipsize(TextUtils.TruncateAt.END)
                    .setIncludePad(false).build();
            float top = area.top + h * .91f - line.getHeight() / 2f;
            canvas.save();
            canvas.translate(area.left, top);
            line.draw(canvas);
            canvas.restore();
        }
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

    private Bitmap mediaArtwork(int size) {
        if (media == null) {
            return null;
        }
        if (media.isQuranSession && media.quran != null) {
            return SurahArt.render(getContext(), media.quran.surah.number, size,
                    colours.primaryContainer);
        }
        if (media.art != null && !media.art.isRecycled()) {
            return media.art;
        }
        return null;
    }

    private void drawLyricsArt(Canvas canvas, RectF bounds) {
        Bitmap art = mediaArtwork(Math.round(bounds.width()));
        Path shape = ExpressiveShapes.cookie(bounds);
        paint.setColor(art == null ? colours.primaryContainer : colours.surfaceContainerHighest);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(shape, paint);
        if (art == null) {
            if (musicNote == null) {
                musicNote = getContext().getDrawable(R.drawable.ic_music_note).mutate();
            }
            int half = Math.round(bounds.width() * .21f);
            musicNote.setBounds(Math.round(bounds.centerX()) - half,
                    Math.round(bounds.centerY()) - half,
                    Math.round(bounds.centerX()) + half,
                    Math.round(bounds.centerY()) + half);
            musicNote.setTint(colours.onPrimaryContainer);
            musicNote.draw(canvas);
            return;
        }
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

    void drawClock(Canvas canvas, RectF r) {
        String time = new SimpleDateFormat("12h".equals(renderingCard.options.get("format"))
                ? "h:mm a" : "HH:mm", AppLocale.locale(getContext())).format(new Date());
        clockDisplayTime = time;
        boolean showDate = option(renderingCard, "show_date");
        boolean showHijri = "true".equals(renderingCard.options.get("show_hijri"));
        String date = showDate ? this.date : "";
        String hijri = showHijri ? hijriDate() : "";

        Typeface clockFace = Fonts.medium(getContext());
        float size = fit(time, r.height() * .60f, r.width(), clockFace);
        float dateSize = fit(date.length() > hijri.length() ? date : hijri, Math.min(18f * unit(), r.height() * .16f),
                r.width(), Fonts.regular(getContext()));
        paint.setTypeface(clockFace);
        paint.setTextSize(size);
        Paint.FontMetrics timeMetrics = paint.getFontMetrics();
        paint.setTypeface(Fonts.regular(getContext()));
        paint.setTextSize(dateSize);
        Paint.FontMetrics dateMetrics = paint.getFontMetrics();
        float spacing = 5f * unit();
        float lineHeight = spacing + dateMetrics.descent - dateMetrics.ascent;
        float blockHeight = timeMetrics.descent - timeMetrics.ascent
                + (date.isEmpty() ? 0 : lineHeight) + (hijri.isEmpty() ? 0 : lineHeight);
        if (blockHeight > r.height()) {
            float scale = r.height() / blockHeight;
            size *= scale; dateSize *= scale; spacing *= scale;
            paint.setTypeface(clockFace); paint.setTextSize(size);
            timeMetrics = paint.getFontMetrics();
            paint.setTypeface(Fonts.regular(getContext())); paint.setTextSize(dateSize);
            dateMetrics = paint.getFontMetrics();
            lineHeight = spacing + dateMetrics.descent - dateMetrics.ascent;
            blockHeight = timeMetrics.descent - timeMetrics.ascent
                    + (date.isEmpty() ? 0 : lineHeight) + (hijri.isEmpty() ? 0 : lineHeight);
        }
        float blockTop = r.top + (r.height() - blockHeight) / 2f;
        float y = blockTop - timeMetrics.ascent;
        text(canvas, time, r.left, y, size, CREAM,
                clockFace, r.width());
        float dateY = y + timeMetrics.descent + spacing - dateMetrics.ascent;
        if (!date.isEmpty()) text(canvas, date, r.left, dateY, dateSize, SAGE,
                Fonts.regular(getContext()), r.width());
        if (!hijri.isEmpty()) {
            text(canvas, hijri, r.left, dateY + (date.isEmpty() ? 0 : lineHeight),
                    dateSize, SAGE, Fonts.regular(getContext()), r.width());
        }
    }

    /** Tile rectangles in draw order; shared by drawing and car hit-testing. */
    private RectF[] shortcutTiles(RectF r, int count) {
        int cols = Shortcut.columns(count, r.width(), r.height());
        int rows = (count + cols - 1) / cols;
        float gap = Math.max(3f * unit(), Math.min(r.width(), r.height()) * .04f);
        float w = (r.width() - gap * (cols - 1)) / cols;
        float h = (r.height() - gap * (rows - 1)) / rows;
        RectF[] tiles = new RectF[count];
        for (int i = 0; i < count; i++) {
            float x = r.left + (i % cols) * (w + gap);
            float y = r.top + (i / cols) * (h + gap);
            tiles[i] = new RectF(x, y, x + w, y + h);
        }
        return tiles;
    }

    private RectF contentBounds(BoardLayout.Card card) {
        RectF r = visualBounds(card);
        float pad = Math.max(4.5f * unit(),
                Math.min(18f * unit(), Math.min(r.width(), r.height()) * .15f));
        return new RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad);
    }

    void drawShortcuts(Canvas canvas, RectF r) {
        Shortcut[] items = Shortcut.values();
        RectF[] tiles = shortcutTiles(r, items.length);
        for (int i = 0; i < items.length; i++) {
            RectF t = tiles[i];
            float radius = Math.min(t.width(), t.height()) * .32f;
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(colours.primary, .16f));
            canvas.drawRoundRect(t, radius, radius, paint);
            float size = Math.min(t.height() * .30f, 20f * unit());
            centeredText(canvas, words.getString(items[i].label), t.centerX(),
                    t.centerY() + size * .35f, size, colours.onSurface,
                    Fonts.semibold(getContext()), t.width() * .88f);
        }
    }

    /** Returns the tapped shortcut action, or null. Only the car calls this. */
    public String hitShortcut(float x, float y) {
        for (BoardLayout.Card card : layout.cards) {
            if (!"shortcuts".equals(card.type) || !visualBounds(card).contains(x, y)) continue;
            Shortcut[] items = Shortcut.values();
            RectF[] tiles = shortcutTiles(contentBounds(card), items.length);
            for (int i = 0; i < items.length; i++) {
                if (tiles[i].contains(x, y)) return items[i].action;
            }
            return "";
        }
        return null;
    }

    private AudioSwitchClient.State audio = AudioSwitchClient.MISSING;

    /** Row 0 is the separate toggle; rows 1 and 2 are car and phone levels with -/+ halves. */
    private RectF[] audioRows(RectF r) {
        float gap = Math.max(3f * unit(), r.height() * .04f);
        float h = (r.height() - 2 * gap) / 3f;
        RectF[] rows = new RectF[3];
        for (int i = 0; i < 3; i++) rows[i] = new RectF(r.left, r.top + i * (h + gap), r.right, r.top + i * (h + gap) + h);
        return rows;
    }

    void drawAudio(Canvas canvas, RectF r) {
        float size = Math.min(r.height() * .11f, 18f * unit());
        if (!audio.installed) {
            stateText(canvas, words.getString(R.string.audio_missing), r, size, colours.onSurfaceVariant);
            return;
        }
        RectF[] rows = audioRows(r);
        Typeface semibold = Fonts.semibold(getContext());
        float radius = rows[0].height() * .5f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(audio.enabled ? colours.primary : withAlpha(colours.onSurface, .12f));
        canvas.drawRoundRect(rows[0], radius, radius, paint);
        String toggle = words.getString(audio.enabled ? R.string.audio_on : R.string.audio_off);
        if (audio.enabled && !audio.helpers) toggle = words.getString(R.string.audio_helpers_stopped);
        centeredText(canvas, toggle, rows[0].centerX(), rows[0].centerY() + size * .35f, size,
                audio.enabled ? colours.onPrimary : colours.onSurface, semibold, rows[0].width() * .9f);
        String[] labels = {words.getString(R.string.audio_car, audio.car), words.getString(R.string.audio_phone, audio.phone)};
        for (int i = 0; i < 2; i++) {
            RectF row = rows[i + 1];
            float button = row.height();
            paint.setColor(withAlpha(colours.primary, .16f));
            canvas.drawCircle(row.left + button / 2f, row.centerY(), button * .45f, paint);
            canvas.drawCircle(row.right - button / 2f, row.centerY(), button * .45f, paint);
            centeredText(canvas, "\u2212", row.left + button / 2f, row.centerY() + size * .4f, size * 1.3f,
                    colours.onSurface, semibold, button);
            centeredText(canvas, "+", row.right - button / 2f, row.centerY() + size * .4f, size * 1.3f,
                    colours.onSurface, semibold, button);
            centeredText(canvas, labels[i], row.centerX(), row.centerY() + size * .35f, size,
                    colours.onSurface, Fonts.regular(getContext()), row.width() - 2.2f * button);
        }
    }

    /** Returns "toggle", "car-", "car+", "phone-", "phone+", "" for the card body, or null. */
    public String hitAudio(float x, float y) {
        BoardLayout.Card card = findCard("audio");
        if (card == null || !visualBounds(card).contains(x, y)) return null;
        if (!audio.installed) return "";
        RectF[] rows = audioRows(contentBounds(card));
        if (rows[0].contains(x, y)) return "toggle";
        for (int i = 1; i < 3; i++) {
            if (!rows[i].contains(x, y)) continue;
            String key = i == 1 ? "car" : "phone";
            if (x < rows[i].left + rows[i].height()) return key + "-";
            if (x > rows[i].right - rows[i].height()) return key + "+";
        }
        return "";
    }

    public AudioSwitchClient.State audioState() {
        return audio;
    }

    private String hijriDate() {
        java.time.chrono.HijrahDate day = java.time.chrono.HijrahDate.now();
        String[] months = "ms".equals(AppLocale.locale(getContext()).getLanguage())
                ? new String[] {"Muharam", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir", "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijah"}
                : new String[] {"Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Shaban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah"};
        return day.get(java.time.temporal.ChronoField.DAY_OF_MONTH) + " "
                + months[day.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) - 1] + " "
                + day.get(java.time.temporal.ChronoField.YEAR) + " H";
    }

    void drawPrayer(Canvas canvas, RectF r) {
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
        WidgetData.PrayerRow[] rows = prayer == null ? null : prayer.rows;
        float rowTimeSize = 11f * unit();
        float rowNameSize = rowTimeSize * .82f;
        float rowHeight = prayerRowHeight(rowNameSize, rowTimeSize);
        float minColumnWidth = prayerColumnWidth(rows, rowNameSize, rowTimeSize);
        float summaryHeight = Math.min(r.height(), Math.max(28f * unit(),
                2.5f * Math.min(14f * unit(), r.height() * .22f)));
        int columns = !"next".equals(renderingCard.options.get("view")) && rows != null && rows.length == 5
                ? BoardLayout.prayerRowColumns(r.width(),
                        r.height() - summaryHeight - 3f * unit(),
                        minColumnWidth, rowHeight) : 0;
        float rowTop = r.bottom - rowHeight * (columns == 3 ? 2 : 1);
        float limit = columns == 0 ? r.bottom : rowTop - 3f * unit();
        float star = Math.min(28f * unit(), Math.max(10f * unit(), r.height() * .36f));
        star = Math.min(star, Math.min(r.width() * .15f, (limit - r.top) * .62f));
        float x = r.left;
        boolean showStar = next != null && r.width() >= star * 3.2f
                && r.height() >= star * 1.5f;
        if (showStar) x += star + Math.max(5f * unit(), star * .25f);
        float width = r.right - x;
        String title = next == null ? DASH : next.name;
        if (next == null) {
            float stateTop = r.top;
            if (!Store.FULL.equals(mode) && columns != 0) {
                float groupGap = 14f * unit() * .6f;
                float rowsHeight = rowHeight * (columns == 3 ? 2 : 1);
                float headerHeight = Math.min(summaryHeight,
                        Math.max(1f, r.height() - rowsHeight - groupGap));
                stateTop = r.centerY() - (headerHeight + groupGap + rowsHeight) / 2f;
                limit = stateTop + headerHeight;
                rowTop = limit + groupGap;
            }
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
            stateText(canvas, stateMessage, new RectF(x, stateTop, r.right, limit),
                    Math.min(16f * unit(), r.height() * .22f), stale ? DIM : SOFT);
            if (columns != 0) {
                drawPrayerRows(canvas, r, rows, columns, rowTop,
                        rowHeight, rowNameSize, rowTimeSize);
            }
            return;
        }
        float available = Math.max(1f, limit - r.top);
        float titleSize = Math.min(32f * unit(), Math.min(r.height() * .40f,
                available * .57f)) * .80f;
        float countSize = Math.min(18f * unit(), Math.min(r.height() * .22f,
                available * .42f)) * .85f;
        titleSize = fit(title + "  " + next.time, titleSize, width,
                Fonts.semibold(getContext()));
        paint.setTypeface(Fonts.semibold(getContext()));
        paint.setTextSize(titleSize);
        Paint.FontMetrics titleMetrics = paint.getFontMetrics();
        paint.setTypeface(Fonts.medium(getContext()));
        paint.setTextSize(countSize);
        Paint.FontMetrics countMetrics = paint.getFontMetrics();
        float gap = 2f * unit();
        float blockHeight = titleMetrics.descent - titleMetrics.ascent + gap
                + countMetrics.descent - countMetrics.ascent;
        if (blockHeight > available) {
            float scale = available / blockHeight;
            titleSize *= scale;
            countSize *= scale;
            gap *= scale;
            paint.setTypeface(Fonts.semibold(getContext()));
            paint.setTextSize(titleSize);
            titleMetrics = paint.getFontMetrics();
            paint.setTypeface(Fonts.medium(getContext()));
            paint.setTextSize(countSize);
            countMetrics = paint.getFontMetrics();
            blockHeight = titleMetrics.descent - titleMetrics.ascent + gap
                    + countMetrics.descent - countMetrics.ascent;
        }
        float headerTop = r.top + (available - blockHeight) / 2f;
        if (!Store.FULL.equals(mode) && columns != 0) {
            float headerHeight = Math.max(blockHeight, showStar ? star : 0f);
            float rowsHeight = rowHeight * (columns == 3 ? 2 : 1);
            float groupGap = Math.min((titleMetrics.descent - titleMetrics.ascent) * .6f,
                    Math.max(0f, r.height() - headerHeight - rowsHeight));
            float groupTop = r.centerY() - (headerHeight + groupGap + rowsHeight) / 2f;
            headerTop = groupTop + (headerHeight - blockHeight) / 2f;
            rowTop = groupTop + headerHeight + groupGap;
        }
        if (showStar) {
            drawStar(canvas, r.left + star / 2f,
                    Store.FULL.equals(mode) ? r.top + available / 2f
                    : headerTop + blockHeight / 2f, star, true);
        }
        float titleY = headerTop - titleMetrics.ascent;
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
        float countdownY = titleY + titleMetrics.descent + gap - countMetrics.ascent;
        if (option(renderingCard, "show_countdown")) {
        text(canvas, displayCountdown, x, countdownY, countSize,
                stale ? DIM : withAlpha(colours.onPrimaryContainer, .8f),
                Fonts.medium(getContext()), width);
        if (prayer != null && prayer.zoneCode != null && !prayer.zoneCode.isEmpty()) {
            paint.setTypeface(Fonts.regular(getContext()));
            paint.setTextSize(countSize);
            float suffixX = x + paint.measureText(displayCountdown + "  ");
            float zoneSize = Math.min(11f * unit(), countSize);
            if (suffixX < r.right - 8f * unit()) {
                text(canvas, "\u00b7 " + prayer.zoneCode, suffixX, countdownY, zoneSize,
                        SAGE, Fonts.regular(getContext()), r.right - suffixX);
            }
        }
        }
        if (columns != 0) {
            drawPrayerRows(canvas, r, rows, columns, rowTop,
                    rowHeight, rowNameSize, rowTimeSize);
        }
    }

    private float prayerRowHeight(float nameSize, float timeSize) {
        paint.setTypeface(Fonts.medium(getContext()));
        paint.setTextSize(nameSize);
        Paint.FontMetrics nameMetrics = paint.getFontMetrics();
        paint.setTypeface(Fonts.regular(getContext()));
        paint.setTextSize(timeSize);
        Paint.FontMetrics timeMetrics = paint.getFontMetrics();
        return nameMetrics.descent - nameMetrics.ascent
                + timeMetrics.descent - timeMetrics.ascent
                + timeSize * .28f + 2f * unit();
    }

    private float prayerColumnWidth(WidgetData.PrayerRow[] rows,
            float nameSize, float timeSize) {
        float widest = 0f;
        if (rows != null) {
            for (WidgetData.PrayerRow row : rows) {
                paint.setTypeface(Fonts.medium(getContext()));
                Fonts.cardText(paint);
                paint.setTextSize(nameSize);
                widest = Math.max(widest, paint.measureText(
                        row.name == null ? DASH : row.name));
                paint.setTypeface(Fonts.regular(getContext()));
                Fonts.cardText(paint);
                paint.setTextSize(timeSize);
                widest = Math.max(widest, paint.measureText(
                        row.time == null ? DASH : row.time));
            }
        }
        return widest + 4f * unit();
    }

    private void drawPrayerRows(Canvas canvas, RectF r, WidgetData.PrayerRow[] rows,
            int columns, float top, float rowHeight, float nameSize, float timeSize) {
        float col = r.width() / columns;
        paint.setTypeface(Fonts.medium(getContext()));
        paint.setTextSize(nameSize);
        Paint.FontMetrics nameMetrics = paint.getFontMetrics();
        paint.setTypeface(Fonts.regular(getContext()));
        paint.setTextSize(timeSize);
        Paint.FontMetrics timeMetrics = paint.getFontMetrics();
        float gap = timeSize * .28f;
        float blockHeight = nameMetrics.descent - nameMetrics.ascent + gap
                + timeMetrics.descent - timeMetrics.ascent;
        for (int i = 0; i < 5; i++) {
            WidgetData.PrayerRow row = rows[i];
            int line = columns == 3 && i >= 3 ? 1 : 0;
            float center = line == 1
                    ? r.centerX() + (i - 3.5f) * col
                    : r.left + (i + .5f) * col;
            float lineTop = top + line * rowHeight;
            float nameY = lineTop + (rowHeight - blockHeight) / 2f - nameMetrics.ascent;
            float timeY = nameY + nameMetrics.descent + gap - timeMetrics.ascent;
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
                && artworkTileSource == art
                && artworkTileGeneration == art.getGenerationId()) {
            return artworkTile;
        }
        if (artworkTile != null && !artworkTile.isRecycled()) {
            artworkTile.recycle();
        }
        final int size = 384;
        artworkTile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        artworkTileSource = art;
        artworkTileGeneration = art.getGenerationId();
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

    private android.graphics.drawable.Drawable musicNote;

    void drawMedia(Canvas canvas, RectF r) {
        boolean tall = tallMediaCard(r) && media != null && media.controller != null;
        boolean header = tall && cardLyrics();
        float artworkSize = header ? Math.min(r.height() * .26f, r.width() * .24f)
                : Math.min(r.height() * .95f, r.width() * .45f);
        if (tall && !header) artworkSize = Math.min(r.height() * .40f, r.width() * .45f);
        float artworkTop = header || tall ? r.top : r.centerY() - artworkSize / 2f;
        // Tall card without lyrics: the cover sits top centre.
        float artworkLeft = tall && !header ? r.centerX() - artworkSize / 2f : r.left;
        RectF artwork = new RectF(artworkLeft, artworkTop,
                artworkLeft + artworkSize, artworkTop + artworkSize);
        Path artShape = cookiePath(artwork);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setShadowLayer(artworkSize * .06f, 0,
                artworkSize * .02f, 0x59000000);
        canvas.drawPath(artShape, paint);
        paint.clearShadowLayer();
        Bitmap art = mediaArtwork(Math.round(artworkSize));
        if (art != null) {
            paint.setShader(null);
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(255);
            paint.setFilterBitmap(true);
            canvas.drawBitmap(artworkTile(art), null, artwork, paint);
        } else {
            // Empty state: accent-tinted shape with the Material music note, not a grey placeholder.
            paint.setColor(colours.primaryContainer);
            canvas.drawPath(artShape, paint);
            if (musicNote == null) {
                musicNote = getContext().getDrawable(R.drawable.ic_music_note).mutate();
            }
            int half = Math.round(artworkSize * .21f);
            musicNote.setBounds(Math.round(artwork.centerX()) - half,
                    Math.round(artwork.centerY()) - half,
                    Math.round(artwork.centerX()) + half,
                    Math.round(artwork.centerY()) + half);
            musicNote.setTint(colours.onPrimaryContainer);
            musicNote.draw(canvas);
        }
        float x = artwork.right + 10f * unit();
        float width = Math.max(1f, r.right - x);
        String title = media == null || media.title == null ? DASH
                : displayedQuran == null ? media.title : displayedQuran.heading();
        String artist = media == null || media.artist == null ? "" : media.artist;
        boolean playing = media != null && media.playing;
        boolean empty = media == null || media.controller == null;
        float titleSize = Math.min(19f * unit(), r.height() * .19f) * .64f;
        float artistSize = Math.min(13f * unit(), r.height() * .14f) * .70f;
        if (empty) {
            drawEmptyMediaText(canvas, new RectF(x, r.top, r.right, r.bottom),
                    titleSize, artistSize);
            return;
        }
        MediaControlLayout controls = mediaControlLayout(r);
        if (header) {
            // Lyrics below: title and artist sit beside the artwork at the top.
            float size = Math.min(22f * unit(), artworkSize * .26f);
            float block = size * 1.25f + size * .8f;
            float top = artwork.centerY() - block / 2f;
            text(canvas, title, x, top + size, size, CREAM, Fonts.semibold(getContext()), width);
            text(canvas, artist, x, top + size * 1.25f + size * .8f, size * .72f, SAGE,
                    Fonts.regular(getContext()), width);
            return;
        }
        if (tall) {
            // No lyrics: title and artist centred across the card, between the cover and timeline.
            x = r.left;
            width = r.width();
            float size = Math.min(26f * unit(), r.height() * .09f);
            TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            big.setTypeface(Fonts.semibold(getContext()));
            big.setTextSize(size);
            big.setColor(playing ? CREAM : SAGE);
            StaticLayout name = StaticLayout.Builder.obtain(title, 0, title.length(), big,
                            Math.max(1, Math.round(width)))
                    .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false).setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END).build();
            float bottom = controls.playPause.top - 20f * unit();
            float block = name.getHeight() + size * 1.3f;
            float top = artwork.bottom + (bottom - artwork.bottom - block) / 2f;
            canvas.save();
            canvas.translate(x, top);
            name.draw(canvas);
            canvas.restore();
            Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
            small.setTypeface(Fonts.regular(getContext()));
            small.setTextSize(size * .7f);
            String shown = TextUtils.ellipsize(artist, new TextPaint(small), width,
                    TextUtils.TruncateAt.END).toString();
            text(canvas, shown, x + (width - small.measureText(shown)) / 2f,
                    top + name.getHeight() + size * 1.05f, size * .7f, SAGE,
                    Fonts.regular(getContext()), width);
            return;
        }
        if (MediaControlLayout.isCompact(r.height(), width, controlDpScale())) {
            float compactSize = Math.min(19f * unit(), r.height() * .25f);
            float titleBottom = Math.min(controls.playPause.top - 3f * unit(),
                    r.top + compactSize * 1.3f);
            text(canvas, title, x, Math.max(r.top + compactSize, titleBottom), compactSize,
                    playing ? CREAM : SAGE, Fonts.semibold(getContext()), width);
            return;
        }
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
        if (displayedQuran != null && quranApproximate) {
            float labelY = artistY + artistSize * 1.15f;
            if (labelY < controls.playPause.top - 3f * unit()) {
                String label = "ms".equals(AppLocale.locale(getContext()).getLanguage())
                        ? "Anggaran" : "Approximate";
                text(canvas, label, x, labelY, artistSize * .72f, SAGE,
                        Fonts.regular(getContext()), width);
            }
        }
    }

    private void drawEmptyMediaText(Canvas canvas, RectF bounds, float titleSize,
            float artistSize) {
        String title = media == null ? words.getString(R.string.media_nothing_playing)
                : media.title;
        String subtitle = media == null ? words.getString(R.string.media_start_music)
                : media.artist;
        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTypeface(Fonts.semibold(getContext()));
        titlePaint.setTextSize(titleSize);
        titlePaint.setColor(CREAM);
        Fonts.cardText(titlePaint);
        TextPaint subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        subtitlePaint.setTypeface(Fonts.regular(getContext()));
        subtitlePaint.setTextSize(artistSize);
        subtitlePaint.setColor(SAGE);
        Fonts.cardText(subtitlePaint);
        int width = Math.max(1, Math.round(bounds.width()));
        StaticLayout heading = StaticLayout.Builder.obtain(title, 0, title.length(),
                        titlePaint, width)
                .setIncludePad(false).setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END).build();
        StaticLayout hint = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length(),
                        subtitlePaint, width)
                .setIncludePad(false).setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END).build();
        float gap = artistSize * .30f;
        canvas.save();
        canvas.clipRect(bounds);
        canvas.translate(bounds.left, bounds.centerY()
                - (heading.getHeight() + gap + hint.getHeight()) / 2f);
        heading.draw(canvas);
        canvas.translate(0, heading.getHeight() + gap);
        hint.draw(canvas);
        canvas.restore();
    }

    private float waveLevel = 1f;
    private long waveLevelAt;

    /** Eases the wave height toward 1 while playing and 0 when paused, over about 300 ms. */
    private float waveLevel(boolean playing) {
        long now = SystemClock.uptimeMillis();
        float step = waveLevelAt == 0 ? 1f : Math.min(1f, (now - waveLevelAt) / 300f);
        waveLevelAt = now;
        waveLevel += ((playing ? 1f : 0f) - waveLevel) * step;
        return waveLevel;
    }

    private void drawProgress(Canvas canvas) {
        if (media == null || !media.hasTrack || media.duration <= 0) {
            return;
        }
        for (BoardLayout.Card card : layout.cards) {
            if (!"media".equals(card.type) || !option(card, "show_progress")) {
                continue;
            }
            RectF bounds = visualBounds(card);
            float pad = Math.max(2f * unit(),
                    Math.min(bounds.width(), bounds.height()) * .08f);
            RectF r = new RectF(bounds.left + pad, bounds.top + pad,
                    bounds.right - pad, bounds.bottom - pad);
            float x = mediaControlsLeft(r);
            float width = Math.max(1f, r.right - x);
            if (MediaControlLayout.isCompact(r.height(), width, controlDpScale())) continue;
            float progress = Math.max(0f, Math.min(1f,
                    (float) media.estimatedPositionMs() / media.duration));
            float lineY = option(card, "show_controls") ? mediaControlLayout(r).playPause.top - 5f * unit() : r.bottom - 3f * unit();
            canvas.save();
            float alpha = transitionAlpha(card.id, SystemClock.uptimeMillis());
            if (alpha < 1f) {
                canvas.saveLayerAlpha(bounds, Math.round(alpha * 255f));
            }
            canvas.clipPath(cardPath(card.type, bounds));
            // M3 Expressive linear wavy indicator: fixed-size wave that tapers into the line at both
            // ends, drifts while playing and flattens when paused, a gap, then the track and a stop dot.
            float stroke = Math.max(1.5f, 2.5f * unit());
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            float start = x + stroke / 2f;
            float trackEnd = r.right - stroke / 2f;
            float end = start + (trackEnd - start) * progress;
            float gap = stroke * 1.6f;
            float unplayedStart = progress <= 0f ? start : end + gap;
            if (unplayedStart < trackEnd) {
                paint.setColor((BRASS & 0x00FFFFFF) | 0x4D000000);
                canvas.drawLine(unplayedStart, lineY, trackEnd, lineY, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(BRASS);
            canvas.drawCircle(trackEnd, lineY, stroke / 2f, paint);
            if (end > start) {
                paint.setStyle(Paint.Style.STROKE);
                float amplitude = stroke * 1.25f * waveLevel(media.playing);
                float wavelength = 22f * unit();
                float taper = wavelength * .5f;
                float drift = media.playing ? (SystemClock.uptimeMillis() % 1600L) / 1600f : 0f;
                Path wave = new Path();
                wave.moveTo(start, lineY);
                float step = Math.max(1f, unit());
                for (float point = start + step; point < end; point += step) {
                    float edge = Math.min(1f, Math.min(point - start, end - point) / taper);
                    float envelope = edge * edge * (3f - 2f * edge);
                    wave.lineTo(point, lineY + (float) Math.sin(
                            ((point - start) / wavelength - drift) * Math.PI * 2)
                            * amplitude * envelope);
                }
                wave.lineTo(end, lineY);
                canvas.drawPath(wave, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            if (tallMediaCard(r) && cardLyrics()) {
                drawCardLyrics(canvas, r);
            }
            if (alpha < 1f) {
                canvas.restore();
            }
            canvas.restore();
        }
    }

    void drawWeather(Canvas canvas, RectF r) {
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
            if (option(renderingCard, "show_rain")) centeredText(canvas, words.getString(R.string.weather_rain, rain), center,
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
        if (value != null && (value.equals(time) || value.equals(clockDisplayTime))) {
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
        if (s.equals(time) || s.equals(clockDisplayTime)) {
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
        if (!editing && event.getActionMasked() == MotionEvent.ACTION_DOWN
                && navigationShown && navigationBounds.contains(event.getX(), event.getY())) {
            navigationPressed = true;
            return true;
        }
        if (navigationPressed) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                navigationPressed = false;
                hitNavigation(event.getX(), event.getY());
                performClick();
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                navigationPressed = false;
            }
            return true;
        }
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
                        selectedCard = c;
                        if (editStartListener != null) { editStartListener.run(); }
                        invalidateStatic();
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
                    layout.tryResize(dragging, startW + dx, startH + dy);
                } else {
                    layout.tryMove(dragging, startCol + dx, startRow + dy);
                }
                if (!editorLayout) Store.prefs(getContext()).edit()
                        .putString(Store.layoutKey(mode, Store.page(getContext())), layout.toJson()).apply();
                invalidateStatic();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging == null) {
                    return false;
                }
                BoardLayout.Card tapped = dragging;
                editorTap = Math.abs(event.getX() - downX) < 8 * getResources().getDisplayMetrics().density
                        && Math.abs(event.getY() - downY) < 8 * getResources().getDisplayMetrics().density;
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    dragging.col = startCol; dragging.row = startRow; dragging.w = startW; dragging.h = startH;
                }
                dragging = null;
                if (event.getActionMasked() == MotionEvent.ACTION_UP && selectionListener != null) { selectionListener.accept(tapped); }
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
