package local.driveboard;

/** "DriveBoard Peta": the weather-category entry, so the navigation app's map can sit beside it. */
public final class DriveBoardPetaService extends DriveBoardService {
    @Override
    protected boolean weather() {
        return true;
    }
}
