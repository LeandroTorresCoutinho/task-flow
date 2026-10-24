package com.auth.authservice.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
