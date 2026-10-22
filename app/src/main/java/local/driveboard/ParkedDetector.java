package local.driveboard;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Phone GPS subscription owned by an open car browser. Never requests permission. */
final class ParkedDetector implements LocationListener {
    interface Callback {
        void onState(ParkedState.State state, boolean permission);
    }

    private final Context context;
    private final LocationManager manager;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private boolean listening;
    private Location latest;
    private Location anchor;
    private long stationarySince = -1;
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (running) {
                update();
                handler.postDelayed(this, 1000);
            }
        }
    };

    ParkedDetector(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
        manager = context.getSystemService(LocationManager.class);
    }

    void start() {
        if (!running) {
            running = true;
            heartbeat.run();
        }
    }

    void stop() {
        running = false;
        handler.removeCallbacks(heartbeat);
        unsubscribe();
        latest = null;
        anchor = null;
        stationarySince = -1;
    }

    private boolean hasPermission() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void unsubscribe() {
        if (manager != null && listening) {
            try {
                manager.removeUpdates(this);
            } catch (RuntimeException error) {
                CarDiagnostics.log(context, "Browser GPS unsubscribe failed: " + error);
            }
        }
        listening = false;
    }

    private void update() {
        boolean permission = hasPermission();
        boolean enabled = false;
        try {
            enabled = permission && manager != null
                    && manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if (enabled && !listening) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,
                        this, Looper.getMainLooper());
                listening = true;
            }
        } catch (RuntimeException error) {
            CarDiagnostics.log(context, "Browser GPS unavailable: " + error);
            enabled = false;
        }
        if (!permission || !enabled) {
            unsubscribe();
            latest = null;
            anchor = null;
            stationarySince = -1;
        }
        long now = SystemClock.elapsedRealtime();
        ParkedState.State state = latest == null ? ParkedState.State.UNKNOWN
                : ParkedState.evaluate(permission, now, latest.getElapsedRealtimeNanos() / 1_000_000,
                        latest.hasSpeed(), latest.getSpeed() * 3.6, stationarySince);
        callback.onState(state, permission);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!running || !LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
            return;
        }
        long at = location.getElapsedRealtimeNanos() / 1_000_000;
        long now = SystemClock.elapsedRealtime();
        if (at < 0 || at > now || now - at > 120_000
                || (latest != null && at <= latest.getElapsedRealtimeNanos() / 1_000_000)) {
            return;
        }
        // A small, fixed 5 m envelope avoids treating GPS accuracy itself as proof of parking.
        boolean gap = latest == null || at - latest.getElapsedRealtimeNanos() / 1_000_000 > 5000;
        if (gap || anchor == null || anchor.distanceTo(location) > 5
                || (location.hasSpeed() && location.getSpeed() * 3.6 > 8)) {
            anchor = new Location(location);
            stationarySince = at;
        }
        latest = new Location(location);
        update();
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (running) {
            update();
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (running) {
            update();
        }
    }
}
