package analytics.query;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Optional inclusive date window of a stats request. A null bound is open.
 */
public record DateRange(LocalDate from, LocalDate to) {
    public static final DateRange ALL = new DateRange(null, null);

    public DateRange {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRange("from must not be after to");
        }
    }

    /**
     * Blank or null text means an open bound. Anything else must be an ISO date (yyyy-MM-dd).
     */
    public static DateRange parse(String from, String to) {
        return new DateRange(date(from, "from"), date(to, "to"));
    }

    public boolean isOpen() {
        return from == null && to == null;
    }

    public boolean contains(LocalDate day) {
        return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
    }

    private static LocalDate date(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new InvalidRange(field + " must be an ISO date (yyyy-MM-dd)");
        }
    }

    public static final class InvalidRange extends RuntimeException {
        public InvalidRange(String message) {
            super(message);
        }
    }
}
