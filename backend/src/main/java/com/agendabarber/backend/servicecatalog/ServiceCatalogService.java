package com.agendabarber.backend.servicecatalog;

import com.agendabarber.backend.servicecatalog.ServiceCatalogDtos.CreateService;
import com.agendabarber.backend.servicecatalog.ServiceCatalogDtos.IconKey;
import com.agendabarber.backend.servicecatalog.ServiceCatalogDtos.ServiceView;
import com.agendabarber.backend.servicecatalog.ServiceCatalogDtos.UpdateService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-owned barber scope: service IDs never bypass tenant isolation. */
@Service
@Profile("neon")
public class ServiceCatalogService {
    private static final String COLUMNS = "id, name, duration_minutes, default_price, "
            + "is_haircut, active, icon_key, created_at, updated_at";
    private static final RowMapper<ServiceView> MAPPER = (rs, number) -> map(rs);
    private final JdbcTemplate jdbc;

    public ServiceCatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ServiceView> list(UUID barberId, boolean activeOnly) {
        String sql = "SELECT " + COLUMNS + " FROM services WHERE barber_id=?"
                + (activeOnly ? " AND active=true" : "") + " ORDER BY lower(name), id";
        return jdbc.query(sql, MAPPER, barberId);
    }

    @Transactional(readOnly = true)
    public ServiceView get(UUID barberId, UUID id) {
        try {
            return jdbc.queryForObject("SELECT " + COLUMNS + " FROM services "
                    + "WHERE barber_id=? AND id=?", MAPPER, barberId, id);
        } catch (EmptyResultDataAccessException ignored) {
            throw new ServiceNotFoundException();
        }
    }

    @Transactional
    public ServiceView create(UUID barberId, CreateService request) {
        String name = normalizeName(request.name());
        BigDecimal price = request.defaultPrice();
        IconKey icon = request.iconKey() == null ? IconKey.SCISSORS : request.iconKey();
        boolean active = request.active() == null || request.active();
        return jdbc.queryForObject("INSERT INTO services "
                        + "(barber_id,name,duration_minutes,default_price,is_haircut,active,icon_key) "
                        + "VALUES(?,?,?,?,?,?,?) RETURNING " + COLUMNS,
                MAPPER, barberId, name, request.durationMinutes(), price,
                request.isHaircut(), active, icon.name());
    }

    @Transactional
    public ServiceView update(UUID barberId, UUID id, UpdateService request) {
        if (!request.hasChanges()) {
            throw new InvalidServiceUpdateException();
        }
        String name = request.name() == null ? null : normalizeName(request.name());
        String icon = request.iconKey() == null ? null : request.iconKey().name();
        try {
            return jdbc.queryForObject("UPDATE services SET "
                            + "name=COALESCE(?,name), duration_minutes=COALESCE(?,duration_minutes), "
                            + "default_price=COALESCE(?,default_price), is_haircut=COALESCE(?,is_haircut), "
                            + "active=COALESCE(?,active), icon_key=COALESCE(?,icon_key) "
                            + "WHERE barber_id=? AND id=? RETURNING " + COLUMNS,
                    MAPPER, name, request.durationMinutes(), request.defaultPrice(),
                    request.isHaircut(), request.active(), icon, barberId, id);
        } catch (EmptyResultDataAccessException ignored) {
            throw new ServiceNotFoundException();
        }
    }

    /** Soft deletion preserves appointment FK and historical price snapshots. */
    @Transactional
    public void deactivate(UUID barberId, UUID id) {
        int rows = jdbc.update("UPDATE services SET active=false "
                + "WHERE barber_id=? AND id=?", barberId, id);
        if (rows == 0) {
            throw new ServiceNotFoundException();
        }
    }

    private static ServiceView map(ResultSet rs) throws SQLException {
        return new ServiceView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getInt("duration_minutes"),
                rs.getBigDecimal("default_price"),
                rs.getBoolean("is_haircut"),
                rs.getBoolean("active"),
                IconKey.valueOf(rs.getString("icon_key")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static String normalizeName(String value) {
        String name = value.strip();
        if (name.isEmpty()) {
            throw new InvalidServiceUpdateException();
        }
        return name;
    }

    public static final class ServiceNotFoundException extends RuntimeException {
        public ServiceNotFoundException() {
            super("Service not found");
        }
    }

    public static final class InvalidServiceUpdateException extends RuntimeException {
        public InvalidServiceUpdateException() {
            super("Invalid service update");
        }
    }
}
