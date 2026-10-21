package local.driveboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.Manifest;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared phone and car weather feed. Network and cache writes run off the main thread. */
public final class WeatherRepository {
    public interface Callback {
        void onWeather(Snapshot snapshot);
    }

    public static final class Snapshot {
        public final double tempC;
        public final String condition;
        public final int rainProbability;
        public final long observedAtMillis;
        public final String place;
        public final int weatherCode;
        public final boolean isDay;

        Snapshot(double tempC, int weatherCode, boolean isDay, int rainProbability,
                long observedAtMillis, String place) {
            this.tempC = tempC;
            this.weatherCode = weatherCode;
            this.isDay = isDay;
            this.rainProbability = rainProbability;
            this.observedAtMillis = observedAtMillis;
            this.place = place;
            this.condition = WeatherCode.description(weatherCode);
        }
    }

    private static final String TAG = "DriveBoardWeather";
    private static final long REFRESH_MS = 15 * 60 * 1000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final Set<Callback> CLIENTS = new HashSet<>();
    private static Context app;
    private static boolean inFlight;
    private static final Runnable TICK = WeatherRepository::refreshIfDue;
    private static final SharedPreferences.OnSharedPreferenceChangeListener PREFS_LISTENER =
            (prefs, key) -> {
                if ("zone".equals(key)) {
                    MAIN.post(WeatherRepository::refreshIfDue);
                }
            };

    private WeatherRepository() {}

    /** Begins shared periodic updates; the listener immediately receives any saved reading. */
    public static void start(Context context, Callback callback) {
        Context application = context.getApplicationContext();
        MAIN.post(() -> {
            app = application;
            if (CLIENTS.isEmpty()) {
                Store.prefs(application).registerOnSharedPreferenceChangeListener(PREFS_LISTENER);
            }
            CLIENTS.add(callback);
            callback.onWeather(snapshot(application));
            refreshIfDue();
        });
    }

    public static void stop(Callback callback) {
        MAIN.post(() -> {
            CLIENTS.remove(callback);
            if (CLIENTS.isEmpty()) {
                MAIN.removeCallbacks(TICK);
                if (app != null) {
                    Store.prefs(app).unregisterOnSharedPreferenceChangeListener(PREFS_LISTENER);
                }
            }
        });
    }

    public static Snapshot snapshot(Context context) {
        SharedPreferences prefs = Store.prefs(context);
        long fetched = prefs.getLong("weather_fetched_at", 0);
        if (fetched <= 0 || !prefs.contains("weather_temp_c")) {
            return null;
        }
        double temp = Double.longBitsToDouble(prefs.getLong("weather_temp_c", 0));
        if (!Double.isFinite(temp)) {
            return null;
        }
        String source = prefs.getString("weather_source", "");
        String place = "phone".equals(source)
                ? AppLocale.wrap(context).getString(R.string.weather_your_location)
                : prefs.getString("weather_place", "");
        return new Snapshot(temp, prefs.getInt("weather_code", -1),
                prefs.getBoolean("weather_is_day", true),
                prefs.getInt("weather_next_hour_rain", -1), fetched, place);
    }

    private static void refreshIfDue() {
        MAIN.removeCallbacks(TICK);
        if (CLIENTS.isEmpty() || app == null) {
            return;
        }
        Snapshot cached = snapshot(app);
        long now = System.currentTimeMillis();
        boolean hasPermission = app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        SharedPreferences prefs = Store.prefs(app);
        String savedSource = prefs.getString("weather_source", "");
        boolean sourceChanged = prefs.getBoolean("weather_permission", false) != hasPermission
                || savedSource.startsWith("zone:")
                && !savedSource.equals("zone:" + WidgetData.zone(app));
        if (!inFlight && (cached == null || now - cached.observedAtMillis >= REFRESH_MS
                || now < cached.observedAtMillis || sourceChanged)) {
            inFlight = true;
            Context context = app;
            ZoneLocator.coordinates(context, location -> fetchAt(context, location));
        } else if (!inFlight) {
            MAIN.postDelayed(TICK, Math.max(1000, REFRESH_MS
                    - (now - cached.observedAtMillis)));
        }
    }

    private static void fetchAt(Context context, Location location) {
        if (CLIENTS.isEmpty()) {
            inFlight = false;
            return;
        }
        ZoneCatalog.Centre centre = ZoneCatalog.centre(WidgetData.zone(context));
        double latitude = location == null ? centre.latitude : location.getLatitude();
        double longitude = location == null ? centre.longitude : location.getLongitude();
        String place = location == null ? centre.place
                : AppLocale.wrap(context).getString(R.string.weather_your_location);
        String source = location == null ? "zone:" + WidgetData.zone(context) : "phone";
        boolean hadPermission = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        NETWORK.execute(() -> {
            Snapshot result = null;
            boolean obsolete = false;
            try {
                result = fetch(latitude, longitude, place);
                obsolete = source.startsWith("zone:")
                        && !source.equals("zone:" + WidgetData.zone(context));
                if (!obsolete) {
                    save(context, result, source, hadPermission);
                }
            } catch (Exception e) {
                Log.w(TAG, "Weather refresh unavailable", e);
            }
            Snapshot latest = result;
            boolean sourceObsolete = obsolete;
            MAIN.post(() -> {
                inFlight = false;
                if (sourceObsolete) {
                    refreshIfDue();
                } else if (latest != null) {
                    for (Callback callback : new HashSet<>(CLIENTS)) {
                        callback.onWeather(latest);
                    }
                    // A zone may have changed while the previous request was in flight.
                    refreshIfDue();
                } else if (!CLIENTS.isEmpty()) {
                    MAIN.postDelayed(TICK, REFRESH_MS);
                }
            });
        });
    }

    private static Snapshot fetch(double latitude, double longitude, String place) throws Exception {
        String address = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f"
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&hourly=precipitation_probability&forecast_hours=2&timezone=auto",
                latitude, longitude);
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        try {
            if (connection.getResponseCode() != 200) {
                throw new java.io.IOException("Weather HTTP " + connection.getResponseCode());
            }
            JSONObject response = new JSONObject(WidgetData.readLimited(connection, 256 * 1024));
            JSONObject current = response.getJSONObject("current");
            double temp = current.getDouble("temperature_2m");
            int code = current.getInt("weather_code");
            if (!Double.isFinite(temp) || temp < -100 || temp > 70 || code < 0 || code > 99) {
                throw new java.io.IOException("Invalid weather reading");
            }
            boolean isDay = current.getInt("is_day") == 1;
            int rain = nextHourRain(response.optJSONObject("hourly"),
                    current.getString("time"));
            return new Snapshot(temp, code, isDay, rain, System.currentTimeMillis(), place);
        } finally {
            connection.disconnect();
        }
    }

    static int nextHourRain(JSONObject hourly, String currentTime) {
        if (hourly == null) {
            return -1;
        }
        JSONArray times = hourly.optJSONArray("time");
        JSONArray chances = hourly.optJSONArray("precipitation_probability");
        if (times == null || chances == null) {
            return -1;
        }
        for (int i = 0; i < Math.min(times.length(), chances.length()); i++) {
            if (times.optString(i, "").compareTo(currentTime) > 0) {
                int chance = chances.optInt(i, -1);
                return chance >= 0 && chance <= 100 ? chance : -1;
            }
        }
        return -1;
    }

    private static void save(Context context, Snapshot reading, String source,
            boolean hadPermission) {
        Store.prefs(context).edit()
                .putLong("weather_temp_c", Double.doubleToRawLongBits(reading.tempC))
                .putInt("weather_code", reading.weatherCode)
                .putBoolean("weather_is_day", reading.isDay)
                .putInt("weather_next_hour_rain", reading.rainProbability)
                .putLong("weather_fetched_at", reading.observedAtMillis)
                .putString("weather_place", reading.place)
                .putString("weather_source", source)
                .putBoolean("weather_permission", hadPermission)
                .apply();
    }
}
