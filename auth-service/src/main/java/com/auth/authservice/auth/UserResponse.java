package com.auth.authservice.auth;

import com.auth.authservice.user.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, Instant createdAt) {

    static UserResponse from(User user) {
        return new UserResponse(user.id(), user.email(), user.createdAt());
    }
}
