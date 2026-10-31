package com.agendabarber.backend.common;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadRetryExecutorTests {
    private final List<Long> delays = new ArrayList<>();
    private final ReadRetryExecutor retries = new ReadRetryExecutor(delays::add, () -> 0L);

    @Test
    void retriesConnectionFailureTwiceThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String value = retries.run(() -> {
            if (calls.incrementAndGet() <= 2) {
                throw failure("08006");
            }
            return "recovered";
        });
        assertEquals("recovered", value);
        assertEquals(3, calls.get());
        assertEquals(List.of(100L, 200L), delays);
    }

    @Test
    void jitterIsIncludedInBoundedBackoff() {
        List<Long> observedDelays = new ArrayList<>();
        ReadRetryExecutor jittered = new ReadRetryExecutor(observedDelays::add, () -> 50L);
        AtomicInteger calls = new AtomicInteger();
        assertEquals("ok", jittered.run(() -> {
            if (calls.incrementAndGet() <= 2) {
                throw failure("08006");
            }
            return "ok";
        }));
        assertEquals(List.of(150L, 250L), observedDelays);
    }

    @Test
    void totalRetryWindowStopsFurtherAttempts() {
        AtomicInteger clockCalls = new AtomicInteger();
        ReadRetryExecutor bounded = new ReadRetryExecutor(delays::add, () -> 0L,
                () -> clockCalls.getAndIncrement() == 0 ? 0L : TimeUnit.SECONDS.toNanos(46));
        AtomicInteger calls = new AtomicInteger();
        assertThrows(ReadRetryExecutor.UnavailableAfterRetries.class,
                () -> bounded.run(() -> {
                    calls.incrementAndGet();
                    throw failure("08006");
                }));
        assertEquals(1, calls.get());
        assertTrue(delays.isEmpty());
    }

    @Test
    void typedTransientConnectionWithoutSqlStateIsRetryable() {
        assertTrue(ReadRetryExecutor.isConnectionSqlState(new DataAccessResourceFailureException(
                "temporary connection problem", new SQLTransientConnectionException("reset"))));
    }

    @Test
    void exhaustionReturnsTypedUnavailableForHttp503() {
        AtomicInteger calls = new AtomicInteger();
        ReadRetryExecutor.UnavailableAfterRetries unavailable = assertThrows(
                ReadRetryExecutor.UnavailableAfterRetries.class,
                () -> retries.run(() -> {
                    calls.incrementAndGet();
                    throw failure("08001");
                }));
        assertEquals(3, calls.get());
        assertEquals(List.of(100L, 200L), delays);
        assertTrue(unavailable.getCause() instanceof DataAccessResourceFailureException);
    }

    @Test
    void permanentSqlStatesNeverRetry() {
        for (String state : List.of("23505", "42601", "28000", "40001")) {
            AtomicInteger calls = new AtomicInteger();
            RuntimeException permanent = failure(state);
            RuntimeException propagated = assertThrows(RuntimeException.class,
                    () -> retries.run(() -> {
                        calls.incrementAndGet();
                        throw permanent;
                    }));
            assertSame(permanent, propagated);
            assertEquals(1, calls.get());
        }
        assertTrue(delays.isEmpty());
    }

    @Test
    void unspecifiedSqlStateAndOrdinaryErrorsNeverRetry() {
        assertFalse(ReadRetryExecutor.isConnectionSqlState(failure(null)));
        assertFalse(ReadRetryExecutor.isConnectionSqlState(new IllegalArgumentException("bad input")));
        assertTrue(ReadRetryExecutor.isConnectionSqlState(failure("08003")));
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> retries.run(() -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("bad input");
        }));
        assertEquals(1, calls.get());
        assertTrue(delays.isEmpty());
    }

    @Test
    void interruptedSleepStopsRetriesAndPreservesFlag() {
        ReadRetryExecutor interrupted = new ReadRetryExecutor(millis -> {
            throw new InterruptedException("shutdown");
        }, () -> 0L);
        AtomicInteger calls = new AtomicInteger();
        try {
            assertThrows(ReadRetryExecutor.UnavailableAfterRetries.class,
                    () -> interrupted.run(() -> {
                        calls.incrementAndGet();
                        throw failure("08006");
                    }));
            assertEquals(1, calls.get());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void unavailableResponseKeepsStructured503AndRetryAfter() {
        var response = new ApiExceptionHandler().unavailableDatabase(
                new ReadRetryExecutor.UnavailableAfterRetries(failure("08006")));
        assertEquals(503, response.getStatusCode().value());
        assertEquals("5", response.getHeaders().getFirst("Retry-After"));
        assertEquals("DB_TEMPORARILY_UNAVAILABLE", response.getBody().getProperties().get("code"));
        assertEquals("Service unavailable", response.getBody().getTitle());
        assertEquals("Storage temporarily unavailable.", response.getBody().getDetail());
    }

    private static DataAccessResourceFailureException failure(String state) {
        return new DataAccessResourceFailureException(
                "database unavailable", new SQLException("connection reset", state));
    }
}
