package com.agendabarber.backend.scheduleconfig;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** UX-08 block form, UX-10 working hours and UX-04 availability contracts. */
public final class ScheduleDtos {
    private ScheduleDtos() {
    }

    public record WorkingHoursInput(
            @NotNull Boolean enabled, @NotNull LocalTime opensAt,
            @NotNull LocalTime closesAt) {
    }

    public record WorkingHoursView(
            int weekday, boolean enabled, LocalTime opensAt, LocalTime closesAt) {
    }

    public record SingleBlockInput(
            @NotNull Instant startsAt, @NotNull Instant endsAt,
            @Size(max = 500) String reason, @Size(max = 2000) String notes) {
    }

    public record SingleBlockView(UUID id, Instant startsAt, Instant endsAt,
                                  String reason, String notes) {
    }

    public record WeeklyBlockInput(
            @NotNull @Min(1) @Max(7) Integer weekday,
            @NotNull LocalTime startsAt, @NotNull LocalTime endsAt,
            @NotNull LocalDate validFrom, @NotNull LocalDate validUntil,
            @Size(max = 500) String reason, @Size(max = 2000) String notes) {
    }

    public record WeeklyBlockView(
            UUID id, int weekday, LocalTime startsAt, LocalTime endsAt,
            LocalDate validFrom, LocalDate validUntil, String reason, String notes) {
    }

    public record FreeSlot(Instant startsAt, Instant endsAt, LocalTime localStart) {
    }
}
