package com.agendabarber.backend.auth;

import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Profile("neon")
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final LoginRateLimiter limiter;
    private final AuthSessionService sessions;
    private final String dummyHash;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder encoder, LoginRateLimiter limiter,
                       AuthSessionService sessions) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.limiter = limiter;
        this.sessions = sessions;
        dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public LoginToken login(String identifier, String password, String remoteAddress) {
        String normalized = identifier.strip().toLowerCase(Locale.ROOT);
        // Keep email-based sign-in compatible with older APKs during rollout.
        String identifierColumn = normalized.contains("@") ? "email" : "username";
        limiter.check(normalized, remoteAddress);
        UserAccount account = null;
        try {
            account = jdbc.queryForObject(
                    "SELECT u.id, u.password_hash, u.active, u.role::text, "
                            + "u.credential_version, b.id AS barber_id "
                            + "FROM users u LEFT JOIN barbers b ON b.user_id=u.id "
                            + "WHERE lower(u." + identifierColumn + ")=?",
                    (rs, row) -> new UserAccount(
                            rs.getObject("id", UUID.class),
                            rs.getString("password_hash"),
                            rs.getBoolean("active"),
                            rs.getString("role"),
                            rs.getObject("barber_id", UUID.class),
                            rs.getLong("credential_version")),
                    normalized);
        } catch (EmptyResultDataAccessException ignored) {
            // Unknown accounts receive the same generic response.
        }
        boolean matches = encoder.matches(password, account == null ? dummyHash : account.hash());
        if (account == null || !matches || !account.active()
                || !"BARBER".equals(account.role()) || account.barberId() == null) {
            LOGGER.info("event=auth.login_rejected");
            throw new InvalidLoginException();
        }
        LoginToken issued = sessions.newSession(account.userId(), account.barberId(),
                account.credentialVersion());
        limiter.success(normalized);
        LOGGER.info("event=auth.login_succeeded");
        return issued;
    }

    private record UserAccount(UUID userId, String hash, boolean active, String role,
                               UUID barberId, long credentialVersion) {
    }

    public record LoginToken(String accessToken, String refreshToken, String tokenType,
                             long expiresInSeconds, long refreshExpiresInSeconds) {
    }

    public static final class InvalidLoginException extends RuntimeException {
        public InvalidLoginException() {
            super("Invalid credentials");
        }
    }
}
