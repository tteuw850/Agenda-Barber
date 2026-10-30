package com.agendabarber.backend.reports;

import com.agendabarber.backend.reports.ReportDtos.DailyMetric;
import com.agendabarber.backend.reports.ReportDtos.Dashboard;
import com.agendabarber.backend.reports.ReportDtos.PopularService;
import com.agendabarber.backend.reports.ReportDtos.Summary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("neon")
@RequestMapping("/api/v1/reports")
@Tag(name = "Financial reports", description = "Owner-scoped revenue, costs and completed services")
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard for an inclusive local-date period",
            description = "Completed-at is grouped in the barber's timezone. Only actual receipts "
                    + "contribute to revenue; soft-deleted expenses are excluded. Maximum 366 days.")
    public Dashboard dashboard(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.dashboard(barber(jwt), from, to);
    }

    @GetMapping("/summary")
    @Operation(summary = "Financial totals and paid ticket for the inclusive period",
            description = "Average ticket divides revenue by completed appointments with known "
                    + "received amounts; historical NULL receipts are counted separately.")
    public Summary summary(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.summary(barber(jwt), from, to);
    }

    @GetMapping("/daily")
    @Operation(summary = "Daily revenue and expenses including empty days")
    public List<DailyMetric> daily(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.daily(barber(jwt), from, to);
    }

    @GetMapping("/popular-services")
    @Operation(summary = "Top completed services ranked by appointment count")
    public List<PopularService> popularServices(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return reports.popularServices(barber(jwt), from, to, limit);
    }

    private static UUID barber(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("barber_id"));
    }
}
