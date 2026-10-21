package local.driveboard;

import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;
import androidx.lifecycle.LifecycleEventObserver;

/** Entry point for the car host. */
public final class DriveBoardService extends CarAppService {
    @Override
    public void onCreate() {
        super.onCreate();
        CarDiagnostics.install(this);
        CarDiagnostics.log(this, "DriveBoardService create");
        BackgroundStore.cleanupLegacy(this);
    }

    @Override
    public void onDestroy() {
        CarDiagnostics.log(this, "DriveBoardService destroy");
        super.onDestroy();
    }

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        CarDiagnostics.log(this, "Host validator requested (development allow-all)");
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        CarDiagnostics.log(this, "Session create");
        return new Session() {
            private BoardScreen boardScreen;
            {
                getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
                    String host;
                    try {
                        androidx.car.app.HostInfo info = getCarContext().getHostInfo();
                        host = info == null ? "unknown" : info.getPackageName() + " uid=" + info.getUid();
                    } catch (RuntimeException error) {
                        host = "unavailable " + error;
                    }
                    CarDiagnostics.log(DriveBoardService.this, "Session " + event + " host=" + host);
                });
            }

            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull android.content.Intent intent) {
                CarDiagnostics.log(DriveBoardService.this, "Session screen API="
                        + getCarContext().getCarAppApiLevel());
                boardScreen = new BoardScreen(getCarContext());
                return boardScreen;
            }

            @Override
            public void onCarConfigurationChanged(@NonNull Configuration configuration) {
                if (boardScreen != null) {
                    boardScreen.onCarConfigurationChanged();
                }
            }
        };
    }
}
