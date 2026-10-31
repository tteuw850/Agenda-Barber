package com.agendabarber.backend.clientcatalog;

import com.agendabarber.backend.common.CreateIdempotencyExecutor;
import com.agendabarber.backend.common.ReadRetryExecutor;
import com.agendabarber.backend.clientcatalog.ClientCatalogDtos.ClientInput;
import com.agendabarber.backend.clientcatalog.ClientCatalogDtos.ClientView;
import java.util.Map;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("neon")
@RequestMapping("/api/v1/clients")
public class ClientCatalogController {
    private final ClientCatalogService clients;
    private final ReadRetryExecutor retry;
    private final CreateIdempotencyExecutor idempotency;

    public ClientCatalogController(ClientCatalogService clients, ReadRetryExecutor retry,
            CreateIdempotencyExecutor idempotency) {
        this.clients = clients;
        this.retry = retry;
        this.idempotency = idempotency;
    }

    @GetMapping
    public List<ClientView> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return retry.run(() -> clients.list(barberId(jwt), search, filter, limit, offset));
    }

    @GetMapping("/{id}")
    public ClientView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return retry.run(() -> clients.get(barberId(jwt), id));
    }

    @PostMapping
    public ResponseEntity<ClientView> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ClientInput input) {
        UUID barber = barberId(jwt);
        ClientView created = idempotency.run(barber, UUID.fromString(jwt.getSubject()),
                key, "CLIENT_CREATE", input, () -> clients.create(barber, input),
                ClientView::id, id -> clients.get(barber, id));
        return ResponseEntity.created(URI.create("/api/v1/clients/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    public ClientView update(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID id, @RequestBody Map<String, Object> patch) {
        return clients.update(barberId(jwt), id, patch);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        clients.deactivate(barberId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static UUID barberId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("barber_id"));
    }
}
