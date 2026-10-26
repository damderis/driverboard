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

    RoutineSettings(AppCompatActivity activity) {
        this.activity = activity;
        routines = Store.routines(activity);
    }

    void attach(LinearLayout parent) {
        container = column();
        container.setPadding(0, dp(20), 0, dp(8));
        parent.addView(container);
        render();
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
            row.addView(label((index + 1) + ". " + QuranCatalog.get(number).name, 15),
                    new LinearLayout.LayoutParams(0, -2, 1));
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
        MaterialButton reciter = button(R.string.routine_reciter, () ->
                new MaterialAlertDialogBuilder(activity).setTitle(R.string.routine_reciter)
                        .setSingleChoiceItems(new String[] {"Husary", "Minshawi Murattal"},
                                routine.qariId == 1 ? 0 : 1, (dialog, which) -> {
                                    routine.qariId = which == 0 ? 1 : 0;
                                    save();
                                    dialog.dismiss();
                                    render();
                                }).show());
        reciter.setText(activity.getString(R.string.routine_reciter_value,
                routine.qariId == 1 ? "Husary" : "Minshawi Murattal"));
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
                .setNegativeButton(android.R.string.cancel, null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            routine.surahs.add(matches.get(position).number);
            save();
            dialog.dismiss();
            render();
        });
        dialog.show();
    }
}
