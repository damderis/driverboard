package local.driveboard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Landscape, edge-to-edge editor for the two independent car screen layouts. */
public final class BoardEditorActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "editor_mode";
    private static final int HISTORY_LIMIT = 40;

    private final Map<String, ArrayDeque<String>> undo = new HashMap<>();
    private final Map<String, ArrayDeque<String>> redo = new HashMap<>();
    private String mode = Store.FULL;
    private BoardLayout layout;
    private BoardLayout.Card selected;
    private String gestureBefore;
    private BoardView board;
    private FrameLayout canvasHolder;
    private LinearLayout root;
    private LinearLayout properties;
    private BottomSheetDialog propertySheet;
    private MaterialButton undoButton;
    private MaterialButton redoButton;
    private int ink;
    private int muted;
    private int surface;
    private int panel;
    private int accent;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (board != null) board.refresh();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String requested = state == null ? getIntent().getStringExtra(EXTRA_MODE)
                : state.getString(EXTRA_MODE);
        mode = Store.SPLIT.equals(requested) ? Store.SPLIT : Store.FULL;
        layout = Store.layout(this, mode);
        ink = color(com.google.android.material.R.attr.colorOnSurface);
        muted = color(com.google.android.material.R.attr.colorOnSurfaceVariant);
        surface = color(com.google.android.material.R.attr.colorSurface);
        panel = color(com.google.android.material.R.attr.colorSurfaceContainer);
        accent = color(androidx.appcompat.R.attr.colorPrimary);
        build();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString(EXTRA_MODE, mode);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int color(int attribute) {
        return MaterialColors.getColor(this, attribute, Color.BLACK);
    }

    private GradientDrawable shape(int fill, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(fill);
        result.setCornerRadius(dp(radius));
        return result;
    }

    private LinearLayout column() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    private MaterialButton button(int label) {
        MaterialButton result = new MaterialButton(this);
        result.setText(label);
        result.setAllCaps(false);
        result.setTextSize(13);
        result.setTypeface(Fonts.semibold(this));
        result.setMinHeight(dp(48));
        result.setMinimumHeight(dp(48));
        result.setInsetTop(0);
        result.setInsetBottom(0);
        result.setCornerRadius(dp(24));
        return result;
    }

    private MaterialButton textButton(int label) {
        MaterialButton result = button(label);
        result.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        result.setTextColor(accent);
        result.setStrokeWidth(0);
        result.setMinWidth(dp(48));
        return result;
    }

    private TextView label(CharSequence value, int size, boolean strong) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(ink);
        result.setTypeface(strong ? Fonts.semibold(this) : Fonts.regular(this));
        result.setGravity(Gravity.CENTER_VERTICAL);
        return result;
    }

    private void build() {
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        root = column();
        root.setBackgroundColor(surface);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), 0, dp(12), 0);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));
        MaterialButton back = textButton(R.string.editor_back);
        back.setText("\u2039");
        back.setContentDescription(getString(R.string.editor_back));
        back.setOnClickListener(view -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = label(getString(R.string.editor_title), 20, true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        undoButton = textButton(R.string.editor_undo);
        undoButton.setOnClickListener(view -> historyStep(undo, redo));
        toolbar.addView(undoButton);
        redoButton = textButton(R.string.editor_redo);
        redoButton.setOnClickListener(view -> historyStep(redo, undo));
        toolbar.addView(redoButton);
        MaterialButton done = button(R.string.editor_done);
        done.setOnClickListener(view -> finish());
        toolbar.addView(done);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(16), 0, dp(16), dp(8));
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(60)));
        addModeSelector(controls);
        MaterialButton reset = textButton(R.string.editor_reset);
        reset.setOnClickListener(view -> confirmReset());
        controls.addView(reset);
        canvasHolder = new FrameLayout(this);
        canvasHolder.setBackground(shape(panel, 24));
        LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(-1, 0, 1);
        canvasParams.leftMargin = dp(16);
        canvasParams.rightMargin = dp(16);
        root.addView(canvasHolder, canvasParams);
        board = new BoardView(this);
        board.setMode(mode);
        board.setEditorLayout(layout);
        board.setEditing(true, this::finishGesture);
        board.setEditStartListener(() -> gestureBefore = layout.toJson());
        board.setSelectionListener(this::select);
        board.setBackground(shape(Tone.current(this).ground, 20));
        board.setClipToOutline(true);
        canvasHolder.addView(board);
        FloatingActionButton add = new FloatingActionButton(this);
        add.setImageResource(android.R.drawable.ic_input_add);
        add.setContentDescription(getString(R.string.editor_add));
        add.setOnClickListener(view -> showCatalog());
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(dp(56), dp(56),
                Gravity.RIGHT | Gravity.BOTTOM);
        addParams.setMargins(0, 0, dp(16), dp(16));
        canvasHolder.addView(add, addParams);
        canvasHolder.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> updateCanvasFit());
        canvasHolder.post(this::updateCanvasFit);

        properties = column();
        properties.setPadding(dp(16), dp(12), dp(16), dp(20));
        propertySheet = new BottomSheetDialog(this);
        propertySheet.setContentView(properties);
        updateHistoryButtons();
    }

    private void addModeSelector(LinearLayout parent) {
        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(this);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        group.setBackground(shape(panel, 24));
        group.setPadding(dp(3), dp(3), dp(3), dp(3));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1);
        params.rightMargin = dp(12);
        parent.addView(group, params);
        MaterialButton full = modeButton(R.string.full_screen);
        MaterialButton split = modeButton(R.string.split_screen);
        group.addView(full, new LinearLayout.LayoutParams(0, dp(46), 1));
        group.addView(split, new LinearLayout.LayoutParams(0, dp(46), 1));
        group.check(Store.SPLIT.equals(mode) ? split.getId() : full.getId());
        group.addOnButtonCheckedListener((buttons, checkedId, checked) -> {
            if (checked) switchMode(checkedId == split.getId() ? Store.SPLIT : Store.FULL);
        });
    }

    private MaterialButton modeButton(int text) {
        MaterialButton result = button(text);
        result.setId(View.generateViewId());
        result.setCheckable(true);
        result.setStrokeWidth(0);
        result.setMinWidth(0);
        result.setBackgroundTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{color(com.google.android.material.R.attr.colorPrimaryContainer), panel}));
        result.setTextColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{color(com.google.android.material.R.attr.colorOnPrimaryContainer), ink}));
        return result;
    }

    private void updateCanvasFit() {
        if (canvasHolder == null || board == null) return;
        int availableW = canvasHolder.getWidth() - dp(24);
        int availableH = canvasHolder.getHeight() - dp(16);
        if (availableW <= 0 || availableH <= 0) return;
        int frameW = Math.max(1, Store.prefs(this).getInt(mode + "_width", 1920));
        int frameH = Math.max(1, Store.prefs(this).getInt(mode + "_height", 1080));
        float scale = Math.min(availableW / (float) frameW, availableH / (float) frameH);
        int fittedW = Math.max(1, Math.round(frameW * scale));
        int fittedH = Math.max(1, Math.round(frameH * scale));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) board.getLayoutParams();
        if (params.width != fittedW || params.height != fittedH) {
            params.width = fittedW;
            params.height = fittedH;
            params.gravity = Gravity.CENTER;
            board.setLayoutParams(params);
        }
    }

    private void switchMode(String next) {
        if (next.equals(mode)) return;
        mode = next;
        selected = null;
        layout = Store.layout(this, mode);
        board.setMode(mode);
        board.setEditorLayout(layout);
        board.selectCard(null);
        updateCanvasFit();
        propertySheet.dismiss();
        updateHistoryButtons();
    }

    private void select(BoardLayout.Card card) {
        selected = card;
        if (card != null && board.isEditorTap()) openProperties();
    }

    private void finishGesture() {
        layout = board.getBoardLayout();
        if (gestureBefore != null) commit(gestureBefore);
        gestureBefore = null;
        showProperties();
    }

    private void commit(String before) {
        String after = layout.toJson();
        if (before.equals(after)) return;
        ArrayDeque<String> stack = undo.computeIfAbsent(mode, key -> new ArrayDeque<>());
        stack.push(before);
        while (stack.size() > HISTORY_LIMIT) stack.removeLast();
        redo.computeIfAbsent(mode, key -> new ArrayDeque<>()).clear();
        persist();
        updateHistoryButtons();
    }

    private void persist() {
        Store.prefs(this).edit().putString(Store.layoutKey(mode), layout.toJson()).apply();
        board.setEditorLayout(layout);
        board.selectCard(selected);
    }

    private void historyStep(Map<String, ArrayDeque<String>> from,
            Map<String, ArrayDeque<String>> to) {
        ArrayDeque<String> source = from.get(mode);
        if (source == null || source.isEmpty()) return;
        to.computeIfAbsent(mode, key -> new ArrayDeque<>()).push(layout.toJson());
        layout = BoardLayout.fromJson(source.pop(), Store.SPLIT.equals(mode));
        selected = null;
        board.selectCard(null);
        persist();
        showProperties();
        updateHistoryButtons();
    }

    private void updateHistoryButtons() {
        undoButton.setEnabled(undo.containsKey(mode) && !undo.get(mode).isEmpty());
        redoButton.setEnabled(redo.containsKey(mode) && !redo.get(mode).isEmpty());
    }

    private void confirmReset() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.editor_reset)
                .setMessage(R.string.editor_reset_message)
                .setNegativeButton(R.string.cancel_action, null)
                .setPositiveButton(R.string.reset_action, (dialog, which) -> {
                    String before = layout.toJson();
                    layout = Store.SPLIT.equals(mode)
                            ? BoardLayout.splitDefaults() : BoardLayout.defaults();
                    selected = null;
                    board.selectCard(null);
                    persist();
                    commit(before);
                    showProperties();
                }).show();
    }

    private void showCatalog() {
        WidgetType[] types = WidgetType.values();
        BottomSheetDialog catalog = new BottomSheetDialog(this);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(12), dp(20), dp(20));
        TextView title = label(getString(R.string.editor_add), 20, true);
        content.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));
        for (WidgetType type : types) {
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            TextView icon = label(type.icon, 22, true);
            icon.setGravity(Gravity.CENTER);
            item.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(56)));
            TextView name = label(getString(type.label), 16, true);
            item.addView(name, new LinearLayout.LayoutParams(0, dp(56), 1));
            content.addView(item);
            item.setOnClickListener(view -> {
                    catalog.dismiss();
                    String before = layout.toJson();
                    BoardLayout.Card added = layout.add(type.id);
                    if (added == null) {
                        noSpace();
                        return;
                    }
                    selected = added;
                    board.selectCard(added);
                    persist();
                    commit(before);
                    openProperties();
                });
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(content);
        catalog.setContentView(scroll);
        catalog.show();
    }

    private void noSpace() {
        Snackbar.make(root, R.string.editor_no_space, Snackbar.LENGTH_SHORT).show();
    }

    private void openProperties() {
        showProperties();
        if (selected != null && !propertySheet.isShowing()) propertySheet.show();
    }

    private void showProperties() {
        properties.removeAllViews();
        if (selected == null || !layout.cards.contains(selected)) {
            selected = null;
            propertySheet.dismiss();
            return;
        }
        WidgetType type = WidgetType.fromId(selected.type);
        if (type == null) return;
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(16), 0, dp(8), 0);
        properties.addView(heading, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView name = label(getString(type.label) + "  "
                + getString(R.string.editor_size, selected.w, selected.h), 15, true);
        heading.addView(name, new LinearLayout.LayoutParams(0, -1, 1));
        MaterialButton duplicate = textButton(R.string.editor_duplicate);
        duplicate.setVisibility(type.multiple ? View.VISIBLE : View.GONE);
        duplicate.setOnClickListener(view -> {
            String before = layout.toJson();
            BoardLayout.Card added = layout.duplicate(selected);
            if (added == null) {
                noSpace();
                return;
            }
            selected = added;
            board.selectCard(added);
            persist();
            commit(before);
            openProperties();
        });
        heading.addView(duplicate);
        MaterialButton delete = textButton(R.string.editor_delete);
        delete.setOnClickListener(view -> {
            String before = layout.toJson();
            layout.remove(selected);
            selected = null;
            board.selectCard(null);
            persist();
            commit(before);
            showProperties();
        });
        heading.addView(delete);

        if (type.options.isEmpty()) return;
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), dp(8));
        scroller.addView(row);
        properties.addView(scroller, new LinearLayout.LayoutParams(-1, dp(56)));
        for (WidgetType.OptionSpec spec : type.options.values()) {
            if ("toggle".equals(spec.kind)) {
                addToggle(row, spec);
            } else if ("choice".equals(spec.kind)) {
                addChoice(row, spec);
            }
        }
    }

    private void addToggle(LinearLayout row, WidgetType.OptionSpec spec) {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(spec.label);
        toggle.setTextSize(13);
        toggle.setTextColor(ink);
        toggle.setChecked("true".equals(selected.options.get(spec.key)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(48));
        params.rightMargin = dp(20);
        row.addView(toggle, params);
        toggle.setOnCheckedChangeListener((control, checked) -> setOption(spec.key,
                Boolean.toString(checked)));
    }

    private void addChoice(LinearLayout row, WidgetType.OptionSpec spec) {
        TextView caption = label(getString(spec.label), 13, false);
        caption.setTextColor(muted);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-2, dp(48));
        captionParams.rightMargin = dp(8);
        row.addView(caption, captionParams);
        MaterialButtonToggleGroup choices = new MaterialButtonToggleGroup(this);
        choices.setSingleSelection(true);
        choices.setSelectionRequired(true);
        choices.setBackground(shape(surface, 22));
        row.addView(choices, new LinearLayout.LayoutParams(-2, dp(44)));
        int checkedId = View.NO_ID;
        for (String value : spec.values) {
            String text = value;
            if ("full".equals(value)) text = getString(R.string.editor_option_full);
            if ("next".equals(value)) text = getString(R.string.editor_option_next);
            MaterialButton choice = modeButton(R.string.editor_title);
            choice.setText(text);
            choice.setMinWidth(dp(54));
            choices.addView(choice, new LinearLayout.LayoutParams(-2, dp(44)));
            if (value.equals(selected.options.get(spec.key))) checkedId = choice.getId();
            final String chosen = value;
            final int choiceId = choice.getId();
            choices.addOnButtonCheckedListener((group, id, checked) -> {
                if (checked && id == choiceId) setOption(spec.key, chosen);
            });
        }
        if (checkedId != View.NO_ID) choices.check(checkedId);
    }

    private void setOption(String key, String value) {
        if (selected == null || value.equals(selected.options.get(key))) return;
        String before = layout.toJson();
        selected.options.put(key, value);
        persist();
        commit(before);
    }
}
