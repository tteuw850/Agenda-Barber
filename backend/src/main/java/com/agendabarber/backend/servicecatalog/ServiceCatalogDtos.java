package com.agendabarber.backend.servicecatalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** API payloads tailored to the UX-07 cards and UX-04 service selector. */
public final class ServiceCatalogDtos {
    private ServiceCatalogDtos() {
    }

    public enum IconKey {
        SCISSORS, BEARD, COMBO, EYEBROW, CHILD, OTHER
    }

    public record CreateService(
            @NotBlank @Size(max = 150) String name,
            @NotNull @Min(1) @Max(480) Integer durationMinutes,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal defaultPrice,
            @NotNull Boolean isHaircut,
            Boolean active,
            IconKey iconKey) {
    }

    public record UpdateService(
            @Size(max = 150) String name,
            @Min(1) @Max(480) Integer durationMinutes,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal defaultPrice,
            Boolean isHaircut,
            Boolean active,
            IconKey iconKey) {
        public boolean hasChanges() {
            return name != null || durationMinutes != null || defaultPrice != null
                    || isHaircut != null || active != null || iconKey != null;
        }
    }

    public record ServiceView(UUID id, String name, int durationMinutes, BigDecimal defaultPrice,
                              boolean isHaircut, boolean active, IconKey iconKey,
                              Instant createdAt, Instant updatedAt) {
    }
}
