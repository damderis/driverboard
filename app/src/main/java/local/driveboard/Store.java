package local.driveboard;

import android.content.Context;
import android.content.SharedPreferences;

public final class Store {
    private Store() {}
    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("driveboard", Context.MODE_PRIVATE);
    }
}
