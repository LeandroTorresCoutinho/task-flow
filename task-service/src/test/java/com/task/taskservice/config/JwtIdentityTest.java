package com.task.taskservice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtIdentityTest {

    @Test
    void extractsOwnerIdFromSubject() {
        UUID ownerId = UUID.randomUUID();

        assertEquals(ownerId, JwtIdentity.ownerId(jwt(ownerId.toString())));
    }

    @Test
    void rejectsSubjectThatIsNotAUuid() {
        assertThrows(BadJwtException.class, () -> JwtIdentity.ownerId(jwt("not-a-uuid")));
    }

    private Jwt jwt(String subject) {
        Instant issuedAt = Instant.parse("2026-10-23T12:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .build();
    }
}
