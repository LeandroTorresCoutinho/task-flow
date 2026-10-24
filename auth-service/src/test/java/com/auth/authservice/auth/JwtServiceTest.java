package com.auth.authservice.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.auth.authservice.config.JwtProperties;
import com.auth.authservice.user.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtServiceTest {

    @Test
    void issuedTokenIsSignedWithRs256AndContainsIdentityClaims() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        var publicKey = (RSAPublicKey) keyPair.getPublic();
        var signingKey = new RSAKey.Builder(publicKey)
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-key")
                .build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
        var properties =
                new JwtProperties(null, null, "test-key", "https://task-flow.local/auth", Duration.ofHours(1));
        var issuedAt = Instant.now().minusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var service = new JwtService(encoder, properties, Clock.fixed(issuedAt, ZoneOffset.UTC));
        var user = new User(UUID.randomUUID(), "user@example.com", "hash", issuedAt, issuedAt);

        TokenResponse response = service.issue(user);
        var jwt = NimbusJwtDecoder.withPublicKey(publicKey).build().decode(response.accessToken());

        assertEquals("RS256", jwt.getHeaders().get("alg"));
        assertEquals("test-key", jwt.getHeaders().get("kid"));
        assertEquals(user.id().toString(), jwt.getSubject());
        assertEquals(user.email(), jwt.getClaimAsString("email"));
        assertEquals("https://task-flow.local/auth", jwt.getIssuer().toString());
        assertEquals(issuedAt, jwt.getIssuedAt());
        assertEquals(issuedAt.plusSeconds(3600), jwt.getExpiresAt());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600, response.expiresIn());
    }
}
