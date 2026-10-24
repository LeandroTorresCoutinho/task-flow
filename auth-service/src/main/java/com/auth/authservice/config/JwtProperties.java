package com.auth.authservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("auth.jwt")
public record JwtProperties(Resource privateKey, Resource publicKey, String keyId, String issuer, Duration ttl) {}
