package com.agendabarber.backend.auth;

import java.time.Clock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTests {

    @Test
    void rejectsTwentyFirstAttemptFromOneOriginEvenWithDifferentAccounts() {
        LoginRateLimiter limiter = new LoginRateLimiter(Clock.systemUTC());
        for (int i = 0; i < 20; i++) {
            String email = "origin-" + i + "@tests.invalid";
            assertDoesNotThrow(() -> limiter.check(email, "192.0.2.10"));
        }
        assertThrows(LoginRateLimiter.RateLimitedException.class,
                () -> limiter.check("last@tests.invalid", "192.0.2.10"));
        assertDoesNotThrow(() -> limiter.check("last@tests.invalid", "192.0.2.11"));
    }

    @Test
    void rejectsSixthAttemptForOneAccountAcrossDifferentOrigins() {
        LoginRateLimiter limiter = new LoginRateLimiter(Clock.systemUTC());
        for (int i = 0; i < 5; i++) {
            String ip = "192.0.2." + (i + 1);
            assertDoesNotThrow(() -> limiter.check("target@tests.invalid", ip));
        }
        assertThrows(LoginRateLimiter.RateLimitedException.class,
                () -> limiter.check("target@tests.invalid", "192.0.2.99"));
    }
}
