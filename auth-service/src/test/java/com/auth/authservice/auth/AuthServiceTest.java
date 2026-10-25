package com.auth.authservice.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auth.authservice.error.DuplicateEmailException;
import com.auth.authservice.error.InvalidCredentialsException;
import com.auth.authservice.user.User;
import com.auth.authservice.user.UserRepository;
import io.r2dbc.postgresql.api.ErrorDetails;
import io.r2dbc.postgresql.api.PostgresqlException;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import reactor.core.publisher.Mono;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                users, passwordEncoder, jwtService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registrationNormalizesEmailAndHashesPassword() {
        when(users.findByEmail("user@example.com")).thenReturn(Mono.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        UserResponse response = authService
                .register(new RegisterRequest(" User@Example.com ", "password123"))
                .block();

        var savedUser = ArgumentCaptor.forClass(User.class);
        verify(users).save(savedUser.capture());
        assertEquals("user@example.com", response.email());
        assertEquals(NOW, response.createdAt());
        assertNotEquals("password123", savedUser.getValue().passwordHash());
        assertTrue(passwordEncoder.matches("password123", savedUser.getValue().passwordHash()));
    }

    @Test
    void registrationRejectsAnExistingEmail() {
        when(users.findByEmail("user@example.com")).thenReturn(Mono.just(user("encoded")));

        assertThrows(
                DuplicateEmailException.class,
                () -> authService.register(new RegisterRequest("USER@example.com", "password123")).block());
    }

    @Test
    void registrationReportsADuplicateWhenTheDatabaseWinsTheRace() {
        var violation = databaseViolation("users_email_key");
        when(users.findByEmail("user@example.com")).thenReturn(Mono.empty());
        when(users.save(any(User.class))).thenReturn(Mono.error(violation));

        assertThrows(
                DuplicateEmailException.class,
                () -> authService.register(new RegisterRequest("user@example.com", "password123")).block());
    }

    @Test
    void registrationPreservesOtherIntegrityViolations() {
        var violation = databaseViolation("users_pkey");
        when(users.findByEmail("user@example.com")).thenReturn(Mono.empty());
        when(users.save(any(User.class))).thenReturn(Mono.error(violation));

        var thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> authService.register(new RegisterRequest("user@example.com", "password123")).block());

        assertSame(violation, thrown);
    }

    @Test
    void loginReturnsATokenForValidCredentials() {
        User user = user(passwordEncoder.encode("password123"));
        TokenResponse expected = new TokenResponse("signed.jwt", "Bearer", 3600);
        when(users.findByEmail("user@example.com")).thenReturn(Mono.just(user));
        when(jwtService.issue(user)).thenReturn(expected);

        TokenResponse response = authService
                .login(new LoginRequest("USER@example.com", "password123"))
                .block();

        assertEquals(expected, response);
    }

    @Test
    void loginRejectsInvalidCredentials() {
        User user = user(passwordEncoder.encode("password123"));
        when(users.findByEmail("user@example.com")).thenReturn(Mono.just(user));

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("user@example.com", "wrong-password")).block());
    }

    @Test
    void loginRejectsAnUnknownEmailWithTheSameError() {
        when(users.findByEmail("missing@example.com")).thenReturn(Mono.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("missing@example.com", "password123")).block());
    }

    private User user(String passwordHash) {
        return new User(UUID.randomUUID(), "user@example.com", passwordHash, NOW, NOW);
    }

    private DataIntegrityViolationException databaseViolation(String constraintName) {
        var details = mock(ErrorDetails.class);
        when(details.getConstraintName()).thenReturn(Optional.of(constraintName));
        return new DataIntegrityViolationException(
                constraintName, new TestPostgresqlException(details));
    }

    private static final class TestPostgresqlException extends R2dbcDataIntegrityViolationException
            implements PostgresqlException {

        private final ErrorDetails details;

        private TestPostgresqlException(ErrorDetails details) {
            super("constraint violation", "23505");
            this.details = details;
        }

        @Override
        public ErrorDetails getErrorDetails() {
            return details;
        }
    }
}
