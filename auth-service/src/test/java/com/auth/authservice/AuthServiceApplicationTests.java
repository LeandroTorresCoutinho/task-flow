package com.auth.authservice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthServiceApplicationTests {

    private static final Path PRIVATE_KEY;
    private static final Path PUBLIC_KEY;

    static {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var keyPair = generator.generateKeyPair();
            PRIVATE_KEY = writePem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
            PUBLIC_KEY = writePem("PUBLIC KEY", keyPair.getPublic().getEncoded());
        } catch (GeneralSecurityException | IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("task_flow");

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> r2dbcUrl("auth_service"));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.liquibase.url", postgres::getJdbcUrl);
        registry.add("spring.liquibase.user", postgres::getUsername);
        registry.add("spring.liquibase.password", postgres::getPassword);
        registry.add("auth.jwt.private-key", () -> PRIVATE_KEY.toUri().toString());
        registry.add("auth.jwt.public-key", () -> PUBLIC_KEY.toUri().toString());
    }

    @Test
    void healthEndpointIsUp() {
        client()
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void publishesPublicSigningKey() {
        client()
                .get()
                .uri("/.well-known/jwks.json")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.keys[0].kid")
                .isEqualTo("task-flow-key-1")
                .jsonPath("$.keys[0].kty")
                .isEqualTo("RSA")
                .jsonPath("$.keys[0].alg")
                .isEqualTo("RS256")
                .jsonPath("$.keys[0].use")
                .isEqualTo("sig")
                .jsonPath("$.keys[0].d")
                .doesNotExist();
    }

    @Test
    void registeredUserCanLoginAndInvalidCredentialsAreRejected() {
        String email = UUID.randomUUID() + "@example.com";
        var registration = Map.of("email", email, "password", "password123");

        client()
                .post()
                .uri("/api/v1/auth/register")
                .bodyValue(registration)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.email")
                .isEqualTo(email);

        client()
                .post()
                .uri("/api/v1/auth/register")
                .bodyValue(registration)
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("EMAIL_ALREADY_EXISTS");

        client()
                .post()
                .uri("/api/v1/auth/login")
                .bodyValue(Map.of("email", email, "password", "password123"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.accessToken")
                .isNotEmpty()
                .jsonPath("$.tokenType")
                .isEqualTo("Bearer")
                .jsonPath("$.expiresIn")
                .isEqualTo(3600);

        client()
                .post()
                .uri("/api/v1/auth/login")
                .bodyValue(Map.of("email", email, "password", "not-the-password"))
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_CREDENTIALS");
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port).build();
    }

    private static String r2dbcUrl(String schema) {
        return "r2dbc:postgresql://%s:%d/%s?schema=%s"
                .formatted(postgres.getHost(),
                        postgres.getMappedPort(5432),
                        postgres.getDatabaseName(),
                        schema);
    }

    private static Path writePem(String type, byte[] encoded) throws IOException {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        Path path = Files.createTempFile("task-flow-jwt-", ".pem");
        Files.writeString(
                path,
                "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n",
                StandardCharsets.US_ASCII);
        path.toFile().deleteOnExit();
        return path;
    }
}
