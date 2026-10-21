package local.driveboard;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Parked-phone paint editor for a user-supplied car picture. */
public final class CarEditorActivity extends Activity {
    private static final int[] PRESETS = {0, 0xFF5E6670, Color.WHITE, Color.BLACK,
            0xFFBA3035, 0xFF3062AA, 0xFF1B8D83, 0xFFB69146};
    private static final int CREAM = 0xFFF4EFE4;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Bitmap source;
    private ImageView image;
    private TextView hint;
    private Button save;
    private Button cancel;
    private long sourceStamp;
    private int generation;
    private int tapX = -1;
    private int tapY = -1;
    private int tolerance = 25;
    private int target = PRESETS[1];
    private float brightness = 1f;
    private boolean original = true;
    private volatile boolean stopped;
    private boolean saving;
    private int ink;
    private int panel;
    private int ground;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        sourceStamp = Store.prefs(this).getLong("car_source_at", 0);
        source = BitmapFactory.decodeFile(CarPictureStore.sourceFile(this).getAbsolutePath());
        if (source == null) {
            finish();
            return;
        }
        if (Store.prefs(this).contains("car_target")) {
            target = Store.prefs(this).getInt("car_target", PRESETS[1]);
            original = target == 0;
            tolerance = Store.prefs(this).getInt("car_tolerance", 25);
            brightness = Store.prefs(this).getFloat("car_brightness", 1f);
            tapX = Math.min(source.getWidth() - 1, Math.round(Store.prefs(this)
                    .getFloat("car_tap_x", 0) * source.getWidth()));
            tapY = Math.min(source.getHeight() - 1, Math.round(Store.prefs(this)
                    .getFloat("car_tap_y", 0) * source.getHeight()));
        }
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        Tone tone = Tone.current(this);
        ground = night ? tone.ground : 0xFFEDE6DA;
        panel = night ? tone.panel : 0xFFFAF7F1;
        ink = night ? CREAM : 0xFF1D2423;
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ground);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        LinearLayout content = column();
        content.setPadding(dp(12), dp(8), dp(12), dp(20));
        scroll.addView(content);
        TextView title = label(R.string.car_editor_title, 26, true);
        title.setTypeface(Fonts.extraBold(this));
        title.setTextColor(night ? CREAM : tone.ground);
        title.setPadding(dp(8), dp(6), dp(8), dp(14));
        content.addView(title);
        image = new ImageView(this);
        image.setImageBitmap(source);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackground(round(0xFF292D2D, 14));
        image.setContentDescription(getString(R.string.car_editor_image));
        image.setOnTouchListener(this::tapImage);
        content.addView(image, new LinearLayout.LayoutParams(-1, dp(280)));
        image.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            int desired = Math.max(dp(160), Math.round(image.getWidth()
                    * source.getHeight() / (float) source.getWidth()));
            if (image.getLayoutParams().height != desired) {
                image.getLayoutParams().height = desired;
                image.requestLayout();
            }
        });
        hint = label(R.string.car_editor_hint, 13, false);
        hint.setPadding(dp(8), dp(10), dp(8), dp(14));
        content.addView(hint);
        LinearLayout card = column();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(round(panel, 14));
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        card.addView(label(R.string.car_editor_colours, 16, true));
        addPresets(card);
        addSlider(card, R.string.car_editor_tolerance, 100, tolerance, value -> {
            tolerance = Math.max(1, value);
            schedulePreview();
        });
        float[] savedHue = new float[3];
        Color.colorToHSV(target, savedHue);
        addSlider(card, R.string.car_editor_hue, 359, Math.round(savedHue[0]), value -> {
            target = CarPaint.rgb(value, 0.62f, 0.48f);
            original = false;
            schedulePreview();
        });
        addSlider(card, R.string.car_editor_brightness, 160,
                Math.round(brightness * 100), value -> {
            brightness = Math.max(0.4f, value / 100f);
            schedulePreview();
        });
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(16), 0, 0);
        content.addView(actions);
        cancel = button(R.string.car_editor_cancel, tone.ground);
        cancel.setOnClickListener(view -> finish());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1));
        save = button(R.string.car_editor_save, tone.ground);
        save.setOnClickListener(view -> save());
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(52), 1));
        setContentView(scroll);
        scroll.requestApplyInsets();
        schedulePreview();
    }

    private void addPresets(LinearLayout card) {
        String[] labels = getResources().getStringArray(R.array.car_editor_presets);
        LinearLayout row = null;
        for (int index = 0; index < PRESETS.length; index++) {
            if (index % 4 == 0) {
                row = new LinearLayout(this);
                card.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
            }
            final int selected = index;
            Button option = new Button(this);
            option.setAllCaps(false);
            option.setText(labels[index]);
            option.setTextSize(11);
            option.setTypeface(Fonts.semibold(this));
            option.setMinWidth(0);
            option.setMinimumHeight(dp(48));
            option.setPadding(dp(2), 0, dp(2), 0);
            option.setTextColor(index == 0 ? ink
                    : index == 2 || index == 7 ? 0xFF1D2423 : CREAM);
            option.setBackground(round(index == 0 ? ground : PRESETS[index], 9));
            option.setStateListAnimator(null);
            option.setOnClickListener(view -> {
                original = selected == 0;
                target = PRESETS[selected];
                schedulePreview();
            });
            row.addView(option, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
    }

    private void addSlider(LinearLayout card, int title, int max, int progress,
            java.util.function.IntConsumer changed) {
        TextView label = label(title, 14, true);
        label.setPadding(0, dp(10), 0, 0);
        card.addView(label);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max);
        seek.setProgress(progress);
        seek.setMinHeight(dp(48));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) {
                    changed.accept(value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        card.addView(seek, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private boolean tapImage(View view, MotionEvent event) {
        if (saving) {
            return true;
        }
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        float scale = Math.min(image.getWidth() / (float) source.getWidth(),
                image.getHeight() / (float) source.getHeight());
        float left = (image.getWidth() - source.getWidth() * scale) / 2;
        float top = (image.getHeight() - source.getHeight() * scale) / 2;
        float x = (event.getX() - left) / scale;
        float y = (event.getY() - top) / scale;
        if (x < 0 || y < 0 || x >= source.getWidth() || y >= source.getHeight()) {
            return true;
        }
        tapX = (int) x;
        tapY = (int) y;
        original = false;
        hint.setText(R.string.car_editor_selected);
        schedulePreview();
        return true;
    }

    private void schedulePreview() {
        if (saving || stopped) {
            return;
        }
        int request = ++generation;
        handler.postDelayed(() -> preview(request), 180);
    }

    private void preview(int request) {
        if (stopped || request != generation) {
            return;
        }
        if (tapX < 0 || original) {
            replaceImage(source);
            return;
        }
        int x = tapX;
        int y = tapY;
        int chosenTolerance = tolerance;
        int chosenTarget = target;
        float chosenBrightness = brightness;
        worker.execute(() -> {
            Bitmap result = painted(x, y, chosenTolerance, chosenTarget, chosenBrightness);
            handler.post(() -> {
                if (stopped || request != generation) {
                    result.recycle();
                    return;
                }
                replaceImage(result);
            });
        });
    }

    private Bitmap painted(int x, int y, int chosenTolerance, int chosenTarget,
            float chosenBrightness) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        int[] rendered = CarPaint.recolor(pixels, width, height, x, y,
                chosenTolerance, chosenTarget, chosenBrightness);
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(rendered, 0, width, 0, 0, width, height);
        return result;
    }

    private void replaceImage(Bitmap next) {
        Bitmap previous = null;
        if (image.getDrawable() instanceof BitmapDrawable) {
            previous = ((BitmapDrawable) image.getDrawable()).getBitmap();
        }
        image.setImageBitmap(next);
        if (previous != null && previous != source && previous != next) {
            previous.recycle();
        }
    }

    private void save() {
        if (tapX < 0 && !original) {
            Toast.makeText(this, R.string.car_editor_tap_first, Toast.LENGTH_SHORT).show();
            return;
        }
        save.setEnabled(false);
        cancel.setEnabled(false);
        saving = true;
        int request = ++generation;
        int x = tapX;
        int y = tapY;
        int chosenTolerance = tolerance;
        int chosenTarget = target;
        float chosenBrightness = brightness;
        boolean chosenOriginal = original;
        worker.execute(() -> {
            Bitmap result = chosenOriginal ? source : painted(x, y, chosenTolerance,
                    chosenTarget, chosenBrightness);
            boolean written = false;
            try {
                if (!stopped) {
                    written = CarPictureStore.saveRender(this, result, sourceStamp,
                        x < 0 ? 0 : x / (float) source.getWidth(),
                        y < 0 ? 0 : y / (float) source.getHeight(),
                        chosenTolerance, chosenOriginal ? 0 : chosenTarget,
                        chosenBrightness);
                }
            } catch (IOException ignored) {
                written = false;
            }
            if (!chosenOriginal) {
                result.recycle();
            }
            boolean success = written;
            handler.post(() -> {
                if (stopped || request != generation) {
                    return;
                }
                if (success) {
                    setResult(RESULT_OK);
                    finish();
                } else {
                    saving = false;
                    save.setEnabled(true);
                    cancel.setEnabled(true);
                    Toast.makeText(this, R.string.car_editor_save_error,
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView label(int resource, int size, boolean strong) {
        TextView label = new TextView(this);
        label.setText(resource);
        label.setTextSize(size);
        label.setTextColor(ink);
        label.setTypeface(strong ? Fonts.semibold(this) : Fonts.regular(this));
        return label;
    }

    private Button button(int resource, int color) {
        Button button = new Button(this);
        button.setText(resource);
        button.setAllCaps(false);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        button.setTextColor(night ? color : CREAM);
        button.setTypeface(Fonts.semibold(this));
        button.setBackground(round(night ? CREAM : color, 12));
        button.setStateListAnimator(null);
        return button;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        stopped = true;
        generation++;
        worker.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!saving) {
            super.onBackPressed();
        }
    }
}
