// Regenerate the checked-in chapter table: node tools/generate-quran-catalog.cjs
const https = require('https');
const fs = require('fs');
const path = require('path');

const source = 'https://api.quran.com/api/v4/chapters';
const destination = path.join(__dirname, '..', 'app', 'src', 'main', 'java',
    'local', 'driveboard', 'QuranCatalog.java');

function get(url) {
    return new Promise((resolve, reject) => {
        https.get(url, response => {
            if (response.statusCode !== 200) {
                response.resume();
                reject(new Error(`HTTP ${response.statusCode}`));
                return;
            }
            let body = '';
            response.setEncoding('utf8');
            response.on('data', chunk => body += chunk);
            response.on('end', () => resolve(JSON.parse(body)));
        }).on('error', reject);
    });
}

function javaString(value) {
    return JSON.stringify(value).replace(/\\u2028/g, '\\u2028');
}

(async () => {
    const chapters = (await get(source)).chapters;
    if (!Array.isArray(chapters) || chapters.length !== 114
            || chapters.reduce((count, chapter) => count + chapter.verses_count, 0) !== 6236) {
        throw new Error('Unexpected Quran.com chapter catalog');
    }
    const rows = chapters.map((chapter, index) => {
        if (chapter.id !== index + 1 || !chapter.name_simple
                || !chapter.name_arabic || chapter.verses_count < 3) {
            throw new Error(`Invalid chapter at ${index + 1}`);
        }
        const transliteration = chapter.name_complex
            .replaceAll('ĥ', 'ḥ').replaceAll('Ĥ', 'Ḥ')
            .replaceAll('`', 'ʿ').replaceAll("'", 'ʾ')
            .replaceAll('Ţ', 'Ṭ').replaceAll('ţ', 'ṭ')
            .replaceAll('Ş', 'Ṣ').replaceAll('ş', 'ṣ')
            .replaceAll('đ', 'ḍ').replaceAll('Đ', 'Ḍ');
        return `            new Entry(${chapter.id}, ${javaString(transliteration)}, `
            + `${javaString(chapter.name_arabic)}, ${chapter.verses_count}, `
            + `${javaString(chapter.name_simple)})`;
    });
    const header = `package local.driveboard;\n\n`
        + `import java.text.Normalizer;\nimport java.util.ArrayList;\n`
        + `import java.util.Collections;\nimport java.util.List;\n`
        + `import java.util.Locale;\n\n`
        + `/** Bundled chapter metadata from ${source}. */\n`
        + `public final class QuranCatalog {\n`
        + `    private static final String[] ARTICLES = {"al", "ar", "as", "an", "at",\n`
        + `            "ad", "az", "ash", "adh", "ath"};\n\n`
        + `    public static final class Entry {\n`
        + `        public final int number;\n`
        + `        public final String transliteration;\n`
        + `        public final String arabic;\n`
        + `        public final int ayahCount;\n`
        + `        public final String name;\n`
        + `        public final String arabicName;\n`
        + `        private final String simpleName;\n\n`
        + `        private final String[] latinKeys;\n`
        + `        private final String arabicKey;\n\n`
        + `        private final boolean hasArticle;\n\n`
        + `        private Entry(int number, String transliteration, String arabic,\n`
        + `                int ayahCount, String simpleName) {\n`
        + `            this.number = number;\n`
        + `            this.transliteration = transliteration;\n`
        + `            this.arabic = arabic;\n`
        + `            this.ayahCount = ayahCount;\n`
        + `            this.name = transliteration;\n`
        + `            this.arabicName = arabic;\n`
        + `            this.simpleName = simpleName;\n`
        + `            this.latinKeys = new String[] {canonicalLatin(transliteration),\n`
        + `                    canonicalLatin(simpleName)};\n`
        + `            this.arabicKey = canonicalArabic(arabicKey(arabic));\n`
        + `            this.hasArticle = transliteration.matches("(?iu)^(?:ash|adh|ath|al|ar|as|an|at|ad|az)[-\\\\s].*");\n`
        + `        }\n`
        + `    }\n\n`
        + `    private static final Entry[] ENTRIES = {\n`;
    const footer = `\n    };\n\n`
        + `    private QuranCatalog() {}\n\n`
        + `    public static Entry get(int number) {\n`
        + `        return number >= 1 && number <= ENTRIES.length ? ENTRIES[number - 1] : null;\n`
        + `    }\n\n`
        + `    public static List<Entry> all() {\n`
        + `        List<Entry> result = new ArrayList<>(ENTRIES.length);\n`
        + `        Collections.addAll(result, ENTRIES);\n`
        + `        return Collections.unmodifiableList(result);\n`
        + `    }\n\n`
        + `    public static Entry match(String text) {\n`
        + `        if (text == null) {\n`
        + `            return null;\n`
        + `        }\n`
        + `        String candidate = text.trim().replaceFirst("(?iu)^(?:surah|surat|سورة)\\\\s+", "");\n`
        + `        String latin = canonicalLatin(candidate);\n`
        + `        String arabic = canonicalArabic(arabicKey(candidate));\n`
        + `        for (Entry entry : ENTRIES) {\n`
        + `            for (String key : entry.latinKeys) {\n`
        + `                if (!latin.isEmpty() && (latin.equals(key)\n`
        + `                        || (entry.hasArticle && articleVariant(latin, key)))) {\n`
        + `                    return entry;\n`
        + `                }\n`
        + `            }\n`
        + `            if (!arabic.isEmpty() && arabic.equals(entry.arabicKey)) {\n`
        + `                return entry;\n`
        + `            }\n`
        + `        }\n`
        + `        return null;\n`
        + `    }\n\n`
        + `    private static boolean articleVariant(String candidate, String key) {\n`
        + `        for (String article : ARTICLES) {\n`
        + `            if (candidate.equals(article + key)) {\n`
        + `                return true;\n`
        + `            }\n`
        + `        }\n`
        + `        return false;\n`
        + `    }\n\n`
        + `    private static String canonicalLatin(String value) {\n`
        + `        String bare = value.replaceFirst("(?iu)^(?:ash|adh|ath|al|ar|as|an|at|ad|az)[-\\\\s]+", "");\n`
        + `        String key = latinKey(bare);\n`
        + `        if (key.endsWith("ah")) {\n`
        + `            key = key.substring(0, key.length() - 1);\n`
        + `        }\n`
        + `        return key;\n`
        + `    }\n\n`
        + `    private static String canonicalArabic(String key) {\n`
        + `        return key.startsWith("ال") ? key.substring(2) : key;\n`
        + `    }\n\n`
        + `    private static String latinKey(String value) {\n`
        + `        if (value == null) {\n`
        + `            return "";\n`
        + `        }\n`
        + `        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)\n`
        + `                .toLowerCase(Locale.ROOT);\n`
        + `        return decomposed.replaceAll("\\\\p{M}", "")\n`
        + `                .replace("ʿ", "").replace("ʾ", "")\n`
        + `                .replaceAll("[^a-z0-9]", "");\n`
        + `    }\n\n`
        + `    private static String arabicKey(String value) {\n`
        + `        if (value == null) {\n`
        + `            return "";\n`
        + `        }\n`
        + `        return Normalizer.normalize(value, Normalizer.Form.NFD)\n`
        + `                .replaceAll("\\\\p{M}", "")\n`
        + `                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')\n`
        + `                .replace('ى', 'ي').replaceAll("[^\\\\p{IsArabic}]", "");\n`
        + `    }\n`
        + `}\n`;
    fs.writeFileSync(destination, header + rows.join(',\n') + footer, 'utf8');
    console.log(`Wrote ${chapters.length} chapters, 6236 ayahs to ${destination}`);
})().catch(error => { console.error(error); process.exitCode = 1; });
