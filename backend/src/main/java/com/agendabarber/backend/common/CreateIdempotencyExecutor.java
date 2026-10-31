package com.agendabarber.backend.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Replays a previously committed creation instead of inserting duplicates.
 * The tenant row lock and resource/receipt writes share one transaction.
 */
@Service
@Profile("neon")
public class CreateIdempotencyExecutor {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public CreateIdempotencyExecutor(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
    }

    public <T> T run(UUID barberId, UUID userId, String key, String operation,
            Object request, Supplier<T> create, Function<T, UUID> resourceId,
            Function<UUID, T> readExisting) {
        // Legacy clients remain compatible until Android sends a key for every write.
        if (key == null) {
            return create.get();
        }
        if (!key.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new InvalidIdempotencyKeyException();
        }
        String requestHash = sha256(barberId + "|" + operation + "|" + request);
        return transaction.execute(status -> {
            // A single barber's concurrent creations cannot race to insert the same key.
            jdbc.queryForObject("SELECT id FROM barbers WHERE id=? FOR UPDATE",
                    UUID.class, barberId);
            List<StoredResult> stored = jdbc.query(
                    "SELECT operation, request_hash, resource_id FROM idempotency_records"
                            + " WHERE user_id=? AND idempotency_key=?",
                    (rs, ignored) -> new StoredResult(
                            rs.getString(1), rs.getString(2),
                            rs.getObject(3, UUID.class)),
                    userId, key);
            if (!stored.isEmpty()) {
                StoredResult previous = stored.getFirst();
                if (!operation.equals(previous.operation())
                        || !requestHash.equals(previous.hash()) || previous.resourceId() == null) {
                    throw new IdempotencyConflictException();
                }
                return readExisting.apply(previous.resourceId());
            }
            T created = create.get();
            UUID id = resourceId.apply(created);
            if (id == null) {
                throw new IllegalStateException("Created resource has no identifier");
            }
            jdbc.update("INSERT INTO idempotency_records"
                            + "(user_id,idempotency_key,request_hash,operation,resource_id)"
                            + " VALUES(?,?,?,?,?)",
                    userId, key, requestHash, operation, id);
            return created;
        });
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private record StoredResult(String operation, String hash, UUID resourceId) {
    }

    public static final class InvalidIdempotencyKeyException extends RuntimeException {
    }

    public static final class IdempotencyConflictException extends RuntimeException {
    }
}
