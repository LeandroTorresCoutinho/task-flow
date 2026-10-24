package com.task.taskservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class ApiIntegrationEnvironmentIT {

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private static PostgreSQLContainer postgres;
    private static Process authService;
    private static Process taskService;
    private static String authBaseUrl;
    private static String taskBaseUrl;

    @BeforeAll
    static void startEnvironment() throws Exception {
        try {
            postgres = new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("task_flow");
            postgres.start();

            Path logsDirectory = Path.of(requiredProperty("integration.logs-directory"));
            Files.createDirectories(logsDirectory);
            KeyFiles keys = generateKeyFiles(logsDirectory);

            int authPort = availablePort();
            int taskPort = availablePort();
            authBaseUrl = "http://localhost:" + authPort;
            taskBaseUrl = "http://localhost:" + taskPort;

            authService = startService(
                    "auth-service",
                    Path.of(requiredProperty("integration.auth-service-jar")),
                    authPort,
                    List.of(
                            "--spring.r2dbc.url=" + r2dbcUrl("auth_service"),
                            "--spring.r2dbc.username=" + postgres.getUsername(),
                            "--spring.r2dbc.password=" + postgres.getPassword(),
                            "--spring.liquibase.url=" + postgres.getJdbcUrl(),
                            "--spring.liquibase.user=" + postgres.getUsername(),
                            "--spring.liquibase.password=" + postgres.getPassword(),
                            "--auth.jwt.private-key=" + keys.privateKey().toUri(),
                            "--auth.jwt.public-key=" + keys.publicKey().toUri(),
                            "--auth.jwt.issuer=" + authBaseUrl),
                    logsDirectory.resolve("auth-service.log"));
            waitUntilHealthy("auth-service", authService, authBaseUrl, logsDirectory.resolve("auth-service.log"));

            taskService = startService(
                    "task-service",
                    Path.of(requiredProperty("integration.task-service-jar")),
                    taskPort,
                    List.of(
                            "--spring.r2dbc.url=" + r2dbcUrl("task_service"),
                            "--spring.r2dbc.username=" + postgres.getUsername(),
                            "--spring.r2dbc.password=" + postgres.getPassword(),
                            "--spring.liquibase.url=" + postgres.getJdbcUrl(),
                            "--spring.liquibase.user=" + postgres.getUsername(),
                            "--spring.liquibase.password=" + postgres.getPassword(),
                            "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + authBaseUrl,
                            "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri="
                                    + authBaseUrl + "/.well-known/jwks.json"),
                    logsDirectory.resolve("task-service.log"));
            waitUntilHealthy("task-service", taskService, taskBaseUrl, logsDirectory.resolve("task-service.log"));
        } catch (Exception exception) {
            stopEnvironment();
            throw exception;
        }
    }

    @AfterAll
    static void stopEnvironment() {
        stop(taskService);
        stop(authService);
        if (postgres != null) {
            postgres.stop();
            postgres = null;
        }
    }

    @Test
    void bothServicesStartOverHttpAndAuthPublishesItsSigningKey() throws Exception {
        HttpResponse<String> authHealth = get(authBaseUrl + "/actuator/health");
        HttpResponse<String> taskHealth = get(taskBaseUrl + "/actuator/health");
        HttpResponse<String> jwks = get(authBaseUrl + "/.well-known/jwks.json");

        assertEquals(HttpURLConnection.HTTP_OK, authHealth.statusCode());
        assertEquals(HttpURLConnection.HTTP_OK, taskHealth.statusCode());
        assertEquals(HttpURLConnection.HTTP_OK, jwks.statusCode());
        assertTrue(authHealth.body().contains("\"status\":\"UP\""));
        assertTrue(taskHealth.body().contains("\"status\":\"UP\""));
        assertTrue(jwks.body().contains("\"kid\":\"task-flow-key-1\""));
    }

    @Test
    void karateApiIntegrationSuitePasses() {
        SuiteResult result = Runner.path("classpath:features/api")
                .systemProperty("auth.base-url", authBaseUrl)
                .systemProperty("task.base-url", taskBaseUrl)
                .parallel(1);

        assertTrue(result.isPassed());
    }

    private static Process startService(
            String name, Path jar, int port, List<String> arguments, Path logFile) throws IOException {
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Missing packaged " + name + ": " + jar);
        }

        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-jar");
        command.add(jar.toString());
        command.add("--server.port=" + port);
        command.addAll(arguments);

        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();
    }

    private static void waitUntilHealthy(String name, Process process, String baseUrl, Path logFile)
            throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException(name + " exited during startup.\n" + Files.readString(logFile));
            }
            try {
                if (get(baseUrl + "/actuator/health").statusCode() == HttpURLConnection.HTTP_OK) {
                    return;
                }
            } catch (IOException ignored) {
                // The service is still starting.
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException(name + " did not become healthy.\n" + Files.readString(logFile));
    }

    private static HttpResponse<String> get(String url) throws IOException, InterruptedException {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static int availablePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String r2dbcUrl(String schema) {
        return "r2dbc:postgresql://%s:%d/%s?schema=%s"
                .formatted(postgres.getHost(), postgres.getMappedPort(5432), postgres.getDatabaseName(), schema);
    }

    private static KeyFiles generateKeyFiles(Path directory) throws GeneralSecurityException, IOException {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        Path privateKey = writePem(directory.resolve("jwt-private.pem"), "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        Path publicKey = writePem(directory.resolve("jwt-public.pem"), "PUBLIC KEY", keyPair.getPublic().getEncoded());
        return new KeyFiles(privateKey, publicKey);
    }

    private static Path writePem(Path path, String type, byte[] encoded) throws IOException {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        return Files.writeString(
                path,
                "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n",
                StandardCharsets.US_ASCII);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing system property: " + name);
        }
        return value;
    }

    private static void stop(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private record KeyFiles(Path privateKey, Path publicKey) {}
}
