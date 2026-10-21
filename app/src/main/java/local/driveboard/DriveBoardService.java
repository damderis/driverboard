package local.driveboard;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

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
            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull android.content.Intent intent) {
                return new BoardScreen(getCarContext());
            }
        };
    }
}
