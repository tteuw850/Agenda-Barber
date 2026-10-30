package com.agendabarber.backend.appointments;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** DTOs for UX-02 agenda, UX-03 details/history and UX-04 new booking. */
public final class AppointmentDtos {
    private AppointmentDtos() {
    }

    public record CreateAppointment(
            @NotNull UUID clientId, @NotNull UUID serviceId, @NotNull Instant startsAt,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Size(max = 2000) String notes) {
    }

    public record CompleteAppointment(
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal receivedAmount) {
    }

    public record AppointmentView(
            UUID id, UUID clientId, String clientName, String clientPhone,
            UUID serviceId, String serviceName, int durationMinutes, BigDecimal price,
            Instant startsAt, Instant endsAt, String status, String notes,
            Instant completedAt, long version, Instant createdAt, Instant updatedAt,
            BigDecimal receivedAmount) {
    }

    public record HistoryItem(
            UUID id, String eventType, String previousStatus, String newStatus,
            Instant happenedAt) {
    }
}
