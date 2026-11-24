package local.driveboard;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.SectionedItemList;
import androidx.car.app.model.Template;

/**
 * Host-drawn controls. Android Auto renders and delivers these rows itself, so they work
 * on head units that never pass surface taps to the board.
 */
public final class ControlsScreen extends Screen {
    private final BoardScreen board;

    ControlsScreen(@NonNull CarContext context, BoardScreen board) {
        super(context);
        this.board = board;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        Context words = AppLocale.wrap(getCarContext());
        AudioSwitchClient.State audio = AudioSwitchClient.read(getCarContext());
        ListTemplate.Builder template = new ListTemplate.Builder()
                .setTitle(words.getString(R.string.page_controls))
                .setHeaderAction(Action.BACK);

        ItemList.Builder sound = new ItemList.Builder();
        if (!audio.installed) {
            sound.addItem(new Row.Builder().setTitle(words.getString(R.string.audio_missing)).build());
        } else {
            String toggle = words.getString(audio.enabled ? R.string.audio_on : R.string.audio_off);
            Row.Builder toggleRow = new Row.Builder().setTitle(toggle)
                    .setOnClickListener(() -> audio("toggle"));
            if (audio.enabled && !audio.helpers) {
                toggleRow.addText(words.getString(R.string.audio_helpers_stopped));
            }
            sound.addItem(toggleRow.build());
            sound.addItem(level(words, R.string.audio_car, audio.car, "car"));
            sound.addItem(level(words, R.string.audio_phone, audio.phone, "phone"));
        }
        template.addSectionedList(SectionedItemList.create(sound.build(),
                words.getString(R.string.widget_audio)));

        ItemList.Builder places = new ItemList.Builder();
        for (Shortcut shortcut : Shortcut.values()) {
            places.addItem(new Row.Builder().setTitle(words.getString(shortcut.label))
                    .setOnClickListener(() -> {
                        if (shortcut.action.startsWith("web:") || "lyrics".equals(shortcut.action)) {
                            getScreenManager().pop();
                            board.runShortcut(shortcut.action);
                        } else {
                            BoardScreen.launchShortcut(getCarContext(), shortcut.action);
                        }
                    }).build());
        }
        template.addSectionedList(SectionedItemList.create(places.build(),
                words.getString(R.string.widget_shortcuts)));
        return template.build();
    }

    private Row level(Context words, int label, int value, String key) {
        return new Row.Builder()
                .setTitle(words.getString(label, value))
                .addAction(button(R.drawable.ic_minus, () -> audio(key + "-")))
                .addAction(button(R.drawable.ic_plus, () -> audio(key + "+")))
                .build();
    }

    private Action button(int icon, androidx.car.app.model.OnClickListener listener) {
        return new Action.Builder().setIcon(new androidx.car.app.model.CarIcon.Builder(
                androidx.core.graphics.drawable.IconCompat.createWithResource(getCarContext(), icon))
                .build()).setOnClickListener(listener).build();
    }

    private void audio(String hit) {
        BoardScreen.applyAudio(getCarContext(), hit);
        // Audio Switch writes atomically; read back after a short delay so the row shows
        // the saved value rather than the pre-write state.
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::invalidate, 150);
    }
}
