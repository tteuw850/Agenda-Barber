package com.agendabarber.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Single-instance fixed-window limiter, fail-closed on capacity. Does not
 * persist emails, IPs, tokens, or secrets. Distributed limits belong to F04.
 */
@Component
@Profile("neon")
public class LoginRateLimiter {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginRateLimiter.class);
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_KEYS = 10000;
    private final Map<String, Bucket> counters = new HashMap<>();
    private final Clock clock;

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void check(String normalizedEmail, String remoteAddress) {
        Instant now = clock.instant();
        counters.entrySet().removeIf(item -> !item.getValue().expiresAt().isAfter(now));
        String account = digest("account:" + normalizedEmail);
        String origin = digest("origin:" + remoteAddress);
        Bucket accountBucket = counters.get(account);
        Bucket originBucket = counters.get(origin);
        if ((accountBucket != null && accountBucket.count() >= 5)
                || (originBucket != null && originBucket.count() >= 20)) {
            LOGGER.warn("event=auth.login_rate_limited");
            throw new RateLimitedException();
        }
        if (counters.size() >= MAX_KEYS && (!counters.containsKey(account) || !counters.containsKey(origin))) {
            LOGGER.warn("event=auth.login_rate_limited");
            throw new RateLimitedException();
        }
        increment(account, now);
        increment(origin, now);
    }

    public synchronized void success(String normalizedEmail) {
        counters.remove(digest("account:" + normalizedEmail));
    }

    private void increment(String key, Instant now) {
        counters.compute(key, (id, bucket) -> bucket == null
                ? new Bucket(1, now.plus(WINDOW)) : new Bucket(bucket.count() + 1, bucket.expiresAt()));
    }

    private static String digest(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Required digest is not available");
        }
    }

    private record Bucket(int count, Instant expiresAt) {
    }

    public static final class RateLimitedException extends RuntimeException {
        public RateLimitedException() {
            super("Login temporarily limited");
        }
    }
}
