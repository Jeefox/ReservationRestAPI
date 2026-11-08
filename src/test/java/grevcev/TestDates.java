package grevcev;

import java.time.LocalDate;
import java.util.regex.Pattern;

public final class TestDates {
    private static final int YEAR = LocalDate.now().getYear() + 1;
    private static final Pattern DATE = Pattern.compile("<date:(\\d{2})-(\\d{2})>");

    private TestDates() {
    }

    public static LocalDate future(int month, int day) {
        return LocalDate.of(YEAR, month, day);
    }

    public static String futureJson(String json) {
        return DATE.matcher(json).replaceAll(match ->
                future(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2))).toString());
    }
}
