package local.driveboard;

/** Platform-independent labels used by the surah artwork. */
public final class SurahArtText {
    private SurahArtText() {}

    public static String name(int surah) {
        QuranCatalog.Entry entry = QuranCatalog.get(surah);
        return entry == null ? "" : entry.arabic;
    }

    public static String arabicIndic(int number) {
        String latin = Integer.toString(number);
        StringBuilder result = new StringBuilder(latin.length());
        for (int i = 0; i < latin.length(); i++) {
            char digit = latin.charAt(i);
            result.append(digit >= '0' && digit <= '9'
                    ? (char) ('\u0660' + digit - '0') : digit);
        }
        return result.toString();
    }
}
