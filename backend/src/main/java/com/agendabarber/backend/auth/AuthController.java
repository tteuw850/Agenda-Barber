package com.agendabarber.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
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
    private final AuthSessionService sessions;

    public AuthController(AuthService service, AuthSessionService sessions) {
        this.service = service;
        this.sessions = sessions;
    }

    @PostMapping("/login")
    public AuthService.LoginToken login(@Valid @RequestBody LoginRequest request,
                                        HttpServletRequest servletRequest) {
        return service.login(request.username(), request.password(), servletRequest.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public AuthService.LoginToken refresh(@Valid @RequestBody RefreshRequest request) {
        return sessions.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        sessions.logout(UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString("session_id")));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentBarber me(@AuthenticationPrincipal Jwt jwt) {
        return new CurrentBarber(UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString("barber_id")),
                jwt.getClaimAsString("role"));
    }

    public record LoginRequest(@JsonAlias("email") @NotBlank @Size(max = 254) String username,
                               @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 128) String refreshToken) {
    }

    public record CurrentBarber(UUID userId, UUID barberId, String role) {
    }
}
