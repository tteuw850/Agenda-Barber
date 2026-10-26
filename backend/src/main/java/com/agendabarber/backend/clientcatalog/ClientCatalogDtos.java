package com.agendabarber.backend.clientcatalog;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Data contract for UX-04 client selector, UX-05 form and UX-06 client cards. */
public final class ClientCatalogDtos {
    private ClientCatalogDtos() {
    }

    public record ClientInput(
            @NotBlank @Size(max = 150) String name,
            String phone,
            @Email @Size(max = 254) String email,
            @PastOrPresent LocalDate birthDate,
            @Size(max = 4000) String notes,
            Boolean favorite,
            @Size(max = 512) @Pattern(regexp = "https://[^\\s]+") String photoUrl) {
    }

    public record ClientView(
            UUID id, String name, String phone, String email, LocalDate birthDate,
            String notes, boolean favorite, String photoUrl, boolean active,
            long completedAppointments, Instant lastAppointmentAt,
            Instant createdAt, Instant updatedAt) {
    }
}
