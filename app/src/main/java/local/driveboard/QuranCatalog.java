package local.driveboard;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Bundled chapter metadata from https://api.quran.com/api/v4/chapters. */
public final class QuranCatalog {
    private static final String[] ARTICLES = {"al", "ar", "as", "an", "at",
            "ad", "az", "ash", "adh", "ath"};

    public static final class Entry {
        public final int number;
        public final String transliteration;
        public final String arabic;
        public final int ayahCount;
        public final String name;
        public final String arabicName;
        private final String simpleName;

        private final String[] latinKeys;
        private final String arabicKey;

        private final boolean hasArticle;

        private Entry(int number, String transliteration, String arabic,
                int ayahCount, String simpleName) {
            this.number = number;
            this.transliteration = transliteration;
            this.arabic = arabic;
            this.ayahCount = ayahCount;
            this.name = transliteration;
            this.arabicName = arabic;
            this.simpleName = simpleName;
            this.latinKeys = new String[] {canonicalLatin(transliteration),
                    canonicalLatin(simpleName)};
            this.arabicKey = canonicalArabic(arabicKey(arabic));
            this.hasArticle = transliteration.matches("(?iu)^(?:ash|adh|ath|al|ar|as|an|at|ad|az)[-\\s].*");
        }
    }

    private static final Entry[] ENTRIES = {
            new Entry(1, "Al-Fātiḥah", "الفاتحة", 7, "Al-Fatihah"),
            new Entry(2, "Al-Baqarah", "البقرة", 286, "Al-Baqarah"),
            new Entry(3, "Āli ʿImrān", "آل عمران", 200, "Ali 'Imran"),
            new Entry(4, "An-Nisā", "النساء", 176, "An-Nisa"),
            new Entry(5, "Al-Māʾidah", "المائدة", 120, "Al-Ma'idah"),
            new Entry(6, "Al-ʾAnʿām", "الأنعام", 165, "Al-An'am"),
            new Entry(7, "Al-ʾAʿrāf", "الأعراف", 206, "Al-A'raf"),
            new Entry(8, "Al-ʾAnfāl", "الأنفال", 75, "Al-Anfal"),
            new Entry(9, "At-Tawbah", "التوبة", 129, "At-Tawbah"),
            new Entry(10, "Yūnus", "يونس", 109, "Yunus"),
            new Entry(11, "Hūd", "هود", 123, "Hud"),
            new Entry(12, "Yūsuf", "يوسف", 111, "Yusuf"),
            new Entry(13, "Ar-Raʿd", "الرعد", 43, "Ar-Ra'd"),
            new Entry(14, "Ibrāhīm", "ابراهيم", 52, "Ibrahim"),
            new Entry(15, "Al-Ḥijr", "الحجر", 99, "Al-Hijr"),
            new Entry(16, "An-Naḥl", "النحل", 128, "An-Nahl"),
            new Entry(17, "Al-ʾIsrā", "الإسراء", 111, "Al-Isra"),
            new Entry(18, "Al-Kahf", "الكهف", 110, "Al-Kahf"),
            new Entry(19, "Maryam", "مريم", 98, "Maryam"),
            new Entry(20, "Ṭāhā", "طه", 135, "Taha"),
            new Entry(21, "Al-ʾAnbyā", "الأنبياء", 112, "Al-Anbya"),
            new Entry(22, "Al-Ḥajj", "الحج", 78, "Al-Hajj"),
            new Entry(23, "Al-Muʾminūn", "المؤمنون", 118, "Al-Mu'minun"),
            new Entry(24, "An-Nūr", "النور", 64, "An-Nur"),
            new Entry(25, "Al-Furqān", "الفرقان", 77, "Al-Furqan"),
            new Entry(26, "Ash-Shuʿarā", "الشعراء", 227, "Ash-Shu'ara"),
            new Entry(27, "An-Naml", "النمل", 93, "An-Naml"),
            new Entry(28, "Al-Qaṣaṣ", "القصص", 88, "Al-Qasas"),
            new Entry(29, "Al-ʿAnkabūt", "العنكبوت", 69, "Al-'Ankabut"),
            new Entry(30, "Ar-Rūm", "الروم", 60, "Ar-Rum"),
            new Entry(31, "Luqmān", "لقمان", 34, "Luqman"),
            new Entry(32, "As-Sajdah", "السجدة", 30, "As-Sajdah"),
            new Entry(33, "Al-ʾAḥzāb", "الأحزاب", 73, "Al-Ahzab"),
            new Entry(34, "Saba", "سبإ", 54, "Saba"),
            new Entry(35, "Fāṭir", "فاطر", 45, "Fatir"),
            new Entry(36, "Yā-Sīn", "يس", 83, "Ya-Sin"),
            new Entry(37, "Aṣ-Ṣāffāt", "الصافات", 182, "As-Saffat"),
            new Entry(38, "Ṣād", "ص", 88, "Sad"),
            new Entry(39, "Az-Zumar", "الزمر", 75, "Az-Zumar"),
            new Entry(40, "Ghāfir", "غافر", 85, "Ghafir"),
            new Entry(41, "Fuṣṣilat", "فصلت", 54, "Fussilat"),
            new Entry(42, "Ash-Shūraá", "الشورى", 53, "Ash-Shuraa"),
            new Entry(43, "Az-Zukhruf", "الزخرف", 89, "Az-Zukhruf"),
            new Entry(44, "Ad-Dukhān", "الدخان", 59, "Ad-Dukhan"),
            new Entry(45, "Al-Jāthiyah", "الجاثية", 37, "Al-Jathiyah"),
            new Entry(46, "Al-ʾAḥqāf", "الأحقاف", 35, "Al-Ahqaf"),
            new Entry(47, "Muḥammad", "محمد", 38, "Muhammad"),
            new Entry(48, "Al-Fatḥ", "الفتح", 29, "Al-Fath"),
            new Entry(49, "Al-Ḥujurāt", "الحجرات", 18, "Al-Hujurat"),
            new Entry(50, "Qāf", "ق", 45, "Qaf"),
            new Entry(51, "Adh-Dhāriyāt", "الذاريات", 60, "Adh-Dhariyat"),
            new Entry(52, "Aṭ-Ṭūr", "الطور", 49, "At-Tur"),
            new Entry(53, "An-Najm", "النجم", 62, "An-Najm"),
            new Entry(54, "Al-Qamar", "القمر", 55, "Al-Qamar"),
            new Entry(55, "Ar-Raḥmān", "الرحمن", 78, "Ar-Rahman"),
            new Entry(56, "Al-Wāqiʿah", "الواقعة", 96, "Al-Waqi'ah"),
            new Entry(57, "Al-Ḥadīd", "الحديد", 29, "Al-Hadid"),
            new Entry(58, "Al-Mujādila", "المجادلة", 22, "Al-Mujadila"),
            new Entry(59, "Al-Ḥashr", "الحشر", 24, "Al-Hashr"),
            new Entry(60, "Al-Mumtaḥanah", "الممتحنة", 13, "Al-Mumtahanah"),
            new Entry(61, "Aṣ-Ṣaf", "الصف", 14, "As-Saf"),
            new Entry(62, "Al-Jumuʿah", "الجمعة", 11, "Al-Jumu'ah"),
            new Entry(63, "Al-Munāfiqūn", "المنافقون", 11, "Al-Munafiqun"),
            new Entry(64, "At-Taghābun", "التغابن", 18, "At-Taghabun"),
            new Entry(65, "Aṭ-Ṭalāq", "الطلاق", 12, "At-Talaq"),
            new Entry(66, "At-Taḥrīm", "التحريم", 12, "At-Tahrim"),
            new Entry(67, "Al-Mulk", "الملك", 30, "Al-Mulk"),
            new Entry(68, "Al-Qalam", "القلم", 52, "Al-Qalam"),
            new Entry(69, "Al-Ḥāqqah", "الحاقة", 52, "Al-Haqqah"),
            new Entry(70, "Al-Maʿārij", "المعارج", 44, "Al-Ma'arij"),
            new Entry(71, "Nūḥ", "نوح", 28, "Nuh"),
            new Entry(72, "Al-Jinn", "الجن", 28, "Al-Jinn"),
            new Entry(73, "Al-Muzzammil", "المزمل", 20, "Al-Muzzammil"),
            new Entry(74, "Al-Muddaththir", "المدثر", 56, "Al-Muddaththir"),
            new Entry(75, "Al-Qiyāmah", "القيامة", 40, "Al-Qiyamah"),
            new Entry(76, "Al-ʾInsān", "الانسان", 31, "Al-Insan"),
            new Entry(77, "Al-Mursalāt", "المرسلات", 50, "Al-Mursalat"),
            new Entry(78, "An-Naba", "النبإ", 40, "An-Naba"),
            new Entry(79, "An-Nāziʿāt", "النازعات", 46, "An-Nazi'at"),
            new Entry(80, "ʿAbasa", "عبس", 42, "'Abasa"),
            new Entry(81, "At-Takwīr", "التكوير", 29, "At-Takwir"),
            new Entry(82, "Al-ʾInfiṭār", "الإنفطار", 19, "Al-Infitar"),
            new Entry(83, "Al-Muṭaffifīn", "المطففين", 36, "Al-Mutaffifin"),
            new Entry(84, "Al-ʾInshiqāq", "الإنشقاق", 25, "Al-Inshiqaq"),
            new Entry(85, "Al-Burūj", "البروج", 22, "Al-Buruj"),
            new Entry(86, "Aṭ-Ṭāriq", "الطارق", 17, "At-Tariq"),
            new Entry(87, "Al-ʾAʿlá", "الأعلى", 19, "Al-A'la"),
            new Entry(88, "Al-Ghāshiyah", "الغاشية", 26, "Al-Ghashiyah"),
            new Entry(89, "Al-Fajr", "الفجر", 30, "Al-Fajr"),
            new Entry(90, "Al-Balad", "البلد", 20, "Al-Balad"),
            new Entry(91, "Ash-Shams", "الشمس", 15, "Ash-Shams"),
            new Entry(92, "Al-Layl", "الليل", 21, "Al-Layl"),
            new Entry(93, "Aḍ-Ḍuḥaá", "الضحى", 11, "Ad-Duhaa"),
            new Entry(94, "Ash-Sharḥ", "الشرح", 8, "Ash-Sharh"),
            new Entry(95, "At-Tīn", "التين", 8, "At-Tin"),
            new Entry(96, "Al-ʿAlaq", "العلق", 19, "Al-'Alaq"),
            new Entry(97, "Al-Qadr", "القدر", 5, "Al-Qadr"),
            new Entry(98, "Al-Bayyinah", "البينة", 8, "Al-Bayyinah"),
            new Entry(99, "Az-Zalzalah", "الزلزلة", 8, "Az-Zalzalah"),
            new Entry(100, "Al-ʿĀdiyāt", "العاديات", 11, "Al-'Adiyat"),
            new Entry(101, "Al-Qāriʿah", "القارعة", 11, "Al-Qari'ah"),
            new Entry(102, "At-Takāthur", "التكاثر", 8, "At-Takathur"),
            new Entry(103, "Al-ʿAṣr", "العصر", 3, "Al-'Asr"),
            new Entry(104, "Al-Humazah", "الهمزة", 9, "Al-Humazah"),
            new Entry(105, "Al-Fīl", "الفيل", 5, "Al-Fil"),
            new Entry(106, "Quraysh", "قريش", 4, "Quraysh"),
            new Entry(107, "Al-Māʿūn", "الماعون", 7, "Al-Ma'un"),
            new Entry(108, "Al-Kawthar", "الكوثر", 3, "Al-Kawthar"),
            new Entry(109, "Al-Kāfirūn", "الكافرون", 6, "Al-Kafirun"),
            new Entry(110, "An-Naṣr", "النصر", 3, "An-Nasr"),
            new Entry(111, "Al-Masad", "المسد", 5, "Al-Masad"),
            new Entry(112, "Al-ʾIkhlāṣ", "الإخلاص", 4, "Al-Ikhlas"),
            new Entry(113, "Al-Falaq", "الفلق", 5, "Al-Falaq"),
            new Entry(114, "An-Nās", "الناس", 6, "An-Nas")
    };

    private QuranCatalog() {}

    public static Entry get(int number) {
        return number >= 1 && number <= ENTRIES.length ? ENTRIES[number - 1] : null;
    }

    public static List<Entry> all() {
        List<Entry> result = new ArrayList<>(ENTRIES.length);
        Collections.addAll(result, ENTRIES);
        return Collections.unmodifiableList(result);
    }

    public static Entry match(String text) {
        if (text == null) {
            return null;
        }
        String candidate = text.trim().replaceFirst("(?iu)^(?:surah|surat|سورة)\\s+", "");
        String latin = canonicalLatin(candidate);
        String arabic = canonicalArabic(arabicKey(candidate));
        for (Entry entry : ENTRIES) {
            for (String key : entry.latinKeys) {
                if (!latin.isEmpty() && (latin.equals(key)
                        || (entry.hasArticle && articleVariant(latin, key)))) {
                    return entry;
                }
            }
            if (!arabic.isEmpty() && arabic.equals(entry.arabicKey)) {
                return entry;
            }
        }
        return null;
    }

    private static boolean articleVariant(String candidate, String key) {
        for (String article : ARTICLES) {
            if (candidate.equals(article + key)) {
                return true;
            }
        }
        return false;
    }

    private static String canonicalLatin(String value) {
        String bare = value.replaceFirst("(?iu)^(?:ash|adh|ath|al|ar|as|an|at|ad|az)[-\\s]+", "");
        String key = latinKey(bare);
        if (key.endsWith("ah")) {
            key = key.substring(0, key.length() - 1);
        }
        return key;
    }

    private static String canonicalArabic(String key) {
        return key.startsWith("ال") ? key.substring(2) : key;
    }

    private static String latinKey(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT);
        return decomposed.replaceAll("\\p{M}", "")
                .replace("ʿ", "").replace("ʾ", "")
                .replaceAll("[^a-z0-9]", "");
    }

    private static String arabicKey(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي').replaceAll("[^\\p{IsArabic}]", "");
    }
}
