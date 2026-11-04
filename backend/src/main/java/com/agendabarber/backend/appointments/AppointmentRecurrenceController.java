package com.agendabarber.backend.appointments;

import com.agendabarber.backend.appointments.AppointmentRecurrenceService.Preview;
import com.agendabarber.backend.appointments.AppointmentRecurrenceService.Request;
import com.agendabarber.backend.appointments.AppointmentRecurrenceService.Series;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("neon")
@RequestMapping("/api/v1/appointment-recurrences")
public class AppointmentRecurrenceController {
    private final AppointmentRecurrenceService service;

    public AppointmentRecurrenceController(AppointmentRecurrenceService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    public Preview preview(@AuthenticationPrincipal Jwt jwt,
                           @Valid @RequestBody Request request) {
        return service.preview(barber(jwt), request);
    }

    @PostMapping
    public ResponseEntity<Series> create(@AuthenticationPrincipal Jwt jwt,
                    @RequestHeader("Idempotency-Key") String key,
                    @Valid @RequestBody Request request) {
        return ResponseEntity.ok(service.create(barber(jwt),
                UUID.fromString(jwt.getSubject()), key, request));
    }

    @GetMapping
    public List<Series> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(barber(jwt));
    }

    @GetMapping("/{id}")
    public Series get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(barber(jwt), id);
    }

    @PostMapping("/{id}/cancel-future")
    public Series cancelFuture(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.cancelFuture(barber(jwt), id);
    }

    private static UUID barber(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("barber_id"));
    }
}
