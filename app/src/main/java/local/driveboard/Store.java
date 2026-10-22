package local.driveboard;

import android.content.Context;
import android.content.SharedPreferences;

public final class Store {
    public static final String FULL = "full";
    public static final String SPLIT = "split";
    public static final String NARROW = "narrow";
    private Store() {}
    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("driveboard", Context.MODE_PRIVATE);
    }

    public static String layoutKey(String mode) {
        return SPLIT.equals(mode) ? "split_layout" : "full_layout";
    }

    public static BoardLayout layout(Context context, String mode) {
        if (NARROW.equals(mode)) {
            return BoardLayout.narrowDefaults(WidgetData.mediaSnapshot(context).playing,
                    layout(context, SPLIT));
        }
        SharedPreferences p = prefs(context);
        String saved = p.getString(layoutKey(mode), null);
        String legacy = p.getString("layout", null);
        boolean split = SPLIT.equals(mode);
        BoardLayout layout = BoardLayout.fromSaved(saved, legacy, split);
        boolean legacyFull = !split && saved == null && p.contains("layout");
        if (legacyFull || (saved != null && saved.contains("\"battery\""))) {
            SharedPreferences.Editor edit = p.edit().putString(layoutKey(mode), layout.toJson());
            if (legacyFull) {
                edit.remove("layout");
            }
            edit.apply();
        }
        return layout;
    }
}
