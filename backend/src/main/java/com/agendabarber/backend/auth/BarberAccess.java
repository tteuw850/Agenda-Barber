package com.agendabarber.backend.auth;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Reusable server-side scope check. Never trust an arbitrary barberId from a client. */
@Component("barberAccess")
@Profile("neon")
public class BarberAccess {
    public boolean owns(Authentication authentication, UUID requestedBarberId) {
        if (requestedBarberId == null || !(authentication instanceof JwtAuthenticationToken token)) {
            return false;
        }
        try {
            return requestedBarberId.equals(UUID.fromString(
                    token.getToken().getClaimAsString("barber_id")));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
