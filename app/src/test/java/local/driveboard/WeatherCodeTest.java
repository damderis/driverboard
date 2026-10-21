package local.driveboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherCodeTest {
    @Test
    public void usesMoonOnlyForClearAndPartlyCloudyNights() {
        assertTrue(WeatherCode.isNightVariant(0, false));
        assertTrue(WeatherCode.isNightVariant(1, false));
        assertTrue(WeatherCode.isNightVariant(2, false));
        assertFalse(WeatherCode.isNightVariant(0, true));
        assertFalse(WeatherCode.isNightVariant(2, true));
        assertFalse(WeatherCode.isNightVariant(3, false));
        assertFalse(WeatherCode.isNightVariant(95, false));
        assertFalse(WeatherCode.isNightVariant(-1, false));
    }

    @Test
    public void mapsCommonWmoGroups() {
        assertEquals(WeatherCode.Kind.CLEAR, WeatherCode.kind(0));
        assertEquals(WeatherCode.Kind.PARTLY_CLOUDY, WeatherCode.kind(2));
        assertEquals(WeatherCode.Kind.CLOUDY, WeatherCode.kind(3));
        assertEquals(WeatherCode.Kind.FOG, WeatherCode.kind(45));
        assertEquals(WeatherCode.Kind.DRIZZLE, WeatherCode.kind(53));
        assertEquals(WeatherCode.Kind.RAIN, WeatherCode.kind(65));
        assertEquals(WeatherCode.Kind.SNOW, WeatherCode.kind(73));
        assertEquals(WeatherCode.Kind.RAIN, WeatherCode.kind(81));
        assertEquals(WeatherCode.Kind.STORM, WeatherCode.kind(95));
        assertEquals(WeatherCode.Kind.UNKNOWN, WeatherCode.kind(-1));
    }

    @Test
    public void mapsDescriptions() {
        assertEquals("Clear sky", WeatherCode.description(0));
        assertEquals("Partly cloudy", WeatherCode.description(2));
        assertEquals("Thunderstorm", WeatherCode.description(99));
        assertEquals("Weather", WeatherCode.description(-1));
    }
}
