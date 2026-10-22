package local.driveboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class QuranMetadataTest {
    private static final String PACKAGE = "com.quran.labs.androidquran";

    @Test public void catalogContainsCompleteKnownChapters() {
        assertEquals(114, QuranCatalog.all().size());
        assertEquals(6236, QuranCatalog.all().stream().mapToInt(entry -> entry.ayahCount).sum());
        assertEquals("الفاتحة", QuranCatalog.get(1).arabic);
        assertEquals(7, QuranCatalog.get(1).ayahCount);
        assertEquals("Yā-Sīn", QuranCatalog.get(36).transliteration);
        assertEquals(83, QuranCatalog.get(36).ayahCount);
        assertEquals("Al-Wāqiʿah", QuranCatalog.get(56).transliteration);
        assertEquals(96, QuranCatalog.get(56).ayahCount);
        assertEquals(30, QuranCatalog.get(67).ayahCount);
    }

    @Test public void matchesDiacriticsApostrophesAndArticleVariants() {
        assertEquals(56, QuranCatalog.match("Surah Al-Wāqiʿah").number);
        assertEquals(56, QuranCatalog.match("Surat Al Waqi'ah").number);
        assertEquals(56, QuranCatalog.match("Surah AlWaqiah").number);
        assertEquals(1, QuranCatalog.match("Al-Fātiḥah").number);
        assertEquals(1, QuranCatalog.match("Surah Fātiḥah").number);
        assertEquals(36, QuranCatalog.match("Yā-Sīn").number);
        assertEquals(36, QuranCatalog.match("Surah Ya Sin").number);
        assertEquals(67, QuranCatalog.match("Al-Mulk").number);
        assertEquals(67, QuranCatalog.match("AlMulk").number);
        assertEquals(67, QuranCatalog.match("Surah Mulk").number);
        assertEquals(55, QuranCatalog.match("Al-Rahman").number);
        assertEquals(114, QuranCatalog.match("Al-Nas").number);
        assertEquals(30, QuranCatalog.match("Al-Rum").number);
        assertEquals(1, QuranCatalog.match("سورة الفاتحة").number);
        assertNull(QuranCatalog.match("Reciter sings Al-Mulk tonight"));
    }

    @Test public void parsesOnlyQuranPlayerAndExplicitAyah() {
        QuranMetadata verse = QuranMetadata.parse(PACKAGE,
                "Surah Al-Wāqiʿah, Ayah 84", "Mishary Alafasy");
        assertEquals(56, verse.surah.number);
        assertEquals(84, verse.ayah);
        assertEquals("Al-Wāqiʿah · 84", verse.heading());

        QuranMetadata surahOnly = QuranMetadata.parse(PACKAGE,
                "Surah Al-Mulk", "Ayah 12");
        assertEquals(67, surahOnly.surah.number);
        assertEquals(0, surahOnly.ayah);
        assertEquals("Al-Mulk", surahOnly.heading());

        QuranMetadata fallback = QuranMetadata.parse(PACKAGE,
                "Now playing", "Surah Yā-Sīn · Ayah 12");
        assertEquals(36, fallback.surah.number);
        assertEquals(12, fallback.ayah);

        assertNull(QuranMetadata.parse("other.player", "Surah Al-Mulk, Ayah 1", ""));
        assertNull(QuranMetadata.parse(PACKAGE, "Recitation", "Mishary Alafasy"));
    }
}
