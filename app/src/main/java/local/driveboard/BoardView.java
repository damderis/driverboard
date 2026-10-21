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
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.Choreographer;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Shared proportional renderer for the phone preview and car surface. */
public final class BoardView extends View {
    private int CREAM = 0xFFF4EFE4;
    private int SAGE = 0xFFA9C2BD;
    private int BRASS = 0xFFC8A55A;
    private int SOFT = 0xFFCFDCD8;
    private int DIM = 0xFF9DB3AF;
    private static final String DASH = "\u2014";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BoardLayout layout = BoardLayout.defaults();
    private String mode = Store.FULL;
    private WidgetData.PrayerSnapshot prayer;
    private WidgetData.BatterySnapshot battery;
    private WidgetData.MediaSnapshot media;
    private String time = DASH;
    private String date = DASH;
    private Bitmap background;
    private Bitmap glassBackdrop;
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
    private boolean lyricsVisible;
    private boolean lyricsLoading;
    private long lyricsPosition;
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
        if (isMediaPlaying()) {
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
        return lyrics == null ? -1 : lyrics.lineIndexAt(currentLyricsPosition());
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

    private long currentLyricsPosition() {
        return isMediaPlaying() ? media.estimatedPositionMs() : lyricsPosition;
    }

    private void invalidateStatic() {
        staticDirty = true;
        invalidate();
    }

    void onGlassBackdropReady() {
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
        if (isMediaPlaying()) {
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
        if (staticLayer != null) {
            staticLayer.recycle();
            staticLayer = null;
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
            if ("media".equals(card.type) && bounds(card).contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    public void setMode(String value) {
        String next = Store.SPLIT.equals(value) ? Store.SPLIT : Store.FULL;
        if (!next.equals(mode)) {
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
        battery = WidgetData.battery(words);
        media = WidgetData.mediaSnapshot(words);
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
            drawTimedLyrics(canvas);
        } else {
            drawProgress(canvas);
        }
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
        paint.setColor(colours.surface);
        paint.setStyle(Paint.Style.FILL);
        paint.setFilterBitmap(true);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        glassBackdrop = GlassBackdrop.get(background, getWidth(), getHeight(), this);
        if (background != null && !background.isRecycled()) {
            drawCenterCrop(canvas, background, new RectF(0, 0, getWidth(), getHeight()));
            paint.setColor(colours.surface);
            paint.setAlpha(carNight ? 204 : 184);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setAlpha(255);
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
            canvas.save();
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
            } else if ("battery".equals(c.type)) {
                float aspect = r.width() / r.height();
                if (aspect > .75f && aspect < 1.4f) {
                    // Keep text inside the circle's inscribed square, with padding.
                    float half = Math.min(r.width(), r.height()) / (float) Math.sqrt(8);
                    half *= .90f;
                    content = new RectF(r.centerX() - half, r.centerY() - half,
                            r.centerX() + half, r.centerY() + half);
                }
                drawBattery(canvas, content);
            }
            if (editing) {
                drawHandle(canvas, r);
            }
            canvas.restore();
        }
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
        if ("battery".equals(type)) {
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
            canvas.drawBitmap(glassBackdrop, null,
                    new RectF(0, 0, getWidth(), getHeight()), paint);
        }
        paint.setColor(background == null ? (179 << 24) | (tint & 0x00FFFFFF)
                : colours.translucentContainer(tint));
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(bounds.left, bounds.top,
                bounds.right, bounds.bottom,
                0x38FFFFFF, 0x0FFFFFFF, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
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
        float artSize = Math.min(content.height() * .50f, content.width() * .31f);
        RectF artBounds = new RectF(content.left, content.top,
                content.left + artSize, content.top + artSize);
        if (staticPass) {
            drawLyricsArt(canvas, artBounds);
        }
        float leftWidth = artSize;
        float titleSize = Math.min(20f * unit(), content.height() * .15f) * .80f;
        float titleY = artBounds.bottom + 8f * unit() + titleSize;
        if (staticPass && media != null) {
            text(canvas, media.title, content.left, titleY, titleSize,
                    CREAM, Fonts.semibold(getContext()), leftWidth);
            text(canvas, media.artist, content.left,
                    titleY + titleSize * 1.2f, titleSize * .72f,
                    SAGE, Fonts.regular(getContext()), leftWidth);
        }
        float lyricLeft = artBounds.right + Math.max(16f * unit(), content.width() * .05f);
        RectF lyricArea = new RectF(lyricLeft, content.top,
                content.right, content.bottom);
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
        if (lyrics.isTimed()) {
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

    private void drawTimedLyrics(Canvas canvas) {
        if (lyrics != null && lyrics.isTimed() && !lyricsLoading && !lyrics.isEmpty()) {
            drawLyrics(canvas, false);
        }
    }

    private void drawLyricBlock(Canvas canvas, RectF area, int current,
            boolean outgoing, float offsetY, int alpha) {
        LyricBlock block = outgoing ? outgoingLyricBlock : currentLyricBlock;
        if (block == null || !block.matches(lyrics, current, area)) {
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
        int[] colors = {withAlpha(SAGE, .5f), BRASS,
                withAlpha(CREAM, .75f), withAlpha(CREAM, .48f)};
        float mainSize = Math.min(25f * unit(), area.height() * .22f) * .70f;
        float sideSize = Math.min(16f * unit(), area.height() * .14f) * .70f;
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
        return new LyricBlock(lyrics, current, area, lines, gap, total);
    }

    private void drawLyricsArt(Canvas canvas, RectF bounds) {
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
            if (index < 0 || index >= lyrics.lines.length) {
                continue;
            }
            String line = lyrics.lines[index].text;
            if (line == null || line.isEmpty()) {
                continue;
            }
            boolean current = i == 1 && hasCurrent;
            TextPaint lyricPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            lyricPaint.setColor(colors[i]);
            lyricPaint.setTypeface(current ? Fonts.semibold(getContext())
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

    private void drawMedia(Canvas canvas, RectF r) {
        float artworkSize = Math.min(r.height() * .95f, r.width() * .45f);
        float artworkTop = r.centerY() - artworkSize / 2f;
        RectF artwork = new RectF(r.left, artworkTop,
                r.left + artworkSize, artworkTop + artworkSize);
        Path artShape = ExpressiveShapes.cookie(artwork);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setShadowLayer(artworkSize * .06f, 0,
                artworkSize * .02f, 0x59000000);
        canvas.drawPath(artShape, paint);
        paint.clearShadowLayer();
        if (media != null && media.art != null && !media.art.isRecycled()) {
            Bitmap art = media.art;
            BitmapShader shader = new BitmapShader(art,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float scale = Math.max(artwork.width() / art.getWidth(),
                    artwork.height() / art.getHeight());
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate(artwork.centerX() - art.getWidth() * scale / 2f,
                    artwork.centerY() - art.getHeight() * scale / 2f);
            shader.setLocalMatrix(matrix);
            paint.setColor(0xFFFFFFFF);
            paint.setShader(shader);
            canvas.drawPath(artShape, paint);
            paint.setShader(null);
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
        float textTop = r.top + r.height() * .23f;
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
        StaticLayout titleLayout = stateLayout(wordWrappedTitle(title, titlePaint, width),
                titlePaint, width);
        canvas.save();
        canvas.translate(x, textTop);
        titleLayout.draw(canvas);
        canvas.restore();
        float artistY = Math.min(r.bottom - artistSize,
                textTop + titleLayout.getHeight() + artistSize * 1.3f);
        if (!artist.isEmpty()) {
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
            RectF bounds = bounds(card);
            float pad = Math.max(2f * unit(),
                    Math.min(bounds.width(), bounds.height()) * .08f);
            RectF r = new RectF(bounds.left + pad, bounds.top + pad,
                    bounds.right - pad, bounds.bottom - pad);
            float artworkSize = Math.min(r.height() * .95f, r.width() * .45f);
            float x = r.left + artworkSize + 10f * unit();
            float width = Math.max(1f, r.right - x);
            float progress = Math.max(0f, Math.min(1f,
                    (float) media.estimatedPositionMs() / media.duration));
            float lineY = r.bottom - 3f * unit();
            canvas.save();
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
            canvas.restore();
        }
    }

    private void drawBattery(Canvas canvas, RectF r) {
        boolean stale = battery != null && "stale".equals(battery.status);
        boolean valid = battery != null && battery.percent >= 0;
        String value = valid ? battery.percent + "%" : DASH;
        String range = battery != null && battery.rangeMeters >= 0
                ? words.getString(R.string.board_range_km,
                        Math.round(battery.rangeMeters / 1000f))
                : valid ? DASH : battery == null
                        ? words.getString(R.string.board_no_car_data)
                        : battery.shortReason(words);
        float secondary = Math.min(14f * unit(), r.height() * .18f);
        float size = fit(value, Math.min((valid ? 32f : 18f) * unit(),
                r.height() * (valid ? .40f : .18f)), r.width(),
                Fonts.regular(getContext()));
        TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setTypeface(Fonts.regular(getContext()));
        Fonts.cardText(labelPaint);
        labelPaint.setTextSize(secondary);
        labelPaint.setColor(SAGE);
        StaticLayout label = StaticLayout.Builder.obtain(range, 0, range.length(),
                        labelPaint, Math.max(1, (int) r.width()))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build();
        paint.setTypeface(Fonts.regular(getContext()));
        Fonts.cardText(paint);
        paint.setTextSize(size);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float valueHeight = metrics.descent - metrics.ascent;
        float gap = secondary * .35f;
        float updatedHeight = stale ? secondary * 1.1f : 0;
        float blockHeight = valueHeight + gap + label.getHeight() + updatedHeight;
        float top = r.centerY() - blockHeight / 2f;
        centeredText(canvas, value, r.centerX(), top - metrics.ascent, size,
                stale ? DIM : CREAM, Fonts.regular(getContext()), r.width());
        canvas.save();
        canvas.translate(r.left, top + valueHeight + gap);
        label.draw(canvas);
        canvas.restore();
        if (stale && battery.updated != null && !battery.updated.isEmpty()) {
            String updated = words.getString(R.string.board_updated, battery.updated);
            centeredText(canvas, updated, r.centerX(), top + blockHeight,
                    Math.min(10f * unit(), secondary * .75f), DIM,
                    Fonts.regular(getContext()), r.width());
        }
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
