package local.driveboard;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;
import android.content.res.Configuration;

/** Entry point for the car host. */
public final class DriveBoardService extends CarAppService {
    @NonNull
    @Override
    public HostValidator createHostValidator() {
        // Sideloaded development build: accept the host installed on the test head unit.
        // Replace this with a signature allowlist before distributing the app.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new Session() {
            private BoardScreen boardScreen;

            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull android.content.Intent intent) {
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
