package local.driveboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.view.View;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

/** Shared proportional renderer for the phone preview and car surface. */
public final class BoardView extends View {
    private static final int CREAM = 0xFFF4EFE4;
    private static final int SAGE = 0xFFA9C2BD;
    private static final int BRASS = 0xFFC8A55A;
    private static final int SOFT = 0xFFCFDCD8;
    private static final int DIM = 0xFF9DB3AF;
    private static final String DASH = "\u2014";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BoardLayout layout = BoardLayout.defaults();
    private String mode = Store.FULL;
    private WidgetData.PrayerSnapshot prayer;
    private WidgetData.BatterySnapshot battery;
    private WidgetData.MediaSnapshot media;
    private String time = DASH;
    private String date = DASH;
    private Bitmap carRender;
    private long carRenderAt = Long.MIN_VALUE;
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

    public BoardView(Context c) {
        super(c);
        refresh();
    }

    public void setEditing(boolean value, Runnable callback) {
        editing = value;
        onEdit = callback;
        invalidate();
    }

    public void setCarNight(boolean value) {
        carNight = value;
        invalidate();
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
        date = new SimpleDateFormat("EEE, d MMM", AppLocale.locale(getContext())).format(now);
        prayer = WidgetData.prayerSnapshot(words);
        battery = WidgetData.battery(words);
        media = WidgetData.mediaSnapshot(words);
        long renderAt = Store.prefs(getContext()).getLong("car_render_at", 0);
        if (renderAt != carRenderAt) {
            carRenderAt = renderAt;
            if (carRender != null) {
                carRender.recycle();
                carRender = null;
            }
            if (renderAt > 0) {
                carRender = BitmapFactory.decodeFile(
                        CarPictureStore.renderFile(getContext()).getAbsolutePath());
            }
        }
        invalidate();
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
        return new RectF(e + c.col * cw, e + c.row * ch,
                e + (c.col + c.w) * cw, e + (c.row + c.h) * ch);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Tone tone = Tone.current(getContext());
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.Style.FILL);
        paint.setFilterBitmap(true);
        paint.setShader(new LinearGradient(0, 0, 0, getHeight(),
                lighten(tone.ground, .04f), tone.ground, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);
        if (editing) {
            drawGrid(canvas);
        }
        for (BoardLayout.Card c : layout.cards) {
            RectF r = bounds(c);
            if (r.width() <= 0 || r.height() <= 0) {
                continue;
            }
            canvas.save();
            canvas.clipRect(r);
            float pad = Math.max(4.5f * unit(),
                    Math.min(18f * unit(), Math.min(r.width(), r.height()) * .15f));
            RectF content = new RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad);
            if ("clock".equals(c.type)) {
                drawClock(canvas, content);
            } else if ("prayer".equals(c.type)) {
                drawPrayer(canvas, content, c.h);
            } else if ("media".equals(c.type)) {
                drawMedia(canvas, content);
            } else if ("battery".equals(c.type)) {
                drawBattery(canvas, content);
            }
            if (editing) {
                drawHandle(canvas, r);
            }
            canvas.restore();
        }
        drawDividers(canvas);
    }

    private static int lighten(int color, float amount) {
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;
        return 0xFF000000 | ((r + Math.round((255 - r) * amount)) << 16)
                | ((g + Math.round((255 - g) * amount)) << 8)
                | (b + Math.round((255 - b) * amount));
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
        Typeface clockFace = Fonts.regular(getContext());
        float size = fit(time, r.height() * .60f, r.width(), clockFace);
        float dateSize = Math.min(18f * unit(), r.height() * .16f);
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
        int imminentMinutes = minutesUntil(next);
        boolean imminent = imminentMinutes >= 0 && imminentMinutes <= 15;
        float limit = gridRows >= 3 ? r.top + r.height() * .72f : r.bottom;
        float star = Math.min(28f * unit(), Math.max(10f * unit(), r.height() * .36f));
        star = Math.min(star, r.width() * .15f);
        float x = r.left;
        if (next != null && r.width() >= star * 3.2f && r.height() >= star * 1.5f) {
            drawStar(canvas, x + star / 2,
                    r.top + (limit - r.top) / 2f, star, imminent);
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
                available * .57f));
        float countSize = Math.min(18f * unit(), Math.min(r.height() * .22f,
                available * .42f));
        float blockHeight = titleSize + countSize * 1.12f;
        float titleY = r.top + (available - blockHeight) / 2f + titleSize;
        titleSize = fit(title + "  " + next.time, titleSize, width,
                Fonts.semibold(getContext()));
        text(canvas, title, x, titleY, titleSize, stale ? DIM : CREAM,
                Fonts.semibold(getContext()), width);
        paint.setTypeface(Fonts.semibold(getContext()));
        paint.setTextSize(titleSize);
        float timeX = x + paint.measureText(title + "  ");
        text(canvas, next.time, timeX, titleY, titleSize, stale ? DIM : CREAM,
                Fonts.regular(getContext()), r.right - timeX);
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
        String displayCountdown = imminent
                ? words.getString(R.string.board_imminent_minutes, imminentMinutes)
                : countdown;
        float countdownY = Math.min(limit, titleY + countSize * 1.12f);
        text(canvas, displayCountdown, x, countdownY, countSize,
                stale ? DIM : BRASS,
                Fonts.regular(getContext()), width);
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
        float nameSize = Math.min(10f * unit(), (r.bottom - top) * .37f);
        float timeSize = Math.min(11f * unit(), (r.bottom - top) * .42f);
        for (int i = 0; i < 5; i++) {
            WidgetData.PrayerRow row = rows[i];
            float center = r.left + (i + .5f) * col;
            int color = row.past || (prayer != null && "stale".equals(prayer.status))
                    ? DIM : CREAM;
            if (row.next) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(BRASS);
                canvas.drawCircle(center, top + (r.bottom - top) * .10f,
                        (r.bottom - top) * .03f, paint);
            }
            centeredText(canvas, row.name, center, top + (r.bottom - top) * .58f,
                    nameSize, color, Fonts.regular(getContext()), col - 2f * unit());
            centeredText(canvas, row.time, center, top + (r.bottom - top) * .91f,
                    timeSize, color, Fonts.regular(getContext()), col - 2f * unit());
        }
    }

    private void drawStar(Canvas canvas, float x, float y, float size, boolean filled) {
        paint.setColor(BRASS);
        paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, 1.6f * unit()));
        float half = size * .34f;
        canvas.drawRect(x - half, y - half, x + half, y + half, paint);
        canvas.save();
        canvas.rotate(45f, x, y);
        canvas.drawRect(x - half, y - half, x + half, y + half, paint);
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);
    }

    private int minutesUntil(WidgetData.PrayerRow next) {
        if (next == null || prayer == null || !"live".equals(prayer.status)) {
            return -1;
        }
        try {
            LocalTime now = ZonedDateTime.now(ZoneId.of("Asia/Kuala_Lumpur")).toLocalTime();
            long seconds = Duration.between(now, LocalTime.parse(next.time)).getSeconds();
            return seconds < 0 || seconds > 15 * 60 ? -1 : (int) ((seconds + 59) / 60);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }
    private void drawMedia(Canvas canvas, RectF r) {
        float artworkSize = Math.min(r.height() * .90f, r.width() * .32f);
        float artworkTop = r.centerY() - artworkSize / 2f;
        RectF artwork = new RectF(r.left, artworkTop,
                r.left + artworkSize, artworkTop + artworkSize);
        float radius = artworkSize * .12f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setShadowLayer(artworkSize * .06f, 0,
                artworkSize * .02f, 0x59000000);
        canvas.drawRoundRect(artwork, radius, radius, paint);
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
            canvas.drawRoundRect(artwork, radius, radius, paint);
            paint.setShader(null);
        } else {
            paint.setColor(Tone.current(getContext()).panel);
            canvas.drawRoundRect(artwork, radius, radius, paint);
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
        float titleSize = Math.min(19f * unit(), r.height() * .19f);
        float artistSize = Math.min(13f * unit(), r.height() * .14f);
        float textTop = r.top + r.height() * .23f;
        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTypeface(Fonts.semibold(getContext()));
        titlePaint.setTextSize(titleSize);
        titlePaint.setFontFeatureSettings("tnum");
        titlePaint.setColor(playing ? (carNight ? 0xFFD0CBC2 : CREAM) : SAGE);
        float minimumTitleSize = Math.min(titleSize, 4f * unit());
        for (int attempt = 0; attempt < 16 && titleSize > minimumTitleSize; attempt++) {
            StaticLayout fullTitle = StaticLayout.Builder.obtain(title, 0,
                            title.length(), titlePaint, Math.max(1, (int) width))
                    .setIncludePad(false)
                    .build();
            if (fullTitle.getLineCount() <= 2
                    && fullTitle.getHeight() <= r.height() * .50f) {
                break;
            }
            titleSize = Math.max(minimumTitleSize, titleSize * .88f);
            titlePaint.setTextSize(titleSize);
        }
        StaticLayout titleLayout = stateLayout(title, titlePaint, width);
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
        if (playing && media.duration > 0) {
            long position = media.position;
            if (media.updatedAt > 0) {
                position += (long) ((SystemClock.elapsedRealtime() - media.updatedAt)
                        * media.speed);
            }
            float progress = Math.max(0f, Math.min(1f,
                    (float) position / media.duration));
            float lineY = r.bottom - 2f * unit();
            paint.setStrokeWidth(2f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor((CREAM & 0x00FFFFFF) | 0x40000000);
            canvas.drawLine(x, lineY, r.right, lineY, paint);
            paint.setColor((CREAM & 0x00FFFFFF) | 0xD9000000);
            canvas.drawLine(x, lineY, x + width * progress, lineY, paint);
        }
    }

    private void drawBattery(Canvas canvas, RectF r) {
        boolean stale = battery != null && "stale".equals(battery.status);
        boolean valid = battery != null && battery.percent >= 0;
        String percent = valid ? Integer.toString(battery.percent) : DASH;
        String range = battery != null && battery.rangeMeters >= 0
                ? words.getString(R.string.board_range_km,
                        Math.round(battery.rangeMeters / 1000f))
                : valid ? DASH : words.getString(R.string.board_no_car_data);
        float secondary = Math.min(14f * unit(), r.height() * .18f);
        float size = fit(percent, Math.min(valid ? 32f * unit() : 18f * unit(),
                        r.height() * (valid ? .40f : .16f)),
                r.width(), Fonts.regular(getContext()));
        float blockHeight = size + secondary * 2.2f;
        float y = r.top + (r.height() - blockHeight) / 2f + size;
        float rangeTop = Math.min(r.bottom, y + secondary * .25f);
        RectF rangeBounds = new RectF(r.left, rangeTop, r.right,
                stale ? Math.max(rangeTop, r.bottom - secondary * .8f) : r.bottom);
        boolean showRender = carRender != null && r.height() > 60f * unit();
        if (showRender) {
            RectF imageBounds = new RectF(r.left, r.top, r.right,
                    r.top + r.height() * .53f);
            float scale = Math.min(imageBounds.width() / carRender.getWidth(),
                    imageBounds.height() / carRender.getHeight());
            float imageWidth = carRender.getWidth() * scale;
            float imageHeight = carRender.getHeight() * scale;
            float centerX = imageBounds.centerX();
            float centerY = imageBounds.centerY();
            RectF destination = new RectF(centerX - imageWidth / 2f,
                    centerY - imageHeight / 2f, centerX + imageWidth / 2f,
                    centerY + imageHeight / 2f);
            paint.setAlpha(255);
            canvas.drawBitmap(carRender, null, destination, paint);
            float valueSize = Math.min(size, r.height() * .24f);
            float valueY = r.top + r.height() * .79f;
            drawBatteryValue(canvas, percent, r.left, valueY,
                    valueSize, r.width(), valid, stale);
            stateText(canvas, range, new RectF(r.left,
                    r.top + r.height() * .80f, r.right,
                    stale ? r.bottom - secondary * .8f : r.bottom), secondary, SAGE);
            if (stale && battery.updated != null && !battery.updated.isEmpty()) {
                String updated = words.getString(R.string.board_updated, battery.updated);
                text(canvas, updated, r.left, r.bottom,
                        fit(updated, Math.min(10f * unit(), secondary * .75f), r.width(),
                                Fonts.regular(getContext())), DIM,
                        Fonts.regular(getContext()), r.width());
            }
            return;
        }
        drawBatteryValue(canvas, percent, r.left, y, size, r.width(), valid, stale);
        stateText(canvas, range, rangeBounds, secondary, SAGE);
        if (stale && battery.updated != null && !battery.updated.isEmpty()) {
            String updated = words.getString(R.string.board_updated, battery.updated);
            text(canvas, updated, r.left, r.bottom,
                    fit(updated, Math.min(10f * unit(), secondary * .75f), r.width(),
                            Fonts.regular(getContext())), DIM,
                    Fonts.regular(getContext()), r.width());
        }
    }

    private void drawBatteryValue(Canvas canvas, String value, float x, float baseline,
            float size, float width, boolean valid, boolean stale) {
        if (valid) {
            paint.setTypeface(Fonts.regular(getContext()));
            paint.setFontFeatureSettings("tnum");
            paint.setTextSize(size);
            float needed = paint.measureText(value) + size * .08f
                    + paint.measureText("%") * .50f;
            if (needed > width && needed > 0) {
                size *= width / needed;
            }
        }
        text(canvas, value, x, baseline, size, stale ? DIM : CREAM,
                Fonts.regular(getContext()), width);
        if (!valid) {
            return;
        }
        paint.setTypeface(Fonts.regular(getContext()));
        paint.setTextSize(size);
        float suffixX = x + paint.measureText(value) + size * .08f;
        text(canvas, "%", suffixX, baseline, size * .50f, SAGE,
                Fonts.regular(getContext()), Math.max(0f, x + width - suffixX));
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
        textPaint.setColor(carNight && color == CREAM ? 0xFFD0CBC2 : color);
        textPaint.setTypeface(Fonts.regular(getContext()));
        textPaint.setFontFeatureSettings("tnum");
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
                .build();
    }

    private float fit(String value, float size, float width, Typeface face) {
        paint.setTypeface(face);
        paint.setFontFeatureSettings("tnum");
        paint.setTextSize(Math.max(1f, size));
        float measured = paint.measureText(value == null ? DASH : value);
        return Math.max(1f, measured > width && measured > 0
                ? size * width / measured : size);
    }

    private void text(Canvas canvas, String value, float x, float baseline,
            float size, int color, Typeface face, float width) {
        if (width <= 0 || size <= 0) {
            return;
        }
        String s = value == null || value.isEmpty() ? DASH : value;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(carNight && color == CREAM ? 0xFFD0CBC2 : color);
        paint.setTypeface(face);
        paint.setFontFeatureSettings("tnum");
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
                invalidate();
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
