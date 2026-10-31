package com.agendabarber.backend;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.management.ManagementFactory;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class NeonHikariRuntimeIntegrationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("agenda_pool_test").withUsername("tests").withPassword("tests");

    @Test
    void boundSettingsRegisterJmxAndHandleRealConnections() throws Exception {
        PropertySource<?> neon = new YamlPropertySourceLoader()
                .load("app", new ClassPathResource("application.yml")).stream()
                .filter(s -> "neon".equals(s.getProperty("spring.config.activate.on-profile")))
                .findFirst().orElseThrow();
        Binder binder = new Binder(ConfigurationPropertySources.from(neon));
        HikariConfig config = binder.bind("spring.datasource.hikari",
                Bindable.of(HikariConfig.class)).orElseThrow(() -> new IllegalStateException());
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        assertEquals("AgendaBarberNeonPool", config.getPoolName());
        config.setPoolName("AgendaBarberNeonPoolRuntimeTest");

        try (HikariDataSource pool = new HikariDataSource(config)) {
            assertEquals("AgendaBarberNeonPoolRuntimeTest", pool.getPoolName());
            assertEquals(0, pool.getMinimumIdle());
            assertEquals(3, pool.getMaximumPoolSize());
            assertEquals(0, pool.getKeepaliveTime());
            assertTrue(pool.isRegisterMbeans());
            assertEquals("10", pool.getDataSourceProperties().getProperty("connectTimeout"));
            assertEquals("15", pool.getDataSourceProperties().getProperty("loginTimeout"));
            assertEquals("60", pool.getDataSourceProperties().getProperty("socketTimeout"));
            assertEquals("false", pool.getDataSourceProperties().getProperty("logServerErrorDetail"));
            ObjectName jmx = new ObjectName(
                    "com.zaxxer.hikari:type=Pool (" + pool.getPoolName() + ")");
            assertTrue(ManagementFactory.getPlatformMBeanServer().isRegistered(jmx));
            try (var conn = pool.getConnection();
                    var stmt = conn.createStatement();
                    var rs = stmt.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertTrue(pool.getHikariPoolMXBean().getActiveConnections() >= 1);
            }
            assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
            assertTrue(pool.getHikariPoolMXBean().getTotalConnections() <= 3);
            int backendPid;
            try (var connection = pool.getConnection();
                    var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT pg_backend_pid()")) {
                assertTrue(result.next());
                backendPid = result.getInt(1);
            }
            try (var admin = java.sql.DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    var terminate = admin.prepareStatement("SELECT pg_terminate_backend(?)")) {
                terminate.setInt(1, backendPid);
                try (var result = terminate.executeQuery()) {
                    assertTrue(result.next());
                    assertTrue(result.getBoolean(1));
                }
            }
            try (var connection = pool.getConnection();
                    var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT 1")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            } catch (java.sql.SQLException connectionLost) {
                // A failed in-flight query is not retried by the connection pool.
                assertTrue(connectionLost.getSQLState().startsWith("08")
                        || "57P01".equals(connectionLost.getSQLState()));
            }
            // A subsequent fresh read succeeds after Hikari evicts the broken session.
            try (var recovered = pool.getConnection();
                    var statement = recovered.createStatement();
                    var result = statement.executeQuery("SELECT 1")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
            assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
            assertTrue(pool.getHikariPoolMXBean().getTotalConnections() <= 3);

        }
    }
}
