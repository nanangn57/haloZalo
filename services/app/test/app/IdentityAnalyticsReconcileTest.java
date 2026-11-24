package app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class IdentityAnalyticsReconcileTest {
    @Test
    void deterministicIdsAreStableAndDistinctByKind() {
        String first = IdentityAnalyticsReconcile.deterministicId("reg", "user-1");
        String again = IdentityAnalyticsReconcile.deterministicId("reg", "user-1");
        String other = IdentityAnalyticsReconcile.deterministicId("ver", "user-1");
        assertEquals(first, again);
        assertNotEquals(first, other);
        assertEquals(36, first.length());
    }
}
