package local.driveboard;

import org.junit.Test;
import java.util.HashSet;

import static org.junit.Assert.*;

public class ZoneCatalogTest {
    @Test
    public void bundledLiveCatalogHasUniqueCodesAndKnownNames() {
        ZoneCatalog.Zone[] zones = ZoneCatalog.options();
        assertEquals(60, zones.length);
        HashSet<String> codes = new HashSet<>();
        for (ZoneCatalog.Zone zone : zones) {
            assertTrue(zone.code.matches("[A-Z]{3}[0-9]{2}"));
            assertTrue(codes.add(zone.code));
            assertFalse(zone.name.isEmpty());
        }

        assertEquals("Kuala Lumpur, Putrajaya", ZoneCatalog.name("WLY01"));
        assertTrue(ZoneCatalog.name("PHG07").contains("Rompin"));
        assertEquals("XXX99", ZoneCatalog.name("XXX99"));
    }
}
