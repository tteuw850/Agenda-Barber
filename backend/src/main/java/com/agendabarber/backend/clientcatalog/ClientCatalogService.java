package com.agendabarber.backend.clientcatalog;

import com.agendabarber.backend.clientcatalog.ClientCatalogDtos.ClientInput;
import com.agendabarber.backend.clientcatalog.ClientCatalogDtos.ClientView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All reads and mutations use barberId obtained exclusively from a validated JWT. */
@Service
@Profile("neon")
public class ClientCatalogService {
    private static final Set<String> EDITABLE = Set.of(
            "name", "phone", "email", "birthDate", "notes", "favorite", "photoUrl");

    private static final String SELECT = "SELECT c.id,c.name,c.phone,c.email,c.birth_date,c.notes,"
            + "c.favorite,c.photo_url,c.active,c.created_at,c.updated_at,"
            + "COALESCE(stats.completed_count,0) AS completed_count,stats.last_visit "
            + "FROM clients c LEFT JOIN LATERAL ("
            + "SELECT count(*) AS completed_count, max(a.starts_at) AS last_visit "
            + "FROM appointments a WHERE a.barber_id=c.barber_id AND a.client_id=c.id "
            + "AND a.status='COMPLETED'::appointment_status) stats ON true ";
    private static final RowMapper<ClientView> MAPPER = (rs, number) -> map(rs);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Validator validator;

    public ClientCatalogService(JdbcTemplate jdbc, ObjectMapper mapper, Validator validator) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<ClientView> list(UUID barberId, String search, String filter, int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0 || offset > 10000) {
            throw new InvalidClientException();
        }
        String term = search == null ? "" : search.strip();
        if (term.length() > 150) {
            throw new InvalidClientException();
        }
        String view = filter == null ? "all" : filter.toLowerCase(Locale.ROOT);
        if (!Set.of("all", "recent", "favorites").contains(view)) {
            throw new InvalidClientException();
        }
        List<Object> arguments = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT).append(
                "WHERE c.barber_id=? AND c.active=true ");
        arguments.add(barberId);
        if (!term.isEmpty()) {
            String digits = term.replaceAll("[^0-9]", "");
            sql.append("AND (position(lower(?) in lower(c.name))>0 ");
            arguments.add(term);
            if (!digits.isEmpty()) {
                sql.append("OR position(? in COALESCE(c.phone,''))>0 ");
                arguments.add(digits);
            }
            sql.append(") ");
        }
        if ("favorites".equals(view)) {
            sql.append("AND c.favorite=true ");
        } else if ("recent".equals(view)) {
            sql.append("AND stats.completed_count>0 ");
        }
        if ("recent".equals(view)) {
            sql.append("ORDER BY stats.last_visit DESC NULLS LAST, lower(c.name),c.id ");
        } else {
            sql.append("ORDER BY lower(c.name),c.id ");
        }
        sql.append("LIMIT ? OFFSET ?");
        arguments.add(limit);
        arguments.add(offset);
        return jdbc.query(sql.toString(), MAPPER, arguments.toArray());
    }

    @Transactional(readOnly = true)
    public ClientView get(UUID barberId, UUID clientId) {
        try {
            return jdbc.queryForObject(SELECT
                    + "WHERE c.barber_id=? AND c.id=? AND c.active=true",
                    MAPPER, barberId, clientId);
        } catch (EmptyResultDataAccessException ignored) {
            throw new ClientNotFoundException();
        }
    }

    @Transactional
    public ClientView create(UUID barberId, ClientInput input) {
        ClientInput data = normalize(input);
        try {
            UUID id = jdbc.queryForObject(
                    "INSERT INTO clients(barber_id,name,phone,email,birth_date,notes,favorite,photo_url) "
                            + "VALUES(?,?,?,?,?,?,?,?) RETURNING id",
                    UUID.class, barberId, data.name(), data.phone(), data.email(),
                    data.birthDate(), data.notes(), data.favorite(), data.photoUrl());
            return get(barberId, id);
        } catch (DataIntegrityViolationException error) {
            throw conflictOrOriginal(error);
        }
    }

    /** JSON merge semantics: missing keys preserve values; null clears optional fields. */
    @Transactional
    public ClientView update(UUID barberId, UUID clientId, JsonNode patch) {
        if (!(patch instanceof ObjectNode object) || object.isEmpty()) {
            throw new InvalidClientException();
        }
        ClientView current = get(barberId, clientId);
        ObjectNode merged = mapper.createObjectNode();
        merged.put("name", current.name());
        putOptional(merged, "phone", current.phone());
        putOptional(merged, "email", current.email());
        putOptional(merged, "birthDate",
                current.birthDate() == null ? null : current.birthDate().toString());
        putOptional(merged, "notes", current.notes());
        merged.put("favorite", current.favorite());
        putOptional(merged, "photoUrl", current.photoUrl());
        object.fields().forEachRemaining(field -> {
            if (!EDITABLE.contains(field.getKey())
                    || (("name".equals(field.getKey()) || "favorite".equals(field.getKey()))
                        && field.getValue().isNull())) {
                throw new InvalidClientException();
            }
            merged.set(field.getKey(), field.getValue());
        });
        ClientInput updated;
        try {
            updated = mapper.treeToValue(merged, ClientInput.class);
        } catch (JsonProcessingException error) {
            throw new InvalidClientException();
        }
        ClientInput data = normalize(updated);
        try {
            jdbc.update("UPDATE clients SET name=?,phone=?,email=?,birth_date=?,notes=?,"
                            + "favorite=?,photo_url=? WHERE barber_id=? AND id=? AND active=true",
                    data.name(), data.phone(), data.email(), data.birthDate(), data.notes(),
                    data.favorite(), data.photoUrl(), barberId, clientId);
        } catch (DataIntegrityViolationException error) {
            throw conflictOrOriginal(error);
        }
        return get(barberId, clientId);
    }

    /** Soft deletion retains appointment foreign keys and historical attribution. */
    @Transactional
    public void deactivate(UUID barberId, UUID clientId) {
        int changed = jdbc.update("UPDATE clients SET active=false WHERE barber_id=? "
                + "AND id=? AND active=true", barberId, clientId);
        if (changed != 1) {
            throw new ClientNotFoundException();
        }
    }

    private ClientInput normalize(ClientInput input) {
        if (input == null) {
            throw new InvalidClientException();
        }
        Set<ConstraintViolation<ClientInput>> violations = validator.validate(input);
        if (!violations.isEmpty()) {
            throw new InvalidClientException();
        }
        String name = input.name().strip();
        String phone = normalizePhone(input.phone());
        String email = optional(input.email());
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
        }
        return new ClientInput(name, phone, email, input.birthDate(),
                optional(input.notes()), input.favorite() != null && input.favorite(),
                optional(input.photoUrl()));
    }

    private static String normalizePhone(String value) {
        String raw = optional(value);
        if (raw == null) {
            return null;
        }
        if (!raw.matches("[+0-9() .-]+")) {
            throw new InvalidClientException();
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13)) {
            digits = digits.substring(2);
        }
        if (digits.length() != 10 && digits.length() != 11) {
            throw new InvalidClientException();
        }
        return digits;
    }

    private static String optional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void putOptional(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static ClientView map(ResultSet rs) throws SQLException {
        Timestamp last = rs.getTimestamp("last_visit");
        LocalDate birthday = rs.getObject("birth_date", LocalDate.class);
        return new ClientView(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("phone"), rs.getString("email"), birthday, rs.getString("notes"),
                rs.getBoolean("favorite"), rs.getString("photo_url"), rs.getBoolean("active"),
                rs.getLong("completed_count"), last == null ? null : last.toInstant(),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static RuntimeException conflictOrOriginal(DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return new DuplicatePhoneException();
            }
        }
        return error;
    }

    public static final class ClientNotFoundException extends RuntimeException {
        public ClientNotFoundException() {
            super("Client not found");
        }
    }

    public static final class InvalidClientException extends RuntimeException {
        public InvalidClientException() {
            super("Invalid client data");
        }
    }

    public static final class DuplicatePhoneException extends RuntimeException {
        public DuplicatePhoneException() {
            super("Phone already registered");
        }
    }
}
