package com.agendabarber.backend;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Validate Neon settings without opening a database connection. */
class NeonHikariConfigurationTests {
    @Test
    void neonPoolIsElasticAndJdbcTimeoutsAreBounded() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        PropertySource<?> neon = sources.stream()
                .filter(source -> "neon".equals(
                        source.getProperty("spring.config.activate.on-profile")))
                .findFirst().orElseThrow();

        assertEquals("AgendaBarberNeonPool",
                neon.getProperty("spring.datasource.hikari.pool-name"));
        assertEquals(0, neon.getProperty("spring.datasource.hikari.minimum-idle"));
        assertEquals(3, neon.getProperty("spring.datasource.hikari.maximum-pool-size"));
        assertEquals(60000, neon.getProperty("spring.datasource.hikari.idle-timeout"));
        assertEquals(20000, neon.getProperty("spring.datasource.hikari.connection-timeout"));
        assertEquals(5000, neon.getProperty("spring.datasource.hikari.validation-timeout"));
        assertEquals(0, neon.getProperty("spring.datasource.hikari.keepalive-time"));
        assertEquals(true, neon.getProperty("spring.datasource.hikari.register-mbeans"));
        assertEquals(10, neon.getProperty(
                "spring.datasource.hikari.data-source-properties.connectTimeout"));
        assertEquals(15, neon.getProperty(
                "spring.datasource.hikari.data-source-properties.loginTimeout"));
        assertEquals(60, neon.getProperty(
                "spring.datasource.hikari.data-source-properties.socketTimeout"));
        assertEquals(false, neon.getProperty(
                "spring.datasource.hikari.data-source-properties.logServerErrorDetail"));
        assertEquals(false, neon.getProperty("management.health.db.enabled"));
        assertEquals("health",
                neon.getProperty("management.endpoints.web.exposure.include"));
    }
}
