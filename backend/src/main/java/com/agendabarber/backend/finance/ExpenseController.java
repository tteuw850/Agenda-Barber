package com.agendabarber.backend.finance;

import com.agendabarber.backend.finance.ExpenseDtos.ExpenseInput;
import com.agendabarber.backend.finance.ExpenseDtos.ExpenseView;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("neon")
@RequestMapping("/api/v1/expenses")
public class ExpenseController {
    private final ExpenseService expenses;

    public ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    @GetMapping
    public List<ExpenseView> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return expenses.list(barber(jwt), from, to, limit, offset);
    }

    @GetMapping("/{id}")
    public ExpenseView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return expenses.get(barber(jwt), id);
    }

    @PostMapping
    public ResponseEntity<ExpenseView> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ExpenseInput input) {
        ExpenseView saved = expenses.create(barber(jwt), UUID.fromString(jwt.getSubject()), key, input);
        return ResponseEntity.created(URI.create("/api/v1/expenses/" + saved.id())).body(saved);
    }

    @PutMapping("/{id}")
    public ExpenseView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ExpenseInput input) {
        return expenses.update(barber(jwt), id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        expenses.delete(barber(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static UUID barber(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("barber_id"));
    }
}
