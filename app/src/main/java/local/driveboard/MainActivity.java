package local.driveboard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int PICK_CAR = 20;
    private final ExecutorService pictureWorker = Executors.newSingleThreadExecutor();
    private ImageView carThumbnail;
    private TextView carPlaceholder;
    private long thumbnailVersion = Long.MIN_VALUE;
    private static final int CREAM = 0xFFF4EFE4;
    private static final int FAINT_CREAM = 0x66F4EFE4;

    private final Handler handler = new Handler();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            board.refresh();
            updateStates();
            handler.postDelayed(this, 1000);
        }
    };

    private BoardView board;
    private TextView zoneDetail;
    private TextView batteryDetail;
    private TextView batteryValue;
    private TextView allowed;
    private Button grant;
    private int ink;
    private int muted;
    private int cardColor;
    private int hairline;
    private int accent;
    private String locationMessage;
    private boolean locating;
    private boolean night;
    private String selectedMode = Store.FULL;
    private Button fullModeButton;
    private Button splitModeButton;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLocale.wrap(base));
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        build();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void build() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        night = dark;
        Tone tone = Tone.current(this);
        accent = night ? CREAM : tone.ground;
        ink = night ? CREAM : 0xFF1D2423;
        muted = night ? 0xFFA9C2BD : 0xFF5F6763;
        cardColor = night ? tone.panel : 0xFFFAF7F1;
        hairline = night ? 0xFF46605B : 0xFFE3DCCF;

        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().getWindowInsetsController();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(night ? tone.ground : 0xFFEDE6DA);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        LinearLayout content = column();
        content.setPadding(dp(12), dp(8), dp(12), dp(16));
        scroll.addView(content);

        addTitle(content);
        addModeSelector(content, tone);
        addBoard(content, tone);
        LinearLayout card = column();
        card.setBackground(shape(cardColor, 14));
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        addPrayerRow(card);
        addMediaRow(card);
        addBatteryRow(card);
        addCarRow(card);
        addToneRow(card, tone);
        addLanguageRow(card, tone);
        addFooter(content);

        setContentView(scroll);
        getWindow().getDecorView().post(() -> getWindow().getInsetsController()
                .setSystemBarsAppearance(dark ? 0 : 24, 24));
        scroll.requestApplyInsets();
        board.post(this::updateAspect);
        updateStates();
    }

    private void addTitle(LinearLayout content) {
        TextView title = text(getString(R.string.app_name), 26, true);
        title.setTypeface(Fonts.extraBold(this));
        title.setTextColor(accent);
        title.setPadding(dp(8), dp(6), dp(8), dp(14));
        content.addView(title);
    }

    private void addBoard(LinearLayout content, Tone tone) {
        board = new BoardView(this);
        board.setMode(selectedMode);
        board.setBackground(shape(tone.ground, 14));
        if (night) {
            GradientDrawable edge = shape(Color.TRANSPARENT, 14);
            edge.setStroke(dp(1), hairline);
            board.setForeground(edge);
        }
        board.setClipToOutline(true);
        board.setContentDescription(getString(R.string.preview_description));
        board.setEditing(true, () -> {});
        content.addView(board, new LinearLayout.LayoutParams(-1, dp(150)));
        board.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> updateAspect());

        TextView hint = text(getString(R.string.edit_hint), 12, false);
        hint.setTextColor(muted);
        hint.setPadding(dp(8), dp(10), dp(8), dp(10));
        content.addView(hint);
    }

    private void addModeSelector(LinearLayout content, Tone tone) {
        LinearLayout segments = new LinearLayout(this);
        segments.setPadding(dp(3), dp(3), dp(3), dp(3));
        segments.setBackground(shape(cardColor, 24));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.bottomMargin = dp(10);
        content.addView(segments, params);
        fullModeButton = modeButton(segments, R.string.full_screen, Store.FULL, tone);
        splitModeButton = modeButton(segments, R.string.split_screen, Store.SPLIT, tone);
        updateModeButtons(tone);
    }

    private Button modeButton(LinearLayout segments, int label, String mode, Tone tone) {
        Button option = new Button(this);
        option.setText(label);
        option.setTextSize(13);
        option.setAllCaps(false);
        option.setStateListAnimator(null);
        option.setTypeface(Fonts.semibold(this));
        option.setMinWidth(0);
        option.setMinimumHeight(dp(48));
        option.setPadding(dp(6), 0, dp(6), 0);
        option.setOnClickListener(view -> {
            selectedMode = mode;
            board.setMode(mode);
            updateModeButtons(tone);
            updateAspect();
        });
        segments.addView(option, new LinearLayout.LayoutParams(0, dp(48), 1));
        return option;
    }

    private void updateModeButtons(Tone tone) {
        styleModeButton(fullModeButton, Store.FULL.equals(selectedMode), tone);
        styleModeButton(splitModeButton, Store.SPLIT.equals(selectedMode), tone);
    }

    private void styleModeButton(Button button, boolean selected, Tone tone) {
        button.setSelected(selected);
        button.setBackground(shape(selected ? filledControlColor(tone) : Color.TRANSPARENT, 21));
        button.setTextColor(selected ? filledControlTextColor(tone) : ink);
    }

    private void addPrayerRow(LinearLayout card) {
        LinearLayout prayer = row(card, false);
        prayer.setOrientation(LinearLayout.VERTICAL);
        prayer.setGravity(Gravity.START);
        LinearLayout zone = column();
        zone.addView(text(getString(R.string.prayer_zone), 15, true));
        prayer.addView(zone, new LinearLayout.LayoutParams(-1, -2));
        zoneDetail = detail(zone, "");
        prayer.setOnClickListener(view -> pickZone());
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionParams.topMargin = dp(8);
        prayer.addView(actions, actionParams);
        Button location = pill(getString(R.string.use_location));
        location.setOnClickListener(view -> useLocation());
        actions.addView(location);
        TextView change = text(getString(R.string.change_zone), 12, true);
        change.setTextColor(accent);
        change.setGravity(Gravity.CENTER);
        change.setMinWidth(dp(48));
        change.setMinHeight(dp(48));
        change.setPadding(dp(8), 0, 0, 0);
        change.setOnClickListener(view -> pickZone());
        actions.addView(change);
    }

    private void addMediaRow(LinearLayout card) {
        LinearLayout media = row(card, true);
        description(media, R.string.now_playing);
        allowed = text(getString(R.string.allowed), 13, true);
        allowed.setTextColor(accent);
        media.addView(allowed);
        grant = pill(getString(R.string.grant));
        grant.setOnClickListener(view -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        media.addView(grant);
    }

    private void addBatteryRow(LinearLayout card) {
        LinearLayout battery = row(card, true);
        LinearLayout description = description(battery, R.string.car_battery);
        batteryDetail = detail(description, getString(R.string.battery_next_drive));
        batteryValue = text("", 13, true);
        batteryValue.setFontFeatureSettings("tnum");
        battery.addView(batteryValue);
    }

    private void addCarRow(LinearLayout card) {
        LinearLayout car = row(card, true);
        LinearLayout description = description(car, R.string.my_car);
        carPlaceholder = detail(description, getString(R.string.no_picture));
        carThumbnail = new ImageView(this);
        carThumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carThumbnail.setContentDescription(getString(R.string.my_car));
        car.addView(carThumbnail, new LinearLayout.LayoutParams(dp(72), dp(48)));
        TextView choose = text(getString(R.string.choose_picture), 12, true);
        choose.setTextColor(accent);
        choose.setGravity(Gravity.CENTER);
        choose.setMinHeight(dp(48));
        choose.setMinWidth(dp(48));
        choose.setPadding(dp(12), 0, dp(12), 0);
        choose.setOnClickListener(view -> chooseCarPicture());
        car.addView(choose);
        car.setOnClickListener(view -> {
            if (CarPictureStore.sourceFile(this).isFile()) {
                startActivity(new Intent(this, CarEditorActivity.class));
            } else {
                chooseCarPicture();
            }
        });
        thumbnailVersion = Long.MIN_VALUE;
    }

    private void chooseCarPicture() {
        Intent intent = new Intent(Build.VERSION.SDK_INT >= 33
                ? MediaStore.ACTION_PICK_IMAGES : Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        if (Build.VERSION.SDK_INT < 33) {
            intent.addCategory(Intent.CATEGORY_OPENABLE);
        }
        startActivityForResult(intent, PICK_CAR);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_CAR || result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        android.net.Uri uri = data.getData();
        pictureWorker.execute(() -> {
            try {
                CarPictureStore.importSource(this, uri);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        updateStates();
                        startActivity(new Intent(this, CarEditorActivity.class));
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.picture_import_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void addToneRow(LinearLayout card, Tone tone) {
        LinearLayout colours = row(card, true);
        colours.setOrientation(LinearLayout.VERTICAL);
        colours.setGravity(Gravity.START);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text(getString(R.string.colour_tone), 15, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView toneName = text(toneName(tone), 12, false);
        toneName.setTextColor(muted);
        heading.addView(toneName);
        colours.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout swatches = new LinearLayout(this);
        colours.addView(swatches, new LinearLayout.LayoutParams(-1, dp(48)));
        for (Tone option : Tone.values()) {
            addSwatch(swatches, option, tone);
        }
    }

    private void addSwatch(LinearLayout swatches, Tone option, Tone tone) {
        FrameLayout target = new FrameLayout(this);
        target.setMinimumWidth(dp(48));
        target.setContentDescription(toneName(option));
        target.setSelected(option == tone);
        View circle = new View(this);
        GradientDrawable drawable = shape(option.ground, 24);
        if (night) {
            drawable.setStroke(dp(1), FAINT_CREAM);
        }
        circle.setBackground(drawable);
        target.addView(circle, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        if (option == tone) {
            View ring = new View(this);
            GradientDrawable outline = shape(Color.TRANSPARENT, 24);
            outline.setStroke(dp(2), night ? CREAM : ink);
            ring.setBackground(outline);
            target.addView(ring, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        }
        swatches.addView(target, new LinearLayout.LayoutParams(0, dp(48), 1));
        target.setOnClickListener(view -> {
            Store.prefs(this).edit().putString("tone", option.id).apply();
            build();
            board.post(this::updateAspect);
        });
    }

    private void addLanguageRow(LinearLayout card, Tone tone) {
        LinearLayout language = row(card, true);
        description(language, R.string.language);
        LinearLayout segments = new LinearLayout(this);
        segments.setBackground(shape(hairline, 24));
        language.addView(segments);
        for (String lang : new String[]{"en", "ms"}) {
            addLanguageOption(segments, lang, tone);
        }
    }

    private void addLanguageOption(LinearLayout segments, String lang, Tone tone) {
        Button option = new Button(this);
        option.setText(lang.equals("en") ? "EN" : "BM");
        option.setTextSize(13);
        option.setStateListAnimator(null);
        option.setTypeface(Fonts.semibold(this));
        option.setMinWidth(0);
        option.setMinimumWidth(dp(52));
        option.setMinimumHeight(dp(48));
        option.setPadding(dp(10), 0, dp(10), 0);
        boolean selected = lang.equals(Store.prefs(this).getString("lang", "en"));
        option.setBackground(shape(selected ? filledControlColor(tone) : Color.TRANSPARENT, 24));
        option.setTextColor(selected ? filledControlTextColor(tone) : ink);
        option.setContentDescription(getString(lang.equals("en")
                ? R.string.english : R.string.malay));
        option.setSelected(selected);
        option.setOnClickListener(view -> {
            Store.prefs(this).edit().putString("lang", lang).apply();
            recreate();
        });
        segments.addView(option, new LinearLayout.LayoutParams(dp(54), dp(48)));
    }

    private void addFooter(LinearLayout content) {
        TextView reset = text(getString(R.string.reset_layout), 13, false);
        reset.setGravity(Gravity.CENTER);
        reset.setMinHeight(dp(48));
        reset.setTextColor(accent);
        reset.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.reset_layout)
                .setMessage(R.string.reset_layout_confirmation)
                .setPositiveButton(R.string.reset_action, (dialog, which) -> Store.prefs(this).edit()
                        .putString(Store.layoutKey(selectedMode),
                                (Store.SPLIT.equals(selectedMode)
                                        ? BoardLayout.splitDefaults() : BoardLayout.defaults()).toJson())
                        .apply())
                .setNegativeButton(R.string.cancel_action, null)
                .show());
        content.addView(reset, new LinearLayout.LayoutParams(-1, -2));
        TextView help = text(getString(R.string.auto_help), 11, false);
        help.setTextColor(muted);
        help.setPadding(dp(8), 0, dp(8), dp(8));
        content.addView(help);
    }

    private int filledControlColor(Tone tone) {
        return night ? CREAM : tone.ground;
    }

    private int filledControlTextColor(Tone tone) {
        return night ? tone.ground : Color.WHITE;
    }

    private String toneName(Tone tone) {
        return getResources().getStringArray(R.array.tone_names)[tone.ordinal()];
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView text(String value, int size, boolean strong) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(ink);
        label.setTypeface(strong ? Fonts.semibold(this) : Fonts.regular(this));
        label.setIncludeFontPadding(false);
        return label;
    }

    private LinearLayout row(LinearLayout card, boolean divider) {
        if (divider) {
            View line = new View(this);
            line.setBackgroundColor(hairline);
            card.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setMinimumHeight(dp(64));
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private LinearLayout description(LinearLayout row, int label) {
        LinearLayout column = column();
        column.setPadding(0, 0, dp(8), 0);
        column.addView(text(getString(label), 15, true));
        row.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        return column;
    }

    private TextView detail(LinearLayout column, String value) {
        TextView label = text(value, 12, false);
        label.setTextColor(muted);
        label.setPadding(0, dp(4), 0, 0);
        column.addView(label);
        return label;
    }

    private Button pill(String label) {
        Tone tone = Tone.current(this);
        Button button = new Button(this);
        button.setText(label);
        button.setStateListAnimator(null);
        button.setTextSize(12);
        button.setTypeface(Fonts.semibold(this));
        button.setTextColor(filledControlTextColor(tone));
        button.setBackground(shape(filledControlColor(tone), 24));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(48));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setAllCaps(false);
        return button;
    }

    private void updateStates() {
        boolean access = WidgetData.hasMediaAccess(this);
        allowed.setVisibility(access ? View.VISIBLE : View.GONE);
        grant.setVisibility(access ? View.GONE : View.VISIBLE);
        SharedPreferences prefs = Store.prefs(this);
        long renderVersion = prefs.getLong("car_render_at", 0);
        if (thumbnailVersion != renderVersion) {
            thumbnailVersion = renderVersion;
            boolean rendered = CarPictureStore.renderFile(this).isFile();
            carThumbnail.setImageBitmap(rendered ? BitmapFactory.decodeFile(
                    CarPictureStore.renderFile(this).getAbsolutePath()) : null);
            carThumbnail.setVisibility(rendered ? View.VISIBLE : View.GONE);
            carPlaceholder.setVisibility(rendered ? View.GONE : View.VISIBLE);
        }
        zoneDetail.setText(locationMessage != null ? locationMessage
                : WidgetData.zone(this) + " · " + WidgetData.zoneName(this));
        long at = prefs.getLong("battery_at", 0);
        if (at == 0) {
            boolean unavailable = prefs.getBoolean("battery_unsupported", false);
            batteryValue.setText(unavailable ? R.string.unavailable : R.string.waiting);
            batteryValue.setTextColor(unavailable ? muted : night ? 0xFFE5BC6B : 0xFF8A6100);
            batteryDetail.setText(unavailable ? R.string.no_car_data
                    : R.string.battery_next_drive);
            batteryDetail.setAlpha(1f);
        } else {
            String pct = prefs.contains("battery_pct")
                    ? String.format(AppLocale.locale(this), "%.0f%%", prefs.getFloat("battery_pct", 0))
                    : "—";
            String range = prefs.contains("range_m")
                    ? getString(R.string.phone_range_km, Math.round(prefs.getInt("range_m", 0) / 1000f))
                    : "—";
            String when = new SimpleDateFormat("HH:mm", AppLocale.locale(this))
                    .format(new Date(at));
            batteryValue.setText("");
            batteryDetail.setText(getString(R.string.battery_summary, pct, range,
                    System.currentTimeMillis() - at > 1800000
                            ? getString(R.string.updated, when) : when));
            batteryDetail.setAlpha(1f);
            batteryDetail.setTextColor(muted);
        }
    }

    private void pickZone() {
        ZoneCatalog.Zone[] zones = ZoneCatalog.options();
        String[] labels = new String[zones.length];
        for (int index = 0; index < zones.length; index++) {
            labels[index] = zones[index].code + " · " + zones[index].name;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.prayer_zone)
                .setItems(labels, (dialog, index) -> {
                    locationMessage = null;
                    Store.prefs(this).edit()
                            .putString("zone", zones[index].code)
                            .putString("zone_name", zones[index].name)
                            .putLong("prayer_refresh_" + zones[index].code,
                                    System.currentTimeMillis())
                            .apply();
                    board.refresh();
                    updateStates();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void useLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
            return;
        }
        locate();
    }

    private void locate() {
        if (locating) {
            return;
        }
        locating = true;
        locationMessage = getString(R.string.locating);
        updateStates();
        ZoneLocator.refresh(this, (success, message) -> runOnUiThread(() -> {
            locating = false;
            locationMessage = success ? null : getString(R.string.location_unavailable);
            updateStates();
            board.refresh();
        }));
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 7) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                locate();
            } else {
                locationMessage = getString(R.string.location_unavailable);
                updateStates();
            }
        }
    }

    private void updateAspect() {
        if (board.getWidth() == 0) {
            return;
        }
        SharedPreferences prefs = Store.prefs(this);
        boolean split = Store.SPLIT.equals(selectedMode);
        int width = Math.max(1, prefs.getInt(selectedMode + "_width", split ? 1190 : 1920));
        int height = Math.max(1, prefs.getInt(selectedMode + "_height", split ? 1050 : 1080));
        int desired = Math.max(1, Math.round(board.getWidth() * height / (float) width));
        if (board.getLayoutParams().height != desired) {
            board.getLayoutParams().height = desired;
            board.requestLayout();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Store.prefs(this).registerOnSharedPreferenceChangeListener(this);
        handler.post(tick);
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                && System.currentTimeMillis()
                - Store.prefs(this).getLong("zone_checked_at", 0) > 43200000) {
            locate();
        }
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(tick);
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        pictureWorker.shutdown();
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        handler.post(() -> {
            board.refresh();
            updateStates();
            updateAspect();
        });
    }
}
