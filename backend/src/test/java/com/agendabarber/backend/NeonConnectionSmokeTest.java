package com.agendabarber.backend;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = "neon")
class NeonConnectionSmokeTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void selectsOneWithDevelopmentIdentityAndTls() {
        String jdbcUrl = System.getenv("NEON_JDBC_URL");
        String username = System.getenv("NEON_DB_USERNAME");

        assertTrue(jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql://"),
                "A PostgreSQL JDBC URL must be provided through NEON_JDBC_URL");
        assertTrue(jdbcUrl.matches("(?i).*([?&])sslmode=verify-full(&.*)?"),
                "JDBC TLS must use sslmode=verify-full");
        assertEquals("agenda_app_dev", username, "The smoke test must not use production credentials");

        Map<String, Object> result = jdbcTemplate.queryForMap(
                "SELECT 1 AS value, current_user AS role_name, current_database() AS database_name, "
                        + "(SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()) AS tls_enabled");

        assertEquals(1, ((Number) result.get("value")).intValue());
        assertEquals("agenda_app_dev", result.get("role_name"));
        assertEquals("agenda_barber", result.get("database_name"));
        assertEquals(Boolean.TRUE, result.get("tls_enabled"), "JDBC connection must negotiate TLS");
    }
}
