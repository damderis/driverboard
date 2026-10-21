package local.driveboard;

/** WMO weather interpretation codes used by Open-Meteo. */
public final class WeatherCode {
    public enum Kind {
        CLEAR,
        PARTLY_CLOUDY,
        CLOUDY,
        FOG,
        DRIZZLE,
        RAIN,
        SNOW,
        STORM,
        UNKNOWN
    }

    private WeatherCode() {
    }

    /** Only clear and partly cloudy glyphs have a moon variant. */
    public static boolean isNightVariant(int code, boolean isDay) {
        Kind kind = kind(code);
        return !isDay && (kind == Kind.CLEAR || kind == Kind.PARTLY_CLOUDY);
    }

    public static Kind kind(int code) {
        if (code >= 0 && code <= 1) {
            return Kind.CLEAR;
        }
        if (code == 2) {
            return Kind.PARTLY_CLOUDY;
        }
        if (code == 3) {
            return Kind.CLOUDY;
        }
        if (code == 45 || code == 48) {
            return Kind.FOG;
        }
        if (code >= 51 && code <= 57) {
            return Kind.DRIZZLE;
        }
        if (code >= 61 && code <= 67 || code >= 80 && code <= 82) {
            return Kind.RAIN;
        }
        if (code >= 71 && code <= 77 || code == 85 || code == 86) {
            return Kind.SNOW;
        }
        if (code == 95 || code == 96 || code == 99) {
            return Kind.STORM;
        }
        return Kind.UNKNOWN;
    }

    public static String description(int code) {
        switch (code) {
            case 0:
                return "Clear sky";
            case 1:
                return "Mainly clear";
            case 2:
                return "Partly cloudy";
            case 3:
                return "Overcast";
            case 45:
            case 48:
                return "Fog";
            case 51:
            case 53:
            case 55:
            case 56:
            case 57:
                return "Drizzle";
            case 61:
            case 63:
            case 65:
            case 66:
            case 67:
                return "Rain";
            case 71:
            case 73:
            case 75:
            case 77:
                return "Snow";
            case 80:
            case 81:
            case 82:
                return "Rain showers";
            case 85:
            case 86:
                return "Snow showers";
            case 95:
            case 96:
            case 99:
                return "Thunderstorm";
            default:
                return "Weather";
        }
    }
}
