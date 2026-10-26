package local.driveboard;

import android.app.Application;
import android.content.Context;

import androidx.car.app.connection.CarConnection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small, durable flight recorder; diagnostics must never interrupt the app. */
public final class CarDiagnostics extends Application {
    private static final Object LOCK = new Object();
    private static boolean installed;
    private int carConnectionType = CarConnection.CONNECTION_TYPE_NOT_CONNECTED;

    @Override
    public void onCreate() {
        super.onCreate();
        Store.prefs(this).edit().remove("battery_pct").remove("range_m")
                .remove("battery_at").remove("battery_reason").remove("battery_unsupported").apply();
        install(this);
        // The notification-listener binding normally keeps this process alive between car starts.
        new CarConnection(this).getType().observeForever(type -> {
            if (type == null) {
                return;
            }
            int previous = carConnectionType;
            carConnectionType = type;
            if (type == CarConnection.CONNECTION_TYPE_PROJECTION && type != previous) {
                RoutinePlayer.get(this).onCarStarted();
            } else if (previous == CarConnection.CONNECTION_TYPE_PROJECTION
                    && type != previous) {
                RoutinePlayer.get(this).onCarDisconnected();
            }
        });
    }

    public static void install(Context context) {
        try {
            synchronized (LOCK) {
                if (installed) {
                    return;
                }
                installed = true;
                Context app = context.getApplicationContext();
                Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                    try {
                        StringWriter stack = new StringWriter();
                        error.printStackTrace(new PrintWriter(stack));
                        log(app, "Uncaught thread=" + thread.getName() + "\n" + stack);
                    } catch (Throwable ignored) {
                        // Even an out-of-memory failure must reach Android's handler.
                    } finally {
                        if (previous != null) {
                            previous.uncaughtException(thread, error);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    public static void log(Context context, String message) {
        try {
            synchronized (LOCK) {
                File file = new File(context.getFilesDir(), "car-diagnostics.log");
                byte[] bytes = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
                        .format(new Date()) + " " + message + "\n").getBytes(StandardCharsets.UTF_8);
                if (file.length() + bytes.length > 256 * 1024) {
                    File backup = new File(file.getParentFile(), "car-diagnostics.log.1");
                    if (backup.exists() && !backup.delete()) {
                        return;
                    }
                    if (file.exists() && !file.renameTo(backup)) {
                        return;
                    }
                }
                try (FileOutputStream output = new FileOutputStream(file, true)) {
                    output.write(bytes);
                }
            }
        } catch (Throwable ignored) {
            // Storage, framework and formatting failures are deliberately non-fatal.
        }
    }
}
