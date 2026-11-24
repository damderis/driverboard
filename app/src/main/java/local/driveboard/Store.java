package local.driveboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Store {
    public static final String FULL = "full";
    public static final String SPLIT = "split";
    public static final String NARROW = "narrow";
    private Store() {}
    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("driveboard", Context.MODE_PRIVATE);
    }

    /** Car pages in action-strip order; page 0 keeps the original layout keys. */
    public static final int[] PAGE_NAMES = {R.string.page_drive, R.string.page_media,
            R.string.page_controls};

    public static int page(Context context) {
        return BoardLayout.clamp(prefs(context).getInt("car_page", 0), 0, PAGE_NAMES.length - 1);
    }

    public static void setPage(Context context, int page) {
        prefs(context).edit().putInt("car_page", Math.floorMod(page, PAGE_NAMES.length)).apply();
    }

    public static String layoutKey(String mode, int page) {
        String base = SPLIT.equals(mode) ? "split_layout" : "full_layout";
        return page == 0 ? base : base + "_p" + page;
    }

    public static BoardLayout layout(Context context, String mode) {
        return layout(context, mode, page(context));
    }

    public static BoardLayout layout(Context context, String mode, int page) {
        if (NARROW.equals(mode)) {
            // ponytail: the narrow strip always derives from the Drive page; give pages
            // their own narrow layouts if the e.MAS ever shows a narrow panel with pages.
            return BoardLayout.narrowDefaults(WidgetData.mediaSnapshot(context).playing,
                    layout(context, SPLIT, 0));
        }
        SharedPreferences p = prefs(context);
        boolean split = SPLIT.equals(mode);
        String key = layoutKey(mode, page);
        String saved = p.getString(key, null);
        if (page > 0) {
            return saved == null ? BoardLayout.pageDefaults(page, split)
                    : BoardLayout.fromJson(saved, split);
        }
        String legacy = p.getString("layout", null);
        BoardLayout layout = BoardLayout.fromSaved(saved, legacy, split);
        boolean legacyFull = !split && saved == null && p.contains("layout");
        if (legacyFull || (saved != null && !saved.equals(layout.toJson()))) {
            SharedPreferences.Editor edit = p.edit().putString(key, layout.toJson());
            if (legacyFull) {
                edit.remove("layout");
            }
            edit.apply();
        }
        return layout;
    }
}
