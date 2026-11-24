package local.driveboard;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Reads and changes Audio Switch through its signature-pinned provider. */
public final class AudioSwitchClient {
    static final String AUTHORITY = "local.audio.separator.carcontrol";
    private static final Uri URI = Uri.parse("content://" + AUTHORITY);

    public static final class State {
        public final boolean installed;
        public final boolean enabled;
        public final boolean helpers;
        public final int car;
        public final int phone;
        public final String status;

        State(boolean installed, boolean enabled, boolean helpers, int car, int phone, String status) {
            this.installed = installed;
            this.enabled = enabled;
            this.helpers = helpers;
            this.car = car;
            this.phone = phone;
            this.status = status;
        }
    }

    static final State MISSING = new State(false, false, false, 100, 100, "");

    private AudioSwitchClient() {}

    public static State read(Context context) {
        try (Cursor c = context.getContentResolver().query(URI, null, null, null, null)) {
            if (c == null || !c.moveToFirst()) return MISSING;
            return new State(true, c.getInt(0) == 1, c.getInt(3) == 1, c.getInt(1), c.getInt(2),
                    c.getString(4));
        } catch (RuntimeException error) {
            return MISSING;
        }
    }

    /** Applies "toggle", "car+", "car-", "phone+" or "phone-". Returns null on success, or a reason. */
    public static String apply(Context context, String hit) {
        State state = read(context);
        if (!state.installed) return "not installed";
        Bundle change = new Bundle();
        if ("toggle".equals(hit)) {
            change.putBoolean("enabled", !state.enabled);
        } else {
            String key = hit.substring(0, hit.length() - 1);
            change.putInt(key, Shortcut.volumeStep("car".equals(key) ? state.car : state.phone,
                    hit.endsWith("+") ? 10 : -10));
        }
        return set(context, change);
    }

    /** Returns null on success, or a short reason. */
    public static String set(Context context, Bundle values) {
        try {
            context.getContentResolver().call(URI, "set", null, values);
            return null;
        } catch (RuntimeException error) {
            return error.getClass().getSimpleName();
        }
    }

}
