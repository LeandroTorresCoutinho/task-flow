package com.auth.authservice.auth;

import com.auth.authservice.error.DuplicateEmailException;
import com.auth.authservice.error.InvalidCredentialsException;
import com.auth.authservice.user.User;
import com.auth.authservice.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    @Autowired
    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this(users, passwordEncoder, jwtService, Clock.systemUTC());
    }

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    public Mono<UserResponse> register(RegisterRequest request) {
        String email = normalize(request.email());
        return users.findByEmail(email)
                .flatMap(existing -> Mono.<UserResponse>error(new DuplicateEmailException()))
                .switchIfEmpty(hash(request.password()).flatMap(passwordHash -> {
                    Instant now = clock.instant();
                    return users.save(new User(UUID.randomUUID(), email, passwordHash, now, now))
                            .map(UserResponse::from);
                }))
                .onErrorMap(DataIntegrityViolationException.class, ignored -> new DuplicateEmailException());
    }

    public Mono<TokenResponse> login(LoginRequest request) {
        return users.findByEmail(normalize(request.email()))
                .switchIfEmpty(Mono.error(new InvalidCredentialsException()))
                .flatMap(user -> matches(request.password(), user.passwordHash())
                        .filter(Boolean::booleanValue)
                        .switchIfEmpty(Mono.error(new InvalidCredentialsException()))
                        .map(ignored -> jwtService.issue(user)));
    }

    private Mono<String> hash(String password) {
        return Mono.fromCallable(() -> passwordEncoder.encode(password))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> matches(String password, String hash) {
        return Mono.fromCallable(() -> passwordEncoder.matches(password, hash))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
