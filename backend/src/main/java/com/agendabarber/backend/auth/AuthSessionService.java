package com.agendabarber.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opaque single-use refresh credentials with replay-triggered family revocation. */
@Service
@Profile("neon")
public class AuthSessionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthSessionService.class);
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(14);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final JwtEncoder encoder;
    private final AuthJwtConfig.JwtKeyMaterial keys;
    private final Clock clock;

    public AuthSessionService(JdbcTemplate jdbc, JwtEncoder encoder,
                              AuthJwtConfig.JwtKeyMaterial keys, Clock clock) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.keys = keys;
        this.clock = clock;
    }

    @Transactional
    public AuthService.LoginToken newSession(UUID userId, UUID barberId, long version) {
        return issue(userId, barberId, version, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = InvalidSessionException.class)
    public AuthService.LoginToken refresh(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new InvalidSessionException();
        }
        String hash = digest(rawToken);
        RefreshRow row;
        try {
            row = jdbc.queryForObject(
                    "SELECT user_id, session_id, credential_version, expires_at, consumed_at, revoked_at "
                            + "FROM auth_refresh_tokens WHERE token_hash=? FOR UPDATE",
                    (rs, index) -> new RefreshRow(
                            rs.getObject("user_id", UUID.class),
                            rs.getObject("session_id", UUID.class),
                            rs.getLong("credential_version"),
                            rs.getTimestamp("expires_at").toInstant(),
                            rs.getTimestamp("consumed_at") != null,
                            rs.getTimestamp("revoked_at") != null),
                    hash);
        } catch (EmptyResultDataAccessException ignored) {
            throw new InvalidSessionException();
        }
        if (row.consumed()) {
            // Throwing does not roll back this security-critical revocation.
            revokeFamily(row.userId(), row.sessionId());
            LOGGER.warn("event=auth.refresh_replay");
            throw new InvalidSessionException();
        }
        if (row.revoked() || !row.expiresAt().isAfter(clock.instant())) {
            throw new InvalidSessionException();
        }
        Account account;
        try {
            account = jdbc.queryForObject(
                    "SELECT u.credential_version, b.id AS barber_id "
                            + "FROM users u JOIN barbers b ON b.user_id=u.id "
                            + "WHERE u.id=? AND u.active=true AND u.role='BARBER'::user_role",
                    (rs, index) -> new Account(
                            rs.getObject("barber_id", UUID.class), rs.getLong("credential_version")),
                    row.userId());
        } catch (EmptyResultDataAccessException ignored) {
            throw new InvalidSessionException();
        }
        if (account.version() != row.version()) {
            throw new InvalidSessionException();
        }
        jdbc.update("UPDATE auth_refresh_tokens SET consumed_at=now() WHERE token_hash=?", hash);
        AuthService.LoginToken rotated = issue(row.userId(), account.barberId(),
                account.version(), row.sessionId());
        LOGGER.info("event=auth.refresh_succeeded");
        return rotated;
    }

    @Transactional
    public void logout(UUID userId, UUID sessionId) {
        revokeFamily(userId, sessionId);
        LOGGER.info("event=auth.logout_succeeded");
    }

    private void revokeFamily(UUID userId, UUID sessionId) {
        jdbc.update("UPDATE auth_refresh_tokens SET revoked_at=now() "
                + "WHERE user_id=? AND session_id=? AND revoked_at IS NULL", userId, sessionId);
    }

    private AuthService.LoginToken issue(UUID userId, UUID barberId, long version, UUID sessionId) {
        Instant now = clock.instant();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO auth_refresh_tokens "
                        + "(user_id,session_id,token_hash,credential_version,expires_at) "
                        + "VALUES(?,?,?,?,?)",
                userId, sessionId, digest(rawRefresh), version, Timestamp.from(now.plus(REFRESH_TTL)));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(keys.issuer())
                .audience(List.of(keys.audience()))
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TTL))
                .id(UUID.randomUUID().toString())
                .claim("barber_id", barberId.toString())
                .claim("role", "BARBER")
                .claim("session_id", sessionId.toString())
                .claim("credential_version", version)
                .build();
        String access = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        return new AuthService.LoginToken(access, rawRefresh, "Bearer",
                ACCESS_TTL.toSeconds(), REFRESH_TTL.toSeconds());
    }

    private static String digest(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private record Account(UUID barberId, long version) {
    }

    private record RefreshRow(UUID userId, UUID sessionId, long version, Instant expiresAt,
                              boolean consumed, boolean revoked) {
    }

    public static final class InvalidSessionException extends RuntimeException {
        public InvalidSessionException() {
            super("Invalid or expired session");
        }
    }
}
