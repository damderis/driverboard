package local.driveboard;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONObject;

/** Coarse phone location to JAKIM zone; a failed lookup never changes the selected zone. */
public final class ZoneLocator {
    private static final String TAG = "DriveBoardZone";
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ZoneLocator() {}

    public interface Callback {
        void onResult(boolean success, String message);
    }

    public static void refresh(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        if (app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            callback.onResult(false,
                    AppLocale.wrap(app).getString(R.string.location_permission_required));
            return;
        }
        LocationManager manager = app.getSystemService(LocationManager.class);
        if (manager == null) {
            callback.onResult(false, AppLocale.wrap(app).getString(R.string.location_no_fix));
            return;
        }
        Location last = null;
        try {
            last = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (RuntimeException e) {
            Log.w(TAG, "Last known network location unavailable", e);
        }
        final Location fallback = last;
        final CancellationSignal signal = new CancellationSignal();
        final AtomicBoolean resolved = new AtomicBoolean();
        final Runnable timeout = () -> {
            if (resolved.compareAndSet(false, true)) {
                signal.cancel();
                resolve(app, fallback, callback);
            }
        };
        MAIN.postDelayed(timeout, 15000);
        try {
            manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, signal,
                    app.getMainExecutor(), location -> {
                if (resolved.compareAndSet(false, true)) {
                    MAIN.removeCallbacks(timeout);
                    resolve(app, location != null ? location : fallback, callback);
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Current network location unavailable", e);
            if (resolved.compareAndSet(false, true)) {
                MAIN.removeCallbacks(timeout);
                resolve(app, fallback, callback);
            }
        }
    }

    private static void resolve(Context app, Location location, Callback callback) {
        long ageNanos = location == null ? Long.MAX_VALUE
                : android.os.SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos();
        if (location == null || ageNanos < 0 || ageNanos > 12L * 60 * 60 * 1_000_000_000L
                || !validCoordinates(location.getLatitude(), location.getLongitude())) {
            callback.onResult(false, AppLocale.wrap(app).getString(R.string.location_no_fix));
            return;
        }
        NETWORK.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(String.format(java.util.Locale.ROOT,
                        "https://api.waktusolat.app/zones/%.6f/%.6f",
                        location.getLatitude(), location.getLongitude()));
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                if (connection.getResponseCode() != 200) {
                    throw new java.io.IOException("Zone HTTP " + connection.getResponseCode());
                }
                String json = WidgetData.readLimited(connection, 16384);
                String code = parseZone(json);
                if (code == null) {
                    throw new java.io.IOException("Invalid zone response");
                }
                WidgetData.selectZone(app, code, ZoneCatalog.name(code));
                Store.prefs(app).edit().putLong("zone_checked_at", System.currentTimeMillis()).apply();
                MAIN.post(() -> callback.onResult(true, code));
            } catch (Exception e) {
                Log.w(TAG, "Zone lookup failed", e);
                MAIN.post(() -> callback.onResult(false,
                        AppLocale.wrap(app).getString(R.string.location_lookup_failed)));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    static boolean validCoordinates(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon) && lat >= 0 && lat <= 8
                && lon >= 99 && lon <= 120;
    }

    static String parseZone(String json) {
        try {
            JSONObject response = new JSONObject(json);
            String code = response.getString("zone");
            return code.matches("[A-Z]{3}[0-9]{2}")
                    && !ZoneCatalog.name(code).equals(code) ? code : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
