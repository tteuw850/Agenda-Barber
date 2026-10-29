package com.agendabarber.backend.finance;

import com.agendabarber.backend.finance.ExpenseDtos.ExpenseInput;
import com.agendabarber.backend.finance.ExpenseDtos.ExpenseView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("neon")
public class ExpenseService {
    private static final String SELECT = "SELECT id,occurred_on,description,amount,category,"
            + "created_at,updated_at FROM expenses WHERE barber_id=? AND deleted_at IS NULL";
    private static final RowMapper<ExpenseView> MAPPER = (rs, ignored) -> map(rs);
    private final JdbcTemplate jdbc;

    public ExpenseService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ExpenseView> list(UUID barberId, LocalDate from, LocalDate to, int limit, int offset) {
        if (from == null || to == null || from.isAfter(to)
                || limit < 1 || limit > 200 || offset < 0 || offset > 10000) {
            throw new InvalidExpenseException();
        }
        return jdbc.query(SELECT + " AND occurred_on BETWEEN ? AND ?"
                        + " ORDER BY occurred_on DESC,id DESC LIMIT ? OFFSET ?",
                MAPPER, barberId, from, to, limit, offset);
    }

    @Transactional(readOnly = true)
    public ExpenseView get(UUID barberId, UUID id) {
        List<ExpenseView> rows = jdbc.query(SELECT + " AND id=?", MAPPER, barberId, id);
        if (rows.isEmpty()) {
            throw new ExpenseNotFoundException();
        }
        return rows.getFirst();
    }

    @Transactional
    public ExpenseView create(UUID barberId, UUID userId, String key, ExpenseInput input) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new InvalidExpenseException();
        }
        ExpenseInput clean = validate(input);
        String digest = hash(clean);
        // Same transaction & tenant lock serialize key lookup with creation.
        lockBarber(barberId);
        List<StoredKey> stored = jdbc.query(
                "SELECT operation,request_hash,resource_id FROM idempotency_records "
                        + "WHERE user_id=? AND idempotency_key=?",
                (rs, n) -> new StoredKey(rs.getString(1), rs.getString(2),
                        rs.getObject(3, UUID.class)),
                userId, key);
        if (!stored.isEmpty()) {
            StoredKey previous = stored.getFirst();
            if (!"EXPENSE_CREATE".equals(previous.operation())
                    || !digest.equals(previous.hash()) || previous.id() == null) {
                throw new ExpenseConflictException();
            }
            return get(barberId, previous.id());
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO expenses(id,barber_id,occurred_on,description,amount,category)"
                        + " VALUES(?,?,?,?,?,?)",
                id, barberId, clean.occurredOn(), clean.description(),
                clean.amount(), clean.category());
        jdbc.update("INSERT INTO idempotency_records(user_id,idempotency_key,request_hash,"
                        + "operation,resource_id) VALUES(?,?,?,'EXPENSE_CREATE',?)",
                userId, key, digest, id);
        return get(barberId, id);
    }

    @Transactional
    public ExpenseView update(UUID barberId, UUID id, ExpenseInput input) {
        ExpenseInput clean = validate(input);
        int changed = jdbc.update("UPDATE expenses SET occurred_on=?,description=?,amount=?,"
                        + "category=? WHERE barber_id=? AND id=? AND deleted_at IS NULL",
                clean.occurredOn(), clean.description(), clean.amount(), clean.category(),
                barberId, id);
        if (changed == 0) {
            throw new ExpenseNotFoundException();
        }
        return get(barberId, id);
    }

    @Transactional
    public void delete(UUID barberId, UUID id) {
        // An exact owner match is required; repeat DELETE stays idempotent.
        int affected = jdbc.update("UPDATE expenses SET deleted_at=now() WHERE barber_id=?"
                        + " AND id=? AND deleted_at IS NULL", barberId, id);
        if (affected == 0) {
            Integer owned = jdbc.queryForObject(
                    "SELECT count(*) FROM expenses WHERE barber_id=? AND id=?",
                    Integer.class, barberId, id);
            if (owned == null || owned == 0) {
                throw new ExpenseNotFoundException();
            }
        }
    }

    private void lockBarber(UUID barberId) {
        jdbc.queryForObject("SELECT id FROM barbers WHERE id=? FOR UPDATE", UUID.class, barberId);
    }

    public static ExpenseInput validate(ExpenseInput value) {
        if (value == null || value.occurredOn() == null
                || value.amount() == null || value.amount().signum() <= 0
                || value.amount().scale() > 2
                || value.amount().precision() - value.amount().scale() > 10
                || value.description() == null || value.description().isBlank()
                || value.description().length() > 255
                || value.category() == null || value.category().isBlank()
                || value.category().length() > 80) {
            throw new InvalidExpenseException();
        }
        return new ExpenseInput(value.occurredOn(), value.description().strip(),
                value.amount(), value.category().strip());
    }

    private static String hash(ExpenseInput input) {
        String data = input.occurredOn() + "|" + input.description() + "|"
                + input.amount().setScale(2) + "|" + input.category();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static ExpenseView map(ResultSet rs) throws SQLException {
        return new ExpenseView(rs.getObject("id", UUID.class),
                rs.getObject("occurred_on", LocalDate.class), rs.getString("description"),
                rs.getBigDecimal("amount"), rs.getString("category"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private record StoredKey(String operation, String hash, UUID id) {
    }

    public static class InvalidExpenseException extends RuntimeException {
    }

    public static class ExpenseNotFoundException extends RuntimeException {
    }

    public static class ExpenseConflictException extends RuntimeException {
    }
}
