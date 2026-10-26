package com.agendabarber.backend.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** RSA signing keys are required only in the database-backed profile. */
@Configuration
@Profile("neon")
@EnableMethodSecurity
public class AuthJwtConfig {
    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    JwtKeyMaterial jwtKeyMaterial(Environment environment) {
        try {
            String issuer = environment.getRequiredProperty("AUTH_JWT_ISSUER");
            String audience = environment.getRequiredProperty("AUTH_JWT_AUDIENCE");
            RSAPrivateKey privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(parsePem(environment.getRequiredProperty(
                            "AUTH_JWT_PRIVATE_KEY_PEM"), "PRIVATE KEY")));
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(parsePem(environment.getRequiredProperty(
                            "AUTH_JWT_PUBLIC_KEY_PEM"), "PUBLIC KEY")));
            if (publicKey.getModulus().bitLength() < 2048 || !privateKey.getModulus().equals(publicKey.getModulus())
                    || issuer.isBlank() || audience.isBlank()) {
                throw new IllegalStateException("Invalid JWT signing configuration");
            }
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update("agenda-barber-key-check".getBytes(StandardCharsets.UTF_8));
            byte[] proof = signature.sign();
            signature.initVerify(publicKey);
            signature.update("agenda-barber-key-check".getBytes(StandardCharsets.UTF_8));
            if (!signature.verify(proof)) {
                throw new IllegalStateException("Signing keys do not match");
            }
            return new JwtKeyMaterial(privateKey, publicKey, issuer, audience);
        } catch (Exception error) {
            throw new IllegalStateException("JWT signing configuration is missing or invalid");
        }
    }

    private static byte[] parsePem(String value, String kind) {
        String normalized = value.replace("\\n", "\n").replace("\r", "");
        String begin = "-----BEGIN " + kind + "-----";
        String end = "-----END " + kind + "-----";
        if (!normalized.contains(begin) || !normalized.contains(end)) {
            throw new IllegalArgumentException("Invalid key format");
        }
        String body = normalized.substring(normalized.indexOf(begin) + begin.length(),
                normalized.indexOf(end)).replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeyMaterial material) {
        RSAKey key = new RSAKey.Builder(material.publicKey()).privateKey(material.privateKey()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeyMaterial material, JdbcTemplate jdbc) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(material.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> claims = token -> {
            try {
                UUID userId = UUID.fromString(token.getSubject());
                UUID barberId = UUID.fromString(token.getClaimAsString("barber_id"));
                String role = token.getClaimAsString("role");
                UUID sessionId = UUID.fromString(token.getClaimAsString("session_id"));
                Object rawVersion = token.getClaim("credential_version");
                if (!(rawVersion instanceof Number version) || !"BARBER".equals(role)
                        || token.getId() == null || token.getId().isBlank()
                        || !token.getAudience().contains(material.audience())) {
                    return invalid();
                }
                Boolean active = jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM users u JOIN barbers b ON b.user_id=u.id "
                                + "WHERE u.id=? AND b.id=? AND u.active=true "
                                + "AND u.role='BARBER'::user_role AND u.credential_version=? "
                                + "AND EXISTS(SELECT 1 FROM auth_refresh_tokens r "
                                + "WHERE r.user_id=u.id AND r.session_id=? "
                                + "AND r.revoked_at IS NULL AND r.expires_at > now()))",
                        Boolean.class, userId, barberId, version.longValue(), sessionId);
                return Boolean.TRUE.equals(active) ? OAuth2TokenValidatorResult.success() : invalid();
            } catch (IllegalArgumentException | NullPointerException error) {
                return invalid();
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(material.issuer()), claims));
        return decoder;
    }

    private static OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Invalid authentication token", null));
    }

    public record JwtKeyMaterial(RSAPrivateKey privateKey, RSAPublicKey publicKey,
                                 String issuer, String audience) {
    }
}
