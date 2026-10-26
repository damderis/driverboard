package local.driveboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the surah list entered in routine settings. */
public final class RoutineInputs {
    private static final Pattern RANGE = Pattern.compile("([0-9]+)\\s*-\\s*([0-9]+)");

    private RoutineInputs() {}

    /** Accepts comma-separated surah numbers, names, and ascending numeric ranges. */
    public static List<Integer> parseSurahs(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter at least one surah");
        }
        List<Integer> result = new ArrayList<>();
        for (String raw : input.split("[,،]", -1)) {
            String token = raw.trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("Empty surah entry");
            }
            Matcher range = RANGE.matcher(token);
            if (range.matches()) {
                int first = parseNumber(range.group(1));
                int last = parseNumber(range.group(2));
                if (first > last) {
                    throw new IllegalArgumentException("Surah range must be ascending");
                }
                if (last - first + 1 > 30 - result.size()) {
                    throw new IllegalArgumentException("A routine can contain at most 30 surahs");
                }
                for (int number = first; number <= last; number++) {
                    result.add(number);
                }
            } else {
                int number;
                if (token.matches("[0-9]+")) {
                    number = parseNumber(token);
                } else {
                    QuranCatalog.Entry match = QuranCatalog.match(token);
                    if (match == null) {
                        throw new IllegalArgumentException("Unknown surah: " + token);
                    }
                    number = match.number;
                }
                if (result.size() >= 30) {
                    throw new IllegalArgumentException("A routine can contain at most 30 surahs");
                }
                result.add(number);
            }
        }
        return result;
    }

    private static int parseNumber(String value) {
        try {
            int number = Integer.parseInt(value);
            if (number >= 1 && number <= 114) {
                return number;
            }
        } catch (NumberFormatException ignored) {
            // The same validation message applies to numbers outside the integer range.
        }
        throw new IllegalArgumentException("Surah number must be between 1 and 114");
    }
}
