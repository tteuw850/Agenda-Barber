package com.agendabarber.backend.servicecatalog;

import com.agendabarber.backend.common.CreateIdempotencyExecutor;
import com.agendabarber.backend.common.ReadRetryExecutor;
import com.agendabarber.backend.servicecatalog.ServiceCatalogDtos.CreateService;
import com.agendabarber.backend.servicecatalog.ServiceCatalogDtos.ServiceView;
import com.agendabarber.backend.servicecatalog.ServiceCatalogDtos.UpdateService;
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
@RequestMapping("/api/v1/services")
public class ServiceCatalogController {
    private final ServiceCatalogService catalog;
    private final ReadRetryExecutor retry;
    private final CreateIdempotencyExecutor idempotency;

    public ServiceCatalogController(ServiceCatalogService catalog, ReadRetryExecutor retry,
            CreateIdempotencyExecutor idempotency) {
        this.catalog = catalog;
        this.retry = retry;
        this.idempotency = idempotency;
    }

    @GetMapping
    public List<ServiceView> list(@AuthenticationPrincipal Jwt jwt,
                                  @RequestParam(defaultValue = "false") boolean activeOnly) {
        return retry.run(() -> catalog.list(barberId(jwt), activeOnly));
    }

    @GetMapping("/{id}")
    public ServiceView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return retry.run(() -> catalog.get(barberId(jwt), id));
    }

    @PostMapping
    public ResponseEntity<ServiceView> create(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CreateService request) {
        UUID barber = barberId(jwt);
        ServiceView created = idempotency.run(barber, UUID.fromString(jwt.getSubject()),
                key, "SERVICE_CREATE", request, () -> catalog.create(barber, request),
                ServiceView::id, id -> catalog.get(barber, id));
        return ResponseEntity.created(URI.create("/api/v1/services/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public ServiceView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody UpdateService request) {
        return catalog.update(barberId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        catalog.deactivate(barberId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static UUID barberId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("barber_id"));
    }
}
