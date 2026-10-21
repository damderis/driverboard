package local.driveboard;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public final class AppLocale {
    private AppLocale() {}

    public static Context wrap(Context context) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(Store.prefs(context).getString("lang", "en")));
        return context.createConfigurationContext(config);
    }

    public static Locale locale(Context context) {
        return wrap(context).getResources().getConfiguration().getLocales().get(0);
    }
}
