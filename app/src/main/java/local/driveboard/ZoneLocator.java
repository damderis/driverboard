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

    public interface CoordinatesCallback {
        /** Called on the main thread. Null means no recent coarse phone fix is available. */
        void onCoordinates(Location location);
    }

    /** Obtain only a coarse phone network fix; never consult the car location provider. */
    public static void coordinates(Context context, CoordinatesCallback callback) {
        Context app = context.getApplicationContext();
        if (app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            MAIN.post(() -> callback.onCoordinates(null));
            return;
        }
        LocationManager manager = app.getSystemService(LocationManager.class);
        if (manager == null) {
            MAIN.post(() -> callback.onCoordinates(null));
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
                callback.onCoordinates(recent(fallback));
            }
        };
        MAIN.postDelayed(timeout, 15000);
        try {
            manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, signal,
                    app.getMainExecutor(), location -> {
                if (resolved.compareAndSet(false, true)) {
                    MAIN.removeCallbacks(timeout);
                    callback.onCoordinates(recent(location != null ? location : fallback));
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Current network location unavailable", e);
            if (resolved.compareAndSet(false, true)) {
                MAIN.removeCallbacks(timeout);
                MAIN.post(() -> callback.onCoordinates(recent(fallback)));
            }
        }
    }

    private static Location recent(Location location) {
        long ageNanos = location == null ? Long.MAX_VALUE
                : android.os.SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos();
        return location != null && ageNanos >= 0 && ageNanos <= 12L * 60 * 60 * 1_000_000_000L
                && Double.isFinite(location.getLatitude())
                && Double.isFinite(location.getLongitude())
                && Math.abs(location.getLatitude()) <= 90
                && Math.abs(location.getLongitude()) <= 180
                ? location : null;
    }

    public static void refresh(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        if (app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            callback.onResult(false,
                    AppLocale.wrap(app).getString(R.string.location_permission_required));
            return;
        }
        coordinates(app, location -> {
            if (location == null) {
                callback.onResult(false, AppLocale.wrap(app).getString(R.string.location_no_fix));
            } else {
                resolve(app, location, callback);
            }
        });
    }

    private static void resolve(Context app, Location location, Callback callback) {
        if (!validCoordinates(location.getLatitude(), location.getLongitude())) {
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
