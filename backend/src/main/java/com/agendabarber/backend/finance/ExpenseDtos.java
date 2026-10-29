package com.agendabarber.backend.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ExpenseDtos {
    private ExpenseDtos() {
    }

    public record ExpenseInput(
            @NotNull LocalDate occurredOn,
            @NotBlank @Size(max = 255) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 80) String category) {
    }

    public record ExpenseView(
            UUID id, LocalDate occurredOn, String description, BigDecimal amount,
            String category, Instant createdAt, Instant updatedAt) {
    }
}
