package net.tfminecraft.surgery.procedures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurationsTest {

    private static final long HOUR = 3_600_000L;

    @Test
    void parsesSingleAndCombinedUnits() {
        assertEquals(12 * HOUR, Durations.parseMs("12h"));
        assertEquals(90 * 60_000L, Durations.parseMs("90m"));
        assertEquals(30 * HOUR, Durations.parseMs("1d 6h"));
        assertEquals(30 * HOUR, Durations.parseMs("1D6H"));
        assertEquals(0L, Durations.parseMs("0h"));
    }

    @Test
    void rejectsInvalidValues() {
        assertEquals(-1L, Durations.parseMs(null));
        assertEquals(-1L, Durations.parseMs(""));
        assertEquals(-1L, Durations.parseMs("12"));
        assertEquals(-1L, Durations.parseMs("twelve hours"));
        assertEquals(-1L, Durations.parseMs("12h soon"));
    }

    @Test
    void formatsHoursRoundedUp() {
        assertEquals("12h", Durations.formatHours(12 * HOUR));
        assertEquals("49h", Durations.formatHours(48 * HOUR + 1));
        assertEquals("45m", Durations.formatHours(45 * 60_000L));
    }

    @Test
    void overflowIsAnInvalidDurationInsteadOfAnExceptionOrWrappedPenalty() {
        assertEquals(-1L, Durations.parseMs("999999999999999999999999999h"));
        assertEquals(-1L, Durations.parseMs(Long.MAX_VALUE + "d"));
        assertEquals(-1L, Durations.parseMs("9223372036854775s 1s"));
    }

    @Test
    void formatsExtremeAndMinuteBoundaryDurationsWithoutOverflow() {
        long hour = 3_600_000L;
        assertEquals((Long.MAX_VALUE / hour + 1) + "h", Durations.formatHours(Long.MAX_VALUE));
        assertEquals("0m", Durations.formatHours(Long.MIN_VALUE));
        assertEquals("0m", Durations.formatHours(0));
        assertEquals("1m", Durations.formatHours(1));
        assertEquals("1m", Durations.formatHours(60_000));
        assertEquals("2m", Durations.formatHours(60_001));
    }

    @Test
    void parsesSecondsAndRejectsJunkBetweenValidParts() {
        assertEquals(1_001_000L, Durations.parseMs(" 16m 41s "));
        assertEquals(-1L, Durations.parseMs("1h junk 2m"));
        assertEquals(-1L, Durations.parseMs("   "));
    }
}
