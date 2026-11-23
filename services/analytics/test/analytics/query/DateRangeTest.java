package analytics.query;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateRangeTest {
    @Test
    void blankBoundsAreOpen() {
        DateRange range = DateRange.parse(null, " ");
        assertTrue(range.isOpen());
        assertNull(range.from());
        assertNull(range.to());
    }

    @Test
    void parsesIsoDatesAndIncludesBothEnds() {
        DateRange range = DateRange.parse("2026-10-01", "2026-10-31");
        assertEquals(LocalDate.of(2026, 10, 1), range.from());
        assertTrue(range.contains(LocalDate.of(2026, 10, 1)));
        assertTrue(range.contains(LocalDate.of(2026, 10, 31)));
        assertFalse(range.contains(LocalDate.of(2026, 9, 30)));
        assertFalse(range.contains(LocalDate.of(2026, 11, 1)));
    }

    @Test
    void rejectsBadDateAndReversedRange() {
        assertThrows(DateRange.InvalidRange.class, () -> DateRange.parse("yesterday", null));
        assertThrows(DateRange.InvalidRange.class, () -> DateRange.parse("2026-10-31", "2026-10-01"));
    }
}
