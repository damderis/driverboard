package local.driveboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.util.LruCache;

/** Draws square artwork; the caller owns the final cookie clipping and shadow. */
public final class SurahArt {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4096) {
        @Override protected int sizeOf(String key, Bitmap bitmap) {
            return Math.max(1, bitmap.getAllocationByteCount() / 1024);
        }
    };
    private static Typeface typeface;

    private SurahArt() {}

    public static synchronized Bitmap render(Context context, int surah, int size,
            int primaryContainer) {
        if (size <= 0 || SurahArtText.name(surah).isEmpty()) {
            return null;
        }
        int panel = Color.rgb(Math.round(Color.red(primaryContainer) * .65f),
                Math.round(Color.green(primaryContainer) * .65f),
                Math.round(Color.blue(primaryContainer) * .65f));
        final int gold = 0xFFD9B45A;
        String key = surah + ":" + size + ":" + panel + ":" + gold;
        Bitmap cached = CACHE.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        if (typeface == null) {
            typeface = context.getResources().getFont(R.font.aref_ruqaa);
        }
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(panel);
        Paint pattern = new Paint(Paint.ANTI_ALIAS_FLAG);
        pattern.setColor(gold);
        pattern.setAlpha(26);
        pattern.setStyle(Paint.Style.STROKE);
        pattern.setStrokeWidth(Math.max(.5f, size * .002f));
        float tile = size / 6f;
        float half = tile * .29f;
        for (float y = 0; y <= size + tile; y += tile) {
            for (float x = 0; x <= size + tile; x += tile) {
                canvas.drawRect(x - half, y - half, x + half, y + half, pattern);
                canvas.save();
                canvas.rotate(45f, x, y);
                canvas.drawRect(x - half, y - half, x + half, y + half, pattern);
                canvas.restore();
            }
        }
        TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        text.setTypeface(typeface);
        text.setColor(gold);
        String name = SurahArtText.name(surah);
        int width = Math.max(1, Math.round(size * .70f));
        float nameSize = size * .25f;
        StaticLayout nameLayout;
        // Fit the complete text, including Arabic font ascenders/descenders, without ellipsis.
        do {
            text.setTextSize(nameSize);
            nameLayout = layout(name, text, width);
            if (nameLayout.getLineCount() <= 2 && nameLayout.getHeight() <= size * .34f) {
                break;
            }
            nameSize *= .94f;
        } while (nameSize > size * .06f);
        float nameTop = size * .50f - nameLayout.getHeight() / 2f;
        drawLayout(canvas, nameLayout, (size - width) / 2f, nameTop);
        text.setTextSize(nameSize * .30f);
        text.setAlpha(179);
        StaticLayout above = layout("سورة", text, width);
        drawLayout(canvas, above, (size - width) / 2f,
                nameTop - above.getHeight() - size * .025f);
        text.setAlpha(255);
        text.setTextSize(size * .068f);
        float radius = size * .065f;
        float cy = Math.max(size * .72f,
                nameTop + nameLayout.getHeight() + radius + size * .025f);
        pattern.setAlpha(210);
        pattern.setStrokeWidth(Math.max(.7f, size * .003f));
        canvas.drawCircle(size / 2f, cy, radius, pattern);
        StaticLayout number = layout(SurahArtText.arabicIndic(surah), text, width);
        drawLayout(canvas, number, (size - width) / 2f, cy - number.getHeight() / 2f);
        CACHE.put(key, bitmap);
        return bitmap;
    }

    private static StaticLayout layout(String value, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(value, 0, value.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(true)
                .build();
    }

    private static void drawLayout(Canvas canvas, StaticLayout layout, float x, float y) {
        canvas.save();
        canvas.translate(x, y);
        layout.draw(canvas);
        canvas.restore();
    }
}
