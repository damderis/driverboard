package local.driveboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Shared proportional renderer for the phone preview and car surface. */
public final class BoardView extends View {
    private static final int CREAM = 0xFFF4EFE4;
    private static final int SAGE = 0xFFA9C2BD;
    private static final int BRASS = 0xFFC8A55A;
    private static final int SOFT = 0xFFCFDCD8;
    private static final int DIM = 0xFF78918E;
    private static final String DASH = "\u2014";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BoardLayout layout = BoardLayout.defaults();
    private WidgetData.PrayerSnapshot prayer;
    private WidgetData.BatterySnapshot battery;
    private String[] media = {DASH, DASH};
    private String time = DASH;
    private String date = DASH;
    private Context words;
    private boolean editing;
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

    public void refresh() {
        if (dragging == null) {
            layout = BoardLayout.fromJson(Store.prefs(getContext()).getString("layout", ""));
        }
        words = AppLocale.wrap(getContext());
        Date now = new Date();
        time = android.text.format.DateFormat.getTimeFormat(words).format(now);
        date = new SimpleDateFormat("EEE, d MMM", AppLocale.locale(getContext())).format(now);
        prayer = WidgetData.prayerSnapshot(words);
        battery = WidgetData.battery(words);
        media = WidgetData.media(words);
        invalidate();
    }

    private float unit() {
        return Math.max(.25f, Math.min(getWidth() / 390f, getHeight() / 150f));
    }

    private float edge() {
        return Math.max(2f, 8f * unit());
    }

    private float gap() {
        return Math.max(1f, 5f * unit());
    }

    private RectF bounds(BoardLayout.Card c) {
        float e = edge();
        float g = gap();
        float cw = (getWidth() - 2 * e) / 12f;
        float ch = (getHeight() - 2 * e) / 6f;
        return new RectF(e + c.col * cw + g / 2, e + c.row * ch + g / 2,
                e + (c.col + c.w) * cw - g / 2, e + (c.row + c.h) * ch - g / 2);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Tone tone = Tone.current(getContext());
        canvas.drawColor(tone.ground);
        if (editing) {
            drawGrid(canvas);
        }
        for (BoardLayout.Card c : layout.cards) {
            RectF r = bounds(c);
            if (r.width() <= 0 || r.height() <= 0) {
                continue;
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(tone.panel);
            float radius = Math.min(8f * unit(), Math.min(r.width(), r.height()) * .16f);
            canvas.drawRoundRect(r, radius, radius, paint);
            canvas.save();
            canvas.clipRect(r);
            float pad = Math.max(3f * unit(),
                    Math.min(12f * unit(), Math.min(r.width(), r.height()) * .10f));
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

    private float small(RectF r) {
        return Math.max(5f * unit(), Math.min(10f * unit(), r.height() * .14f));
    }

    private void drawClock(Canvas canvas, RectF r) {
        float label = small(r);
        text(canvas, words.getString(R.string.board_clock)
                .toUpperCase(AppLocale.locale(getContext())), r.left, r.top + label,
                label, SAGE, Fonts.semibold(getContext()), r.width());
        float size = fit(time, Math.min(r.height() * .40f, r.width() * .48f),
                r.width(), Fonts.extraBold(getContext()));
        float dateSize = Math.min(label * 1.25f, Math.max(6f * unit(), r.height() * .15f));
        paint.setTypeface(Fonts.extraBold(getContext()));
        paint.setTextSize(size);
        Paint.FontMetrics timeMetrics = paint.getFontMetrics();
        paint.setTypeface(Fonts.regular(getContext()));
        paint.setTextSize(dateSize);
        Paint.FontMetrics dateMetrics = paint.getFontMetrics();
        float spacing = label * .45f;
        float blockHeight = timeMetrics.descent - timeMetrics.ascent + spacing
                + dateMetrics.descent - dateMetrics.ascent;
        float top = r.top + label * 1.5f;
        float blockTop = top + (r.bottom - top - blockHeight) / 2f;
        float y = blockTop - timeMetrics.ascent;
        text(canvas, time, r.left, y, size, CREAM, Fonts.extraBold(getContext()), r.width());
        float dateY = y + timeMetrics.descent + spacing - dateMetrics.ascent;
        text(canvas, date, r.left, dateY, dateSize, SOFT,
                Fonts.regular(getContext()), r.width());
    }

    private void drawPrayer(Canvas canvas, RectF r, int gridRows) {
        float label = small(r);
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
        float star = Math.min(28f * unit(), Math.max(10f * unit(), r.height() * .36f));
        star = Math.min(star, r.width() * .15f);
        float x = r.left;
        if (next != null && r.width() >= star * 3.2f && r.height() >= star * 1.5f) {
            drawStar(canvas, x + star / 2,
                    r.top + Math.min(star * .72f, r.height() * .36f), star);
            x += star + Math.max(5f * unit(), star * .25f);
        }
        float width = r.right - x;
        String zone = prayer == null ? "" : prayer.zoneCode;
        String head = words.getString(R.string.board_next_prayer)
                .toUpperCase(AppLocale.locale(getContext()));
        if (zone != null && !zone.isEmpty()) {
            head += "  \u00b7  " + zone;
        }
        text(canvas, head, x, r.top + label, label, SAGE,
                Fonts.semibold(getContext()), width);
        String title = next == null ? DASH : next.name + " " + next.time;
        if (next == null) {
            int message = prayer != null && "unavailable".equals(prayer.status)
                    ? R.string.prayer_unavailable
                    : prayer != null && "tomorrow_waiting".equals(prayer.status)
                    ? R.string.prayer_tomorrow_waiting : R.string.prayer_waiting;
            float bottom = gridRows >= 3 ? r.top + r.height() * .70f : r.bottom;
            String stateMessage = words.getString(message);
            if (stale) {
                stateMessage = words.getString(R.string.board_stale)
                        + (prayer.updated.isEmpty() ? "" : " \u00b7 "
                        + words.getString(R.string.board_updated, prayer.updated));
            }
            stateText(canvas, stateMessage, new RectF(x, r.top + label * 1.6f,
                    r.right, bottom), Math.min(13f * unit(), r.height() * .19f), stale ? DIM : SOFT);
            if (gridRows >= 3 && prayer != null && prayer.rows != null && prayer.rows.length == 5) {
                drawPrayerRow(canvas, r, prayer.rows);
            }
            return;
        }
        float titleSize = fit(title, Math.min(22f * unit(), r.height() * .28f),
                width, Fonts.extraBold(getContext()));
        float titleY = r.top + label + Math.max(titleSize * 1.18f, r.height() * .26f);
        text(canvas, title, x, titleY, titleSize, stale ? DIM : CREAM,
                Fonts.extraBold(getContext()), width);
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
        float limit = gridRows >= 3 ? r.top + r.height() * .73f : r.bottom;
        float countSize = Math.min(13f * unit(), r.height() * .19f);
        if (titleY + countSize * 1.05f <= limit) {
            text(canvas, countdown, x, titleY + countSize * 1.12f, countSize,
                    stale ? DIM : BRASS, Fonts.semibold(getContext()), width);
        }
        if (gridRows >= 3 && prayer != null && prayer.rows != null
                && prayer.rows.length == 5) {
            drawPrayerRow(canvas, r, prayer.rows);
        }
    }

    private void drawPrayerRow(Canvas canvas, RectF r, WidgetData.PrayerRow[] rows) {
        float top = r.top + r.height() * .73f;
        float col = r.width() / 5f;
        float nameSize = Math.min(10f * unit(), (r.bottom - top) * .37f);
        float timeSize = Math.min(11f * unit(), (r.bottom - top) * .42f);
        for (int i = 0; i < 5; i++) {
            WidgetData.PrayerRow row = rows[i];
            float x = r.left + i * col;
            int color = prayer != null && "stale".equals(prayer.status)
                    ? DIM : row.next ? BRASS : row.past ? DIM : SOFT;
            text(canvas, row.name, x, top + nameSize, nameSize, color,
                    Fonts.semibold(getContext()), col - 2f * unit());
            text(canvas, row.time, x,
                    Math.min(r.bottom, top + nameSize + timeSize * 1.15f), timeSize,
                    color, row.next ? Fonts.extraBold(getContext()) : Fonts.regular(getContext()),
                    col - 2f * unit());
        }
    }

    private void drawStar(Canvas canvas, float x, float y, float size) {
        paint.setColor(BRASS);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, 1.6f * unit()));
        float half = size * .34f;
        canvas.drawRect(x - half, y - half, x + half, y + half, paint);
        canvas.save();
        canvas.rotate(45f, x, y);
        canvas.drawRect(x - half, y - half, x + half, y + half, paint);
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);
    }
    private void drawMedia(Canvas canvas, RectF r) {
        float label = small(r);
        text(canvas, words.getString(R.string.board_now_playing)
                .toUpperCase(AppLocale.locale(getContext())), r.left, r.top + label,
                label, SAGE, Fonts.semibold(getContext()), r.width());
        String title = media.length > 0 ? media[0] : DASH;
        String artist = media.length > 1 ? media[1] : DASH;
        boolean state = media.length < 3 || "state".equals(media[2]);
        if (state) {
            float top = r.top + label * 1.6f;
            float middle = top + (r.bottom - top) * .65f;
            float secondary = Math.min(13f * unit(), r.height() * .2f);
            stateText(canvas, title, new RectF(r.left, top, r.right, middle), secondary, CREAM);
            stateText(canvas, artist, new RectF(r.left, middle, r.right, r.bottom), secondary, SOFT);
            return;
        }
        float titleSize = Math.min(23f * unit(), r.height() * .31f);
        float y = r.top + Math.max(label + titleSize * 1.5f, r.height() * .55f);
        text(canvas, title, r.left, y, titleSize, CREAM,
                Fonts.extraBold(getContext()), r.width());
        float secondary = Math.min(13f * unit(), r.height() * .2f);
        text(canvas, artist, r.left, Math.min(r.bottom, y + secondary * 1.35f),
                secondary, SOFT, Fonts.regular(getContext()), r.width());
    }

    private void drawBattery(Canvas canvas, RectF r) {
        float label = small(r);
        text(canvas, words.getString(R.string.board_battery)
                .toUpperCase(AppLocale.locale(getContext())), r.left, r.top + label,
                label, SAGE, Fonts.semibold(getContext()), r.width());
        boolean stale = battery != null && "stale".equals(battery.status);
        boolean valid = battery != null && battery.percent >= 0;
        if (!valid && (battery == null || battery.rangeMeters < 0)) {
            float top = r.top + label * 1.6f;
            stateText(canvas, words.getString(R.string.board_no_car_data),
                    new RectF(r.left, top, r.right, r.bottom),
                    Math.min(13f * unit(), r.height() * .18f), SOFT);
            return;
        }
        String percent = valid ? battery.percent + "%" : DASH;
        float size = fit(percent, Math.min(32f * unit(), r.height() * .40f),
                r.width(), Fonts.extraBold(getContext()));
        float y = r.top + Math.max(label + size * 1.35f, r.height() * .57f);
        text(canvas, percent, r.left, y, size, stale ? DIM : CREAM,
                Fonts.extraBold(getContext()), r.width());
        String range = battery != null && battery.rangeMeters >= 0
                ? words.getString(R.string.board_range_km,
                        Math.round(battery.rangeMeters / 1000f))
                : valid ? DASH : words.getString(R.string.board_no_car_data);
        float secondary = Math.min(13f * unit(), r.height() * .18f);
        float rangeY = stale
                ? Math.min(r.bottom - secondary * 1.1f, y + secondary * 1.2f)
                : Math.min(r.bottom, y + secondary * 1.3f);
        text(canvas, range, r.left, rangeY, secondary, stale ? DIM : SOFT,
                Fonts.regular(getContext()), r.width());
        if (stale && battery.updated != null && !battery.updated.isEmpty()) {
            text(canvas, words.getString(R.string.board_updated, battery.updated),
                    r.left, r.bottom, Math.min(9f * unit(), secondary * .72f),
                    DIM, Fonts.regular(getContext()), r.width());
        }
    }
    private void stateText(Canvas canvas, String value, RectF bounds, float size, int color) {
        if (bounds.width() < 1 || bounds.height() <= 0) {
            return;
        }
        String message = value == null || value.isEmpty() ? DASH : value;
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(color);
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
        paint.setColor(color);
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
        paint.setColor(BRASS);
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
                Store.prefs(getContext()).edit().putString("layout", layout.toJson()).apply();
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
