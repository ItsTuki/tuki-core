package cl.itstuki.core.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Duraciones como "30m", "2h", "7d", "1w2d" o "perm". */
public final class Durations {
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])");

    private Durations() {
    }

    /** Segundos, 0 = permanente, -1 = no es una duración. */
    public static long parse(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        if (value.equals("perm") || value.equals("permanente") || value.equals("permanent")) {
            return 0;
        }
        Matcher matcher = PART.matcher(value);
        long total = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                return -1;
            }
            end = matcher.end();
            long n = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            total += unit.equals("s") ? n : unit.equals("m") ? n * 60 : unit.equals("h") ? n * 3600
                    : unit.equals("d") ? n * 86400 : n * 604800;
        }
        return end == value.length() && total > 0 ? total : -1;
    }

    public static String format(long seconds) {
        if (seconds <= 0) {
            return "∞";
        }
        long d = seconds / 86400;
        long h = seconds % 86400 / 3600;
        long m = seconds % 3600 / 60;
        long s = seconds % 60;
        StringBuilder out = new StringBuilder();
        if (d > 0) {
            out.append(d).append("d ");
        }
        if (h > 0) {
            out.append(h).append("h ");
        }
        if (m > 0 && d == 0) {
            out.append(m).append("m ");
        }
        if (s > 0 && d == 0 && h == 0) {
            out.append(s).append("s");
        }
        return out.toString().trim();
    }
}
