package local.driveboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

import org.junit.Test;

public class SurahArtTextTest {
    @Test public void bundledFontCoversEverySurahName() throws Exception {
        File file = new File("src/main/res/font/aref_ruqaa.ttf");
        if (!file.isFile()) {
            file = new File("app/src/main/res/font/aref_ruqaa.ttf");
        }
        ByteBuffer font = ByteBuffer.wrap(Files.readAllBytes(file.toPath()))
                .order(ByteOrder.BIG_ENDIAN);
        int cmap = 0;
        int tables = Short.toUnsignedInt(font.getShort(4));
        for (int i = 0; i < tables; i++) {
            int table = 12 + i * 16;
            if (font.getInt(table) == 0x636D6170) {
                cmap = font.getInt(table + 8);
            }
        }
        int format4 = 0;
        int subtables = Short.toUnsignedInt(font.getShort(cmap + 2));
        for (int i = 0; i < subtables; i++) {
            int offset = cmap + font.getInt(cmap + 4 + i * 8 + 4);
            if (font.getShort(offset) == 4) {
                format4 = offset;
                break;
            }
        }
        assertFalse("Bundled font must contain a BMP cmap", format4 == 0);
        for (int surah = 1; surah <= 114; surah++) {
            assertCovered(font, format4, SurahArtText.name(surah));
        }
        assertCovered(font, format4, "سورة ٠١٢٣٤٥٦٧٨٩");
    }

    private static void assertCovered(ByteBuffer font, int cmap, String text) {
        int segments = Short.toUnsignedInt(font.getShort(cmap + 6)) / 2;
        int ends = cmap + 14;
        int starts = ends + segments * 2 + 2;
        int deltas = starts + segments * 2;
        int ranges = deltas + segments * 2;
        for (char character : text.toCharArray()) {
            int glyph = 0;
            for (int i = 0; i < segments; i++) {
                int end = Short.toUnsignedInt(font.getShort(ends + i * 2));
                int start = Short.toUnsignedInt(font.getShort(starts + i * 2));
                if (character < start || character > end) {
                    continue;
                }
                int delta = font.getShort(deltas + i * 2);
                int range = Short.toUnsignedInt(font.getShort(ranges + i * 2));
                glyph = range == 0 ? (character + delta) & 0xFFFF
                        : Short.toUnsignedInt(font.getShort(ranges + i * 2
                                + range + (character - start) * 2));
                if (range != 0 && glyph != 0) {
                    glyph = (glyph + delta) & 0xFFFF;
                }
                break;
            }
            assertFalse("Missing glyph U+" + Integer.toHexString(character), glyph == 0);
        }
    }

    @Test public void convertsArabicIndicDigits() {
        assertEquals("٠", SurahArtText.arabicIndic(0));
        assertEquals("١٢٣٤٥٦٧٨٩٠", SurahArtText.arabicIndic(1234567890));
        assertEquals("١١٤", SurahArtText.arabicIndic(114));
    }

    @Test public void allSurahsHaveArabicArtworkNames() {
        for (int surah = 1; surah <= 114; surah++) {
            assertFalse("Surah " + surah, SurahArtText.name(surah).trim().isEmpty());
            assertEquals(QuranCatalog.get(surah).arabic, SurahArtText.name(surah));
        }
        assertEquals("", SurahArtText.name(0));
        assertEquals("", SurahArtText.name(115));
    }
}
