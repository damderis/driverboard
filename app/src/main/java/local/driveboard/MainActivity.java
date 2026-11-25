package local.driveboard;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int PICK_BACKGROUND = 20;
    private static final int REQUEST_PARKED_LOCATION = 8;
    private static final int REQUEST_PRAYER_NOTIFICATIONS = 9;
    private TextView prayerAlertStatus;
    private final ExecutorService pictureWorker = Executors.newSingleThreadExecutor();
    private ImageView backgroundThumbnail;
    private TextView backgroundPlaceholder;
    private TextView removeBackground;
    private long thumbnailVersion = Long.MIN_VALUE;
    private static final String[] COLOUR_VALUES = {
            "wallpaper", "background", "album", "teal", "emerald", "navy", "maroon", "graphite"
    };
    private static final int[] PRESET_COLOURS = {
            0, 0, 0, 0xFF0F6E6E, 0xFF3FB978, 0xFF688FCB, 0xFFC36B82, 0xFF818A91
    };

    private final Handler handler = new Handler();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            board.refresh();
            if ("album".equals(Store.prefs(MainActivity.this).getString("colour_source", "wallpaper"))) {
                int currentSeed = BoardColours.current(MainActivity.this,
                        WidgetData.mediaSnapshot(MainActivity.this).art).seed;
                // Defer a live retheme while a dialog or system window has focus.
                if (currentSeed != themeSeed && hasWindowFocus()) {
                    recreate();
                    return;
                }
            }
            updateStates();
            handler.postDelayed(this, 1000);
        }
    };

    private BoardView board;
    private TextView zoneDetail;
    private static final String DASH = "\u2014";
    private TextView weatherDetail;
    private TextView weatherValue;
    private final WeatherRepository.Callback weatherCallback = snapshot -> runOnUiThread(() -> {
        if (board != null) {
            board.refresh();
        }
        if (weatherDetail != null) {
            updateStates();
        }
    });
    private TextView allowed;
    private MaterialButton grant;
    private TextView parkedLocationStatus;
    private MaterialButton parkedLocationGrant;
    private boolean parkedLocationDenied;
    private int ink;
    private int muted;
    private int cardColor;
    private int hairline;
    private int accent;
    private String locationMessage;
    private boolean locating;
    private boolean night;
    private String selectedMode = Store.FULL;
    private MaterialButton fullModeButton;
    private MaterialButton splitModeButton;
    private int themeSeed;
    private ScrollView scrollView;
    private int savedScrollY;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLocale.wrap(base));
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            selectedMode = state.getString("selected_mode", Store.FULL);
            savedScrollY = state.getInt("scroll_y", 0);
        }
        String source = Store.prefs(this).getString("colour_source", "wallpaper");
        themeSeed = BoardColours.current(this, WidgetData.mediaSnapshot(this).art).seed;
        if ("wallpaper".equals(source)) {
            DynamicColors.applyToActivityIfAvailable(this);
        } else {
            DynamicColors.applyToActivityIfAvailable(this,
                    new DynamicColorsOptions.Builder().setContentBasedSource(themeSeed).build());
        }
        BackgroundStore.cleanupLegacy(this);
        build();
        if (Store.prayerAlertsEnabled(this)
                && !Store.prefs(this).getBoolean("prayer_alert_permission_asked", false)) {
            requestPrayerNotificationPermission();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("selected_mode", selectedMode);
        state.putInt("scroll_y", scrollView == null ? 0 : scrollView.getScrollY());
        super.onSaveInstanceState(state);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int materialColor(int attribute) {
        return MaterialColors.getColor(this, attribute, Color.BLACK);
    }

    private void build() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        night = dark;
        Tone tone = Tone.current(this);
        accent = materialColor(androidx.appcompat.R.attr.colorPrimary);
        ink = materialColor(com.google.android.material.R.attr.colorOnSurface);
        muted = materialColor(com.google.android.material.R.attr.colorOnSurfaceVariant);
        cardColor = materialColor(com.google.android.material.R.attr.colorSurfaceContainerHigh);
        hairline = materialColor(com.google.android.material.R.attr.colorOutlineVariant);

        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().getWindowInsetsController();

        ScrollView scroll = new ScrollView(this);
        scrollView = scroll;
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(materialColor(com.google.android.material.R.attr.colorSurface));
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        LinearLayout content = column();
        content.setPadding(dp(16), dp(4), dp(16), dp(24));
        scroll.addView(content);

        addTitle(content);
        addModeSelector(content);
        addBoard(content, tone);
        MaterialCardView settingsCard = new MaterialCardView(this);
        settingsCard.setRadius(dp(28));
        settingsCard.setCardBackgroundColor(cardColor);
        settingsCard.setCardElevation(0);
        settingsCard.setStrokeWidth(0);
        LinearLayout card = column();
        settingsCard.addView(card);
        content.addView(settingsCard, new LinearLayout.LayoutParams(-1, -2));
        addPrayerRow(card);
        addPrayerAlertsRow(card);
        addParkedDetectionRow(card);
        addMediaRow(card);
        addNavigationRow(card);
        addNavAppRow(card);
        addPlaceRow(card, "home", R.string.home_address, R.string.home_not_set);
        addPlaceRow(card, "work", R.string.work_address, R.string.work_not_set);
        addPanelTilesRow(card);
        addWeatherRow(card);
        addBackgroundRow(card);
        addColoursRow(card);
        addLanguageRow(card);
        addQuranResumeRow(card);
        addFooter(content);

        setContentView(scroll);
        getWindow().getDecorView().post(() -> getWindow().getInsetsController()
                .setSystemBarsAppearance(dark ? 0 : 24, 24));
        scroll.requestApplyInsets();
        board.post(this::updateAspect);
        scroll.post(() -> scroll.scrollTo(0, savedScrollY));
        updateStates();
    }

    private void addTitle(LinearLayout content) {
        MaterialToolbar toolbar = new MaterialToolbar(this, null,
                androidx.appcompat.R.attr.toolbarStyle);
        toolbar.setTitle(R.string.app_name);
        toolbar.setTitleTextAppearance(this, R.style.TextAppearance_DriveBoard_Headline);
        toolbar.setTitleTextColor(ink);
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        toolbar.setContentInsetsAbsolute(0, 0);
        content.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(88)));
    }

    private void addBoard(LinearLayout content, Tone tone) {
        board = new BoardView(this);
        board.setCarNight(night);
        board.setMode(selectedMode);
        MaterialCardView preview = new MaterialCardView(this);
        preview.setRadius(dp(32));
        preview.setCardBackgroundColor(tone.ground);
        preview.setCardElevation(0);
        preview.setStrokeColor(hairline);
        preview.setStrokeWidth(dp(1));
        preview.setClipToOutline(true);
        board.setBackground(shape(tone.ground, 32));
        board.setClipToOutline(true);
        board.setContentDescription(getString(R.string.preview_description));
        board.setEditing(false, null);
        preview.addView(board, new FrameLayout.LayoutParams(-1, dp(150)));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
        previewParams.bottomMargin = dp(4);
        content.addView(preview, previewParams);
        board.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> updateAspect());

        MaterialButton edit = pill(getString(R.string.board_edit_action));
        edit.setOnClickListener(view -> startActivity(new Intent(this,
                BoardEditorActivity.class).putExtra(BoardEditorActivity.EXTRA_MODE, selectedMode)));
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(-1, dp(52));
        editParams.topMargin = dp(10);
        editParams.bottomMargin = dp(12);
        content.addView(edit, editParams);
    }

    private void addModeSelector(LinearLayout content) {
        MaterialButtonToggleGroup segments = new MaterialButtonToggleGroup(this, null,
                com.google.android.material.R.attr.materialButtonToggleGroupStyle);
        segments.setSingleSelection(true);
        segments.setSelectionRequired(true);
        segments.setPadding(dp(3), dp(3), dp(3), dp(3));
        segments.setBackground(shape(materialColor(
                com.google.android.material.R.attr.colorSurfaceContainer), 28));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.bottomMargin = dp(10);
        content.addView(segments, params);
        fullModeButton = modeButton(segments, R.string.full_screen);
        splitModeButton = modeButton(segments, R.string.split_screen);
        segments.check(Store.SPLIT.equals(selectedMode)
                ? splitModeButton.getId() : fullModeButton.getId());
        segments.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            selectedMode = checkedId == splitModeButton.getId() ? Store.SPLIT : Store.FULL;
            board.setMode(selectedMode);
            updateAspect();
        });
    }

    private MaterialButton modeButton(MaterialButtonToggleGroup segments, int label) {
        MaterialButton option = new MaterialButton(this);
        option.setId(View.generateViewId());
        option.setText(label);
        option.setTextSize(13);
        option.setAllCaps(false);
        option.setStateListAnimator(null);
        option.setTypeface(Fonts.semibold(this));
        option.setMinWidth(0);
        option.setMinimumHeight(dp(48));
        option.setPadding(dp(6), 0, dp(6), 0);
        option.setCheckable(true);
        option.setStrokeWidth(0);
        option.setBackgroundTintList(toggleBackground());
        option.setTextColor(toggleForeground());
        segments.addView(option, new LinearLayout.LayoutParams(0, dp(48), 1));
        return option;
    }

    private ColorStateList toggleBackground() {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{materialColor(com.google.android.material.R.attr.colorPrimaryContainer),
                        materialColor(com.google.android.material.R.attr.colorSurfaceContainer)});
    }

    private ColorStateList toggleForeground() {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{materialColor(com.google.android.material.R.attr.colorOnPrimaryContainer),
                        ink});
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
        if (BuildConfig.DEBUG) {
            prayer.setOnLongClickListener(view -> {
                PrayerAlerts.postDebugSample(this);
                Toast.makeText(this, R.string.prayer_alert_sample, Toast.LENGTH_SHORT).show();
                return true;
            });
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionParams.topMargin = dp(8);
        prayer.addView(actions, actionParams);
        MaterialButton location = pill(getString(R.string.use_location));
        location.setBackgroundTintList(ColorStateList.valueOf(materialColor(
                com.google.android.material.R.attr.colorSecondaryContainer)));
        location.setTextColor(materialColor(com.google.android.material.R.attr.colorOnSecondaryContainer));
        location.setOnClickListener(view -> useLocation());
        actions.addView(location);
        MaterialButton change = textButton(R.string.change_zone);
        change.setTextColor(accent);
        change.setGravity(Gravity.CENTER);
        change.setMinWidth(dp(48));
        change.setMinHeight(dp(48));
        change.setPadding(dp(8), 0, 0, 0);
        change.setOnClickListener(view -> pickZone());
        actions.addView(change);
    }

    private void addPrayerAlertsRow(LinearLayout card) {
        LinearLayout item = row(card, true);
        item.setOrientation(LinearLayout.VERTICAL);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(getString(R.string.prayer_alerts), 15, true);
        heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setContentDescription(getString(R.string.prayer_alerts));
        toggle.setChecked(Store.prayerAlertsEnabled(this));
        heading.addView(toggle);
        item.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = detail(item, getString(R.string.prayer_alert_hint));
        hint.setSingleLine(true);
        hint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        prayerAlertStatus = detail(item, "");
        prayerAlertStatus.setMinHeight(dp(48));
        prayerAlertStatus.setGravity(Gravity.CENTER_VERTICAL);
        prayerAlertStatus.setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                requestPrayerNotificationPermission();
            } else {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            }
        });
        MaterialButton lead = pill(getString(R.string.prayer_alert_lead,
                Store.prayerAlertLeadMinutes(this)));
        item.addView(lead);
        lead.setOnClickListener(view -> {
            int[] values = {5, 10, 15, 30};
            String[] labels = new String[values.length];
            int selected = 2;
            for (int i = 0; i < values.length; i++) {
                labels[i] = getString(R.string.prayer_alert_lead, values[i]);
                if (values[i] == Store.prayerAlertLeadMinutes(this)) selected = i;
            }
            new MaterialAlertDialogBuilder(this).setTitle(R.string.prayer_alert_lead_title)
                    .setSingleChoiceItems(labels, selected, (dialog, index) -> {
                        Store.setPrayerAlertLeadMinutes(this, values[index]);
                        lead.setText(labels[index]);
                        PrayerAlerts.refresh(this);
                        dialog.dismiss();
                    }).show();
        });
        toggle.setOnCheckedChangeListener((button, checked) -> {
            Store.setPrayerAlertsEnabled(this, checked);
            if (checked) requestPrayerNotificationPermission();
            PrayerAlerts.refresh(this);
            updatePrayerAlertState();
        });
        updatePrayerAlertState();
    }

    private void requestPrayerNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Store.prefs(this).edit().putBoolean("prayer_alert_permission_asked", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_PRAYER_NOTIFICATIONS);
        }
    }

    private void updatePrayerAlertState() {
        if (prayerAlertStatus == null) return;
        boolean allowed = PrayerAlerts.canNotify(this);
        prayerAlertStatus.setText(!Store.prayerAlertsEnabled(this) ? R.string.prayer_alert_off
                : allowed ? R.string.prayer_alert_ready : R.string.prayer_alert_denied);
    }

    private void addMediaRow(LinearLayout card) {
        LinearLayout media = row(card, true);
        description(media, R.string.now_playing);
        MaterialSwitch accessSwitch = new MaterialSwitch(this);
        accessSwitch.setChecked(true);
        accessSwitch.setClickable(false);
        accessSwitch.setContentDescription(getString(R.string.allowed));
        allowed = accessSwitch;
        media.addView(accessSwitch);
        grant = pill(getString(R.string.grant));
        grant.setOnClickListener(view -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        media.addView(grant);
    }

    private void addNavigationRow(LinearLayout card) {
        LinearLayout item = row(card, true);
        LinearLayout labels = column();
        labels.addView(text(getString(R.string.navigation_overlay), 15, true));
        TextView hint = detail(labels, getString(R.string.navigation_overlay_hint));
        hint.setSingleLine(true);
        hint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setContentDescription(getString(R.string.navigation_overlay));
        toggle.setChecked(Store.prefs(this).getBoolean("navigation_overlay", true));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            Store.prefs(this).edit().putBoolean("navigation_overlay", checked).apply();
            board.refresh();
        });
        item.addView(toggle);
    }

    // Navigation apps DriveBoard Peta's Home / Charging / Mosque tiles can send a route to.
    static final String[] NAV_APPS = {"", "my.peta.maps", "com.google.android.apps.maps", "com.waze"};
    private static final String[] NAV_APP_NAMES = {"", "Peta", "Google Maps", "Waze"};

    private String navAppName(String pkg) {
        for (int i = 1; i < NAV_APPS.length; i++) if (NAV_APPS[i].equals(pkg)) return NAV_APP_NAMES[i];
        return getString(R.string.nav_app_auto);
    }

    private void addNavAppRow(LinearLayout card) {
        LinearLayout item = row(card, true);
        LinearLayout labels = column();
        labels.addView(text(getString(R.string.nav_app), 15, true));
        TextView chosen = detail(labels, navAppName(Store.prefs(this).getString("nav_app", "")));
        item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        item.setOnClickListener(view -> {
            java.util.List<String> packages = new java.util.ArrayList<>();
            java.util.List<String> names = new java.util.ArrayList<>();
            for (int i = 0; i < NAV_APPS.length; i++) {
                if (i > 0) {
                    try {
                        getPackageManager().getPackageInfo(NAV_APPS[i], 0);
                    } catch (android.content.pm.PackageManager.NameNotFoundException missing) {
                        continue;
                    }
                }
                packages.add(NAV_APPS[i]);
                names.add(i == 0 ? getString(R.string.nav_app_auto) : NAV_APP_NAMES[i]);
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.nav_app)
                    .setItems(names.toArray(new String[0]), (dialog, index) -> {
                        Store.prefs(this).edit().putString("nav_app", packages.get(index)).apply();
                        chosen.setText(names.get(index));
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    /** A saved place for the Home / Work tiles: "lat,lng" or a typed address (pref key). */
    private void addPlaceRow(LinearLayout card, String key, int title, int notSet) {
        LinearLayout item = row(card, true);
        LinearLayout labels = column();
        labels.addView(text(getString(title), 15, true));
        String saved = Store.prefs(this).getString(key, "");
        TextView value = detail(labels, saved.isEmpty() ? getString(notSet) : saved);
        item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        item.setOnClickListener(view -> {
            android.widget.EditText input = new android.widget.EditText(this);
            input.setText(Store.prefs(this).getString(key, ""));
            input.setHint(R.string.home_hint);
            input.setSingleLine(true);
            android.widget.FrameLayout box = new android.widget.FrameLayout(this);
            box.setPadding(dp(24), dp(8), dp(24), 0);
            box.addView(input);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setView(box)
                    .setPositiveButton(android.R.string.ok, (dialog, which) ->
                            savePlace(key, input.getText().toString().trim(), value, notSet))
                    .setNeutralButton(R.string.home_use_location, (dialog, which) ->
                            placeFromLocation(key, value, notSet))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    /** Which tiles DriveBoard Peta's panel shows (pref "panel_tiles", up to four). */
    private void addPanelTilesRow(LinearLayout card) {
        LinearLayout item = row(card, true);
        LinearLayout labels = column();
        labels.addView(text(getString(R.string.panel_tiles), 15, true));
        TextView value = detail(labels, panelTileNames());
        item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        item.setOnClickListener(view -> {
            PanelShortcut[] all = PanelShortcut.values();
            String[] names = new String[all.length];
            boolean[] checked = new boolean[all.length];
            java.util.List<PanelShortcut> picked = PanelShortcut.parse(
                    Store.prefs(this).getString("panel_tiles", PanelShortcut.DEFAULT));
            for (int i = 0; i < all.length; i++) {
                names[i] = getString(all[i].label);
                checked[i] = picked.contains(all[i]);
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.panel_tiles) + " · "
                            + getString(R.string.panel_tiles_max))
                    .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> {
                        if (isChecked && picked.size() >= PanelShortcut.MAX) {
                            // Already four: undo this tick.
                            ((androidx.appcompat.app.AlertDialog) dialog).getListView()
                                    .setItemChecked(which, false);
                            checked[which] = false;
                            return;
                        }
                        if (isChecked) picked.add(all[which]);
                        else picked.remove(all[which]);
                    })
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        Store.prefs(this).edit()
                                .putString("panel_tiles", PanelShortcut.join(picked)).apply();
                        value.setText(panelTileNames());
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    private String panelTileNames() {
        StringBuilder names = new StringBuilder();
        for (PanelShortcut shortcut : PanelShortcut.parse(
                Store.prefs(this).getString("panel_tiles", PanelShortcut.DEFAULT))) {
            if (names.length() > 0) names.append(" · ");
            names.append(getString(shortcut.label));
        }
        return names.toString();
    }

    private void savePlace(String key, String place, TextView value, int notSet) {
        Store.prefs(this).edit().putString(key, place).apply();
        value.setText(place.isEmpty() ? getString(notSet) : place);
    }

    private void placeFromLocation(String key, TextView value, int notSet) {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PARKED_LOCATION);
            return;
        }
        value.setText(R.string.locating);
        android.location.LocationManager manager =
                getSystemService(android.location.LocationManager.class);
        manager.getCurrentLocation(android.location.LocationManager.GPS_PROVIDER, null,
                getMainExecutor(), location -> {
                    if (location == null) {
                        value.setText(R.string.location_unavailable);
                        return;
                    }
                    savePlace(key, String.format(java.util.Locale.ROOT, "%.6f,%.6f",
                            location.getLatitude(), location.getLongitude()), value, notSet);
                });
    }

    private void addParkedDetectionRow(LinearLayout card) {
        LinearLayout parked = row(card, true);
        parked.setOrientation(LinearLayout.VERTICAL);
        LinearLayout description = column();
        description.addView(text(getString(R.string.parked_detection), 15, true));
        parked.addView(description, new LinearLayout.LayoutParams(-1, -2));
        parkedLocationStatus = detail(description, "");
        parkedLocationGrant = pill(getString(R.string.parked_permission_grant));
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(-2, dp(48));
        action.topMargin = dp(8);
        parked.addView(parkedLocationGrant, action);
        parkedLocationGrant.setOnClickListener(view -> requestParkedLocationPermission());
    }

    private void requestParkedLocationPermission() {
        if (hasParkedLocationPermission()) {
            parkedLocationDenied = false;
            updateParkedLocationState();
            return;
        }
        parkedLocationDenied = false;
        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PARKED_LOCATION);
    }

    private boolean hasParkedLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updateParkedLocationState() {
        boolean granted = hasParkedLocationPermission();
        int state = granted ? R.string.parked_permission_granted
                : parkedLocationDenied ? R.string.parked_permission_denied
                : R.string.browser_permission_needed;
        parkedLocationStatus.setText(getString(state));
        parkedLocationGrant.setText(granted
                ? R.string.parked_permission_granted : R.string.parked_permission_grant);
        parkedLocationGrant.setEnabled(!granted);
        parkedLocationGrant.setAlpha(granted ? 0.6f : 1f);
    }

    private void addWeatherRow(LinearLayout card) {
        LinearLayout weather = row(card, true);
        LinearLayout description = description(weather, R.string.weather);
        weatherDetail = detail(description, DASH);
        weatherValue = text(DASH, 13, true);
        weatherValue.setFontFeatureSettings("tnum");
        weather.addView(weatherValue);
    }

    private void addBackgroundRow(LinearLayout card) {
        LinearLayout row = row(card, true);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(header, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout description = description(header, R.string.background);
        backgroundPlaceholder = detail(description, getString(R.string.no_picture));
        backgroundThumbnail = new ImageView(this);
        backgroundThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backgroundThumbnail.setContentDescription(getString(R.string.background));
        backgroundThumbnail.setBackground(shape(materialColor(
                com.google.android.material.R.attr.colorSecondaryContainer), 14));
        backgroundThumbnail.setClipToOutline(true);
        header.addView(backgroundThumbnail, new LinearLayout.LayoutParams(dp(56), dp(48)));
        LinearLayout actions = new LinearLayout(this);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionsParams.topMargin = dp(8);
        row.addView(actions, actionsParams);
        MaterialButton choose = backgroundAction(getString(R.string.choose_picture));
        choose.setOnClickListener(view -> chooseBackgroundPicture());
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(0, dp(48), 1);
        action.rightMargin = dp(8);
        actions.addView(choose, action);
        removeBackground = backgroundAction(getString(R.string.remove_picture));
        removeBackground.setOnClickListener(view -> pictureWorker.execute(() -> {
            try {
                BackgroundStore.remove(this);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        recreate();
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.picture_remove_failed, Toast.LENGTH_LONG).show());
            }
        }));
        actions.addView(removeBackground, new LinearLayout.LayoutParams(0, dp(48), 1));
        thumbnailVersion = Long.MIN_VALUE;
    }

    private MaterialButton backgroundAction(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTypeface(Fonts.semibold(this));
        button.setCornerRadius(dp(24));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setBackgroundTintList(ColorStateList.valueOf(
                materialColor(com.google.android.material.R.attr.colorSecondaryContainer)));
        button.setTextColor(materialColor(
                com.google.android.material.R.attr.colorOnSecondaryContainer));
        return button;
    }

    private void chooseBackgroundPicture() {
        Intent intent = new Intent(Build.VERSION.SDK_INT >= 33
                ? MediaStore.ACTION_PICK_IMAGES : Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        if (Build.VERSION.SDK_INT < 33) {
            intent.addCategory(Intent.CATEGORY_OPENABLE);
        }
        startActivityForResult(intent, PICK_BACKGROUND);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_BACKGROUND || result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        android.net.Uri uri = data.getData();
        pictureWorker.execute(() -> {
            try {
                BackgroundStore.importSource(this, uri);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        recreate();
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.picture_import_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void addColoursRow(LinearLayout card) {
        LinearLayout colours = row(card, true);
        colours.setOrientation(LinearLayout.VERTICAL);
        colours.setGravity(Gravity.START);
        LinearLayout description = column();
        description.addView(text(getString(R.string.colours), 15, true));
        colours.addView(description, new LinearLayout.LayoutParams(-1, -2));
        String[] labels = getResources().getStringArray(R.array.colour_sources);
        String selected = Store.prefs(this).getString("colour_source", "wallpaper");
        int selectedIndex = 0;
        for (int index = 0; index < COLOUR_VALUES.length; index++) {
            if (COLOUR_VALUES[index].equals(selected)) {
                selectedIndex = index;
                break;
            }
        }
        detail(description, labels[selectedIndex]);
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        ChipGroup chips = new ChipGroup(this);
        chips.setSingleLine(true);
        chips.setSingleSelection(true);
        chips.setSelectionRequired(true);
        chips.setChipSpacingHorizontal(dp(8));
        chips.setPadding(0, dp(8), 0, dp(4));
        scroller.addView(chips);
        colours.addView(scroller, new LinearLayout.LayoutParams(-1, dp(64)));
        int selectedId = View.NO_ID;
        for (int index = 0; index < COLOUR_VALUES.length; index++) {
            final int choice = index;
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setText(labels[index]);
            chip.setTextSize(12);
            chip.setTypeface(Fonts.medium(this));
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipMinHeight(dp(48));
            chip.setEnsureMinTouchTargetSize(true);
            chip.setChipCornerRadius(dp(24));
            chip.setChipBackgroundColor(toggleBackground());
            chip.setTextColor(toggleForeground());
            chip.setChipStrokeWidth(dp(1));
            chip.setChipStrokeColor(ColorStateList.valueOf(hairline));
            if (index >= 3) {
                chip.setChipIconResource(R.drawable.ic_colour_dot);
                chip.setChipIconTint(ColorStateList.valueOf(PRESET_COLOURS[index]));
                chip.setChipIconVisible(true);
            }
            chip.setOnClickListener(view -> {
                Store.prefs(this).edit().putString("colour_source", COLOUR_VALUES[choice]).apply();
                recreate();
            });
            chips.addView(chip, new LinearLayout.LayoutParams(-2, dp(48)));
            if (index == selectedIndex) {
                selectedId = chip.getId();
            }
        }
        chips.check(selectedId);
    }

    private void addQuranResumeRow(LinearLayout card) {
        LinearLayout item = row(card, true);
        LinearLayout label = description(item, R.string.quran_auto_resume);
        detail(label, getString(R.string.quran_auto_resume_help));
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setContentDescription(getString(R.string.quran_auto_resume));
        toggle.setChecked(Store.prefs(this).getBoolean("quran_auto_resume", true));
        toggle.setOnCheckedChangeListener((button, checked) ->
                Store.prefs(this).edit().putBoolean("quran_auto_resume", checked).apply());
        item.addView(toggle);
    }

    private void addLanguageRow(LinearLayout card) {
        LinearLayout language = row(card, true);
        description(language, R.string.language);
        MaterialButtonToggleGroup segments = new MaterialButtonToggleGroup(this, null,
                com.google.android.material.R.attr.materialButtonToggleGroupStyle);
        segments.setSingleSelection(true);
        segments.setSelectionRequired(true);
        segments.setBackground(shape(hairline, 24));
        language.addView(segments);
        MaterialButton en = addLanguageOption(segments, "en");
        MaterialButton ms = addLanguageOption(segments, "ms");
        segments.check("ms".equals(Store.prefs(this).getString("lang", "en"))
                ? ms.getId() : en.getId());
        segments.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                Store.prefs(this).edit().putString("lang",
                        checkedId == ms.getId() ? "ms" : "en").apply();
                recreate();
            }
        });
    }

    private MaterialButton addLanguageOption(MaterialButtonToggleGroup segments, String lang) {
        MaterialButton option = new MaterialButton(this);
        option.setId(View.generateViewId());
        option.setText(lang.equals("en") ? "EN" : "BM");
        option.setTextSize(13);
        option.setStateListAnimator(null);
        option.setTypeface(Fonts.semibold(this));
        option.setMinWidth(0);
        option.setMinimumWidth(dp(52));
        option.setMinimumHeight(dp(48));
        option.setPadding(dp(10), 0, dp(10), 0);
        option.setCheckable(true);
        option.setStrokeWidth(0);
        option.setBackgroundTintList(toggleBackground());
        option.setTextColor(toggleForeground());
        option.setContentDescription(getString(lang.equals("en")
                ? R.string.english : R.string.malay));
        segments.addView(option, new LinearLayout.LayoutParams(dp(54), dp(48)));
        return option;
    }

    private void addFooter(LinearLayout content) {
        TextView help = text(getString(R.string.auto_help), 11, false);
        help.setTextColor(muted);
        help.setPadding(dp(8), 0, dp(8), dp(8));
        content.addView(help);
    }

    private int filledControlColor(Tone tone) {
        return materialColor(androidx.appcompat.R.attr.colorPrimary);
    }

    private int filledControlTextColor(Tone tone) {
        return materialColor(com.google.android.material.R.attr.colorOnPrimary);
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

    private MaterialButton textButton(int label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextColor(accent);
        button.setTypeface(Fonts.semibold(this));
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setStrokeWidth(0);
        button.setMinimumWidth(dp(48));
        button.setMinWidth(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinHeight(dp(48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private MaterialButton pill(String label) {
        Tone tone = Tone.current(this);
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setStateListAnimator(null);
        button.setTextSize(12);
        button.setTypeface(Fonts.semibold(this));
        button.setTextColor(filledControlTextColor(tone));
        button.setCornerRadius(dp(24));
        button.setBackgroundTintList(ColorStateList.valueOf(
                filledControlColor(tone)));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(48));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setAllCaps(false);
        return button;
    }

    private void updateStates() {
        updatePrayerAlertState();
        boolean access = WidgetData.hasMediaAccess(this);
        allowed.setVisibility(access ? View.VISIBLE : View.GONE);
        grant.setVisibility(access ? View.GONE : View.VISIBLE);
        updateParkedLocationState();
        SharedPreferences prefs = Store.prefs(this);
        long backgroundVersion = prefs.getLong("background_at", 0);
        if (thumbnailVersion != backgroundVersion) {
            thumbnailVersion = backgroundVersion;
            android.graphics.Bitmap image = BackgroundStore.load(this);
            backgroundThumbnail.setImageBitmap(image);
            backgroundThumbnail.setVisibility(image != null ? View.VISIBLE : View.GONE);
            backgroundPlaceholder.setVisibility(image != null ? View.GONE : View.VISIBLE);
            removeBackground.setVisibility(image != null ? View.VISIBLE : View.GONE);
        }
        zoneDetail.setText(locationMessage != null ? locationMessage
                : WidgetData.zone(this) + " · " + WidgetData.zoneName(this));
        WeatherRepository.Snapshot weather = WeatherRepository.snapshot(this);
        boolean valid = weather != null && !Double.isNaN(weather.tempC);
        boolean stale = valid && weather.observedAtMillis > 0
                && System.currentTimeMillis() - weather.observedAtMillis > 60 * 60 * 1000L;
        weatherValue.setText(valid ? Math.round(weather.tempC) + "°" : DASH);
        weatherValue.setTextColor(stale ? muted : ink);
        String place = weather == null || weather.place == null || weather.place.isEmpty()
                ? DASH : weather.place;
        String when = weather == null || weather.observedAtMillis <= 0 ? DASH
                : new SimpleDateFormat("HH:mm", AppLocale.locale(this))
                        .format(new Date(weather.observedAtMillis));
        weatherDetail.setText(weather == null ? DASH
                : getString(R.string.weather_place_updated, place, when));
        weatherDetail.setTextColor(muted);
    }

    private void pickZone() {
        ZoneCatalog.Zone[] zones = ZoneCatalog.options();
        String[] labels = new String[zones.length];
        for (int index = 0; index < zones.length; index++) {
            labels[index] = zones[index].code + " · " + zones[index].name;
        }
        new MaterialAlertDialogBuilder(this)
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
        if (request == REQUEST_PRAYER_NOTIFICATIONS) {
            updatePrayerAlertState();
            PrayerAlerts.refresh(this);
            return;
        }
        if (request == REQUEST_PARKED_LOCATION) {
            parkedLocationDenied = !hasParkedLocationPermission();
            updateParkedLocationState();
            return;
        }
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
        WeatherRepository.start(this, weatherCallback);
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
        WeatherRepository.stop(weatherCallback);
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
