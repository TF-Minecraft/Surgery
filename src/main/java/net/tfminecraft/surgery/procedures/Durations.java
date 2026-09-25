package net.tfminecraft.surgery.procedures;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ==============================================
// Parses config durations such as "12h", "90m" or "1d 6h" into milliseconds
// ==============================================
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([dhms])");

    private Durations() {
    }

    // Returns -1 when the value is blank or not a valid duration
    public static long parseMs(String value) {
        if (value == null || value.isBlank()) {
            return -1L;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = PART.matcher(normalized);
        long total = 0L;
        int end = 0;
        while (matcher.find()) {
            if (!normalized.substring(end, matcher.start()).isBlank()) {
                return -1L;
            }
            long amount = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2)) {
                case "d" -> amount * 86_400_000L;
                case "h" -> amount * 3_600_000L;
                case "m" -> amount * 60_000L;
                default -> amount * 1_000L;
            };
            end = matcher.end();
        }
        return end > 0 && normalized.substring(end).isBlank() ? total : -1L;
    }

    // ==============================================
    // Short hours form for player messages: "12h", or "45m" under an hour
    // ==============================================
    public static String formatHours(long ms) {
        if (ms < 3_600_000L) {
            return Math.max(0L, (ms + 59_999L) / 60_000L) + "m";
        }
        return ((ms + 3_599_999L) / 3_600_000L) + "h";
    }
}
