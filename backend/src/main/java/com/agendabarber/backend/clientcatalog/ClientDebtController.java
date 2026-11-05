package com.agendabarber.backend.clientcatalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("neon")
@RequestMapping("/api/v1/debts")
public class ClientDebtController {
    private final ClientDebtService service;
    public ClientDebtController(ClientDebtService service) { this.service = service; }
    public record Summary(UUID clientId, String clientName, BigDecimal balance) {}
    public record Entry(UUID id, String kind, BigDecimal amount, String note,
                        java.time.Instant createdAt) {}
    public record Detail(UUID clientId, String clientName, BigDecimal balance,
                         List<Entry> entries) {}
    public record Input(@NotNull String kind, @NotNull @DecimalMin("0.01") BigDecimal amount,
                        @Size(max = 255) String note) {}
    @GetMapping
    public List<Summary> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(barber(jwt));
    }
    @GetMapping("/{clientId}")
    public Detail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
        return service.get(barber(jwt), clientId);
    }
    @PostMapping("/{clientId}/entries")
    public Detail add(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId,
                      @RequestHeader("Idempotency-Key") UUID key,
                      @Valid @RequestBody Input input) {
        return service.add(barber(jwt), clientId, key, input);
    }
    private static UUID barber(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("barber_id"));
    }
}

@Service
@Profile("neon")
class ClientDebtService {
    private final JdbcTemplate jdbc;
    ClientDebtService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<ClientDebtController.Summary> list(UUID barber) {
        return jdbc.query("""
                SELECT c.id,c.name,SUM(d.amount) balance FROM client_debt_entries d
                JOIN clients c ON c.barber_id=d.barber_id AND c.id=d.client_id
                WHERE d.barber_id=? AND c.active=true
                GROUP BY c.id,c.name HAVING SUM(d.amount)>0 ORDER BY lower(c.name)
                """, (rs,n) -> new ClientDebtController.Summary(
                rs.getObject("id",UUID.class),rs.getString("name"),rs.getBigDecimal("balance")),barber);
    }
    @Transactional(readOnly = true)
    public ClientDebtController.Detail get(UUID barber, UUID client) {
        return detail(barber,client,name(barber,client,false));
    }
    @Transactional
    public ClientDebtController.Detail add(UUID barber, UUID client, UUID key,
                                           ClientDebtController.Input input) {
        String name = name(barber,client,true);
        if (!"CHARGE".equals(input.kind()) && !"PAYMENT".equals(input.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tipo inválido");
        }
        BigDecimal amount = input.amount();
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.precision()-amount.scale() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valor inválido");
        }
        BigDecimal signed = "CHARGE".equals(input.kind()) ? amount : amount.negate();
        List<BigDecimal> previous = jdbc.query(
            "SELECT amount FROM client_debt_entries WHERE barber_id=? AND request_key=?",
            (rs,n) -> rs.getBigDecimal(1), barber,key);
        if (!previous.isEmpty()) {
            if (previous.get(0).compareTo(signed) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Chave de operação reutilizada");
            }
            return detail(barber,client,name);
        }
        if (balance(barber,client).add(signed).signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Pagamento excede dívida");
        }
        jdbc.update("""
                INSERT INTO client_debt_entries(barber_id,client_id,amount,note,request_key)
                VALUES(?,?,?,?,?)
                """,barber,client,signed,input.note(),key);
        return detail(barber,client,name);
    }
    private String name(UUID barber, UUID client, boolean lock) {
        List<String> names = jdbc.query(
            "SELECT name FROM clients WHERE barber_id=? AND id=? AND active=true"
                + (lock ? " FOR UPDATE" : ""),
            (rs,n) -> rs.getString(1),barber,client);
        if (names.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cliente não encontrado");
        }
        return names.get(0);
    }
    private BigDecimal balance(UUID barber, UUID client) {
        BigDecimal b = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM client_debt_entries
                WHERE barber_id=? AND client_id=?
                """,BigDecimal.class,barber,client);
        return b == null ? BigDecimal.ZERO : b;
    }
    private ClientDebtController.Detail detail(UUID barber,UUID client,String name) {
        List<ClientDebtController.Entry> entries=jdbc.query("""
                SELECT id,amount,note,created_at FROM client_debt_entries
                WHERE barber_id=? AND client_id=?
                ORDER BY created_at DESC,id DESC LIMIT 100
                """,(rs,n) -> {
                    BigDecimal v=rs.getBigDecimal("amount");
                    return new ClientDebtController.Entry(rs.getObject("id",UUID.class),
                        v.signum()>0?"CHARGE":"PAYMENT",v.abs(),rs.getString("note"),
                        rs.getTimestamp("created_at").toInstant());
                },barber,client);
        return new ClientDebtController.Detail(client,name,balance(barber,client),entries);
    }
}
