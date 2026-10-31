package com.agendabarber.backend.common;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Retries SQLState 08 connection errors on explicit, idempotent GET reads only. */
@Component
@Profile("neon")
public final class ReadRetryExecutor {
    static final int MAX_ATTEMPTS = 3;
    static final long MAX_RETRY_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(45);
    private final Sleeper sleeper;
    private final LongSupplier jitter;
    private final LongSupplier nanoClock;

    public ReadRetryExecutor() {
        this(Thread::sleep, () -> ThreadLocalRandom.current().nextLong(51), System::nanoTime);
    }

    ReadRetryExecutor(Sleeper sleeper, LongSupplier jitter) {
        this(sleeper, jitter, System::nanoTime);
    }

    ReadRetryExecutor(Sleeper sleeper, LongSupplier jitter, LongSupplier nanoClock) {
        this.sleeper = Objects.requireNonNull(sleeper);
        this.jitter = Objects.requireNonNull(jitter);
        this.nanoClock = Objects.requireNonNull(nanoClock);
    }

    /** The caller must supply a read-only operation that is safe to repeat. */
    public <T> T run(Supplier<T> read) {
        Objects.requireNonNull(read);
        long startedAt = nanoClock.getAsLong();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return read.get();
            } catch (RuntimeException failure) {
                if (!isConnectionSqlState(failure)) {
                    throw failure;
                }
                if (attempt == MAX_ATTEMPTS
                        || nanoClock.getAsLong() - startedAt >= MAX_RETRY_WINDOW_NANOS) {
                    throw new UnavailableAfterRetries(failure);
                }
                long delayMillis = (100L << (attempt - 1)) + Math.floorMod(jitter.getAsLong(), 51L);
                if (nanoClock.getAsLong() - startedAt
                        >= MAX_RETRY_WINDOW_NANOS - TimeUnit.MILLISECONDS.toNanos(delayMillis)) {
                    throw new UnavailableAfterRetries(failure);
                }
                try {
                    sleeper.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new UnavailableAfterRetries(failure);
                }
            }
        }
        throw new IllegalStateException("Read retry loop ended unexpectedly");
    }

    static boolean isConnectionSqlState(Throwable failure) {
        boolean typedTransientConnection = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                String sqlState = sql.getSQLState();
                if (sqlState != null) {
                    return sqlState.startsWith("08");
                }
                if (sql instanceof SQLTransientConnectionException) {
                    typedTransientConnection = true;
                }
            }
        }
        return typedTransientConnection;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** Sanitized as HTTP 503; never expose JDBC credentials or host details. */
    public static final class UnavailableAfterRetries extends RuntimeException {
        public UnavailableAfterRetries(RuntimeException cause) {
            super("Storage temporarily unavailable after read retries", cause);
        }
    }
}
