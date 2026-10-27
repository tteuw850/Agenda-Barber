package com.agendabarber.backend.scheduleconfig;

import com.agendabarber.backend.scheduleconfig.ScheduleDtos.FreeSlot;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.SingleBlockInput;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.SingleBlockView;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.WeeklyBlockInput;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.WeeklyBlockView;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.WorkingHoursInput;
import com.agendabarber.backend.scheduleconfig.ScheduleDtos.WorkingHoursView;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("neon")
@RequestMapping("/api/v1/schedule")
public class ScheduleConfigController {
    private final ScheduleConfigService schedule;

    public ScheduleConfigController(ScheduleConfigService schedule) {
        this.schedule = schedule;
    }

    @GetMapping("/working-hours")
    public List<WorkingHoursView> workingHours(@AuthenticationPrincipal Jwt jwt) {
        return schedule.workingHours(barberId(jwt));
    }

    @PutMapping("/working-hours/{weekday}")
    public WorkingHoursView setHours(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable int weekday,
                                     @Valid @RequestBody WorkingHoursInput input) {
        return schedule.setHours(barberId(jwt), weekday, input);
    }

    @GetMapping("/blocks")
    public List<SingleBlockView> blocks(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return schedule.blocks(barberId(jwt), date);
    }

    @PostMapping("/blocks")
    public ResponseEntity<SingleBlockView> createBlock(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SingleBlockInput input) {
        SingleBlockView view = schedule.createBlock(barberId(jwt), input);
        return ResponseEntity.created(URI.create("/api/v1/schedule/blocks/" + view.id()))
                .body(view);
    }

    @DeleteMapping("/blocks/{id}")
    public ResponseEntity<Void> deleteBlock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        schedule.deleteBlock(barberId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/recurring-blocks")
    public List<WeeklyBlockView> recurring(@AuthenticationPrincipal Jwt jwt) {
        return schedule.recurring(barberId(jwt));
    }

    @PostMapping("/recurring-blocks")
    public ResponseEntity<WeeklyBlockView> createRecurring(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WeeklyBlockInput input) {
        WeeklyBlockView view = schedule.createRecurring(barberId(jwt), input);
        return ResponseEntity.created(URI.create("/api/v1/schedule/recurring-blocks/" + view.id()))
                .body(view);
    }

    @DeleteMapping("/recurring-blocks/{id}")
    public ResponseEntity<Void> deleteRecurring(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID id) {
        schedule.deleteRecurring(barberId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/available-slots")
    public List<FreeSlot> availableSlots(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam UUID serviceId) {
        return schedule.availableSlots(barberId(jwt), date, serviceId);
    }

    private static UUID barberId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("barber_id"));
    }
}
