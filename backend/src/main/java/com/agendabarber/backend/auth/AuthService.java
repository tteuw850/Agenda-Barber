package com.agendabarber.backend.auth;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    /** Change password without logging credentials; revoke all sessions atomically. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 12
                || newPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || currentPassword == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha inválida");
        }
        List<String> hashes = jdbc.query(
                "SELECT password_hash FROM users WHERE id=? AND active=true FOR UPDATE",
                (rs, index) -> rs.getString(1), userId);
        if (hashes.isEmpty() || !encoder.matches(currentPassword, hashes.get(0))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
        }
        if (encoder.matches(newPassword, hashes.get(0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma senha diferente");
        }
        jdbc.update("UPDATE users SET password_hash=?, credential_version=credential_version+1, "
                + "updated_at=now() WHERE id=?", encoder.encode(newPassword), userId);
        jdbc.update("UPDATE auth_refresh_tokens SET revoked_at=now() "
                + "WHERE user_id=? AND revoked_at IS NULL", userId);
        LOGGER.info("event=auth.password_changed");
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
