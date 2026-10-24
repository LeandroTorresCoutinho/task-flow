package com.task.taskservice.config;

import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtIdentity {

    private JwtIdentity() {}

    public static UUID ownerId(Jwt jwt) {
        try {
            return UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BadJwtException("JWT subject must be a UUID");
        }
    }
}
