package storage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.SqlParameterValue;

/**
 * Conversions between domain values and JDBC parameters and columns. Ids arrive already canonical.
 */
final class Sql {
    private Sql() {
    }

    static SqlParameterValue uuid(String id) {
        return new SqlParameterValue(Types.OTHER, id == null ? null : UUID.fromString(id));
    }

    static SqlParameterValue jsonb(String json) {
        return new SqlParameterValue(Types.VARCHAR, json);
    }

    static SqlParameterValue time(Instant instant) {
        return new SqlParameterValue(Types.TIMESTAMP_WITH_TIMEZONE,
            instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
