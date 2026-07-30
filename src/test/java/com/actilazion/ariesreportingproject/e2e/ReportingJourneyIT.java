package com.actilazion.ariesreportingproject.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Public-API journey against isolated infrastructure.
 *
 * <p>Run with {@code mvn -Dit.test=ReportingJourneyIT verify}. This is an
 * E2E lane, not a replacement for fast unit or focused HTTP tests.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportingJourneyIT {
    private static final String JWT_SECRET =
            "dGVzdFNlY3JldEtleUZvckp3dFRlc3RpbmdQdXJwb3Nlc09ubHk=";
    private static final String JWT_ISSUER = "aries-transaction";
    private static final String JWT_AUDIENCE = "aries-transaction-api";
    private static final String USER_EMAIL = "journey-user@aries.test";
    private static final String OTHER_EMAIL = "other-user@aries.test";
    private static final String ADMIN_EMAIL = "journey-admin@aries.test";

    @Container
    static final PostgreSQLContainer<?> REPORTING_DB =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final PostgreSQLContainer<?> SOURCE_DB =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    @Container
    static final GenericContainer<?> SMTP = new GenericContainer<>(
            DockerImageName.parse("mailhog/mailhog:v1.0.1"))
            .withExposedPorts(1025)
            .waitingFor(Wait.forListeningPort());

    private static final Path EXPORT_DIR;

    static {
        try {
            EXPORT_DIR = Files.createTempDirectory("aries-journey-exports-");
            // Start before Spring evaluates DynamicPropertySource suppliers.
            REPORTING_DB.start();
            SOURCE_DB.start();
            REDIS.start();
            SMTP.start();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.reporting.url", REPORTING_DB::getJdbcUrl);
        registry.add("spring.datasource.reporting.username", REPORTING_DB::getUsername);
        registry.add("spring.datasource.reporting.password", REPORTING_DB::getPassword);
        registry.add("spring.datasource.transaction.url", SOURCE_DB::getJdbcUrl);
        registry.add("spring.datasource.transaction.username", SOURCE_DB::getUsername);
        registry.add("spring.datasource.transaction.password", SOURCE_DB::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.mail.host", SMTP::getHost);
        registry.add("spring.mail.port", () -> SMTP.getMappedPort(1025));
        registry.add("spring.mail.username", () -> "journey");
        registry.add("spring.mail.password", () -> "journey");
        registry.add("app.reporting.export-dir", () -> EXPORT_DIR.toString());
        registry.add("app.reporting.email.dispatch-delay-ms", () -> 3_600_000);
        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("jwt.issuer", () -> JWT_ISSUER);
        registry.add("jwt.audience", () -> JWT_AUDIENCE);
        registry.add("jwt.token-type", () -> "access");
    }

    @LocalServerPort
    int port;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpClient httpClient;
    private UUID userId;
    private UUID otherUserId;
    private UUID adminId;
    private UUID userAccountId;
    private UUID otherAccountId;
    private UUID transactionId;

    @BeforeAll
    void seedSourceDatabase() throws Exception {
        httpClient = HttpClient.newHttpClient();
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        userAccountId = UUID.randomUUID();
        otherAccountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        try (Connection connection = DriverManager.getConnection(
                SOURCE_DB.getJdbcUrl(), SOURCE_DB.getUsername(), SOURCE_DB.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE users (
                        id UUID PRIMARY KEY, full_name VARCHAR(100) NOT NULL,
                        email VARCHAR(255) NOT NULL UNIQUE, role VARCHAR(20) NOT NULL,
                        is_active BOOLEAN NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE accounts (
                        id UUID PRIMARY KEY, user_id UUID NOT NULL,
                        account_number VARCHAR(20) NOT NULL, balance NUMERIC(18,2) NOT NULL,
                        currency CHAR(3) NOT NULL, status VARCHAR(20) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE transactions (
                        id UUID PRIMARY KEY, from_account_id UUID NOT NULL,
                        to_account_id UUID NOT NULL, initiated_by UUID NOT NULL,
                        amount NUMERIC(18,2) NOT NULL, currency CHAR(3) NOT NULL,
                        status VARCHAR(20) NOT NULL, idempotency_key VARCHAR(64) NOT NULL,
                        description VARCHAR(255), failure_reason VARCHAR(500),
                        created_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ
                    )
                    """);
        }

        try (Connection connection = DriverManager.getConnection(
                SOURCE_DB.getJdbcUrl(), SOURCE_DB.getUsername(), SOURCE_DB.getPassword())) {
            insertUser(connection, userId, "Journey User", USER_EMAIL, "USER");
            insertUser(connection, otherUserId, "Other User", OTHER_EMAIL, "USER");
            insertUser(connection, adminId, "Journey Admin", ADMIN_EMAIL, "ADMIN");
            insertAccount(connection, userAccountId, userId, "J10001");
            insertAccount(connection, otherAccountId, otherUserId, "J20001");
            OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO transactions
                    (id, from_account_id, to_account_id, initiated_by, amount, currency,
                     status, idempotency_key, description, created_at, completed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setObject(1, transactionId);
                insert.setObject(2, userAccountId);
                insert.setObject(3, otherAccountId);
                insert.setObject(4, userId);
                insert.setBigDecimal(5, new BigDecimal("125.50"));
                insert.setString(6, "VND");
                insert.setString(7, "COMPLETED");
                insert.setString(8, "journey-" + transactionId);
                insert.setString(9, "journey payment");
                insert.setObject(10, createdAt);
                insert.setObject(11, createdAt.plusSeconds(2));
                insert.executeUpdate();
            }
        }
    }

    @AfterAll
    void cleanExportDirectory() throws Exception {
        if (Files.exists(EXPORT_DIR)) {
            try (var paths = Files.walk(EXPORT_DIR)) {
                paths.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    void userJourney_backfill_statement_export_download_andIdor() throws Exception {
        HttpResponse<String> backfill = request("POST", "/api/v1/admin/backfill?since=2020-01-01T00:00:00Z",
                adminToken(), null);
        assertThat(backfill.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(backfill.body()).path("data").asLong()).isEqualTo(1);

        HttpResponse<String> statement = request("GET",
                "/api/v1/reports/statement?accountId=" + userAccountId + "&from=2026-01&to=2026-12",
                userToken(), null);
        assertThat(statement.statusCode()).isEqualTo(200);
        JsonNode statementData = objectMapper.readTree(statement.body()).path("data");
        assertThat(statementData.path("totalDebit").decimalValue())
                .isEqualByComparingTo("125.50");
        assertThat(statementData.path("txCount").asInt()).isEqualTo(1);
        assertThat(statementData.path("transactions").path("content").size()).isEqualTo(1);

        String exportBody = objectMapper.createObjectNode()
                .put("accountId", userAccountId.toString())
                .put("jobType", "ACCOUNT_STATEMENT")
                .put("format", "EXCEL")
                .put("from", "2026-01")
                .put("to", "2026-12")
                .toString();
        HttpResponse<String> export = request("POST", "/api/v1/reports/export/request",
                userToken(), exportBody);
        assertThat(export.statusCode()).isEqualTo(202);
        UUID jobId = UUID.fromString(objectMapper.readTree(export.body())
                .path("data").path("id").asText());

        JsonNode status = awaitReady(jobId);
        assertThat(status.path("status").asText()).isEqualTo("READY");

        HttpResponse<byte[]> download = requestBytes("GET",
                "/api/v1/reports/export/" + jobId + "/download", userToken());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).contains("spreadsheetml"));
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(download.body()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            boolean accountFound = false;
            for (Row row : workbook.getSheetAt(0)) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.STRING
                            && cell.getStringCellValue().contains("J10001")) {
                        accountFound = true;
                    }
                }
            }
            assertThat(accountFound).isTrue();
        }

        HttpResponse<String> idor = request("GET",
                "/api/v1/reports/export/" + jobId + "/status", otherUserToken(), null);
        assertThat(idor.statusCode()).isEqualTo(403);
    }

    private JsonNode awaitReady(UUID jobId) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            HttpResponse<String> response = request("GET",
                    "/api/v1/reports/export/" + jobId + "/status", userToken(), null);
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            String status = data.path("status").asText();
            if ("READY".equals(status) || "FAILED".equals(status)) {
                return data;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Export did not reach a terminal state");
    }

    private HttpResponse<String> request(String method, String path, String token, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = switch (method) {
            case "POST" -> builder.POST(publisher).build();
            case "GET" -> builder.GET().build();
            default -> throw new IllegalArgumentException("Unsupported method " + method);
        };
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> requestBytes(String method, String path, String token)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private String userToken() {
        return token(USER_EMAIL);
    }

    private String otherUserToken() {
        return token(OTHER_EMAIL);
    }

    private String adminToken() {
        return token(ADMIN_EMAIL);
    }

    private String token(String subject) {
        var now = new Date();
        return Jwts.builder()
                .subject(subject)
                .issuer(JWT_ISSUER)
                .audience().add(JWT_AUDIENCE).and()
                .claim("typ", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 300_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET)))
                .compact();
    }

    private void insertUser(Connection connection, UUID id, String name, String email, String role)
            throws Exception {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO users (id, full_name, email, role, is_active) VALUES (?, ?, ?, ?, true)")) {
            insert.setObject(1, id);
            insert.setString(2, name);
            insert.setString(3, email);
            insert.setString(4, role);
            insert.executeUpdate();
        }
    }

    private void insertAccount(Connection connection, UUID id, UUID ownerId, String number)
            throws Exception {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO accounts (id, user_id, account_number, balance, currency, status)
                VALUES (?, ?, ?, 10000.00, 'VND', 'ACTIVE')
                """)) {
            insert.setObject(1, id);
            insert.setObject(2, ownerId);
            insert.setString(3, number);
            insert.executeUpdate();
        }
    }
}
