package local.driveboard;

import android.content.Context;
import android.content.SharedPreferences;

public final class Store {
    public static final String FULL = "full";
    public static final String SPLIT = "split";
    private Store() {}
    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("driveboard", Context.MODE_PRIVATE);
    }

    public static String layoutKey(String mode) {
        return SPLIT.equals(mode) ? "split_layout" : "full_layout";
    }

    public static BoardLayout layout(Context context, String mode) {
        SharedPreferences p = prefs(context);
        if (SPLIT.equals(mode)) {
            return BoardLayout.fromSaved(p.getString(layoutKey(mode), null),
                    p.getString("layout", null), true);
        }
        String saved = p.getString(layoutKey(mode), null);
        String legacy = p.getString("layout", null);
        if (saved == null && p.contains("layout")) {
            p.edit().putString(layoutKey(mode),
                    BoardLayout.fromSaved(saved, legacy, false).toJson())
                    .remove("layout").apply();
        }
        return BoardLayout.fromSaved(saved, legacy, false);
    }
}
