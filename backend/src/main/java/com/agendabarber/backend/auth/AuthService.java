package com.agendabarber.backend.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@Profile("neon")
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AuthJwtConfig.JwtKeyMaterial material;
    private final LoginRateLimiter limiter;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder encoder, JwtEncoder jwtEncoder,
                       AuthJwtConfig.JwtKeyMaterial material, LoginRateLimiter limiter, Clock clock) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.material = material;
        this.limiter = limiter;
        this.clock = clock;
        dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public LoginToken login(String email, String password, String remoteAddress) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        limiter.check(normalized, remoteAddress);
        UserAccount account = null;
        try {
            account = jdbc.queryForObject(
                    "SELECT u.id, u.password_hash, u.active, u.role::text, b.id AS barber_id "
                            + "FROM users u LEFT JOIN barbers b ON b.user_id=u.id "
                            + "WHERE lower(u.email)=?",
                    (rs, row) -> new UserAccount(
                            rs.getObject("id", UUID.class),
                            rs.getString("password_hash"),
                            rs.getBoolean("active"),
                            rs.getString("role"),
                            rs.getObject("barber_id", UUID.class)),
                    normalized);
        } catch (EmptyResultDataAccessException ignored) {
            // The same generic response must be returned for unknown accounts.
        }
        boolean matches = encoder.matches(password, account == null ? dummyHash : account.hash());
        if (account == null || !matches || !account.active()
                || !"BARBER".equals(account.role()) || account.barberId() == null) {
            LOGGER.info("event=auth.login_rejected");
            throw new InvalidLoginException();
        }
        limiter.success(normalized);
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(material.issuer())
                .audience(List.of(material.audience()))
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TTL))
                .id(UUID.randomUUID().toString())
                .subject(account.userId().toString())
                .claim("barber_id", account.barberId().toString())
                .claim("role", "BARBER")
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        LOGGER.info("event=auth.login_succeeded");
        return new LoginToken(token, "Bearer", ACCESS_TTL.toSeconds());
    }

    private record UserAccount(UUID userId, String hash, boolean active, String role, UUID barberId) {
    }

    public record LoginToken(String accessToken, String tokenType, long expiresInSeconds) {
    }

    public static final class InvalidLoginException extends RuntimeException {
        public InvalidLoginException() {
            super("Invalid credentials");
        }
    }
}
