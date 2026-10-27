package com.agendabarber.backend.appointments;

import com.agendabarber.backend.appointments.AppointmentDtos.AppointmentView;
import com.agendabarber.backend.appointments.AppointmentDtos.CreateAppointment;
import com.agendabarber.backend.appointments.AppointmentDtos.HistoryItem;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
@RequestMapping("/api/v1/appointments")
public class AppointmentController {
    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @GetMapping
    public List<AppointmentView> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "day") String view,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return appointments.list(barber(jwt), date, view, status, limit, offset);
    }

    @GetMapping("/{id}")
    public AppointmentView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return appointments.get(barber(jwt), id);
    }

    @GetMapping("/{id}/history")
    public List<HistoryItem> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return appointments.history(barber(jwt), id);
    }

    @PostMapping
    public ResponseEntity<AppointmentView> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateAppointment request) {
        // PostgreSQL deadlocks (40P01) abort the transaction. Re-enter through
        // the service proxy with the identical idempotency key in a fresh one.
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                AppointmentView created = appointments.create(
                        barber(jwt), UUID.fromString(jwt.getSubject()), idempotencyKey, request);
                return ResponseEntity.created(URI.create("/api/v1/appointments/" + created.id()))
                        .body(created);
            } catch (PessimisticLockingFailureException deadlock) {
                if (attempt == 2) {
                    throw deadlock;
                }
            }
        }
        throw new IllegalStateException("Unreachable appointment retry state");
    }

    @PatchMapping("/{id}")
    public AppointmentView edit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @RequestBody Map<String, Object> body) {
        return appointments.edit(barber(jwt), id, body);
    }

    @PostMapping("/{id}/start")
    public AppointmentView start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return appointments.transition(barber(jwt), id, "IN_PROGRESS");
    }

    @PostMapping("/{id}/complete")
    public AppointmentView complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return appointments.transition(barber(jwt), id, "COMPLETED");
    }

    @PostMapping("/{id}/cancel")
    public AppointmentView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return appointments.transition(barber(jwt), id, "CANCELLED");
    }

    private static UUID barber(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("barber_id"));
    }
}
