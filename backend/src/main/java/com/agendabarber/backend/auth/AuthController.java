package com.agendabarber.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("neon")
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public AuthService.LoginToken login(@Valid @RequestBody LoginRequest request,
                                        HttpServletRequest servletRequest) {
        return service.login(request.email(), request.password(), servletRequest.getRemoteAddr());
    }

    @GetMapping("/me")
    public CurrentBarber me(@AuthenticationPrincipal Jwt jwt) {
        return new CurrentBarber(UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString("barber_id")),
                jwt.getClaimAsString("role"));
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank String password) {
    }

    public record CurrentBarber(UUID userId, UUID barberId, String role) {
    }
}
