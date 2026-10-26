package local.driveboard;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Phone-only editor; every change is saved immediately. */
final class RoutineSettings {
    private final AppCompatActivity activity;
    private final List<Routine> routines;
    private LinearLayout container;
    private Runnable refreshQaris;

    RoutineSettings(AppCompatActivity activity) {
        this.activity = activity;
        routines = Store.routines(activity);
    }

    void attach(LinearLayout parent) {
        container = column();
        container.setPadding(0, dp(20), 0, dp(8));
        parent.addView(container);
        render();
        QariCatalog.refresh(activity, () -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                render();
                if (refreshQaris != null) { refreshQaris.run(); }
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView label(String value, int size) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTypeface(Fonts.semibold(activity));
        text.setTextColor(MaterialColors.getColor(activity,
                com.google.android.material.R.attr.colorOnSurface, 0));
        return text;
    }

    private MaterialButton button(int text, Runnable action) {
        MaterialButton button = new MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setAllCaps(false);
        button.setCornerRadius(dp(24));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void save() {
        Store.saveRoutines(activity, routines);
    }

    private void render() {
        container.removeAllViews();
        container.addView(label(activity.getString(R.string.routine_title), 22));
        TextView help = label(activity.getString(R.string.routine_help), 13);
        help.setPadding(0, dp(8), 0, dp(8));
        container.addView(help);
        for (Routine routine : routines) {
            addCard(routine);
        }
        container.addView(button(R.string.routine_add, () -> {
            routines.add(new Routine(UUID.randomUUID().toString(),
                    activity.getString(R.string.routine_new), true,
                    EnumSet.allOf(DayOfWeek.class), "05:00", "11:00",
                    new ArrayList<>(List.of(55, 56)), 1));
            save();
            render();
        }));
    }

    private void addCard(Routine routine) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setRadius(dp(28));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setCardBackgroundColor(MaterialColors.getColor(activity,
                com.google.android.material.R.attr.colorSurfaceContainerHigh, 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        container.addView(card, params);
        LinearLayout body = column();
        body.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.addView(body);

        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(routine.name, 18);
        name.setContentDescription(activity.getString(R.string.routine_rename));
        name.setOnClickListener(view -> rename(routine));
        heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialSwitch enabled = new MaterialSwitch(activity);
        enabled.setContentDescription(activity.getString(R.string.routine_enabled, routine.name));
        enabled.setChecked(routine.enabled);
        enabled.setOnCheckedChangeListener((view, checked) -> {
            routine.enabled = checked;
            save();
        });
        heading.addView(enabled);
        body.addView(heading);
        body.addView(button(R.string.routine_rename, () -> rename(routine)));

        ChipGroup days = new ChipGroup(activity);
        String[] names = activity.getResources().getStringArray(R.array.routine_days);
        for (DayOfWeek day : DayOfWeek.values()) {
            Chip chip = new Chip(activity);
            chip.setText(names[day.getValue() - 1]);
            chip.setCheckable(true);
            chip.setChecked(routine.days.contains(day));
            chip.setOnCheckedChangeListener((view, checked) -> {
                if (checked) {
                    routine.days.add(day);
                } else {
                    routine.days.remove(day);
                }
                save();
            });
            days.addView(chip);
        }
        body.addView(days);
        MaterialButton start = button(R.string.routine_start, () -> time(routine, true));
        start.setText(activity.getString(R.string.routine_start_value, routine.startTime));
        body.addView(start);
        MaterialButton end = button(R.string.routine_end, () -> time(routine, false));
        end.setText(activity.getString(R.string.routine_end_value, routine.endTime));
        body.addView(end);
        body.addView(label(activity.getString(R.string.routine_surahs), 15));
        for (int index = 0; index < routine.surahs.size(); index++) {
            final int position = index;
            int number = routine.surahs.get(index);
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            body.addView(label((index + 1) + ". " + QuranCatalog.get(number).name, 15));
            MaterialButton up = button(R.string.routine_up, () -> move(routine, position, -1));
            up.setText("↑");
            up.setContentDescription(activity.getString(R.string.routine_up));
            up.setMinimumWidth(0);
            up.setMinWidth(0);
            up.setPadding(0, 0, 0, 0);
            up.setEnabled(position > 0);
            row.addView(up, new LinearLayout.LayoutParams(dp(48), -2));
            MaterialButton down = button(R.string.routine_down, () -> move(routine, position, 1));
            down.setText("↓");
            down.setContentDescription(activity.getString(R.string.routine_down));
            down.setMinimumWidth(0);
            down.setMinWidth(0);
            down.setPadding(0, 0, 0, 0);
            down.setEnabled(position < routine.surahs.size() - 1);
            row.addView(down, new LinearLayout.LayoutParams(dp(48), -2));
            MaterialButton remove = button(R.string.routine_remove, () -> {
                routine.surahs.remove(position);
                save();
                render();
            });
            remove.setContentDescription(activity.getString(R.string.routine_remove_surah,
                    QuranCatalog.get(number).name));
            row.addView(remove);
            body.addView(row);
        }
        body.addView(button(R.string.routine_add_surah, () -> pickSurah(routine)));
        MaterialButton repeat = button(R.string.routine_repeat, () ->
                new MaterialAlertDialogBuilder(activity).setTitle(R.string.routine_repeat)
                        .setSingleChoiceItems(new String[] {"1", "2", "3", "4", "5"},
                                routine.repeatCount - 1, (dialog, which) -> {
                                    routine.repeatCount = which + 1;
                                    save();
                                    dialog.dismiss();
                                    render();
                                }).show());
        repeat.setText(activity.getString(R.string.routine_repeat_value, routine.repeatCount));
        body.addView(repeat);
        MaterialButton reciter = button(R.string.routine_reciter, () -> pickReciter(routine));
        String qariName = "Qari #" + routine.qariId;
        for (QariCatalog.Entry entry : QariCatalog.entries(activity)) {
            if (entry.id == routine.qariId) { qariName = entry.name; break; }
        }
        reciter.setText(activity.getString(R.string.routine_reciter_value, qariName));
        body.addView(reciter);
        MaterialButton play = button(R.string.routine_play, () ->
                RoutinePlayer.get(activity).playNow(routine));
        play.setEnabled(!routine.surahs.isEmpty());
        body.addView(play);
        body.addView(button(R.string.routine_delete, () -> {
            routines.remove(routine);
            save();
            render();
        }));
    }

    private void rename(Routine routine) {
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setText(routine.name);
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.routine_rename).setView(name)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String value = name.getText().toString().trim();
                    if (!value.isEmpty()) {
                        routine.name = value;
                        save();
                        render();
                    }
                }).show();
    }

    private void time(Routine routine, boolean start) {
        LocalTime current = LocalTime.parse(start ? routine.startTime : routine.endTime);
        MaterialTimePicker picker = new MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(current.getHour()).setMinute(current.getMinute())
                .setTitleText(start ? R.string.routine_start : R.string.routine_end).build();
        picker.addOnPositiveButtonClickListener(view -> {
            String value = String.format(Locale.ROOT, "%02d:%02d", picker.getHour(), picker.getMinute());
            if ((start && value.compareTo(routine.endTime) > 0)
                    || (!start && value.compareTo(routine.startTime) < 0)) {
                Toast.makeText(activity, R.string.routine_time_invalid, Toast.LENGTH_LONG).show();
                return;
            }
            if (start) {
                routine.startTime = value;
            } else {
                routine.endTime = value;
            }
            save();
            render();
        });
        picker.show(activity.getSupportFragmentManager(), "routine_time");
    }

    private void pickSurah(Routine routine) {
        LinearLayout content = column();
        content.setPadding(dp(20), 0, dp(20), 0);
        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint(R.string.routine_search);
        content.addView(search);
        ListView list = new ListView(activity);
        content.addView(list, new LinearLayout.LayoutParams(-1, dp(320)));
        List<QuranCatalog.Entry> matches = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1);
        list.setAdapter(adapter);
        Runnable filter = () -> {
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            matches.clear();
            adapter.clear();
            QuranCatalog.Entry exact = QuranCatalog.match(query);
            for (QuranCatalog.Entry entry : QuranCatalog.all()) {
                if (query.isEmpty() || entry == exact || String.valueOf(entry.number).contains(query)
                        || entry.name.toLowerCase(Locale.ROOT).contains(query)
                        || entry.arabicName.contains(query)) {
                    matches.add(entry);
                    adapter.add(entry.number + ". " + entry.name);
                }
            }
            adapter.notifyDataSetChanged();
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                filter.run();
            }

            @Override
            public void afterTextChanged(Editable text) {
            }
        });
        filter.run();
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.routine_add_surah).setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.routine_add_surah, null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            addSurahs(routine, String.valueOf(matches.get(position).number), dialog);
        });
        dialog.show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view ->
                addSurahs(routine, search.getText().toString(), dialog));
    }

    private void addSurahs(Routine routine, String input, androidx.appcompat.app.AlertDialog dialog) {
        try {
            List<Integer> added = RoutineInputs.parseSurahs(input);
            if (routine.surahs.size() + added.size() > 30) { throw new IllegalArgumentException(); }
            routine.surahs.addAll(added);
            save();
            dialog.dismiss();
            render();
        } catch (IllegalArgumentException error) {
            Toast.makeText(activity, R.string.routine_surah_invalid, Toast.LENGTH_LONG).show();
        }
    }

    private void move(Routine routine, int position, int delta) {
        java.util.Collections.swap(routine.surahs, position, position + delta);
        save();
        render();
    }

    private void pickReciter(Routine routine) {
        LinearLayout content = column();
        content.setPadding(dp(20), 0, dp(20), 0);
        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint(R.string.routine_qari_search);
        content.addView(search);
        ListView list = new ListView(activity);
        content.addView(list, new LinearLayout.LayoutParams(-1, dp(360)));
        List<QariCatalog.Entry> matches = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
                android.R.layout.simple_list_item_2, android.R.id.text1) {
            @Override
            public boolean areAllItemsEnabled() { return false; }
            @Override
            public boolean isEnabled(int position) {
                return matches.get(position).status != QariCatalog.UNPLAYABLE;
            }
            @Override
            public android.view.View getView(int position, android.view.View recycled,
                    android.view.ViewGroup parent) {
                android.view.View view = super.getView(position, recycled, parent);
                QariCatalog.Entry entry = matches.get(position);
                TextView title = view.findViewById(android.R.id.text1);
                TextView note = view.findViewById(android.R.id.text2);
                title.setText((entry.id == routine.qariId ? "✓ " : "") + entry.name);
                note.setText(entry.status == QariCatalog.UNPLAYABLE
                        ? activity.getString(R.string.routine_qari_unplayable)
                        : entry.status == QariCatalog.UNKNOWN
                                ? activity.getString(R.string.routine_qari_unknown) : "");
                note.setVisibility(note.getText().length() == 0
                        ? android.view.View.GONE : android.view.View.VISIBLE);
                view.setEnabled(isEnabled(position));
                view.setAlpha(isEnabled(position) ? 1f : 0.45f);
                return view;
            }
        };
        list.setAdapter(adapter);
        Runnable filter = () -> {
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            matches.clear();
            adapter.clear();
            List<QariCatalog.Entry> entries = new ArrayList<>(QariCatalog.entries(activity));
            boolean savedPresent = false;
            for (QariCatalog.Entry entry : entries) {
                if (entry.id == routine.qariId) { savedPresent = true; }
            }
            if (!savedPresent) {
                entries.add(new QariCatalog.Entry(routine.qariId, "Qari #" + routine.qariId,
                        QariCatalog.UNKNOWN));
            }
            entries.sort(java.util.Comparator.comparingInt((QariCatalog.Entry entry) ->
                    entry.status == QariCatalog.PLAYABLE ? 0 :
                            entry.status == QariCatalog.UNKNOWN ? 1 : 2)
                    .thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
            for (QariCatalog.Entry entry : entries) {
                if (!entry.name.toLowerCase(Locale.ROOT).contains(query)
                        && !String.valueOf(entry.id).equals(query)) { continue; }
                matches.add(entry);
                adapter.add(entry.name);
            }
            adapter.notifyDataSetChanged();
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) { filter.run(); }
            @Override
            public void afterTextChanged(Editable text) {}
        });
        filter.run();
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.routine_reciter).setView(content)
                .setNegativeButton(android.R.string.cancel, null).create();
        refreshQaris = filter;
        dialog.setOnDismissListener(ignored -> refreshQaris = null);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (!adapter.isEnabled(position)) { return; }
            routine.qariId = matches.get(position).id;
            save();
            dialog.dismiss();
            render();
        });
        dialog.show();
    }
}
